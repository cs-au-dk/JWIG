package dk.brics.jwig;

import javax.servlet.http.HttpServletResponse;

/**
 * Exception thrown when a client is denied access to a requested resource.
 */
public class AccessDeniedException extends JWIGException {

	/**
	 * Constructs a new exception.
	 */
	public AccessDeniedException() {
		super("Access denied");
	}

	/**
	 * Constructs a new exception.
	 */
	public AccessDeniedException(String msg) {
		super("Access denied (" + msg + ")");
	}

    @Override
    public int getErrorCode() {
        return HttpServletResponse.SC_FORBIDDEN;
    }
}
