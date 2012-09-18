package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootMethod;
import soot.jimple.Stmt;

public class NonConstantWebAppArgumentUsed extends AbstractFeedback {

    public NonConstantWebAppArgumentUsed(SootMethod enclosingMethod,
            Stmt statement) {
        message = "Non-constant class argument to makeURL "
                + SourceUtil.getLocation(enclosingMethod, statement);
        type = FeedbackType.WARNING;
    }
}
