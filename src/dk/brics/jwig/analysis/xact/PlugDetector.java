package dk.brics.jwig.analysis.xact;

import java.util.Set;

import soot.Body;
import soot.Type;
import soot.Value;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import dk.brics.jwig.analysis.JwigResolver;
import dk.brics.xact.XML;

/**
 * Local reachable definitions analysis for finding any possible values in a
 * {@link XML#plug(String, Object)} expression.
 * 
 * If the expression isn't a plug-expression, {@link #isPlug()} is false.
 * 
 * If the expression is a plug-expression, {@link #getTypes()} contains the
 * plugged types.
 */
public class PlugDetector {

    private final boolean isPlug;
    private static final JwigResolver resolver = JwigResolver.get();
    private final Set<Type> types;

    /**
     * @param expr
     *            as the value to find possible SubmitHandlers for.
     * @param st
     *            as the statement of the value
     * @param b
     *            as the body of the statement
     */
    public PlugDetector(InvokeExpr expr, Stmt st, Body b) {
        isPlug = resolver.isXMLPlug(expr.getMethod());
        Set<Type> submitHandlers = null;
        if (isPlug) {
            final Value plugVariable = expr.getArg(expr.getArgCount() - 1);
            submitHandlers = resolver.getReachingTypes(plugVariable, st, b);
        }
        this.types = submitHandlers;
    }

    /**
     * @return the isPlug
     */
    public boolean isPlug() {
        return isPlug;
    }

    /**
     * @return the types
     */
    public Set<Type> getTypes() {
        return types;
    }


}
