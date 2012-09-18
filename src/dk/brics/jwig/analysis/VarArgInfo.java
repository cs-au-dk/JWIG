package dk.brics.jwig.analysis;

import soot.Type;

/**
 * DataClass for information about a varArg instantiation: ex. new Object[1]
 */
public class VarArgInfo {

    public int getSize() {
        return size;
    }

    public Type[] getTypes() {
        return types;
    }

    public Type getBaseType() {
        return baseType;
    }

    private final int size;
    private final Type[] types;
    private final Type baseType;

    public VarArgInfo(int size, Type[] varArgTypes, Type baseType) {
        this.size = size;
        this.types = varArgTypes;
        this.baseType = baseType;
    }

}
