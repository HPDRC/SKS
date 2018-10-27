/*
 * Dataset.java
 *
 */

package sks.dataset;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

public class Dataset implements Serializable {
  static final long serialVersionUID = 3365168475461644636L;
	transient private File dataFile;
	transient private File headerFile;
	private Schema schema;
	
	public Dataset(File dataFile, File headerFile) throws IOException {
		this.dataFile = dataFile;
		this.headerFile = headerFile;
		schema = new Schema(headerFile);
	}
	
	public File getFile() {
		return dataFile;
	}
  
  // This set method is used while reading dataset from disk
  public void setDataFile(String dataFile) {
    this.dataFile = new File(dataFile);
  }
	
	public File getHeaderFile() {
		return headerFile;
	}

  // Tshis set method is used while reading dataset from disk
  public void setHeaderFile(String headerFile) {
    this.headerFile = new File(headerFile);
  }
	
	public Schema getSchema() {
		return schema;
	}
}