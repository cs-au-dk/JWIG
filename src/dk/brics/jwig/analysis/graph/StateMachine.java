package dk.brics.jwig.analysis.graph;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import soot.SootMethod;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import dk.brics.jwig.analysis.jaive.Plugging;

/**
 * A state machine for a #WebSite: a call graph with makeURLs as edges too.
 */
public class StateMachine implements Cloneable {

    /**
     * Container class.
     */
    public static class MethodStatementContainer {

        private final SootMethod method;
        private final Stmt st;

        public MethodStatementContainer(SootMethod method, Stmt st) {
            this.method = method;
            this.st = st;
        }

        /**
         * @return the method
         */
        public SootMethod getMethod() {
            return method;
        }

        /**
         * @return the st
         */
        public Stmt getStatement() {
            return st;
        }

    }

    public class StateFlattener {
        private final Set<State> allStates = new HashSet<State>();

        public StateFlattener() {
            for (State s : initialsStates) {
                findSubStates(s);
            }
            // add any isolated filterStates
            allStates.addAll(filterStates);
        }

        private void findSubStates(State s) {
            allStates.add(s);
            if (s != null) {
                for (Transition sub : s.getTransitions()) {
                    State target = sub.getTarget();
                    if (target != null && !allStates.contains(target)) {
                        findSubStates(target);
                    }
                }
            }
        }

        public Set<State> getAllStates() {
            return allStates;
        }
    }

    public static int getHash(State o1) {
        if (o1 == null) {
            return "ANY".hashCode();
        }
        return o1.getMethod().getDeclaringClass().hashCode();
    }

    private final Set<WebMethodState> initialsStates = new HashSet<WebMethodState>();
    private final Map<SootMethod, State> methodStateMap = new HashMap<SootMethod, State>();
    private final Map<InvokeExpr, MethodStatementContainer> makeURLLocations = new HashMap<InvokeExpr, MethodStatementContainer>();
    private final Map<InvokeExpr, Plugging> plugLocations = new HashMap<InvokeExpr, Plugging>();
    private final Set<FilterState> filterStates = new HashSet<FilterState>();

    public void addFilterState(FilterState state) {
        filterStates.add(state);
    }

    public void addInitialState(WebMethodState state) {
        initialsStates.add(state);
    }

    public void addMakeURLLocation(InvokeExpr expr, SootMethod method, Stmt st) {
        makeURLLocations.put(expr, new MethodStatementContainer(method, st));
    }

    private void addState(State state) {
        if (!methodStateMap.containsKey(state.getMethod()))
            methodStateMap.put(state.getMethod(), state);
    }

    /**
     * Clones the state machine. All states and their transitions are cloned
     * too. Soot-classes aren't cloned.
     */
    @Override
    public StateMachine clone() {
        final StateMachine clone = new StateMachine();
        cloneInitialStates(clone);
        cloneFilterStates(clone);
        cloneStates(clone);
        cloneTransitions(clone);
        cloneMakeURLLocations(clone);
        // TODO clone submitHandlerPluggings
        return clone;
    }

    /**
     * Clones the {@link FilterState}s of this {@link StateMachine}.
     * 
     * @param clone
     *            as the clone to add the {@link FilterState}s to
     */
    private void cloneFilterStates(StateMachine clone) {
        for (FilterState filter : getFilterStates()) {
            clone.addFilterState(clone.createFilterState(filter.getMethod()));
        }
    }

    /**
     * Clones the initial states of this {@link StateMachine}.
     * 
     * @param clone
     *            as the clone to add the initial states to
     */
    private void cloneInitialStates(final StateMachine clone) {
        for (WebMethodState initial : getInitialStates()) {
            clone.addInitialState(clone.createWebMethodState(initial
                    .getMethod()));
        }
    }

    /**
     * Clones the {@link #makeURLLocations} of this {@link StateMachine}.
     * 
     * @param clone
     *            as the clone to add the {@link #makeURLLocations} to
     */
    private void cloneMakeURLLocations(StateMachine clone) {
        for (Entry<InvokeExpr, MethodStatementContainer> entry : makeURLLocations
                .entrySet()) {
            final MethodStatementContainer container = entry.getValue();
            clone.addMakeURLLocation(entry.getKey(), container.getMethod(),
                    container.getStatement());
        }
    }

    /**
     * Clones the {@link State}s of this {@link StateMachine}.
     * 
     * @param clone
     *            as the clone to add the states to
     */
    private void cloneStates(final StateMachine clone) {
        StateVisitor<State> stateCloner = new StateVisitor<State>() {

            @Override
            public FilterState visit(FilterState state) {
                return clone.createFilterState(state.getMethod());
            }

            @Override
            public HandlerState visit(HandlerState state) {
                return clone.createHandlerState(state.getMethod());
            }

            @Override
            public State visit(State state) {
                throw new RuntimeException("Unknown state: " + state);
            }

            @Override
            public WebMethodState visit(WebMethodState state) {
                return clone.createWebMethodState(state.getMethod());
            }

            @Override
            public State visit(RegularMethodState state) {
                return clone.createReqularMethodState(state.getMethod());
            }
        };

        for (State state : getAllStates()) {
            clone.addState(state.accept(stateCloner));
        }
    }

    /**
     * Clones the {@link Transition}s of this {@link StateMachine}.
     * 
     * @param clone
     *            as the clone containing the {@link State}s to link
     */
    private void cloneTransitions(final StateMachine clone) {
        for (Entry<SootMethod, State> entry : methodStateMap.entrySet()) {
            SootMethod method = entry.getKey();
            State state = entry.getValue();
            final State clonedState = clone.getState(method);
            TransitionVisitor<Transition> transitionCloner = new TransitionVisitor<Transition>() {

                private State getClonedTarget(Transition transition) {
                    final State target = transition.getTarget();
                    if (target == null)
                        return null;
                    return clone.getState(target.getMethod());
                }

                private void setTransitions(Transition clone,
                        final Transition transition) {
                    transition.setOrigin(clonedState);
                    transition.setTarget(getClonedTarget(clone));
                }

                @Override
                public AnyTransition visit(AnyTransition transition) {
                    final AnyTransition clone = new AnyTransition();
                    setTransitions(transition, clone);
                    return clone;
                }

                @Override
                public FilterTransition visit(FilterTransition transition) {
                    final FilterTransition clone = new FilterTransition();
                    setTransitions(transition, clone);
                    return clone;
                }

                @Override
                public HandlerTransition visit(HandlerTransition transition) {
                    final HandlerTransition clone = new HandlerTransition();
                    setTransitions(transition, clone);
                    return clone;
                }

                @Override
                public LambdaTransition visit(LambdaTransition transition) {
                    final LambdaTransition clone = new LambdaTransition();
                    setTransitions(transition, clone);
                    return clone;
                }

                @Override
                public WebMethodTransition visit(WebMethodTransition transition) {
                    final WebMethodTransition clone = new WebMethodTransition(
                            transition.getExpr());
                    setTransitions(transition, clone);
                    return clone;
                }
            };
            for (Transition t : state.getTransitions()) {
                clonedState.addSuccessor(t.accept(transitionCloner));
            }
        }
    }

    public FilterState createFilterState(SootMethod method) {
        FilterState state = (FilterState) getState(method);
        if (state == null) {
            state = new FilterState(method);
            addState(state);
        }
        return state;
    }

    public RegularMethodState createReqularMethodState(SootMethod method) {
        RegularMethodState state = (RegularMethodState) getState(method);
        if (state == null) {
            state = new RegularMethodState(method);
            addState(state);
        }
        return state;
    }

    public HandlerState createHandlerState(SootMethod method) {
        HandlerState state = (HandlerState) getState(method);
        if (state == null) {
            state = new HandlerState(method);
            addState(state);
        }
        return state;
    }

    public WebMethodState createWebMethodState(SootMethod method) {
        WebMethodState state = (WebMethodState) getState(method);
        if (state == null) {
            state = new WebMethodState(method);
            addState(state);
        }
        return state;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (o == null || !(o instanceof StateMachine))
            return false;
        StateMachine m = (StateMachine) o;
        final boolean equalTransitions = equalTransitions(this, m);
        final boolean equalInitials = initialsStates.equals(m.initialsStates);
        final boolean equalFilters = filterStates.equals(m.filterStates);
        final boolean equalMethodStateMaps = methodStateMap
                .equals(m.methodStateMap);
        final boolean equalMakeURLLocations = makeURLLocations
                .equals(makeURLLocations);
        return equalTransitions && equalInitials && equalFilters
                && equalMethodStateMaps && equalMakeURLLocations;
    }

    boolean equalTransitions(StateMachine m1, StateMachine m2) {
        return m1.getTransitions().equals(m2.getTransitions());

    }

    public Set<State> getAllStates() {
        return new StateFlattener().getAllStates();
    }

    public Set<FilterState> getFilterStates() {
        return filterStates;
    }

    public Collection<WebMethodState> getInitialStates() {
        return initialsStates;
    }

    public Set<InvokeExpr> getMakeURLExpressions() {
        return makeURLLocations.keySet();
    }

    public MethodStatementContainer getMakeURLLocation(InvokeExpr expr) {
        final MethodStatementContainer methodStatementContainer = makeURLLocations
                .get(expr);
        if (methodStatementContainer == null)
            throw new IllegalArgumentException("Unknown expression:" + expr);
        return methodStatementContainer;
    }

    public Collection<Plugging> getPluggings() {
        return plugLocations.values();
    }

    public MethodStatementContainer getPlugLocation(InvokeExpr expr) {
        final Plugging plugging = plugLocations.get(expr);
        if (plugging == null)
            throw new IllegalArgumentException("Unknown expression:" + expr);
        final MethodStatementContainer methodStatementContainer = plugging
                .getContainer();
        return methodStatementContainer;
    }

    public Collection<MethodStatementContainer> getMakeURLLocations() {
        return makeURLLocations.values();
    }

    public State getState(SootMethod method) {
        return methodStateMap.get(method);
    }

    public StateFlattener getStateFlattener() {
        return new StateFlattener();
    }

    /**
     * Finds all {@link Transition}s between the {@link State}s of this
     * {@link StateMachine}.
     * 
     * @return the {@link Transition}s
     */
    Set<Transition> getTransitions() {
        Set<Transition> transitions = new HashSet<Transition>();
        for (State s : getAllStates()) {
            for (Transition t : s.getTransitions())
                transitions.add(t);
        }
        return transitions;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((filterStates == null) ? 0 : filterStates.hashCode());
        result = prime * result
                + ((initialsStates == null) ? 0 : initialsStates.hashCode());
        result = prime
                * result
                + ((makeURLLocations == null) ? 0 : makeURLLocations.hashCode());
        result = prime * result
                + ((methodStateMap == null) ? 0 : methodStateMap.hashCode());
        return result;
    }

    public void removeLambdas() {
        boolean more = true;
        while (more) {
            more = false;
            for (State s : getAllStates()) {
                if (s != null) {
                    for (Transition t : s.getTransitions()) {
                        if (t instanceof LambdaTransition) {
                            LambdaTransition lambdaTransition = (LambdaTransition) t;
                            State target = lambdaTransition.getTarget();
                            if (target != null && !target.equals(s)) {
                                for (Transition tt : target.getTransitions()) {
                                    s.addSuccessor(tt.clone());
                                }
                            }
                            s.removeSuccessor(t);
                            more = true;
                        }
                    }
                }
            }
        }
    }

    public void addPlugging(Plugging plugging) {
        plugLocations.put(plugging.getContainer().getStatement()
                .getInvokeExpr(), plugging);
    }

    public Set<MethodStatementContainer> getPlugLocations() {
        Collection<Plugging> values = plugLocations.values();
        Set<MethodStatementContainer> locations = new HashSet<StateMachine.MethodStatementContainer>();
        for (Plugging plugging : values) {
            locations.add(plugging.getContainer());
        }
        return locations;
    }
}
