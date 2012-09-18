package dk.brics.jwig.server;

import dk.brics.jwig.Response;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Plain-text payload for a {@link Response}.
 */
public final class PlaintextPayload implements Payload {
	
	private String str;
	
	/**
	 * Constructs a new plain-text payload.
	 */
	public PlaintextPayload(String str) {
		this.str = str;
	}
	
	/**
	 * Returns the plain-text value.
	 */
	public String getText() {
		return str;
	}
	
	/**
	 * Writes the payload.
	 */
	@Override
	public void write(HttpServletRequest request, HttpServletResponse response, 
			int status, String contenttype) throws IOException {
		if (contenttype == null)
			contenttype = "text/plain";
		response.setContentType(contenttype);
		response.setCharacterEncoding("UTF-8");
		PrintWriter w = response.getWriter();
		w.print(str);
		w.close();
	}

    @Override
	public Object getValue() {
        return getText();
    }
}
