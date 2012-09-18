package dk.brics.jwig.analysis.jaive.feedback;

import soot.RefType;
import soot.Type;

public class PossibleNullPointerInMakeURL extends AbstractFeedback {

    public PossibleNullPointerInMakeURL(Type actual, Type bound) {
        // TODO this does not provide location...
        message = "Assigning variable of type "
                + ((RefType) actual).getClassName() + " to " + bound
                + " may result in a null pointer dereference in makeURL call";
        type = FeedbackType.WARNING;
    }
}
