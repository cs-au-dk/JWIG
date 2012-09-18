package dk.brics.jwig.analysis.jaive;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import dk.brics.jwig.analysis.JwigResolver;
import dk.brics.jwig.analysis.jaive.feedback.Feedbacks;

/**
 * Represents all the consequence
 */
public class FilterGroup {

    private final Method webMethod;
    private final Set<Filter> filters;
    private final int priority;
    private final boolean defaultPriority;

    /**
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    public FilterGroup(Method webMethod, Set<Method> filters) {
        JwigResolver resolver = JwigResolver.get();
        this.webMethod = webMethod;
        this.priority = MyPatternMatcher.getPriority(webMethod);
        this.defaultPriority = MyPatternMatcher.isDefaultPriority(webMethod);
        this.filters = new HashSet<Filter>();
        for (Method filter : filters) {
            if (webMethod.equals(filter))
                throw new IllegalArgumentException(
                        "The filters of a webmethod hit can not contain the webmethod it self");
            if (resolver.isFilter(filter))
                this.filters.add(new Filter(filter));
            else
                Feedbacks.add(new URLToWebmethodTargetsTwoWebMethods(webMethod,
                        filter));
        }
    }

    /**
     * @return the defaultPriority
     */
    public boolean isDefaultPriority() {
        return defaultPriority;
    }

    /**
     * @return the webMethod
     */
    public Method getWebMethod() {
        return webMethod;
    }

    /**
     * @return the filters
     */
    public Set<Filter> getFilters() {
        return filters;
    }
}
