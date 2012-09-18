package dk.brics.jwig.server.comet;

import dk.brics.jwig.JWIGException;
import dk.brics.jwig.Response;
import dk.brics.jwig.server.Config;
import dk.brics.jwig.server.Dispatcher;
import dk.brics.jwig.server.ThreadContext;
import dk.brics.jwig.server.cache.Cache;
import org.apache.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

// XXX: jsmin disabled in build.xml -- use YUI Compressor or dojo instead?
// FIXME: jwig.js needs more testing!

/**
 * Synchronizer.
 * <p>
 * Receives Comet long-polls from clients and sends back update instructions.
 */
public class Synchronizer {

    private final Logger log = Logger.getLogger(Synchronizer.class);

	private final Map<HttpServletResponse,Listener> connection2listener = new HashMap<HttpServletResponse,Listener>();

	private final Map<String,Collection<Listener>> fragment2listeners = new HashMap<String,Collection<Listener>>();

	private int max_long_polling;

    /**
	 * Constructs a new synchronizer.
	 */
	public Synchronizer() {
		super();
	}

    /**
     * Invoked by the servlet engine when starting.
     */
    public void init() {
    	log.info("Initializing");
    	ThreadContext.setSynchronizer(this);
    	max_long_polling = Config.get("jwig.max_long_polling", 1000);
    }

    /**
     * Invoked by the servlet engine when stopping.
     */
    public void destroy() {
    	for (HttpServletResponse response : connection2listener.keySet()) {
    		if (!response.isCommitted()) {
    			response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    			response.setHeader("Connection", "close");
    			try {
					response.getOutputStream().close();
				} catch (IOException e) {
					throw new JWIGException(e);
				}
    		}
    	}
    	connection2listener.clear();
    	fragment2listeners.clear();
        ThreadContext.setSynchronizer(null);
        log.info("Stopped");
    }

    /**
     * Handles a Comet request from a client.
     * Invoked by a Comet handler.
     * For a BEGIN event, the connection is stored and it is checked (using the cache) whether updates are available already.
     * For an END event, the connection is removed.
     * @return true if request should be closed, false to keep alive
     */
    public boolean handleComet(HttpServletRequest request, HttpServletResponse response, CometEvent event) throws IOException, ServletException {
        if (event.getEventType() == CometEventType.BEGIN) {
        	String[] fragmentnames = request.getParameterValues("h");
        	String[] etags = request.getParameterValues("e");
        	log.debug("Poll from " + Dispatcher.getClient(request) +
        			", fragmentnames=" + Arrays.deepToString(fragmentnames) +
        			", etags=" + Arrays.deepToString(etags));
        	if (fragmentnames == null || etags == null || fragmentnames.length != etags.length) {
        		log.error("Bad request: invalid fragmentnames/etags");
        		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        		response.setHeader("Connection", "close");
        		response.setContentLength(0);
        		response.getOutputStream().close();
        		return true;
        	}
        	if (connection2listener.size() >= max_long_polling) {
        		log.warn("max_long_polling reached");
        		response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        		response.setHeader("Connection", "close");
        		response.setContentLength(0);
        		response.getOutputStream().close();
        		return true;
        	}
        	Cache cache = ThreadContext.getCache();
        	StringBuilder updates = new StringBuilder();
        	for (int i = 0; i < fragmentnames.length; i++) {
        		String name = fragmentnames[i];
        		String etag = etags[i];
        		Response p = cache.get(name);
        		if (p == null || !etag.equals("\"" + p.getETag() + "\""))
        			updates.append(name).append(';');
        	}
        	if (updates.length() > 0) {
        		String u = updates.toString();
    			log.debug("Update ready already: " + u);
    			send(u, response);
    			return false;
        	}
        	event.setTimeout(30000);
        	addListener(new Listener(response, fragmentnames));
        } else if (event.getEventType() == CometEventType.END ||
        		event.getEventType() == CometEventType.ERROR) {
        	removeListener(response);
        	if (!response.isCommitted()) {
        		response.setHeader("Connection", "close");
        		response.setContentLength(0);
        		response.getOutputStream().close();
        	}
        	return true;
        } else
        	log.info("Event " + event.getEventType() + " ignored");
        return false;
    }

    /**
 	 * Adds a new listener connection.
 	 */
 	synchronized private void addListener(Listener lis) {
 		connection2listener.put(lis.response, lis);
 		for (String name : lis.fragmentnames) {
 			Collection<Listener> ls = fragment2listeners.get(name);
 			if (ls == null) {
 				ls = new LinkedList<Listener>();
 				fragment2listeners.put(name, ls);
 			}
 			ls.add(lis);
 		}
 		log.debug("Listener added, #connections=" + connection2listener.size() + ", #fragments=" + fragment2listeners.size());
 	}

 	/**
 	 * Removes all information about the given connection.
 	 */
 	synchronized private void removeListener(HttpServletResponse response) {
 		Listener lis = connection2listener.remove(response);
 		if (lis != null) {
 			for (String name : lis.fragmentnames) {
 				Collection<Listener> ls = fragment2listeners.get(name);
 				if (ls != null) {
 					ls.remove(lis);
 					if (ls.isEmpty()) {
 						fragment2listeners.remove(name);
 					}
 				}
 			}
 		}
 		log.debug("Listener removed, #connections=" + connection2listener.size() + ", #fragments=" + fragment2listeners.size());
 	}

	/**
	 * Invoked when a page fragment has been updated.
	 * Informs all listeners.
	 */
	synchronized public void update(String name) {
		log.debug("Update ready: " + name);
		Collection<Listener> ls = fragment2listeners.get(name);
		if (ls != null) {
			for (Listener lis : ls) {
				send(name, lis.response);
				connection2listener.remove(lis.response);
			}
		}
		fragment2listeners.remove(name);
 		log.info("Update command sent, #connections=" + connection2listener.size() + ", #fragments=" + fragment2listeners.size());
	}

	/**
	 * Informs a listener that a page fragment has been updated and should be reloaded.
	 */
	private void send(String name, HttpServletResponse response) {
		try {
			response.setHeader("Connection", "close");
			response.setContentLength(name.length());
			PrintWriter w = response.getWriter();
			w.print(name);
			w.close();
		} catch (IOException e) {
			log.debug(e.getMessage());
		}
	}

	/**
	 * A connection from a client that awaits updates for a set of page fragment names.
	 */
	private static class Listener {

		final HttpServletResponse response;

		final String[] fragmentnames;

		Listener(HttpServletResponse response, String[] fragmentnames) {
			this.response = response;
			this.fragmentnames = fragmentnames;
		}
	}
}
