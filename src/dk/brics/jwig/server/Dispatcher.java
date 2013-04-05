package dk.brics.jwig.server;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import dk.brics.jwig.FileField;
import dk.brics.jwig.JWIGException;
import dk.brics.jwig.Response;
import dk.brics.jwig.WebApp;
import dk.brics.jwig.WebSite;
import dk.brics.jwig.persistence.JwigCurrentSessionContext;
import dk.brics.jwig.persistence.JwigCurrentSessionContextProxy;
import dk.brics.jwig.server.cache.DependencyMap;
import dk.brics.xact.XML;

/**
 * Dispatcher servlet.
 * <p/>
 * Connects the JWIG system with the underlying servlet engine.
 */
public final class Dispatcher implements Filter {

    private Logger log;

    private final List<DispatchListener> listeners = new LinkedList<DispatchListener>();
    private static final Collection<String> ALLOWED_METHODS = new HashSet<String>(Arrays.asList("GET", "POST", "TRACE", "PUT", "DELETE", "HEAD", "OPTIONS"));

    private ServletFileUpload fileupload;


    static {
        // set XHTML namespace declarations for XACT
        String XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml";
        XML.getNamespaceMap().put("", XHTML_NAMESPACE);
        XML.getNamespaceMap().put("xhtml", XHTML_NAMESPACE);
    }

    /**
     * Constructs a new dispatcher.
     */
    public Dispatcher() {
        super();
    }

    /**
     * Adds a dispatch event listener.
     */
    public void addListener(DispatchListener d) {
        listeners.add(d);
    }

    /**
     * Invoked by the servlet engine when starting.
     */
    @Override
    public void init(FilterConfig config) throws ServletException {
        try {

            WebApp.context = config.getServletContext();
            // prepare persistence session context
            JwigCurrentSessionContextProxy.registerCallBack(new JwigCurrentSessionContextProxy.ConstructorCallBack() {
                @Override
                public void call(JwigCurrentSessionContext sessionContext) {
                    addListener(sessionContext);
                }
            });

            // find main web site/app class
            String websiteclass = config.getInitParameter("MainClass");
            if (websiteclass == null) {
                websiteclass = "Main";
            }
            final Object o = Dispatcher.class.getClassLoader().loadClass(websiteclass).newInstance();
            WebSite website;
            if (o instanceof WebApp) {
                website = new WebSite() {
                    @Override
                    public void init() {
                        add((WebApp) o);
                    }
                };
            } else if (o instanceof WebSite) {
                website = (WebSite) o;
            } else {
                throw new ServletException("JWIG main class does not extend dk.brics.jwig.WebSite or dk.brics.jwig.WebApp");
            }

            // set thread context, log4j properties, and Hibernate properties
            ThreadContext.setWebSite(website);
            String base_url = Config.get("jwig.base_url", (String) null);
            String base_url_secure = Config.get("jwig.base_url_secure", (String) null);
            boolean hibernate = Config.get("jwig.hibernate", false);
            Properties properties = new Properties();
            properties.putAll(website.getProperties());
            if (!Logger.getRootLogger().getAllAppenders().hasMoreElements()) {
                PropertyConfigurator.configure(properties);
            }
            log = Logger.getLogger(Dispatcher.class);
            log.info("Initializing {"
                     + "MainClass=" + websiteclass
                     + ", jwig.hibernate=" + hibernate
                     + (base_url != null ? ", jwig.base_url=" + base_url : "")
                     + (base_url_secure != null ? ", jwig.base_url_secure=" + base_url_secure : "")
                     + "}");
            website.getQuerier().preInit(properties);
            website.init();
            if (!Logger.getRootLogger().getAllAppenders().hasMoreElements()) {
                //Make absolutely sure that at least some logging can happen
                BasicConfigurator.configure();
                log.warn("Log4J logging not configured by the application. Initializing Log4J with a default setup.");
            }

            website.getQuerier().postInit();
            List<RequestManager> requestmanagers = new ArrayList<RequestManager>();
            ThreadContext.init(base_url, base_url_secure, website.getCache(), requestmanagers, new SessionManager(config.getServletContext()),
                               new DependencyMap(), config.getServletContext().getRealPath("/"), this);

            // register request manager for each web app
            for (WebApp app : website.getWebApps()) {
                requestmanagers.add(new RequestManager(app));
                addListener(app.getSecurityManager());
            }

            int fileupload_memory_threshold = Config.get("jwig.fileupload_memory_threshold", 100000);
            String fileupload_tmpdir = Config.get("jwig.fileupload_tmpdir", System.getProperty("java.io.tmpdir"));
            long multipart_maxsize = Config.get("jwig.multipart_maxsize", 100000000L);
            long fileupload_maxsize = Config.get("jwig.fileupload_maxsize", -1L);

            File repository = new File(fileupload_tmpdir);
            if (!repository.isAbsolute()) {
                repository = new File(ThreadContext.getServletHome(), fileupload_tmpdir);
            }
            fileupload = new ServletFileUpload(new DiskFileItemFactory(fileupload_memory_threshold, repository));
            fileupload.setSizeMax(multipart_maxsize);
            fileupload.setFileSizeMax(fileupload_maxsize);
            log.debug("Initializing {jwig.fileupload_memory_threshold=" + fileupload_memory_threshold +
                      ", jwig.fileupload_tmpdir=" + fileupload_tmpdir +
                      ", jwig.multipart_maxsize=" + multipart_maxsize +
                      ", jwig.fileupload_maxsize=" + fileupload_maxsize +
                      "}");

            website.postInit();
        } catch (Exception e) {
            if (log != null) {
                log.fatal("Exception in init", e);
            } else {
                e.printStackTrace();
            }
            throw new UnavailableException("Web site initialization error - see the server log", 0);
        }
    }

    /**
     * Invoked by the servlet engine when stopping.
     */
    @Override
    public void destroy() {
        log.info("Stopping");
        ThreadContext.getWebSite().getQuerier().close();
        ThreadContext.destroy();
    }

    /**
     * Invoked by the clients via the servlet engine on each request.
     */
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        try {
            // inform listeners
            for (DispatchListener l : listeners) {
                l.threadDispatched(new ThreadDispatchEvent(Thread.currentThread()));
            }

            // forward Comet requests to Synchronizer
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) resp;
            if (request.getServletPath().equals("/==")) {
                chain.doFilter(req, resp);
                return;
            }

            // handle error codes
            WebSite website = ThreadContext.getWebSite();
            Object sc = request.getAttribute("javax.servlet.error.status_code");
            if (sc != null) {
                int status_code = (Integer) sc;
                String error_url = (String) request.getAttribute("javax.servlet.error.request_uri");
                log.info("Status code " + status_code);
                ThreadContext.set(new ThreadContext(request, response, error_url));
                switch (status_code) { // TODO: need to cover more status codes? (should match web.xml)
                    case HttpServletResponse.SC_NOT_FOUND:
                        website.sendError(status_code, "The requested resource is not found: " + error_url);
                        ThreadContext.get().getResponse().write(request, response);
                        break;
                    case HttpServletResponse.SC_FORBIDDEN:
                        website.sendError(status_code, "Access to the requested resource is forbidden: " + error_url);
                        ThreadContext.get().getResponse().write(request, response);
                        break;
                    default:
                        log.error("Unexpected status code " + status_code);
                }
                return;
            }

            // first phase of parsing the request
            String url = getFullRequestURL(request);
            String method = request.getMethod();
            ThreadContext.set(new ThreadContext(request, response, url));
            log.info(method + " from " + getClient(request) + ": " + url);
            if (!ALLOWED_METHODS.contains(method)) {
                log.info("HTTP method not allowed: " + method);
                website.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "HTTP method " + method + " not allowed.");
                ThreadContext.get().getResponse().write(request, response);
                return;
            }
            //url = URLEncoding.decode(url);
            String path = request.getRequestURI().substring(request.getContextPath().length());
            ThreadContext context = new ThreadContext(request, response, url);
            ThreadContext.set(context);

            //Add all web methods that may answer to the URL provided
            SortedSet<RegisteredMethod> allMethods = new TreeSet<RegisteredMethod>();
            for (RequestManager r : ThreadContext.getRequestManagers()) {
                if (r.matches(path)) {
                    allMethods.addAll(r.getWebMethods());
                }
            }
            context.setMatchedWebMethods(new LinkedList<RegisteredMethod>(allMethods));

            // try all request managers until one takes the request

            if (process(path, url, request, response)) {
                if (!response.isCommitted()) {
                    log.error("No response generated");
                    website.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No response.");
                    ThreadContext.get().getResponse().write(request, response);
                }
                log.info("Completed");
                return;
            }

            // try default servlet to serve static resources
            log.info("Forwarding to default servlet for static resources");
            chain.doFilter(req, resp);

        } catch (RuntimeException t) {
            log.error("Exception", t);
            throw t;
        } finally {
            ThreadContext.set(null);
            for (DispatchListener l : listeners) {
                l.threadDismissed(new ThreadDispatchEvent(Thread.currentThread()));
            }
        }
    }

    /**
     * Processes the given request with this web app. The web app should match the request.
     *
     * @return true if processed by this request manager
     */
    public boolean process(String path, String url, HttpServletRequest request, HttpServletResponse response) {
        ThreadContext oc = ThreadContext.get();
        ThreadContext threadContext = new ThreadContext(request, response, url, new Response(),
                                                        getServletParams(request), path, oc);
        threadContext.createInvocationContext(null, null, null, -1);
        threadContext.setRequestManager(null);

        try {
            ThreadContext.set(threadContext);
            invokeNextWebMethod();
            Object result = threadContext.getResultIfAny();
            if (result != null) {
            	if (!response.isCommitted()) {
            		send(threadContext.getReferer(), result);
            	}
            }
        } finally {
            ThreadContext.set(oc);
        }
        return response.isCommitted();

    }

    /**
     * Invokes the next applicable web method.
     */
    public void invokeNextWebMethod() {
        ThreadContext c = ThreadContext.get();
        String path = c.getFullRequestPath();
        LinkedList<RegisteredMethod> skippedList = new LinkedList<RegisteredMethod>();

        while (true) {
            LinkedList<RegisteredMethod> webmethods = c.getMatchedWebMethods();
            if (webmethods.isEmpty()) {
                break;
            }

            RegisteredMethod rm = webmethods.removeFirst();
            RequestManager manager = rm.getRequestManager();


            RequestManager oldManager = c.getRequestManager();
            try {
                c.setRequestManager(manager);
                if (!c.isDone()) {
                    int prefixLength = c.getCurrentInvocationContext().getPrefixLength();

                    Object[] args = manager.match(rm, path.substring(prefixLength));
                    if (args != null) {
                        try {
                            for (DispatchListener l : listeners) {
                                l.webMethodDispatched(new WebMethodDispatchEvent(rm.getRequestManager().getWebApp(), rm.getMethod().getName(), args));
                            }
                            manager.invoke(rm.getMethod(), manager.getWebApp(), args);
                        }
                        finally {
                            c.setDone(true);
                        }
                    }
                } else {
                    skippedList.add(rm);
                }
            } finally {
                c.setRequestManager(oldManager);
            }
        }
        c.setMatchedWebMethods(skippedList);
    }

    /**
     * Finds the servlet parameters (application/x-www-form-urlencoded or multipart/form-data).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object[]> getServletParams(HttpServletRequest request) {
        if (request.getMethod().equals("POST") && ServletFileUpload.isMultipartContent(request)) {
            Map<String, List<Object>> paramslists = new HashMap<String, List<Object>>();
            try {
                List<FileItem> parsed_request = ThreadContext.get().getParsed_request();
                if (parsed_request == null) {
                    parsed_request = fileupload.parseRequest(request);
                    ThreadContext.get().setParsed_request(parsed_request);
                }
                for (FileItem aParsed_request : parsed_request) {
                    String name = aParsed_request.getFieldName();
                    Object value;
                    if (aParsed_request.isFormField()) {
                        try {
                            value = aParsed_request.getString("UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            throw new JWIGException(e);
                        }
                    } else {
                        value = new FileField(aParsed_request);
                    }
                    List<Object> ps = paramslists.get(name);
                    if (ps == null) {
                        ps = new ArrayList<Object>();
                        paramslists.put(name, ps);
                    }
                    ps.add(value);
                }
                Map<String, Object[]> params = new HashMap<String, Object[]>();
                for (Map.Entry<String, List<Object>> es : paramslists.entrySet()) {
                    params.put(es.getKey(), es.getValue().toArray());
                }
                return params;
            } catch (FileUploadException e) {
                throw new JWIGException(e);
            }
        } else {
            if (request.getCharacterEncoding() == null) {
                try {
                    request.setCharacterEncoding("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new JWIGException(e);
                }
            }
            return request.getParameterMap();
        }
    }

    private void send(String referer, Object result) {
        ThreadContext c = ThreadContext.get();
        HttpServletResponse response = c.getServletResponse();
        if (result == null && referer != null && !response.isCommitted()) {
            try {
                log.info("Sending empty response");
                response.setContentLength(0);
                response.flushBuffer();
            } catch (IOException e) {
                log.warn("IOException when sending empty response: " + e.getMessage());
            }
        }
        if (result != null) {
            if (result instanceof XML || result instanceof String) {
                Response r = new Response(c.getResponse());
                r.setResult(result);
                send(r);
            } else if (result instanceof URL) {
                try {
                    response.sendRedirect(result.toString());
                } catch (IOException e) {
                    throw new JWIGException(e);
                }
            } else { // TODO: support other return types? (e.g. binary data?)
                throw new JWIGException("Unrecognized return type of web method");
            }
        }
    }


    /**
     * Sends the given response.
     */
    public void send(Response r) {
        ThreadContext c = ThreadContext.get();
        c.setResponse(r);
        if (!c.getServletResponse().isCommitted()) {
            log.info("Sending response page");
            r.write(c.getServletRequest(), c.getServletResponse());
        } else {
            log.debug("Response already committed");
        }
    }

    /**
     * Describes the client location (for logging).
     */
    public static String getClient(HttpServletRequest request) {
        String client = request.getHeader("X-Forwarded-For");
        if (client != null) {
            client += " (via proxy)";
        } else {
            client = request.getRemoteHost() + ":" + request.getRemotePort();
        }
        return client;
    }

    /**
     * Returns the full request URL.
     */
    private String getFullRequestURL(HttpServletRequest request) {
        StringBuffer b = request.getRequestURL();
        String base = ThreadContext.getBaseURL(request.isSecure());
        if (base.length() > 0 && b.length() > 9 && b.indexOf("/", 9) > 0) {
            b.delete(0, b.indexOf("/", 9)).insert(0, base);
        }
        String query = request.getQueryString();
        if (query != null) {
            b.append('?').append(query);
        }
        return b.toString();
    }
}
