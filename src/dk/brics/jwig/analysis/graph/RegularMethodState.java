package dk.brics.jwig.analysis.graph;

import soot.SootMethod;

public class RegularMethodState extends AbstractState {

    public RegularMethodState(SootMethod method) {
        super(method);
    }

    @Override
    public <T> T accept(StateVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
