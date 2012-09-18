package dk.brics.jwig.analysis.jaive;

import java.util.List;

import dk.brics.jwig.analysis.graph.StateMachine;
import dk.brics.jwig.analysis.jaive.feedback.Feedback;

public class Result {

    private final StateMachine stateMachine;
    private final Interface interfacee;
    private final List<Feedback> feedbacks;

    public Result(StateMachine stateMachine, Interface interfacee,
            List<Feedback> feedbacks) {
        this.stateMachine = stateMachine;
        this.interfacee = interfacee;
        this.feedbacks = feedbacks;
    }

    /**
     * @return the stateMachine
     */
    public StateMachine getStateMachine() {
        return stateMachine;
    }

    /**
     * @return the interfacee
     */
    public Interface getInterfacee() {
        return interfacee;
    }

    /**
     * @return the feedbacks
     */
    public List<Feedback> getFeedbacks() {
        return feedbacks;
    }

}
