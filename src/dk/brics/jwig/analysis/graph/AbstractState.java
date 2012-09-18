package dk.brics.jwig.analysis.graph;

import java.util.HashSet;
import java.util.Set;

import soot.SootMethod;
import dk.brics.jwig.analysis.JwigResolver;

public abstract class AbstractState implements State {
    private Set<Transition> transitions;
    private final SootMethod method;
    private final boolean inSession;
    private static JwigResolver resolver;
    private boolean defaultPriority;
    private int priority;

    /*
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (defaultPriority ? 1231 : 1237);
        result = prime * result + (inSession ? 1231 : 1237);
        result = prime * result + ((method == null) ? 0 : method.hashCode());
        result = prime * result + priority;
        return result;
    }

    /**
     * Two states are equal if they represents the same Method. 'inSession',
     * 'defaultPriority', 'priority' can be inferred from the method, but they
     * are compared too for ensuring consistency. <br/>
     * <br/>
     * <strong>The transitions from the states are not considered</strong>
     */
    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (o == null || !(o instanceof AbstractState))
            return false;
        AbstractState s = (AbstractState) o;

        if (getClass().equals(s.getClass()) && method.equals(s.method)) {
            if (inSession == s.inSession
                    && defaultPriority == s.defaultPriority
                    && priority == s.priority)
                return true;
            throw new IllegalArgumentException(
                    "The states have the same Method, but they haven't got the same inferred values from that Method!");
        }
        return false;
    }

    /**
     * @return the defaultPriority
     */
    @Override
    public boolean isDefaultPriority() {
        return defaultPriority;
    }

    /**
     * @param defaultPriority
     *            the defaultPriority to set
     */
    @Override
    public void setDefaultPriority(boolean defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    public AbstractState(SootMethod method) {
        defaultPriority = true;
        transitions = new HashSet<Transition>();
        this.method = method;
        if (resolver == null)
            resolver = JwigResolver.get();
        this.inSession = resolver.isSessionMethod(method);

    }

    @Override
    public void addSuccessor(Transition s) {
        s.setOrigin(this);
        transitions = new HashSet<Transition>(transitions);
        transitions.add(s);
    }

    /**
     * @return the method
     */
    @Override
    public SootMethod getMethod() {
        return method;
    }

    /**
     * @return the priority
     */
    @Override
    public int getPriority() {
        return priority;
    }

    /**
     * @return the transitions
     */
    @Override
    public Set<Transition> getTransitions() {
        return transitions;
    }

    /**
     * @return the inSession
     */
    @Override
    public boolean isInSession() {
        return inSession;
    }

    @Override
    public void removeSuccessor(Transition s) {
        transitions = new HashSet<Transition>(transitions);
        transitions.remove(s);
        s.setOrigin(null);
    }

    /**
     * @param priority
     *            the priority to set
     */
    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

}
