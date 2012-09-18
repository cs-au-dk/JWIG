package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootMethod;
import soot.jimple.Stmt;

public class ArityMismatch extends AbstractFeedback {

    public ArityMismatch(SootMethod webMethod, int count, int varArgSize,
            SootMethod enclosingMethod, Stmt enclosingStatement) {
        message = webMethod.getNumberedSubSignature() + " expects " + count
                + " argument" + (count > 1 ? "s" : "") + " but got "
                + varArgSize + " in "
                + SourceUtil.getLocation(enclosingMethod, enclosingStatement);
    }

}
