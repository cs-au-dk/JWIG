package dk.brics.jwig.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

import dk.brics.jwig.JWIGException;

/**
 * URL-encoding/decoding.
 */
public class URLEncoding {
	
	private URLEncoding() {}

	/**
	 * URL-encodes the given string (with UTF-8).
	 */
	public static String encode(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new JWIGException(e);
		}
	}

	/**
	 * URL-decodes the given string (with UTF-8).
	 */
	public static String decode(String url) {
		try {
			return URLDecoder.decode(url, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new JWIGException(e);
		}
	}
}
