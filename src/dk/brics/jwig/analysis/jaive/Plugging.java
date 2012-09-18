package dk.brics.jwig.analysis.jaive;

import java.util.Set;

import soot.SootMethod;
import soot.Type;
import soot.jimple.AssignStmt;
import dk.brics.automaton.Automaton;
import dk.brics.jwig.analysis.graph.StateMachine.MethodStatementContainer;

public class Plugging {

    private final Set<Type> types;
    private final MethodStatementContainer container;
    private final Automaton names;

    public Plugging(AssignStmt statement, SootMethod method, Set<Type> types,
            Automaton automaton) {
        this.container = new MethodStatementContainer(method, statement);
        this.types = types;
        this.names = automaton;
    }

    /**
     * @return the names
     */
    public Automaton getNames() {
        return names;
    }

    /**
     * @return the container
     */
    public MethodStatementContainer getContainer() {
        return container;
    }

    /**
     * @return the types
     */
    public Set<Type> getTypes() {
        return types;
    }

    public AssignStmt getStmt() {
        return (AssignStmt) container.getStatement();
    }
}
