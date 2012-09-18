package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootMethod;
import soot.jimple.Stmt;

public class DirectFilterInvocation extends AbstractFeedback {

    public DirectFilterInvocation(SootMethod makeURLEnclosingMethod,
            Stmt makeURLstatement, SootMethod sootMethod) {
        message = "Direct invocation of a Filter webmethod is not allowed: "
                + SourceUtil.getLocation(makeURLEnclosingMethod,
                        makeURLstatement) + " -> " + sootMethod.getSignature();
    }
}
