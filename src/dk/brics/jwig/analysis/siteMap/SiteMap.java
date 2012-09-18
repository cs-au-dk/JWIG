package dk.brics.jwig.analysis.siteMap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import soot.jimple.InvokeExpr;

public class SiteMap {

    private final Map<String, Page> pages;
    private final Map<Page, Set<Page>> links;

    public SiteMap() {
        pages = new HashMap<String, Page>();
        links = new HashMap<Page, Set<Page>>();
    }

    public void addPage(Page page) {
        if (!pages.containsKey(page)) {
            pages.put(page.getName(), page);
            links.put(page, new HashSet<Page>());
        } else {
            throw new IllegalArgumentException("Page exists already: " + page);
        }
    }

    public boolean containsPage(Page page) {
        return pages.containsValue(page);
    }

    public void addLink(Page from, Page to, InvokeExpr link) {
        // TODO use the link for something
        if (pages.containsValue(from) && pages.containsValue(to)) {
            links.get(from).add(to);
        } else {
            String message;
            // find the error
            if (!pages.containsValue(to))
                message = "'to' is unknown";
            else
                message = "'from' is unknown";
            throw new IllegalArgumentException(
                    "Both pages must be present before a link can be made between them. "
                            + from.toString() + "--->" + to.toString() + "."
                            + message);
        }
    }

    public String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        // naming
        Map<Page, String> names = new HashMap<Page, String>();
        int i = 0;
        for (Page page : pages.values()) {
            final String name = "N" + i++;
            names.put(page, name);
        }
        sb.append("digraph{\n");
        sb.append("rankdir=LR;\n");
        // draw nodes
        for (Entry<Page, String> entry : names.entrySet()) {
            final Page page = entry.getKey();
            sb.append(entry.getValue() + "[label=\"" + page.getName()
                    + "\"];\n");
        }

        // draw transitions
        for (Entry<Page, Set<Page>> link : links.entrySet()) {
            final String fromName = names.get(link.getKey());
            for (Page page : link.getValue()) {
                sb.append(fromName + "->" + names.get(page) + ";\n");
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    public Set<Page> getLinksFrom(Page root) {
        return links.get(root);
    }

    public Page getPageByName(String name) {
        return pages.get(name);
    }

    public Set<Page> getPages() {
        return new HashSet<Page>(pages.values());
    }
}
