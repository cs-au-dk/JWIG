package dk.brics.jwig.sitemap;

import dk.brics.jwig.WebApp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A parent web method is a web method that is logically above this annotated web methods
 * in the web application page hierarchy.
 * The arguments of this annotation is used to generate a URL using {@link WebApp#makeURL} such
 * that a parameter <tt>p</tt> of the annotated web method can be given as parameter to the
 * parent web app by
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ParentWebMethod {
    /**
     * The web application to which the parent web method belongs.
     */
    Class<? extends WebApp> webApp();

    /**
     * The name of the web method that represents the parent of the annotated web application.
     */
    String methodName();

    /**
     * The mapping of parameters from the annotated web method to the parent web method.
     * If the {"p"} is given as parameters array the value of the parameter <tt>p</tt>
     * is given as the argument to the makeURL method when generating the URL for the parent
     * web app.
     */
    String[] parameters() default {};
}
