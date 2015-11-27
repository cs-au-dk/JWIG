package dk.brics.jwig.util;

import dk.brics.jwig.JWIGException;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.Type;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds data about method parameters.
 */
public class ParameterNamer {
    private static Logger log = Logger.getLogger(ParameterNamer.class);
    
    private ParameterNamer() {}

    /**
     * Returns the list of parameter names of the given method.
     */
    public static List<String> getParameterNames(java.lang.reflect.Method method) {
        Method parsedMethod = getBCELMethod(method);
        //There must be the same number of argument types as there are arguments
        Type[] types = parsedMethod.getArgumentTypes();
        LocalVariableTable table = parsedMethod.getLocalVariableTable();
        ArrayList<String> names = new ArrayList<String>();
	if (table != null) {
            //And the arguments are located in the first locals on the stack at pc=0
            for (int i = 0; i < types.length; i++) {
                int offset = i;
                if (!parsedMethod.isStatic()) {
                    offset++;//We add one to the offset for nonstatic methods since argument 0 is 'this'
                }
                LocalVariable localVariable = table.getLocalVariable(offset, 0);
                names.add(localVariable.getName());
            }
        }
        return names;

    }

    private static Method getBCELMethod(java.lang.reflect.Method method) {
        try {
            Class<?> declaringClass = method.getDeclaringClass();
            JavaClass javaClass = Repository.lookupClass(declaringClass);
            return javaClass.getMethod(method);
        } catch (ClassNotFoundException e) {
            throw new JWIGException(e);
        }
    }

    /**
     * Returns the contents type of a collection class argument.
     * If the argument is a collection class, this method
     * returns the class that this collection class is declared to contain. Wildcards
     * are allowed but generic parameters are not. The basic form of a supported declaration
     * is <code>java.util.List&lt;String&gt;</code>. If the collection is a raw type, null
     * is returned.
     */
    public static Class<?> getListType(java.lang.reflect.Method method, int parameterIndex) {
        Method bcelMethod = getBCELMethod(method);
        for (Attribute a : bcelMethod.getAttributes()) {
            if (a instanceof Signature) {
                try {
                    Signature signature = (Signature) a; //The signature is the generic signature of the type
                    String sig = signature.getSignature();
                    ArrayList<String> parameterArguments = new ArrayList<String>();
                    if (sig.startsWith("<")) {
                        String genericParametersSig = sig.substring(sig.indexOf("<")+1, sig.indexOf(">"));
                        ArrayList<String> strings = splitArguments(genericParametersSig);
                        for(String s : strings) {
                            parameterArguments.add(s.substring(0,s.indexOf("::")));
                        }
                    }
                    //Strip anything but the argument types
                    sig = sig.substring(sig.indexOf("(") + 1);
                    sig = sig.substring(0, sig.lastIndexOf(")") - 1);
                    ArrayList<String> strings = splitArguments(sig);
                    String parameterSig = strings.get(parameterIndex);
                    if (!parameterSig.contains("<")) {
                        return null;
                    }
                    String className = parameterSig.substring(parameterSig.indexOf("L") + 1, parameterSig.indexOf("<"));
                    JavaClass javaClass = Repository.lookupClass(className);
                    if (!Repository.instanceOf(javaClass, "java.util.Collection")) {
                        throw new IllegalArgumentException(javaClass.getClassName() + " is not a collection type");
                    }
                    String arguments = parameterSig.substring(parameterSig.indexOf("<") + 1, parameterSig.lastIndexOf(">"));
                    ArrayList<String> genericArguments = splitArguments(arguments);
                    if (genericArguments.size() != 1) {
                        throw new IllegalArgumentException("Collections with more than 1 generic arguments not supported");
                    }
                    String s = genericArguments.get(0);
                    String genericClass;
                    if (s.startsWith("+")) {
                        log.warn("Wildcard capture in method " + method.getDeclaringClass().getName() + method.getName());
                        genericClass = s.substring(2);
                    } else {
                        genericClass = s.substring(1);
                    }
                    int genericArgumentStart = genericClass.indexOf("<");
                    if (genericArgumentStart == -1) {
                        genericArgumentStart = genericClass.length();
                    }
                    String rawClassName = genericClass.substring(0, genericArgumentStart);
                    if (parameterArguments.contains(rawClassName)) {
                        throw new IllegalArgumentException("Error in method " +method.getDeclaringClass() + "." + method.getName() + ". Generic parameters not supported");
                    }
                    String javaClassName = Repository.lookupClass(rawClassName).getClassName();
                    return Class.forName(javaClassName);
                } catch (ClassNotFoundException e) {
                    throw new JWIGException(e);
                }
            }
        }
        return null;
    }

    /**
     * Splits the arguments in a generic signature into a list of arguments.
     */
    private static ArrayList<String> splitArguments(String sig) {
        ArrayList<String> strings = new ArrayList<String>();
        StringBuffer buf = new StringBuffer();
        int i = 0;
        for (char c : sig.toCharArray()) {
            if (c == '<') {
                i++;
            }
            if (c == '>') {
                i--;
            }
            if (i == 0) {
                if (c == ';') {
                    strings.add(buf.toString());
                    buf = new StringBuffer();
                } else {
                    buf.append(c);
                }
            } else {
                buf.append(c);
            }
        }
        String s = buf.toString();
        if (s.length() > 0) {
            strings.add(s);
        }
        return strings;
    }
}
