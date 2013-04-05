package dk.brics.jwig;

import dk.brics.jwig.server.Payload;
import dk.brics.jwig.server.PlaintextPayload;
import dk.brics.jwig.server.ThreadContext;
import dk.brics.jwig.server.XMLPayload;
import dk.brics.xact.XML;
import org.apache.log4j.Logger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * Response for an HTTP request. The Response object holds the instantiated handlers as well as a payload of data
 * that is sent to the client.
 *
 * Programmers can store data related to the computation of the response in the {@link #responseScopeData} property
 * of the Response
 */
public class Response {

	private final Logger log = Logger.getLogger(Response.class);

	private Payload payload;

	private String etag;

	private long last_modified;

	private Map<String,AbstractHandler> handlers;

	private int status;

	private Set<Session> sessions;

	private String contenttype;

	private boolean augmented;

	private List<Cookie> cookies;

    private static String RES = "JWIG_RESSC" + new Random().nextInt();

    /**
	 * Constructs a new empty response with status code 200 (OK).
	 */
	public Response() {
		handlers = new HashMap<>();
		status = HttpServletResponse.SC_OK;
		refresh(null);
	}

	/**
	 * Constructs a new empty response from an existing one.
	 * Inherits handlers, status, sessions, content type from the existing response object.
	 */
	public Response(Response p) {
		handlers = p.handlers;
		status = p.status;
		sessions = p.sessions;
	    contenttype = p.contenttype;
        augmented = p.augmented;
        refresh(null);
	}

	/**
	 * Sets the status code.
	 * Default: 200 (OK) (See {@link HttpServletResponse} for possible status codes)
	 */
	public void setStatus(int status) {
		this.status = status;
	}

	/**
	 * Sets the Content-Type.
	 * Usually set automatically depending on the payload.
	 */
	public void setContentType(String contenttype) {
		this.contenttype = contenttype;
	}

	/**
	 * Returns the status code.
	 */
	public int getStatus() {
		return status;
	}

	/**
	 * Registers a handler for this response.
	 */
	public void setHandler(String name, AbstractHandler sh) {
		log.debug("Registering handler: " + name);
		handlers.put(name, sh);
	}

	/**
	 * Returns a registered handler, or null if not found.
	 */
	public AbstractHandler getHandler(String name) {
		log.debug("Finding handler: " + name);
		return handlers.get(name);
	}

	/**
	 * Returns the registered handlers.
	 */
	public Collection<AbstractHandler> getHandlers() {
		return handlers.values();
	}

	/**
	 * Refreshes the ETag and the Last-Modified timestamp.
	 */
	public void refresh(Object value) {
		etag = ThreadContext.get().getETag(value);
		last_modified = new Date().getTime();
	}

	/**
	 * Returns the current ETag of this response.
	 */
	public String getETag() {
		return etag;
	}

	/**
	 * Returns the current Last-Modified timestamp of this response.
	 */
	public long getLastModified() {
		return last_modified;
	}

	/**
	 * Adds a session as relevant for the response.
	 */
	public void addSession(Session s) {
		if (sessions == null)
			sessions = new HashSet<Session>();
		sessions.add(s);
	}

	/**
	 * Returns the associated sessions.
	 * @return non-null set of sessions
	 */
	public Set<Session> getSessions() {
		if (sessions != null)
			return sessions;
		else
			return Collections.emptySet();
	}

	/**
	 * Sets the payload to the given XML value.
	 */
	synchronized public void setXML(XML xml) {
		refresh(xml);
		payload = new XMLPayload(xml);
	}

    /**
	 * Returns the XML payload.
	 * @throws JWIGException if the response has no XML value
	 */
	synchronized public XML getXML() throws JWIGException {
		if (payload == null || !(payload instanceof XMLPayload)) {
			throw new JWIGException("Response has no XML value");
		}
		return ((XMLPayload)payload).getXML();
	}

	/**
	 * Sets the payload to the given plain-text value.
	 */
	synchronized public void setText(String str) {
		refresh(str);
		payload = new PlaintextPayload(str);
	}

	/**
	 * Returns the plain-text payload.
	 * @throws JWIGException if the response has no plain-text value
	 */
	synchronized public String getText() throws JWIGException {
		if (payload == null || !(payload instanceof PlaintextPayload)) {
			throw new JWIGException("Response has no plain-text value");
		}
		return ((PlaintextPayload)payload).getText();
	}

    /**
     * Returns the payload
     */
    synchronized public Object getObject() {
        if (payload == null) {
            throw new JWIGException("Response has no value");
        }
        return payload.getValue();
    }

    /**
	 * Checks whether the payload is XML.
	 */
	public boolean isXML() {
		return payload != null && payload instanceof XMLPayload;
	}

	/**
	 * Checks whether the payload is plain-text.
	 */
	public boolean isText() {
		return payload != null && payload instanceof PlaintextPayload;
	}

    /**
	 * Returns augmented status.
	 */
	public boolean isAugmented() {
		return augmented;
	}

	/**
	 * Sets augmented status.
	 */
	public void setAugmented(boolean augmented) {
		this.augmented = augmented;
	}

	/**
	 * Adds a cookie.
	 */
	public void addCookie(Cookie c) {
		if (cookies == null) {
			cookies = new ArrayList<Cookie>();
		}
		cookies.add(c);
    }

    /**
     * Writes this response as HTTP response to the client and closes the response.
     */
    public void write(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        if (!request.getProtocol().equals("HTTP/1.1")) {
            response.setHeader("Pragma", "no-cache");
        }
        response.setDateHeader("Last-Modified", (last_modified / 1000) * 1000);
        response.setHeader("ETag", "\"" + etag + "\"");
        if (cookies != null) {
            for (Cookie c : cookies) {
                response.addCookie(c);
            }
        }
        if (payload != null) {
            try {
                payload.write(request, response, status, contenttype);
            } catch (IOException e) {
				log.warn("IOException when writing response: " + e.getMessage());
			}
		} else {
			log.error("No payload to write");
		}
	}

    public boolean hasPayload(){
        return payload != null;
    }

    public void setResult(Object result) {
        if (result instanceof XML) {
            setXML((XML) result);
        } else if (result instanceof String) {
            setText((String) result);
        } else throw new JWIGException("Tried to set result of type " + result.getClass());
    }

    public Object getResult() {
        if (hasPayload()) {
            return payload.getValue();
        }
        return null;
    }

    /**
      * Sets the responseScopeData object on the current response. See {@link #getResponseScopeData(Class)}.
     *  At most one object can be added for each type.
      *
      * @param <E>
      */
    public <E> void setResponseScopeData(E e) {
        Class<?> type = e.getClass();
        Map<Class<?>, Object> classObjectMap = getRequestResponseMap();
        classObjectMap.put(type, e);
    }


    /**
        * Returns the responseScopeData object for the current response. The object should be a Java bean containing
        * properties corresponding to the data that is tracked as part of the response generation.
        *
        * @param <E> The type of the returned object
        * @return
        */
    public <E> E getResponseScopeData(Class<E> type) {
        Map<Class<?>, Object> classObjectMap = getRequestResponseMap();
        Object o = classObjectMap.get(type);
        if (!o.getClass().equals(type)) {
            throw new JWIGException("No response scope data of type " + type.getName());
        } else
            return (E) o;
    }

    /**
     * Returns true if the current response has response code data of the given type
     * @param type
     * @return
     */
    public boolean hasResponseScopeData(Class<?> type) {
        Map<Class<?>, Object> classObjectMap = getRequestResponseMap();
        if (classObjectMap == null) return false;
        return classObjectMap.containsKey(type);
    }

    private Map<Class<?>, Object> getRequestResponseMap() {
        ThreadContext threadContext = ThreadContext.get();
        HttpServletRequest servletRequest = threadContext.getServletRequest();
        Map<Class<?>, Object> classObjectMap = (Map<Class<?>, Object>) servletRequest.getAttribute(RES);
        if (classObjectMap == null) {
            classObjectMap = new HashMap<>();
            servletRequest.setAttribute(RES,classObjectMap);
        }
        return classObjectMap;
    }
}
