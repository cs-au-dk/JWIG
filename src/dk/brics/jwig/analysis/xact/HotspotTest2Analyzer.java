package dk.brics.jwig.analysis.xact;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.ReturnStmt;
import dk.brics.automaton.Automaton;
import dk.brics.xact.analysis.XMLAnalysis;
import dk.brics.xact.analysis.config.StandardConfiguration;
import dk.brics.xact.analysis.flowgraph.FlowGraph;
import dk.brics.xact.analysis.flowgraph.statements.AnalyzeStm;
import dk.brics.xact.analysis.soot.TranslationResult;
import dk.brics.xact.analysis.xmlgraph.XMLGraphBuilder;
import dk.brics.xmlgraph.XMLGraph;
import dk.brics.xmlgraph.converter.XMLGraph2Dot;

public class HotspotTest2Analyzer {

    public static class JWIGConfiguration extends StandardConfiguration {
        private final Set<ValueBox> hotspots;

        public JWIGConfiguration(Set<ValueBox> hotspots) {
            super();
            this.hotspots = hotspots;
        }

        @Override
        public Set<ValueBox> getHotspots() {
            return hotspots;
        }

    }

    public static void main(String[] args) throws IOException {
        // init xact
        XMLAnalysis analysis = new XMLAnalysis(null,
                Collections.singletonList(HotspotTest1.class.getName()));
        analysis.loadClasses();

        Set<ValueBox> hotspots = new HashSet<ValueBox>();
        Map<ValueBox, SootMethod> hotspot2method = new HashMap<ValueBox, SootMethod>();
        for (SootClass cl : Scene.v().getApplicationClasses()) {
            for (SootMethod m : cl.getMethods()) {
                if (!m.isConcrete())
                    continue; // ignore abstract methods etc.
                if (!m.isPublic())
                    continue; // only public
                if (!m.getReturnType().equals(RefType.v("dk.brics.xact.XML")))
                    continue; // only methods that return XML (FIXME: Will
                              // exclude more specific types like Element)
                Body body = m.retrieveActiveBody();
                for (Unit unit : body.getUnits()) {
                    if (unit instanceof ReturnStmt) {
                        ReturnStmt stmt = (ReturnStmt) unit;
                        hotspots.add(stmt.getOpBox());
                        hotspot2method.put(stmt.getOpBox(), m);
                    }
                }
            }
        }

        analysis.setConfiguration(new JWIGConfiguration(hotspots));

        // run xact
        TranslationResult result = analysis.buildFlowGraph();

        analysis.transformFlowGraph(result.getGraph());

        final FlowGraph graph = result.getGraph();
        XMLGraphBuilder xmlGraphs = analysis.buildXMLGraphs(graph);

        int counter = 0;
        for (Map.Entry<ValueBox, AnalyzeStm> en : result.getHotspots()
                .entrySet()) {
            ValueBox value = en.getKey();
            AnalyzeStm stm = en.getValue();
            XMLGraph g = xmlGraphs.getIn(stm, stm.getBase());
            List<Automaton> findInputNameValues = findInputNameValues(g);
            for (Automaton automaton : findInputNameValues) {
                System.out.println(automaton.getFiniteStrings());
            }
            SootMethod method = hotspot2method.get(value);

            System.out
                    .printf("Return stmt from method %s has an XML graph with %d root nodes\n",
                            method.getName(), g.getRoots().size());

            PrintWriter output = new PrintWriter(new File("test/out/"
                    + method.getName() + counter + ".dot"));
            try {
                new XMLGraph2Dot(output).print(g);
            } finally {
                output.close();
            }
            counter++;
        }
    }

    private static List<Automaton> findInputNameValues(XMLGraph g) {
        Set<Integer> rootIndexes = g.getRoots();
        InputNameValueFinder finder = new InputNameValueFinder(g);
        List<Automaton> results = new ArrayList<Automaton>();
        for (Integer rootIndex : rootIndexes) {
            results.addAll(g.getNode(rootIndex).process(finder));
        }
        return results;
    }
}
