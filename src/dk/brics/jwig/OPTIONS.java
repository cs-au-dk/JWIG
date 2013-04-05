package dk.brics.jwig;

import java.lang.annotation.*;

/**
 * Specifies that this method is invoked when a OPTIONS request is sent from the client.
 * This overrides the default behaviour of the dispatcher.
 * The method may be annotated by more than one method.
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface OPTIONS {
}
