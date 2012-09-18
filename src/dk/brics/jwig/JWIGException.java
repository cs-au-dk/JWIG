package dk.brics.jwig;

import dk.brics.xact.XML;

import javax.servlet.http.HttpServletResponse;

/**
 * JWIG related runtime exception.
 */
public class JWIGException extends RuntimeException {
    /**
     * @see #setErrorPage(dk.brics.xact.XML)
     */
    private XML xml;

    /**
     * Constructs a new exception.
     */
    public JWIGException() {
        super();
    }

    /**
     * Constructs a new exception with a message string.
     */
    public JWIGException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with a message string and a
     * <code>Throwable</code>.
     */
    public JWIGException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new exception with a <code>Throwable</code>.
     */
    public JWIGException(Throwable cause) {
        super(cause);
    }

    /**
     * Sets the error page that is shown to the user.
     *
     * This replaces the default rendering of this exception type.
     */
    public void setErrorPage(XML errorPage) {
        this.xml = errorPage;
    }

    /**
     * The XML to display to the user.
     * 
     * @see #setErrorPage(dk.brics.xact.XML)
     */
    public XML getXml() {
        return xml;
    }

    public String getLogInfo() {
        return getClass().getSimpleName() + ": " + getMessage();
    }

    public int getErrorCode() {
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    public void setHeaders(HttpServletResponse r) {

    }

    /**
     * Returns the default rendering of this exception type. May be overridden in sub classes to specify another default rendering.
     * The returned XML object will be sent to the user unless another XML object is set using {@link #setErrorPage(dk.brics.xact.XML)}.
     */
    protected XML getMessagePage() {
        return XML.parseTemplate("<p><[MSG]></p>").plug("MSG", getMessage() + ".");
    }
}
