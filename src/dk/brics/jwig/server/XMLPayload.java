package dk.brics.jwig.server;

import dk.brics.jwig.Response;
import dk.brics.xact.XML;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;

/**
 * XML (XHTML) payload for a {@link Response}.
 */
public final class XMLPayload implements Payload {

    private static byte[] blank;

    static {
        blank = new byte[500];
        for (int i = 0; i < blank.length; i++) {
            blank[i] = ' ';
        }
    }

    private transient SoftReference<XML> xml;
    private final String serializedXML;
    private static final String CHARSET = "UTF-8";

    /**
     * Constructs a new XML payload.
     */
    public XMLPayload(XML xml) {
        this.xml = new SoftReference<XML>(xml);
        this.serializedXML = xml.toTemplate();
    }

    /**
     * Returns the XML value.
     */
    public XML getXML() {
        XML xml1 = xml.get();
        if (xml1 == null) {
            xml1 = XML.parseTemplate(serializedXML);
            xml = new SoftReference<XML>(xml1);
        }
        return xml1;
    }

    /**
     * Writes the payload.
     */
    @Override
	public void write(HttpServletRequest request, HttpServletResponse response,
                      int status, String contenttype) throws IOException { // TODO: optional runtime XHTML 1.0 validation
        String useragent = request.getHeader("User-Agent");
        if (contenttype == null) {
            if (useragent != null && (
                    useragent.contains("MSIE 8") ||
                    useragent.contains("MSIE 7") ||
                    useragent.contains("MSIE 6") ||
                    useragent.contains("Googlebot"))) {
                contenttype = "text/html";
            } else {
                contenttype = "application/xhtml+xml";
            }
        }
        response.setContentType(contenttype);
        response.setCharacterEncoding(CHARSET);
        ServletOutputStream servletOutputStream = response.getOutputStream();
        XML xml = getXML();
        ByteArrayOutputStream w = new ByteArrayOutputStream();
        boolean hack = status != HttpServletResponse.SC_OK; // hack to disable IE "friendly error messages" :-(
        xml.toDocument(w, CHARSET);
        int length = w.size();
        if (hack) {
            length += blank.length;
        }
        response.setContentLength(length);
        if (hack) {
            w.write(blank);
        }
        response.flushBuffer();
        servletOutputStream.write(w.toByteArray());
        servletOutputStream.close();
    }

    @Override
	public Object getValue() {
        return getXML();
    }
}
