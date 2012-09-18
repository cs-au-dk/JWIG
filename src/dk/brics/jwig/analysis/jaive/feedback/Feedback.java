package dk.brics.jwig.analysis.jaive.feedback;

public interface Feedback {
    public enum FeedbackType {
        ERROR, WARNING
    }

    public String getMessage();

    public FeedbackType getType();
}
