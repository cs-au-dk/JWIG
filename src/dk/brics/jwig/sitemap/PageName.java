package dk.brics.jwig.sitemap;

/**
 * A page name is used as the name of the web application page in the site map when presented to
 * the user.
 * The value of the annotation is used as argument in a call to
 * {@link dk.brics.jwig.WebApp#getPageName}
 */
public @interface PageName {
    String value();
}
