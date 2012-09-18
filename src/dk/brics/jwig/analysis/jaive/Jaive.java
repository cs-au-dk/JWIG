package dk.brics.jwig.analysis.jaive;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import soot.SootClass;
import soot.SootMethod;
import dk.brics.jwig.WebApp;
import dk.brics.jwig.WebSite;
import dk.brics.jwig.analysis.DotVisualizer;
import dk.brics.jwig.analysis.JwigResolver;
import dk.brics.jwig.analysis.graph.State;
import dk.brics.jwig.analysis.graph.StateMachine;
import dk.brics.jwig.analysis.jaive.feedback.Feedbacks;
import dk.brics.jwig.analysis.siteMap.SiteMapper;

public class Jaive {
    private static final File DIR = new File("test/out");
    private static InterfaceDetector interfaceDetector;
    private static InvocationDetector invocationDetector;
    private static InterfaceInvocationLinker linker;
    private static InterfaceInvocationChecker checker;
    private static Interface interfacee;
    private static StateMachine stateMachine;
    private final JwigResolver resolver;

    /**
     * Takes one or three arguments:
     * 
     * 1: WebSite class name to analyze
     * 
     * 2: (for siteMap) root class
     * 
     * 3: (for siteMap) root method
     */
    public static void main(String[] args) throws ClassNotFoundException {
        Class<? extends WebSite> webSiteClass = Class.forName(args[0])
                .asSubclass(WebSite.class);
        Jaive jaive = new Jaive(webSiteClass);
        jaive.doChecks();
        SootMethod root = null;
        if (args.length == 3) {
            final JwigResolver resolver = JwigResolver.get();
            Class<?> rootClass = Class.forName(args[1]);
            resolver.load(rootClass);
            root = resolver.getSootClass(rootClass).getMethodByName(args[2]);
        }
        jaive.printSiteMaps(root);
    }

    public Jaive(Class<? extends WebSite> webSiteClass) {
        resolver = JwigResolver.get();
        interfaceDetector = new InterfaceDetector();
        invocationDetector = new InvocationDetector();
        linker = new InterfaceInvocationLinker();
        checker = new InterfaceInvocationChecker();
        interfacee = interfaceDetector.detect(webSiteClass);
        stateMachine = invocationDetector.detect(interfacee);
        linker.link(interfacee, stateMachine);
    }

    StateMachine getStateMachine() {
        return stateMachine;
    }
    public void doChecks() {
        checker.check(stateMachine);
        String prettyPrint = Feedbacks.prettyPrint();
        System.out.println(prettyPrint);
        write(new File(DIR, "feedback.txt"), prettyPrint);
    }

    public void printSiteMaps(SootMethod root) {
        StringBuilder sb = new StringBuilder();
        final SiteMapper siteMapper = new SiteMapper(stateMachine);
        sb.append("FULL:\n");
        final String full = siteMapper.getFullSiteMap().prettyPrint();
        sb.append(full);
        write(new File(DIR, "full.dot"), full);
        sb.append("RANKED:\n");
        final String ranked = siteMapper.getRankedSiteMap().prettyPrint();
        sb.append(ranked);
        write(new File(DIR, "ranked.dot"), ranked);
        if (root != null) {
            sb.append("BFS:\n");
            final String bfs = siteMapper.getBreadthFirstSiteMap(root)
                    .prettyPrint();
            sb.append(bfs);
            write(new File(DIR, "bfs.dot"), bfs);
        }
        final String string = sb.toString();
        System.out.println(string);
    }

    public void saveFlowGraph() {
        DotVisualizer visualizer = new DotVisualizer(stateMachine);
        System.out.println("Saving flowgraph");
        saveFlowGraph(visualizer, "lambda");

        final StateMachine clone = stateMachine.clone();
        clone.removeLambdas();

        DotVisualizer v2 = new DotVisualizer(clone);
        saveFlowGraph(v2, "");
        System.out.println("Done");
    }

    private void saveFlowGraph(DotVisualizer visualizer, String suffix) {
        // full
        String full = visualizer.visualize(true);
        write(new File(DIR, "flow_full_" + suffix + ".dot"), full);

        // by class
        Set<Class<? extends WebApp>> webApps = interfacee.getWebApps();
        for (Class<? extends WebApp> webApp : webApps) {
            SootClass sootClass = resolver.getSootClass(webApp);
            @SuppressWarnings("unchecked")
            final Set<SootClass> set = Collections.singleton(sootClass);
            String graph = visualizer.visualize(set);
            write(new File(DIR, sootClass.getShortName() + "@"
                    + sootClass.getJavaPackageName() + suffix + ".dot"), graph);
        }

        // by method
        for (State state : stateMachine.getAllStates()) {
            String predecessors = visualizer.visualizePredecessors(state);
            final SootMethod method = state.getMethod();
            final String methodName = method.getName();
            final String methodIdentifier = methodName
                    + "@"
                    + method.getDeclaringClass().getJavaStyleName()
                            .replace("/", ".");
            final String predName = methodIdentifier + "_pred_" + suffix;
            write(new File(DIR, predName + ".dot"), predecessors);
            String successors = visualizer.visualizeSuccessors(state);
            final String succName = methodIdentifier + "_succ_" + suffix;
            write(new File(DIR, succName + ".dot"), successors);
            System.out.print(".");
        }
    }

    private void write(File file, String text) {
        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new FileWriter(file));
            out.write(text);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (out != null)
                    out.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
