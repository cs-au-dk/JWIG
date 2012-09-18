package dk.brics.jwig.analysis.graph;

import soot.jimple.InvokeExpr;

public class WebMethodTransition extends AbstractTransition {
    private final InvokeExpr expr;

    /**
     * @return the expr
     */
    public InvokeExpr getExpr() {
        return expr;
    }

    public WebMethodTransition(InvokeExpr expr) {
        this.expr = expr;

    }

    @Override
    public <T> T accept(TransitionVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
