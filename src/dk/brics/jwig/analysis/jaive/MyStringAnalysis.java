package dk.brics.jwig.analysis.jaive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.toolkits.graph.CompleteUnitGraph;
import dk.brics.automaton.Automaton;
import dk.brics.jwig.WebContext;
import dk.brics.jwig.analysis.JwigResolver;
import dk.brics.jwig.analysis.MakeURLSignatureHandler;
import dk.brics.jwig.analysis.graph.StateMachine;
import dk.brics.jwig.analysis.graph.StateMachine.MethodStatementContainer;
import dk.brics.string.StringAnalysis;
import dk.brics.xact.XML;

/**
 * 
 */
public class MyStringAnalysis {
    /**
     * Returns a list containing all expressions occurring as argument to the
     * specified method.
     * 
     * @param targetSignature
     *            the signature of the method to collect arguments to, e.g.
     *            <code>"&lt;java.io.PrintStream: void println(java.lang.String)&gt"</code>
     *            .
     * @param argnum
     *            the index of the argument to the call
     * @return a {@link java.util.List} of {@link soot.ValueBox} objects. It is
     *         not checked that these have valid types.
     */
    private static List<ValueBox> $tringAnalysis$getExps(
            String targetSignature, int argnum) {
        // FIXME this method origins from an older version of StringAnalysis??
        List<ValueBox> list = new ArrayList<ValueBox>();
        // each class
        for (SootClass classs : Scene.v().getApplicationClasses()) {
            // each method in the class
            for (SootMethod method : classs.getMethods()) {
                if (method.isConcrete()) {
                    CompleteUnitGraph cug = new CompleteUnitGraph(
                            method.retrieveActiveBody());
                    // each statement in the unit in the class
                    for (Unit unit : cug) {
                        Stmt stmt = (Stmt) unit;
                        if (stmt.containsInvokeExpr()) {
                            InvokeExpr expr = stmt.getInvokeExpr();
                            final String invokedSignature = expr.getMethod()
                                    .getSignature();
                            if (invokedSignature.equals(targetSignature)) {
                                // we have found an invocation of our target

                                // fetch the argument of the invocation to
                                // analyze
                                ValueBox box = expr.getArgBox(argnum);
                                list.add(box);
                            }
                        }
                    }
                }
            }
        }
        return list;
    }

    private final StringAnalysis analysis;

    private final Logger log = Logger.getLogger(MyStringAnalysis.class);

    private final StateMachine stateMachine;

    public MyStringAnalysis(StateMachine stateMachine, Set<SootClass> classes) {
        this.stateMachine = stateMachine;

        loadStringAnalysisClasses(classes);

        List<ValueBox> boxes = new LinkedList<ValueBox>();
        for (Map.Entry<String, Integer> entry : MakeURLSignatureHandler
                .getMakeURLSignatures().entrySet()) {
            boxes.addAll($tringAnalysis$getExps(entry.getKey(),
                    entry.getValue()));
        }
        boxes.addAll($tringAnalysis$getExps(JwigResolver.get()
                .getXMLPlugSignature(), 0));

        log.info("Performing String Analysis");
        // we need to instantiate with releaseSoot = false, as we use soot too
        this.analysis = new StringAnalysis(boxes, null, null, null, null, false);

        // Hierarchy may have been modified by the recently added classes.
        JwigResolver.get().reload();
    }

    /**
     * Finds all possible webmethodnames present in an invocation of makeURL.
     * 
     * @param expr
     *            as the expression containing
     *            {@link WebContext#makeURL(String, Object...)} (any variant)
     * @return the automaton representing the possible names
     */
    public Automaton getPossibleNameValuesOfMakeURLInvocation(InvokeExpr expr) {
        Integer index = MakeURLSignatureHandler.getMakeURLSignatures().get(
                expr.getMethod().getSignature());
        index = index == null ? -1 : index;

        ValueBox valueBox = expr.getArgBox(index);
        Automaton possibleValues;
        try {
            possibleValues = analysis.getAutomaton(valueBox);
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage() + " \"" + expr.toString() + "\"");
            possibleValues = Automaton.makeEmpty();
        }

        String methodNames = getReadableLanguage(possibleValues);
        SootMethod makeURLMethod = expr.getMethod();
        MethodStatementContainer container = stateMachine
                .getMakeURLLocation(expr);

        SootMethod enclosingMethod = container.getMethod();
        log.debug(makeURLMethod.getName() + " called from "
                + enclosingMethod.getName() + " in "
                + enclosingMethod.getDeclaringClass().getName()
                + " with possible method name arg " + methodNames);

        return possibleValues;
    }

    /**
     * Finds all possible gap names present in an invocation of makeURL.
     * 
     * @param expr
     *            as the expression containing {@link XML#plug(String, Object)}
     * @return the automaton representing the possible names
     */
    public Automaton getPossibleGapNameValuesofXMLPlug(InvokeExpr expr) {
        ValueBox valueBox = expr.getArgBox(0);
        Automaton possibleValues = analysis.getAutomaton(valueBox);

        String methodNames = getReadableLanguage(possibleValues);
        SootMethod plug = expr.getMethod();

        log.debug(plug.getName() + " called from " + expr.getMethod().getName()
                + " with possible gap name arg " + methodNames);
        return possibleValues;
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

    private void loadStringAnalysisClasses(Collection<SootClass> classes) {
        for (SootClass classs : classes) {
            if (!classs.isApplicationClass()) {
                log.info("Added " + classs.getName());
                StringAnalysis.loadClass(classs.getName());
            }
        }
    }
}
