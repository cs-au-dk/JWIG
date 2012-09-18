package dk.brics.jwig;

/**
 * Marker interface that tells JWIG to serialize the marked session when the server is closed and to restore it again
 * when the server is up again. JWIG will call the {@link dk.brics.jwig.server.SessionManagerListener#sessionEnded(Session)}
 * method when persisting the session and call {@link dk.brics.jwig.server.SessionManagerListener#sessionCreated(Session)}
 * when the session is read back from the disk at next server start
 */
public interface SerializableSession {
}
