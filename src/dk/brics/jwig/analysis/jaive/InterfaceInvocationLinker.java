package dk.brics.jwig.analysis.jaive;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import soot.Body;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.ClassConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.toolkits.scalar.SimpleLocalDefs;
import dk.brics.automaton.Automaton;
import dk.brics.jwig.WebApp;
import dk.brics.jwig.WebSite;
import dk.brics.jwig.analysis.JwigResolver;
import dk.brics.jwig.analysis.graph.FilterState;
import dk.brics.jwig.analysis.graph.FilterTransition;
import dk.brics.jwig.analysis.graph.HandlerTransition;
import dk.brics.jwig.analysis.graph.LambdaTransition;
import dk.brics.jwig.analysis.graph.PredecessorResolver;
import dk.brics.jwig.analysis.graph.State;
import dk.brics.jwig.analysis.graph.StateMachine;
import dk.brics.jwig.analysis.graph.StateMachine.MethodStatementContainer;
import dk.brics.jwig.analysis.graph.Transition;
import dk.brics.jwig.analysis.graph.WebMethodState;
import dk.brics.jwig.analysis.graph.WebMethodTransition;
import dk.brics.jwig.analysis.jaive.feedback.DirectFilterInvocation;
import dk.brics.jwig.analysis.jaive.feedback.Feedbacks;
import dk.brics.jwig.analysis.jaive.feedback.MayHitMoreUnexistingWebMethods;
import dk.brics.jwig.analysis.jaive.feedback.NoWebMethodHit;
import dk.brics.jwig.analysis.jaive.feedback.NonConstantWebAppArgumentUsed;
import dk.brics.jwig.analysis.jaive.feedback.UnregisteredWebAppReference;
import dk.brics.jwig.analysis.jaive.feedback.UnresolvedWebAppGivenToMakeURL;
import dk.brics.jwig.analysis.jaive.feedback.UnusedFilter;

/**
 * Links makeURL invocations to WebMethods (and the run-methods of
 * submithandlers).
 */
public class InterfaceInvocationLinker {
    Logger log = Logger.getLogger(InterfaceInvocationLinker.class);
    private final JwigResolver resolver;

    public InterfaceInvocationLinker() {
        this.resolver = JwigResolver.get();
    }

    /**
     * Links makeURL invocations to WebMethods (and the run-methods of
     * submithandlers). This is done by altering the call graph of the
     * {@link WebSite}.
     * 
     * Once done, the {@link StateMachine} represents a call graph containing
     * non-java calls too.
     * 
     * @param interfacee
     *            as the interface of the {@link WebSite} to analyze
     * @param stateMachine
     *            as the (java) call graph
     * @param resolver
     * @return
     */
    public void link(Interface interfacee, StateMachine stateMachine) {
        log.info("Linking makeURL invocations to the WebMethod interfaces");
        // TODO make light weight String constant analysis for makeURL with
        // fallback to StringAnalysis, in the same way as the analysis for
        // XML.plug("foo",o)
        MyStringAnalysis analysis = new MyStringAnalysis(stateMachine,
                getMakeURLInvokingClasses(stateMachine));
        for (InvokeExpr expr : stateMachine.getMakeURLExpressions()) {
            linkMakeURL(stateMachine, expr, analysis, interfacee);
        }
        linkFilterGroups(interfacee, stateMachine);

        log.info("Done linking makeURL invocations to the Registered Methods");
        log.info("Checking for ambiguous priorities");
        PredecessorResolver predecessorResolver = new PredecessorResolver(
                stateMachine);
        // TODO move to checking phase
        for (WebMethodState state : stateMachine.getInitialStates())
            checkPriorities(state);
        // TODO move to checking phase
        for (FilterState state : stateMachine.getFilterStates())
            checkFilterInvocation(state, predecessorResolver);

        log.info("Done checking for ambiguous priorities");
    }

    private Set<SootClass> getMakeURLInvokingClasses(StateMachine stateMachine) {
        Set<SootClass> classes = new HashSet<SootClass>();
        for (MethodStatementContainer container : stateMachine
                .getMakeURLLocations()) {
            SootClass c = container.getMethod().getDeclaringClass();
            classes.add(c);
        }
        return classes;
    }

    private void linkFilterGroups(Interface interfacee,
            StateMachine stateMachine) {
        final Collection<WebMethodState> initialStates = stateMachine
                .getInitialStates();
        for (WebMethodState initialState : initialStates) {
            FilterGroup filterGroup = interfacee.getFilterGroup(resolver
                    .getJavaMethod(initialState.getMethod()));

            // get WebMethod state
            Method method = filterGroup.getWebMethod();
            SootMethod webMethod = resolver.getSootMethod(method);
            State webMethodState = stateMachine.getState(webMethod);
            webMethodState.setDefaultPriority(filterGroup.isDefaultPriority());
            webMethodState.setPriority(filterGroup.getPriority());

            for (Filter filter : filterGroup.getFilters()) {
                // get the Filter state
                final SootMethod filterMethod = resolver.getSootMethod(filter
                        .getMethod());
                State filterState = stateMachine.getState(filterMethod);
                filterState.setPriority(filter.getPriority());
                filterState.setDefaultPriority(filter.isDefaultPriority());

                // link the WebMethod of the FilterGroup to the Filter
                FilterTransition filterTransition = new FilterTransition();
                webMethodState.addSuccessor(filterTransition);
                filterTransition.setTarget(filterState);
            }
        }
    }

    private void checkFilterInvocation(FilterState state,
            PredecessorResolver predecessorResolver) {
        final Set<Transition> predecessors = predecessorResolver
                .getPredecessors(state);
        if (predecessors.isEmpty())
            Feedbacks.add(new UnusedFilter(state.getMethod()));
    }

    /**
     * Checks the {@link FilterTransition}s from a {@link WebMethodState} for
     * ambiguities. If two transitions have the same priority, they are said to
     * have ambiguous priority.
     * 
     * @param state
     *            as the {@link WebMethodState} to check.
     */
    private void checkPriorities(WebMethodState state) {
        Map<Integer, State> priorityMap = new HashMap<Integer, State>();
        for (Transition transition : state.getTransitions()) {
            if (transition instanceof FilterTransition) {
                final State target = transition.getTarget();
                if (!target.isDefaultPriority()) {
                    final Integer priority = target.getPriority();
                    if (priorityMap.containsKey(priority)) {
                        Feedbacks.add(new AmbiguousPriority(state, target,
                                priorityMap.get(priority)));
                    } else {
                        priorityMap.put(priority, target);
                    }
                }
            }
        }
    }

    /**
     * Adds edges from the enclosing method of a makeURL invocation to the
     * target webmethods (plural) of the makeURL. Thus the StateMachine is
     * modified.
     * 
     * @param interfacee
     */
    private void linkMakeURL(StateMachine stateMachine, InvokeExpr expr,
            MyStringAnalysis analysis, Interface interfacee) {

        SootMethod makeURLMethod = expr.getMethod();

        Automaton possibleValues = analysis
                .getPossibleNameValuesOfMakeURLInvocation(expr);

        MethodStatementContainer container = stateMachine
                .getMakeURLLocation(expr);
        SootMethod enclosingMethod = container.getMethod();
        Stmt statement = container.getStatement();

        Set<SootClass> targetedWebApps = getTargetedWebApps(stateMachine, expr,
                makeURLMethod, enclosingMethod, statement, interfacee);

        // makeURL called in an unknown context
        if (targetedWebApps.isEmpty()) {
            Feedbacks.add(new UnresolvedWebAppGivenToMakeURL(enclosingMethod,
                    statement));
        }

        final State callerState = stateMachine.getState(enclosingMethod);
        for (SootClass containingClass : targetedWebApps) {
            Set<Method> webMethods = getTargetedWebMethods(possibleValues,
                    containingClass, enclosingMethod, statement, interfacee);
            for (Method webMethod : webMethods) {
                // create state
                SootMethod method = resolver.getSootMethod(webMethod);
                State webMethodState = stateMachine.getState(method);

                // link
                Transition transition = new WebMethodTransition(expr);
                callerState.addSuccessor(transition);
                transition.setTarget(webMethodState);
            }
        }
    }

    private Set<SootClass> getTargetedWebApps(StateMachine stateMachine,
            InvokeExpr expr, SootMethod makeURLMethod,
            SootMethod enclosingMethod, Stmt statement, Interface interfacee) {
        int classParamPosition = resolver
                .findClassPositionInParameterList(makeURLMethod);
        Set<SootClass> targetedWebApps = new HashSet<SootClass>();
        if (classParamPosition != -1) {
            // class parameter used:
            ValueBox classParameterArg = expr.getArgBox(classParamPosition);
            Value classParameter = classParameterArg.getValue();
            if (classParameter instanceof ClassConstant) {
                // constant class used, add it to the set of containing classes.
                ClassConstant classConstant = (ClassConstant) classParameter;
                targetedWebApps.add(Scene.v().getSootClass(
                        classConstant.getValue().replace('/', '.')));
            } else {
                // not constant: extract the types by hand
                Set<Type> reachingTypes = getReachingWebAppClassDescriptors(
                        classParameter, statement,
                        enclosingMethod.retrieveActiveBody());
                for (Type type : reachingTypes) {
                    // the makeURL signature guarantees that the type is a
                    // WebApp
                    targetedWebApps.add(((RefType) type).getSootClass());
                }
            }
        } else {
            // class parameter not used, find the context:
            targetedWebApps.addAll(findPredecessorWebApp(stateMachine,
                    resolver, enclosingMethod));
        }

        // check if all makeURLs point to registered WebApps
        Collection<SootClass> unregisteredWebApps = new LinkedList<SootClass>();
        final Set<SootClass> webApps = new HashSet<SootClass>(
                getSootClasses(interfacee.getWebApps()));

        for (SootClass targetedWebApp : targetedWebApps) {
            if (!webApps.contains(targetedWebApp)) {
                Feedbacks.add(new UnregisteredWebAppReference(targetedWebApp,
                        enclosingMethod, statement));
                unregisteredWebApps.add(targetedWebApp);
            }
        }
        targetedWebApps.removeAll(unregisteredWebApps);
        return targetedWebApps;
    }

    public Set<Type> getReachingWebAppClassDescriptors(Value value, Stmt st,
            Body body) {
        SimpleLocalDefs simpleLocalDefs = resolver.getSimpleLocalDefs(body);
        return getReachingWebAppClassDescriptors(value, st, simpleLocalDefs,
                body.getMethod());
    }

    private Set<Type> getReachingWebAppClassDescriptors(Value value, Stmt st,
            SimpleLocalDefs simpleLocalDefs, SootMethod enclosingMethod) {
        Set<Type> types = new HashSet<Type>();
        if (value instanceof Local) // recursion case
            types.addAll(getReachingWebAppClassDescriptors((Local) value, st,
                    simpleLocalDefs, enclosingMethod));
        else if (value instanceof ClassConstant) {
            // constant class used
            ClassConstant classConstant = (ClassConstant) value;
            final RefType type = Scene.v()
                    .getSootClass(classConstant.getValue().replace('/', '.'))
                    .getType();
            types.add(type);
        } else {
            // unknown: warn
            Feedbacks
                    .add(new NonConstantWebAppArgumentUsed(enclosingMethod, st));
        }
        return types;
    }

    private Set<Type> getReachingWebAppClassDescriptors(Local local, Stmt st,
            SimpleLocalDefs simpleLocalDefs, SootMethod enclosingMethod) {
        Set<Type> types = new HashSet<Type>();
        List<Unit> defsOfAt = simpleLocalDefs.getDefsOfAt(local, st);
        for (Unit unit : defsOfAt) {
            Value value;
            if (unit instanceof JAssignStmt) {
                JAssignStmt assign = (JAssignStmt) unit;
                value = assign.getRightOp();
            } else if (unit instanceof JIdentityStmt) {
                JIdentityStmt identity = (JIdentityStmt) unit;
                value = identity.getRightOp();
            } else {
                throw new RuntimeException(
                        "getConcreteReachingTypes: unknown type "
                                + unit.getClass() + " at \"" + unit.toString());
            }
            types.addAll(getReachingWebAppClassDescriptors(value, (Stmt) unit,
                    simpleLocalDefs, enclosingMethod));
        }
        return types;
    }

    private List<SootClass> getSootClasses(Set<Class<? extends WebApp>> set) {
        List<SootClass> soots = new ArrayList<SootClass>();
        for (Class<?> classs : set) {
            soots.add(resolver.getSootClass(classs));
        }
        return soots;
    }

    // TODO move this to some class?
    public static Set<SootClass> findPredecessorWebApp(
            StateMachine stateMachine, JwigResolver resolver,
            SootMethod enclosingMethod) {
        Set<SootClass> targetedWebApps = new HashSet<SootClass>();
        PredecessorResolver pred = new PredecessorResolver(stateMachine);
        HashSet<State> seen = new HashSet<State>();
        LinkedList<State> workList = new LinkedList<State>();
        workList.add(stateMachine.getState(enclosingMethod));
        while (!workList.isEmpty()) {
            // move _backwards_ from the current state, gathering any
            // webapps on the way - these can be context
            State state1 = workList.removeFirst();
            SootMethod callingMethod = state1.getMethod();
            SootClass declaringClass = callingMethod.getDeclaringClass();
            if (resolver.isWebApp(declaringClass)) {
                targetedWebApps.add(declaringClass);
            } else {
                for (Transition predecessor : pred.getPredecessors(state1)) {
                    if (predecessor instanceof HandlerTransition
                            || predecessor instanceof LambdaTransition
                            || predecessor instanceof FilterTransition) {
                        State target = predecessor.getTarget();
                        if (!seen.contains(target)) {
                            seen.add(target);
                            workList.add(target);
                        }
                    }
                }
            }
        }
        return targetedWebApps;
    }

    /**
     * Finds all WebMethods which matches a makeURL call by containing webapp
     * and name. Will report errors if some webmethod names doesn't exist.
     * 
     * 
     * @param possibleWebMethodNamesAutomaton
     *            the automaton representing all the webmethod names the makeURL
     *            expression can generate
     * @param targetedWebApp
     *            the webapp the makeURL expression will hit - supposed to
     *            contain the webmethods
     * @param makeURLEnclosingMethod
     *            (for error reporting) the method enclosing the makeURL
     *            expression
     * @param makeURLstatement
     *            the statement containing the makeURLExpression (for error
     *            reporting)
     * @return all the webmethods with names which matches
     */
    private Set<Method> getTargetedWebMethods(
            Automaton possibleWebMethodNamesAutomaton,
            SootClass targetedWebApp, SootMethod makeURLEnclosingMethod,
            Stmt makeURLstatement, Interface interfacee) {

        @SuppressWarnings("unchecked")
        Class<? extends WebApp> webAppClass = (Class<? extends WebApp>) resolver
                .getJavaClass(targetedWebApp);

        final Set<Method> webMethodsByName = interfacee.getWebMethodsByName(
                webAppClass, possibleWebMethodNamesAutomaton);

        // errror checking
        if (webMethodsByName.isEmpty()) {
            // no webmethods hits is an error
            Feedbacks.add(new NoWebMethodHit(makeURLEnclosingMethod,
                    makeURLstatement, targetedWebApp,
                    possibleWebMethodNamesAutomaton));
        } else {
            // check if the makeURL can hit more names than we have available: a
            // possible error
            Automaton possibleMethodNames = findMatcher(webMethodsByName);
            Automaton slack = possibleWebMethodNamesAutomaton
                    .minus(possibleMethodNames);
            final boolean hasSlack = !slack.isEmpty();
            if (hasSlack) {
                Feedbacks.add(new MayHitMoreUnexistingWebMethods(
                        makeURLEnclosingMethod, makeURLstatement, slack));
            }
        }
        HashSet<Method> webMethods = new HashSet<Method>();
        for (Method method : webMethodsByName) {
            if (resolver.isFilter(method)) {
                Feedbacks.add(new DirectFilterInvocation(
                        makeURLEnclosingMethod, makeURLstatement, resolver
                                .getSootMethod(method)));
            } else {
                webMethods.add(method);
            }
        }
        return webMethods;
    }

    /**
     * Constructs an {@link Automaton} with a language of all the names of the
     * webmethods
     * 
     * @return the constructed {@link Automaton}
     */
    private Automaton findMatcher(Collection<Method> methods) {
        Automaton a = Automaton.makeEmpty();
        for (Method m : methods) {
            String name = m.getName();
            a = a.union(Automaton.makeString(name));
        }
        a.determinize();
        a.minimize();
        return a;
    }

}
