package dk.brics.jwig.analysis.graph;

public interface StateVisitor<T> {
    public T visit(WebMethodState state);

    public T visit(FilterState state);

    public T visit(HandlerState state);

    public T visit(RegularMethodState state);

    public T visit(State state);
}
