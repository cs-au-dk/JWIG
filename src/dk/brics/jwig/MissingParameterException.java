package dk.brics.jwig;

import javax.servlet.http.HttpServletResponse;

/**
 * Thrown when a parameter is missing from a request where the parameter is 
 * marked with the {@link RequiredParameter} annotation.
 */
public class MissingParameterException extends JWIGException {

	public MissingParameterException(String name) {
        super("Required argument '" + name + "' was not given");
    }

    @Override
    public int getErrorCode() {
        return HttpServletResponse.SC_BAD_REQUEST;
    }
}
