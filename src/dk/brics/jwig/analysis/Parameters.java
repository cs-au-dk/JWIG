package dk.brics.jwig.analysis;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Parameters {
    private final Map<String, Parameter> parameters;

    public Parameters(Set<Parameter> parameterSet) {
        this.parameters = new HashMap<String, Parameter>();
        for (Parameter parameter : parameterSet) {
            parameters.put(parameter.getName(), parameter);
        }
    }

    public Set<String> getNames() {
        return parameters.keySet();
    }

    public Parameter getParameter(String name) {
        return parameters.get(name);
    }

    public Collection<Parameter> getParameters() {
        return parameters.values();
    }

}
