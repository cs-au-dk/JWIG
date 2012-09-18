package dk.brics.jwig.analysis.graph;

public interface Transition {
    // TODO use generics for the origin, target to get a type safe graph
    <T> T accept(TransitionVisitor<T> visitor);

    State getTarget();

    void setTarget(State target);

    State getOrigin();

    void setOrigin(State origin);

    public Transition clone();

    boolean isInSession();

    void setInSession(boolean inSession);
}
