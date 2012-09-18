package dk.brics.jwig;

import dk.brics.jwig.util.QuotedString;
import dk.brics.xact.XML;

import javax.servlet.http.HttpServletResponse;

/**
 * Exception thrown when a client should resend the request with HTTP Basic
 * authentication information.
 */
public class AuthorizationRequiredException extends JWIGException {

    private final String realm;

    /**
     * Constructs a new exception.
     */
    public AuthorizationRequiredException(String realm) {
        super();
        this.realm = realm;
    }

    /**
     * Returns the realm string.
     */
    public String getRealm() {
        return realm;
    }

    @Override
    public void setHeaders(HttpServletResponse r) {
        r.setHeader("WWW-Authenticate", "Basic realm=" + QuotedString.encode(getRealm()));
    }

    @Override
    public int getErrorCode() {
        return HttpServletResponse.SC_UNAUTHORIZED;
    }

    @Override
    public XML getMessagePage() {
        return XML.parseTemplate("<h2>Authorization Required</h2>"
                                        + "<p>This server could not verify that you are authorized to access "
                                        + "the resource requested. You most likely supplied the wrong credentials "
                                        + "(such as, bad password).</p>");
    }
}
