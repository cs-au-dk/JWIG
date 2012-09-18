package dk.brics.jwig.server;

/**
 * Listener for dispatch events.
 */
public interface DispatchListener {

	/**
     * Called when a new thread enters the JWIG framework through the dispatcher.
     */
    public void threadDispatched(Thread t);

    /**
     * Called when a thread leaves JWIG. 
     * {@link #threadDispatched(Thread)} can be assumed to have been called with the thread
     * before this method is called
     */
    public void threadDismissed(Thread t);
}
