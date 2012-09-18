package dk.brics.jwig.analysis;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import soot.ArrayType;
import soot.Body;
import soot.BooleanType;
import soot.ByteType;
import soot.CharType;
import soot.DoubleType;
import soot.FloatType;
import soot.Hierarchy;
import soot.IntType;
import soot.Local;
import soot.LongType;
import soot.NullType;
import soot.PrimType;
import soot.RefType;
import soot.Scene;
import soot.ShortType;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JNewExpr;
import soot.options.Options;
import soot.toolkits.graph.CompleteUnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
import dk.brics.jwig.RequiredParameter;
import dk.brics.jwig.Session;
import dk.brics.jwig.SubmitHandler;
import dk.brics.jwig.URLPattern;
import dk.brics.jwig.WebApp;
import dk.brics.jwig.WebContext;
import dk.brics.jwig.WebSite;
import dk.brics.jwig.analysis.jaive.feedback.DuplicateWebApp;
import dk.brics.jwig.analysis.jaive.feedback.Feedbacks;
import dk.brics.jwig.analysis.jaive.feedback.PossibleNullPointerInMakeURL;
import dk.brics.jwig.persistence.AbstractPersistable;
import dk.brics.jwig.server.RequestManager;
import dk.brics.jwig.util.ParameterNamer;
import dk.brics.string.StringAnalysis;
import dk.brics.xact.ToXMLable;
import dk.brics.xact.XML;

/**
 * Utility methods for navigating the JWIG class hierarchy
 */
public class JwigResolver {
    // TODO cache all queries java<->soot queries

    private static JwigResolver instance;

    public static JwigResolver get() {
        if (instance == null)
            instance = new JwigResolver(false);
        return instance;
    }

    /**
     * To be used by testing methods which doesn't require the whole program
     * analysis.
     */
    public static JwigResolver getTesting() {
        if (instance == null)
            instance = new JwigResolver(true);
        return instance;
    }

    private final Logger log = Logger.getLogger(JwigResolver.class);
    private Hierarchy hiearchy;
    private String nextSignature;
    private Set<Type> flowSafeTypes;
    private List<Type> interestingTypes;

    private List<Type> urlTypes;

    private HashMap<SootMethod, Parameters> parameterMap;

    private RefType parameterType;

    private List<SootClass> abstractPersistables;

    private String xmlPlugSignature;

    private HashSet<SootClass> couldBeSubmitHandlers;

    private Map<Body, SimpleLocalDefs> simpleLocalDefCache;

    private JwigResolver(boolean testing) {
        Options.v().set_whole_program(!testing);
        Options.v().setPhaseOption("cg", "verbose:true");
        Options.v().set_allow_phantom_refs(true);
        reload();
    }

    /**
     * A type can be a {@link SubmitHandler} if it is a super or sub type.
     */
    public boolean couldBeSubmitHandler(Type couldBeSubmitHandler) {
        if (couldBeSubmitHandlers == null) {
            couldBeSubmitHandlers = new HashSet<SootClass>();
            final SootClass submitHandlerClass = getSootClass(SubmitHandler.class);
            couldBeSubmitHandlers.addAll(getHiearchy()
                    .getSubclassesOfIncluding(submitHandlerClass));
            couldBeSubmitHandlers.addAll(getHiearchy().getSuperclassesOf(
                    submitHandlerClass));
        }
        if (couldBeSubmitHandler instanceof RefType)
            return couldBeSubmitHandlers
                    .contains(((RefType) couldBeSubmitHandler).getSootClass());
        return false;
    }

    private Parameters createParameters(SootMethod sootMethod) {
        Set<Parameter> parameterSet = new HashSet<Parameter>();
        Method method = getJavaMethod(sootMethod);
        List<String> parameterNames = ParameterNamer.getParameterNames(method);
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < parameterNames.size(); i++) {
            boolean isRequired = false;
            Annotation[] annotations = parameterAnnotations[i];
            for (Annotation annotation : annotations) {
                if (annotation instanceof RequiredParameter)
                    isRequired = true;
            }
            parameterSet.add(new Parameter(parameterNames.get(i), sootMethod
                    .getParameterType(i), isRequired, sootMethod));
        }
        return new Parameters(parameterSet);
    }

    /**
     * Finds the position of the parameter with type {@link Class} in the
     * parameter list of the makeURLMethod. If no Class parameter is found, -1
     * is returned.
     * 
     * This {@link Class} will always correspond to a {@link WebApp}.
     * 
     * @param makeURLMethod
     *            as the method to analyze
     * @return the position of the parameter of type {@link Class}
     */
    public int findClassPositionInParameterList(SootMethod makeURLMethod) {
        RefType classType = Scene.v().getSootClass(Class.class.getName())
                .getType();
        int classParamPosition = -1;
        if (makeURLMethod.getParameterType(0).equals(classType)) {
            classParamPosition = 0;
        }
        if (makeURLMethod.getParameterType(1).equals(classType)) {
            classParamPosition = 1;
        }
        return classParamPosition;
    }

    /**
     * Finds all {@link WebApp}s instantiated in the {@link WebSite#init()}
     * method. Super classes of instantiated WebApps are also found.
     * {@link WebApp} and its super classes are not returned.
     * 
     * @param webSiteClass
     *            as the {@link WebSite} to analyze
     * @return all {@link WebApp}s instantiaed in the initMethod
     */
    public Collection<SootClass> findWebApps(SootClass webSiteClass) {
        SootMethod initMethod = webSiteClass.getMethodByName("init");
        log.info("Adding web app classes to analysis");
        Set<SootClass> webapps = new HashSet<SootClass>();
        ExceptionalUnitGraph graph = new ExceptionalUnitGraph(
                initMethod.retrieveActiveBody());
        for (Unit aGraph : graph) {
            assert aGraph instanceof Stmt;
            Stmt st = (Stmt) aGraph;
            if (st instanceof JAssignStmt) {
                JAssignStmt jAssignStmt = (JAssignStmt) st;
                Value right = jAssignStmt.getRightOp();
                if (right instanceof JNewExpr) {
                    JNewExpr newInstance = (JNewExpr) right;
                    SootClass constructedClass = newInstance.getBaseType()
                            .getSootClass();
                    if (isWebApp(constructedClass)) {
                        if (webapps.contains(constructedClass)) {
                            Feedbacks
                                    .add(new DuplicateWebApp(constructedClass));
                        } else {
                            log.info("Added " + constructedClass.getName());
                            // TODO what is actually needed for the
                            // StringAnalysis here?
                            StringAnalysis
                                    .loadClass(constructedClass.getName());
                            reload();
                            // add all 'new WebAppSubclass()' to the set of
                            // WebApps
                            webapps.add(constructedClass);
                        }
                    }
                }
            }
        }
        // add all previously unseen super classes of instantiated WebApps
        List<SootClass> superclasses = new LinkedList<SootClass>();
        boolean more = true;
        while (more) {
            more = false;
            for (SootClass cl : webapps) {
                SootClass sootClass = cl.getSuperclass();
                // must be 'new':
                if (!webapps.contains(sootClass)
                        && !superclasses.contains(sootClass)) {
                    // stop at the WebApp class - there's no sense in making
                    // urls to the framework.
                    if (!sootClass.equals(Scene.v().getSootClass(
                            WebApp.class.getName()))) {
                        superclasses.add(sootClass);
                        log.info("Added " + sootClass.getName());
                        more = true;
                    }
                }
            }
            webapps.addAll(superclasses);
            superclasses.clear();
        }
        return webapps;
    }

    /**
     * Constructs a map from {@link WebApp}s to {@link RequestManager}s,
     * representing the webmethods of the webapp.
     * 
     * @param webapps
     *            as the {@link WebApp}s to find webmethods for
     * @return the map from {@link WebApp}s to corresponding webmethods
     */
    public Map<SootClass, RequestManager> findWebMethods(
            Collection<SootClass> webapps) {
        log.info("Reading classes to find web methods");
        Map<SootClass, RequestManager> managers = new HashMap<SootClass, RequestManager>();
        for (SootClass webApp : webapps) {
            try {
                Class<? extends WebApp> webAppClass = Class.forName(
                        webApp.getName()).asSubclass(WebApp.class);
                RequestManager man = new RequestManager();
                man.introspectWebAppClass(webAppClass);
                managers.put(webApp, man);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        log.info("Found all WebMethods");
        return managers;
    }

    /**
     * Returns a collection of the types that only contain methods that we know
     * do not have any influence on the flow. This is for example the Xact
     * classes
     * 
     * @return the 'flow safe' type
     */
    public Collection<Type> getFlowSafeTypes() {
        if (flowSafeTypes == null) {
            flowSafeTypes = new HashSet<Type>();
            flowSafeTypes.add(getSootClass(XML.class).getType());
        }
        return flowSafeTypes;
    }

    public Hierarchy getHiearchy() {
        return hiearchy;
    }

    /**
     * Finds all 'interesting' types in the hierarchy.
     * 
     * A type is interesting if it is a subtype (inclusive) of either
     * {@link ToXMLable}, {@link URL}, {@link SubmitHandler}.
     * 
     * These are interesting for the analysis as:
     * 
     * {@link ToXMLable}: can contain makeURL-generated urls
     * 
     * {@link URL}: can be constructed with makeURL
     * 
     * {@link SubmitHandler}: uses makeURL
     * 
     * @return the interesting types.
     */
    private Collection<Type> getInterestingTypes() {
        if (interestingTypes == null) {
            // TODO generalize SubmitHandler to AbstractHandler in this
            // collection?
            RefType XMLtype = Scene.v().getSootClass(ToXMLable.class.getName())
                    .getType();
            RefType URLtype = Scene.v().getSootClass(URL.class.getName())
                    .getType();
            RefType SubmitType = Scene.v()
                    .getSootClass(SubmitHandler.class.getName()).getType();

            List<SootClass> interestingClasses = new LinkedList<SootClass>();
            interestingClasses.addAll(getHiearchy().getImplementersOf(
                    XMLtype.getSootClass()));
            interestingClasses.addAll(getHiearchy().getSubclassesOfIncluding(
                    SubmitType.getSootClass()));
            interestingClasses.addAll(getHiearchy().getSubclassesOfIncluding(
                    URLtype.getSootClass()));

            interestingTypes = new LinkedList<Type>();
            for (SootClass cl : interestingClasses) {
                interestingTypes.add(cl.getType());
            }
        }
        return interestingTypes;
    }

    public Class<?> getJavaClass(SootClass sootClass) {
        Class<?> javaClass;
        try {
            javaClass = Class.forName(sootClass.getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return javaClass;
    }

    private Class<?> getJavaClass(Type sootType) {
        Class<?> javaType = null;
        if (sootType instanceof PrimType) {
            PrimType primType = (PrimType) sootType;
            javaType = getPrimitiveType(primType);
        } else if (sootType instanceof RefType) {
            RefType refType = (RefType) sootType;
            javaType = getJavaClass(refType.getSootClass());
        } else if (sootType instanceof ArrayType) {
            // FIXME implement array type conversion
            return null;
        } else {
            throw new RuntimeException("Unknown type: " + sootType);
        }
        return javaType;
    }

    public Method getJavaMethod(SootMethod sootMethod) {

        Class<?> declaringClass = getJavaClass(sootMethod.getDeclaringClass());

        final int parameterCount = sootMethod.getParameterCount();
        Class<?>[] parameterTypesArray = new Class<?>[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            final Class<?> javaClass = getJavaClass(sootMethod
                    .getParameterType(i));
            // FIXME implement array type conversion
            if (javaClass == null)
                return getJavaWebMethodByName(sootMethod);
            parameterTypesArray[i] = javaClass;
        }

        Method method;
        try {
            method = declaringClass.getDeclaredMethod(sootMethod.getName(),
                    parameterTypesArray);
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Could not find method: "
                    + declaringClass.getCanonicalName() + "."
                    + sootMethod.getName()
                    + Arrays.toString(parameterTypesArray));
        }
        return method;
    }

    private Method getJavaWebMethodByName(SootMethod sootMethod) {
        Class<?> declaringClass = getJavaClass(sootMethod.getDeclaringClass());
        final String name = sootMethod.getName();
        Method method = null;
        for (Method m : declaringClass.getMethods()) {
            final int modifiers = m.getModifiers();
            if (m.getName().equals(name) && Modifier.isPublic(modifiers)
                    && !Modifier.isStatic(modifiers)) {
                if (method == null)
                    method = m;
                // FIXME find webmethods instead!?
            }
        }
        if (method == null)
            throw new IllegalArgumentException("Method "
                    + sootMethod.getSignature() + " with method name \"" + name
                    + "\" not found.");
        return method;
    }

    /**
     * Finds the least common super type of two types.
     */
    public RefType getLeastCommonSupertypeOf(RefType type1, RefType type2) {
        // SOOT HAS NOT IMPLEMENTED THIS!
        // return hiearchy.getLeastCommonSuperclassOf(type1.getSootClass(),
        // type2.getSootClass()).getType();
        return getSootClass(Object.class).getType();
    }

    private String getNextSignature() {
        if (nextSignature == null)
            nextSignature = SootMethod.getSignature(
                    getSootClass(WebContext.class), "next",
                    new ArrayList<Object>(), getSootClass(Object.class)
                            .getType());
        return nextSignature;
    }

    public Parameters getParameters(SootMethod method) {
        // TODO add check somewhere to ensure consistency between url-params and
        // parameter names
        if (parameterMap == null)
            parameterMap = new HashMap<SootMethod, Parameters>();
        Parameters parameters = parameterMap.get(method);
        if (parameters == null) {
            parameters = createParameters(method);
            parameterMap.put(method, parameters);
        }
        return parameters;
    }

    /**
     * Finds all implementations (in subtypes of the enclosing class) of the
     * given method
     * 
     * @param m
     *            as the method to analyze
     * @return the list of the possible call targets
     */
    @SuppressWarnings("unchecked")
    public Collection<SootMethod> getPossibleTargets(SootMethod m) {
        return hiearchy.resolveAbstractDispatch(m.getDeclaringClass(), m);
    }

    /**
     * Finds the Java #Class corresponding to the Soot #PrimType
     */
    Class<?> getPrimitiveType(PrimType t) {
        if (t instanceof IntType) {
            return int.class;
        }
        if (t instanceof CharType) {
            return char.class;
        }
        if (t instanceof ByteType) {
            return byte.class;
        }
        if (t instanceof FloatType) {
            return float.class;
        }
        if (t instanceof DoubleType) {
            return double.class;
        }
        if (t instanceof BooleanType) {
            return boolean.class;
        }
        if (t instanceof LongType) {
            return long.class;
        }
        if (t instanceof ShortType) {
            return short.class;
        }
        throw new RuntimeException("Unkown primitive type " + t);
    }

    /**
     * Finds the Java #Class corresponding to the Soot #RefType
     */
    private Class<?> getPrimitiveType(RefType t) {
        if (t.equals(getSootClass(Boolean.class).getType())) {
            return boolean.class;
        }

        if (t.equals(getSootClass(Integer.class).getType())) {
            return int.class;
        }

        if (t.equals(getSootClass(Character.class).getType())) {
            return char.class;
        }

        if (t.equals(getSootClass(Long.class).getType())) {
            return long.class;
        }

        if (t.equals(getSootClass(Double.class).getType())) {
            return double.class;
        }

        if (t.equals(getSootClass(Byte.class).getType())) {
            return byte.class;
        }

        if (t.equals(getSootClass(Short.class).getType())) {
            return short.class;
        }
        // java 'pure' reference type
        return null;
    }

    public SimpleLocalDefs getSimpleLocalDefs(Body body) {
        if (simpleLocalDefCache == null)
            simpleLocalDefCache = new HashMap<Body, SimpleLocalDefs>();
        if (!simpleLocalDefCache.containsKey(body)) {
            SimpleLocalDefs simpleLocalDefs = new SimpleLocalDefs(
                    new ExceptionalUnitGraph(body));
            simpleLocalDefCache.put(body, simpleLocalDefs);
        }
        return simpleLocalDefCache.get(body);
    }

    /**
     * Finds the {@link SootClass} corresponding to the Java {@link Class}
     * 
     * @param classs
     *            as the class to be found as {@link SootClass}
     * @return the corresponding {@link SootClass}
     */
    public SootClass getSootClass(Class<?> classs) {
        return Scene.v().getSootClass(classs.getName());
    }

    /**
     * Finds the {@link SootMethod} corresponding to the Java {@link Method}
     * 
     * @param method
     *            as the method to be found as {@link SootMethod}
     * @return the corresponding {@link SootMethod}
     */
    public SootMethod getSootMethod(Method method) {
        return getSootMethod(getSootClass(method.getDeclaringClass()), method);
    }

    /**
     * Finds the {@link SootMethod} in the containingClass corresponding to the
     * Java {@link Method}
     * 
     * @param containingClass
     *            as the class containing the method
     * @param method1
     *            as the method to be found as {@link SootMethod}
     * @return the corresponding {@link SootMethod}
     */
    public SootMethod getSootMethod(SootClass containingClass, Method method1) {
        List<Type> argumentTypes = new LinkedList<Type>();
        for (Class<?> cl : method1.getParameterTypes()) {
            Type sootType = getSootType(cl);
            argumentTypes.add(sootType);
        }
        return containingClass.getMethod(method1.getName(), argumentTypes);
    }

    /**
     * Finds the Soot #Type corresponding to the Java #Class
     * 
     * @param cl
     *            as the class to to be found as #Type
     * @return the #Type corresponding to the given #Class
     */
    public Type getSootType(Class<?> cl) {
        Type sootType;
        if (cl.isArray()) {
            String sig = cl.getName();
            int depth = 0;
            for (char c : sig.toCharArray()) {
                if (c == '[') {
                    depth++;
                } else {
                    break;
                }
            }
            sig = sig.substring(depth);
            char type = sig.charAt(0);
            Type baseType;
            if (type == 'L') {
                baseType = Scene.v()
                        .getSootClass(sig.substring(1).replace(";", ""))
                        .getType();
            } else if (type == 'Z') {
                baseType = BooleanType.v();
            } else if (type == 'B') {
                baseType = ByteType.v();
            } else if (type == 'C') {
                baseType = CharType.v();
            } else if (type == 'D') {
                baseType = DoubleType.v();
            } else if (type == 'F') {
                baseType = FloatType.v();
            } else if (type == 'I') {
                baseType = IntType.v();
            } else if (type == 'J') {
                baseType = LongType.v();
            } else if (type == 'S') {
                baseType = ShortType.v();
            } else {
                throw new IllegalArgumentException("Unknown array type: "
                        + cl.getName());
            }
            sootType = ArrayType.v(baseType, depth);
        } else if (cl.isPrimitive()) {
            if (cl == boolean.class) {
                sootType = BooleanType.v();
            } else if (cl == int.class) {
                sootType = IntType.v();
            } else if (cl == float.class) {
                sootType = FloatType.v();
            } else if (cl == byte.class) {
                sootType = ByteType.v();
            } else if (cl == double.class) {
                sootType = DoubleType.v();
            } else if (cl == long.class) {
                sootType = LongType.v();
            } else if (cl == short.class) {
                sootType = ShortType.v();
            } else if (cl == char.class) {
                sootType = CharType.v();
            } else {
                throw new IllegalArgumentException("Unknown primitive type: "
                        + cl);
            }
        } else {
            sootType = getSootClass(cl).getType();
        }
        return sootType;
    }

    /**
     * Finds all subtypes (inclusive) of {@link URL} in the hierarchy
     * 
     * @return all URL types
     */
    public Collection<Type> getURLTypes() {
        if (urlTypes == null) {
            urlTypes = new LinkedList<Type>();
            List<SootClass> classes = getHiearchy().getSubclassesOfIncluding(
                    getSootClass(URL.class));
            for (SootClass cl : classes) {
                urlTypes.add(cl.getType());
            }
        }
        return urlTypes;
    }

    public String getXMLPlugSignature() {
        if (xmlPlugSignature == null) {
            final ArrayList<Object> args = new ArrayList<Object>();
            RefType objectArg = Scene.v().getSootClass(Object.class.getName())
                    .getType();
            RefType stringArg = Scene.v().getSootClass(String.class.getName())
                    .getType();
            args.add(stringArg);
            args.add(objectArg);
            xmlPlugSignature = SootMethod.getSignature(getSootClass(XML.class),
                    "plug", args, getSootClass(XML.class).getType());
        }
        return xmlPlugSignature;
    }

    private boolean hasNextCall(Method method) {
        return hasNextCall(getSootMethod(method));
    }

    /**
     * Decides wether a WebMethod makes any calls to {@link WebContext#next()}.
     * All known implementations of the method are also checked.
     * 
     * @param method
     *            as the WebMethod to analyze
     * @return true iff the WebMethod contains a call to
     *         {@link WebContext#next()}
     */
    boolean hasNextCall(SootMethod method) {
        // TODO next() could be nested arbitrarily deep in delegates

        // check all implementations:
        for (SootMethod calledMethod : getPossibleTargets(method)) {
            CompleteUnitGraph cug = new CompleteUnitGraph(
                    calledMethod.retrieveActiveBody());
            // check every statement for a next() invocation
            for (Unit aCug : cug) {
                assert aCug instanceof Stmt;
                final Stmt stmt = (Stmt) aCug;
                if (stmt.containsInvokeExpr()) {
                    if (isNext(stmt.getInvokeExpr().getMethod())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasURLPatternAnnotation(Method method) {
        URLPattern annotation = method.getAnnotation(URLPattern.class);
        return annotation != null;
    }

    /**
     * Tells wether a WebMethod has an explicit {@link URLPattern} or not.
     * 
     * @param method
     *            as the WebMethod to analyze
     * @return true iff the WebMethod has an {@link URLPattern}
     */
    boolean hasURLPatternAnnotation(SootMethod method) {

        // convert to regular java-method:
        Class<?> declaringClass;
        try {
            declaringClass = Class
                    .forName(method.getDeclaringClass().getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        Method target = null;
        for (Method javaMethod : declaringClass.getMethods()) {
            // LATER as long as overloading of webmethod isn't possible, this
            // should be sufficient
            if (javaMethod.getName().equals(method.getName())) {
                target = javaMethod;
                break;
            }
        }

        boolean hasURLPattern = false;
        // check for occurence
        if (target != null) {
            hasURLPattern = hasURLPatternAnnotation(target);
        }

        return hasURLPattern;
    }

    /**
     * Convenience method for checking if a type is a subtype of another,
     * disregarding interfaces.
     */
    public boolean is(SootClass actual, SootClass to) {
        reload();
        return !actual.isInterface()
                && hiearchy.isClassSubclassOfIncluding(actual, to);
    }

    public boolean isAbstractPersistable(Type type) {
        if (type instanceof RefType) {
            RefType refType = (RefType) type;
            // lazy
            if (abstractPersistables == null) {
                SootClass abstractPersistable = Scene.v().getSootClass(
                        AbstractPersistable.class.getName());
                abstractPersistables = getHiearchy().getSubclassesOf(
                        abstractPersistable);
            }
            return abstractPersistables.contains(refType.getSootClass());
        }
        return false;

    }

    public boolean isAssignable(Type from, Type to) {
        boolean assignable = false;
        if (from == null || to == null) {
            log.error("isAssignable received null argument: [" + from + ","
                    + to + "]");
            return false;
        }

        if (from.equals(to)) {
            // same type
            assignable = true;
        } else if (from instanceof RefType && to instanceof RefType) {
            // reference type to reference type
            RefType fromRef = (RefType) from;
            RefType toRef = (RefType) to;
            if (hiearchy.isClassSubclassOfIncluding(fromRef.getSootClass(),
                    toRef.getSootClass())) {
                // subclass to super class
                assignable = true;
            }
        } else if (from instanceof NullType && to instanceof RefType) {
            // null to ReferenceType
            assignable = true;
        } else if (from instanceof PrimType && to instanceof PrimType) {
            // primitive to primitive
            Class<?> fromPrim = getPrimitiveType((PrimType) from);
            Class<?> toPrim = getPrimitiveType((PrimType) to);
            if (toPrim.isAssignableFrom(fromPrim)) {
                // TODO improve with dOvs type widening and narrowing of the
                // types
                assignable = true;
            }
        } else if (from instanceof RefType && to instanceof PrimType) {
            // reference to primitive --> unsafe autoboxing
            Class<?> fromPrim = getPrimitiveType((RefType) from);
            Class<?> toPrim = getPrimitiveType((PrimType) to);
            if (fromPrim != null && toPrim.isAssignableFrom(fromPrim)) {
                assignable = true;
                Feedbacks.add(new PossibleNullPointerInMakeURL(from, to));
            }
        } else if (from instanceof PrimType && to instanceof RefType) {
            // primitive to reference--> safe autoboxing
            Class<?> fromPrim = getPrimitiveType((PrimType) from);
            Class<?> toPrim = getPrimitiveType((RefType) to);
            if (toPrim != null && toPrim.isAssignableFrom(fromPrim)) {
                assignable = true;
            }
        }
        return assignable;
    }

    public boolean isFilter(Method method) {
        // the order of the predicates is this as the first predicate is faster
        // to compute
        return hasURLPatternAnnotation(method) && hasNextCall(method);
    }

    /**
     * Decides whether a WebMethod is a filter or not: it is a filter iff it
     * makes a call to {@link WebContext#next()} AND has an explicit
     * {@link URLPattern}.
     * 
     * @param method
     *            as the {@link SootMethod} to analyze
     * @return true if the method is a filter
     */
    public boolean isFilter(SootMethod method) {
        // the order of the predicates is this as the first predicate is faster
        // to compute
        return hasURLPatternAnnotation(method) && hasNextCall(method);
    }

    public boolean isInterestingType(Type type) {
        return getInterestingTypes().contains(type);
    }

    /**
     * Decides whether a method is a makeURL-method or not.
     * 
     * @param method
     *            as the method to test
     * @return true iff the method is a makeURL-method
     */
    public boolean isMakeURL(SootMethod method) {
        return MakeURLSignatureHandler.getMakeURLSignatures().containsKey(
                method.getSignature());
    }

    public boolean isMappedMakeURL(SootMethod method) {
        return MakeURLSignatureHandler.isMappedMakeURL(method.getSignature());
    }

    public boolean isNext(SootMethod calledMethod) {
        return calledMethod.getSignature().equals(getNextSignature());
    }

    public boolean isParameterType(Type type) {
        // lazy
        if (parameterType == null)
            parameterType = getSootClass(dk.brics.jwig.Parameters.class)
                    .getType();
        return parameterType.equals(type);
    }

    private boolean isSession(RefType refType) {
        return isSession(refType.getSootClass());
    }

    /**
     * @return true iff the given class is of type #Session
     */
    public boolean isSession(SootClass cl) {
        return is(cl, getSootClass(Session.class));
    }

    /**
     * Decides if any of the parameters of the method is {@link Session} or a
     * subtype thereof.
     * 
     * The element type of Arrays is also considered
     * 
     * @param method
     *            as the method to analyze
     * @return true if any of the parameters is of type #Session
     */
    public boolean isSessionMethod(SootMethod method) {
        boolean usesSession = false;
        @SuppressWarnings("unchecked")
        final List<Type> parameterTypes = method.getParameterTypes();
        for (Type t : parameterTypes) {
            if (t instanceof RefType) {
                RefType refType = (RefType) t;
                usesSession = isSession(refType);
            } else if (t instanceof ArrayType) {
                // check if the array is a Session[]
                ArrayType arrayType = (ArrayType) t;
                Type type = arrayType.getElementType();
                if (type instanceof RefType) {
                    usesSession = isSession((RefType) type);
                }
            }
        }
        return usesSession;
    }

    /**
     * @return true iff the given class is of type #SubmitHandler
     */
    public boolean isSubmitHandler(SootClass cl) {
        return is(cl, getSootClass(SubmitHandler.class));
    }

    public boolean isSubmitHandler(Type type) {
        if (type instanceof RefType)
            return isSubmitHandler(((RefType) type).getSootClass());
        return false;
    }

    /**
     * @return true iff the given class is of type #WebApp
     */
    public boolean isWebApp(SootClass cl) {
        return is(cl, getSootClass(WebApp.class));
    }

    public boolean isWebApp(Type type) {
        return isWebApp(((RefType) type).getSootClass());
    }

    public boolean isXMLPlug(SootMethod method) {
        return method.getSignature().equals(getXMLPlugSignature());
    }

    /**
     * Loads the desired java {@link Class} into the Soot scene.
     * 
     * @param classs
     *            the class to load
     */
    public SootClass load(Class<?> classs) {
        final SootClass sootClass = Scene.v().loadClassAndSupport(
                classs.getName());
        sootClass.setApplicationClass();
        reload();
        return sootClass;
    }

    /**
     * Reloads the {@link Hierarchy}, to be used on Soot Scene changes.
     */
    public void reload() {
        this.hiearchy = new Hierarchy();
    }

    /**
     * Decides whether a list of arguments are type compatible with a list of
     * parameters.
     * 
     * It is a precondition that the two lists are of the same size
     * 
     * @param givenArgumentTypes
     *            as list of arguments
     * @param parameterTypes
     *            as the list of parameters
     * @return true iff the arguments are type compatible with the parameter
     *         list
     */
    public boolean typeCheckArguments(Type[] givenArgumentTypes,
            List<? extends Type> parameterTypes) {
        if (!(givenArgumentTypes.length == parameterTypes.size()))
            throw new IllegalArgumentException(
                    "The two lists are not the same size");

        Type[] paramTypes = parameterTypes.toArray(new Type[parameterTypes
                .size()]);
        for (int i = 0; i < givenArgumentTypes.length; i++) {
            if (!isAssignable(givenArgumentTypes[i], paramTypes[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Performs a local reachable definition analysis for a value, and returns
     * the types which could be assigned to the value.
     * 
     * @param value
     *            as the value to find assigned types for
     * @param st
     *            as the statement containing the value
     * @param body
     *            as the body containing the statemenet
     * @return the types the value can be assigned
     */
    public Set<Type> getReachingTypes(Value value, Stmt st, Body body) {
        Set<Type> types = new HashSet<Type>();
        for (Value v : getReachingValues(value, st, body)) {
            types.add(v.getType());
        }
        return types;
    }

    /**
     * Performs a local reachable definition analysis for a value, and returns
     * the values which could be assigned to the value.
     * 
     * @param value
     *            as the value to find assigned types for
     * @param st
     *            as the statement containing the value
     * @param body
     *            as the body containing the statemenet
     * @return the values the value can be assigned
     */
    public List<Value> getReachingValues(Value value, Stmt st, Body body) {
        List<Value> values = new ArrayList<Value>();
        if (value instanceof Local) {
            SimpleLocalDefs simpleLocalDefs = getSimpleLocalDefs(body);
            // find the actual type of the locals
            Local local = (Local) value;
            List<Unit> defsOfAt = simpleLocalDefs.getDefsOfAt(local, st);
            for (Unit unit : defsOfAt) {
                if (unit instanceof JAssignStmt) {
                    JAssignStmt assign = (JAssignStmt) unit;
                    values.add(assign.getRightOp());
                } else if (unit instanceof JIdentityStmt) {
                    JIdentityStmt identity = (JIdentityStmt) unit;
                    values.add(identity.getRightOp());
                } else {
                    throw new RuntimeException(
                            "getReachingValues: unknown type "
                                    + unit.getClass() + " at \""
                                    + unit.toString() + "\" in "
                                    + body.getMethod().getSignature());
                }

            }
        } else {
            // default to the value itself (usefull default for type analysis)
            values.add(value);
        }
        return values;
    }
}