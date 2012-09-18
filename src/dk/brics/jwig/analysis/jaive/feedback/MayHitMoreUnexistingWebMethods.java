package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootMethod;
import soot.jimple.Stmt;
import dk.brics.automaton.Automaton;

public class MayHitMoreUnexistingWebMethods extends AbstractFeedback {

    public MayHitMoreUnexistingWebMethods(SootMethod makeURLEnclosingMethod,
            Stmt makeURLstatement, Automaton slack) {
        message = "MakeURL call in "
                + SourceUtil.getLocation(makeURLEnclosingMethod,
                        makeURLstatement)
                + " may not hit a registered web method. May try to call the method '"
                + slack.getShortestExample(true) + "'";
    }

}
