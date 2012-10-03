package dk.brics.jwig.persistence;

import dk.brics.jwig.AccessDeniedException;
import dk.brics.jwig.WebApp;
import dk.brics.jwig.server.ThreadContext;
import dk.brics.jwig.server.cache.CacheInterceptor;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.proxy.HibernateProxyHelper;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;

import java.util.Properties;

/**
 * A {@link dk.brics.jwig.persistence.Querier} for Hibernate connection to the database.
 * The querier should first be initialized with the init method, then persistent classes should
 * be added to the configuration and finally the {@link #buildSessionFactory()} method
 * should be called. The configuration should be set in the jwig.properties file. Specifically
 * the three properties hibernate.connection.username, hibernate.connection.password, and
 * hibernate.connection.url should be set for jwig to be able to connect to the database.
 * If all persistent classes are added in the {@link dk.brics.jwig.WebApp} constructors, JWIG will
 * handle calling the appropriate init methods.
 */
public class HibernateQuerier implements Querier {

    private static Configuration configuration;

    private static SessionFactory sessionFactory;

    private boolean noNull;


    /**
     * Creates configuration and reads the hibernate properties from jwig.properties in the webapp root folder.
     */
    public static void init(Properties ps) {
        Properties filteredProps = new Properties();
        for (Object o : ps.keySet()) {
            String key = (String) o;
            if (key.startsWith("hibernate.") && ps.get(key) != null) {
                filteredProps.put(key, ps.get(key));
            }
        }
        configuration = new Configuration();
        configuration.addProperties(filteredProps);
    }

    /**
     * Build the session factory from the data in the configuration. This method should be called
     * when all persistent classes are added to the configuration.
     */
    public static void buildSessionFactory() {
        configuration.setInterceptor(new CacheInterceptor());
        ServiceRegistry serviceRegistry = new ServiceRegistryBuilder().applySettings(configuration.getProperties()).buildServiceRegistry();
        sessionFactory = configuration.buildSessionFactory(serviceRegistry);
    }

    /**
     * Closes the session factory and releases all resources. The HipernateQuerier
     * can be reconfigured and reused af this method has been called.
     * All hibernate sessions must be closed when this method is called
     */
    public static void destroy() {
        sessionFactory.close();
        //Allow heavy-weight objects to be garbage collected
        configuration = null;
        sessionFactory = null;
    }

    /**
     * Gets the session factory from the querier. This session factory can be used to
     * create new sessions but if session are created this way they must
     * be flushed and closed manually.
     */
    public static SessionFactory getFactory() {
        if (sessionFactory == null) {
            throw new PersistenceException("Hibernate not enabled, session factory has not been created");
        }
        return sessionFactory;
    }

    /**
     * Gets the current configuration for Hibernate that will be used to build the session factory.
     * All persistent classes should be added to this configuration before building the session factory.
     */
    public static Configuration getConfig() {
        if (configuration == null) {
            throw new PersistenceException("Hibernate not enabled, configuration has not been created");
        }
        return configuration;
    }

    @Override
	public <E extends Persistable> E getObject(Class<E> aClass, Integer id) throws NoSuchObjectException {
        return getObject(aClass, id, true);
    }

    @SuppressWarnings("unchecked")
    public <E extends Persistable> E getObject(Class<E> aClass, Integer id, boolean notify) {
        Session session = getFactory().getCurrentSession();
        E object = (E) session.get(aClass, id);
        if (noNull && object == null) {
            throw new NoSuchObjectException(id, aClass);
        }
        if (notify && object != null) {
            ThreadContext.getDependencyMap().addResponseDependency(object);
            WebApp webContext = WebApp.get();
            if (webContext != null) {
                if (webContext.getSecurityManager().hasAccess(object)) {
                    return object;
                } else {
                    throw new AccessDeniedException("Access rights to " + aClass.getName() + " with id " + id + " could not be proven");
                }
            }
        }
        return object;
    }

    @Override
	public Integer getIdFromProperty(Class<? extends Persistable> clazz, String property, String value) {
        Session session = sessionFactory.getCurrentSession();
        Criteria criteria = session.createCriteria(clazz);
        criteria.add(Restrictions.eq(property, value));
        criteria.setProjection(Projections.property("id"));
        Object o = criteria.uniqueResult();
        if (o == null) {
            throw new NoSuchObjectException(0, clazz);
        }
        return (Integer) o;
    }

    @Override
	public String getPropertyFromId(Class<? extends Persistable> clazz, String property, Integer id) {
        Session session = sessionFactory.getCurrentSession();
        Criteria criteria = session.createCriteria(clazz);
        criteria.add(Restrictions.eq("id", id));
        criteria.setProjection(Projections.property(property));
        Object o = criteria.uniqueResult();
        if (o == null) {
            throw new NoSuchObjectException(0, clazz);
        }
        return (String) o;
    }

    @Override
	@SuppressWarnings("unchecked")
	public <E extends Persistable> Class<E> getClass(E p) {
        return HibernateProxyHelper.getClassWithoutInitializingProxy(p);
    }

    public boolean isNoNull() {
        return noNull;
    }

    /**
     * If noNull is set then an exception will be thrown if no object with the given Id
     * exists when querying with getObject.
     *
     * @param noNull
     */
    public void setNoNull(boolean noNull) {
        this.noNull = noNull;
    }

    @Override
	public void close() {
        sessionFactory.close();
    }

    @Override
	public void postInit() {
        buildSessionFactory();
    }

    @Override
	public void preInit(Properties properties) {
        init(properties);
    }

    /**
     * Returns the current session from the Hibernate SessionFactory
     * @return
     */
    public Session getSession() {
        return getFactory().getCurrentSession();
    }

    /**
     * Returns the active transaction for this session if such
     * a transaction exists. Otherwise creates a new active transaction
     * and returns it.
     * @param ses
     * @return
     */
    public Transaction getOrBeginTransaction(Session ses) {
        Transaction transaction = ses.getTransaction();
        if (transaction == null || !transaction.isActive()) {
            return ses.beginTransaction();
        }
        return transaction;
    }

    /**
     * Convenience method for, equivalent to calling <code>getOrBeginTransaction(getSession())</code>
     * @return
     */
    public Transaction getOrBeginTransaction() {
        return getOrBeginTransaction(getSession());
    }


    @SuppressWarnings("unchecked")
	@Override
	public Class<? extends Persistable> getBaseType(Persistable p) {
        return HibernateProxyHelper.getClassWithoutInitializingProxy(p);
    }
}
