package dk.brics.jwig.server.cache;

import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cache transaction contains all the state relevant to caching of a resource such that the changes to the global cache
 * can happen atomically.
 */
public class CacheTransaction {
    private static Logger log = Logger.getLogger(CacheTransaction.class);

    private Map<Object, Set<CacheObject>> objectPageMap = new ConcurrentHashMap<Object, Set<CacheObject>>();

    private Map<CacheObject, Set<Object>> pageObjectMap = new ConcurrentHashMap<CacheObject, Set<Object>>();

    private Set<Object> updatedObjects = new HashSet<Object>();

    /**
     * Dirty objects are objects that have been changed in another transaction while this transaction was running
     */
    private Set<Object> dirtyObjects = Collections.synchronizedSet(new HashSet<Object>());

    private boolean getMode;

    public CacheTransaction(boolean getMode) {
        this.getMode = getMode;
    }

    public synchronized void addDependency(CacheObject c, Object object) {
        getSet(object, objectPageMap).add(c);
        getSet(c, pageObjectMap).add(object);
    }

    public synchronized void objectUpdated(Object p) {
        updatedObjects.add(p);
    }

    public void objectUpdatedInOtherTransaction(Object o) {
        dirtyObjects.add(o);
    }

    public Map<Object, Set<CacheObject>> getObjectPageMap() {
        return objectPageMap;
    }

    public Map<CacheObject, Set<Object>> getPageObjectMap() {
        return pageObjectMap;
    }

    public Set<Object> getUpdatedObjects() {
        return updatedObjects;
    }

    public Set<Object> getDirtyObjects() {
        return dirtyObjects;
    }

    private <S, T> Set<T> getSet(S p, Map<S, Set<T>> map) {
        Set<T> setOfT = map.get(p);
        if (setOfT == null) {
            setOfT = new HashSet<T>();
            map.put(p, setOfT);
        }
        return setOfT;
    }

    public boolean isGetMode() {
        return getMode;
    }

}
