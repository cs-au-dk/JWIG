package dk.brics.jwig.analysis.jaive;

import static org.junit.Assert.assertEquals;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import soot.SootMethod;
import dk.brics.jwig.SubmitHandler;
import dk.brics.jwig.WebApp;
import dk.brics.jwig.WebSite;
import dk.brics.jwig.analysis.graph.StateMachine;
import dk.brics.xact.XML;

public class FormCheckerMarkAnalysisClassesTest {
    public static class MyWebApp extends WebApp {
        public URL url() {
            return makeURL("");
        }

        public XML xmlHandler() {
            return XML.parseTemplate("<form action=[HANLDER]/>").plug(
                    "HANDLER", new SubmitHandler() {
                        @SuppressWarnings("unused")
                        public XML run() {
                            return null;
                        }
                    });
        }
    }

    public static class MyWebSite extends WebSite {
        @Override
        public void init() {
            add(new MyWebApp());
        }
    }

    private static final int JWIG_CLASSES = 2;

    @Test
    public void test() {
        StateMachine stateMachine = new Jaive(MyWebSite.class)
                .getStateMachine();
        final FormChecker checker = new FormChecker(stateMachine);
        Set<SootMethod> xmlReturners = checker.findXMLReturners();
        final Collection<Plugging> pluggings = stateMachine.getPluggings();
        List<String> classes = checker.markAnalysisClasses(pluggings,
                xmlReturners);
        // webapp & handler = 2 classes

        assertEquals(2 + JWIG_CLASSES, classes.size());
    }
}
