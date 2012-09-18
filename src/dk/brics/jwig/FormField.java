package dk.brics.jwig;

/**
 * Form field.
 * <p>
 * Constructed for web method arguments of type {@link Parameters}
 * (which matches any collection of form fields) or {@link FileField}.
 */
abstract public class FormField {

	/**
	 * Constructs a new form field.
	 */
	FormField() {}
	
    /**
     * Returns the value of this field.
     */ 
	abstract public String getValue();
}
