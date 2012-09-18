package dk.brics.jwig.analysis.siteMap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class RankedSiteMapConstructor implements SiteMapConstructor {

    private final SiteMap fullSiteMap;

    public RankedSiteMapConstructor(SiteMap fullSiteMap) {
        this.fullSiteMap = fullSiteMap;
    }

    @Override
    public SiteMap construct() {
        Map<Page, Set<Page>> reverseSiteMap = makeReverseSiteMap(fullSiteMap);
        SiteMap ranked = new SiteMap();
        for (Page page : fullSiteMap.getPages()) {
            ranked.addPage(page);
        }
        for (Entry<Page, Set<Page>> entry : reverseSiteMap.entrySet()) {
            Page highestRanked = getHighestRanked(entry.getValue(),
                    reverseSiteMap);
            if (highestRanked != null) {
                ranked.addLink(highestRanked, entry.getKey(), null);
            }
        }
        return ranked;
    }

    /**
     * Selects the highest ranked page among a set of pages.
     * 
     * The rank of a page is determined by the amount of pages pointing to it in
     * the reverse site map.
     * 
     * @param pages
     *            as the pages to select from
     * @param reverseSiteMap
     *            as the site map to find ranks from
     * @return the highest ranked page
     */
    private Page getHighestRanked(Set<Page> pages,
            Map<Page, Set<Page>> reverseSiteMap) {
        int max = -1;
        Page maxPage = null;
        for (Page page : pages) {
            int size = reverseSiteMap.get(page).size();
            if (size > max) {
                max = size;
                maxPage = page;
            }
        }
        return maxPage;
    }

    /**
     * Makes a reverse site map, the map represents which pages that point to a
     * specific page.
     * 
     * @param siteMap
     *            as the full SiteMap to analyse
     * @return a reverse site map
     */
    private Map<Page, Set<Page>> makeReverseSiteMap(SiteMap siteMap) {
        Set<Page> pages = siteMap.getPages();
        Map<Page, Set<Page>> reverse = new HashMap<Page, Set<Page>>();
        for (Page page : pages) {
            reverse.put(page, new HashSet<Page>());
        }
        for (Page page : pages) {
            Set<Page> linksFrom = siteMap.getLinksFrom(page);
            for (Page linkedTo : linksFrom) {
                reverse.get(linkedTo).add(page);
            }
        }
        return reverse;
    }
}
