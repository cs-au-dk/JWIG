package dk.brics.jwig.server;

import java.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import dk.brics.jwig.*;
import dk.brics.jwig.server.cache.Cache;
import dk.brics.jwig.server.cache.CacheTransaction;
import dk.brics.jwig.util.RandomString;
import dk.brics.xact.XML;
import org.apache.commons.fileupload.FileItem;

import dk.brics.jwig.server.cache.DependencyMap;
import dk.brics.jwig.server.comet.Synchronizer;

/**
 * Context of a request processing thread. This class is not part of the public JWIG API and should
 * <b>not</b> be used by applications.
 *
 * @see WebContext
 */
public class ThreadContext {

    private static ThreadLocal<ThreadContext> threadcontext = new ThreadLocal<ThreadContext>();

    private static WebSite website;

    private static String baseurl;

    private static String baseurl_secure;

    private static Cache cache;

    private static DependencyMap dependency_map;

    private static List<RequestManager> requestmanagers;

    private static SessionManager sessionmanager;

    private static Synchronizer synchronizer;

    private static String servlethome;

    private static Dispatcher dispatcher;

    private static HandlerCache handlerCache = new HandlerCache();


    private Response response;

    private HttpServletRequest http_request;

    private HttpServletResponse http_response;

    private String requesturl;

    private Map<String, Object[]> servletparams;

    private XMLProducer producer;

    private List<FileItem> parsed_request;

    private String fullRequestPath;

    /**
     * A thread context is punished if it goes wrong
     */
    private boolean punished;

    private String referer;

    private Map<Object, String> cachedPropertyValues = new WeakHashMap<Object, String>();

    private LinkedList<RegisteredMethod> matchedWebMethods;

    private Map<RequestManager, InvocationContext> invocationContexts = new HashMap<RequestManager, InvocationContext>();

    private InvocationContext currentInvocationContext;

    private Map<Object, String> etagMap = new HashMap<Object, String>();

    /**
     * Initializes the shared thread context.
     */
    public static void init(String baseurl, String baseurl_secure, Cache cache,
                            List<RequestManager> requestmanagers, SessionManager sessionmanager,
                            DependencyMap dependency_map, String servlethome, Dispatcher dispatcher) {
        ThreadContext.baseurl = baseurl;
        ThreadContext.baseurl_secure = baseurl_secure;
        ThreadContext.cache = cache;
        ThreadContext.requestmanagers = requestmanagers;
        ThreadContext.sessionmanager = sessionmanager;
        ThreadContext.dependency_map = dependency_map;
        ThreadContext.servlethome = servlethome;
        ThreadContext.dispatcher = dispatcher;
    }

    /**
     * Resets all thread context.
     * Invokes {@link WebSite#destroy()}, {@link dk.brics.jwig.server.cache.HashMapCache#destroy()}, and {@link SessionManager#destroy()}.
     */
    public static void destroy() {
        website.close();
        cache.destroy();
        sessionmanager.destroy();
        website = null;
        cache = null;
        sessionmanager = null;
        threadcontext = null;
    }

    /**
     * Returns the thread context for the current thread.
     */
    public static ThreadContext get() {
        if (!isInRequestContext()) {
            throw new JWIGException("Request specific method called out of request context. This method can only be called when handling "
                                    + "a request and only from the thread that receives the request.");
        }
        return threadcontext.get();
    }

    /**
     * Returns true if the calling thread is bound to a request context, that is the current thread is the receiver thread for
     * a request from the client.
     */
    public static boolean isInRequestContext() {
        return threadcontext.get() != null;
    }

    /**
     * Returns the web site.
     */
    public static WebSite getWebSite() {
        return website;
    }

    /**
     * Returns the list of request managers.
     */
    public static List<RequestManager> getRequestManagers() {
        return requestmanagers;
    }

    /**
     * Returns the session manager.
     */
    public static SessionManager getSessionManager() {
        return sessionmanager;
    }

    /**
     * Sets the web site.
     */
    public static void setWebSite(WebSite website) {
        ThreadContext.website = website;
    }

    /**
     * Sets the synchronizer.
     */
    public static void setSynchronizer(Synchronizer synchronizer) {
        ThreadContext.synchronizer = synchronizer;
    }

    /**
     * Returns the synchronizer.
     */
    public static Synchronizer getSynchronizer() {
        return synchronizer;
    }

    /**
     * Sets the thread context for the current thread.
     */
    public static void set(ThreadContext c) {
        threadcontext.set(c);
    }

    /**
     * Returns the base URL.
     * Should be included before the context path in all self-URLs sent back to the client.
     *
     * @param ssl if true, returns the <code>jwig.base_url_secure</code> parameter, otherwise <code>jwig.base_url</code>
     * @return the base URL if set in the web site configuration, or the empty string if not set
     */
    public static String getBaseURL(boolean ssl) {
        String url;
        if (ssl) {
            url = baseurl_secure;
        } else {
            url = baseurl;
        }
        if (url == null) {
            url = "";
        }
        return url;
    }

    /**
     * Returns the cache.
     */
    public static Cache getCache() {
        return cache;
    }

    /**
     * Returns the dependency map.
     */
    public static DependencyMap getDependencyMap() {
        return dependency_map;
    }

    /**
     * Returns the file system path for the servlet home directory.
     */
    public static String getServletHome() {
        return servlethome;
    }

    /**
     * Creates a new thread context.
     */
    public ThreadContext(
            HttpServletRequest http_request, HttpServletResponse http_response,
            String requesturl, Response response,
            Map<String, Object[]> servletparams,
            String path,
            ThreadContext oc) {
        this.http_request = http_request;
        this.http_response = http_response;
        this.requesturl = requesturl;
        this.response = response;
        this.servletparams = servletparams;
        fullRequestPath = path;

        if (oc != null) {
            this.parsed_request = oc.parsed_request;
            this.matchedWebMethods = oc.matchedWebMethods;
            this.invocationContexts.putAll(oc.invocationContexts);
        }
    }


    /**
     * Creates a new dummy thread context.
     */
    public ThreadContext(HttpServletRequest request, HttpServletResponse response, String requesturl) {
        this(request, response, requesturl, null, null, null, null);
    }

    /**
     * Returns the servlet parameter map.
     */
    public Map<String, Object[]> getServletParams() {
        return servletparams;
    }

    /**
     * Returns the web app request manager.
     */
    public RequestManager getRequestManager() {
        return currentInvocationContext.getRequestmanager();
    }

    /**
     * Sets the response object.
     */
    public void setResponse(Response r) {
        registerETag(r);
        response = r;
    }

    /**
     * Returns the response object.
     */
    public Response getResponse() {
        return response;
    }

    /**
     * Returns the HTTP request.
     */
    public HttpServletRequest getServletRequest() {
        return http_request;
    }

    /**
     * Returns the HTTP response.
     */
    public HttpServletResponse getServletResponse() {
        return http_response;
    }

    /**
     * Returns the request URL.
     */
    public String getRequestURL() {
        return requesturl;
    }

    /**
     * Returns the current XML producer, or null if none.
     */
    public XMLProducer getProducer() {
        return producer;
    }

    /**
     * Sets the current XML producer.
     */
    public void setProducer(XMLProducer producer) {
        this.producer = producer;
    }

    /**
     * Returns the web app parameters.
     */
    public Map<String, String> getWebAppParams() {
        return currentInvocationContext.getWebapp_params();
    }


    public List<FileItem> getParsed_request() {
        return parsed_request;
    }

    public void setParsed_request(List<FileItem> parsed_request) {
        this.parsed_request = parsed_request;
    }

    public boolean isPunished() {
        return punished;
    }

    public void setPunished(boolean punished) {
        this.punished = punished;
    }

    public boolean isCacheAugmented() {
        return currentInvocationContext.isCacheAugmented();
    }

    public void setCacheAugmented(boolean cacheAugmented) {
        currentInvocationContext.setCacheAugmented(cacheAugmented);
    }

    /**
     * Gets the result that is being returned through the filter chain.
     */
    public Object getCurrentResult() {
        return currentInvocationContext.currentResult;
    }

    public Object getResultIfAny() {
        for (InvocationContext c : invocationContexts.values()) {
            if (c.currentResult != null) {
                return c.currentResult;  //XXX: What if there are more of them?
            }
        }
        return null;
    }

    /**
     * Sets the result that will be returned through the filter chain an eventually sent to the client.
     */
    public void setCurrentResult(Object currentResult) {
        currentInvocationContext.currentResult = currentResult;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }


    /**
     * Gets the current mail context using the {@link RequestManager} if the current thread has a request context (see {@link #isInRequestContext()}.
     * If no thread context is available, use the first one that can be found.
     */
    public static javax.mail.Session getEmailSession() {
        javax.mail.Session emailSession;
        try {
            emailSession = get().getRequestManager().getEmailSession();
        } catch (Exception e) {
            emailSession = ThreadContext.getRequestManagers().get(0).getEmailSession();
        }
        return emailSession;
    }

    public Map<Object, String> getCachedPropertyValues() {
        return cachedPropertyValues;
    }

    LinkedList<RegisteredMethod> getMatchedWebMethods() {
        return matchedWebMethods;
    }

    void setMatchedWebMethods(LinkedList<RegisteredMethod> matchedWebMethods) {
        this.matchedWebMethods = matchedWebMethods;
    }

    public static Dispatcher getDispatcher() {
        return dispatcher;
    }

    public String getFullRequestPath() {
        return fullRequestPath;
    }

    void setRequestManager(RequestManager manager) {
        InvocationContext invocationContext = invocationContexts.get(manager);
        if (invocationContext == null) {
            throw new JWIGException("Error in invocation context");
        }
        currentInvocationContext = invocationContext;
    }

    void createInvocationContext(RequestManager requestmanager, WebContext context, Map<String, String> webapp_params, int prefixLength) {
        InvocationContext cont = new InvocationContext(requestmanager, context, webapp_params, prefixLength);
        invocationContexts.put(requestmanager, cont);
    }

    public InvocationContext getCurrentInvocationContext() {
        return currentInvocationContext;
    }

    /**
     * Returns true if the application has generated and returned a response.
     */
    public boolean isDone() {
        return currentInvocationContext.isDone();
    }

    /**
     * Sets the state of the done property. See {@link #isDone()}.
     */
    public void setDone(boolean done) {
        currentInvocationContext.setDone(done);
    }

    public void setCurrentCacheTransaction(CacheTransaction currentCacheTransaction) {
        currentInvocationContext.setCurrentCacheTransaction(currentCacheTransaction);
    }

    public CacheTransaction getCurrentCacheTransaction() {
        return currentInvocationContext.getCurrentCacheTransaction();
    }

    /**
     * Returns or creates an etag for the object o. Returns a random string if o is null
     */
    public String getETag(Object o) {
        if (o instanceof XML) {
            o = ((XML) o).toTemplate();
        }
        if (etagMap.containsKey(o)) {
            return etagMap.get(o);
        }
        String eTag = RandomString.get(10);
        if (o != null) {
            etagMap.put(o, eTag);
        }
        return eTag;
    }

    private void registerETag(Response r) {
        Object o = r.getResult();
        if (o instanceof XML) {
            o = ((XML) o).toTemplate();
        }
        if (!etagMap.containsKey(o))
            etagMap.put(o, r.getETag());
    }

    public static HandlerCache getHandlerCache() {
        return handlerCache;
    }

    public RuntimeException getThrowable() {
        return currentInvocationContext.getThrowable();
    }

    public void setThrowable(RuntimeException throwable) {
        currentInvocationContext.setThrowable(throwable);
    }

    /**
     * The part of the ThreadContext that is specific to a RequestManager and therefore subject to change for each invocation
     */
    class InvocationContext {
        private Map<String, String> webapp_params;
        private RequestManager requestmanager;
        private WebContext currentContext;
        private int prefixLength;
        private Object currentResult;
        private boolean done;
        private CacheTransaction currentCacheTransaction;
        private boolean cacheAugmented;
        private RuntimeException throwable;


        public InvocationContext(RequestManager requestmanager, WebContext context, Map<String, String> webapp_params, int prefixLength) {
            this.requestmanager = requestmanager;
            this.currentContext = context;
            this.webapp_params = webapp_params;
            this.prefixLength = prefixLength;
        }

        public WebContext getCurrentContext() {
            return currentContext;
        }

        public RequestManager getRequestmanager() {
            return requestmanager;
        }

        public Map<String, String> getWebapp_params() {
            return webapp_params;
        }

        public int getPrefixLength() {
            return prefixLength;
        }

        public boolean isDone() {
            return done;
        }

        public void setDone(boolean done) {
            this.done = done;
        }

        public CacheTransaction getCurrentCacheTransaction() {
            return currentCacheTransaction;
        }

        public void setCurrentCacheTransaction(CacheTransaction currentCacheTransaction) {
            this.currentCacheTransaction = currentCacheTransaction;
        }

        public boolean isCacheAugmented() {
            return cacheAugmented;
        }

        public void setCacheAugmented(boolean cacheAugmented) {
            this.cacheAugmented = cacheAugmented;
        }

        public RuntimeException getThrowable() {
            return throwable;
        }

        public void setThrowable(RuntimeException throwable) {
            this.throwable = throwable;
        }
    }
}
