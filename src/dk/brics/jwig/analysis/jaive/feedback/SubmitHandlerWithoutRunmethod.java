package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootMethod;
import soot.jimple.Stmt;

public class SubmitHandlerWithoutRunmethod extends AbstractFeedback {

    public SubmitHandlerWithoutRunmethod(SootMethod method, Stmt st) {
        message = "Submithandler used in method "
                + SourceUtil.getLocation(method, st)
                + " declares no run method";

    }
}
