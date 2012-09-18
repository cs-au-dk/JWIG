package dk.brics.jwig;

import java.lang.annotation.*;

/**
 * If a method is marked with augmented cache, the resulting responses are put in the cache
 * not only separated by URL values but also by the separation string constructed by
 * the {@link dk.brics.jwig.WebSite#getCacheAugmentationString()} method.
 * Normally when a page at the url www.example.com/page is put in the cache, the next person
 * who fetches the page will get the cached response. However when this annotation is used
 * each user on the system has his own cache entry.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AugmentedCache {
}
