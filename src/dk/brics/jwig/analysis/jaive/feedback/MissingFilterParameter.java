package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootMethod;
import dk.brics.jwig.analysis.Parameter;

public class MissingFilterParameter extends AbstractFeedback {

    public MissingFilterParameter(Parameter p, SootMethod caller) {
        message = "The parameter \""
                + p.getName()
                + "\" in the method"
                + p.getHost().getSignature()
                + " is required. But it is not present when the filter is invoked from "
                + caller.getSignature();
    }
}
