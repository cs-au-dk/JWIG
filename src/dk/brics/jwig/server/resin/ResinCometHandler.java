package dk.brics.jwig.server.resin;

import com.caucho.servlet.comet.CometController;
import com.caucho.servlet.comet.GenericCometServlet;
import dk.brics.jwig.server.comet.CometEvent;
import dk.brics.jwig.server.comet.CometEventType;
import dk.brics.jwig.server.comet.Synchronizer;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * A bridge between the Resin Comet system and the generic Comet synchronizer in JWIG.
 */
public class ResinCometHandler extends GenericCometServlet {

	private Synchronizer synchronizer = new Synchronizer();

    /**
     * Constructs a new handler.
     */
    public ResinCometHandler() {}

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
     * Invoked by the clients via the servlet engine on BEGIN event.
     */
    @Override
	public boolean service(ServletRequest servletRequest, ServletResponse servletResponse, CometController cometController) throws IOException, ServletException {
        CometEvent event = new ResinCometEvent(cometController, CometEventType.BEGIN);
        return !synchronizer.handleComet((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse, event);
    }

    /**
     * Invoked by the clients via the servlet engine on READ event.
     */
    @Override
	public boolean resume(ServletRequest servletRequest, ServletResponse servletResponse, CometController cometController) throws IOException, ServletException {
        CometEvent event = new ResinCometEvent(cometController, CometEventType.READ);
        return !synchronizer.handleComet((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse, event);
    }

    private class ResinCometEvent implements CometEvent {

    	private CometController controller;
        
    	private CometEventType event;

        private ResinCometEvent(CometController controller, CometEventType event) {
            this.controller = controller;
            this.event = event;
        }

        @Override
		public CometEventType getEventType() {
            return event;
        }

        @Override
		public void setTimeout(int timeout) {
            controller.setMaxIdleTime(timeout);
        }
    }
}
