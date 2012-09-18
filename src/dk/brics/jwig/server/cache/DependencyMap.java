package dk.brics.jwig.server.cache;

import dk.brics.jwig.WebApp;
import dk.brics.jwig.XMLProducer;
import dk.brics.jwig.persistence.Persistable;
import dk.brics.jwig.server.ThreadContext;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The dependency map contains a map from objects to cached responses and XMLProducers that depend on them.
 */
public class DependencyMap {

    private Map<Object, Set<CacheObject>> objectPageMap = new ConcurrentHashMap<Object, Set<CacheObject>>();

    private Map<CacheObject, Set<Object>> pageObjectMap = new ConcurrentHashMap<CacheObject, Set<Object>>();

    private volatile int counter;
    private static final Logger log = Logger.getLogger(DependencyMap.class);
    private final Set<CacheTransaction> openTransactions = Collections.synchronizedSet(new HashSet<CacheTransaction>());

    /**
     * Registers the current response object as dependent on the given object.
     */
    public void addResponseDependency(Object p) {
        Object proxy = getObject(p);
        ThreadContext context = ThreadContext.get();
        //The context may be null data is queried from the database by a thread that does not create a page
        if (context == null) {
            return;
        }
        String requestURL = context.getRequestURL();
        if (context.isCacheAugmented()) {
            requestURL = requestURL + "<|>" + WebApp.get().getWebSite().getCacheAugmentationString();
        }
        CacheObject c = new CacheObject(requestURL);
        XMLProducer producer = context.getProducer();
        if (producer != null) {
            c.setUrl(producer.getHandlerIdentifier());
        }
        addDependency(c, proxy);
    }

    /**
     * Registers the given XMLProducer as dependent on the given object.
     */
    public void addDependency(XMLProducer x, Object p) {
        CacheObject o = new CacheObject(x.getHandlerIdentifier());
        addDependency(o, p);
    }

    private void addDependency(CacheObject c, Object object) {
        if (log.isDebugEnabled()) {
            log.debug(c.getUrl() + " depends on " + object);
        }
        if (hasTransaction()) {
            CacheTransaction currentCacheTransaction = getCurrentCacheTransaction();
            if (currentCacheTransaction.isGetMode()) {
                currentCacheTransaction.addDependency(c, object);
            }
        }
    }

    /**
     * All entries that depend on the given object are invalidated in the cache and XMLProducers are recomputed.
     *
     * @param p the object that has been updated
     */
    public synchronized void objectUpdated(Object p) {
        Object proxy = getObject(p);
        if (log.isDebugEnabled()) {
            log.debug("Object updated " + proxy);
        }
        CacheTransaction currentCacheTransaction = null;
        if (hasTransaction()) {
            currentCacheTransaction = getCurrentCacheTransaction();
        }

        if (currentCacheTransaction != null) {
            currentCacheTransaction.objectUpdated(proxy);
        } else {
            instantUpdateObject(p);
        }
    }

    public void pageRemovedFromCache(String url) {
        for (Iterator<Map.Entry<CacheObject, Set<Object>>> it = pageObjectMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry<CacheObject, Set<Object>> entry = it.next();
            CacheObject key = entry.getKey();
            if (url.equals(key.getUrl())) {
                Set<Object> value = entry.getValue();
                it.remove();
                for (Object o : value) {
                    Set<CacheObject> cacheObjects = objectPageMap.get(o);
                    if (cacheObjects != null) {
                        cacheObjects.remove(key);
                    }
                }
            }
        }
    }

    private synchronized boolean merge(CacheTransaction t) {
        synchronized (ThreadContext.getCache()) {
            //Propagate the changed object to the other threads
            synchronized (openTransactions) {
                for (Object p : t.getUpdatedObjects()) {
                    for (CacheTransaction tran : openTransactions) {
                        if (tran != t) {
                            tran.objectUpdatedInOtherTransaction(p);
                        }
                    }
                }
            }

            //Remove pages from the cache that depend on objects changed in this transaction
            for (Object p : t.getUpdatedObjects()) {
                instantUpdateObject(p);
            }

            //Finally update the dependence map to save the newly generated page.
            // First check that objects we depend on have not changed in concurrent threads.
            Set<Object> dirtyObjects = t.getDirtyObjects();
            if (!dirtyObjects.isEmpty()) {
                dirtyObjects.retainAll(objectPageMap.keySet());
                if (!dirtyObjects.isEmpty()) {
                    log.warn(String.format("Objects changed in other threads while generating the response: %s. This page will not be cached.", dirtyObjects));
                    return false;
                }
            }

            // Merge the object->page and the page->object maps with the transaction
            Map<Object, Set<CacheObject>> tObjectPageMap = t.getObjectPageMap();
            for (Map.Entry<Object, Set<CacheObject>> e : tObjectPageMap.entrySet()) {
                Set<CacheObject> cacheObjects = objectPageMap.get(e.getKey());
                if (cacheObjects != null) {
                    cacheObjects.addAll(e.getValue());
                } else {
                    objectPageMap.put(e.getKey(),new HashSet<CacheObject>(e.getValue()));
                }
            }
            Map<CacheObject, Set<Object>> tPageObjectMap = t.getPageObjectMap();
            for (Map.Entry<CacheObject, Set<Object>> e : tPageObjectMap.entrySet()) {
                Set<Object> cacheObjects = pageObjectMap.get(e.getKey());
                if (cacheObjects != null) {
                    cacheObjects.addAll(e.getValue());
                } else {
                    pageObjectMap.put(e.getKey(), new HashSet<Object>(e.getValue()));
                }
            }
            if (counter++ >= 100) { //Purge maps for each 100 cache transactions/requests
                purge(pageObjectMap);
                purge(objectPageMap);
                counter = 0;
            }
            return true;
        }
    }

    private void instantUpdateObject(Object p) {
        Set<CacheObject> urls = objectPageMap.get(p);
        if (urls != null) {
            urls = new HashSet<CacheObject>(urls);
            for (CacheObject url : urls) {
                Cache cache = ThreadContext.getCache();
                cache.remove(url.getUrl());
                log.debug("Url removed " + url.getUrl());
                ThreadContext.getSynchronizer().update(url.getUrl());
            }
        }
    }

    public boolean hasTransaction() {
        if (ThreadContext.isInRequestContext()) {
            ThreadContext threadContext = ThreadContext.get();
            return threadContext.getCurrentCacheTransaction() != null;
        } else {
            return false;
        }
    }

    public synchronized CacheTransaction getCurrentCacheTransaction() {
        CacheTransaction currentCacheTransaction = ThreadContext.get().getCurrentCacheTransaction();
        if (currentCacheTransaction == null) {
            throw new InconsistentDependencyException("Cache transaction is not open");
        }
        return currentCacheTransaction;
    }

    public void beginTransaction(boolean getMode) {
        if (hasTransaction()) {
            throw new InconsistentDependencyException("Cache transaction is already opened");
        } else {
            ThreadContext.get().setCurrentCacheTransaction(new CacheTransaction(getMode));
        }
    }

    /**
     * Merges the current cache transaction with the dependency map. Also checks of objects that
     * this page depends on have changed.
     * @return true if it is safe to cache the page
     */
    public boolean mergeTransaction(){
        return merge(getCurrentCacheTransaction());
    }

    public void removeTransaction() {
        ThreadContext.get().setCurrentCacheTransaction(null);
    }

    /**
     * If p is a persistable object, a proxy object is returned. Else the object itself is simply returned.
     */
    private Object getObject(Object p) {
        if (p instanceof Persistable) {
            Persistable persistable = (Persistable) p;
            if (persistable.getId() == null) {
                log.warn("Tried to set up a dependency with a non-persistent persistable", new Exception());
                return null;
            }
            return new ProxyObject(ThreadContext.getWebSite().getQuerier(), (Persistable) p);
        } else {
            return p;
        }
    }

    private <S, T> void purge(Map<S, Set<T>> map) {
        log.info("Purging dependency map");
        Set<S> deadKeys = new HashSet<S>();
        for (Map.Entry<S, Set<T>> e : map.entrySet()) {
            if (e.getValue().isEmpty()) {
                deadKeys.add(e.getKey());
            }
        }
        for (S key : deadKeys) {
            map.remove(key);
        }
        log.info("End purging dependency map");
    }

}
