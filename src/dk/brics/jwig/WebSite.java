package dk.brics.jwig;

import dk.brics.jwig.persistence.FailingQuerier;
import dk.brics.jwig.persistence.HibernateQuerier;
import dk.brics.jwig.persistence.Querier;
import dk.brics.jwig.server.Config;
import dk.brics.jwig.server.Dispatcher;
import dk.brics.jwig.server.ThreadContext;
import dk.brics.jwig.server.cache.Cache;
import dk.brics.jwig.server.cache.HashMapCache;
import dk.brics.xact.XML;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract base class for JWIG web sites.
 * <p/>
 * Each {@link Dispatcher} creates one instance of the class named
 * <code>Main</code> from the default package. (Another class name can be
 * selected using a servlet initialization parameter named
 * <code>MainClass</code> with the class of interest as value.) That class must
 * be a subclass of <code>dk.brics.jwig.WebSite</code> (or
 * <code>dk.brics.jwig.WebApp</code> for a web site consisting of only one web
 * app). The {@link #init()} method in the <code>Main</code> class constructs
 * {@link WebApp} objects and adds them to the site using {@link #add(WebApp)}.
 * <p/>
 * Configuration properties can be set in <code>jwig.properties</code> and/or
 * using {@link #setProperty(String, Object)} during initialization.
 * <p/>
 * <table border=1>
 * <tr>
 * <th>Name
 * <th>Description
 * <th>Default value
 * <tr>
 * <td><tt>jwig.base_url</tt>
 * <td>absolute base URL
 * <td>auto-detect (should be set if using a proxy server)
 * <tr>
 * <td><tt>jwig.base_url_secure</tt>
 * <td>absolute base URL for secure connections
 * <td>auto-detect (should be set if using proxy server or another port than 443
 * for https)
 * <tr>
 * <td><tt>jwig.session_timeout</tt>
 * <td>default {@linkplain Session session} timeout (minutes)
 * <td><tt>1440</tt> (=one day)
 * <tr>
 * <td><tt>jwig.max_sessions</tt>
 * <td>maximum number of {@linkplain Session session}s allowed
 * <td><tt>1000</tt>
 * <tr>
 * <td><tt>jwig.auto_refresh_sessions</tt>
 * <td>if set, sessions are automatically updated while a page is being viewed
 * <td><tt>true</tt>
 * <tr>
 * <td><tt>jwig.cache_max_pages</tt>
 * <td>maximal number of response pages to cache in memory
 * <td><tt>25</tt>
 * <tr>
 * <td><tt>jwig.fileupload_memory_threshold</tt>
 * <td>file uploads above this size (in bytes) are stored on disk instead of in
 * memory
 * <td><tt>100000</tt>
 * <tr>
 * <td><tt>jwig.fileupload_tmpdir</tt>
 * <td>directory for file upload storage (relative to the
 * <code>Dispatcher</code> home directory)
 * <td>as the <code>java.io.tmpdir</code> system property (e.g. the Tomcat
 * <code>temp</code> directory)
 * <tr>
 * <td><tt>jwig.fileupload_maxsize</tt>
 * <td>maximal size (in bytes) for each file upload (-1 = no limit)
 * <td><tt>-1</tt>
 * <tr>
 * <td><tt>jwig.multipart_maxsize</tt>
 * <td>maximal size (in bytes) for multipart/form-data requests (-1 = no limit)
 * <td><tt>100000000</tt>
 * <tr>
 * <td><tt>jwig.max_long_polling</tt>
 * <td>maximal number of long polling connections (used for {@link XMLProducer})
 * <td>1000
 * <tr>
 * <td><tt>jwig.logo</tt>
 * <td>if set, JWIG logo is added to all XML pages
 * <td><tt>true</tt>
 * <tr>
 * <td><tt>jwig.hibernate</tt>
 * <td>if set, Hibernate is enabled
 * <td><tt>false</tt>
 * <tr>
 * <td><tt>mail.*</tt>
 * <td>configuration of <a href="http://java.sun.com/products/javamail/"
 * target="_top">JavaMail</a> (used by {@link WebContext#sendEmail(Email)
 * sendEmail})
 * <td><i>none set</i>
 * <tr>
 * <td><tt>hibernate.*</tt>
 * <td>configuration of <a href="http://www.hibernate.org/"
 * target="_top">Hibernate</a> (see <a
 * href="persistence/package-summary.html">dk.brics.jwig.persistence</a>)
 * <td><i>see below</i>
 * <tr>
 * <td><tt>log4j.*</tt>
 * <td>configuration of <a href="http://logging.apache.org/log4j/"
 * target="_top">log4j</a> (see {@link WebContext#log})
 * <td><i>see below</i>
 * </table>
 * <p/>
 * Default Hibernate properties:
 * <p/>
 * <table border=1>
 * <tr>
 * <th>Name
 * <th>Default value
 * <tr>
 * <td><tt>hibernate.connection.driver_class</tt>
 * <td><tt>com.mysql.jdbc.Driver</tt>
 * <tr>
 * <td><tt>hibernate.dialect</tt>
 * <td><tt>org.hibernate.dialect.MySQLDialect</tt>
 * <tr>
 * <td><tt>hibernate.connection.pool_size</tt>
 * <td><tt>10</tt>
 * <tr>
 * <td><tt>hibernate.transaction.factory_class</tt>
 * <td><tt>org.hibernate.transaction.JDBCTransactionFactory</tt>
 * <tr>
 * <td><tt>hibernate.cache.provider_class</tt>
 * <td><tt>org.hibernate.cache.HashtableCacheProvider</tt>
 * <tr>
 * <td><tt>hibernate.hbm2ddl.auto</tt>
 * <td><tt>update</tt>
 * <tr>
 * <td><tt>hibernate.show_sql</tt>
 * <td><tt>false</tt>
 * <tr>
 * <td><tt>hibernate.current_session_context_class</tt>
 * <td><tt>dk.brics.jwig.persistence.JwigCurrentSessionContext</tt>
 * <tr>
 * <td><tt>hibernate.c3p0.acquire_increment</tt>
 * <td><tt>1</tt>
 * <tr>
 * <td><tt>hibernate.c3p0.idle_test_period</tt>
 * <td><tt>1000</tt>
 * <tr>
 * <td><tt>hibernate.c3p0.max_size</tt>
 * <td><tt>100</tt>
 * <tr>
 * <td><tt>hibernate.c3p0.max_statements</tt>
 * <td><tt>0</tt>
 * <tr>
 * <td><tt>hibernate.c3p0.min_size</tt>
 * <td><tt>3</tt>
 * <tr>
 * <td><tt>hibernate.c3p0.timeout</tt>
 * <td><tt>100</tt>
 * <tr>
 * <td><tt>hibernate.c3p0.acquireRetryAttempts</tt>
 * <td><tt>1</tt>
 * </table>
 * <p/>
 * Default log4j properties:
 * <p/>
 * <table border=1>
 * <tr>
 * <th>Name
 * <th>Default value
 * <tr>
 * <td><tt>log4j.rootLogger</tt>
 * <td><tt>INFO, jwig</tt>
 * <tr>
 * <td><tt>log4j.appender.jwig</tt>
 * <td><tt>org.apache.log4j.ConsoleAppender</tt>
 * <tr>
 * <td><tt>log4j.appender.jwig.layout</tt>
 * <td><tt>org.apache.log4j.PatternLayout</tt>
 * <tr>
 * <td><tt>log4j.appender.jwig.layout.ConversionPattern</tt>
 * <td><tt>%d{dd MMM yyyy HH:mm:ss,SSS} [%t] %p %c - %m%n</tt>
 * </table>
 */
public abstract class WebSite {

    private final Logger log = Logger.getLogger(WebSite.class);

    private final ArrayList<WebApp> webapps;

    private final Config configuration;

    private Querier hibernatequerier;

    private final XML error_template = XML
            .parseTemplate("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Error</title></head>"
                    + "<style type=\"text/css\">"
                    + "body {font-family: sans-serif}"
                    + "</style><body>"
                    + "<h1><[BADNESS]> (HTTP status <[CODE]>)</h1>"
                    + "<[MSG]>"
                    + "<hr/><table width=\"100%\"><tr>"
                    + "<td><i><[SERVER]>, <[TIME]></i></td>"
                    + "<td align=\"right\"><a href=\"http://www.brics.dk/JWIG/\">"
                    + "<img src=[LOGOURL] alt=\"Powered by JWIG!\" border=\"0\"/></a></td>"
                    + "</tr></table></body></html>");

    /**
     * Constructs a new web site object. If present, configuration properties
     * are read from the file <code>jwig.properties</code> obtained from the
     * class loader.
     * 
     * @throws JWIGException
     *             if an error occurred when reading configuration properties
     */
    public WebSite() throws JWIGException {
        webapps = new ArrayList<WebApp>();
        configuration = new Config();
        configuration.loadProperties();
    }

    /**
     * Invoked when the JWIG server starts, for constructing web apps (
     * <code>WebApp</code> objects) and other initialization.
     * At this point the ThreadContext class has not yet been initialized.
     */
    abstract public void init();

    /**
     * Invoked when the JWIG server has finished initializing. Can be used to add hooks into the initialized JWIG system.
     */
    public void postInit() {}

    /**
     * Closes the hibernate session factory (if any) and calls the {@link #destroy}
     * method which may be overridden by the client.
     */
    public final void close() {
        if (Config.get("jwig.hibernate", false)) {
            HibernateQuerier.destroy();
        }
        for (WebApp w : webapps) {
            w.destroy();
        }
        LogManager.shutdown();
        destroy();
    }

    /**
     * Invoked when the JWIG server stops, for cleaning up after web apps. The
     * default implementation of this method does nothing.
     */
    public void destroy() {
    }

    /**
     * Adds a new web application to this web site. To be invoked from
     * <code>init()</code>.
     * 
     * @param app
     *            web application object
     */
    protected final void add(WebApp app) {
        webapps.add(app);
    }

    /**
     * Returns the list of web application objects of this web site.
     */
    public final List<WebApp> getWebApps() {
        return webapps;
    }

    /**
     * Sets a web site configuration property. Typically invoked from the web
     * site constructor.
     * 
     * @see WebApp#setProperty(String, Object)
     */
    public final void setProperty(String name, Object value) {
        configuration.setProperty(name, value);
    }

    /**
     * Returns the web site configuration property value for the given name.
     * 
     * @see WebApp#getProperty(String)
     */
    @SuppressWarnings("unchecked")
    public final <T> T getProperty(String name) {
        return (T) configuration.getProperty(name);
    }

    /**
     * Returns the configuration property value for the given name, with a
     * default value.
     * 
     * @throws ClassCastException
     *             if the actual type cannot be converted to the type of the
     *             default value
     */
    public final <T> T getProperty(String name, T defaultvalue) {
        return Config.get(name, defaultvalue);
    }

    /**
     * Returns the web site configuration properties.
     */
    public Map<String, Object> getProperties() {
        return configuration.getProperties();
    }

    /**
     * Creates an error message string to be send to the client. Only to be
     * called when processing a request.
     *
     * @param status_code
     *            HTTP status code
     * @param msg
     *            message string
     */
    public XML sendError(int status_code, String msg) {
        return sendError(status_code,
                XML.parseTemplate("<p><[MSG]></p>").plug("MSG", msg));
    }

    public XML sendError(int status_code, XML msg) {
        return sendError(status_code, msg, false);
    }

    /**
     * Creates an error message string to be send to the client. Only to be
     * called when processing a request.
     * 
     * @param status_code
     *            HTTP status code
     * @param msg
     *            message document body
     */
    public XML sendError(int status_code, XML msg, boolean standalone) {
        ThreadContext c = ThreadContext.get();
        ThreadContext.getCache().remove(c.getRequestURL());
        HttpServletRequest request = c.getServletRequest();
        HttpServletResponse response = c.getServletResponse();
        if (response.isCommitted()) {
            log.warn("Response already committed, unable to send error message");
            return null;
        }
        Response error = ThreadContext.get().getResponse();
        if (error == null) {
            error = new Response();
            ThreadContext.get().setResponse(error);
        }
        String base = ThreadContext
                .getBaseURL(c.getServletRequest().isSecure());
        Pattern pattern = Pattern.compile("https?://([^:/]*).*");
        Matcher matcher = pattern.matcher(base);
        String host = request.getServerName();
        if (matcher.find()) {
            host = matcher.group(1);
        }
        error.setStatus(status_code);

        XML xml;
        if (standalone)
            xml = msg;
        else {
            xml = error_template
                    .plug("CODE", status_code)
                    .plug("MSG", msg)
                    .plug("SERVER", host)
                    .plug("TIME",
                            new SimpleDateFormat("d MMM yyyy HH:mm:ss Z",
                                    Locale.US).format(new Date()))
                    .plug("LOGOURL",
                            ThreadContext.getBaseURL(request.isSecure())
                                    + request.getContextPath()
                                    + "/jwiglogo.gif");
            if (status_code == HttpServletResponse.SC_NOT_FOUND) {
                xml = xml.plug("BADNESS", "NOT FOUND");
            } else {
                xml = xml.plug("BADNESS", "ERROR");
            }
        }

        log.info("Sending error " + status_code + " to client");
        WebContext.punish(Dispatcher.getClient(request));
        error.setXML(xml);
        return xml;
    }

    /**
     * Gets the querier that should be used to query object from the database.
     * Default method returns a {@link HibernateQuerier} if
     * <code>jwig.hibernate</code> is enabled, otherwise a placeholder querier.
     */
    synchronized public Querier getQuerier() {
        if (hibernatequerier == null) {
            if (Config.get("jwig.hibernate", false)) {
                hibernatequerier = new HibernateQuerier();
            } else {
                hibernatequerier = FailingQuerier.getInstance();
            }
        }
        return hibernatequerier;
    }

    /**
     * Returns the string used to seperate two entries with the same URL in the
     * cache. The default implementation seperates the entries by the HTTP basic
     * username given.
     */
    public String getCacheAugmentationString() {
        User user = WebApp.get().getUser();
        return user == null ? "" : user.getUsername();
    }

    /**
     * Return a new instance of the cache implementation that is used in this
     * web site.
     */
    public Cache getCache() {
        return new HashMapCache();
    }
}
