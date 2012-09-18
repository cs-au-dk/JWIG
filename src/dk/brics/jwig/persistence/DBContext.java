package dk.brics.jwig.persistence;

/**
 * Context in which JWIG maps the running program to a database session.
 */
class DBContext {
    private Thread activeThread;

    public Thread getActiveThread() {
        return activeThread;
    }

    public void setActiveThread(Thread activeThread) {
        this.activeThread = activeThread;
    }
}
