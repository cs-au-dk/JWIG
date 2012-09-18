package dk.brics.jwig;

/**
 * Text form field (or equivalent).
 * @see FormField
 */
public class TextField extends FormField {
	
	private final String value;
	
	/**
	 * Constructs a new text form field.
	 */
	public TextField(String value) {
		this.value = value;
	}

	/**
	 * Returns the value of this field.
	 */
	@Override
	public String getValue() {
		return value;
	}
}
