package dk.brics.jwig;

import dk.brics.jwig.server.Config;
import dk.brics.jwig.server.Dispatcher;
import dk.brics.jwig.server.RequestManager;
import dk.brics.jwig.server.ThreadContext;
import dk.brics.jwig.server.cache.Cache;
import dk.brics.jwig.server.cache.DependencyMap;
import dk.brics.jwig.util.Base64;
import dk.brics.xact.XML;
import org.apache.log4j.Logger;

import javax.mail.*;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Context for web execution.
 */
abstract public class WebContext {

    /**
     * Priority for the main web method.
     *
     * @see #main()
     */
    public static final int MAX_PRIORITY = Integer.MAX_VALUE;

    /**
     * Priority for the server cache web method.
     *
     * @see #cache()
     */
    public static final int CACHE_PRIORITY = 500;

    /**
     * Priority to use for web methods to be executed before the server cache,
     * such as authentication methods.
     */
    public static final int PRE_CACHE = 1000;

    /**
     * Priority to use for handlers.
     *
     * @see #handlers()
     */
    public static final int HANDLERS = 0;
    static final String JWIG_DEFUNCT_SESSION = "X-JWIG-defunct-session";
    private static final String JWIG_REGENERATE = "X-JWIG_regenerate";

    /**
     * Logger.
     */
    public final Logger log = Logger.getLogger(WebContext.class);

    public static ServletContext context;

    /**
     * A map that for each client that has had a failed request saves the number
     * of consecutive failed requests.
     */
    private static final ConcurrentHashMap<String, AtomicInteger> failedRequestMap = new ConcurrentHashMap<String, AtomicInteger>();

    /**
     * A map that allows sessions to carry handlers from one request to another. Made a map on WebContext to
     * ensure that this data is not available to the client code that uses the session.
     */
    private final WeakHashMap<Session, Set<AbstractHandler>> sessionHandlers = new WeakHashMap<Session, Set<AbstractHandler>>();

    private final boolean auto_refresh_sessions;

    /**
     * Constructs a new empty web context object.
     */
    WebContext() {
        auto_refresh_sessions = Config.get("jwig.auto_refresh_sessions", true);

    }

    /**
     * Starts the timer that will reduce punishment for clients every five
     * minutes.
     */
    static {
        final Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    Logger log = Logger.getLogger(WebContext.class);
                    for (Iterator<Map.Entry<String, AtomicInteger>> it = failedRequestMap
                            .entrySet().iterator(); it.hasNext();) {
                        Map.Entry<String, AtomicInteger> entry = it.next();
                        log.info("Reducing punishment for " + entry.getKey());
                        AtomicInteger atomicInteger = entry.getValue();
                        atomicInteger.decrementAndGet();
                        if (atomicInteger.get() == 0) {
                            log.info("Forgiving " + entry.getKey());
                            it.remove();
                        }
                    }
                } catch (Exception e) {
                    Logger.getLogger(WebContext.class).info(
                            "Punishing timer stopped");
                    timer.cancel();
                }
            }
        }, 100000, 1000 * 60 * 5);
    }

    /**
     * Returns the <code>HttpServletRequest</code> object.
     */
    public static HttpServletRequest getServletRequest() {
        return ThreadContext.get().getServletRequest();
    }

    /**
     * Returns the <code>HttpServletResponse</code> object.
     */
    public static HttpServletResponse getServletResponse() {
        return ThreadContext.get().getServletResponse();
    }

    /**
     * Returns the <code>ServletContext</code> object.
     */
    public static ServletContext getServletContext() {
        return context;
    }

    /**
     * Sends the current response. Use in filter web methods (i.e. only GET
     * requests) for producing response. If no response has been sent and the
     * method has return type void, then the next web method in the chain is
     * invoked when the filter web method returns. Also used for producing a
     * temporary response and continuing execution.
     */
    public static void sendResponse() {
        ThreadContext c = ThreadContext.get();
        ThreadContext.getDispatcher().send(c.getResponse());
    }

    /**
     * Returns the current response object.
     */
    public static Response getResponse() {
        return ThreadContext.get().getResponse();
    }

    /**
     * Returns the URL of the current request.
     */
    public static URL getRequestURL() {
        try {
            return new URL(ThreadContext.get().getRequestURL());
        } catch (MalformedURLException e) {
            throw new JWIGException(e);
        }
    }

    /**
     * Generates a URL for the given web method within the same web site. The
     * URL uses <code>https</code> if the current request is on a secure
     * connection.
     *
     * @param method
     *            the name of the web method (optionally qualified by the class
     *            name, default: this web app)
     * @param args
     *            arguments to the web method. These arguments will be encoded using <code>toString</code> and will be part of
     *            the request URL for the web method. The list of args must match the list of formal parameters in the web method
     *            both in terms of number and types. The parameter will be mapped to names based on their order.
     */
    public static URL makeURL(String method, Object... args) {
        return makeURL(ThreadContext.get().getWebAppParams(), method, args);
    }

    /**
     * Generates a URL for the given web method within the same web site, with
     * web app arguments. The URL uses <code>https</code> if the current request
     * is on a secure connection.
     *
     * @param webapp_args
     *            web app arguments. This map can define values for arguments defined by the WebApp class (rather than the web method).
     *            See section 4.3 in the manual for details
     * @param method
     *            the name of the web method (optionally qualified by the class
     *            name, default: this web app)
     * @param args
     *            arguments to the web method. These arguments will be encoded using <code>toString</code> and will be part of the
     *            request URL for the web method. The list of args must match the list of formal parameters in the web method both in
     *            terms of number and types. The parameter will be mapped to names based on their order.
     */
    public static URL makeURL(Map<String, ?> webapp_args, String method,
            Object... args) {
        return ThreadContext.get().getRequestManager()
                .makeURL(webapp_args, method, args);
    }

    /**
     * Generates a URL for the given web method within the same web site and in
     * the given web app. The URL uses <code>https</code> if the current request
     * is on a secure connection.
     *
     * @param cl
     *            the web app to search for the method in
     * @param method
     *            the name of the web method
     * @param args
     *            arguments to the web method. These arguments will be encoded using <code>toString</code> and
     *            will be part of the request URL for the web method. The list of args must match the list of formal parameters
     *            in the web method both in terms of number and types. The parameter will be mapped to names based on their order.
     */
    public static URL makeURL(Class<? extends WebApp> cl, String method,
            Object... args) {
        return makeURL(cl.getCanonicalName() + "." + method, args);
    }

    /**
     * Generates a URL for the given web method within the same web site.
     *
     * @param secure
     *            if true, use <code>https</code>, otherwise <code>http</code>
     * @param method
     *            the name of the web method (optionally qualified by the class
     *            name, default: this web app)
     * @param args
     *            arguments to the web method. These arguments will be encoded using <code>toString</code> and will be part
     *            of the request URL for the web method. The list of args must match the list of formal parameters in the web method
     *            both in terms of number and types. The parameter will be mapped to names based on their order.
     */
    public static URL makeURL(boolean secure, String method, Object... args) {
        return makeURL(secure, ThreadContext.get().getWebAppParams(), method,
                args);
    }

    /**
     * Generates a URL for the given web method within the same web site, with
     * web app arguments.
     *
     * @param secure
     *            if true, use <code>https</code>, otherwise <code>http</code>
     * @param webapp_args
     *            web app arguments. This map can define values for arguments defined by the WebApp class (rather than the web method).
     *            See section 4.3 in the manual for details
     * @param method
     *            the name of the web method (optionally qualified by the class
     *            name, default: this web app)
     * @param args
     *            arguments to the web method. These arguments will be encoded using <code>toString</code> and will be part of the
     *            request URL for the web method. The list of args must match the list of formal parameters in the web method both
     *            in terms of number and types. The parameter will be mapped to names based on their order.
     */
    public static URL makeURL(boolean secure, Map<String, ?> webapp_args,
            String method, Object... args) {
        return ThreadContext.get().getRequestManager()
                .makeURL(secure, webapp_args, method, args);
    }

    /**
     * Generates a URL for the given web method within the same web site and in
     * the given web app.
     *
     * @param secure
     *            if true, use <code>https</code>, otherwise <code>http</code>
     * @param cl
     *            the web app to search for the method in
     * @param method
     *            the name of the web method
     * @param args
     *            arguments to the web method. These arguments will be encoded using <code>toString</code> and will be part of the
     *            request URL for the web method. The list of args must match the list of formal parameters in the web method both
     *            in terms of number and types. The parameter will be mapped to names based on their order.
     */
    public static URL makeURL(boolean secure, Class<? extends WebApp> cl,
            String method, Object... args) {
        return makeURL(secure, cl, ThreadContext.get().getWebAppParams(),
                       method, args);
    }

    /**
     * Generates a URL for the given web method within the same web site and in
     * the given web app, with web app arguments.
     *
     * @param secure
     *            if true, use <code>https</code>, otherwise <code>http</code>
     * @param cl
     *            the web app to search for the method in
     * @param webapp_args
     *            web app arguments. This map can define values for arguments defined by the WebApp class (rather than the web method).
     *            See section 4.3 in the manual for details
     * @param method
     *            the name of the web method
     * @param args
     *            arguments to the web method. These arguments will be encoded using <code>toString</code> and will be part of the
     *            request URL for the web method. The list of args must match the list of formal parameters in the web method both
     *            in terms of number and types. The parameter will be mapped to names based on their order.
     */
    public static URL makeURL(boolean secure, Class<? extends WebApp> cl,
            Map<String, ?> webapp_args, String method, Object... args) {
        return makeURL(secure, webapp_args, cl.getCanonicalName() + "."
                + method, args);
    }

    /**
     * Generates a URL for the root of the web site.
     * If the calling thread is bound to a request this method will return the URL for the WebSite either
     * from the configuration (if available) or (otherwise by inspecting the current request.
     * Otherwise if the method is called without a request context, this method will only inspect the configuration.
     * <br/>
     * The result of calling this method will be the same with or without a request context if the <tt>jwig.base_url</tt> and
     * <tt>jwig.base_url_secure</tt> properties have been set in <tt>jwig.properties</tt>.
     *
     * @param secure
     *            if true, use <code>https</code>, otherwise <code>http</code>
     */
    public static URL getWebSiteURL(boolean secure) {
        try {
            if (!ThreadContext.isInRequestContext()) {
                return new URL(ThreadContext.getBaseURL(secure));
            }
            return new URL(ThreadContext.get().getRequestManager()
                                        .getWebSiteURL(secure));
        } catch (MalformedURLException e) {
            throw new JWIGException(e);
        }
    }

    /**
     * Generates a URL for the root of this website. The URL uses https iff the
     * request does.
     * Requires the calling thread to be bound to a request.
     */
    public static URL getWebSiteURL() {
        return getWebSiteURL(ThreadContext.get().getServletRequest().isSecure());
    }

    /**
     * Generates a URL for the root of the current web application.
     *
     * @param secure
     *            if true, use <code>https</code>, otherwise <code>http</code>
     */
    public static URL getWebAppURL(boolean secure) {
        try {
            Map<String, String> stringMap = ThreadContext.get()
                    .getWebAppParams();
            Map<String, String[]> stringMap2 = new HashMap<String, String[]>();
            for (Map.Entry<String, String> e : stringMap.entrySet()) {
                stringMap2.put(e.getKey(), new String[] { e.getValue() });
            }
            return new URL(ThreadContext.get().getRequestManager()
                    .getWebAppURL(secure, stringMap2));
        } catch (MalformedURLException e) {
            throw new JWIGException(e);
        }
    }

    /**
     * Registers the given object as observable for the response. This means
     * that if the object is updated (see @link #update(Object)), the response
     * is invalidated in the cache.
     */
    public static void addResponseInvalidator(Object obj) {
        if (obj == null) {
            return;
        }
        DependencyMap dependencyMap = ThreadContext.getDependencyMap();
        if (dependencyMap != null) {
            dependencyMap.addResponseDependency(obj);
        }
    }

    /**
     * Informs observers that the given object has been updated.
     *
     * @see XMLProducer
     * @see #addResponseInvalidator(Object)
     */
    public static void update(Object obj) {
        if (obj == null) {
            return;
        }
        DependencyMap dependencyMap = ThreadContext.getDependencyMap();
        if (dependencyMap != null) {
            dependencyMap.objectUpdated(obj);
        }
    }

    /**
     * Sends an email using <a target="_top"
     * href="http://java.sun.com/products/javamail/">JavaMail</a>. All
     * configuration properties starting with <code>mail.</code> are used for
     * setting up a JavaMail session when the web app is initialized.
     *
     * @return a <code>SendFailedException</code> or null if there is no error
     * @throws JWIGException
     *             in case of other errors than <code>SendFailedException</code>
     * @see WebApp#setProperty(String, Object)
     * @see #sendEmails(List)
     */
    public SendFailedException sendEmail(Email message) throws JWIGException {
        List<Email> messages = new ArrayList<Email>();
        messages.add(message);
        return sendEmails(messages).get(0);
    }

    /**
     * Sends a collection of emails.
     *
     * @return a list of <code>SendFailedException</code>s, one for each message
     *         being sent, with null meaning "no error"
     * @throws JWIGException
     *             in case of other errors than <code>SendFailedException</code>
     * @see #sendEmail(Email)
     */
    public List<SendFailedException> sendEmails(List<Email> messages)
            throws JWIGException {
        try {
            List<SendFailedException> results = new ArrayList<SendFailedException>();
            // XXX ThreadContext threadContext = ThreadContext.get();
            // If method is not called from a web app thread, we will use the
            // first request manager to get the session
            Transport transport = ThreadContext.getEmailSession()
                    .getTransport();
            transport.connect();
            for (Email m : messages) {
                try {
                    log.info("Sending email to "
                            + Arrays.toString(m.getAllRecipients()));
                    transport.sendMessage(m, m.getAllRecipients());
                    results.add(null);
                } catch (SendFailedException e) {
                    results.add(e);
                }
            }
            transport.close();
            return results;
        } catch (NoSuchProviderException e) {
            throw new JWIGException(e);
        } catch (AuthenticationFailedException e) {
            throw new JWIGException(e);
        } catch (MessagingException e) {
            throw new JWIGException(e);
        }
    }

    /**
     * Works like {@link #sendEmails(java.util.List)} but sends the emails in a
     * separate thread. For large batches of emails this means that the
     * application does not have to wait. Errors can however not be propagated
     * using this method
     *
     * @param messages
     *            Messages to send
     * @return an empty list
     * @throws JWIGException
     */
    public List<SendFailedException> sendEmailsAsynchronously(
            final List<Email> messages) throws JWIGException {
        // XXX ThreadContext threadContext = ThreadContext.get();
        // If method is not called from a web app thread, we will use the first
        // request manager to get the session
        try {
            final Transport transport = ThreadContext.getEmailSession()
                    .getTransport();
            new Thread() {
                @Override
                public void run() {
                    try {
                        transport.connect();
                        for (Email m : messages) {
                            try {
                                log.info("Sending email to "
                                        + Arrays.toString(m.getAllRecipients()));
                                transport.sendMessage(m, m.getAllRecipients());
                            } catch (SendFailedException e) {
                                log.warn(e.getMessage(), e);
                            }
                        }
                        transport.close();
                    } catch (NoSuchProviderException e) {
                        log.error(e.getMessage(), e);
                    } catch (AuthenticationFailedException e) {
                        log.error(e.getMessage(), e);
                    } catch (MessagingException e) {
                        log.error(e.getMessage(), e);
                    }
                }

            }.start();
        } catch (NoSuchProviderException e) {
            throw new JWIGException(e);
        }
        return Collections.emptyList();
    }

    /**
     * Returns (non-null) array of cookies from the request.
     */
    public Cookie[] getCookies() {
        Cookie[] cookies = ThreadContext.get().getServletRequest().getCookies();
        if (cookies == null) {
            cookies = new Cookie[0];
        }
        return cookies;
    }

    /**
     * Adds a cookie to the response.
     *
     * @param send_now
     *            if set, the cookie is sent immediately without storing it in
     *            the response cache
     */
    protected void addCookie(Cookie c, boolean send_now) {
        if (send_now) {
            ThreadContext.get().getServletResponse().addCookie(c);
        } else {
            ThreadContext.get().getResponse().addCookie(c);
        }
    }

    /**
     * Checks whether the request was made using a secure channel, such as
     * SSL/TLS.
     */
    public boolean isSecure() {
        return ThreadContext.get().getServletRequest().isSecure();
    }

    /**
     * Gets the user for the request, using HTTP Basic authentication. Returns
     * null if not available.
     */
    public User getUser() {
        String username;
        String password;
        HttpServletRequest req = getServletRequest();
        String auth = req.getHeader("Authorization");
        if (auth == null) {
            return null;
        }
        auth = auth.trim();
        if (!auth.startsWith("Basic")) {
            return null;
        }
        auth = auth.substring(5).trim();
        try {
            auth = Base64.decodeString(auth, "ISO-8859-1");
        } catch (IllegalArgumentException e) {
            return null;
        } catch (UnsupportedEncodingException e) {
            return null;
        }
        int i = auth.indexOf(':');
        if (i == -1) {
            return null;
        }
        username = auth.substring(0, i);
        password = auth.substring(i + 1);
        return new User(username, password);
    }

    /**
     * Returns the web site object.
     */
    public final WebSite getWebSite() {
        return ThreadContext.getWebSite();
    }

    /**
     * Augments the current XML response with script code, JWIG logo, and web
     * app params. TODO: for all XML output (including XMLProducer): remove
     * empty ul/ol/... (see PostProcessor.flex in old JWIG)
     */
    @URLPattern("**")
    @Priority(MAX_PRIORITY - 1)
    @GET
    @POST
    @PUT
    @DELETE
    @TRACE
    @HEAD
    public Object augment() {
        Object o = next();
        ThreadContext c = ThreadContext.get();
        RequestManager manager = c.getRequestManager();
        Response r = getResponse();
        if (o instanceof XML) {
            // log.debug("Augmenting: " + r.getETag());
            XML xml = (XML) o;
            String home = manager.getWebSiteURL(c.getServletRequest()
                    .isSecure());
            xml = xml
                    .prependContent(
                            "//xhtml:head",
                            XML.parseTemplate(
                                    "<script type=\"text/javascript\" src=[JWIG]></script>")
                                    .plug("JWIG", manager.makeURL("jwigJS")));
            xml = xml
                    .prependContent(
                            "//xhtml:body", //It is safe to set a constant id given that only one body may exist
                            XML.parseTemplate("<div id=\"noscript\" style=\"color:red\">" +
                                    "[ This web application uses JavaScript, which is not enabled in your browser - "
                                    + "for full functionality, use a browser that supports JavaScript ]</div>" +
                                    "<script type=\"text/javascript\"> " +
                                    "document.getElementById(\"noscript\").style.display = \"none\";" +
                                    "</script>"));
            xml = xml.appendContent("//xhtml:body",
                    XML.parseTemplate(
                            "<script type=\"text/javascript\">"
                                    + "jwig.run('<[HOME]>');" + "</script>")
                            .plug("HOME", home));
            xml = xml.appendContent("//xhtml:body",
                                    XML.parseTemplate("<script type=\"text/javascript\">"
                                    + "jwig.submitHandlerData = <[SUB]> ;" + "</script>").plug("SUB",
                                                                                               generateSubmitHandlerData(r)));
            if (auto_refresh_sessions) {
                Set<Session> sessions = r.getSessions();
                if (sessions != null && sessions.size() > 0) {
                    int minutes = 0;
                    for (Session s : sessions) {
                        int min = s.getMinutes();
                        if (minutes < min || minutes == 0) {
                            minutes = min;
                        }
                    }
                    Object[] ss = { sessions.toArray() };
                    xml = xml
                            .appendContent(
                                    "//xhtml:head",
                                    XML.parseTemplate(
                                            "<script type=\"text/javascript\">"
                                                    + "jwig.startRefreshSessions('<[REFRESH_URL]>',<[MINUTES]>);"
                                                    + "</script>")
                                            .plug("REFRESH_URL",
                                                    manager.makeURL(
                                                            "touchSessions", ss)
                                                            .toString())
                                            .plug("MINUTES", minutes - 1));
                }
            }
            if (Config.get("jwig.logo", true)) {
                xml = xml
                        .appendContent(
                                "//xhtml:body",
                                XML.parseTemplate(
                                        "<table width=\"100%\"><tr>"
                                                + "<td align=\"right\"><a href=\"http://www.brics.dk/JWIG/\">"
                                                + "<img src=[LOGOURL] alt=\"Powered by JWIG!\" style=\"border:0\"/></a></td>"
                                                + "</tr></table>").plug(
                                        "LOGOURL", home + "jwiglogo.gif"));
            }
            return xml;
        }
        return o;
    }

    private String generateSubmitHandlerData(Response r) {
        StringBuilder jSSubmitHandlerData = new StringBuilder();
        jSSubmitHandlerData.append("{");
        boolean first = true;
        for (AbstractHandler a : r.getHandlers()) {
            if (a instanceof SubmitHandler) {
                if (!first) {
                    jSSubmitHandlerData.append(",");
                } else
                    first = false;
                SubmitHandler submitHandler = (SubmitHandler) a;
                //These are the form field names that the submit handler wants to validate. Add them to the client side
                //so that this can happen
                List<String> strings = submitHandler.validatedFormFields();
                StringBuilder jSFormFieldList = new StringBuilder();
                jSFormFieldList.append("[");
                for (Iterator<String> iterator = strings.iterator(); iterator.hasNext(); ) {
                    String s = iterator.next();
                    jSFormFieldList.append("\"" + s + "\""); //No need to escape, we know that s is a valid Java identifier...
                    if (iterator.hasNext()) {
                        jSFormFieldList.append(",");
                    }
                }
                jSFormFieldList.append("]");
                jSSubmitHandlerData.append(String.format("\"%s\" : %s", a.getHandlerIdentifier(), jSFormFieldList.toString()));
            }
        }
        jSSubmitHandlerData.append("}");
        return jSSubmitHandlerData.toString();
    }

    /**
     * If a piece of XML contains a handler and this piece of XML is stored in a session
     * between requests, we need to make sure that the handler is transported
     * from one request to the next.<br/><br/>
     *
     * An example of this pattern exists in the guessing game example of jwig, where
     * XML is stored in the session and updated from one request to the next.
     *
     * This method allows for handlers to be transported from one request where the
     * handler is part of the XML and to the current request if the URL of the handler
     * is part of the XML that is returned if those two requests share the same session.
     */
    @Priority(MAX_PRIORITY - 1)
    @URLPattern("**")
    public void transportSessionHandlers() {
        next();
        ThreadContext c = ThreadContext.get();
        Set<Session> activeSessions = c.getResponse().getSessions();
        Response response = c.getResponse();
        Collection<AbstractHandler> responseHandlers = response.getHandlers();
        for (Session s : activeSessions) {
            Set<AbstractHandler> sessionHandlers = this.sessionHandlers.get(s);
            if (sessionHandlers == null) {
                sessionHandlers = new HashSet<AbstractHandler>();
                this.sessionHandlers.put(s, sessionHandlers);
            }
            for (AbstractHandler handler : sessionHandlers) {
                c.getResponse().setHandler(handler.getHandlerIdentifier(), handler);
            }
            sessionHandlers.addAll(responseHandlers);
        }
    }

    /**
     * Main web method. Catches exceptions and reports them as HTTP errors.
     */
    @URLPattern("**")
    @Priority(MAX_PRIORITY)
    @GET
    @POST
    @PUT
    @DELETE
    @TRACE
    @HEAD
    public final Object main() throws InterruptedException {
        long startTime = getTime();
        String key = Dispatcher.getClient(getServletRequest());
        AtomicInteger atomicInteger = failedRequestMap.get(key);
        WebSite website = ThreadContext.getWebSite();
        if (atomicInteger != null && atomicInteger.get() != 0) {
            int wait = atomicInteger.get();
            long millis = 1l << wait;
            if (0 < millis && millis < 10000) {
                log.info("Letting " + key + " wait for " + millis
                        + " ms before responding.");
                Thread.sleep(millis);
            } else {
                log.info("Trying to drop " + key);
                return website.sendError(
                        HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Too many bad requests. You have been dropped.");
            }
        }
        try {
            log.info("Start generate response");
            Object o = next();
            if (o instanceof XML && getServletRequest().getHeader(JWIG_REGENERATE) != null) {
                Response currentResponse = getResponse();
                String ticket = ThreadContext.getHandlerCache().createTicket(currentResponse);
                getServletResponse().setHeader(JWIG_REGENERATE, ticket);
            }
            if (failedRequestMap != null) { // Redeployment can make this map null
                atomicInteger = failedRequestMap.get(key);
                if (!ThreadContext.get().isPunished() && atomicInteger != null
                        && atomicInteger.get() != 0) {
                    failedRequestMap.remove(key);
                    log.info("Forgiving " + key);
                }
            }
            long time = ((getTime() - startTime) / (long) 1E6);
            log.info("It took " + time
                    + "ms of CPU time to generate the response");
        } catch (JWIGException e) {
            log.info(e.getLogInfo());
            e.setHeaders(ThreadContext.get().getServletResponse());
            XML xml = e.getXml();
            if (xml == null) {
                return website.sendError(e.getErrorCode(), e.getMessagePage());
            } else {
                return website.sendError(e.getErrorCode(), xml, true);
            }
        } catch (Exception e) {
            log.error("Exception", e);
            return website.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                     "Internal server error. (Details are in the server log file.)");
        }
        return null;
    }

    private long getTime() {
        long start;
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (bean.isCurrentThreadCpuTimeSupported()) {
            start = bean.getCurrentThreadCpuTime();
        } else {
            start = System.currentTimeMillis();
        }
        return start;
    }

    public static void punish(String key) {
        Logger.getLogger(WebContext.class).info("Punishing " + key);
        ThreadContext.get().setPunished(true);
        AtomicInteger atomicInteger;
        atomicInteger = failedRequestMap.get(key);
        if (atomicInteger == null) {
            atomicInteger = new AtomicInteger();
            failedRequestMap.put(key, atomicInteger);
        }
        atomicInteger.incrementAndGet();
    }

    @Priority(PRE_CACHE)
    @URLPattern("jwig.js")
    public void jwigJS() throws IOException {
        ServletOutputStream outputStream = getServletResponse()
                .getOutputStream();
        InputStream stream = WebContext.class.getResourceAsStream("jwig.js");
        byte[] buf = new byte[1024];
        int read;
        while ((read = stream.read(buf)) != -1) {
            outputStream.write(buf, 0, read);
        }
        getResponse().setContentType("text/javascript");
    }

    /**
     * Server cache web method. Checks whether the client's cached resource is
     * valid (using <code>If-Modified-Since</code> and <code>ETag</code>) and
     * whether the requested resource is in the server cache. Only considers GET
     * requests. Invokes {@link #next()} if a valid cached resource is not
     * available.
     */
    @GET
    @URLPattern("**")
    @Priority(CACHE_PRIORITY)
    public Object cache() {
        try {
            ThreadContext.getDependencyMap().beginTransaction(true);
            ThreadContext c = ThreadContext.get();
            HttpServletRequest request = c.getServletRequest();
            HttpServletResponse response = c.getServletResponse();
            Cache cache = ThreadContext.getCache();
            String cacheURL = c.getRequestURL();
            Response cachedResponse = cache.get(cacheURL);
            if (cachedResponse == null) {
                String augmentationString = "<|>"
                        + WebApp.get().getWebSite()
                                .getCacheAugmentationString();
                cacheURL = c.getRequestURL() + augmentationString;
                cachedResponse = cache.get(cacheURL);
            }
            if (cachedResponse != null) {
                String cacheControl = request.getHeader("cache-control");
                long if_modified_since = -1;
                try {
                    if_modified_since = request
                            .getDateHeader("If-Modified-Since");
                } catch (IllegalArgumentException e) {
                    log.info("Client sent invalid If-Modified-Since");
                }
                try {
                    String if_none_match = request.getHeader("If-None-Match");
                    long lastModified = (cachedResponse.getLastModified() / 1000) * 1000;
                    long ifModifiedSince = (if_modified_since / 1000) * 1000;
                    if (if_none_match == null && if_modified_since == -1
                            && cacheControl != null
                            && cacheControl.equals("max-age=0")) {
                        log.info("Client refreshed the page. Removing the old one from the cache");
                        cache.remove(cacheURL);

                    } else {
                        if ((if_none_match != null && if_none_match.equals("\""
                                + cachedResponse.getETag() + "\""))
                                || (if_none_match == null
                                        && if_modified_since != -1 && lastModified <= ifModifiedSince)) {
                            response.setStatus(304);
                            response.flushBuffer();
                            log.info("Client cache valid");
                            return null;
                        }
                        log.info("Response in server cache");
                        ThreadContext.get().setResponse(cachedResponse);
                        return cachedResponse.getObject();
                    }
                } catch (IOException e) {
                    log.info("IOException in serverCache", e);
                    if (response.isCommitted()) {
                        return null;
                    }
                }
            }
            Object result = next();
            if (result instanceof XML || result instanceof String) {
                Response currentResponse = ThreadContext.get().getResponse();
                currentResponse.setResult(result);
                // This may also remove pages from the cache
                boolean shouldCache = ThreadContext.getDependencyMap().mergeTransaction();
                if (shouldCache && currentResponse.getStatus() == HttpServletResponse.SC_OK) {
                    String url = c.getRequestURL();
                    url = getCacheAugmentedString(url);
                    Response newResponseForCache = new Response(currentResponse);
                    newResponseForCache.setResult(currentResponse.getResult());
                    ThreadContext.getCache().put(url, newResponseForCache);
                }
            }
            return result;
        } finally {
            ThreadContext.getDependencyMap().removeTransaction();
        }
    }

    protected String getCacheAugmentedString(String url) {
        boolean cacheAugmented = ThreadContext.get().isCacheAugmented();
        if (cacheAugmented) {
            url += "<|>" + getWebSite().getCacheAugmentationString();
        }
        return url;
    }

    /**
     * Invalidates pages in the cache based on which objects are updated
     */
    @PUT
    @DELETE
    @POST
    @URLPattern("**")
    @Priority(CACHE_PRIORITY)
    public void handleUpdatedObjects() {
        ThreadContext.getDependencyMap().beginTransaction(false);
        try {
            next();
            ThreadContext.getDependencyMap().mergeTransaction();
        } finally {
            ThreadContext.getDependencyMap().removeTransaction();
        }
    }

    /**
     * Invoked automatically from generated XML pages for refreshing active
     * sessions.
     */
    @Priority(PRE_CACHE)
    public String touchSessions(Session[] s) {
        // just preparing the session objects is enough to refresh them
        return "";
    }

    /**
     * Invoked automatically from generated XML pages for executing a handler.
     */
    @Priority(HANDLERS)
    @URLPattern("handlers/*")
    public Object handlers() throws IOException {
        ThreadContext c = ThreadContext.get();
        String referer = getRequestReferer(c.getServletRequest());
        Response referer_response = findOrRegenerateRefererResponse(referer);
        String requestURL = c.getRequestURL();
        if (requestURL.endsWith("-validate")) {
            requestURL = requestURL.substring(0, requestURL.length() - "-validate".length());
            AbstractHandler handler = referer_response.getHandler(requestURL);
            if (handler instanceof SubmitHandler) {
                SubmitHandler submitHandler = (SubmitHandler) handler;
                return submitHandler.validate();
            } else {
                return null;
            }
        }
        AbstractHandler handler = referer_response.getHandler(requestURL);

        if (handler == null) {
            throw new BadRequestException("Handler not found: " + c.getRequestURL());
        } else {
            //Transport all sessions from referer. This is useful because people may use
            //sessions declared in the outer method of an anonymous handler. This pattern
            //exists for example in the GuessingGame example
            for (Session s : referer_response.getSessions()) {
                getResponse().addSession(s);
            }
            if (handler instanceof SubmitHandler) {
                SubmitHandler submitHandler = (SubmitHandler) handler;
                Object o = submitHandler.validate();
                if (o != null) {
                    if (o instanceof XML) {
                        o = ((XML) o).getString();
                    }
                    throw new BadRequestException(o.toString());
                }
            }
            Object o = handler.process(referer);
            if (o == null) {
                return new URL(referer);
            }
            return o;
        }
    }

    private Response findOrRegenerateRefererResponse(String referer) throws IOException {
        ThreadContext c = ThreadContext.get();
        HttpServletRequest request = c.getServletRequest();
        HttpServletResponse response = c.getServletResponse();
        Response referer_response = getReferer(referer);
        //If the referer response does not exist, then we try to reconstruct it
        if (referer_response == null || referer_response.getHandler(c.getRequestURL()) == null) {
            log.info("Handler could not be found in the cache. Referer is: " + referer);
            HttpURLConnection con = (HttpURLConnection) new URL(referer)
                    .openConnection();
            final String authorization = "Authorization";
            String auth = request.getHeader(authorization);
            if (auth != null) {
                con.addRequestProperty(authorization, auth);
            }
            String cookie = "cookie";
            String cook = request.getHeader(cookie);
            if (cook != null) {
                con.addRequestProperty(cookie, cook);
            }
            con.addRequestProperty(JWIG_REGENERATE, "Regenerate");
            con.setConnectTimeout(10000);
            con.setReadTimeout(50000);
            con.setUseCaches(false);
            int code = con.getResponseCode();
            con.disconnect();
            if (code != HttpServletResponse.SC_OK) {
                if (code == HttpServletResponse.SC_GONE) {
                    String defunctSessionId = con
                            .getHeaderField(JWIG_DEFUNCT_SESSION);
                    if (defunctSessionId != null) {
                        throw new SessionDefunctException(defunctSessionId);
                    }
                }
                log.error("Referer response not successfully regenerated, response code "
                        + code);
                response.setHeader("Retry-After", "600");
                throw new ServerBusyException("Service unavailable. (Error "
                                        + code
                                        + " while attempting to regenerate referer response.)");
            }
            String regenerate_id = con.getHeaderField(JWIG_REGENERATE);
                HandlerCache handlerCache = ThreadContext.getHandlerCache();
            if (handlerCache.hasResponse(regenerate_id)) {
                referer_response = handlerCache.removeResponse(regenerate_id);
            } else {
                //There has been a request in between, so the handler is in the ordinary cache
                referer_response = getReferer(referer);
            }
            if (referer_response == null) { //This should never happen
                throw new JWIGException("Failed to regenerate and retrieve referer response.");
            }
        }
        return referer_response;
    }

    private boolean hasReferer(HttpServletRequest request) {
        return request.getHeader("Referer") != null;
    }

    private String getRequestReferer(HttpServletRequest request) {
        // TODO: can we rely on Referer? (if not, encode the relevant parts in the submit URL)
        String referer = request.getHeader("Referer");
        if (referer == null) {
            throw new BadRequestException("Referer missing.");
        }
        if (!referer.startsWith("http:") && !referer.startsWith("https:")) {
            throw new BadRequestException("Referer protocol invalid");
        }
        log.info("Referer: " + referer);
        return referer;
    }

    private Response getReferer(String referer) {
        Cache cache = ThreadContext.getCache();
        Response referer_response = cache.get(referer);
        if (referer_response == null) {
            referer_response = cache.get(referer + "<|>"
                    + getWebSite().getCacheAugmentationString());
        }
        return referer_response;
    }

    /**
     * Invokes the next web method in the chain.
     */
    public static Object next() {
        ThreadContext.getDispatcher().invokeNextWebMethod();
        ThreadContext threadContext = ThreadContext.get();
        RuntimeException throwable = threadContext.getThrowable();
        if (throwable != null) {
            threadContext.setThrowable(null);
            throw throwable;
        }
        return threadContext.getCurrentResult();
    }

    /**
     * Returns the web app parameter of the given name.
     */
    @SuppressWarnings("unchecked")
    public <E> E getWebAppParam(String name, Class<E> type) {
        ThreadContext threadContext = ThreadContext.get();
        String objects = threadContext.getWebAppParams().get(name);
        return (E) threadContext.getRequestManager().deserializeArgument(
                new Object[] { objects }, type, name, false, false, null);
    }

    /**
     * Gets the string representation of the web app parameter with this name
     */
    protected String getWebAppParam(String name) {
        return getWebAppParam(name, String.class);
    }

    /*
     * @URLPattern("**")
     * @Priority(MAX_PRIORITY) public XML addSecurityTokens() { Object result =
     * next(); if (result instanceof XML) { XML xml = (XML) result;
     * MessageDigest md; try { md = MessageDigest.getInstance("SHA-1"); } catch
     * (NoSuchAlgorithmException e) { throw new JWIGException(e); } xml =
     * xml.plug("SECUREFORM", ""); NodeList<Element> forms =
     * xml.getElements("//xhtml:form"); xml = xml.gapify("//xhtml:form",
     * "SECUREFORM"); LinkedList<XML> securedForms = new LinkedList<XML>(); for
     * (XML form : forms) { md.reset(); StringBuffer secureValues = new
     * StringBuffer(); for (XML hiddenInput :
     * form.getElements("//xhtml:input[@type='hidden']")) {
     * secureValues.append(Base64.encodeString(hiddenInput.getString("@name")))
     * .append(":");
     * secureValues.append(Base64.encodeString(hiddenInput.getString("@value")))
     * .append(";"); }
     * secureValues.append(getWebSite().getCacheAugmentationString()); String
     * macString =
     * String.valueOf(Base64.encode(md.digest(secureValues.toString()
     * .getBytes()))); String mac =
     * String.valueOf(Base64.encode(md.digest(macString.getBytes()))); XML
     * securityToken =
     * XML.parseTemplate("<input type='hidden' name='securityToken' value=[VAL] />"
     * ).plug("VAL", mac); form = form.appendContent(securityToken);
     * securedForms.add(form); } xml = xml.plugList("SECUREFORM", securedForms);
     * return xml; } return null; }
     */
}
