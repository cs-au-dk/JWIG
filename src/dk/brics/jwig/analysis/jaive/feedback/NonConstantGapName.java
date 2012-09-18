package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootMethod;
import soot.jimple.Stmt;

public class NonConstantGapName extends AbstractFeedback {

    public NonConstantGapName(Stmt statement, SootMethod method) {
        message = "Non constant gap name reference used at: "
                + SourceUtil.getLocation(method, statement);
        type = FeedbackType.WARNING;
    }

}
