package dk.brics.jwig.server.comet;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Interface for Comet events.
 * Corresponds to the Comet events of e.g. Tomcat or Resin.
 */
public interface CometEvent {
	
	/**
	 * Returns the event type.
	 */
    public CometEventType getEventType();

    /**
     * Sets the timeout on the connection.
     */
    public void setTimeout(int timeout) throws IOException, ServletException;
}