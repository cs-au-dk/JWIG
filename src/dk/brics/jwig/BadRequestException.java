package dk.brics.jwig;

import javax.servlet.http.HttpServletResponse;

/**
 * Exception thrown when an HTTP request contains errors. Results in an
 * HTTP 400 response code to the client.
 */
public class BadRequestException extends JWIGException {

    /**
     * Constructs a new exception.
     */
    public BadRequestException(String msg) {
        this(msg, null);
    }

    /**
     * Constructs a new exception.
     */
    public BadRequestException(String message, Throwable cause) {
        super("Bad request (" + message + ")", cause);
    }

    @Override
    public int getErrorCode() {
        return HttpServletResponse.SC_BAD_REQUEST;
    }
}
