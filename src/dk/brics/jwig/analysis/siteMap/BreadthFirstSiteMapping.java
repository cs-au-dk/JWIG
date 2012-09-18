package dk.brics.jwig.analysis.siteMap;

import java.util.HashSet;
import java.util.Set;

public class BreadthFirstSiteMapping implements SiteMapConstructor {

    private final Page root;
    private final SiteMap full;

    public BreadthFirstSiteMapping(Page root, SiteMap full) {
        this.root = root;
        this.full = full;
    }

    @Override
    public SiteMap construct() {
        SiteMap bf = new SiteMap();
        Set<Page> currentDistance = new HashSet<Page>();
        Set<Page> nextDistance = new HashSet<Page>();
        currentDistance.add(root);
        bf.addPage(root);
        while (!currentDistance.isEmpty() || !nextDistance.isEmpty()) {
            if (currentDistance.isEmpty()) {
                currentDistance.addAll(nextDistance);
                nextDistance.clear();
            }
            for (Page current : currentDistance) {
                Set<Page> linksTo = full.getLinksFrom(current);
                for (Page next : linksTo) {
                    if (!bf.containsPage(next)) {
                        bf.addPage(next);
                        bf.addLink(current, next, null);
                        nextDistance.add(next);
                    }
                }
            }
            currentDistance.clear();
        }

        return bf;
    }

}
