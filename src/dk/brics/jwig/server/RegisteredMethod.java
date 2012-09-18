package dk.brics.jwig.server;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registered method.
 * <p>
 * A registered method is a method with a parameter name list and optionally also a pattern matcher and a priority.
 */
public class RegisteredMethod implements Comparable<RegisteredMethod> {

	private static int next_key;

    private static Map<Method, Integer> methodKeys = new HashMap<Method, Integer>();

    /**
     * If set, each method can only be invoked once even if two web apps are mapped to the same
     * URL and share a method through some super class
     */
    private static boolean singleMethodInvocation;

	private final int key;
	
	private final PatternMatcher patternmatcher;
	
	private final Method method;
	
	private final float priority;
	
	private final String[] paramnames;

    private final RequestManager requestManager;

    /**
     * A list of the http methods that this method can handle. 
     */
    private final Set<String> httpMethods;

    /**
	 * Constructs a new registered method.
	 */
	public RegisteredMethod(RequestManager m, Method method, String[] paramnames, PatternMatcher patternmatcher, float priority, Set<String> httpMethods) {
        if (singleMethodInvocation && methodKeys.containsKey(method)) {
            key = methodKeys.get(method);
        }else{
		    key = next_key++;
            methodKeys.put(method, key);
        }
       	this.patternmatcher = patternmatcher;
		this.method = method;
		this.priority = priority;
		this.paramnames = paramnames;
        this.httpMethods = httpMethods;
        requestManager = m;
    }

	/**
	 * Constructs a new registered method.
	 */
	public RegisteredMethod(RequestManager m, Method method, String[] paramnames, Set<String> httpMethods) {
		this(m, method, paramnames, null, 0f, httpMethods);
	}

	/**
	 * Registered methods are ordered by priority.
	 */
	@Override
	public int compareTo(RegisteredMethod rm) {
		if (priority > rm.priority)
			return -1;
		else if (priority < rm.priority)
			return 1;
		else
			return rm.key - key;
	}

	/**
	 * Returns the pattern matcher.
	 */
	public PatternMatcher getPatternMatcher() {
		return patternmatcher;
	}

	/**
	 * Returns the actual method.
	 */
	public Method getMethod() {
		return method;
	}
	
	/**
	 * Returns the parameter names.
	 */
	public String[] getParameterNames() {
		return paramnames;
	}

    public Set<String> getHttpMethods() {
        return Collections.unmodifiableSet(httpMethods);
    }

    public RequestManager getRequestManager() {
        return requestManager;
    }
}
