package dk.brics.jwig.server.cache;

import dk.brics.jwig.AbstractHandler;
import dk.brics.jwig.Response;
import dk.brics.jwig.WebApp;
import dk.brics.jwig.server.Config;
import dk.brics.jwig.server.ThreadContext;
import org.apache.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cache for {@link Response} objects. Uses a basic HashMap for caching. Not for production usage.
 */
public class HashMapCache extends AbstractCache {

    private final Logger log = Logger.getLogger(HashMapCache.class);

    private final LinkedHashMap<String, CachedResponse> memorycache; // using LinkedHashMap to ensure FIFO behavior

    private final int cache_max_pages;

    /**
     * Constructs a new cache.
     */
    public HashMapCache() {
        memorycache = new LinkedHashMap<String, CachedResponse>();
        cache_max_pages = Config.get("jwig.cache_max_pages", 25);
        log.info("Initializing {jwig.cache_max_pages=" + cache_max_pages + "}");
    }

    @Override
	synchronized public void destroy() {
        memorycache.clear();
        log.info("Stopped");
    }

    @Override
	synchronized public Response get(String url) {
        CachedResponse cachedResponse = memorycache.get(url);
        if (cachedResponse != null) {
            if (ThreadContext.isInRequestContext() && cachedResponse.origin != WebApp.get())
                return null;
            log.debug("Touching cached " + describe(cachedResponse.response, url));
            memorycache.remove(url);
            memorycache.put(url, cachedResponse);
        } else
            return null;
        return cachedResponse.response;
    }

    @Override
	synchronized public void put(String url, Response r) {
        while (memorycache.size() >= cache_max_pages) {
            Map.Entry<String, CachedResponse> e = memorycache.entrySet().iterator().next();
            memorycache.remove(e.getKey());
            log.info("Cache limit reached, removing cached " + describe(r, url));
            ThreadContext.getDependencyMap().pageRemovedFromCache(e.getKey());
        }
        if (memorycache.containsKey(url)) {
            remove(url);
        }
        log.info("Caching " + describe(r, url));
        CachedResponse c = new CachedResponse();
        c.response = r;
        c.origin = WebApp.get();
        memorycache.put(url, c);
    }

    @Override
	synchronized public void remove(String url) {
        CachedResponse c = memorycache.remove(url);
        ThreadContext.getDependencyMap().pageRemovedFromCache(url);
        if (c != null) {
            Response p = c.response;
            log.info("Removing cached " + describe(p, url));
            for (AbstractHandler h : p.getHandlers()) {
                h.destroy();
            }
        }
    }

    private String describe(Response p, String url) {
        return "response " + url + " (LastModified=" + p.getLastModified() + ", ETag=" + p.getETag() + "), #cached=" + memorycache.size();
    }

    /**
     * The cached response is stored with the origin web app to ensure that the cached response is only returned by the cache
     * in the web app it was created by.
     */
    private class CachedResponse {
        Response response;
        WebApp origin;
    }
}
