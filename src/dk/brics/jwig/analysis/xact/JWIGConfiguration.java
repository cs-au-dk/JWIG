package dk.brics.jwig.analysis.xact;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.Datatypes;
import dk.brics.xact.analysis.config.StandardConfiguration;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Configues XACT to analyze JWIG classes.
 * 
 * @author Asger
 */
public class JWIGConfiguration extends StandardConfiguration {

	private boolean isWebApp(SootClass clazz) {
		if (clazz.isInterface())
			return false;
		return Scene.v().getActiveHierarchy().isClassSubclassOf(clazz, Scene.v().getSootClass("dk.brics.jwig.WebApp"));
		/*
		SootClass superclass = clazz.getSuperclass();
		if (superclass == null)
			return false;
		return superclass.getName().equals("dk.brics.jwig.WebApp");*/
	}
	
	@Override
	public String getMethodReturnType(SootMethod method, String annotation) {
		if (method.isPublic() && isWebApp(method.getDeclaringClass())) {
			if (annotation != null)
				throw new RuntimeException("Cannot annotate return type of " + method.getName()
						+ ". HTML type is implicitly returned");
			return "{http://www.w3.org/1999/xhtml}html";
		}
		return super.getMethodReturnType(method, annotation);
	}
	
	@Override
	public boolean isExternallyCallable(SootMethod method) {
		if (method.isPublic() && isWebApp(method.getDeclaringClass())) {
			return true;
		}
		return super.isExternallyCallable(method);
	}
	
	@Override
	public void modifyNamespaces(Map<String, String> namespaces) {
		if (!namespaces.containsKey("")) {
			namespaces.put("", "http://www.w3.org/1999/xhtml");
		}
	}
	
	@Override
	public Collection<URL> getAdditionalSchemas() {
		try {
			return Collections.singletonList(new URL("file:schemas/xhtml1-transitional.dtd"));
		} catch (MalformedURLException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	@Override
	public Automaton resolveToString(SootClass clazz) {
		// this stuff is not really specific to JWIG, but JSA does not know URL and URI
		if (clazz.getName().equals("java.net.URL") || clazz.getName().equals("java.net.URI")) {
			return Datatypes.get("URI");
		}
		return super.resolveToString(clazz);
	}
}
