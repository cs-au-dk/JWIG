package dk.brics.jwig.analysis.graph;

public abstract class AbstractTransition implements Transition, Cloneable {
    private State target;
    private State origin;

    private boolean inSession;

    /**
     * @return the inSession
     */
    @Override
    public boolean isInSession() {
        return inSession;
    }

    /**
     * @param inSession
     *            the inSession to set
     */
    @Override
    public void setInSession(boolean inSession) {
        this.inSession = inSession;
    }

    /**
     * @return the target
     */
    @Override
    public State getTarget() {
        return target;
    }

    /**
     * @param target
     *            the target to set
     */
    @Override
    public void setTarget(State target) {
        if (target != null)
            this.inSession = target.isInSession();
        else
            this.inSession = false;
        this.target = target;
    }

    /**
     * @return the origin
     */
    @Override
    public State getOrigin() {
        return origin;
    }

    /**
     * @param origin
     *            the origin to set
     */
    @Override
    public void setOrigin(State origin) {
        this.origin = origin;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractTransition that = (AbstractTransition) o;

        boolean equalOrigin;
        if (origin == null)
            equalOrigin = that.origin == null;
        else
            equalOrigin = origin.equals(that.origin);

        boolean equalTarget;
        if (target == null)
            equalTarget = that.target == null;
        else
            equalTarget = target.equals(that.target);

        return equalOrigin && equalTarget;
    }

    @Override
    public int hashCode() {
        int result;
        result = (target != null ? target.hashCode() : 0);
        result = 31 * result + (origin != null ? origin.hashCode() : 0);
        return result;
    }

    @Override
    public Transition clone() {
        try {
            return (Transition) super.clone();
        } catch (CloneNotSupportedException e) {
            // Never happens
            return null;
        }
    }
}
