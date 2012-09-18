package dk.brics.jwig.analysis.graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PredecessorResolver {
    private final Map<State, Set<Transition>> predecessorMap = new HashMap<State, Set<Transition>>();

    public Set<Transition> getPredecessors(State s) {
        Set<Transition> transitionSet = predecessorMap.get(s);
        if (transitionSet == null) {
            transitionSet = new HashSet<Transition>();
            predecessorMap.put(s, transitionSet);
        }
        return transitionSet;
    }

    public PredecessorResolver(StateMachine s) {
        for (State st : s.getAllStates()) {
            for (Transition t : st.getTransitions()) {
                State target = t.getTarget();
                Set<Transition> transitionSet = getPredecessors(target);
                Transition newTransition = t.clone();
                newTransition.setTarget(st);
                transitionSet.add(newTransition);
            }
        }
    }
}
