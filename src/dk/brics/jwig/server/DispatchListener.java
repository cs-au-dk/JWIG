package dk.brics.jwig.server;

/**
 * Listener for dispatch events.
 */
public interface DispatchListener {

	/**
     * Called when a new thread enters the JWIG framework through the dispatcher.
     */
    public void threadDispatched(ThreadDispatchEvent t);

    /**
     * Called when a thread leaves JWIG. 
     * {@link #threadDispatched(ThreadDispatchEvent)} can be assumed to have been called with the thread
     * before this method is called
     */
    public void threadDismissed(ThreadDispatchEvent t);

    /**
     * Called before immediately before a web method is called by the dispatcher.
     * @param e
     */
    public void webMethodDispatched(WebMethodDispatchEvent e);
}
