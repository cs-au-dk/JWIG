package dk.brics.jwig.analysis.graph;

public class FilterTransition extends AbstractTransition {

    @Override
    public <T> T accept(TransitionVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
