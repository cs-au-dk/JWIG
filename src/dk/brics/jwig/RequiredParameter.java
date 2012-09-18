package dk.brics.jwig;

import java.lang.annotation.*;

/**
 * A required argument means that the web method will never be run if this argument is missing in the request.
 */
@Documented
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredParameter {
}
