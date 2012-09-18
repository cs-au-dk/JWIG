package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootMethod;
import soot.jimple.Stmt;

public class SubmitHandlerWithMultipleRunmethods extends AbstractFeedback {
    public SubmitHandlerWithMultipleRunmethods(SootMethod method, Stmt st) {
        this.message = "Submithandler used in method "
                + SourceUtil.getLocation(method, st)
                + " declares multiple run methods";
    }
}
