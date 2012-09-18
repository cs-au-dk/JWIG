package dk.brics.jwig.analysis.jaive.feedback;

import java.util.Arrays;
import java.util.List;

import soot.SootMethod;
import soot.Type;
import soot.jimple.Stmt;

public class MakeURLCallDoesNotTypeCheck extends AbstractFeedback {

    public MakeURLCallDoesNotTypeCheck(Type[] varArgTypes, List<Type> types,
            SootMethod webMethod, SootMethod enclosingMethod,
            Stmt enclosingStatement) {
        message = "Argument types: " + Arrays.toString(varArgTypes) + " from "
                + SourceUtil.getLocation(enclosingMethod, enclosingStatement)
                + " not assignable to formal parameter types " + types
                + " of method " + webMethod.getSignature();
    }

}
