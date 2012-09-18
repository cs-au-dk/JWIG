package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootMethod;
import soot.jimple.Stmt;

public class AnyTransitionUsed extends AbstractFeedback {

    public AnyTransitionUsed(SootMethod method, Stmt st) {
        message = "Did not analyze the result of "
                + SourceUtil.getLocation(method, st);
        type = FeedbackType.WARNING;
    }

}
