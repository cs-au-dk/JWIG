package dk.brics.jwig.server;

import dk.brics.jwig.Session;

/**
 * Listener for session manager events.
 */
public interface SessionManagerListener {

    /**
     * Invoked by the session manager when a session is created.
     */
    public void sessionCreated(Session s);

    /**
     * Invoked by the session manager when a session is ended in a normal way (not by timeout).
     *
     * @see Session#end()
     */
    public void sessionEnded(Session s);

    /**
     * Invoked by the session manager when a session is ended by a timeout.
     */
    public void sessionTimeout(Session s);

    /**
     * Invoked by the session manager after a session is refreshed.
     */
    public void sessionRefreshed(Session s);
}
