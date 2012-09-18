package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootMethod;
import dk.brics.jwig.analysis.Parameter;

public class PossiblyMissingFilterParameter extends AbstractFeedback {

    public PossiblyMissingFilterParameter(Parameter p, SootMethod caller) {
        message = "The parameter \""
                + p.getName()
                + "\" in the method"
                + p.getHost().getSignature()
                + " is required. But it might not be present when the filter is invoked from "
                + caller.getSignature() + " as it isn't marked as required.";
        type = FeedbackType.WARNING;
    }
}
