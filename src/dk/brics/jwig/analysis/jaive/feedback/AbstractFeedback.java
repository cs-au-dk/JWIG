package dk.brics.jwig.analysis.jaive.feedback;

public abstract class AbstractFeedback implements Feedback {
    protected String message;
    protected FeedbackType type;

    /**
     * @return the type
     */
    @Override
    public FeedbackType getType() {
        return type;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AbstractFeedback other = (AbstractFeedback) obj;
        if (message == null) {
            if (other.message != null)
                return false;
        } else if (!message.equals(other.message))
            return false;
        return true;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public AbstractFeedback() {
        // default ERROR
        type = FeedbackType.ERROR;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((message == null) ? 0 : message.hashCode());
        return result;
    }
}
