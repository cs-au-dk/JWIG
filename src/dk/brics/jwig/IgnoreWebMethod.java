package dk.brics.jwig;

import java.lang.annotation.*;

/**
 * Instructs the JWIG introspector to ignore a method tha would otherwise be considered a web method
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreWebMethod {
}
