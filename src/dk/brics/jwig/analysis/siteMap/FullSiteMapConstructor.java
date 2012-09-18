package dk.brics.jwig.analysis.siteMap;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import soot.SootMethod;
import soot.jimple.InvokeExpr;
import dk.brics.jwig.analysis.graph.FilterTransition;
import dk.brics.jwig.analysis.graph.LambdaTransition;
import dk.brics.jwig.analysis.graph.State;
import dk.brics.jwig.analysis.graph.StateMachine;
import dk.brics.jwig.analysis.graph.Transition;
import dk.brics.jwig.analysis.graph.WebMethodState;
import dk.brics.jwig.analysis.graph.WebMethodTransition;

public class FullSiteMapConstructor implements SiteMapConstructor {

    private final StateMachine fullStateMachine;

    public FullSiteMapConstructor(StateMachine fullStateMachine) {
        this.fullStateMachine = fullStateMachine;
    }

    @Override
    public SiteMap construct() {
        StateMachine stateMachine = removeLambdaAndFilterTransitions(fullStateMachine);
        Set<State> seen = new HashSet<State>();
        LinkedList<State> worklist = new LinkedList<State>();
        SiteMap map = new SiteMap();
        for (WebMethodState initialState : stateMachine.getInitialStates()) {
            worklist.add(initialState);
            map.addPage(SiteMapper.makePage(initialState.getMethod()));
            seen.add(initialState);
        }
        while (!worklist.isEmpty()) {
            State state = worklist.remove();
            SootMethod current = state.getMethod();
            final String currentName = SiteMapper.makePageName(current);
            for (Transition transition : state.getTransitions()) {
                final State target = transition.getTarget();
                if (target != null) {
                    final SootMethod next = target.getMethod();
                    if (!seen.contains(target)) {
                        seen.add(target);
                        worklist.add(target);
                        map.addPage(SiteMapper.makePage(next));
                    }
                    InvokeExpr link = null;
                    if (transition instanceof WebMethodTransition)
                        link = ((WebMethodTransition) transition).getExpr();
                    map.addLink(map.getPageByName(currentName),
                            map.getPageByName(SiteMapper.makePageName(next)),
                            link);

                }
            }
        }
        return map;
    }

    private StateMachine removeLambdaAndFilterTransitions(StateMachine machine) {
        StateMachine clone = machine.clone();
        boolean more = true;
        while (more) {
            more = false;
            for (State s : clone.getAllStates()) {
                if (s != null) {
                    for (Transition t : s.getTransitions()) {
                        if (t instanceof LambdaTransition
                                || t instanceof FilterTransition) {
                            State target = t.getTarget();
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
        return clone;
    }
}
