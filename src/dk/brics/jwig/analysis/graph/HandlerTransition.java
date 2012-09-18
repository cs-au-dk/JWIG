package dk.brics.jwig.analysis.graph;

/**
 * A handler state is a state to which the state machine can get when a handler
 * is invoked.
 */
public class HandlerTransition extends AbstractTransition {

    @Override
    public <T> T accept(TransitionVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
