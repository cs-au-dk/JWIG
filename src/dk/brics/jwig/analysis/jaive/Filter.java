package dk.brics.jwig.analysis.jaive;

import java.lang.reflect.Method;

public class Filter {

    private final int priority;
    private final Method method;
    private final boolean defaultPriority;

    /**
     * @return the defaultPriority
     */
    public boolean isDefaultPriority() {
        return defaultPriority;
    }

    public Filter(Method filter) {
        this.priority = MyPatternMatcher.getPriority(filter);
        this.defaultPriority = MyPatternMatcher.isDefaultPriority(filter);
        this.method = filter;
    }

    /**
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * @return the method
     */
    public Method getMethod() {
        return method;
    }
}
