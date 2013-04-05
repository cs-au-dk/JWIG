package dk.brics.jwig.server;

import dk.brics.jwig.*;
import dk.brics.jwig.persistence.Persistable;
import dk.brics.jwig.persistence.Querier;
import dk.brics.jwig.util.ParameterNamer;
import dk.brics.xact.XML;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Request manager for a web application.
 */
public class RequestManager {

    private static final Logger log = Logger.getLogger(RequestManager.class);

    private WebApp webapp;

    private PatternMatcher patternmatcher;

    private List<RegisteredMethod> webmethods;

    private ParamNameGetter paramnamegetter;

    private Map<Class<? extends WebContext>, RegisteredMethod> handlermethods;

    private Map<Class<? extends WebContext>, RegisteredMethod> handlerValidatorMethods;

    private javax.mail.Session emailsession;

    private String[] webapp_paramnames;

    /**
     * Constructs an uninitialized request manager object.
     */
    public RequestManager() {
    }

    /**
     * Constructs a new request manager for the given web app. Finds the web
     * methods and creates pattern matchers.
     */
    public RequestManager(WebApp webapp) {
        this.webapp = webapp;

        // configuration
        handlermethods = new HashMap<Class<? extends WebContext>, RegisteredMethod>();
        handlerValidatorMethods = new HashMap<Class<? extends WebContext>, RegisteredMethod>();
        Class<? extends WebApp> webapp_class = webapp.getClass();
        log.info("Initializing web app: " + webapp_class.toString());

        // find web methods
        introspectWebAppClass(webapp_class);

        // initialize javamail session
        Properties props = new Properties();
        for (Map.Entry<String, Object> e : webapp.getProperties().entrySet()) {
            if (e.getKey().startsWith("mail.")) {
                props.put(e.getKey(), e.getValue());
            }
        }
        if (!props.isEmpty()) {
            log.debug("Initializing email session " + props);
            emailsession = javax.mail.Session.getInstance(props, null); // need
                                                                        // context.xml
                                                                        // for
                                                                        // Tomcat
                                                                        // auto-deploy
        }
    }

    /**
     * Finds the web methods for the given web app class. Also creates a pattern
     * matcher for the web app and finds the web app parameter names.
     */
    public void introspectWebAppClass(Class<? extends WebApp> webapp_class) {
        String urlpattern;
        URLPattern up = webapp_class.getAnnotation(URLPattern.class);
        if (up != null) {
            urlpattern = up.value();
        } else {
            urlpattern = webapp_class.getCanonicalName().replace('.', '/');
        }
        webapp_paramnames = getWebAppParameterNames(
                getSuperClasses(webapp_class)).toArray(new String[0]);
        try {
            patternmatcher = new PatternMatcher(urlpattern, false, true);
        } catch (IllegalArgumentException e) {
            throw new JWIGException("Unable to initialize "
                    + webapp_class.getCanonicalName(), e);
        }
        paramnamegetter = new ParamNameGetter();
        webmethods = new ArrayList<RegisteredMethod>();
        for (Method m : webapp_class.getMethods()) {
            Class<?> returntype = m.getReturnType();
            if (Modifier.isPublic(m.getModifiers())
                    && m.getAnnotation(IgnoreWebMethod.class) == null
                    && !Modifier.isStatic(m.getModifiers())
                    && (returntype.equals(Void.TYPE)
                            || returntype.equals(String.class)
                            || returntype.equals(URL.class)
                            || returntype.equals(XML.class) || m
                            .getAnnotation(URLPattern.class) != null)
                    && m.getDeclaringClass() != Object.class) {
                String p = getMethodURLPattern(m);
                try {
                    PatternMatcher matcher = new PatternMatcher(p, true, false);
                    float priority = getMethodPriority(m, matcher);
                    log.debug("Found web method: " + m.toGenericString()
                            + ", priority=" + priority);
                    Method previouslyRegistered = findWebMethodByName(m
                            .getName());
                    if (previouslyRegistered == null) {
                        webmethods.add(new RegisteredMethod(this, m,
                                paramnamegetter.getParameterNames(m), matcher,
                                priority, getSupportedHttpMethods(m)));
                    } else {
                        throw new JWIGException(
                                String.format(
                                        "Web method %s is overloaded (%s was previously found). This is not allowed in JWIG. If you need two web methods"
                                                + "to match the same URL, you must use a @URLPattern annotation instead",
                                        m.getDeclaringClass()
                                                .getCanonicalName()
                                                + "#"
                                                + m.getName(),
                                        previouslyRegistered
                                                .getDeclaringClass()
                                                .getCanonicalName()
                                                + "#"
                                                + previouslyRegistered
                                                        .getName()));
                    }
                } catch (IllegalArgumentException e) {
                    throw new JWIGException("Unable to initialize "
                            + m.toGenericString(), e);
                }
            }
        }
        Collections.sort(webmethods);
    }

    private Method findWebMethodByName(String methodName) {
        for (RegisteredMethod m : getWebMethods()) {
            if (m.getMethod().getName().equals(methodName))
                return m.getMethod();
        }
        return null;
    }

    private Set<String> getSupportedHttpMethods(Method m) {
        boolean none = true;
        Set<String> methods = new HashSet<String>();
        if (m.getAnnotation(GET.class) != null) {
            methods.add("GET");
            none = false;
        }
        if (m.getAnnotation(POST.class) != null) {
            methods.add("POST");
            none = false;
        }
        if (m.getAnnotation(HEAD.class) != null) {
            methods.add("HEAD");
            none = false;
        }
        if (m.getAnnotation(TRACE.class) != null) {
            methods.add("TRACE");
            none = false;
        }
        if (m.getAnnotation(HEAD.class) != null) {
            methods.add("HEAD");
            none = false;
        }
        if (m.getAnnotation(PUT.class) != null) {
            methods.add("PUT");
            none = false;
        }
        if (m.getAnnotation(DELETE.class) != null) {
            methods.add("DELETE");
            none = false;
        }
        if (m.getAnnotation(OPTIONS.class) != null) {
            methods.add("OPTIONS");
            none = false;
        }
        if (none) {
            methods.add("GET");
            methods.add("POST"); // TODO: only GET?
        }
        return methods;
    }

    /**
     * Finds the web app parameter names.
     */
    private List<String> getWebAppParameterNames(
            List<Class<? extends WebApp>> webapp_classes) {
        LinkedList<String> names = new LinkedList<String>();
        for (Class<? extends WebApp> webapp_class : webapp_classes) {
            String[] paramnames;
            URLPattern pn = webapp_class.getAnnotation(URLPattern.class);
            if (pn != null) {
                PatternMatcher pm = new PatternMatcher(pn.value(), false, true);
                List<String> strings = pm.getParameters();
                paramnames = strings.toArray(new String[strings.size()]);
            } else {
                paramnames = new String[0];
            }
            names.addAll(Arrays.asList(paramnames));
        }
        return names;
    }

    /**
     * Finds the URL pattern string of the given web method (using the method
     * name by default).
     */
    private String getMethodURLPattern(Method m) {
        URLPattern a = m.getAnnotation(URLPattern.class);
        if (a != null) {
            return a.value();
        } else {
            return m.getName();
        }
    }

    /**
     * Finds the priority of the given web method (or computes the default value
     * from the URL pattern and return type).
     */
    private float getMethodPriority(Method m, PatternMatcher pm) {
        float pri;
        Priority a = m.getAnnotation(Priority.class);
        if (a != null) {
            pri = a.value();
        } else {
            pri = pm.computeDefaultPriority();
            if (m.getReturnType().equals(Void.TYPE)) {
                pri += WebContext.PRE_CACHE;
            }
        }
        return pri;
    }

    /**
     * Returns the web app of this web app request manager.
     */
    public WebApp getWebApp() {
        return webapp;
    }

    /**
     * Returns the list of web methods.
     */
    public List<RegisteredMethod> getWebMethods() {
        return webmethods;
    }

    /**
     * Returns the email session.
     */
    public javax.mail.Session getEmailSession() {
        return emailsession;
    }

    /**
     * Returns true if this web app may match requests to the given url
     */
    public boolean matches(String path) {
        HashMap<String, String> webapp_params = new HashMap<String, String>();
        int i = patternmatcher.matchPrefix(path, webapp_params);
        boolean matched = i != -1;
        if (matched) {
            ThreadContext.get().createInvocationContext(this, webapp,
                    webapp_params, i);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Invokes the handler <code>run</code> method.
     */
    public Object invokeHandlerMethod(AbstractHandler handler, String referer) {
        ThreadContext c = ThreadContext.get();
        Class<? extends WebContext> handlerclass = handler.getClass();
        RegisteredMethod rm1 = handlermethods.get(handlerclass);
        if (rm1 == null) {
            Method m = null;
            for (Method dm : handlerclass.getDeclaredMethods()) {
                if (dm.getName().equals("run")) {
                    m = dm;
                    break;
                }
            }
            rm1 = m != null ? new RegisteredMethod(this, m,
                    paramnamegetter.getParameterNames(m),
                    getSupportedHttpMethods(m)) : null;
            handlermethods.put(handlerclass, rm1);
        }
        RegisteredMethod rm = rm1;
        if (rm == null) {
            throw new JWIGException("Handler method not found");
        }
        Object[] args = match(rm, null);
        if (args == null) {
            throw new JWIGException("Handler method arguments mismatch");
        }
        for (Session s : c.getResponse().getSessions()) {
            s.refresh();
        }
        Method method = rm.getMethod();
        invoke(method, handler, args);
        ThreadContext threadContext = ThreadContext.get();
        Object result = threadContext.getCurrentResult();
        threadContext.setReferer(referer);
        if (result == null) {
            try {
                result = new URL(referer);
            } catch (MalformedURLException e) {
                // Won't happen
            }
        }
        return result;
    }

    public Object invokeHandlerValidationMethod(SubmitHandler handler) {
        ThreadContext c = ThreadContext.get();
        Class<? extends WebContext> handlerclass = handler.getClass();
        RegisteredMethod registeredValidatorMethod = handlerValidatorMethods.get(handlerclass);
        if (registeredValidatorMethod == null) {
            Method validator = null;
            for (Method method : handlerclass.getDeclaredMethods()) {
                if (method.getName().equals("validate")) {
                    validator = method;
                    break;
                }
            }
            //It is OK to not have a validator
            if (validator == null) {
                return null;
            }
            registeredValidatorMethod = new RegisteredMethod(this, validator,
                                                             paramnamegetter.getParameterNames(validator),
                                                             getSupportedHttpMethods(validator));
            handlerValidatorMethods.put(handlerclass, registeredValidatorMethod);
        }
        Object[] args = match(registeredValidatorMethod, null);
        if (args == null) {
            throw new JWIGException("Handler method arguments mismatch");
        }
        for (Session s : c.getResponse().getSessions()) {
            s.refresh();
        }
        invoke(registeredValidatorMethod.getMethod(), handler, args);
        Object currentResult = c.getCurrentResult();
        if (currentResult == null) return null;
        return XML.parseTemplate("<div><[C]></div>").plug("C", currentResult);
    }

    /**
     * Invokes the given web/handler method.
     */
    void invoke(Method m, Object thisobj, Object[] args) {
        try {
            log.debug("Invoking " + m.toString());
            /*
             * log.debug("  this-type: " + thisobj.getClass()); for (Object o :
             * args) log.debug("  arg-type: " + (o == null ? "null" :
             * o.getClass()));
             */
            m.setAccessible(true);
            Object result = m.invoke(thisobj, args);
            if (thisobj instanceof EventHandler) {
                ((EventHandler) thisobj)
                        .setLatestReturnValue(result != null ? XML
                                .toXML(result) : null);
            }
            if (result != null) {
                ThreadContext.get().setCurrentResult(result);
                if (isCacheAugmented(m)) {
                    ThreadContext.get().setCacheAugmented(true);
                }
            }
        } catch (IllegalArgumentException e) {
            ThreadContext.get().setThrowable(new JWIGException(e));
        } catch (IllegalAccessException e) {
            ThreadContext.get().setThrowable(new JWIGException(e));
        } catch (InvocationTargetException e) {
            Throwable t = e;
            while ((t instanceof InvocationTargetException || t instanceof JWIGException)
                    && t.getCause() != null) {
                t = t.getCause();
            }
            RuntimeException r;
            if (t instanceof JWIGException) {
                r = (JWIGException) t;
            } else if (t instanceof RuntimeException) {
                r = (RuntimeException) t;
            } else {
                r = new JWIGException(t);
            }
            ThreadContext.get().setThrowable(r);
        } catch (SecurityException e) {
            ThreadContext.get().setThrowable(new JWIGException(e));
        }
    }

    private boolean isCacheAugmented(Method m) {
        return m.getAnnotation(AugmentedCache.class) != null
                || m.getDeclaringClass().getAnnotation(AugmentedCache.class) != null;
    }

    /**
     * Checks whether the given method matches the given path and builds
     * argument array (combined from the path parameters and ordinary servlet
     * parameters). Returns null if no match.
     */
    Object[] match(RegisteredMethod rm, String path) {
        Map<String, String> path_params = new HashMap<String, String>();
        if (path != null && !rm.getPatternMatcher().match(path, path_params)) {
            return null;
        }
        Map<String, Object[]> servletparams = new HashMap<String, Object[]>(ThreadContext.get().getServletParams());
        //The 'submit' parameter is called 'jwig_submit' on the client side to avoid a naming clash
        if (servletparams.containsKey("jwig_submit"))
            servletparams.put("submit",servletparams.remove("jwig_submit"));
        if (!matchHttpMethod(rm)) {
            return null;
        }
        Map<String, Object[]> params;
        if (path_params.isEmpty()) {
            params = servletparams;
        } else {
            params = new HashMap<String, Object[]>();
            Set<String> remaining = new HashSet<String>(path_params.keySet());
            for (Map.Entry<String, Object[]> entry : servletparams.entrySet()) {
                if (!path_params.containsKey(entry.getKey())) {
                    params.put(entry.getKey(), entry.getValue()); // throwing
                                                                  // away
                                                                  // servlet
                                                                  // parameters
                                                                  // whose names
                                                                  // also appear
                                                                  // as path
                                                                  // parameters
                }
            }
            for (String n : remaining) {
                Object[] v = new Object[1];
                v[0] = path_params.get(n);
                params.put(n, v);
            }
        }
        return buildMethodArgs(rm, params);
    }

    private boolean matchHttpMethod(RegisteredMethod rm) {
        String method = ThreadContext.get().getServletRequest().getMethod();
        return rm.getHttpMethods().contains(method);
    }

    /**
     * Builds argument array for a web method and actuals.
     */
    private Object[] buildMethodArgs(RegisteredMethod rm,
            Map<String, Object[]> actuals) {
        Method m = rm.getMethod();
        Class<?>[] formal_types = m.getParameterTypes();
        String[] formal_names = rm.getParameterNames();
        Object[] args = new Object[formal_names.length];
        Map<String, Object[]> actuals2 = new HashMap<String, Object[]>(actuals);
        for (int i = 0; i < formal_names.length; i++) {
            Object arg;
            if (formal_types[i].equals(Parameters.class)) {
                arg = buildParameters(actuals2); // TODO: avoid rebuilding all
                                                 // params multiple times per
                                                 // request
            } else {
                Annotation[] parameterAnnotations = m.getParameterAnnotations()[i];
                boolean required = false;
                for (Annotation a : parameterAnnotations) {
                    if (a.annotationType().equals(RequiredParameter.class)) {
                        required = true;
                    }
                }
                arg = buildMethodArg(actuals2.remove(formal_names[i]),
                        formal_types[i], formal_names[i], m, i, required);
            }
            args[i] = arg;
        }
        if (!actuals2.isEmpty()) {
            log.debug("Ignoring parameters: " + actuals2.keySet());
        }
        return args;
    }

    /**
     * Builds <code>Parameters</code> object from all remaining actuals.
     */
    private Parameters buildParameters(Map<String, Object[]> actuals) {
        LinkedHashMap<String, List<FormField>> argmap = new LinkedHashMap<String, List<FormField>>();
        for (Map.Entry<String, Object[]> e : actuals.entrySet()) {
            List<FormField> list = new ArrayList<FormField>();
            for (Object o : actuals.get(e.getKey())) {
                if (o instanceof String) {
                    list.add(new TextField((String) o));
                } else if (o instanceof FileField) {
                    list.add((FileField) o);
                } else {
                    throw new JWIGException("Unexpected object type");
                }
            }
            argmap.put(e.getKey(), list);
        }
        actuals.clear();
        return new Parameters(argmap);
    }

    /**
     * Builds a single web method argument given a type and actuals.
     */
    @SuppressWarnings("unchecked")
    private Object buildMethodArg(Object[] actuals, Class<?> type, String name,
            Method method, int param, boolean required) {
        final Class<?> retyped = retype(type);
        if (retyped != null)
            type = retyped;

        boolean valueMissing = actuals == null || actuals.length == 0;
        if (name.equals("submit") && valueMissing) {
            throw new JavascriptDisabledException();
        } else if (required && valueMissing) {
            throw new MissingParameterException(name);
        }
        boolean is_array = type.isArray();
        boolean is_collection = Collection.class.isAssignableFrom(type);
        Class<? extends Collection<?>> collection_type = null;
        if (is_array) {
            type = type.getComponentType();
        } else if (is_collection) {
            collection_type = (Class<? extends Collection<?>>) type
                    .asSubclass(Collection.class);
            type = ParameterNamer.getListType(method, param);
        }
        return deserializeArgument(actuals, type, name, is_array,
                is_collection, collection_type);
    }

    private Class<?> retype(Class<?> type) {
        WebApp context = WebApp.get();
        try {
            Method m = context.getClass().getMethod("retype", type);
            if (m == null) {
                // this will never happen, a NoSuchMethodException will be
                // thrown instead!
                return null;
            }
            Class<?> returnType = m.getReturnType();
            if (returnType == Class.class) {
                // invoke a method called "retype" with a single formal
                // parameter of type `type`. The method is invoked with null as
                // concrete argument! This makes sense as the method acts as a
                // map between two types (and thus does not need the value of
                // the actual input), but we want it to look like the access
                // methods.

                // if the method returns null, it has the effect of ignoring the
                // retyping rule
                return (Class<?>) m.invoke(context, new Object[] { null });
            } else {
                return null;
            }

        } catch (NoSuchMethodException e) {
            return null; // on purpose!
        } catch (Exception e) {
            throw new JWIGException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public <E> E deserializeArgument(Object[] actuals, Class<E> type,
            String name) {
        return (E) deserializeArgument(actuals, type, name, false, false, null);
    }

    /**
     * Deserializes an argument, i.e. converts it from string to object.
     */
    public Object deserializeArgument(Object[] actuals, Class<?> type,
            String name, boolean is_array, boolean is_collection,
            Class<? extends Collection<?>> collection_type) {
        boolean is_compound = is_array || is_collection;
        boolean is_primitive = type.isPrimitive();
        String t = type.getName();
        if (is_primitive) {
            if (t.equals("float")) {
                type = Float.class;
            } else if (t.equals("double")) {
                type = Double.class;
            } else if (t.equals("byte")) {
                type = Byte.class;
            } else if (t.equals("short")) {
                type = Short.class;
            } else if (t.equals("int")) {
                type = Integer.class;
            } else if (t.equals("long")) {
                type = Long.class;
            } else if (t.equals("char")) {
                type = Character.class;
            } else if (t.equals("boolean")) {
                type = Boolean.class;
            }
        }
        if (actuals == null) {
            actuals = (Object[]) Array.newInstance(type, 0);
        }
        if (!is_compound) {
            if (actuals.length < 1) {
                if (is_primitive || Session.class.isAssignableFrom(type)) { // TODO:
                                                                            // other
                                                                            // argument
                                                                            // types
                                                                            // require
                                                                            // NonNull?
                    if (type == Boolean.class) {
                        return false; // if a checkbox is unchecked, no value is
                                      // sent
                    }
                    throw new BadRequestException("missing parameter");
                } else {
                    return null;
                }
            }
            if (actuals.length > 1) {
                log.debug("Ignoring additional parameters: " + name);
            }
        }
        if (type.equals(String.class) || FileField.class.isAssignableFrom(type)) {
            if (is_array) {
                Object[] actuals_correct_type = (Object[]) Array.newInstance(
                        type, actuals.length);
                System.arraycopy(actuals, 0, actuals_correct_type, 0,
                        actuals.length);
                return actuals_correct_type;
            } else if (is_collection) {
                return createCollection(collection_type, actuals);
            } else {
                Object actual = actuals[0];
                if (actual instanceof FileField
                        && String.class.isAssignableFrom(type)) {
                    throw new BadRequestException(
                            "You sent a binary field as the "
                                    + name
                                    + " parameter in the request, but a string field was expected");
                }
                if (actual instanceof String
                        && FileField.class.isAssignableFrom(type)) {
                    throw new BadRequestException(
                            "You sent a string field as the "
                                    + name
                                    + " parameter in the request, but a binary field was expected");
                }
                return actual;
            }
        }
        if (!is_compound) {
            if (Persistable.class.isAssignableFrom(type)) {
                return getPersistable(actuals[0],
                        type.asSubclass(Persistable.class),name);
            }
            if (Boolean.class.isAssignableFrom(type)) {
                String value = (String) actuals[0];
                if (value.equals("on")) { // for checkboxes
                    return true;
                }
            }
        }
        try {
            if (Persistable.class.isAssignableFrom(type)) {
                Object[] arg = (Object[]) Array.newInstance(type,
                        actuals.length);
                for (int i = 0; i < actuals.length; i++) {
                    arg[i] = getPersistable(actuals[i],
                            type.asSubclass(Persistable.class),name);
                }
                if (is_array) {
                    return arg;
                } else if (is_collection) {
                    return createCollection(collection_type, arg);
                } else {
                    return arg[0];
                }
            } else if (JSONObject.class.isAssignableFrom(type)) { // For web
                                                                  // services we
                                                                  // want to
                                                                  // support
                                                                  // JSON, the
                                                                  // official
                                                                  // implementation
                                                                  // does not
                                                                  // have a
                                                                  // valueOf
                                                                  // method
                String value = (String) actuals[0];
                try {
                    return new JSONObject(value);
                } catch (JSONException e) {
                    throw new BadRequestException(e.getMessage(), e);
                }
            } else if (URL.class.isAssignableFrom(type)) {
                try {
                    return new URL((String) actuals[0]);
                } catch (MalformedURLException e) {
                    throw new BadRequestException(e.getMessage(), e);
                }
            } else {
                Method valueOf = type.getMethod("valueOf", String.class);
                if (is_array) {
                    Object[] arg = createFromValueOf(actuals, type, valueOf);
                    if (is_primitive) {
                        return setPrimitive(t, arg);
                    } else {
                        return arg;
                    }
                } else if (is_collection) {
                    Object[] arg = createFromValueOf(actuals, type, valueOf);
                    return createCollection(collection_type, arg);
                } else {
                    Object invoke;
                    try {
                        invoke = valueOf.invoke(null, actuals[0]);
                    } catch (InvocationTargetException e) {
                        if (e.getCause() instanceof IllegalArgumentException) {
                            throw new BadRequestException(String.format("Argument %s has incorrect format: %s", name, actuals[0]));
                        } else {
                            throw e;
                        }
                    }
                    return invoke;
                }
            }
        } catch (NoSuchMethodException e) {
            throw new JWIGException("valueOf not found", e); // unreachable,
                                                             // detected during
                                                             // init
        } catch (IllegalAccessException e) {
            throw new JWIGException(e);
        } catch (InvocationTargetException e) {
            Throwable te = e.getCause();
            if (te instanceof JWIGException) {
                throw (JWIGException) te;
            }
            throw new BadRequestException(te.toString(), te);
        }
    }

    /**
     * Creates objects by invoking <code>valueOf</code> on the given actuals.
     */
    private Object[] createFromValueOf(Object[] actuals, Class<?> type,
            Method valueOf) throws IllegalAccessException,
            InvocationTargetException {
        Object[] arg = (Object[]) Array.newInstance(type, actuals.length);
        for (int i = 0; i < actuals.length; i++) {
            arg[i] = valueOf.invoke(null, actuals[i]);
        }
        return arg;
    }

    /**
     * Creates a collection for the given objects.
     */
    private Collection<?> createCollection(
            Class<? extends Collection<?>> collection_type, Object[] args) {
        try {
            collection_type = getDefaultImplementation(collection_type);
            Constructor<? extends Collection<?>> constructor = collection_type
                    .getConstructor(Collection.class); // all collections must
                                                       // declare such a
                                                       // constructor
            return constructor.newInstance(Arrays.asList(args));
        } catch (Exception e) {
            throw new JWIGException(e);
        }
    }

    /**
     * Converts boxed primitives to unboxed.
     */
    private Object setPrimitive(String t, Object[] arg) {
        Object res = null;
        if (t.equals("float")) {
            res = new float[arg.length];
            for (int i = 0; i < arg.length; i++) {
                Array.setFloat(res, i, (Float) arg[i]);
            }
        } else if (t.equals("double")) {
            res = new double[arg.length];
            for (int i = 0; i < arg.length; i++) {
                Array.setDouble(res, i, (Double) arg[i]);
            }
        } else if (t.equals("byte")) {
            res = new byte[arg.length];
            for (int i = 0; i < arg.length; i++) {
                Array.setByte(res, i, (Byte) arg[i]);
            }
        } else if (t.equals("short")) {
            res = new short[arg.length];
            for (int i = 0; i < arg.length; i++) {
                Array.setShort(res, i, (Short) arg[i]);
            }
        } else if (t.equals("int")) {
            res = new int[arg.length];
            for (int i = 0; i < arg.length; i++) {
                Array.setInt(res, i, (Integer) arg[i]);
            }
        } else if (t.equals("long")) {
            res = new long[arg.length];
            for (int i = 0; i < arg.length; i++) {
                Array.setLong(res, i, (Long) arg[i]);
            }
        } else if (t.equals("char")) {
            res = new char[arg.length];
            for (int i = 0; i < arg.length; i++) {
                Array.setChar(res, i, (Character) arg[i]);
            }
        } else if (t.equals("boolean")) {
            res = new boolean[arg.length];
            for (int i = 0; i < arg.length; i++) {
                Array.setBoolean(res, i, (Boolean) arg[i]);
            }
        }
        return res;
    }

    /**
     * Gets default implementations for some interfaces so they may be used
     * directly as a formal parameter. The implementations are as follows:
     * <table>
     * <tr>
     * <th>Interface</th>
     * <th>Default implementation</th>
     * </tr>
     * <tr>
     * <td>java.util.List</td>
     * <td>java.util.ArrayList</td>
     * </tr>
     * <tr>
     * <td>java.util.Set</td>
     * <td>java.util.HashSet</td>
     * </tr>
     * <tr>
     * <td>java.util.Queue</td>
     * <td>java.util.LinkedList</td>
     * </tr>
     * <tr>
     * <td>java.util.Deque</td>
     * <td>java.util.LinkedList</td>
     * </tr>
     * <tr>
     * <td>java.util.SortedSet</td>
     * <td>java.util.TreeSet</td>
     * </tr>
     * </table>
     */
    @SuppressWarnings("unchecked")
    private Class<? extends Collection<?>> getDefaultImplementation(
            Class<? extends Collection<?>> type) {
        Class<?> impl;
        if (type.equals(List.class)) {
            impl = ArrayList.class;
        } else if (type.equals(Set.class)) {
            impl = HashSet.class;
        } else if (type.equals(Deque.class)) {
            impl = LinkedList.class;
        } else if (type.equals(SortedSet.class)) {
            impl = TreeSet.class;
        } else if (type.equals(Queue.class)) {
            impl = LinkedList.class;
        } else {
            impl = type;
        }
        return (Class<? extends Collection<?>>) impl;
    }

    /**
     * Finds a persistable object via the persistable querier.
     */
    private Object getPersistable(Object actual,
            Class<? extends Persistable> type, String parameterName) {
        Class<? extends Persistable> ptype = type.asSubclass(Persistable.class);
        Querier querier = ThreadContext.getWebSite().getQuerier();
        int id = -1;
        Method p = findQueryMethod(ptype);
        if ("".equals(actual)) {
            return null;
        }
        if (p != null) {
            String name = p.getName();
            name = name.substring(3, 4).toLowerCase() + name.substring(4);
            id = querier.getIdFromProperty(ptype, name, (String) actual);
        } else {
            try {
                id = Integer.parseInt(String.valueOf(actual));
            } catch (NumberFormatException e) {
                throw new BadRequestException(String.format("Expected '%s' to be an integer, but it was %s", parameterName, actual));
            }
        }
        return querier.getObject(ptype, id);
    }

    private static Method findQueryMethod(Class<? extends Persistable> ptype) {
        Method p = null;
        for (Method m : ptype.getMethods()) {
            if (m.getAnnotation(QueryProperty.class) != null) {
                p = m;
            }
        }
        return p;
    }

    /**
     * Generates a URL for the given web method within the same web site. The
     * URL uses <code>https</code> if the current request is on a secure
     * connection.
     */
    public URL makeURL(Map<String, ?> webapp_params, String method,
            Object... args) {
        return makeURL(ThreadContext.get().getServletRequest().isSecure(),
                webapp_params, method, args);
    }

    /**
     * Generates a URL for the given web method within the same web site. The
     * URL uses <code>https</code> if the current request is on a secure
     * connection.
     */
    public URL makeURL(String method, Object... args) {
        return makeURL(ThreadContext.get().getServletRequest().isSecure(),
                new HashMap<String, Object>(), method, args);
    }

    /**
     * Generates a URL for the given web method within the same web site.
     */
    public URL makeURL(boolean secure, Map<String, ?> webapp_params,
            String method, Object... args) {
        RequestManager request_manager = null;
        int i = method.lastIndexOf('.');
        String methodname;
        if (i != -1) { // FIXME: drop qualifying class name for a class
                       // constant?
            String classname = method.substring(0, i);
            methodname = method.substring(i + 1);
            for (RequestManager h : ThreadContext.getRequestManagers()) {
                if (classname.equals(h.webapp.getClass().getCanonicalName())) {
                    request_manager = h;
                    break;
                }
            }
            if (request_manager == null) {
                throw new JWIGException("web app not found: " + classname);
            }
        } else {
            request_manager = this;
            methodname = method;
        }
        Map<String, String> exisiting_webapp_params;
        if (webapp_params != null) {
            exisiting_webapp_params = ThreadContext.get().getWebAppParams();
        } else {
            exisiting_webapp_params = new HashMap<String, String>();
            webapp_params = new HashMap<String, Object>();
        }
        for (RegisteredMethod rm : request_manager.webmethods) {
            if (rm.getMethod().getName().equals(methodname)) {
                Map<String, String[]> argmap = new HashMap<String, String[]>();
                String[] paramnames = rm.getParameterNames();
                for (int j = 0; j < args.length; j++) {
                    if (args[j] instanceof Parameters) {
                        Parameters ps = (Parameters) args[j];
                        Map<String, List<FormField>> psmap = ps.getMap();
                        for (Map.Entry<String, List<FormField>> me : psmap
                                .entrySet()) {
                            String[] values = new String[me.getValue().size()];
                            int k = 0;
                            for (FormField ff : me.getValue()) {
                                if (!(ff instanceof TextField)) {
                                    throw new JWIGException(
                                            "non-TextField in Parameters arguments for makeURL");
                                }
                                values[k++] = ff.getValue();
                            }
                            if (argmap.containsKey(me.getKey())) {
                                throw new JWIGException(
                                        "argument map already contains "
                                                + me.getKey());
                            }
                            argmap.put(me.getKey(), values);
                        }
                    } else {
                        String[] values;
                        if (args[j] instanceof Object[]) {
                            Object[] as = (Object[]) args[j];
                            values = new String[as.length];
                            for (int k = 0; k < as.length; k++) {
                                values[k] = makeURLArg(as[k]);
                            }
                        } else {
                            values = new String[1];
                            values[0] = makeURLArg(args[j]);
                        }
                        if (j >= paramnames.length) {
                            throw new JWIGException(
                                    "too many arguments. Expected "
                                            + paramnames.length + " got " + j
                                            + ". At: " + method + "(" + args
                                            + ")");
                        } else {
                            if (argmap.containsKey(paramnames[j])) {
                                throw new JWIGException(
                                        "argument map already contains "
                                                + paramnames[j]);
                            }
                            argmap.put(paramnames[j], values);
                        }
                    }
                }
                try {
                    WebApp containing_app = request_manager.getWebApp();
                    WebApp this_webapp = getWebApp();
                    List<Class<? extends WebApp>> common_super_classes = getSuperClasses(containing_app
                            .getClass());
                    common_super_classes.retainAll(getSuperClasses(this_webapp
                            .getClass()));
                    List<String> commonParameters = getWebAppParameterNames(common_super_classes);
                    for (Map.Entry<String, String> e : exisiting_webapp_params
                            .entrySet()) {
                        String key = e.getKey();
                        if (commonParameters.contains(key)) {
                            argmap.put(key, new String[] { e.getValue() });
                        }
                    }
                    List<String> list = getWebAppParameterNames(getSuperClasses(containing_app
                            .getClass()));
                    for (Map.Entry<String, ?> e : webapp_params.entrySet()) {
                        String paramName = e.getKey();
                        if (!list.contains(paramName)) {
                            throw new JWIGException("Web app " + containing_app
                                    + " does not take parameter " + paramName);
                        }
                        argmap.put(paramName,
                                new String[] { makeURLArg(e.getValue()) });
                    }
                    return new URL(request_manager.getWebAppURL(secure, argmap)
                            + rm.getPatternMatcher().makeURL(argmap, true));
                } catch (MalformedURLException e) {
                    throw new JWIGException(e);
                }
            }
        }
        throw new JWIGException("web method not found: " + methodname);
    }

    /**
     * Returns the super classes of the given web app class.
     */
    @SuppressWarnings("unchecked")
    private List<Class<? extends WebApp>> getSuperClasses(
            Class<? extends WebApp> webapp) {
        if (webapp.isAssignableFrom(WebApp.class)) {
            return new LinkedList<Class<? extends WebApp>>();
        } else {
            List<Class<? extends WebApp>> list = getSuperClasses((Class<? extends WebApp>) webapp
                    .getSuperclass());
            list.add(webapp);
            return list;
        }
    }

    /**
     * Converts a single web method argument into its string representation.
     */
    public static String makeURLArg(Object arg) { // TODO: Move method?
        Map<Object, String> map = ThreadContext.get().getCachedPropertyValues();
        String s = "";
        if (arg instanceof Persistable) {
            Persistable persistable = (Persistable) arg;
            if (map.containsKey(persistable)) {
                s = map.get(persistable);
            } else {
                Class<? extends Persistable> persistableClass = ThreadContext
                        .getWebSite().getQuerier().getBaseType(persistable);
                Method method = findQueryMethod(persistableClass);
                if (method != null) {
                    String name = method.getName();
                    try {
                        // Prefer calling the getter method to use cache if
                        // possible
                        name = name.substring(3);
                        String getterName = "get" + name;
                        Method getterMethod = persistableClass
                                .getMethod(getterName);
                        s = getterMethod.invoke(persistable).toString();
                    } catch (Exception e) {
                        name = name.substring(3, 4).toLowerCase()
                                + name.substring(4);
                        s = ThreadContext
                                .getWebSite()
                                .getQuerier()
                                .getPropertyFromId(persistableClass, name,
                                        persistable.getId());
                    }
                    map.put(persistable, s);
                } else {
                    s = persistable.getId().toString();
                }
            }
        } else if (arg != null) {
            s = arg.toString();
        } else {
            s = null;
        }
        return s;
    }

    /**
     * Returns the URL prefix for this web app. Uses SSL/TLS if the current
     * request uses SSL/TLS.
     */
    public String getWebAppURL(Map<String, String[]> argMap) {
        return getWebAppURL(ThreadContext.get().getServletRequest().isSecure(),
                argMap);
    }

    /**
     * Returns the URL prefix for the web site.
     * 
     * @param secure
     *            if true, use <code>https</code> in the URL otherwise use
     *            <code>http</code>
     */
    public String getWebSiteURL(boolean secure) {
        HttpServletRequest request = ThreadContext.get().getServletRequest();
        StringBuilder b = new StringBuilder();
        String baseurl = ThreadContext.getBaseURL(secure);
        if (baseurl.length() > 0) {
            b.append(baseurl);
        } else { // no jwig.base_url/jwig.base_url_secure set, guess from
                 // request
            String scheme;
            int port;
            if (secure) {
                scheme = "https";
            } else {
                scheme = "http";
            }
            if (scheme.equals(request.getScheme())) {
                port = request.getServerPort();
            } else {
                if (secure) {
                    port = 443;
                } else {
                    port = 80;
                }
            }
            b.append(scheme).append("://").append(request.getServerName());
            if (!((port == 80 && scheme.equals("http")) || (port == 443 && scheme
                    .equals("https")))) {
                b.append(':').append(port);
            }
        }
        b.append(request.getContextPath()).append('/');
        return b.toString();
    }

    /**
     * Returns the URL prefix for this web app.
     * 
     * @param secure
     *            if true, use <code>https</code> in the URL, otherwise use
     *            <code>http</code>
     */
    public String getWebAppURL(boolean secure, Map<String, String[]> argMap) {
        return getWebSiteURL(secure) + getRelativeWebAppURL(argMap);
    }

    /**
     * Returns the URL prefix for this web app, relative to the web site URL.
     */
    private String getRelativeWebAppURL(Map<String, String[]> argmap) {
        return patternmatcher.makeURL(argmap, false);
    }

    public PatternMatcher getPatternmatcher() {
        return patternmatcher;
    }
}
