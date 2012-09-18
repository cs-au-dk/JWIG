package dk.brics.jwig;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * URL patterns for <code>WebApp</code> classes and web methods.
 * <p>
 * The default URL pattern for a <code>WebApp</code> class is the fully qualified class name using '/' as separator.
 * The default URL pattern for a web method is the method name. 
 * <p>
 * Syntax for URL patterns:
 * <p><table>
 * <tr><td><i>pattern</i><td>::=<td><i>choice</i> ( <tt>|</tt> <i>choice</i> )*
 * <tr><td><i>choice</i><td>::=<td>( <i>part</i> ( <tt>/</tt> <i>part</i> )* )?
 * <tr><td><i>part<td>::=<td><i>const</i> | <tt>$</tt><i>param</i> | <tt>*</tt> | <tt>**</tt>   
 * </table><p>
 * Each <i>const</i> and <i>param</i> is a nonempty part string that does not contain <tt>/</tt>, <tt>|</tt>, <tt>$</tt>, <tt>*</tt>.
 * The <tt>$</tt><i>param</i> pattern is an alternative to query string arguments (the two forms can be combined
 * for parameters with different names). 
 * The <tt>*</tt> pattern matches any nonempty part string.
 * The <tt>**</tt> pattern matches any pattern suffix and can only be used as the last part of a choice.
 * Web app URL patterns cannot use <tt>*</tt> and <tt>**</tt>.
 * @see Priority
 */ 
@Documented 
@Retention(RetentionPolicy.RUNTIME)
public @interface URLPattern { // TODO: rethink pattern syntax?
	
	/**
	 * URL pattern.
	 */  
	String value();
}
