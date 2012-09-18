package dk.brics.jwig.analysis.graph;

/**
 * An lambda state is a state that does not have any user visible effect. That
 * is for example a web method calling another web method.
 */
public class LambdaTransition extends AbstractTransition {

    @Override
    public <T> T accept(TransitionVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
