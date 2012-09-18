package dk.brics.jwig.analysis.jaive;

import java.lang.reflect.Method;

import dk.brics.jwig.analysis.JwigResolver;
import dk.brics.jwig.analysis.jaive.feedback.Feedback;

public class URLToWebmethodTargetsTwoWebMethods implements Feedback {

    private final String message;

    public URLToWebmethodTargetsTwoWebMethods(Method webMethod, Method nonFilter) {
        JwigResolver resolver = JwigResolver.get();
        String webMethodSig = resolver.getSootMethod(webMethod).getSignature();
        String nonFilterSig = resolver.getSootMethod(nonFilter).getSignature();
        message = "A URL to the webmethod: " + webMethodSig
                + " could also hit another webmethod: " + nonFilterSig;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public FeedbackType getType() {
        return FeedbackType.WARNING;
    }

}
