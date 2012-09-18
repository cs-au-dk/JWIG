package dk.brics.jwig;

import dk.brics.jwig.persistence.FailingQuerier;
import dk.brics.jwig.persistence.Querier;
import dk.brics.jwig.persistence.security.DirectObjectSecurityManager;
import dk.brics.jwig.persistence.security.DirectObjectSecurityManagerImpl;
import dk.brics.jwig.server.Config;
import dk.brics.jwig.server.ThreadContext;
import dk.brics.xact.XML;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for JWIG web applications.
 * <p/>
 * All public non-static methods with return type <code>String</code>, <code>XML</code>, <code>URL</code>,
 * or <vode>void</code> in a deployed <code>WebApp</code> become accessible using HTTP GET requests.
 * For deployment and configuration, see {@link WebSite}.
 *
 * @see URLPattern
 * @see Priority
 */
abstract public class WebApp extends WebContext {

    private final Config configuration;
    private final DirectObjectSecurityManager securityManager;

    /**
     * Constructs a new web application object.
     */
    public WebApp() {
        super();
        configuration = new Config();
        WebSite webSite = getWebSite();
        Querier querier;
        if (webSite != null) {
            querier = webSite.getQuerier();
        } else {
            querier = new FailingQuerier();
        }
        securityManager = new DirectObjectSecurityManagerImpl(this, querier);
    }

    /**
     * Returns the <code>WebApp</code> object for the current thread.
     * This method makes it easy get access to the current <code>WebApp</code>
     * object from outside the <code>WebApp</code> subclass.
     */
    public static WebApp get() {
        ThreadContext threadContext = ThreadContext.get();
        return threadContext.getRequestManager().getWebApp();
    }

    /**
     * Sets a web app configuration property.
     * Typically invoked from the web app constructor.
     *
     * @see WebSite#setProperty(String, Object)
     */
    protected final <T> void setProperty(String name, T value) {
        configuration.setProperty(name, value);
    }

    /**
     * Returns the web app configuration property value for the given name.
     * If not set for the web app, a lookup is performed in the
     * web site configuration.
     *
     * @see WebSite#getProperty(String)
     */
    public final <T> T getProperty(String name) {
        @SuppressWarnings("unchecked")
		T o = (T) configuration.getProperty(name);
        if (o == null) {
            o = ThreadContext.getWebSite().<T>getProperty(name);
        }
        return o;
    }


    /**
     * Returns the configuration property value for the given name, with a default value.
     * If not set for the web app, a lookup is performed in the
     * web site configuration.
     *
     *  @throws ClassCastException if the actual type cannot be converted to the type of the default value
     */
    public final <T> T getProperty(String name, T defaultValue) {
        @SuppressWarnings("unchecked")
		T o = (T) configuration.getProperty(name);
        if (o == null) {
            o = ThreadContext.getWebSite().getProperty(name, defaultValue);
        }
        return o;
    }

    /**
     * Returns the combined web app and web site configuration properties.
     */
    public final Map<String, Object> getProperties() {
        Map<String, Object> properties = new HashMap<String, Object>(ThreadContext.getWebSite().getProperties());
        properties.putAll(configuration.getProperties());
        return properties;
    }

    /**
     * Returns the security manager for this web app.
     */
    public DirectObjectSecurityManager getSecurityManager() {
        return securityManager;
    }

    /**
     * Returns the name of the web method with the given annotated name. The
     * annotated name is the string set on the web method using the {@link dk.brics.jwig.sitemap.PageName}
     * annotation or the name of the web method itself if such an annotation is missing.
     * Web applications may use the method to for instance translate the names of web application
     * pages.
     *
     * @see dk.brics.jwig.sitemap.PageName
     *
     * @param annotatedName The annotated name of the page
     * @return A representation of the actual name of the web method
     */
    protected XML getPageName(String annotatedName) {
        return XML.toXML(annotatedName);
    }

    /**
     * Called on each web app when the website is closed. Default implementation does nothing
     */
    public void destroy() {

    }
}
