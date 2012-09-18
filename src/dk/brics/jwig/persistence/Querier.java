package dk.brics.jwig.persistence;

import java.util.Properties;

/**
 * The object querier is used by JWIG to query the object with a given ID given as argument in a request.
 * Any site that want to use this feature should implement this interface.
 */
public interface Querier {
    /**
     * Returns the instance of the given class uniquely determined by its ID.
     *
     * @param aClass the class of the object
     * @param id     the ID of the object
     * @return the instance
     */
    public <E extends Persistable> E getObject(Class<E> aClass, Integer id) throws PersistenceException;

    public Integer getIdFromProperty(Class<? extends Persistable> clazz, String property, String value);

    public String getPropertyFromId(Class<? extends Persistable> clazz, String property, Integer id);

    /**
     * Returns the class object of a persistable.
     */
    public <E extends Persistable> Class<E> getClass(E p);

    void close();

    /**
     * Called during initialization of the system. This method is called <b>before</b> the web site is initialized.
     *
     * @param properties
     * @see #postInit()
     */
    void preInit(Properties properties);

    /**
     * Called during initialization of the system. This method is called <b>after</b> the web site is initialized.
     *
     * @see #preInit(java.util.Properties)
     */
    void postInit();

    /**
     * If the querier implementation uses an instrumented class for representing a persistable type, then the
     * non-instrumented class of the object must be returned by this method. Otherwise it should just return the class of the object.
     * @param p
     */
    Class<? extends Persistable> getBaseType(Persistable p);
}
