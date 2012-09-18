package dk.brics.jwig.analysis.jaive;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import soot.Body;
import soot.RefType;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import dk.brics.jwig.WebContext;
import dk.brics.jwig.WebSite;
import dk.brics.jwig.analysis.JwigResolver;
import dk.brics.jwig.analysis.graph.HandlerState;
import dk.brics.jwig.analysis.graph.State;
import dk.brics.jwig.analysis.graph.StateMachine;
import dk.brics.jwig.analysis.graph.StateMachine.MethodStatementContainer;
import dk.brics.jwig.analysis.graph.WebMethodState;
import dk.brics.jwig.analysis.jaive.feedback.SourceUtil;
import dk.brics.xact.XML;
import dk.brics.xact.analysis.XMLAnalysis;
import dk.brics.xact.analysis.config.StandardConfiguration;
import dk.brics.xact.analysis.flowgraph.FlowGraph;
import dk.brics.xact.analysis.flowgraph.statements.AnalyzeStm;
import dk.brics.xact.analysis.soot.TranslationResult;
import dk.brics.xact.analysis.xmlgraph.XMLGraphBuilder;
import dk.brics.xmlgraph.ChoiceNode;
import dk.brics.xmlgraph.ElementNode;
import dk.brics.xmlgraph.Node;
import dk.brics.xmlgraph.NodeProcessor;
import dk.brics.xmlgraph.XMLGraph;
import dk.brics.xmlgraph.converter.XMLGraph2Dot;

public class FormChecker {

    public static class JWIGConfiguration extends StandardConfiguration {
        private final Set<ValueBox> hotspots;

        public JWIGConfiguration(Collection<ValueBox> collection) {
            super();
            this.hotspots = new HashSet<ValueBox>(collection);
        }

        @Override
        public Set<ValueBox> getHotspots() {
            return hotspots;
        }

    }

    private static final Logger log = Logger.getLogger(FormChecker.class);

    private final StateMachine machine;

    private final JwigResolver resolver;

    private final Map<ReturnStmt, MethodStatementContainer> returnLocations;

    public FormChecker(StateMachine machine) {
        this.machine = machine;
        resolver = JwigResolver.get();
        returnLocations = new HashMap<ReturnStmt, StateMachine.MethodStatementContainer>();
    }

    public void checkForms() {
        log.info("Checking forms");
        final Collection<Plugging> pluggings = machine.getPluggings();
        Set<SootMethod> xmlReturners = findXMLReturners();
        Map<Stmt, ValueBox> stmt2box = markAnalysisStatements(pluggings,
                xmlReturners);
        List<String> classes = markAnalysisClasses(pluggings, xmlReturners);

        //
        // run xact//
        //
        log.info("Starting xact analysis");
        // non constant gap names has been reported already - no need to crash
        // and burn on them
        System.setProperty("dk.brics.xact.analysis.ignore-non-constant-string",
                true + "");
        XMLAnalysis analysis = new XMLAnalysis(null, classes);
        analysis.loadClasses();
        analysis.setConfiguration(new JWIGConfiguration(stmt2box.values()));
        TranslationResult result = analysis.buildFlowGraph();

        analysis.transformFlowGraph(result.getGraph());

        final FlowGraph graph = result.getGraph();
        XMLGraphBuilder xmlGraphs = analysis.buildXMLGraphs(graph);
        log.info("xact analysis done");

        Map<ChoiceNode, Set<Plugging>> gap2plug = linkGapsToPluggings(
                pluggings, stmt2box, result, xmlGraphs);

        Map<ReturnStmt, XMLGraph> ret2xg = linkReturnsToGraphs(stmt2box,
                result, xmlGraphs);

        Map<ChoiceNode, Set<ElementNode>> shGap2form = linkFormsAndSubmitHandlers(
                gap2plug, ret2xg);

        System.out.println("FORM GAPS");
        for (Entry<ChoiceNode, Set<ElementNode>> entry : shGap2form.entrySet()) {
            System.out.println(entry.toString());
        }
        // find submithandler run method (if it exists, if not: ignore as an
        // errorneous SH has been reported already) (if Parameters used: ignore)

        // find input names

        // match with runmethod

        // (find input types(!), match with runmethod)
        log.info("Done checking forms");
    }

    private boolean couldBeSubmitHandler(Set<Plugging> pluggings) {
        for (Plugging plugging : pluggings) {
            Set<Type> types = plugging.getTypes();
            for (Type type : types) {
                if (resolver.couldBeSubmitHandler(type))
                    return true;
            }
        }
        return false;
    }

    List<String> markAnalysisClasses(final Collection<Plugging> pluggings,
            Set<SootMethod> xmlReturners) {
        List<String> classNames = new ArrayList<String>();
        Set<SootClass> classes = new HashSet<SootClass>();
        for (Plugging plugging : pluggings) {
            classes.add(plugging.getContainer().getMethod().getDeclaringClass());
        }
        for (SootMethod xmlReturningMethod : xmlReturners) {
            classes.add(xmlReturningMethod.getDeclaringClass());
        }
        // FIXME these arent automatically added to the classes despite having
        // plug/makeURL entries!?
        classes.add(resolver.getSootClass(WebContext.class));
        classes.add(resolver.getSootClass(WebSite.class));

        for (SootClass clazz : classes) {
            final String name = clazz.getJavaStyleName();
            if (name == null) {
                throw new RuntimeException("javastylename is null for class: "
                        + clazz);
            }
            classNames.add(name);
        }

        return classNames;
    }

    Map<Stmt, ValueBox> markAnalysisStatements(
            final Collection<Plugging> pluggings, Set<SootMethod> xmlReturners) {
        Map<Stmt, ValueBox> stmt2box = new HashMap<Stmt, ValueBox>();
        // add the return statements of the xml returning
        // methods to the
        // analysis

        for (SootMethod method : xmlReturners) {
            Body body = method.retrieveActiveBody();
            for (Unit unit : body.getUnits()) {
                if (unit instanceof ReturnStmt) {
                    ReturnStmt stmt = (ReturnStmt) unit;
                    final ValueBox box = stmt.getOpBox();
                    returnLocations.put(stmt, new MethodStatementContainer(
                            method, stmt));
                    stmt2box.put(stmt, box);
                }
            }
        }

        // add the plug statements
        for (Plugging plugging : pluggings) {
            final AssignStmt stmt = plugging.getStmt();
            final ValueBox box = stmt.getInvokeExprBox();
            stmt2box.put(stmt, box);
        }
        return stmt2box;
    }

    Set<SootMethod> findXMLReturners() {
        // find webmethods / handler run methods which return XML
        Set<SootMethod> xmlReturners = new HashSet<SootMethod>();
        Set<State> allStates = machine.getAllStates();
        final SootClass xml = resolver.getSootClass(XML.class);
        for (State state : allStates) {
            if (state instanceof WebMethodState
                    || state instanceof HandlerState) {
                final SootMethod method = state.getMethod();
                final Type returnType = method.getReturnType();
                // TODO explicitly checking for the XML type. XML content
                // should be checked too perhaps?
                if (returnType instanceof RefType
                        && resolver.is(((RefType) returnType).getSootClass(),
                                xml))
                    xmlReturners.add(method);
            }
        }
        return xmlReturners;
    }

    private Set<ElementNode> getEnclosingForms(ChoiceNode gap,
            Plugging plugging, Map<ReturnStmt, XMLGraph> ret2xg) {
        // TODO optimize (greatly!) by only considering the WebMethods and
        // SubmitHandler-runmethods which can reach this plugging

        Set<ElementNode> forms = new HashSet<ElementNode>();
        for (XMLGraph xg : ret2xg.values()) {
            NodeProcessor<Set<ElementNode>> isInFormActionChecker = new IsInFormActionChecker(
                    xg);
            Node node = xg.getNode(gap.getIndex());
            final Set<ElementNode> parentForms = node
                    .process(isInFormActionChecker);
            forms.addAll(parentForms);
        }
        return forms;
    }

    private Map<ChoiceNode, Set<ElementNode>> linkFormsAndSubmitHandlers(
            Map<ChoiceNode, Set<Plugging>> gap2plug,
            Map<ReturnStmt, XMLGraph> ret2xg) {
        // find submithandler pluggings forms (<form action=SH>...)
        Map<ChoiceNode, Set<ElementNode>> shGap2form = new HashMap<ChoiceNode, Set<ElementNode>>();
        for (Entry<ChoiceNode, Set<Plugging>> entry : gap2plug.entrySet()) {
            ChoiceNode gap = entry.getKey();
            Set<Plugging> plugValues = entry.getValue();
            // only check possible submithandlers
            if (couldBeSubmitHandler(plugValues)) {
                for (Plugging plugging : plugValues) {
                    Set<ElementNode> forms = getEnclosingForms(gap, plugging,
                            ret2xg);
                    if (!forms.isEmpty())
                        shGap2form.put(gap, forms);
                }
            }

        }
        return shGap2form;
    }

    private Map<ChoiceNode, Set<Plugging>> linkGapsToPluggings(
            final Collection<Plugging> pluggings, Map<Stmt, ValueBox> stmt2box,
            TranslationResult result, XMLGraphBuilder xmlGraphs) {
        // link xml gaps to their plug values
        Map<ChoiceNode, Set<Plugging>> gap2plug = new HashMap<ChoiceNode, Set<Plugging>>();
        for (Plugging plugging : pluggings) {
            final AssignStmt statement = plugging.getStmt();
            AnalyzeStm analyzeStm = result.getHotspots().get(
                    stmt2box.get(statement));
            if (analyzeStm != null) {
                XMLGraph xg = xmlGraphs.getIn(analyzeStm, analyzeStm.getBase());
                final GapFinderByName gapFinder = new GapFinderByName(
                        plugging.getNames());
                log.debug("looking for gaps with the name: "
                        + plugging.getNames().getFiniteStrings());
                log.debug("the xml graph is known to have the following gaps:");

                if (getGapNames(xg).isEmpty()) {
                    System.out.println("empty gaps for: "
                            + SourceUtil.getLocation(plugging.getContainer()
                                    .getMethod(), plugging.getContainer()
                                    .getStatement()));
                    new XMLGraph2Dot(new PrintWriter(System.out)).print(xg);
                }
                xg.processNodes(gapFinder);
                for (ChoiceNode gap : gapFinder.getGaps()) {
                    if (!gap2plug.containsKey(gap))
                        gap2plug.put(gap, new HashSet<Plugging>());
                    gap2plug.get(gap).add(plugging);
                }
            } else
                log.error("analyzeStmt is null for: " + statement
                        + " associated with " + stmt2box.get(statement));
        }
        return gap2plug;
    }

    private Set<String> getGapNames(XMLGraph xg) {
        Set<String> closedAttributeGaps = xg.getClosedAttributeGaps();
        Set<String> openAttributeGaps = xg.getOpenAttributeGaps();
        Set<String> closedTemplateGaps = xg.getClosedTemplateGaps();
        Set<String> openTemplateGaps = xg.getOpenTemplateGaps();
        Set<String> names = new HashSet<String>();
        names.addAll(closedTemplateGaps);
        names.addAll(closedAttributeGaps);
        names.addAll(openAttributeGaps);
        names.addAll(openTemplateGaps);
        return names;
    }

    private Map<ReturnStmt, XMLGraph> linkReturnsToGraphs(
            Map<Stmt, ValueBox> stmt2box, TranslationResult result,
            XMLGraphBuilder xmlGraphs) {
        // link return values to their xmlgraphs
        Map<ReturnStmt, XMLGraph> ret2xg = new HashMap<ReturnStmt, XMLGraph>();
        for (Stmt stmt : stmt2box.keySet()) {
            if (!(stmt instanceof ReturnStmt)) // ignore plugs
                continue;

            ReturnStmt returnStmt = (ReturnStmt) stmt;

            AnalyzeStm analyzeStm = result.getHotspots().get(
                    stmt2box.get(returnStmt));
            if (analyzeStm == null) {
                final MethodStatementContainer container = returnLocations
                        .get(returnStmt);
                System.err.println(returnStmt
                        + " -- "
                        + stmt2box.get(returnStmt)
                        + " @ "
                        + SourceUtil.getLocation(container.getMethod(),
                                container.getStatement())
                        + " --> null analyzestmt");
            } else {
                XMLGraph xg = xmlGraphs.getIn(analyzeStm, analyzeStm.getBase());
                new XMLGraph2Dot(new PrintWriter(System.out)).print(xg);
                ret2xg.put(returnStmt, xg);
            }
        }
        return ret2xg;
    }
}
