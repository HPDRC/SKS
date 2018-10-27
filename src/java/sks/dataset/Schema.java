/*
 * Schema.java
 *
 */

package sks.dataset;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.Serializable;
import sks.Loader;

public class Schema implements Serializable {
  private int fieldCount = 0;
  private int latFieldIndex;
  private int lonFieldIndex;
  private ArrayList<Integer> textFieldIndexes;
  private ArrayList<Integer> numberFieldIndexes;
  private HashMap<String, Integer> fieldIndexMap = new HashMap<String, Integer>();

  public Schema(File file) throws IOException {
    parseHeader(file);
  }

  private void parseHeader(File file) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(file));
    String line = reader.readLine();
    while (!line.startsWith("FIELD DEFINITIONS")) {
      line = reader.readLine();
    }

    textFieldIndexes = new ArrayList<Integer>();
    numberFieldIndexes = new ArrayList<Integer>();
    //for (int i = 0; !(line = reader.readLine()).equals("="); i++) {
    for (int i = 0; (line = reader.readLine()) != null && !line.equals("="); i++) {
      if(!line.startsWith("FIELD-")) {
        i--;
        continue;
      }
      String[] fieldDef = line.split(Loader.FIELD_SEPARATOR, -1);
      String fieldName = fieldDef[1];
      fieldIndexMap.put(fieldName, i);
      if (fieldName.toLowerCase().equals("latitude"))
          latFieldIndex = i;
      else if (fieldName.toLowerCase().equals("longitude")) {
        lonFieldIndex = i;
      } else {
        int index = findFieldTypeIndex(fieldDef);

        /*
         * Mark the field as a text field if the type is undefined or if the type is 'string'.
         * Mark the field as a number field if the type is 'number'.
         */
        if (index == -1 || fieldDef[index].toLowerCase().equals("t:string")) {
          textFieldIndexes.add(i);
        } else if (fieldDef[index].toLowerCase().equals("t:number")) {
          numberFieldIndexes.add(i);
        } else {
          // <ARC: 04-DEC-08> Unknown data types are assumed to be 'string'.
          textFieldIndexes.add(i);
        }
      }
      fieldCount++;
    }
    reader.close();
  } // private void parseHeader(File file)

  private int findFieldTypeIndex(String[] fieldDef) {
    for (int i = 0; i < fieldDef.length; i++) {
      if (fieldDef[i].startsWith("T:")) {
          return i;
      }
    }
    return -1;
  }

  public int getLatitudeFieldIndex() {
    return latFieldIndex;
  }

  public int getLongitudeFieldIndex() {
    return lonFieldIndex;
  }

  public ArrayList<Integer> getTextFieldIndexes() {
    return textFieldIndexes;
  }

  public ArrayList<Integer> getNumberFieldIndexes() {
    return numberFieldIndexes;
  }

  public int getFieldIndex(String fieldName) {
    Integer index = fieldIndexMap.get(fieldName);
    return (index != null) ? index : -1;
  }

  public int getFieldCount() {
    return fieldCount;
  }
}
