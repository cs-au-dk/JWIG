package dk.brics.jwig.analysis.siteMap;

import soot.SootMethod;
import dk.brics.jwig.analysis.graph.StateMachine;

public class SiteMapper {
    static Page makePage(SootMethod root) {
        return new Page(makePageName(root));
    }

    static String makePageName(SootMethod root) {
        return root.getDeclaringClass().getShortJavaStyleName() + "."
                + root.getName();
    }

    private final SiteMap fullSiteMap;

    public SiteMapper(StateMachine stateMachine) {
        this.fullSiteMap = new FullSiteMapConstructor(stateMachine).construct();
    }

    public SiteMap getBreadthFirstSiteMap(SootMethod root) {
        return new BreadthFirstSiteMapping(
                fullSiteMap.getPageByName(makePageName(root)), fullSiteMap)
                .construct();
    }

    public SiteMap getFullSiteMap() {
        return fullSiteMap;
    }

    public SiteMap getNegativelyAnnotatedSiteMap() {
        return fullSiteMap;
    }

    public SiteMap getPositivelyAnnotatedSiteMap() {
        return fullSiteMap;
    }

    public SiteMap getRankedSiteMap() {
        return new RankedSiteMapConstructor(fullSiteMap).construct();
    }

}
