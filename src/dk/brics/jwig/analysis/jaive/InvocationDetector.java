package dk.brics.jwig.analysis.jaive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import soot.Body;
import soot.Local;
import soot.RefType;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.internal.JArrayRef;
import soot.jimple.internal.JCastExpr;
import soot.jimple.internal.JNewExpr;
import soot.toolkits.graph.CompleteUnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
import dk.brics.automaton.Automaton;
import dk.brics.jwig.WebContext;
import dk.brics.jwig.analysis.JwigResolver;
import dk.brics.jwig.analysis.graph.AnyTransition;
import dk.brics.jwig.analysis.graph.FilterState;
import dk.brics.jwig.analysis.graph.HandlerTransition;
import dk.brics.jwig.analysis.graph.LambdaTransition;
import dk.brics.jwig.analysis.graph.State;
import dk.brics.jwig.analysis.graph.StateMachine;
import dk.brics.jwig.analysis.graph.Transition;
import dk.brics.jwig.analysis.graph.WebMethodState;
import dk.brics.jwig.analysis.jaive.feedback.AnyTransitionUsed;
import dk.brics.jwig.analysis.jaive.feedback.Feedbacks;
import dk.brics.jwig.analysis.jaive.feedback.NonConstantGapName;
import dk.brics.jwig.analysis.jaive.feedback.SubmitHandlerWithMultipleRunmethods;
import dk.brics.jwig.analysis.jaive.feedback.SubmitHandlerWithoutRunmethod;
import dk.brics.jwig.analysis.xact.PlugDetector;
import dk.brics.jwig.server.RegisteredMethod;
import dk.brics.xact.XML;

public class InvocationDetector {

    private final JwigResolver resolver;

    public InvocationDetector() {
        resolver = JwigResolver.get();
    }

    private static Logger log = Logger.getLogger(InvocationDetector.class);

    public StateMachine detect(Interface interfacee) {
        log.info("Detecting invocations of makeURL");

        StateMachine stateMachine = new StateMachine();
        addRegisteredMethods(interfacee, stateMachine);
        buildCallGraph(stateMachine);
        log.info("Done detecting invocations of makeURL");
        log.info("Analyzing the gap names of XML.plug invocations");
        return stateMachine;
    }

    /**
     * Builds a call graph with multiple origins: the initial and filter states
     * of the {@link StateMachine}
     * 
     * The graph only considers calls which ultimately can lead to an invocation
     * of {@link WebContext#makeURL(Object...)}. These states are marked for
     * later analysis.
     * 
     * @param stateMachine
     *            as the {@link StateMachine} to alter
     */
    private void buildCallGraph(StateMachine stateMachine) {
        final Collection<WebMethodState> initialStates = stateMachine
                .getInitialStates();
        Set<FilterState> filterStates = stateMachine.getFilterStates();
        List<State> states = new ArrayList<State>();
        states.addAll(initialStates);
        states.addAll(filterStates);
        for (State state : states) {
            SootMethod method = state.getMethod();
            if (method.isConcrete()) {
                buildCallGraph(stateMachine, method);
            }
        }
    }

    /**
     * Utility method for #buildCallGraph(StateMachine), which handles a single
     * entry point in the call graph
     * 
     * @see #buildCallGraph(StateMachine)
     */
    private void buildCallGraph(StateMachine stateMachine,
            SootMethod initialMethod) {
        LinkedList<SootMethod> queue = new LinkedList<SootMethod>();
        queue.add(initialMethod);
        while (!queue.isEmpty()) {
            log.debug("Methods-to-visit-queue has a size of " + queue.size());
            SootMethod method = queue.removeFirst();
            final Body body = method.retrieveActiveBody();
            CompleteUnitGraph cug = new CompleteUnitGraph(body);
            for (Unit aCug : cug) {
                assert aCug instanceof Stmt;
                Stmt st = (Stmt) aCug;
                if (st.containsInvokeExpr()) {
                    handlePlug(st, body, stateMachine);
                    queue.addAll(handleInvocation(stateMachine, method, st));
                } else if (st instanceof AssignStmt) {
                    // will not alter control flow, but an unanalyzable rhs
                    // might pop up
                    Value right = ((AssignStmt) st).getRightOp();
                    Type rightType = right.getType();
                    if (resolver.isInterestingType(rightType)
                            && !resolver.isAbstractPersistable(rightType)
                            && canBeAnything(right, rightType,
                                    method.getActiveBody(), st)) {
                        // The rhs can be anything, if it is interesting - we
                        // have to add the AnyTransition.

                        // TODO in the cast-case, the reaching definitions of
                        // the variable could be analyzed - if the actual return
                        // type is 'interesting' (like the WebApp arg is found
                        // for makeURL)

                        State state = stateMachine.getState(method);
                        state.addSuccessor(new AnyTransition());
                        Feedbacks.add(new AnyTransitionUsed(method, st));
                    } else if (right instanceof JNewExpr) {
                        queue.addAll(handleNewExpression(stateMachine, method,
                                st));
                    }
                }
            }
        }
    }

    /**
     * Checks a statement with an {@link InvokeExpr}. If it is an invocation of
     * {@link XML#plug(String, Object)}, the type of the plugged value will be
     * saved for later analysis
     * 
     * 
     * @param st
     *            as the statement to analyze
     * @param body
     *            as the body of the statement
     * @param stateMachine
     *            as the stateMachine to save the results in
     */
    private void handlePlug(Stmt st, Body body, StateMachine stateMachine) {
        assert (st.containsInvokeExpr());
        PlugDetector detector = new PlugDetector(st.getInvokeExpr(), st, body);
        if (detector.isPlug()) {
            Plugging plugging = makePlugging((AssignStmt) st, body, detector);
            if (plugging != null)
                stateMachine.addPlugging(plugging);
        }
    }

    private Plugging makePlugging(AssignStmt st, Body body,
            PlugDetector detector) {
        Set<String> gapNames = getLocalPlugGapNames(st, body);
        if (gapNames.contains(null)) {
            Feedbacks.add(new NonConstantGapName(st, body.getMethod()));
            return null;
        }
        Automaton names = Automaton.makeEmpty();
        for (String string : gapNames) {
            names = names.union(Automaton.makeString(string));
        }
        return new Plugging(st, body.getMethod(), detector.getTypes(), names);
    }

    /**
     * Finds the values which could be used as gap names in an {@link XML#plug}
     * operation. If a value isn't a (local) String constant ("foo"),
     * <code>null</code> will be added to signal unknown.
     * 
     * @param st
     *            as the statement with the plug invocation
     * @param body
     *            as the body of the statement
     * @return the known and unknown values
     */
    private Set<String> getLocalPlugGapNames(Stmt st, Body body) {
        List<Value> values = resolver.getReachingValues(st.getInvokeExpr()
                .getArg(0), st, body);

        Set<String> names = new HashSet<String>();
        for (Value value : values) {
            if (value instanceof StringConstant)
                names.add(((StringConstant) value).value);
            else
                names.add(null);
        }
        return names;
    }

    /**
     * Decides whether an assignment is too hard to analyze - and therefore can
     * be anything.
     * 
     * Casts, array-references, field-references and 'new URL(...)' are too hard
     * to analyze.
     * 
     * @param right
     *            as the {@link Value} of the rhs
     * @param rightType
     *            as the {@link Type} of the rhs
     * @return false if the is worth investigating further.
     */
    private boolean canBeAnything(Value right, Type rightType, Body body,
            Stmt st) {
        final boolean isInterestingCast = right instanceof JCastExpr
                && isInterestingCast((JCastExpr) right, body, st);
        return isInterestingCast
                || right instanceof JArrayRef
                || right instanceof FieldRef
                || (resolver.getURLTypes().contains(rightType) && right instanceof JNewExpr);
    }

    private boolean isInterestingCast(JCastExpr right, Body body, Stmt st) {
        final boolean isNext = originatesFromNextCall(right, body, st);
        return !isNext;
    }

    private boolean originatesFromNextCall(JCastExpr right, Body body, Stmt st) {
        SimpleLocalDefs simpleLocalDefs = new SimpleLocalDefs(
                new ExceptionalUnitGraph(body));
        List<Unit> defsOfAt = simpleLocalDefs.getDefsOfAt(
                (Local) right.getOp(), st);
        // check all definitions, all must be invocations of next()
        for (Unit unit : defsOfAt) {
            if (unit instanceof AssignStmt) {
                AssignStmt assign = (AssignStmt) unit;
                if (assign.containsInvokeExpr()) {
                    InvokeExpr invokeExpr = assign.getInvokeExpr();
                    if (!resolver.isNext(invokeExpr.getMethod()))
                        return false;
                } else
                    // new, cast ...
                    return false;
            }
        }
        return true;
    }

    /**
     * Utility method for {@link #buildCallGraph(StateMachine, SootMethod)},
     * which handles statements with invocations in.
     * 
     * @see #buildCallGraph(StateMachine)
     */
    private List<SootMethod> handleInvocation(StateMachine stateMachine,
            SootMethod method, Stmt st) {
        assert (st.containsInvokeExpr());
        InvokeExpr expr = st.getInvokeExpr();
        List<SootMethod> queue = new ArrayList<SootMethod>();

        soot.Type exprType = expr.getType();
        SootMethod calledM = expr.getMethod();
        Collection<SootMethod> methods = resolver.getPossibleTargets(calledM);
        // go through all implementations of the invoked method -
        // one of them might return an interesting type.

        for (SootMethod calledMethod : methods) {
            boolean isMakeURL = resolver.isMakeURL(calledMethod);
            boolean isNext = resolver.isNext(calledMethod);
            boolean isSafe = resolver.getFlowSafeTypes().contains(
                    calledMethod.getDeclaringClass().getType())
                    && !isMakeURL && !isNext;
            if (isSafe)
                continue;
            // two cases: makeURL(...) or an interesting type is returned:
            State calleeState = stateMachine.getState(method);
            if (resolver.isInterestingType(exprType) && !isMakeURL && //
                    !isNext /* next() calls need not be analyzed */) {
                State calledState = stateMachine.getState(calledMethod);
                // eventually construct the state of the invoked
                // method
                if (calledState == null) {
                    // webmethods has been added already, thus only invocations
                    // of regular methods gives rise to unknown states
                    calledState = stateMachine
                            .createReqularMethodState(calledMethod);
                    // it was new, analyze it later
                    queue.add(calledMethod);
                }
                Transition lambda = new LambdaTransition();
                lambda.setTarget(calledState);
                calleeState.addSuccessor(lambda);
            } else if (isMakeURL) {
                // mark for later analysis
                stateMachine.addMakeURLLocation(expr, method, st);
            }
        }
        return queue;
    }

    /**
     * Utility method for {@link #buildCallGraph(StateMachine, SootMethod)},
     * which handles statements with 'new' expressions in.
     * 
     * @see #buildCallGraph(StateMachine)
     */
    private List<SootMethod> handleNewExpression(StateMachine stateMachine,
            SootMethod method, Stmt st) {
        JNewExpr newInstance = (JNewExpr) ((AssignStmt) st).getRightOp();
        // TODO generalize to AbstractHandler

        // the only instantiation we are interested in is the
        // SubmitHandler instantiation. Any other instantiations
        // will have to have a method invoked at some later
        // point in time, in order to change the control flow.
        // When that happens, it is caught by the analysis.

        RefType type = newInstance.getBaseType();
        SootClass submitClass = type.getSootClass();
        List<SootMethod> queue = new ArrayList<SootMethod>();
        if (resolver.isSubmitHandler(submitClass))
            handleNewSubmitHandler(stateMachine, method, st, submitClass, queue);
        return queue;
    }

    private void handleNewSubmitHandler(StateMachine stateMachine,
            SootMethod method, Stmt st, SootClass submitClass,
            List<SootMethod> queue) {
        {
            List<SootMethod> submitHandlerMethods = submitClass.getMethods();
            List<SootMethod> runMethods = new ArrayList<SootMethod>();
            for (SootMethod run : submitHandlerMethods) {
                if (run.getName().equals("run")) {
                    runMethods.add(run);
                }
            }
            if (runMethods.isEmpty()) {
                Feedbacks.add(new SubmitHandlerWithoutRunmethod(method, st));
            }
            if (runMethods.size() > 1) {
                // current JWIG implementation does not support
                // multiple runmethods
                Feedbacks.add(new SubmitHandlerWithMultipleRunmethods(method,
                        st));
            } else {
                State state = stateMachine.getState(method);
                for (SootMethod runMethod : runMethods) {
                    State handler = stateMachine.getState(runMethod);
                    if (handler == null) {
                        // TODO forbid field access completely in handlers

                        handler = stateMachine.createHandlerState(runMethod);
                        queue.add(runMethod);
                    }
                    Transition t = new HandlerTransition();
                    t.setTarget(handler);
                    state.addSuccessor(t);
                }
            }
        }
    }

    /**
     * Adds initial states to the {@link StateMachine}.
     */
    void addRegisteredMethods(Interface interfacee, StateMachine stateMachine) {
        Collection<RegisteredMethod> methods = interfacee
                .getRegisteredMethods();
        log.info("Adding entry point web methods to the StateMachine");
        for (RegisteredMethod method : methods) {
            SootMethod sootMethod = resolver.getSootMethod(method.getMethod());
            if (!resolver.isFilter(method.getMethod())) {
                WebMethodState state = stateMachine
                        .createWebMethodState(sootMethod);
                stateMachine.addInitialState(state);
            } else {
                FilterState state = stateMachine.createFilterState(sootMethod);
                stateMachine.addFilterState(state);
            }
        }
        log.info("Done adding entry point web methods to the StateMachine");
    }
}
