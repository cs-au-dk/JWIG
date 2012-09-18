package dk.brics.jwig.analysis.jaive;

import java.util.HashSet;
import java.util.Set;

import dk.brics.automaton.Automaton;
import dk.brics.xmlgraph.ChoiceNode;
import dk.brics.xmlgraph.NodeProcessor;

/**
 * Gathers all gaps with a specific name.
 */
public class GapFinderByName extends NodeProcessor<Object> {

    private final Automaton names;
    private final Set<ChoiceNode> gaps;

    public GapFinderByName(Automaton names) {
        this.names = names;

        this.gaps = new HashSet<ChoiceNode>();
    }

    @Override
    public Object process(ChoiceNode node) {
        if (node.isGap()) {
            if (names.run(node.getName()))
                gaps.add(node);
        }
        return null;
    }

    public Set<ChoiceNode> getGaps() {
        return gaps;
    }

}
