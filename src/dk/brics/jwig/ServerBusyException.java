package dk.brics.jwig;

import dk.brics.xact.XML;

import javax.servlet.http.HttpServletResponse;

/**
 * Exception thrown when the server is overloaded.
 * Thrown e.g. by the session manager. Results in a 503 response code to the client
 */
public class ServerBusyException extends JWIGException {
	
	/**
	 * Constructs a new exception.
	 */
	public ServerBusyException(String msg) {
		super("Server load limit reached (" + msg + ")");
	}

    @Override
    public void setHeaders(HttpServletResponse r) {
        r.setHeader("Retry-After", "600");
    }

    @Override
    public XML getMessagePage() {
        return XML.parseTemplate("<p><[MSG]></p>").plug("MSG", getMessage() + ". Please try again later.");
    }

    @Override
    public int getErrorCode() {
        return HttpServletResponse.SC_SERVICE_UNAVAILABLE;
    }
}
