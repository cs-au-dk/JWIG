package micro;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import javax.mail.internet.InternetAddress;
import dk.brics.jwig.*;

public class EmailTest extends WebApp {
	public String send() throws MessagingException {
Email e = new Email();
		e.setText("This is a test!");
		e.setSubject("JWIG email test");
		e.setSender(new InternetAddress("unknown@example.org"));
		e.addRecipients(Message.RecipientType.TO, "amoeller@cs.au.dk");
		SendFailedException ex = sendEmail(e);
		if ((ex == null)) return "Email sent!";
		else return ex.toString();
		
		
} 
	}

