package dk.brics.jwig.analysis.jaive.feedback;

import java.lang.reflect.Method;

import soot.SootMethod;
import dk.brics.jwig.URLPattern;
import dk.brics.jwig.analysis.JwigResolver;

public class UnusedFilter extends AbstractFeedback {

    public UnusedFilter(SootMethod method) {
        Method javaMethod = JwigResolver.get().getJavaMethod(method);
        // will always exist as the method is a filter!
        URLPattern annotation = javaMethod.getAnnotation(URLPattern.class);
        String pattern = annotation.value();
        this.message = "The filter defined by: " + method.getSignature()
                + " (with the URLPattern: '" + pattern + "') is unused";
    }

}
