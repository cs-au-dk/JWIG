package dk.brics.jwig;

import dk.brics.jwig.persistence.Persistable;
import dk.brics.jwig.server.ThreadContext;
import dk.brics.jwig.server.cache.ProxyObject;
import dk.brics.jwig.util.Base64;

/**
 * Base class for handlers. Handlers are in general regnerable
 */
@Regenerable
public abstract class AbstractHandler extends WebContext {

    private String name;
    
    private int hashcode; // TODO: really need this hashcode? (now used in EventHandler)

    /**
     * Constructs a handler and registers it at the current response object.
     * The URL depends on the handler class name and string values of its dependencies as given.
     * This means that the handler URL can be resilient
     * to regeneration of the handler object (which happens if accessed by a client and the response is not in the cache).
     */
    AbstractHandler(Object... dependencies) {
        IntHasher hasher = new IntHasher();
        hash(dependencies, hasher);
        hashcode = hasher.getValue();
        BufHasher buf = new BufHasher();
        hash(dependencies, buf);
        name = makeURL(ThreadContext.get().getWebAppParams(), "handlers") + Base64.encodeString(getClass().getName()) + buf.getValue();
        ThreadContext.get().getResponse().setHandler(name, this);
    }

    /**
     * Returns the URL for running this handler
     */
    public String getHandlerIdentifier() {
       return name;
    }

    /**
     * Computes a hash value for this handler object using the given hash method.
     * Persistable objects are wrapped, so if they are of the same class and have 
     * the same ID they will always have the same hash value.
     */
    private void hash(Object[] dependencies, HashMethod<?> h) {
        for (Object o : dependencies) {
            if (o instanceof Persistable) {
                Persistable persistable = (Persistable) o;
                o = new ProxyObject(getWebSite().getQuerier(),persistable);
            } else if (o instanceof Enum) {
                Enum<?> anEnum = (Enum<?>) o;
                o = anEnum.getClass().getName().hashCode() * anEnum.ordinal();
            }
            h.addHashParameter("-" + (o == null ? 42 : o.hashCode()));
        }
    }

    @Override
	public int hashCode() {
        return hashcode;
    }

    @Override
	public String toString() {
        return getHandlerIdentifier();
    }

    /**
     * Invokes the <code>run</code> method of the handler.
     */
    Object process(String referer) {
        return ThreadContext.get().getRequestManager().invokeHandlerMethod(this, referer);
    }

    /**
     * Invoked when the current response is invalidated.
     * Default implementation does nothing.
     */
    public void destroy() {
        // do nothing
    }
    
    /**
     * Generic hasher for object sequences.
     */
    private interface HashMethod<E> {

    	public void addHashParameter(Object o);

        public E getValue();
    }

    /**
     * Hasher that concatenates the string values of the objects.
     */
    private class BufHasher implements HashMethod<String> {
    	
        private StringBuffer buf = new StringBuffer();

        @Override
		public void addHashParameter(Object o) {
            buf.append(o);
        }

        @Override
		public String getValue() {
            return buf.toString();
        }
    }

    /**
     * Hasher that computes an integer hash value the object sequence.
     */
    private class IntHasher implements HashMethod<Integer> {

    	private Integer i = 0;

        @Override
		public void addHashParameter(Object o) {
            i += o.hashCode();
        }

        @Override
		public Integer getValue() {
            return i;
        }
    }
}
