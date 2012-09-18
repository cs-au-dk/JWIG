package dk.brics.jwig;

import javax.servlet.http.HttpServletResponse;

/**
 * Exception thrown by the web app when a ressource was not found.
 * Results in an HTTP 404 response to the client.
 */
public class NotFoundException extends JWIGException {
	
	/**
	 * Constructs a new exception.
	 * Uses the default message "The resource was not found".
	 */
    public NotFoundException() {
    	this("The resource was not found");
    }

	/**
	 * Constructs a new exception with the given message.
	 */
    public NotFoundException(String message) {
        super(message);
    }

    @Override
    public int getErrorCode() {
        return HttpServletResponse.SC_NOT_FOUND;
    }
}
