package dk.brics.jwig.analysis.graph;

import soot.SootMethod;

public class WebMethodState extends AbstractState {

    WebMethodState(SootMethod method) {
        super(method);
    }

    @Override
    public <T> T accept(StateVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
