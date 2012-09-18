package dk.brics.jwig.server;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Payload for a response.
 */
public interface Payload {

	/**
	 * Writes the payload.
	 */
	void write(HttpServletRequest request, HttpServletResponse response, 
			int status, String contenttype) throws IOException;

    /**
     * Returns the value of the payload.
     */
    public Object getValue();
}
