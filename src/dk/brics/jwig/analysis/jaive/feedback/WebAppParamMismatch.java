package dk.brics.jwig.analysis.jaive.feedback;

import java.util.Set;

import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Stmt;

public class WebAppParamMismatch extends AbstractFeedback {

    public WebAppParamMismatch(SootMethod method, Stmt statement,
            SootClass targetClass, Set<String> originParams,
            Set<String> targetParams, SootClass context) {
        this.message = "WebApp " + targetClass.getName() + " is invoked from "
                + SourceUtil.getLocation(method, statement)
                + " in the context of " + context.getName()
                + ", but the WebApp params does not match. Expected: "
                + prettyPrint(targetParams) + ", but got: "
                + prettyPrint(originParams) + ".";
    }

    private String prettyPrint(Set<String> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (String param : params) {
            sb.append(param);
        }
        sb.append("]");
        return sb.toString();
    }

}
