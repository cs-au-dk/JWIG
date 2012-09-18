package dk.brics.jwig.persistence;

/**
 * A Persistable is an object that can be persisted. Only requirement is that it
 * must contain an ID.
 */
public interface Persistable {
    /**
     * Retrieves the ID for this object. The ID must be unique for the object
     * within the class of this object and it will correspond to a row ID in the database
     * for at typical use of the persistence system.
     */
    public Integer getId();

}
