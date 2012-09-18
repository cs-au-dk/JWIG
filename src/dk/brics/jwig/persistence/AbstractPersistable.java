package dk.brics.jwig.persistence;

import dk.brics.jwig.server.RequestManager;
import dk.brics.xact.Text;
import dk.brics.xact.ToXMLable;
import dk.brics.xact.XML;

/**
 * Implements the Persistable interface so an integer field can hold the ID of the object.
 * Hibernate will set the ID. It can be retrieved when the object is persisted.
 */
public abstract class AbstractPersistable implements Persistable, ToXMLable {
    private Integer id;

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
	public Integer getId() {
        return id;
    }

    @Override
	public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null) {
            return false;
        }

        if (getClass() != o.getClass()) {
            if (!getClass().isAssignableFrom(o.getClass()) && !o.getClass().isAssignableFrom(getClass())) {
                return false;
            }
        }

        AbstractPersistable that = (AbstractPersistable) o;

        if (getId() == null && that.getId() == null) {
            return this == that;
        }

        if (getId() != null ? !getId().equals(that.getId()) : that.getId() != null) {
            return false;
        }

        return true;
    }

    @Override
	public int hashCode() {
        return getClass().hashCode() + 31 * (id != null ? id.hashCode() : 0);
    }

    @Override
    public final XML toXML() {
        return new Text(RequestManager.makeURLArg(this));
    }
}
