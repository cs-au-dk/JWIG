package dk.brics.jwig.server;

public class ThreadDispatchEvent {
    private Thread thread;

    ThreadDispatchEvent(Thread thread) {
        this.thread = thread;
    }

    public Thread getThread() {
        return thread;
    }
}
