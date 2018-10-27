/*
 * RandomDatasetReader.java
 *
 */

package sks.dataset;

import java.io.*;

public class RandomDatasetReader {
	private RandomAccessFile file;
	private RecordParser parser;	
	
	public RandomDatasetReader(Dataset dataset) throws IOException {
    file = new RandomAccessFile(dataset.getFile(), "r");
    parser = new RecordParser(dataset.getSchema());
	}

	public Record recordAt(long pos) throws IOException, MalformedRecordException {
    file.seek(pos);
    String line = file.readLine();
    return parser.parse(line, true, true);
	}

	public void close() throws IOException {
    file.close();
	}
}