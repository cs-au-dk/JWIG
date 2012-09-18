package dk.brics.jwig.server.cache;

import dk.brics.jwig.persistence.Persistable;
import dk.brics.jwig.persistence.Querier;

/**
 * A proxy object is a light weight version of a persistable object. It contains only the class of the object
 * and the id of that object.
 */
public class ProxyObject {
    public int id;
    public Class<? extends Persistable> type;

    public ProxyObject(Querier q, Persistable persistable) {
        Integer id = persistable.getId();
        assert id != null;
        this.id = id;
        type = q.getBaseType(persistable);
    }

    @Override
	public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ProxyObject that = (ProxyObject) o;

        if (id != that.id) {
            return false;
        }
        if (type != null ? !type.equals(that.type) : that.type != null) {
            return false;
        }

        return true;
    }

    @Override
	public int hashCode() {
        int result;
        result = id;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    @Override
	public String toString() {
        return type.getCanonicalName() + "#" + id;
    }

}
