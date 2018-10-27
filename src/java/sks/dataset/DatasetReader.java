package sks.dataset;

import java.io.*;
import sks.Loader;

public class DatasetReader {
  private Dataset dataset;
  private BufferedReader reader;
  private RecordParser parser;
  private boolean singleByteEOL;
  private long offset = 0;

	public DatasetReader(Dataset dataset) throws IOException {
		this.dataset = dataset;
		checkEOL();
		reader = new BufferedReader(new FileReader(dataset.getFile()), Loader.IO_BUFFER_SIZE);
		parser = new RecordParser(dataset.getSchema());
	}
	
	private void checkEOL() throws IOException {
		singleByteEOL = true;
		FileInputStream stream = new FileInputStream(dataset.getFile());
		int i;
		for (i = stream.read(); i != 0x0D && i != 0x0A; i = stream.read())
			;
		if (i == 0x0D) {
			if (stream.read() == 0x0A) {
				singleByteEOL = false;
      }
    }
		stream.close();
	}

	public Record readRecord(boolean parseTextFields, boolean parseNumericFields)
          throws IOException, MalformedRecordException {
		String line = reader.readLine();
		if (line == null) {
			return null;
    }

    long oldOffset = offset;
		offset += line.length() + (singleByteEOL ? 1 : 2);
    Record rec = parser.parse(line, parseTextFields, parseNumericFields);
		rec.setReference(oldOffset);
		return rec;
	}

	public void close() throws IOException {
		reader.close();
	}
}