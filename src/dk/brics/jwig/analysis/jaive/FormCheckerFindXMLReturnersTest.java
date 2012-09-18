package dk.brics.jwig.analysis.jaive;

import static org.junit.Assert.assertEquals;

import java.net.URL;
import java.util.Set;

import org.junit.Test;

import soot.SootMethod;
import dk.brics.jwig.SubmitHandler;
import dk.brics.jwig.WebApp;
import dk.brics.jwig.WebSite;
import dk.brics.jwig.analysis.graph.StateMachine;
import dk.brics.xact.XML;

public class FormCheckerFindXMLReturnersTest {
    public static class MyWebApp extends WebApp {
        public XML xml1() {
            return null;
        }

        public XML xml2() {
            return null;
        }

        public URL url() {
            return makeURL("");
        }

        public XML voidHandler() {
            return XML.parseTemplate("<form action=[HANLDER]/>").plug(
                    "HANDLER", new SubmitHandler() {
                        @SuppressWarnings("unused")
                        public void run() {
                            //
                        }
                    });
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

    @Test
    public void test() {
        StateMachine stateMachine = new Jaive(MyWebSite.class)
                .getStateMachine();
        Set<SootMethod> xmlReturners = new FormChecker(stateMachine)
                .findXMLReturners();
        assertEquals(5, xmlReturners.size());
    }
}
