package dk.brics.jwig.persistence.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * Access scope can be used to allow access to querying objects from the database as a replacement of
 * access methods in simple (but common) case where access to an object should be granted given that
 * access is allowed one of the properties of the properties of an object.
 * <p>
 * Consider the example where the class <code>A</code> has a property accessor <code>getB</code> that
 * returns a value of type <code>B</code>. If <code>getB</code> is annotated with {@link dk.brics.jwig.persistence.security.AccessScope}
 * then access to an object of type <code>A</code> is allowed if access is allowed to the object of type
 * <code>B</code> returned by <code>getB</code>.
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessScope {
}
