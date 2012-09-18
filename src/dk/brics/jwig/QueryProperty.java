package dk.brics.jwig;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Indicates that the given property is used to represent the object in URLs instead
 * of the database ID. The method must return a unique key for the object given
 * its type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QueryProperty {
}
