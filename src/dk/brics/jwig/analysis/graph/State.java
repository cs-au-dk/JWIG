package dk.brics.jwig.analysis.graph;

import java.util.Set;

import soot.SootMethod;

public interface State {
    boolean isInSession();

    void removeSuccessor(Transition s);

    void addSuccessor(Transition s);

    Set<Transition> getTransitions();

    SootMethod getMethod();

    void setPriority(int priority);

    int getPriority();

    boolean isDefaultPriority();

    void setDefaultPriority(boolean defaultPriority);

    <T> T accept(StateVisitor<T> visitor);
}
