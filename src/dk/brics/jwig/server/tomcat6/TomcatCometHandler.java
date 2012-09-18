package dk.brics.jwig.server.tomcat6;

import org.apache.catalina.CometProcessor;
import org.apache.catalina.CometEvent;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;

import dk.brics.jwig.server.comet.CometEventType;
import dk.brics.jwig.server.comet.Synchronizer;

/**
 * A bridge between the Tomcat Comet system and the generic Comet synchronizer in JWIG.
 */
public class TomcatCometHandler extends HttpServlet implements CometProcessor {
	
    private Synchronizer synchronizer = new Synchronizer();
    
    /**
     * Constructs a new handler.
     */
    public TomcatCometHandler() {}

    /**
     * Invoked when the handler is destroyed.
     */
    @Override
	public void destroy() {
        synchronizer.destroy();
    }

    /**
     * Invoked when the handler is initialized.
     */
    @Override
	public void init() {
        synchronizer.init();
    }

    /**
     * Invoked by the clients via the servlet engine on each event.
     */
 	@Override
	synchronized public void event(CometEvent event) throws IOException, ServletException {
 		HttpServletRequest request = event.getHttpServletRequest();
        HttpServletResponse response = event.getHttpServletResponse();
        if (synchronizer.handleComet(request, response, new TomcatCometEvent(event))) {
            event.close();
        }
    }

    private class TomcatCometEvent implements dk.brics.jwig.server.comet.CometEvent {
    	
        private CometEvent cometEvent;

        private TomcatCometEvent(CometEvent cometEvent) {
            this.cometEvent = cometEvent;
        }

        @Override
		public CometEventType getEventType() {
            switch (cometEvent.getEventType()) {
                case BEGIN: 
                	return CometEventType.BEGIN;
                case ERROR: 
                	return CometEventType.ERROR;
                case END: 
                	return CometEventType.END;
                case READ: 
                	return CometEventType.READ;
            }
            return null;
        }

        @Override
		public void setTimeout(int timeout) throws IOException, ServletException {
            cometEvent.setTimeout(timeout);
        }
    }
}
