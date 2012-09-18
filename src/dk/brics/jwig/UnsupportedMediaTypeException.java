package dk.brics.jwig;

/**
 * The request entity has a media type which the server or resource does not support. For example the client uploads an image as image/svg+xml, but the server requires that images use a different format.
 * Results in an HTTP 415 status code to the client.
 */
public class UnsupportedMediaTypeException extends JWIGException{
    /**
	 * Constructs a new exception.
	 * Uses the default message "Unsupported media type".
	 */
    public UnsupportedMediaTypeException() {
    	this("Unsupported media type");
    }

	/**
	 * Constructs a new exception with the given message.
	 */
    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
