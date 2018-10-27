package sks;

import sks.rtree.NumericRange;
import sks.rtree.Point;

/**
 * NumericParameter
 * <p>
 * Numeric Parameters
 *
 * @author Ariel Cary
 * @version $Id: Loader.java,v 1.0 December 28, 2007
 */

public class NumericParameter {

  /**
   * in the record's numericField array.
   */
  int numFieldIndex; 
  String fieldName = "";
  double value;
  ComparisonOperator op;

  public NumericParameter(int numFieldIndex, double value, ComparisonOperator op,
          String fieldName) {
    this.numFieldIndex = numFieldIndex;
    this.value = value;
    this.op = op;
    this.fieldName = fieldName;
  }

  public boolean isSatisfiedBy(double d) {
    switch (op) {
      case EQUAL:
        return (Math.abs(value - d) <= Point.EPSILON);

      case GREATER_THAN_EQUAL:
        return value >= d;

      case LESS_THAN_EQUAL:
        return value <= d;

      case GREATER_THAN:
        return value > d;

      case LESS_THAN:
        return value < d;

      case NOT_EQUAL:
        return value != d;
    }
    return false;
  } // public boolean isSatisfiedBy()

  /**
   * 
   * @param numRange
   * @return
   */
  public boolean isSatisfiedByRange(NumericRange numRange) {
    switch (op) {
      case EQUAL:	// EQUAL and NOT_EQUAL are not technically range queries.
        return (value >= numRange.getLowerBoundAt(numFieldIndex) &&
                value <= numRange.getUpperBoundAt(numFieldIndex));

      case NOT_EQUAL: // Range intersection is not sufficient to determine
        return true; // if a leaf node contains a candidate object.

      case GREATER_THAN_EQUAL: // data <= value
        return (numRange.getLowerBoundAt(numFieldIndex) <= value);

      case LESS_THAN_EQUAL: // data >= value
        return (numRange.getUpperBoundAt(numFieldIndex) >= value);

      case GREATER_THAN: // data < value
        return (numRange.getLowerBoundAt(numFieldIndex) < value);

      case LESS_THAN: // data > value
        return (numRange.getUpperBoundAt(numFieldIndex) > value);
    }

    return false;
  } // public boolean isSatisfiedByRange()

  @Override
  public String toString() {
    return ("<value=" + value + ", op=" + op.toString() +
            ", field=" + fieldName + ">");
  }
}