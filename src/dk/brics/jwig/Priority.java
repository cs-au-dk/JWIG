package dk.brics.jwig;

import java.lang.annotation.*;

/**
 * Priority for web methods.
 * <p>
 * When the URL of a GET request matches the URL patterns of multiple
 * web methods, then the methods are chained according to their priorities. 
 * <p>
 * If an explicit priority is omitted, a default is computed from the URL pattern.
 * For example, the patterns <code>foo/**</code>, <code>foo/$x</code>,
 * and <code>foo/bar</code> overlap, but the first has higher default priority than the second,
 * and the second has higher default priority than the third.
 * Also, methods with return type void have higher priority than the {@link WebContext#cache()} method.
 * @see URLPattern
 */
@Documented 
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Priority {
	
	/**
	 * Priority value.
	 */
	int value() default 0;
}
