package dk.brics.jwig;

import dk.brics.jwig.server.ThreadContext;
import dk.brics.jwig.util.ParameterNamer;
import dk.brics.xact.XML;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * Handler for form submit requests.
 * <p>
 * The <code>run</code> method must return either an {@link XML} value 
 * (if the response is direct XML), a {@link URL} (if the response is 
 * a redirect to a GET request), or void, in which case the client is 
 * redirected to the current page. 
 */
@Regenerable
abstract public class SubmitHandler extends AbstractHandler {
	
	/**
	 * Constructs a new submit handler for the current page.
	 */
	public SubmitHandler(Object... dependencies) {
		super(dependencies);
	}

    public Object validate() {
        return ThreadContext.get().getRequestManager().invokeHandlerValidationMethod(this);
    }

    public List<String> validatedFormFields() {
        for (Method method: getClass().getDeclaredMethods()) {
            if (method.getName().equals("validate")) {
                return ParameterNamer.getParameterNames(method);
            }
        }
        return Collections.emptyList();
    }
}
