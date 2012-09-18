package dk.brics.jwig;

import java.io.Serializable;
import java.util.Date;

import dk.brics.jwig.server.Config;
import dk.brics.jwig.server.SessionManager;
import dk.brics.jwig.server.ThreadContext;
import dk.brics.jwig.util.RandomString;

/**
 * Session state.
 * <p>
 * Applications make subclasses of this class for encapsulating session state
 * and identifying it by a session ID.
 */
public class Session implements Serializable{
	
	private final String id;
	
	private final int minutes_to_live;
	
	private volatile long timeout;
	
	/**
	 * Constructs a new session state object for the current response.
	 * Timeout is by default set to now + 24 hours (i.e. minutes-to-live is 1440).
	 */
	public Session() {
		this(Config.get("jwig.session_timeout", 60*24));
	}
	
	/**
	 * Constructs a new session state object for the current response.
	 */
	public Session(int minutes_to_live) {
		if (minutes_to_live < 3)
			minutes_to_live = 3;
		this.minutes_to_live = minutes_to_live;
		id = RandomString.get(10);
		updateTimeout();
		ThreadContext.getSessionManager().store(this);
		ThreadContext.get().getResponse().addSession(this);
	}
	
	/**
	 * Returns the minutes-to-live (from when last refreshed).
	 */
	public int getMinutes() {
		return minutes_to_live;
	}
	
	/**
	 * Refreshes this session state object.
	 * Sets the timeout to now + minutes-to-live.
	 * This method is invoked automatically if a client is viewing 
	 * a page that involves the session, if possible.
	 */
	public void refresh() {
		SessionManager m = ThreadContext.getSessionManager();
		synchronized (m) {
			m.refreshBefore(this);
			updateTimeout();
			m.refreshAfter(this);
		}
	}

	private void updateTimeout() {
		timeout = new Date().getTime() + minutes_to_live*60000L;
	}
	
	/**
	 * Returns the current timeout for this session state object.
	 * The timeout is extended automatically if a client is viewing 
	 * a page that involves the session, if possible.
	 */
	public final long getTimeout() {
		return timeout;
	}
	
	/**
	 * Returns the ID of this session state object.
	 */
	public final String getID() {
		return id;
	}
	
	/**
	 * Returns the ID of this session state object.
	 */
	@Override
	public String toString() {
		return id;
	}
	
	/**
	 * Refreshes and returns the session object of the given ID via the session state manager.
	 * @throws SessionDefunctException if the session does not exist
	 */
	public static Session valueOf(String id) throws SessionDefunctException {
		Session s = ThreadContext.getSessionManager().get(id);
		s.refresh();
		ThreadContext.get().getResponse().addSession(s);
        WebContext.addResponseInvalidator(s);
		return s;
	}
	
	/**
	 * Ends this session and informs listeners.
	 */
	public void end() {
		ThreadContext.getSessionManager().endSession(this);
	}
	
	/**
	 * Invoked by the session manager on timeout.
	 * The default implementation does nothing.
	 */
	public void destroy() {}
}
