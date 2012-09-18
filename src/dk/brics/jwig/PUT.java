package dk.brics.jwig;

import java.lang.annotation.*;

/**
 * Specifies that this method is invoked when a PUT request is sent from the client.
 * This overrides the default behaviour of the dispatcher.
 * The method may be annotated by more than one method, but methods carrying this annotation
 * should be idempotent meaning that multiple calls should have the same result as calling once.
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PUT {
}