/*
 * SuperNodeBitmap.java
 *
 * Created on May 18, 2008, 3:32 PM
 *
 */

package sks.sif;

import java.io.Serializable;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Hashtable;

/**
 * Super node bitmap.
 * 
 * @author Ariel Cary
 */
public class SuperNodeBitmap implements Serializable {
  private CBitmap anyFieldBitmap;
  private Hashtable<Short, CBitmap> aFieldBitmaps;
  boolean isSingleField;

  public SuperNodeBitmap(Hashtable<Short, BitSet> fieldBitmaps) {
    if (fieldBitmaps.size() == 0) {
      anyFieldBitmap = null;
      aFieldBitmaps = null;
      isSingleField = true;
      return;
    }

    // Search for identical bitmaps.
    Hashtable<Short, String> sharedBS = new Hashtable<Short, String>();
    HashSet<Short> included = new HashSet<Short>();

    for (Short i: fieldBitmaps.keySet()) {
      if (included.contains(i)) {
        continue;
      }

      included.add(i);
      sharedBS.put(i, "");

      for (Short j: fieldBitmaps.keySet()) {
        if (!included.contains(j)) {
          if (fieldBitmaps.get(i).equals(fieldBitmaps.get(j))) {
            String p = (sharedBS.get(i) == null)? "" : sharedBS.get(i);
            String s = p + " " + j.shortValue();
            sharedBS.put(i, s);
            included.add(j);
          }
        }
      }
    }

    aFieldBitmaps = new Hashtable<Short, CBitmap>();
    BitSet anyFbs = new BitSet();

    for (Short fieldNbr: sharedBS.keySet()) {
      CBitmap cBitmap = new CBitmap(fieldBitmaps.get(fieldNbr));
      aFieldBitmaps.put(fieldNbr, cBitmap);

      if (sharedBS.get(fieldNbr).length() > 0) {
        String fields[] = sharedBS.get(fieldNbr).trim().split(" ");
        for (String f: fields) {
          // Optimize by storing only one bitmap copy referenced by all sharing fields.
          aFieldBitmaps.put(new Short(f), cBitmap);
        }
      }
      
      anyFbs.or(fieldBitmaps.get(fieldNbr));
    }

    if (sharedBS.size() == 1) {
      isSingleField = true;
      anyFieldBitmap = null;
    } else {
      isSingleField = false;
      anyFieldBitmap = new CBitmap(anyFbs);
    }
  }
  
  public CBitmap getAnyFieldBitmap() {
    if (isSingleField && aFieldBitmaps != null) {
      CBitmap anyFBitmap = null;
      for (Short sp: aFieldBitmaps.keySet()) { // there is supposed to be only one member in this set.
        anyFBitmap = aFieldBitmaps.get(sp);
        break;
      }
      
      return anyFBitmap;
    } else {
      return anyFieldBitmap;
    }
  }

  public CBitmap getAFieldBitmap(short fieldNbr) {
    if (aFieldBitmaps != null) {
      return aFieldBitmaps.get(new Short(fieldNbr));
    } else {
      return null;
    }
  }
} // public class SuperNodeBitmap