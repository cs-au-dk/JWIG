package dk.brics.jwig.analysis.jaive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import dk.brics.xmlgraph.AttributeNode;
import dk.brics.xmlgraph.ChoiceNode;
import dk.brics.xmlgraph.ElementNode;
import dk.brics.xmlgraph.InterleaveNode;
import dk.brics.xmlgraph.MultiContentNode;
import dk.brics.xmlgraph.Node;
import dk.brics.xmlgraph.NodeProcessor;
import dk.brics.xmlgraph.OneOrMoreNode;
import dk.brics.xmlgraph.SequenceNode;
import dk.brics.xmlgraph.SingleContentNode;
import dk.brics.xmlgraph.TextNode;
import dk.brics.xmlgraph.XMLGraph;

/**
 * Decides if a given node is contained in a the action attribute of a form
 * element, this is done by returning the parent forms.
 */
public class IsInFormActionChecker extends NodeProcessor<Set<ElementNode>> {
    private final Map<Integer, Set<Node>> parentMap;
    private final Set<Node> visited;
    private final Set<ElementNode> emptySet;
    private boolean isInAction;

    public IsInFormActionChecker(XMLGraph xg) {
        ArrayList<Node> nodes = xg.getNodes();
        this.isInAction = false;
        this.visited = new HashSet<Node>();
        this.parentMap = new HashMap<Integer, Set<Node>>();
        emptySet = Collections.unmodifiableSet(new HashSet<ElementNode>());
        for (Node node : nodes) {
            if (node instanceof SingleContentNode) {
                addParent(((SingleContentNode) node).getContent(), node);
            }
            if (node instanceof MultiContentNode) {
                Collection<Integer> contents = ((MultiContentNode) node)
                        .getContents();
                for (Integer integer : contents) {
                    addParent(integer, node);
                }
            }
        }
    }

    private void addParent(int child, Node parent) {
        if (!parentMap.containsKey(child))
            parentMap.put(child, new HashSet<Node>());
        parentMap.get(child).add(parent);
    }

    @Override
    public Set<ElementNode> process(AttributeNode n) {
        boolean isAction = n.getName().run("action");
        if (isAction)
            return processUpwardsNode(n, true);
        return emptySet;
    }

    @Override
    public Set<ElementNode> process(ChoiceNode n) {
        return processUpwardsNode(n);
    }

    @Override
    public Set<ElementNode> process(ElementNode n) {
        if (isInAction) {
            boolean isForm = n.getName().run("form");
            if (isForm)
                return Collections.singleton(n);
        }
        return emptySet;
    }

    @Override
    public Set<ElementNode> process(InterleaveNode n) {
        return processUpwardsNode(n);
    }

    @Override
    public Set<ElementNode> process(OneOrMoreNode n) {
        return processUpwardsNode(n);
    }

    @Override
    public Set<ElementNode> process(SequenceNode n) {
        return processUpwardsNode(n);
    }

    @Override
    public Set<ElementNode> process(TextNode n) {
        throw new IllegalArgumentException(
                "TextNode can not be a parent of a gap");
    }

    public Set<ElementNode> processUpwardsNode(Node node) {
        return processUpwardsNode(node, this.isInAction);
    }

    public Set<ElementNode> processUpwardsNode(Node node, boolean isInAction) {
        Set<Node> parents = parentMap.get(node.getIndex());
        Set<ElementNode> results = new HashSet<ElementNode>();
        for (Node parent : parents) {
            if (!visited.contains(parent)) {
                visited.add(parent);
                this.isInAction = isInAction;
                results.addAll(parent.process(this));
                this.isInAction = isInAction;
            }
        }
        return results;
    }
}
