package dk.brics.jwig.server;

import dk.brics.jwig.WebApp;

public class WebMethodDispatchEvent {
    private WebApp dispatchedApp;
    private String dispatchedMethodName;
    private Object[] args;

    public WebMethodDispatchEvent(WebApp dispatchedApp, String dispatchedMethodName, Object[] args) {
        this.dispatchedApp = dispatchedApp;
        this.dispatchedMethodName = dispatchedMethodName;
        this.args = args;
    }

    public WebApp getDispatchedApp() {
        return dispatchedApp;
    }

    public String getDispatchedMethodName() {
        return dispatchedMethodName;
    }

    public Object[] getArgs() {
        return args;
    }
}
