package dk.brics.jwig.server.cache;

/**
 *
 */
public class CacheObject { // FIXME: local class DependencyBlaBla

	private String url;
    
	private String name;

    public CacheObject(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
	public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CacheObject that = (CacheObject) o;

        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (url != null ? !url.equals(that.url) : that.url != null) {
            return false;
        }

        return true;
    }

    @Override
	public int hashCode() {
        int result;
        result = (url != null ? url.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CacheObject{" +
               "url='" + url + '\'' +
               '}';
    }
}
