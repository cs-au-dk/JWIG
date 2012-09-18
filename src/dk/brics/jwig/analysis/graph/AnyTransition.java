package dk.brics.jwig.analysis.graph;


/**
 * Represents the case where we do not know what something points to.
 */
public class AnyTransition extends AbstractTransition {
    @Override
    public <T> T accept(TransitionVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
