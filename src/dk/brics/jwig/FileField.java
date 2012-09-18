package dk.brics.jwig;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * File upload form field.
 */
public class FileField extends FormField {

	private final FileItem f;
	
	/**
	 * Constructs a new file upload field.
	 */
	public FileField(FileItem f) {
		this.f = f;
	}
	
    /**
     * Deletes the underlying storage for a file item, including deleting any associated temporary disk file.
     */ 
	public void delete() {
		f.delete();
    }

	/**
     * Returns the contents of the file item as an array of bytes. This will load the entire file to the memory.
     */ 
	public byte[] getBytes() {
    	return f.get();
    }

    /**
     * Returns the content type passed by the browser or null if not defined.
     */ 
	public String getContentType() {
		return f.getContentType();
    }

    /**
     * Returns an InputStream that can be used to retrieve the contents of the file.
     */ 
	public InputStream getInputStream() throws IOException {
		return f.getInputStream();
    }

    /**
     * Returns the original filename in the client's filesystem, as provided by the browser (or other client software).
     * This returns the filename or the full filename depending on the client's browser.
     */ 
	public String getFileName() {
		return f.getName();
    }

    /**
     * Returns the original filename in the client's filesystem. If the browser chooses to send the whole path
     * of the file, the string is normalized to only supply the actual filename.
     * This returns the same string no matter what browser the client uses.
     */
    public String getName() {
        return FilenameUtils.getName(f.getName());
    }

    /**
     * Returns the size of the file item.
     */ 
	public long getSize() {
		return f.getSize();
    }

    /**
     * Returns the contents of the file item as a String, using the specified encoding.
     */ 
	public String getString(String encoding) throws UnsupportedEncodingException {
		return f.getString(encoding);
    }

	/**
     * Returns the contents of the file item as a String, using the system default encoding.
     */ 
	public String getString() {
		return f.getString();
    }

	/**
     * Returns the contents of the file item as a String, using the system default encoding.
     */ 
	@Override
	public String getValue() {
		return f.getString();
    }

    /**
     * A convenience method to write an uploaded item to disk.
     */
	public void write(File file) throws Exception {
    	f.write(file);
    }
}
