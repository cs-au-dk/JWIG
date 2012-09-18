package dk.brics.jwig.analysis.graph;

import soot.SootMethod;

public class HandlerState extends AbstractState {

    HandlerState(SootMethod method) {
        super(method);
    }

    @Override
    public <T> T accept(StateVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
