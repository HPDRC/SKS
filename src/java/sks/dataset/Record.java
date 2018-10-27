/*
 * Record.java
 *
 */

package sks.dataset;

public class Record {
	private String data;
	private long ref = -1;
	private double lat;
	private double lon;
  private String[] textValues;
  private double[] numericValues;
	
	public Record(String data, double lat, double lon,
          String[] textValues, double[] numericValues) {
		this.data = data;
		this.lat = lat;
		this.lon = lon;
    this.textValues = textValues;
    this.numericValues = numericValues;
	}
	
	public String getData() {
    return data;
	}

	public String[] getTextValues() {
    return textValues;
	}

	public double[] getNumericValues() {
    return numericValues;
	}

	public double getLatitude() {
    return lat;
	}
	
	public double getLongitude() {
    return lon;
	}
	
	public long getReference() {
		return ref;
	}

	public void setReference(long ref) {
		this.ref = ref;
	}
}