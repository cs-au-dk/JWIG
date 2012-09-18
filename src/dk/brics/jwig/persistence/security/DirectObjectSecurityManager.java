package dk.brics.jwig.persistence.security;

import dk.brics.jwig.persistence.Persistable;
import dk.brics.jwig.server.DispatchListener;

/**
 * The direct object security manager is responsible for limiting the
 * access to data referenced by and ID in URL's or forms.
 * An URL could look something like http://www.example.com/secretdata?user=42.
 * The user can easily change user=42 to user=43 hereby getting access to
 * secret data. This manager is responsible for handling the model that
 * gives access to data.
 */
public interface DirectObjectSecurityManager extends DispatchListener {
    /**
     * Returns true if the object is accessible
     */
    public boolean hasAccess(Persistable persistable);
}
