/*
 * RecordParser.java
 *
 */

package sks.dataset;

import java.util.ArrayList;
import java.util.regex.Pattern;
import sks.Loader;

public class RecordParser {
  private int latFieldIndex;
  private int lonFieldIndex;
  private ArrayList<Integer> textFieldIndexes;
  private ArrayList<Integer> numericFieldIndexes;
  private int fieldCount;

  // Enhancement <11-JAN-2011> Date pattern:  "YYYY-MM-DD HH:MI:SS"
  static public final Pattern datePattern = Pattern.compile("[0-9]{4}\\-[0-9]{2}\\-[0-9]{2}( [0-9]{2}:[0-9]{2}(:[0-9]{2}){0,1}){0,1}");
  
  // 01-JUL-2008: NULL values are represented by the "_null" constant.
  static public String NULL_CONSTANT = "_null";

  public RecordParser(Schema schema) {
    latFieldIndex = schema.getLatitudeFieldIndex();
    lonFieldIndex = schema.getLongitudeFieldIndex();
    textFieldIndexes = schema.getTextFieldIndexes();
    numericFieldIndexes = schema.getNumberFieldIndexes();
    fieldCount = schema.getFieldCount();
  }

  /**
   * Truncates a date field into the following format:
   * "YYYY-MM-DD T" -> "RYYMMDD".
   * @param value
   * @return
   */
  static private String parseDateField(String value) {
    String truncatedDate = value.replaceAll("\\-", "");
    truncatedDate = truncatedDate.replaceAll("\\s{2,}", " "); // remove extra spaces.
    truncatedDate = truncatedDate.replaceAll(" .*", ""); // Remove time precision if any.

    if (truncatedDate.length() < 8) {
      // Not a valid date value.
      return value;
    }

    // Replace year as follows: "20xx" -> "2xx", "19xx" -> "1xx"

    String year = truncatedDate.substring(0, 4);
    if (year.compareTo("2000") >= 0) {
      truncatedDate = "2" + year.substring(2,4) + truncatedDate.substring(4);
    } else if (year.compareTo("2000") < 0) {
    //} else if (year.compareTo("2000") < 0) {    //yun 2012-06-04
      truncatedDate = "1" + year.substring(2,4) + truncatedDate.substring(4);
    }

    return truncatedDate;
  }

  /**
   *
   * @param value
   * @return
   */
  static public String cleanseNumericField(String value) {
    if (value != null) {
      value = value.trim();
      if (value.toLowerCase().equalsIgnoreCase(NULL_CONSTANT) || value.length() == 0) {
        // Empty, 0, NULL equivalence class.
        value = "0";
      }
      
      // Cleanse leading zeros.
      value = cleanseLeadingZeros(value);

      if (RecordParser.datePattern.matcher(value).matches()) {
        value = RecordParser.parseDateField(value);
      }

      // Remove non-numeric characters.
      value = value.replaceAll("[^0-9\\.\\-]+", "");

      return value;
    } else {
      return null;
    }
  } // static public String cleanseNumericField()

  /**
   * Casts numeric fields into values.
   * @param fields.
   * @return value array.
   */
  private double[] parseNumericFields(String[] fields) {
    int numFieldCount = numericFieldIndexes.size();
    
    if (numFieldCount > 0) {
      int numberOfZeros = 0;
      double[] values = new double[numFieldCount];

      for (int i = 0; i < numFieldCount; i++) {
        int index = numericFieldIndexes.get(i);

        // Read numeric value.
        String value = cleanseNumericField(fields[index]);

        try {
          // Truncate value to float precision.
          double dValue = Double.parseDouble(value);
          if (dValue != 0) {
            values[i] = dValue;
          } else { // Zeros are not indexed.
            numberOfZeros++; // DEBUG
          }
        } catch (NumberFormatException e) {
          // The numeric field could not be evaluated to a valid number.
          values[i] = Double.NaN;
        }
      } // for (int i = 0; i < numFieldCount; i++)

      return values;
    } else {
      return null;
    }
  } // private double[] parseNumericFields()
  
  /**
   * 26-JUL-2010: Filter leading zeros.
   * This is useful for numeric data intended to be queried with equality operator.
   * @param textField
   * @return
   */
  static private String cleanseLeadingZeros(String value) {
    if (value.length() > 0 ) {
      value = value.replaceAll("^[ +_]*0*", "");

      try {
        float fValue = Float.parseFloat(value);
        if (fValue == 0) {
          value = "0";
        }
      } catch (NumberFormatException e) {
        // Invalid number.
      }

      if (value.equals("")) {
        // The string was composed of only zeros.
        value = "0";
      }
    }

    return value;
  }

  /**
   * Returns a string that has been stripped down of not-allowed characters,
   * e.g. non-printable characters, symbols.
   * @param textField
   * @return
   */
  static public String cleanseTextField(String textField) {
    String cleansedTextField = null;

    if (textField.equalsIgnoreCase(NULL_CONSTANT)) {
      cleansedTextField = "";
    } else {
      // 27-MAY-2009: Filter non-printable characters.
      cleansedTextField = textField.replaceAll("[^ -~]]", " ");
      cleansedTextField = cleansedTextField.replaceAll("[!@#$%^&*()/_=`'-+,.:;\"\\-\\?~<>{}\\[\\]{}\\\\|]", " ");
      cleansedTextField = cleansedTextField.replaceAll("\\s{2,}", " ").trim().toLowerCase();

      if (cleansedTextField.matches(".*[0-9]+.*")) {
        String cleansedTerms = "";

        for (String term : cleansedTextField.split(" ", -1)) {
          String cleansedTerm = cleanseLeadingZeros(term);

          // Cleanse {st, nd, rd, th} suffixes in numbers.
          if (cleansedTerm.matches("[0-9]+(st|nd|rd|th){1}$")) {
            cleansedTerm = cleansedTerm.replaceAll("(st|nd|rd|th){1}$", "");
          }

          cleansedTerms += cleansedTerm + " ";
        }

        cleansedTextField = cleansedTerms.trim();
      }
    }
    
    return cleansedTextField;
  } // static public String cleanseTextField()
  
  /**
   * 
   * @param fields
   * @return
   */
  private String[] parseTextFields(String[] fields) {
    int textFieldCount = textFieldIndexes.size();

    if (textFieldCount > 0) {
      String[] textValues = new String[textFieldCount];

      for (int i = 0; i < textFieldCount; i++) {
        int index = textFieldIndexes.get(i);

        // Read text value.
        textValues[i] = cleanseTextField(fields[index]);
      }

      return textValues;
    } else {
      return null;
    }
  }

  /**
   *
   * @param line
   * @param parseTextFields
   * @param parseNumericFields
   * @return
   * @throws MalformedRecordException
   */
  public Record parse(String line
         ,boolean parseTextFields
         ,boolean parseNumericFields) throws MalformedRecordException {
    String fields[] = line.split(Loader.FIELD_SEPARATOR, -1);
    
    if (fields.length != fieldCount) {
      throw new MalformedRecordException("Fields missmatch: found " + fields.length + " fields, expected " + fieldCount + " fields");
    }

    try {
      double lat = Double.parseDouble(fields[latFieldIndex]);
      double lon = Double.parseDouble(fields[lonFieldIndex]);

      // Verify coordinates are within the expected range.
      if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
        throw new MalformedRecordException("Invalid coordinates: lat='" + fields[latFieldIndex] + "', lon='" + fields[lonFieldIndex] + "'");
      }

      return new Record(line, lat, lon,
              (parseTextFields? parseTextFields(fields) : null),
              (parseNumericFields? parseNumericFields(fields) : null));
    } catch (NumberFormatException ex) {
      throw new MalformedRecordException("Invalid coordinates: lat='" + fields[latFieldIndex] + "', lon='" + fields[lonFieldIndex] + "'");
    }
  } // public Record parse()
}