package dk.brics.jwig.util;

/**
 * HTTP quoted-string encoding/decoding.
 */
public class QuotedString {

	private QuotedString() {}
	
	/**
	 * Encodes the given string as a quoted-string.
	 */
	public static String encode(String s) {
		StringBuilder b = new StringBuilder();
		b.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '"')
				b.append('\\');
			b.append(c);
		}
		b.append('"');
		return b.toString();
	}

	/**
	 * Decodes the given quoted-string to a string.
	 */
	public static String decode(String s) {
		if (!s.startsWith("\"") || !s.endsWith("\""))
			return s;
		StringBuilder b = new StringBuilder();
		for (int i = 1; i+1 < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\' && i+2 < s.length())
				c = s.charAt(++i);
			b.append(c);
		}
		return b.toString();
	}
}
