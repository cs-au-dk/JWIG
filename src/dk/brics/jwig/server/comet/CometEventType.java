package dk.brics.jwig.server.comet;

/**
 * Comet event types.
 */
public enum CometEventType {
    
	/**
	 * Connection established.
	 */
	BEGIN, 
    
	/**
	 * Connection lost.
	 */
    ERROR, 
    
    /**
     * Connection terminated.
     */
    END, 
    
    /**
     * Read from connection.
     */
    READ
}
