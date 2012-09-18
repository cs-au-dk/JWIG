package dk.brics.jwig;

import javax.servlet.http.HttpServletResponse;

/**
 * Indicates that the request could not be processed because of conflict in the request, such as an edit conflict.
 * Results in an HTTP 409 response code to the client
 */
public class ConflictException extends JWIGException{
    /**
	 * Constructs a new exception.
	 * Uses the default message "Conflict".
	 */
    public ConflictException() {
    	this("Conflict");
    }

	/**
	 * Constructs a new exception with the given message.
	 */
    public ConflictException(String message) {
        super(message);
    }

    @Override
    public int getErrorCode() {
        return HttpServletResponse.SC_CONFLICT;
    }
}
