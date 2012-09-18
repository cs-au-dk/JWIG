package dk.brics.jwig;

import dk.brics.jwig.persistence.AbstractPersistable;

/**
 * Username and password for a user.
 */
public class User extends AbstractPersistable {

    private String username;

    private String password;

    /**
     * Constructs a new user object.
     */
    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Gets the username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Checks whether this user is equal to the given one.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj.getClass().equals(obj.getClass())) {
            User u = (User) obj;
            return username.equals(u.username) && password.equals(u.password);
        }
        return false;
    }

    /**
     * Computes the hash code for this object.
     */
    @Override
    public int hashCode() {
        return username.hashCode() * 2 + password.hashCode() * 3;
    }

    /**
     * Returns a string describing this user information.
     */
    @Override
    public String toString() {
        return username + ":" + password;
    }
}
