package dk.brics.jwig;

import dk.brics.jwig.server.DispatchListener;
import dk.brics.jwig.server.ThreadDispatchEvent;
import dk.brics.jwig.server.WebMethodDispatchEvent;

public abstract class DispatchAdapter implements DispatchListener {
    @Override
    public void threadDispatched(ThreadDispatchEvent t) {
    }

    @Override
    public void threadDismissed(ThreadDispatchEvent t) {
    }

    @Override
    public void webMethodDispatched(WebMethodDispatchEvent e) {
    }
}
