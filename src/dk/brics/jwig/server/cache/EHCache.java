package dk.brics.jwig.server.cache;

import dk.brics.jwig.Response;
import dk.brics.jwig.WebApp;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.util.ClassLoaderUtil;
import org.apache.log4j.Logger;

import java.io.InputStream;
import java.util.List;

/**
 * A Cache that uses EHCache as the provider.
 */
public class EHCache extends AbstractCache {
    private CacheManager manager;
    private static Logger log = Logger.getLogger(EHCache.class);

    public EHCache(List<WebApp> webApps, InputStream configuration) {
        manager = new CacheManager(configuration);
        for (WebApp w : webApps) {
            String className = w.getClass().getName();
            net.sf.ehcache.Cache cache = manager.getCache(className);
            if (cache == null) {
                log.warn("Could not find cache " + className + ". Setting a cache up with default values");
                manager.addCache(className);
            }
        }
    }

    public EHCache(List<WebApp> webApps) {
        this(webApps, ClassLoaderUtil.getStandardClassLoader().getResourceAsStream("ehcache-jwig.xml"));
    }

    @Override
	public void destroy() {
        manager.shutdown();
    }

    @Override
	public Response get(String url) {
        net.sf.ehcache.Cache cache = manager.getCache(WebApp.get().getClass().getName());
        Element element = cache.get(url);
        return element == null ? null : (Response) element.getObjectValue();
    }

    @Override
	public void put(String url, Response r) {
        net.sf.ehcache.Cache cache = manager.getCache(WebApp.get().getClass().getName());
        cache.put(new Element(url, r));
    }

    @Override
	public void remove(String url) {
        String[] strings = manager.getCacheNames();
        for (String s : strings) {
            net.sf.ehcache.Cache cache = manager.getCache(s);
            cache.remove(url);
        }
    }

}
