package dk.brics.jwig.persistence;

import dk.brics.jwig.BadRequestException;
import dk.brics.jwig.server.DispatchListener;
import dk.brics.jwig.server.SessionManagerListener;
import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Transaction;
import org.hibernate.classic.Session;
import org.hibernate.context.CurrentSessionContext;
import org.hibernate.engine.SessionFactoryImplementor;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * A session context class for Hibernate that binds the life cycle of a persistent
 * object to that of a session.
 */
public class JwigCurrentSessionContext implements CurrentSessionContext, SessionManagerListener, DispatchListener {
    protected SessionFactoryImplementor factoryImplementor;
    private boolean autoCloseEnabled = false, autoFlushEnabled = true;

    private static JwigCurrentSessionContext currentSessionContext;

    private Map<DBContext, SessionProxy> contextMap = new HashMap<DBContext, SessionProxy>();
    private Map<dk.brics.jwig.Session, DBContext> sessionContextMap = new HashMap<dk.brics.jwig.Session, DBContext>();
    /**
     * Reverse map to avoid linear time lookup in session context map
     */
    private Map<DBContext, dk.brics.jwig.Session> reverseSessionContextMap = new HashMap<DBContext, dk.brics.jwig.Session>();
    private Map<Thread, DBContext> threadContextMap = new HashMap<Thread, DBContext>();

    private Logger log = Logger.getLogger(JwigCurrentSessionContext.class);


    /**
     * Gets the object that is the current session context.
     */
    public static JwigCurrentSessionContext getCurrentSessionContext() {
        return currentSessionContext;
    }

    public JwigCurrentSessionContext(SessionFactoryImplementor factoryImplementor) {
        this.factoryImplementor = factoryImplementor;
        if (currentSessionContext == null) {
            log.info("Created jwig hibernate session context");
        } else {
            log.warn("Created another jwig hibernate session context");
        }
        currentSessionContext = this;
        for (JwigCurrentSessionContextProxy.ConstructorCallBack c : JwigCurrentSessionContextProxy.cs) {
            c.call(this);
        }
        JwigCurrentSessionContextProxy.instanciated();
    }

    @Override
	public Session currentSession() throws HibernateException {
        return contextMap.get(getCurrentContext()).getS();
    }

    /**
     * Returns whether or not the the session should be closed by transaction completion.
     */
    public boolean isAutoCloseEnabled() {
        return autoCloseEnabled;
    }

    /**
     * Sets whether or not the the session should be closed by transaction completion. Will only be set for
     * Hibernate sessions created from this point.
     */
    public void setAutoCloseEnabled(boolean autoCloseEnabled) {
        this.autoCloseEnabled = autoCloseEnabled;
    }

    /**
     * Returns whether or not the the session should be flushed prior to transaction completion.
     */

    public boolean isAutoFlushEnabled() {
        return autoFlushEnabled;
    }

    /**
     * Sets whether or not the the session should be flushed prior to transaction completion. Will only be set for
     * Hibernate sessions created from this point.
     */
    public void setAutoFlushEnabled(boolean autoFlushEnabled) {
        this.autoFlushEnabled = autoFlushEnabled;
    }

    @Override
	public synchronized void sessionCreated(dk.brics.jwig.Session s) {
        if (s instanceof DBSession) {
            DBContext context = threadContextMap.get(Thread.currentThread());
            sessionContextMap.put(s, context);
            reverseSessionContextMap.put(context, s);
        }
    }

    @Override
	public synchronized void sessionEnded(dk.brics.jwig.Session s) {
        if (s instanceof DBSession) {
            DBContext dbContext = sessionContextMap.get(s);
            threadContextMap.remove(dbContext.getActiveThread());
            contextMap.remove(dbContext).getS().close();
            sessionContextMap.remove(s);
            reverseSessionContextMap.remove(dbContext);
        }
    }

    @Override
	public synchronized void sessionTimeout(dk.brics.jwig.Session s) {
        if (s instanceof DBSession) {
            DBContext dbContext = sessionContextMap.get(s);
            threadContextMap.remove(dbContext.getActiveThread());
            contextMap.remove(dbContext).getS().close();
            sessionContextMap.remove(s);
            reverseSessionContextMap.remove(dbContext);
        }
    }

    private DBContext getCurrentContext() {
        return threadContextMap.get(Thread.currentThread());
    }

    @Override
	public synchronized void sessionRefreshed(dk.brics.jwig.Session s) {
        if (s instanceof DBSession) {
            DBContext dbContext = sessionContextMap.get(s);
            if (dbContext == null) {
                log.warn("dbContext is null for session " + s.getID() + " with timeout " + new Date(s.getTimeout()), new Exception());
                throw new BadRequestException("Session timeout");
            }
            threadContextMap.remove(dbContext.getActiveThread());
            Thread activeThread = Thread.currentThread();
            dbContext.setActiveThread(activeThread);
            threadContextMap.put(activeThread, dbContext);
        }
    }

    @Override
	public synchronized void threadDispatched(Thread t) {
        DBContext context = new DBContext();
        context.setActiveThread(t);
        threadContextMap.put(t, context);
        contextMap.put(context, new SessionProxy());
    }

    @Override
	public synchronized void threadDismissed(Thread t) {
        Session session = currentSession();
        Transaction transaction = session.getTransaction();
        if (transaction != null && transaction.isActive()) {
            transaction.rollback();
        }
        DBContext dbContext = threadContextMap.get(t);
        if (!reverseSessionContextMap.containsKey(dbContext)) {  //Not bound by a session
            JwigCurrentSessionContext.SessionProxy sessionProxy = contextMap.get(dbContext);
            if (sessionProxy.isOpened()) {
                Session s = sessionProxy.getS();
                if (s.isOpen()) {
                    s.close();
                }
            }
            contextMap.remove(dbContext);
            threadContextMap.remove(t);
        }
    }

    /**
     * A session is a relatively expensive object to initialize. Therefore we want to do it lazily so that
     * the object is not created before it is needed in the program that uses the session. The session proxy
     * handles this.
     */
    private class SessionProxy {
        private Session s;
        private boolean opened;

        public synchronized Session getS() {
            if (s == null) {
                opened = true;
                return s = factoryImplementor.openSession(
                        null,
                        isAutoFlushEnabled(),
                        isAutoCloseEnabled(),
                        factoryImplementor.getSettings().getConnectionReleaseMode()
                );
            }
            return s;
        }

        public boolean isOpened() {
            return opened;
        }
    }
}
