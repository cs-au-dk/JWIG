package dk.brics.jwig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>An object of type Parameters contains a number og request parameters that are sent by the client
 * but whose names may not be known by the programmer at compile time. If a web method declares
 * an argument of type Parameters all request parameters that are not matched by the preceding
 * arguments on the list of formal method parameters are represented using this parameters object.
 *
 * <p>A Parameters object contains the values for each of these parameters as either {@link TextField}
 * objects or {@link FileField} objects depending on the type sent from the client. A file can
 * thus be received by the program by simply reading the input stream from such a file field.
 */
public class Parameters {

	private final LinkedHashMap<String,List<FormField>> parameters;
	
	/**
	 * Constructs a new parameters object. 
	 */
	public Parameters(LinkedHashMap<String,List<FormField>> parameters) {
		this.parameters = parameters;
	}
	
	/**
	 * Returns the parameter map.
	 */
	public Map<String,List<FormField>> getMap() {
		return parameters;
	}
	
	/**
	 * Returns list of <code>FormField</code> objects for the given parameter name.
	 * Returns an empty list if there are no such parameters.
	 */
	public List<FormField> getList(String name) {
		List<FormField> list = parameters.get(name);
		if (list == null)
			list = Collections.emptyList();
		return list;
	}
	
	/**
	 * Returns the <code>FormField</code> object for the given parameter name.
	 * Returns null if there is no such parameter.
	 */
	public FormField getField(String name) {
		List<FormField> list = parameters.get(name);
		if (list == null || list.isEmpty())
			return null;
		return list.get(0);
	}
	
	/**
	 * Returns the string value for the given parameter name.
	 * Returns null if there is no such parameter.
	 */
	public String getValue(String name) {
		FormField f = getField(name);
		if (f == null)
			return null;
		return f.getValue();
	}
}
