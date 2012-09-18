package dk.brics.jwig.server;

import dk.brics.jwig.FileField;
import dk.brics.jwig.JWIGException;
import dk.brics.jwig.ParamName;
import dk.brics.jwig.Parameters;
import dk.brics.jwig.persistence.Persistable;
import dk.brics.jwig.util.ParameterNamer;
import dk.brics.xact.XML;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Gets methods parameter names.
 * (These are unfortunately not available by normal reflection...)
 *
 * @see ParamName
 */
public class ParamNameGetter {

    /**
     * Constructs a new method parameter name getter.
     */
    public ParamNameGetter() {
    }

    /**
     * Gets the parameter names for the given method.
     *
     * @throws JWIGException if parameter names are not found or if the types do not implement <code>valueOf(String)</code>,
     *                       are primitive, or are equal to {@link FileField} or {@link Parameters}.
     */
    public String[] getParameterNames(Method m) throws JWIGException {
        Class<?>[] types = m.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t.isArray()) {
                t = t.getComponentType();
            }
            if (Collection.class.isAssignableFrom(t)) {
                t = ParameterNamer.getListType(m, i);
            }
            if (!t.equals(XML.class) &&
                !t.equals(String.class) &&
                !t.isPrimitive() &&
                !FileField.class.equals(t) &&
                !URL.class.equals(t) && 
                !Parameters.class.equals(t) &&
                !Persistable.class.isAssignableFrom(t)) {
                try {
                    if (!Modifier.isStatic(t.getMethod("valueOf", String.class).getModifiers())) {
                        throw new JWIGException("valueOf(String) must be static for web method parameters: " + m.getName());
                    }
                } catch (NoSuchMethodException e) {
                    throw new JWIGException("web method " + m.getName() + " on " + m.getDeclaringClass() + " parameter does not implement valueOf(String)", e);
                }
            }
        }
        Annotation[][] pa = m.getParameterAnnotations();
        List<String> names = new LinkedList<String>();
        List<String> parameterNames = ParameterNamer.getParameterNames(m);
        for (int i = 0; i < pa.length; i++) {
            String name = null;
            for (Annotation a : pa[i]) {
                if (a.annotationType().equals(ParamName.class)) {
                    name = ((ParamName) a).value();
                }
            }
            if (name == null) { // if at least one parameter has no ParamName annotation, fall back to paranamer
                name = parameterNames.get(i);
                if (name == null) {
                    throw new JWIGException("parameter names not found for web method: " + m.toString());
                }

            }
            names.add(name);
        }
        return names.toArray(new String[names.size()]);
    }
}
