package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootMethod;
import soot.jimple.Stmt;

public class UnanalyzedVarArgs extends AbstractFeedback {

    public UnanalyzedVarArgs(SootMethod enclosingMethod, Stmt enclosingStatement) {
        message = "Could not analyze varargs for call to makeURL "
                + SourceUtil.getLocation(enclosingMethod, enclosingStatement);
        type = FeedbackType.WARNING;
    }

}
