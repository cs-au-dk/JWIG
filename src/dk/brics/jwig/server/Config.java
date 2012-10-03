package dk.brics.jwig.server;

import dk.brics.jwig.JWIGException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration.
 */
public class Config {

    private Map<String, Object> configuration;

    private Config parent;

    /**
     * Constructs a new configuration object.
     */
    public Config() {
        configuration = new HashMap<String, Object>();
    }

    /**
     * Sets the parent configuration.
     */
    public void setParent(Config parent) {
        this.parent = parent;
    }

    /**
     * Looks up the current configuration for the given name, with a default value.
     *
     * @throws ClassCastException if the actual type cannot be converted to the type of the default value
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String name, T defaultvalue) {
        Object o = null;
        if (ThreadContext.isInRequestContext()) {
            ThreadContext c = ThreadContext.get();
            o = c.getRequestManager().getWebApp().getProperty(name);
        }
        if (o == null) {
            o = ThreadContext.getWebSite().getProperty(name);
        }
        if (o == null) {
            o = defaultvalue;
        }
        T t = null;
        if (o != null) {
            if (defaultvalue == null || defaultvalue.getClass().isAssignableFrom(o.getClass())) {
                t = (T) o;
            } else {
                if (o instanceof String) {
                    try {
                        t = (T) defaultvalue.getClass().getMethod("valueOf", String.class).invoke(null, o);
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                if (t == null) {
                    throw new ClassCastException("Unable to convert configuration parameter '" + name + "' to the expected type.");
                }
            }
        }
        return t;
    }

    /**
     * Sets a configuration property.
     */
    public void setProperty(String name, Object value) {
        configuration.put(name, value);
    }

    /**
     * Returns the configuration property value for the given name.
     * If not set here, a lookup is performed in the parent.
     */
    public Object getProperty(String name) {
        Object o = configuration.get(name);
        if (o == null && parent != null) {
            o = parent.getProperty(name);
        }
        return o;
    }

    /**
     * Returns the property map.
     */
    public Map<String, Object> getProperties() {
        return configuration;
    }

    /**
     * Adds properties from the resource <code>jwig.properties</code>, obtained from the class loader.
     *
     * @throws JWIGException if an error occurred when reading from the input stream
     */
    public void loadProperties() throws JWIGException {
        try {
            Properties ps = new Properties();
            addDefaultHibernateProperties(ps);

            addDefaultLog4JProperties(ps);
            InputStream in = getClass().getClassLoader().getResourceAsStream("jwig.properties");
            if (in != null) {
                ps.load(in);
            }
            for (Map.Entry<Object, Object> e : ps.entrySet()) {
                configuration.put((String) e.getKey(), e.getValue());
            }
        } catch (IOException e) {
            throw new JWIGException("unable to read jwig.properties", e);
        }
    }

    /**
     * Adds default values for Hibernate. This method should be run before loading 
     * from <code>jwig.properties</code> to ensure that any values set by the user
     * are used instead of the default ones.
     */
    private void addDefaultHibernateProperties(Properties p) { // should match documentation in WebSite class
        //Default to MySQL database
        p.put("hibernate.connection.driver_class", "com.mysql.jdbc.Driver");
        p.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");

        //Set up connections to 10 active connections using JDBC transactions
        p.put("hibernate.connection.pool_size", "10");
        p.put("hibernate.transaction.factory_class", "org.hibernate.transaction.JDBCTransactionFactory");

        //Use simple caching in a hash table
        p.put("hibernate.cache.provider_class", "org.hibernate.cache.HashtableCacheProvider");

        //Do not show sql and automagically create missing tables/columns
        p.put("hibernate.hbm2ddl.auto", "update");
        p.put("hibernate.show_sql", "false");

        //Let JWIG decide how database sessions are born and killed
        p.put("hibernate.current_session_context_class", "dk.brics.jwig.persistence.JwigCurrentSessionContext");

        //Setup the connection pool. Only thing that should be fiddled with is the timeout.
        //This must be smaller than the server timeout for hibernate to keep the conection alive.
        //The server timeout is typically 1800 seconds.
        p.put("hibernate.connection.provider_class","org.hibernate.service.jdbc.connections.internal.C3P0ConnectionProvider");
        p.put("hibernate.c3p0.acquire_increment", "1");
        p.put("hibernate.c3p0.idle_test_period", "1000");
        p.put("hibernate.c3p0.max_size", "100");
        p.put("hibernate.c3p0.max_statements", "0");
        p.put("hibernate.c3p0.min_size", "3"); //Always keep 3 connections alive. Can be lovered
        p.put("hibernate.c3p0.timeout", "100"); //Timeout. Must be larger than server timeout
        p.put("hibernate.c3p0.acquireRetryAttempts","1" );
    }
    
    /**
     * Adds default values for log4j. This method should be run before loading 
     * from <code>jwig.properties</code> to ensure that any values set by the user
     * are used instead of the default ones.
     */
    private void addDefaultLog4JProperties(Properties p) { // should match documentation in WebSite class
    	p.put("log4j.rootLogger", "INFO");
        p.put("log4j.logger.org.hibernate","WARN"); //Show only warnings from hibernate
    }
}
