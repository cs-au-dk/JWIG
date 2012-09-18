package dk.brics.jwig.persistence;

/**
 * An exception indicating that the object that was queried was not found. This happens
 * when the row with the given ID for the object does not exist in the database.
 */
public class NoSuchObjectException extends PersistenceException {
    public NoSuchObjectException(Integer id, Class<? extends Persistable> clazz) {
        super("No object of " + clazz + ((id==null || id == 0)?"":" exists with id " + id));
    }

}
