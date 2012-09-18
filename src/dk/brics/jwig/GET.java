package dk.brics.jwig;

import java.lang.annotation.*;

/**
 * Specifies that this method is invoked when a GET request is sent from the client.
 * This overrides the default behaviour of the dispatcher.
 * The method may be annotated by more than one method, but the method should respect that
 * GET requests may not have visible side effects when this annotations is present.
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface GET {
}
