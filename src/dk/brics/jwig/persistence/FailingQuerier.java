package dk.brics.jwig.persistence;

import org.apache.log4j.Logger;

import java.util.Properties;

/**
 * A querier that will respond to all queries with an exception. Is used as a placeholder where no querier exists.
 */
public class FailingQuerier implements Querier {
    private static Logger log = Logger.getLogger(FailingQuerier.class);
    private static FailingQuerier instance = new FailingQuerier();

    @Override
	public <E extends Persistable> E getObject(Class<E> aClass, Integer id) throws NoSuchObjectException {
        log.warn("Querying objects on place holder querier");
        throw new NoSuchObjectException(id, aClass);
    }

    @Override
	public Integer getIdFromProperty(Class<? extends Persistable> clazz, String property, String value) {
        log.warn("Querying objects on place holder querier");
        throw new NoSuchObjectException(0, clazz);
    }

    @Override
	public String getPropertyFromId(Class<? extends Persistable> clazz, String property, Integer id) {
        log.warn("Querying objects on place holder querier");
        throw new NoSuchObjectException(id, clazz);
    }

    @Override
	@SuppressWarnings("unchecked")
	public <E extends Persistable> Class<E> getClass(E p) {
        return (Class<E>) p.getClass();
    }

    /**
     * Gets the instance of the place holder querier. It is possible to create more instances, but there is little point in
     * doing it, so for performance we can reuse it
     */
    public static FailingQuerier getInstance() {
        return instance;
    }

    @Override
	public void close() {

    }

    @Override
	public void postInit() {

    }

    @Override
	public void preInit(Properties properties) {

    }

    @Override
	public Class<? extends Persistable> getBaseType(Persistable p) {
        return p.getClass();
    }
}
