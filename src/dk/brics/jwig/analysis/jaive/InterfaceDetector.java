package dk.brics.jwig.analysis.jaive;

import dk.brics.jwig.WebApp;
import dk.brics.jwig.WebSite;
import dk.brics.jwig.analysis.JwigResolver;
import dk.brics.jwig.server.RequestManager;
import org.apache.log4j.Logger;
import soot.Scene;
import soot.SootClass;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is capable of analyzing a {@link WebSite} and detecting all
 * WebMethods in it.
 */
public class InterfaceDetector {

    private final Logger log = Logger.getLogger(InterfaceDetector.class);
    private final JwigResolver resolver;

    /**
     * Constructs a new detector with the given {@link JwigResolver}.
     * 
     * @param resolver
     *            as the {@link JwigResolver} to use.
     */
    public InterfaceDetector() {
        this.resolver = JwigResolver.get();
    }

    /**
     * Detects all WebMethods in the {@link WebSite} and registers them in an
     * {@link Interface}
     * 
     * @param webSiteClass
     *            as the {@link WebSite} to analyze
     * @return the {@link Interface} for the {@link WebSite}
     */
    public Interface detect(Class<? extends WebSite> webSiteClass) {
        log.info("Finding WebSite interfaces");
        SootClass webSite = resolver.load(webSiteClass);
        Scene.v().loadNecessaryClasses();
        Collection<SootClass> webapps = resolver.findWebApps(webSite);

        Map<Class<? extends WebApp>, RequestManager> managers = new HashMap<Class<? extends WebApp>, RequestManager>();
        for (SootClass webApp : webapps) {
            Class<? extends WebApp> webAppClass;
            try {
                webAppClass = Class.forName(webApp.getName()).asSubclass(
                        WebApp.class);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            managers.put(webAppClass, makeRequestManager(webAppClass));
        }
        log.info("Done finding WebSite interfaces");

        return new Interface(managers);
    }

    /**
     * Constructs the {@link RequestManager} for the given {@link WebApp}.
     * 
     * @param webApp
     *            as the {@link WebApp} class to construct the
     *            {@link RequestManager} from
     * @return the {@link RequestManager} for the {@link WebApp}.
     */
    private RequestManager makeRequestManager(
            Class<? extends WebApp> webAppClass) {
        RequestManager man = new RequestManager();
        man.introspectWebAppClass(webAppClass);
        return man;
    }

}
