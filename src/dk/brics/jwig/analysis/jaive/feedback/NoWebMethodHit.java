package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Stmt;
import dk.brics.automaton.Automaton;

public class NoWebMethodHit extends AbstractFeedback {

    public NoWebMethodHit(SootMethod makeURLEnclosingMethod,
            Stmt makeURLstatement, SootClass targetedWebApp,
            Automaton possibleWebMethodNamesAutomaton) {
        message = "MakeURL call in "
                + SourceUtil.getLocation(makeURLEnclosingMethod,
                        makeURLstatement)
                + " does not hit a registered web method. No methods exist in "
                + targetedWebApp.getName() + " matching "
                + getReadableLanguage(possibleWebMethodNamesAutomaton);
    }

    /**
     * Returns the language of the given {@link Automaton}, if the language is
     * finite, the finite set of strings is returned.
     * 
     * @param automaton
     *            as the {@link Automaton} to find a language for
     * @return the language of the {@link Automaton}
     */
    private String getReadableLanguage(Automaton automaton) {
        if (automaton.isFinite())
            return automaton.getFiniteStrings().toString();
        return automaton.toString();
    }
}
