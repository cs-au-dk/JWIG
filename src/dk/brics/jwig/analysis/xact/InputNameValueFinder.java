package dk.brics.jwig.analysis.xact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dk.brics.automaton.Automaton;
import dk.brics.xmlgraph.AttributeNode;
import dk.brics.xmlgraph.ElementNode;
import dk.brics.xmlgraph.MultiContentNode;
import dk.brics.xmlgraph.NoContentNode;
import dk.brics.xmlgraph.NodeProcessor;
import dk.brics.xmlgraph.SingleContentNode;
import dk.brics.xmlgraph.TextNode;
import dk.brics.xmlgraph.XMLGraph;

/**
 * Depth first search in an {@link XMLGraph} for the attribute values of the
 * name of input tags.
 */
public class InputNameValueFinder extends NodeProcessor<List<Automaton>> {
    private boolean inInput;
    private boolean inName;
    private final XMLGraph g;
    private final Set<Integer> visited;

    public InputNameValueFinder(XMLGraph g) {
        this.inInput = false;
        this.inName = false;
        this.g = g;
        this.visited = new HashSet<Integer>();
    }

    @Override
    public List<Automaton> process(AttributeNode n) {
        final boolean isInputName = inInput && n.getName().run("name");
        if (isInputName)
            inName();
        final List<Automaton> processChild = processChild(n);
        if (isInputName)
            outName();
        return processChild;
    }

    @Override
    public List<Automaton> process(ElementNode n) {
        final boolean isInput = n.getName().run("input");
        if (isInput)
            inInput();
        final List<Automaton> processChild = processChild(n);
        if (isInput)
            outInput();
        return processChild;
    }

    @Override
    public List<Automaton> process(MultiContentNode n) {
        List<Automaton> results = new ArrayList<Automaton>();
        for (Integer childIndex : n.getContents()) {
            results.addAll(processNodeNonCyclic(childIndex));
        }
        return results;
    }

    private List<Automaton> processNodeNonCyclic(Integer childIndex) {
        if (visited.contains(childIndex))
            return new ArrayList<Automaton>();
        visited.add(childIndex);
        return g.getNode(childIndex).process(this);
    }

    @Override
    public List<Automaton> process(SingleContentNode n) {
        return processChild(n);
    }

    @Override
    public List<Automaton> process(NoContentNode n) {
        return new ArrayList<Automaton>();
    }

    @Override
    public List<Automaton> process(TextNode n) {
        if (inInput && inName)
            return Collections.singletonList(n.getText());
        return new ArrayList<Automaton>();
    }

    private List<Automaton> processChild(SingleContentNode n) {
        return processNodeNonCyclic(n.getContent());
    }

    private void outName() {
        if (inName == true)
            inName = false;
        else
            throw new IllegalArgumentException("Expected inName to be true");

    }

    private void inName() {
        if (inName == false)
            inName = true;
        else
            throw new IllegalArgumentException("Expected inName to be false");
    }

    private void outInput() {
        if (inInput == true)
            inInput = false;
        else
            throw new IllegalArgumentException("Expected inInput to be true");

    }

    private void inInput() {
        if (inInput == false)
            inInput = true;
        else
            throw new IllegalArgumentException("Expected inInput to be false");
    }

}