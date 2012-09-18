package micro;

import dk.brics.jwig.ParamName;
import dk.brics.jwig.WebApp;
import dk.brics.xact.XML;

import java.net.URL;

/**
 * @author schwarz
 * @created 16-05-2008 14:42:28
 */
public class ArgTest extends WebApp {

    public XML test(@ParamName("args")String... args) {
        return XML.parseTemplate("foo");

    }

    public XML test2() {
        String bar;
        if (hashCode() > 1000) {
            bar = "bar";
        } else {
            bar = "baz";
        }
        return test("foo", bar); //Should create an edge to a _lambda_ transition to test
    }

    public URL test3() {
        String bar;
        if (hashCode() > 1000) {
            bar = "test";
        } else {
            bar = "test2";
        }
        return makeURL(ArgTest.class, bar, new Object[]{"bar"}); //Should create an edge to both test and test2. Should also complain that test2 takes no arguments
    }

    public URL test4() {
        return makeURL(WebFop.class, "test");   //Should fail becase WebFob is not registered
    }

    public URL test5() {
        return makeURL(ArgTest.class, "mathias");  //Should fail because no such method exists
    }

    public URL test6(String s) {
        return makeURL(ArgTest.class, "test5", s); //Should fail because test5 takes no arguments
    }

    public URL test7() {
        return makeURL(ArgTest.class, "test6", new Object()); //Should fail because test6 takes String, not Object as argument
    }

    public URL test8() {
        return makeURL(ArgTest.class, "test7"); //Should succeed
    }

    public URL test9() {
        String bar;
        if (hashCode() < 1000) {
            bar = "mathias";
        } else {
            bar = "test8";
        }
        return makeURL(ArgTest.class, bar);
    }

    public class WebFop extends WebApp {

    }
}
