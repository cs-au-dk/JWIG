package dk.brics.jwig.analysis.graph;

public interface TransitionVisitor<T> {
    public T visit(HandlerTransition transition);

    public T visit(LambdaTransition transition);

    public T visit(WebMethodTransition transition);

    public T visit(AnyTransition transition);

    public T visit(FilterTransition transition);
}
