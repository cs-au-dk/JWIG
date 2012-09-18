package dk.brics.jwig.analysis.jaive;

import dk.brics.jwig.analysis.graph.State;
import dk.brics.jwig.analysis.graph.WebMethodState;
import dk.brics.jwig.analysis.jaive.feedback.AbstractFeedback;

public class AmbiguousPriority extends AbstractFeedback {
    public AmbiguousPriority(WebMethodState from, State to1, State to2) {
        this.message = "Ambiguous priority between: "
                + to1.getMethod().getSignature() + " and "
                + to2.getMethod().getSignature()
                + ", they both have a priority of: " + to1.getPriority()
                + ". They are both invoked by "
                + from.getMethod().getSignature();
    }
}
