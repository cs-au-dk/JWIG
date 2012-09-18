package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Stmt;

public class UnregisteredWebAppReference extends AbstractFeedback {

    public UnregisteredWebAppReference(SootClass targetedWebApp,
            SootMethod enclosingMethod, Stmt statement) {
        message = "MakeURL contains a class argument " + targetedWebApp
                + " which is not a registered web app in "
                + SourceUtil.getLocation(enclosingMethod, statement);
    }
}
