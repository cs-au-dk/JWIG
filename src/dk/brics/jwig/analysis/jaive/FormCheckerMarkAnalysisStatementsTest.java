package dk.brics.jwig.analysis.jaive;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import java.util.Set;

import org.junit.Test;

import soot.SootMethod;
import soot.ValueBox;
import soot.jimple.Stmt;
import dk.brics.jwig.SubmitHandler;
import dk.brics.jwig.WebApp;
import dk.brics.jwig.WebSite;
import dk.brics.jwig.analysis.graph.StateMachine;
import dk.brics.xact.XML;

public class FormCheckerMarkAnalysisStatementsTest {
    public static class MyWebApp extends WebApp {
        public XML singleReturn() {
            return null;
        }

        public XML doubleReturn() {
            if (x()) {
                return null;
            }
            return null;
        }

        public XML singlePlug() { // + 1 return
            return XML.parseTemplate("<wrap><[FOO]></wrap>").plug("FOO", null);
        }

        public XML doublePlug() { // + 1 return
            return XML.parseTemplate("<wrap><[FOO]><[BAR]></wrap>")
                    .plug("FOO", null).plug("BAR", null);
        }

        private boolean x() {
            return false;
        }

        public XML singleReturnInHandler() {
            return XML.parseTemplate("<form action=[HANLDER]/>").plug(
                    "HANDLER", new SubmitHandler() {
                        public XML run() {
                            return null;
                        }
                    });
        }

        public XML singlePlugInHandler() { // + 1 return
            return XML.parseTemplate("<form action=[HANLDER]/>").plug(
                    "HANDLER", new SubmitHandler() {
                        public XML run() {
                            return XML.parseTemplate("<wrap><[FOO]></wrap>")
                                    .plug("FOO", null);
                        }
                    });
        }
        // sum = 1 + 2 + 2 + 3 + 1 + 2 = 7 returns + 4 plugs = 11 statements
    }

    public static class MyWebSite extends WebSite {
        @Override
        public void init() {
            add(new MyWebApp());
        }
    }

    public static class MyEmptyWebSite extends WebSite {
        @Override
        public void init() {
            //
        }
    }

    private static final int JWIG_ANALYZE_STATEMENTS = 17;

    @Test
    public void testEmpty() {
        StateMachine stateMachine = new Jaive(MyEmptyWebSite.class)
                .getStateMachine();
        final FormChecker checker = new FormChecker(stateMachine);
        Set<SootMethod> xmlReturners = checker.findXMLReturners();
        Map<Stmt, ValueBox> analysisStatements = checker
                .markAnalysisStatements(stateMachine.getPluggings(),
                        xmlReturners);
        // no starting states --> empty state machine
        assertEquals(0, analysisStatements.size());
    }

    @Test
    public void test() {
        StateMachine stateMachine = new Jaive(MyWebSite.class)
                .getStateMachine();
        final FormChecker checker = new FormChecker(stateMachine);
        Set<SootMethod> xmlReturners = checker.findXMLReturners();
        Map<Stmt, ValueBox> analysisStatements = checker
                .markAnalysisStatements(stateMachine.getPluggings(),
                        xmlReturners);
        assertEquals(11 + JWIG_ANALYZE_STATEMENTS, analysisStatements.size());
    }
}
