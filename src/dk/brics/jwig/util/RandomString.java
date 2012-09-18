package dk.brics.jwig.util;

import java.util.Random;

/**
 * Creates random printable strings.
 */
public class RandomString {
	
	private static Random rnd = new Random(); // Random is not perfect, but e.g. SecureRandom/randomUUID are probably too slow

	private RandomString() {}
	
	/**
	 * Returns a random printable string.
	 */
	public static String get(int length) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < length; i++)
			b.append((char)(rnd.nextInt(10) + '0'));
		return b.toString();
	}
}
