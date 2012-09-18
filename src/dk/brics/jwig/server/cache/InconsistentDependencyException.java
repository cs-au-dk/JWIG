package dk.brics.jwig.server.cache;

import dk.brics.jwig.JWIGException;

/**
 * Thrown if there is a consistency problem in the way a generated page uses the cache. That may for instance be a page
 * that depends on an object it changes during the request
 */
public class InconsistentDependencyException extends JWIGException{
    public InconsistentDependencyException() {
        super();
    }

    public InconsistentDependencyException(Throwable cause) {
        super(cause);
    }

    public InconsistentDependencyException(String message) {
        super(message);
    }

    public InconsistentDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
