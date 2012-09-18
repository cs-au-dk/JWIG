package dk.brics.jwig;

import dk.brics.jwig.server.ThreadContext;

import javax.mail.internet.MimeMessage;

/**
 * An email message.
 * <p>
 * See <a target="_top" href="http://java.sun.com/products/javamail/">JavaMail</a>.
 * @see WebContext#sendEmail(Email)
 */
public class Email extends MimeMessage {
	
	/**
	 * Constructs a new empty email for the current email session.
	 */
	public Email() {
		super(ThreadContext.getEmailSession());
	}
}
