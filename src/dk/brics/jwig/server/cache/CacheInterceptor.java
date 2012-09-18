package dk.brics.jwig.server.cache;

import dk.brics.jwig.persistence.Persistable;
import dk.brics.jwig.server.ThreadContext;
import org.hibernate.EmptyInterceptor;

import java.util.Iterator;

/**
 * An interceptor that invalidates all cache entries that depend on the object that is saved.
 */
public class CacheInterceptor extends EmptyInterceptor {

    @Override
	public void postFlush(@SuppressWarnings("rawtypes") Iterator iterator) {
        while (iterator.hasNext()) {
		    Persistable persistable = (Persistable) iterator.next();
		    ThreadContext.getDependencyMap().objectUpdated(persistable);
		}
    }

    /*@Override //XXX:Only on explicit PM load
	public boolean onLoad(Object o, Serializable serializable, Object[] objects, String[] strings, Type[] types) {
        assert o instanceof Persistable;
        Persistable p = (Persistable) o;
        ThreadContext.getDependencyMap().addResponseDependency(p);
        return false;
    } */
}

