/*
 * NumericRange.java
 *
 * Created on December 28, 2007, 2:14 AM
 *
 */

package sks.rtree;
import java.io.Serializable;

/**
 * A numeric range has two values [min, max].
 * A NumericRange object can have one or more such ranges.
 * 27-DEC-2008: Equivalence class of ZERO={[0]+, "", [^0-9]+}
 * 
 * @author Ariel Cary
 */
public class NumericRange implements Serializable {
  static final long serialVersionUID = 2337818445367554454L;

  float[] lowerBound = null;
  float[] upperBound = null;
  
  public NumericRange() {
    lowerBound = null;
    upperBound = null;
  }

  /**
   * Creates a new instance of NumericRange
   *
   * @param isLeafNode if true, then only the lowerBound array gets memory allocated,
   * and upperBound will always be null as it's identical to lowerBound.
   */
  public NumericRange(boolean isLeafNode, int numFieldCount) {
    if (isLeafNode) {
      lowerBound = new float[numFieldCount];
      upperBound = null;

      for (int i = 0; i < numFieldCount; i++) {
        lowerBound[i] = Float.POSITIVE_INFINITY;
      }
    } else {
      lowerBound = new float[numFieldCount];
      upperBound = new float[numFieldCount];

      for (int i = 0; i < numFieldCount; i++) {
        lowerBound[i] = Float.POSITIVE_INFINITY;
        upperBound[i] = Float.NEGATIVE_INFINITY;
      }
    }
  } // public NumericRange()

  /**
   *
   * @param numericValues
   */
  public void initializeBound(double[] numericValues) {
    if (numericValues != null) {
      lowerBound = new float[numericValues.length];

      for (int i = 0; i < numericValues.length; i++) {
        lowerBound[i] = (float) numericValues[i];
      }
    } else {
      lowerBound = null;
    }
    upperBound = null;
  } // public void initializeBound()

  /**
   *
   * @param index
   * @return
   */
  public float getLowerBoundAt(int index) {
    if (lowerBound != null && index < lowerBound.length) {
      return lowerBound[index];
    } else {
      return Float.NaN;
    }
  } // public float getLowerBoundAt()

  /**
   *
   * @param index
   * @return
   */
  public float getUpperBoundAt(int index) {
    float[] uBound = upperBound;

    if (uBound == null) {
      uBound = lowerBound;
    }

    if (uBound != null && index < uBound.length) {
      return uBound[index];
    } else {
      return Float.NaN;
    }
  } // public float getUpperBoundAt()
} // public class NumericRange