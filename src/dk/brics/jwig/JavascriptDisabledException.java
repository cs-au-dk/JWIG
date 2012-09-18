package dk.brics.jwig;

import javax.servlet.http.HttpServletResponse;

/**
 * Thrown if a feature requires the client to have JavaScript enabled but where it is disabled
 */
public class JavascriptDisabledException extends JWIGException {
	
    public JavascriptDisabledException() {
        super("Javascript disabled. Please enable JavaScript in your browser.");
    }

    @Override
    public int getErrorCode() {
        return HttpServletResponse.SC_BAD_REQUEST;
    }
}
