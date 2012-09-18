package kickstart;

import dk.brics.jwig.URLPattern;
import dk.brics.jwig.WebApp;

/**
 * A simple web application that simply returns "Hello World!" when the URL /hello/ is requested
 */
@URLPattern("hello")
public class HelloApp extends WebApp {

    @URLPattern("")
    public String hello() {
        return "Hello World!";
    }
}
