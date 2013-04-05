package dk.brics.jwig.persistence.security;

import dk.brics.jwig.AccessDeniedException;
import dk.brics.jwig.DispatchAdapter;
import dk.brics.jwig.JWIGException;
import dk.brics.jwig.WebApp;
import dk.brics.jwig.persistence.Persistable;
import dk.brics.jwig.persistence.Querier;
import dk.brics.jwig.server.DispatchListener;
import dk.brics.jwig.server.ThreadDispatchEvent;
import dk.brics.jwig.server.WebMethodDispatchEvent;
import dk.brics.jwig.server.cache.ProxyObject;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * See {@link DirectObjectSecurityManager}
 */
public class DirectObjectSecurityManagerImpl extends DispatchAdapter implements DirectObjectSecurityManager {
    private final WebApp webapp;
    private final Querier querier;
    private final ThreadLocal<Map<ProxyObject, Boolean>> persistenceCache = new ThreadLocal<Map<ProxyObject, Boolean>>();

    public DirectObjectSecurityManagerImpl(WebApp webapp, Querier querier) {
        this.webapp = webapp;
        this.querier = querier;
    }

    @Override
	public boolean hasAccess(Persistable persistable) { //FIXME: Should it be safe to assume that we can cache the result?
        ProxyObject proxyObject = new ProxyObject(webapp.getWebSite().getQuerier(), persistable);
        Map<ProxyObject, Boolean> objectBooleanMap = persistenceCache.get();
        if (objectBooleanMap.containsKey(proxyObject)) {
            return objectBooleanMap.get(proxyObject);
        }
        boolean b = hasAccess(persistable, 0);
        objectBooleanMap.put(proxyObject, b);
        return b;
    }

    public boolean hasAccess(Persistable persistable, int depth) {
        if (depth == 100) {
            throw new AccessDeniedException("Failed to prove access in depth 100");
        }
        if (persistable == null) {
            return false;
        }
        //First check the value returned by the access method
        boolean accessMethodResult = true;
        boolean hasAccessMethod = false;
        try {
            Method m = webapp.getClass().getMethod("access", querier.getClass(persistable));
            Class<?> returnType = m.getReturnType();
            if (returnType == boolean.class || returnType == Boolean.class) {
                Object o = m.invoke(webapp, persistable);
                if (o instanceof Boolean) {
                    accessMethodResult = (Boolean) o;
                    hasAccessMethod = true;
                }
            }
        } catch (NoSuchMethodException e) {
            //
        } catch (Exception e) {
            throw new JWIGException(e);
        }
        if (!accessMethodResult) {
            return false;
        }
        //If the access method returns true, make sure the constraints from the @AccessScope annotations are also satisfied
        boolean accessScopeAnnotationResult = true;
        boolean hasAccessScopeAnnotation = false;
        for (Method m : querier.getClass(persistable).getMethods()) {
            if (m.getAnnotation(AccessScope.class) != null) {
                if (m.getParameterTypes().length == 0 && Persistable.class.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    hasAccessScopeAnnotation = true;
                    try {
                        accessScopeAnnotationResult &= hasAccess((Persistable) m.invoke(persistable), depth + 1);
                    } catch (Exception e) {
                        throw new JWIGException(e);
                    }
                }
            }
        }
        if (!hasAccessScopeAnnotation && !hasAccessMethod) return false;
        if (!hasAccessScopeAnnotation && hasAccessMethod) { //We know that the access method returned true
            return true;
        } else
            return accessScopeAnnotationResult;
    }

    @Override
	public void threadDispatched(ThreadDispatchEvent t) {
        persistenceCache.set(new HashMap<ProxyObject, Boolean>());
    }

    @Override
	public void threadDismissed(ThreadDispatchEvent t) {
        persistenceCache.set(null);
    }
}
