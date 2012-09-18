package dk.brics.jwig.server;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import dk.brics.jwig.SerializableSession;
import org.apache.log4j.Logger;

import dk.brics.jwig.ServerBusyException;
import dk.brics.jwig.Session;
import dk.brics.jwig.SessionDefunctException;
import dk.brics.jwig.persistence.JwigCurrentSessionContext;
import dk.brics.jwig.persistence.JwigCurrentSessionContextProxy;

import javax.servlet.ServletContext;

/**
 * Session state manager.
 */
public final class SessionManager {

    private final Logger log = Logger.getLogger(SessionManager.class);

    private final TreeSet<Session> sessionset;

    private final ConcurrentHashMap<String, Session> sessionmap;

    private final GarbageCollectorThread collector;

    private final List<SessionManagerListener> listeners;

    private final int max_sessions;

    private final String persistentSessionsFileName;

    /**
     * Constructs a new session manager.
     */
    public SessionManager(ServletContext context) {
        persistentSessionsFileName = context.getContextPath().replace("/", "") + "_persistentSessions.jwig";
        sessionset = new TreeSet<Session>(new Comparator<Session>() {
            @Override
			public int compare(Session s1, Session s2) {
                if (s1.getTimeout() < s2.getTimeout()) {
                    return -1;
                } else if (s1.getTimeout() > s2.getTimeout()) {
                    return 1;
                } else {
                    return s1.getID().compareTo(s2.getID());
                }
            }
        });
        sessionmap = new ConcurrentHashMap<String, Session>();
        listeners = new LinkedList<SessionManagerListener>();
        collector = new GarbageCollectorThread();
        collector.setDaemon(true);
        collector.setName("SessionManager.GarbageCollector");
        collector.start();
        max_sessions = Config.get("jwig.max_sessions", 1000);
        log.info("Initializing {jwig.max_sessions=" + max_sessions + "}");
        JwigCurrentSessionContextProxy.registerCallBack(new JwigCurrentSessionContextProxy.ConstructorCallBack() {
            @Override
			public void call(JwigCurrentSessionContext sessionContext) {
                addListener(sessionContext);
            }
        });
        try {
            File file = new File(persistentSessionsFileName);
            if (file.exists()) {
                ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(file));
                PersistentSessions persistentSessions = (PersistentSessions) objectInputStream.readObject();
                for (Session s : persistentSessions.sessionSet) {
                    store(s);
                }
                objectInputStream.close();
                log.info("Sessions read from disk");
                file.delete();
            }
        } catch (Exception e) {
            log.error(e);
        }
    }

    /**
     * Stops this session manager.
     * Invokes {@link Session#destroy()} on all sessions and {@link SessionManagerListener#sessionTimeout(Session)}
     * on all their listeners.
     */
    synchronized public void destroy() {
        for (Session s : sessionset) {
            s.destroy();
            for (SessionManagerListener l : listeners) {
                l.sessionTimeout(s);
            }
        }
        PersistentSessions persistentSessions = new PersistentSessions();
        for (Session s : sessionset) {
            if (s instanceof SerializableSession) {
                persistentSessions.sessionSet.add(s);
            }
        }
        try {
            ObjectOutputStream objectOut = new ObjectOutputStream(new FileOutputStream(persistentSessionsFileName));
            objectOut.writeObject(persistentSessions);
            objectOut.close();
            log.info("Sessions saved to disk");
        } catch (IOException e) {
            log.error(e,e);
        }


        sessionset.clear();
        sessionmap.clear();
        collector.interrupt();
        try {
            collector.join();
        } catch (InterruptedException e) {
        	// ignore
        }
        log.info("Stopped");
    }

    /**
     * Stores the given session in the session manager.
     *
     * @throws ServerBusyException if <code>jwig.max_sessions</code> reached
     */
    synchronized public void store(Session s) throws ServerBusyException {
        if (sessionset.size() >= max_sessions) {
            throw new ServerBusyException("too many active sessions");
        }
        log.info("Storing session " + s.getID());
        sessionset.add(s);
        sessionmap.put(s.getID(), s);
        for (SessionManagerListener l : listeners) {
            l.sessionCreated(s);
        }
    }

    /**
     * Call before changing a session timeout.
     * Caller should synchronize.
     */
    public void refreshBefore(Session s) {
        log.info("Refreshing session " + s.getID());
        sessionset.remove(s);
    }

    /**
     * Call after changing a session timeout.
     * Caller should synchronize.
     */
    public void refreshAfter(Session s) {
        sessionset.add(s);
        for (SessionManagerListener l : listeners) {
            l.sessionRefreshed(s);
        }
    }

    /**
     * Removes the given session from the session manager.
     * Does not inform listeners.
     */
    synchronized public void remove(Session s) {
        log.info("Removing session " + s.getID());
        sessionset.remove(s);
        sessionmap.remove(s.getID());
    }

    /**
     * Ends the given session and informs listeners.
     */
    synchronized public void endSession(Session s) {
        remove(s);
        for (SessionManagerListener l : listeners) {
            l.sessionEnded(s);
        }
    }

    /**
     * Finds the session of the given ID.
     *
     * @throws SessionDefunctException if the session does not exist
     */
    public Session get(String id) throws SessionDefunctException {
        Session s = sessionmap.get(id);
        if (s == null) {
            throw new SessionDefunctException(id);
        }
        return s;
    }

    /**
     * Adds a session manager listener.
     */
    public void addListener(SessionManagerListener listener) { // TODO: make SessionManagerListener stuff visible in public interface?
        listeners.add(listener);
    }

    /**
     * Session garbage collector.
     * Removes expired sessions once each minute.
     */
    private class GarbageCollectorThread extends Thread {

        @SuppressWarnings("hiding")
        private final Logger log = Logger.getLogger(GarbageCollectorThread.class);

        @Override
        public void run() {
            log.info("Initializing");
            while (!interrupted()) {
                try {
                    sleep(60000); // = 1 minute
                } catch (InterruptedException e) {
                    break;
                }
                synchronized (SessionManager.this) {
                    long now = new Date().getTime();
                    while (!sessionset.isEmpty() && sessionset.first().getTimeout() < now) {
                        Session s = sessionset.first();
                        remove(s);
                        s.destroy();
                        for (SessionManagerListener l : listeners) {
                            l.sessionTimeout(s);
                        }
                    }
                }
            }
            log.info("Stopped");
        }
    }

    public static class PersistentSessions implements Serializable {
        private HashSet<Session> sessionSet = new HashSet<Session>();
    }
}
