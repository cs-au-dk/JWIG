package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootMethod;
import soot.Type;
import soot.jimple.Stmt;

public class CouldNotFindCommonSuperType extends AbstractFeedback {

    public CouldNotFindCommonSuperType(Type oldType, Type type,
            SootMethod enclosingMethod, Stmt enclosingStatement) {
        message = "Could not find common super type of " + oldType + " and "
                + type + ". No record type could be found for makeURL "
                + SourceUtil.getLocation(enclosingMethod, enclosingStatement);
    }

}
