package dk.brics.jwig.persistence;

import dk.brics.jwig.Session;

/**
 * A DBSession serves the same purpose as a {@link Session} and provides no new members. However,
 * a DBSession is treated differently from a session by JWIG in that a DBSession is bound to the
 * current database session such that result sets and objects are live as long as the session
 * exists.
 * <p>
 * <b>Use with caution:</b> Depending on the database implementation, each such session may block a
 * database connection and the number of such connections may be limited in the configuration.
 */
public class DBSession extends Session {
}
