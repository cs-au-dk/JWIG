package dk.brics.jwig;

import javax.servlet.http.HttpServletResponse;

/**
 * Exception thrown when trying to fetch a non-existing session from the session state manager.
 */
public class SessionDefunctException extends JWIGException {

    private String id;

    /**
	 * Constructs a new exception.
	 */
	public SessionDefunctException(String id) {
		super("Session ID not found: " + id + " (it might be expired)");
        this.id = id;
    }

    /**
     * Returns the ID of the defunct session.
     */
    public String getId() {
        return id;
    }

    @Override
    public int getErrorCode() {
        return HttpServletResponse.SC_GONE;
    }

    @Override
    public void setHeaders(HttpServletResponse r) {
        r.setHeader(WebContext.JWIG_DEFUNCT_SESSION, getId());
    }
}
