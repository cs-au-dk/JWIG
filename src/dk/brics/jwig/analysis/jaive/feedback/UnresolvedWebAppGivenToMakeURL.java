package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootMethod;
import soot.jimple.Stmt;

public class UnresolvedWebAppGivenToMakeURL extends AbstractFeedback {

    public UnresolvedWebAppGivenToMakeURL(SootMethod enclosingMethod,
            Stmt statement) {
        message = "Could not resolve containg class for makeURL target "
                + SourceUtil.getLocation(enclosingMethod, statement);
        type = FeedbackType.WARNING;
    }
}
