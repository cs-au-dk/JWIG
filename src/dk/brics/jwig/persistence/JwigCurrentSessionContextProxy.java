package dk.brics.jwig.persistence;

import java.util.LinkedList;
import java.util.List;

/**
 * A proxy class that the rest of the framework can contact JwigCurrentSessionContext through to avoid unwanted dependencies
 * on Hibernate where Hibernate is not used.
 */
public class JwigCurrentSessionContextProxy {
    static List<ConstructorCallBack> cs = new LinkedList<ConstructorCallBack>();

    private static boolean sessionContextInstanciated;

    public static void instanciated() {
        sessionContextInstanciated = true;
    }

    /**
     * @see dk.brics.jwig.persistence.JwigCurrentSessionContextProxy.ConstructorCallBack
     */
    public static void registerCallBack(ConstructorCallBack c) {
        if (!sessionContextInstanciated) {
            cs.add(c);
        } else {
            c.call(JwigCurrentSessionContext.getCurrentSessionContext());
        }
    }

    /**
     * Hibernate has a strange way of creating these session contexts. Instead of a
     * factory method, it is asserted that a public constructer taking 1 parameter
     * (namely a {@link org.hibernate.engine.SessionFactoryImplementor}). This means that we cannot give the
     * one instance of the session context any parameters. To make up for this an object
     * of this type can be created and will be run as a part of the constructor. If the
     * object is already constructed the code will be run right away.
     */
    public interface ConstructorCallBack {
        public void call(JwigCurrentSessionContext sessionContext);
    }
}
