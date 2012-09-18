package dk.brics.jwig.analysis.jaive.feedback;

import soot.SootClass;

public class DuplicateWebApp extends AbstractFeedback {

    public DuplicateWebApp(SootClass constructedClass) {
        message = "Trying to add the same WebApp more than once: "
                + constructedClass.getName();
    }

}
