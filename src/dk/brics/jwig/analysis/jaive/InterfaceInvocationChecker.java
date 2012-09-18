package dk.brics.jwig.analysis.jaive;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import soot.RefType;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.internal.JArrayRef;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JNewArrayExpr;
import soot.toolkits.graph.CompleteUnitGraph;
import dk.brics.jwig.RequiredParameter;
import dk.brics.jwig.URLPattern;
import dk.brics.jwig.WebApp;
import dk.brics.jwig.WebContext;
import dk.brics.jwig.analysis.JwigResolver;
import dk.brics.jwig.analysis.Parameter;
import dk.brics.jwig.analysis.Parameters;
import dk.brics.jwig.analysis.VarArgInfo;
import dk.brics.jwig.analysis.graph.FilterState;
import dk.brics.jwig.analysis.graph.FilterTransition;
import dk.brics.jwig.analysis.graph.HandlerTransition;
import dk.brics.jwig.analysis.graph.PredecessorResolver;
import dk.brics.jwig.analysis.graph.State;
import dk.brics.jwig.analysis.graph.StateMachine;
import dk.brics.jwig.analysis.graph.StateMachine.MethodStatementContainer;
import dk.brics.jwig.analysis.graph.Transition;
import dk.brics.jwig.analysis.graph.WebMethodTransition;
import dk.brics.jwig.analysis.jaive.feedback.ArityMismatch;
import dk.brics.jwig.analysis.jaive.feedback.CouldNotFindCommonSuperType;
import dk.brics.jwig.analysis.jaive.feedback.CouldNotInferRecordTypeForVarArgs;
import dk.brics.jwig.analysis.jaive.feedback.Feedbacks;
import dk.brics.jwig.analysis.jaive.feedback.FilterParameterTypeMismatch;
import dk.brics.jwig.analysis.jaive.feedback.MakeURLCallDoesNotTypeCheck;
import dk.brics.jwig.analysis.jaive.feedback.MissingFilterParameter;
import dk.brics.jwig.analysis.jaive.feedback.PossiblyMissingFilterParameter;
import dk.brics.jwig.analysis.jaive.feedback.UnanalyzedVarArgs;
import dk.brics.jwig.analysis.jaive.feedback.WebAppParamMismatch;

/**
 * Type checks invocations of webmethods, filters and the run-methods of
 * AbstractHandlers.
 */
public class InterfaceInvocationChecker {

    private final JwigResolver resolver;

    private static final Logger log = Logger
            .getLogger(InterfaceInvocationChecker.class);

    public InterfaceInvocationChecker() {
        this.resolver = JwigResolver.get();
    }

    public void check(StateMachine machine) {
        log.info("Type checking invocations of makeURL.");
        typeCheckInvocations(machine);
        log.info("Done checking invocations of makeURL.");
        log.info("Checking filter invocations.");
        PredecessorResolver predecessorResolver = new PredecessorResolver(
                machine);
        for (FilterState filter : machine.getFilterStates()) {
            checkFilterParameters(predecessorResolver, filter);
        }
        log.info("Done checking filter invocations.");
        // new FormChecker(machine).checkForms();
    }

    /**
     * Checks if the arguments passed to a filter is sufficiently present.
     * 
     * @see #checkFilterParameters(PredecessorResolver, FilterState)
     * 
     * @param webMethod
     *            as the invoking webmethod
     * @param webMethodParameters
     *            as the parameters of the invoking webmethod
     * @param p
     *            as the parameter of the filter to check.
     */
    private void checkFilterParameterRequired(SootMethod webMethod,
            Parameters webMethodParameters, Parameter p) {
        if (p.isRequired()) {
            Parameter fromParameter = webMethodParameters.getParameter(p
                    .getName());
            if (fromParameter == null)
                Feedbacks.add(new MissingFilterParameter(p, webMethod));
            else {
                if (!fromParameter.isRequired())
                    Feedbacks.add(new PossiblyMissingFilterParameter(p,
                            fromParameter.getHost()));
            }
        }
    }

    /**
     * Checks the invocation of filters with respect to the WebMethods which can
     * hit this filter. The parameters of the WebMethod is name matched and
     * type-checked against the filterparameters.
     * 
     * If the annotaion {@link RequiredParameter} is present at a filter
     * parameter, the WebMethod is supposed to use this annotation too for the
     * corresponding parameter. If the annotation is missing at the WebMethod,
     * but the parameter is present - a warning is issued. But if the parameter
     * is missing, it is an error.
     * 
     * @param predecessorResolver
     *            as the {@link PredecessorResolver} to use to find the
     *            WebMethods invoking this filter.
     * @param filter
     *            as the filter to check.
     */
    private void checkFilterParameters(PredecessorResolver predecessorResolver,
            FilterState filter) {
        Parameters filterParameters = resolver
                .getParameters(filter.getMethod());
        Set<Transition> predecessors = predecessorResolver
                .getPredecessors(filter);
        for (Transition transition : predecessors) {
            State origin = transition.getOrigin();
            final SootMethod webMethod = origin.getMethod();
            final Parameters webMethodParameters = resolver
                    .getParameters(webMethod);
            checkFilterParameters(webMethod, webMethodParameters,
                    filterParameters);
        }
    }

    /**
     * Checks the filter parameters.
     * 
     * @see #checkFilterParameters(PredecessorResolver, FilterState)
     * 
     * @param webMethod
     *            as the invoking webmethod
     * @param webMethodParameters
     *            as the parameters of the invoking webmethod
     * @param filterParameters
     *            as the parameters of the invoked filter.
     */
    private void checkFilterParameters(SootMethod webMethod,
            Parameters webMethodParameters, Parameters filterParameters) {
        for (Parameter filterParameter : filterParameters.getParameters()) {
            checkFilterParameterRequired(webMethod, webMethodParameters,
                    filterParameter);
            Parameter fromParameter = webMethodParameters
                    .getParameter(filterParameter.getName());
            // type check the matching parameters:
            if (fromParameter != null) {
                final Type toType = filterParameter.getType();
                if (!resolver.isParameterType(toType)
                        && !resolver.isAssignable(fromParameter.getType(),
                                toType))
                    Feedbacks.add(new FilterParameterTypeMismatch(
                            fromParameter, filterParameter));
            }
        }
    }

    /**
     * Checks if a non-mapped {@link WebContext#makeURL(String, Object...)}
     * invocation between two {@link WebApp}s is legal with respect to the
     * webapp params of the two involved webapps.
     * 
     * The invocation is illegal if the two webapps doesn't have the same webapp
     * params. In that case the mapped version of makeURL should be used.
     * 
     * @param contextClass
     *            as the WebApp context the invocation is invoked in
     * @param transition
     *            as the transition representing the invocation.
     * @param enclosingMethod
     *            as the method containing the invocation
     * @param enclosingStatement
     *            as the statement containing the invocation
     */
    private void checkWebAppParams(SootClass contextClass,
            WebMethodTransition transition, SootMethod enclosingMethod,
            Stmt enclosingStatement) {
        final SootClass targetClass = transition.getTarget().getMethod()
                .getDeclaringClass();
        InvokeExpr expr = transition.getExpr();

        // ignore transitions to the same class
        if (targetClass.equals(contextClass))
            return;

        Set<String> originParams = getWebAppParams(contextClass);
        Set<String> targetParams = getWebAppParams(targetClass);
        if (!originParams.equals(targetParams)
                && !resolver.isMappedMakeURL(expr.getMethod())) {
            // find the mismatch:

            HashSet<String> originCopy = new HashSet<String>(originParams);
            HashSet<String> targetCopy = new HashSet<String>(targetParams);
            originCopy.removeAll(targetParams);
            targetCopy.removeAll(originParams);
            if (!originCopy.isEmpty() || !targetCopy.isEmpty()) {
                Feedbacks.add(new WebAppParamMismatch(enclosingMethod,
                        enclosingStatement, targetClass, originParams,
                        targetParams, contextClass));
            }
        }
    }

    /**
     * Utility method for fetching as information about the varArg parameter
     * given to makeURL.
     * 
     * If no information can be found (i.e. not instantiated like: 'new X[3]'),
     * null is returned.
     * 
     * @param jAssignStmt
     *            as the assignement to the varArgs variable
     * @param makeURLEnclosingMethod
     *            the method enclosing the makeURL expression (for error
     *            reporting)
     * @param makeURLStatement
     *            the statement containing the makeURLExpression (for error
     *            reporting)
     * @return the {@link VarArgInfo} or null
     */
    private VarArgInfo getVarArgInfo(DefinitionStmt jAssignStmt) {
        Value right = jAssignStmt.getRightOp();
        if (right instanceof JNewArrayExpr) {
            // "= new X[3]; // where X is the basetype"
            JNewArrayExpr jNewArrayExpr = (JNewArrayExpr) right;
            Value size = jNewArrayExpr.getSize();
            if (size instanceof IntConstant) {
                IntConstant constant = (IntConstant) size;
                Type[] varArgTypes = new Type[constant.value];
                Type varArgBase = jNewArrayExpr.getBaseType();
                VarArgInfo varArgInfo = new VarArgInfo(constant.value,
                        varArgTypes, varArgBase);
                return varArgInfo;
            }
        }
        return null;
    }

    /**
     * Finds all webapp params of a class (most probably a WebApp) by analyzing
     * the {@link URLPattern} (if any).
     * 
     * @param classs
     *            as the class to analyze
     * @return the set of webapp parameter names for this class.
     */
    private Set<String> getWebAppParams(SootClass classs) {
        Class<?> webapp = resolver.getJavaClass(classs);
        URLPattern pattern = webapp.getAnnotation(URLPattern.class);
        if (pattern != null)
            return new HashSet<String>(new MyPatternMatcher(pattern.value(),
                    false).getParameters());
        return new HashSet<String>();
    }

    /**
     * Type checks a single invocation: either a WebMethod or a RunMethod.
     */
    private void typeCheckInvocations(StateMachine machine) {
        Set<State> states = machine.getStateFlattener().getAllStates();
        for (State state : states) {
            for (Transition t : state.getTransitions()) {
                if (t instanceof HandlerTransition) {
                    // TODO move enough information into the HandlerTransition
                    // to do the analysis here?
                } else if (t instanceof WebMethodTransition) {
                    final WebMethodTransition webMethodTransition = (WebMethodTransition) t;
                    final InvokeExpr expr = webMethodTransition.getExpr();
                    MethodStatementContainer container = machine
                            .getMakeURLLocation(expr);
                    SootMethod enclosingMethod = container.getMethod();
                    Stmt enclosingStatement = container.getStatement();

                    typeCheckWebMethodInvocation(webMethodTransition,
                            enclosingMethod, enclosingStatement);

                    final Set<SootClass> possibleContexts = InterfaceInvocationLinker
                            .findPredecessorWebApp(machine, resolver,
                                    enclosingMethod);
                    for (SootClass context : possibleContexts)
                        checkWebAppParams(context, webMethodTransition,
                                enclosingMethod, enclosingStatement);
                } else if (t instanceof FilterTransition) {
                    // TODO move the filter checking here (with cache to keep
                    // performance)
                }
            }
        }
    }

    /**
     * Type checks a single WebMethod invocation.
     * 
     * This is done by comparing the varArgs given to makeURL with the
     * parameters of the webmethod. Does not support overloading. If the user
     * constructed the varargs array himself, the analysis could suffer greatly.
     * 
     * Will report errors if something doesn't type check.
     * 
     * @param transition
     *            as the {@link WebMethodTransition} representing the
     *            invocation.
     * @param enclosingMethod
     *            as the method enclosing the invocation
     * @param enclosingStatement
     *            as the statement enclosing the invocation
     */
    private void typeCheckWebMethodInvocation(WebMethodTransition transition,
            SootMethod enclosingMethod, Stmt enclosingStatement) {
        InvokeExpr expr = transition.getExpr();
        SootMethod webMethod = transition.getTarget().getMethod();
        Type[] varArgTypes = null;
        Type varArgBase = null;
        CompleteUnitGraph graph = new CompleteUnitGraph(
                enclosingMethod.retrieveActiveBody());

        Value varArgs = expr.getArg(expr.getArgs().size() - 1);
        int varArgSize = -1;

        // go through all assignment to determine the varArg types given
        // to makeURL. It is assumed that the varargs array is generated by
        // soot.
        boolean skipAnalysis = false;
        for (Unit unit : graph) {
            assert unit instanceof Stmt;
            Stmt statement = (Stmt) unit;
            if (statement instanceof JAssignStmt) {
                JAssignStmt jAssignStmt = (JAssignStmt) statement;
                if (jAssignStmt.getLeftOp().equals(varArgs)) {
                    // assigning to the varArgs variable
                    log.debug("Found varargs instantiation");

                    VarArgInfo varArgInfo = getVarArgInfo(jAssignStmt);
                    if (varArgInfo != null) {
                        varArgSize = varArgInfo.getSize();
                        varArgTypes = varArgInfo.getTypes();
                        varArgBase = varArgInfo.getBaseType();
                    } else {
                        Feedbacks.add(new UnanalyzedVarArgs(enclosingMethod,
                                enclosingStatement));
                        // we cant do anything, but to skip this analysis
                        skipAnalysis = true;
                        break;
                    }
                } else if (!skipAnalysis
                        && jAssignStmt.getLeftOp() instanceof JArrayRef) {
                    JArrayRef jArrayRef = (JArrayRef) jAssignStmt.getLeftOp();
                    if (jArrayRef.getBase().equals(varArgs)) {
                        // assigning to the varArg array values. Compute
                        // the type at the assigned position. This will always
                        // happen after the varargs instantiation!

                        if (jArrayRef.getIndex() instanceof IntConstant) {
                            IntConstant intConstant = (IntConstant) jArrayRef
                                    .getIndex();
                            int index = intConstant.value;
                            @SuppressWarnings("null")
                            Type oldType = varArgTypes[index];
                            Type type = jAssignStmt.getRightOp().getType();
                            if (oldType instanceof RefType
                                    && type instanceof RefType) {
                                RefType refType = (RefType) oldType;
                                RefType refType2 = (RefType) type;
                                type = resolver.getLeastCommonSupertypeOf(
                                        refType, refType2);
                            } else if (oldType == null || oldType.equals(type)) {
                                // do nothing, new type is good enough
                            } else {
                                Feedbacks.add(new CouldNotFindCommonSuperType(
                                        oldType, type, enclosingMethod,
                                        enclosingStatement));
                            }
                            varArgTypes[index] = type;
                        } else {
                            // unknown index: reset the varArgTypes

                            // TODO complete reset is overkill, least
                            // common
                            // super type is sufficient, but still
                            // unlikely
                            // to make any sense.
                            Feedbacks
                                    .add(new CouldNotInferRecordTypeForVarArgs(
                                            enclosingMethod,
                                            enclosingStatement, varArgBase));
                            @SuppressWarnings("null")
                            final int length = varArgTypes.length;
                            for (int i = 0; i < length; i++) {
                                varArgTypes[i] = varArgBase;
                            }
                        }
                    }
                }
            }
        }
        if (!skipAnalysis) {
            int count = webMethod.getParameterCount();
            if (count == varArgSize) {
                // the number of varArgs match the method parameter count
                @SuppressWarnings("unchecked")
                List<Type> types = webMethod.getParameterTypes();
                if (!resolver.typeCheckArguments(varArgTypes, types)) {
                    Feedbacks.add(new MakeURLCallDoesNotTypeCheck(varArgTypes,
                            types, webMethod, enclosingMethod,
                            enclosingStatement));
                }
            } else {
                Feedbacks.add(new ArityMismatch(webMethod, count, varArgSize,
                        enclosingMethod, enclosingStatement));
            }
        }

    }
}
