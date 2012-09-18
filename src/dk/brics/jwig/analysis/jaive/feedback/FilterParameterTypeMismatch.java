package dk.brics.jwig.analysis.jaive.feedback;

import dk.brics.jwig.analysis.Parameter;

public class FilterParameterTypeMismatch extends AbstractFeedback {

    public FilterParameterTypeMismatch(Parameter fromParameter,
            Parameter toParameter) {
        final String filterSig = toParameter.getHost().getSignature();
        final String webMethodSig = fromParameter.getHost().getSignature();
        message = "The filter " + filterSig + " has parameter \""
                + toParameter.getName() + "\" of type "
                + toParameter.getType().toString()
                + ". But when the filter is invoked from " + webMethodSig
                + " the parameter has the type "
                + fromParameter.getType().toString();
    }
}
