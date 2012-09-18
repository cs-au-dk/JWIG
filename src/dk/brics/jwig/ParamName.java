package dk.brics.jwig;

import java.lang.annotation.*;

/**
 * Annotation for parameters of web methods.
 * <p>
 * To make the parameter names of web methods accessible by reflection at runtime, either
 * this annotation must be used on each parameter or the code must be compiled with debug info (option <code>-g</code>).
 * @see WebApp
 */
@Documented 
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ParamName {
	
	/**
	 * Parameter name.
	 */  
	String value();
}
