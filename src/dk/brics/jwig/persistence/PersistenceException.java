package dk.brics.jwig.persistence;

import dk.brics.jwig.JWIGException;

/**
 * An exception indicating that there was an error in the persistence system.
 */
public class PersistenceException extends JWIGException {
    public PersistenceException() {
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(Throwable cause) {
        super(cause);
    }
}
