package dk.brics.jwig.analysis.jaive;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import dk.brics.automaton.Automaton;
import dk.brics.jwig.URLPattern;
import dk.brics.jwig.WebApp;
import dk.brics.jwig.WebSite;
import dk.brics.jwig.analysis.JwigResolver;
import dk.brics.jwig.server.RegisteredMethod;
import dk.brics.jwig.server.RequestManager;

/**
 * The interface for a {@link WebSite}, containing all WebMethods and their
 * containing {@link WebApp} .
 */
public class Interface {

    private final Map<Class<? extends WebApp>, RequestManager> managers;

    /**
     * Map from both classes and methods to the corresponding url pattern
     * automaton. Only used for caching.
     */
    private final IdentityHashMap<Object, Automaton> urlPatternAutomatons;

    /**
     * Constructs a new {@link Interface} for the given {@link WebApp}s and
     * WebMethods. Uses a {@link JwigResolver}.
     * 
     * @param managers
     *            as the relation between {@link WebApp}s and WebMethods
     * @param resolver
     *            as the {@link JwigResolver}to use
     */
    public Interface(Map<Class<? extends WebApp>, RequestManager> managers) {
        this.managers = managers;
        this.urlPatternAutomatons = new IdentityHashMap<Object, Automaton>();
    }

    /**
     * concatenates two {@link Automaton}, with a slash in between.
     * 
     * the accepted language of the new automaton is: "l(prefix)/l(suffix)"
     * 
     * @param prefix
     *            as the prefix {@link Automaton}
     * @param suffix
     *            as the suffix {@link Automaton}
     * @return the concatenated {@link Automaton}
     */
    private Automaton concatenateWithSlash(Automaton prefix, Automaton suffix) {
        return prefix.concatenate(Automaton.makeChar('/')).concatenate(suffix);
    }

    /**
     * Finds the {@link Automaton} for a {@link URLPattern} of a {@link WebApp}.
     * 
     * @param webAppClass
     *            as the {@link WebApp} to analyze
     * @return the {@link Automaton} representing the {@link URLPattern} of the
     *         {@link WebApp}
     */
    private Automaton constructURLPatternAutomaton(
            Class<? extends WebApp> webAppClass) {
        URLPattern urlPattern = webAppClass.getAnnotation(URLPattern.class);
        String pattern;
        if (urlPattern != null)
            pattern = urlPattern.value();
        else
            /**
             * the same default as
             * {@link RequestManager#introspectWebAppClass(Class)}.
             */
            pattern = webAppClass.getCanonicalName().replace('.', '/');

        return new URLPatternToAutomatonConverter().convert(pattern);
    }

    /**
     * Finds the {@link Automaton} for a {@link URLPattern} of a WebMethod.
     * 
     * @param method
     *            as the WebMethod to analyze
     * @return the {@link Automaton} representing the {@link URLPattern} of the
     *         WebMethod
     */
    private Automaton constructURLPatternAutomaton(Method method) {
        URLPattern urlPattern = method.getAnnotation(URLPattern.class);
        String pattern;
        if (urlPattern != null)
            pattern = urlPattern.value();
        else
            pattern = method.getName();
        final Automaton converted = new URLPatternToAutomatonConverter()
                .convert(pattern);

        return converted;
    }

    /**
     * Caching method for finding the {@link Automaton} for a {@link URLPattern}
     * of a {@link WebApp}.
     * 
     * @param webAppClass
     *            as the {@link WebApp} to analyze
     * @return the {@link Automaton} representing the {@link URLPattern} of the
     *         {@link WebApp}
     */
    private Automaton getURLPatternAutomaton(Class<? extends WebApp> webAppClass) {
        Automaton automaton = urlPatternAutomatons.get(webAppClass);
        if (automaton == null) {
            automaton = constructURLPatternAutomaton(webAppClass);
            urlPatternAutomatons.put(webAppClass, automaton);
        }
        return automaton;
    }

    /**
     * Caching method for finding the {@link Automaton} for a {@link URLPattern}
     * of a WebMethod.
     * 
     * @param method
     *            as the WebMethod to analyze
     * @return the {@link Automaton} representing the {@link URLPattern} of the
     *         WebMethod
     */
    private Automaton getURLPatternAutomaton(Method method) {
        Automaton automaton = urlPatternAutomatons.get(method);
        if (automaton == null) {
            automaton = constructURLPatternAutomaton(method);
            urlPatternAutomatons.put(method, automaton);
        }
        return automaton;
    }

    /**
     * @return the {@link WebApp}s for the Interface.
     */
    public Set<Class<? extends WebApp>> getWebApps() {
        Set<Class<? extends WebApp>> apps = new HashSet<Class<? extends WebApp>>();
        for (Class<? extends WebApp> webApp : managers.keySet()) {
            apps.add(webApp);
        }
        return apps;

    }

    /**
     * Constructs the {@link FilterGroup} corresponding to the URL of the
     * WebMethod in a {@link WebApp}.
     * 
     * @param webMethod
     *            as the WebMethod to extract an URL from.
     * @return the corresponding {@link FilterGroup}
     */
    public FilterGroup getFilterGroup(Method webMethod) {
        Set<Method> urlPatternMatches = new HashSet<Method>();
        @SuppressWarnings("unchecked")
        Automaton prefixedURLPattern = concatenateWithSlash(
                getURLPatternAutomaton((Class<? extends WebApp>) webMethod
                        .getDeclaringClass()),
                getURLPatternAutomaton(webMethod));
        // for each webmethod in the webapps, check if it could be hit by the
        // possible url patterns
        for (Entry<Class<? extends WebApp>, RequestManager> entry : managers
                .entrySet()) {
            for (RegisteredMethod registeredMethod : entry.getValue()
                    .getWebMethods()) {
                final Method possibleTargetedMethod = registeredMethod
                        .getMethod();
                if (!webMethod.equals(possibleTargetedMethod)) {
                    Automaton prefixedPossibleTarget = concatenateWithSlash(
                            getURLPatternAutomaton(entry.getKey()),
                            getURLPatternAutomaton(possibleTargetedMethod));
                    final boolean intersects = !prefixedURLPattern
                            .intersection(prefixedPossibleTarget).isEmpty();
                    if (intersects)
                        urlPatternMatches.add(possibleTargetedMethod);
                }
            }
        }
        return new FilterGroup(webMethod, urlPatternMatches);
    }

    /**
     * @return all WebMethods in the {@link WebSite}
     */
    public Set<RegisteredMethod> getRegisteredMethods() {
        Set<RegisteredMethod> methods = new HashSet<RegisteredMethod>();
        for (RequestManager manager : managers.values()) {
            methods.addAll(manager.getWebMethods());
        }
        return methods;
    }

    /**
     * Finds all WebMethods and Filters in the {@link WebApp} with names in the
     * language of the supplied automaton.
     * 
     * @param webApp
     *            as the {@link WebApp} to find the WebMethods in
     * @param the
     *            {@link Automaton} to match with
     * @return the WebMethods of the {@link WebApp} whose names are matched by
     *         the {@link Automaton}
     */
    public Set<Method> getWebMethodsByName(Class<? extends WebApp> webAppClass,
            Automaton automaton) {
        final RequestManager manager = managers.get(webAppClass);
        if (manager == null)
            throw new IllegalArgumentException("WebApp: "
                    + webAppClass.getName() + " has not been registered");

        HashSet<Method> methodNameMatches = new HashSet<Method>();
        final List<RegisteredMethod> localWebMethods = manager.getWebMethods();
        for (RegisteredMethod methodNameMatch : localWebMethods) {
            final Method method = methodNameMatch.getMethod();
            if (automaton.run(method.getName())) {
                methodNameMatches.add(method);
            }
        }
        return methodNameMatches;
    }
}
