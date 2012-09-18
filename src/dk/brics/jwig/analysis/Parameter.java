package dk.brics.jwig.analysis;

import soot.SootMethod;
import soot.Type;

public class Parameter {

    private final boolean required;

    private final String name;

    private final Type type;

    private final SootMethod host;

    /**
     * @return the host
     */
    public SootMethod getHost() {
        return host;
    }
    public Parameter(String name, Type type, boolean required, SootMethod host) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.host = host;
    }
    /**
     * @return the name
     */
    public String getName() {
        return name;
    }
    /**
     * @return the type
     */
    public Type getType() {
        return type;
    }

    /**
     * @return the required
     */
    public boolean isRequired() {
        return required;
    }

}
