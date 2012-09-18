package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootMethod;
import soot.Type;
import soot.jimple.Stmt;

public class CouldNotInferRecordTypeForVarArgs extends AbstractFeedback {

    public CouldNotInferRecordTypeForVarArgs(SootMethod enclosingMethod,
            Stmt enclosingStatement, Type varArgBase) {
        message = "Could not infer a record type for varargs call array "
                + SourceUtil.getLocation(enclosingMethod, enclosingStatement)
                + ". Assuming all types are of base type " + varArgBase;
        type = FeedbackType.WARNING;
    }

}
