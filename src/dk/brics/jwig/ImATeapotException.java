package dk.brics.jwig;

/**
 * Returns a 418 response code to the client. According to RFC 2324:
 * <blockquote>
 * Any attempt to brew coffee with a teapot should result in the error
 *  code "418 I'm a teapot". The resulting entity body MAY be short and
 *  stout.
 * </blockquote>
 */
public class ImATeapotException extends JWIGException{
    /**
	 * Constructs a new exception.
	 * Uses the default message "I'm a teapot".
	 */
    public ImATeapotException() {
    	this("I'm a teapot");
    }

	/**
	 * Constructs a new exception with the given message.
	 */
    public ImATeapotException(String message) {
        super(message);
    }

    @Override
    public int getErrorCode() {
        return 418;
    }
}
