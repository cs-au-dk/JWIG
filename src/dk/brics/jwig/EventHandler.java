package dk.brics.jwig;

import dk.brics.jwig.server.ParamNameGetter;
import dk.brics.xact.XML;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Handler for HTML events.
 * <p>
 * A plain handler has a void <code>run</code> method that is invoked when the event occurs. 
 * <p>
 * The <code>run</code> method for a handler that has been constructed with a session argument returns
 * an XML value. This XML value can be obtained from an XML producer via {@link #getProducer()}.
 * <p>
 * The <code>run</code> methods may take parameters corresponding to form field values.
 */
@Regenerable
abstract public class EventHandler extends AbstractHandler {

    private static ParamNameGetter paramnamegetter = new ParamNameGetter();

    private String[] fields;

    private final boolean value;

    private XMLProducer producer;

    /**
     * Stores results for event handlers that return XML.
     * Uses a static map and event handler hash codes to allow regeneration of the handler objects.
     */
    private static Map<Session, Map<Integer, XML>> return_values = new WeakHashMap<Session, Map<Integer, XML>>();

    private Session ses;

    /**
     * Constructs a new event handler.
     */
    public EventHandler(Object... dependencies) {
        this(false, dependencies);
    }

    /**
     * Constructs a new event handler with a JavaScript boolean value.
     */
    public EventHandler(boolean value,Object... dependencies) {
        this(value, null,dependencies);
    }

    /**
     * Constructs a new event handler with a JavaScript boolean value and a session.
     */
    public EventHandler(boolean value, Session ses,Object... dependencies) {
        super(dependencies);
        setFields();
        this.value = value;
        this.ses = ses;
    }

    /**
     * Constructs a new event handler with a session.
     */
    public EventHandler(Session ses) {
        this(false, ses, new Object[0]); //array argument appears to fix what seems to be a Java 7 javac bug
    }

    private void setFields() {
        for (Method m : getClass().getDeclaredMethods()) {
            if (m.getName().equals("run")) {
                fields = paramnamegetter.getParameterNames(m);
                break;
            }
        }
        if (fields == null) {
            throw new JWIGException("'run' method not found in event handler");
        }
    }

    /**
     * Returns the JavaScript instruction for running this event handler.
     * The instruction results in the boolean value set by the event handler constructor.
     */
    @Override
    public final String getHandlerIdentifier() {
        return "jwig.sendFields('" + super.getHandlerIdentifier() + "'," + toJavaScriptArray(fields) + ");return " + value;
    }

    private static String toJavaScriptArray(String[] strings) {
        StringBuilder b = new StringBuilder();
        b.append('[');
        boolean first = true;
        for (String string : strings) {
            if (first) {
                first = false;
            } else {
                b.append(',');
            }
            b.append('\'').append(string).append('\'');
        }
        b.append(']');
        return b.toString();
    }

    /**
     * Creates an XML producer for this event handler.
     */
    public XMLProducer getProducer() {
        if (producer == null) {
            producer = new XMLProducer() {
                XML run() {
                    return getXMLValue(ses);
                }
            };
        }
        return producer;
    }

    private XML getXMLValue(Session ses) {
        Map<Integer, XML> map = getMap(ses);
        XML def = XML.parseTemplate("");
		XML xml = map.get(this.hashCode());
		if (xml == null) {
		    xml = def;
		}
		return xml;
    }

    private Map<Integer, XML> getMap(Session ses) {
        Map<Integer, XML> map = return_values.get(ses);
        if (map == null) {
            map = new HashMap<Integer, XML>();
            return_values.put(ses, map);
        }
        return map;
    }

    /**
     * Invoked by the runtime system after the <code>run</code> method has been invoked
     * to store the resulting XML value for the XML producer, and then notifies
     * listeners of the XML producer.
     */
    public void setLatestReturnValue(XML val) {
        if (ses == null || producer == null) {
            return;
        }
        XML latest = getXMLValue(ses);
        if (!latest.equals(val)) {
            if (val == null) {
                getMap(ses).remove(this.hashCode());
            } else {
                getMap(ses).put(this.hashCode(),val);
            }
            producer.update();
        }
    }
}
