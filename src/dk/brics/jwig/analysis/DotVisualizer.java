package dk.brics.jwig.analysis;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import soot.SootClass;
import soot.SootMethod;
import dk.brics.jwig.URLPattern;
import dk.brics.jwig.WebApp;
import dk.brics.jwig.WebContext;
import dk.brics.jwig.WebSite;
import dk.brics.jwig.analysis.graph.FilterState;
import dk.brics.jwig.analysis.graph.FilterTransition;
import dk.brics.jwig.analysis.graph.HandlerState;
import dk.brics.jwig.analysis.graph.HandlerTransition;
import dk.brics.jwig.analysis.graph.LambdaTransition;
import dk.brics.jwig.analysis.graph.PredecessorResolver;
import dk.brics.jwig.analysis.graph.State;
import dk.brics.jwig.analysis.graph.StateMachine;
import dk.brics.jwig.analysis.graph.Transition;
import dk.brics.jwig.analysis.graph.WebMethodState;
import dk.brics.jwig.analysis.graph.WebMethodTransition;

/**
 * Visualizes a #StateMachine in Graphviz dot format
 */
public class DotVisualizer {

    private final StateMachine stateMachine;
    private final Set<State> statesWithManyIngoingEdges;

    // TODO make legend
    public DotVisualizer(StateMachine stateMachine) {
        this.stateMachine = stateMachine;
        statesWithManyIngoingEdges = findStatesWithManyIngoingEdges(stateMachine);
    }

    private Set<State> findStatesWithManyIngoingEdges(StateMachine stateMachine) {
        Map<State, Integer> inCount = new HashMap<State, Integer>();
        final Set<State> allStates = stateMachine.getAllStates();
        for (State state : allStates) {
            for (Transition transition : state.getTransitions()) {
                final State target = transition.getTarget();
                if (target != null) {
                    if (!inCount.containsKey(target))
                        inCount.put(target, 0);
                    Integer count = inCount.get(target);
                    inCount.put(target, count + 1);
                }
            }
        }
        Integer threshold = 25;
        Set<State> states = new HashSet<State>();
        for (Entry<State, Integer> entry : inCount.entrySet()) {
            if (entry.getValue() >= threshold) {
                states.add(entry.getKey());
            }
        }
        return states;
    }

    /**
     * Visualizes
     */
    public String visualize(boolean full) {
        // remove WebContext, WebSite and WebApp from the diagram:
        final Set<State> states = stateMachine.getStateFlattener()
                .getAllStates();
        if (!full) {
            JwigResolver resolver = JwigResolver.get();
            Set<SootClass> jwigClasses = new HashSet<SootClass>();

            jwigClasses.add(resolver.getSootClass(WebContext.class));
            jwigClasses.add(resolver.getSootClass(WebSite.class));
            jwigClasses.add(resolver.getSootClass(WebApp.class));

            for (State state : states) {
                for (Transition transition : state.getTransitions()) {
                    final State target = transition.getTarget();
                    if (target != null) {
                        if (jwigClasses.contains(target.getMethod()
                                .getDeclaringClass())) {
                            state.removeSuccessor(transition);
                        }
                    }
                }
            }
        }
        return visualize(states);
    }

    public String visualize(Collection<State> states) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("digraph StateMachine {\n");
        buffer.append("rankdir=LR;\n");

        Map<State, String> names = new HashMap<State, String>();
        int i = 0;
        for (State s : states) {
            String name = "N" + i++;
            names.put(s, name);
        }
        buffer.append(visualizeStates(names));
        buffer.append(visualizeTransitions(names));
        buffer.append(visualizeManyTransitions(names));
        buffer.append("}\n");
        return buffer.toString();
    }

    public String visualize(Set<SootClass> classes) {
        Collection<State> states = new LinkedList<State>();
        for (State state : stateMachine.getAllStates()) {
            if (classes.contains(state.getMethod().getDeclaringClass())) {
                states.add(state);
            }
        }
        return visualize(states);
    }

    private String visualizeManyTransitions(Map<State, String> names) {
        StringBuilder sb = new StringBuilder();
        for (State state : statesWithManyIngoingEdges) {
            final String id = names.get(state);
            if (id != null)
                sb.append(id + "_many[label=\"many\",shape=none]\n");
        }
        for (State state : statesWithManyIngoingEdges) {
            final String id = names.get(state);
            if (id != null)
                sb.append(id + "_many->" + id + "[color=grey];\n");
        }
        return sb.toString();
    }

    private String visualizeStates(Map<State, String> names) {
        Map<SootClass, String> webapps = new HashMap<SootClass, String>();
        SootClass current = null;
        StringBuilder sb = new StringBuilder();
        State[] states = names.keySet().toArray(
                new State[names.keySet().size()]);
        Arrays.sort(states, new Comparator<State>() {
            @Override
            public int compare(State o1, State o2) {
                int hash1 = StateMachine.getHash(o1);
                int hash2 = StateMachine.getHash(o2);
                return hash1 - hash2;
            }
        });
        for (State s : states) {
            if (s != null) {
                SootClass cl = s.getMethod().getDeclaringClass();
                if (cl != current) {
                    if (current != null) {
                        sb.append("}\n");
                    }
                    final String className = cl.getName();
                    final String label = className + getURLPattern(cl);
                    String name = className.replace(".", "").replace("$", "");
                    sb.append("subgraph cluster" + name + " {\n");
                    sb.append("label=\"" + label + "\";\n");
                    current = cl;
                    webapps.put(cl, name);
                }

                String style = "solid";
                String color = "black";
                if (s instanceof WebMethodState) {
                    //
                } else if (s instanceof FilterState) {
                    style = "dotted";
                } else if (s instanceof HandlerState) {
                    style = "dashed";
                } else {
                    color = "grey";
                }

                if (s.isInSession()) {
                    style = style + ",bold";
                }
                final String name = s.getMethod().getName();
                final String priority = s.getPriority()
                        + (s.isDefaultPriority() ? "" : "!");
                String urlPattern = getURLPattern(s.getMethod());
                final String label = name + "(" + priority + ")" + urlPattern;
                sb.append(names.get(s) + " [label=\"" + label
                        + "\", shape=box,style=\"" + style + "\",color="
                        + color + "];\n");
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String getURLPattern(SootClass cl) {
        URLPattern up = JwigResolver.get().getJavaClass(cl)
                .getAnnotation(URLPattern.class);
        if (up == null)
            return "";
        return "[" + up.value() + "]";
    }

    private String getURLPattern(SootMethod method) {
        if (!method.isPublic() || method.isStatic())
            return "";
        Method m = JwigResolver.get().getJavaMethod(method);
        URLPattern up = m.getAnnotation(URLPattern.class);
        if (up == null)
            return "";
        return "[" + up.value() + "]";
    }

    private String visualizeTransitions(Map<State, String> names) {
        StringBuilder sb = new StringBuilder();
        for (Entry<State, String> entry : names.entrySet()) {
            if (entry != null && (entry.getKey() != null)) {
                for (Transition suc : entry.getKey().getTransitions()) {
                    State target = suc.getTarget();
                    if (!statesWithManyIngoingEdges.contains(target)) {
                        String trans = "";
                        String style = "solid";
                        String color = "black";
                        if (suc instanceof LambdaTransition) {
                            color = "grey";
                        } else if (suc instanceof WebMethodTransition) {
                            //
                        } else if (suc instanceof FilterTransition) {
                            style = "dotted";
                        } else if (suc instanceof HandlerTransition) {
                            style = "dashed";
                        } else {
                            color = "grey";
                        }

                        if (suc.isInSession()) {
                            style = style + ", " + "bold";
                        }

                        String name;
                        if (target != null) {
                            name = names.get(target);
                            if (name == null) {
                                name = "IGNORED";
                                trans = "";
                                style = "dotted";
                            }
                        } else {
                            name = "ANY";
                        }
                        sb.append(entry.getValue() + " -> " + name
                                + " [label=\"" + trans + "\",style=\"" + style
                                + "\",color=" + color + "];\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    public String visualizeSuccessors(State state) {
        Set<State> successors = new HashSet<State>();
        for (Transition transition : state.getTransitions())
            successors.add(transition.getTarget());
        successors.add(state);
        return visualize(successors);
    }

    public String visualizePredecessors(State state) {
        Set<State> successors = new HashSet<State>();
        for (Transition transition : new PredecessorResolver(stateMachine)
                .getPredecessors(state))
            successors.add(transition.getTarget());
        successors.add(state);
        return visualize(successors);
    }
}
