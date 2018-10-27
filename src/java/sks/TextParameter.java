package sks;

import java.util.regex.Pattern;
import sks.dataset.RecordParser;

/**
 * TextParameter
 * <p>
 * This class supports comparisons ">=", "<=" on string data types.
 * Note equality operator "=" is efficiently supported by SIIDX, thereby not included in this class.
 * This class is meant to be used as a post-filtering tool to overload ">=", "<=" on string fields.
 *
 * @author Ariel Cary
 * @version $Id: TextParameter.java,v 1.0 April 11, 2008
 */

public class TextParameter {
  int index;
  String value;
  boolean isValueNumeric;
  ComparisonOperator op;
  String fieldName = "";

  /**
   * @param s argument to be tested.
   * @return true if s is a number, false otherwise
   */
  private boolean isNumber(String s) {
    Pattern p = Pattern.compile("^[+-]?[0-9]+(\\.[0-9]*)?$");
    return p.matcher(s).matches();
  }

  public TextParameter(int index, String value, ComparisonOperator op,
          String fieldName) {
    this.index = index;
    this.value = value;
    this.op = op;
    this.fieldName = fieldName;

    if (isNumber(value)) {
      isValueNumeric = true;
    } else {
      isValueNumeric = false;
    }
  }

  /**
   * Verifies if the argument satisfies the comparison operator in relation to value.
   * Logic 12/19/08: If both arguments are numerals, the comparison is numeric;
   * otherwise the comparison is lexicographic case-insensitive.
   * e.g. ""<"9"<"A"="a"
   *
   * @param s argument to be verified.
   * @return true if s satisfies, else otherwise.
   */
  public boolean isSatisfiedBy(String s) {
    if (isValueNumeric) {
      if (s.trim().length() == 0) {
          s = "0";
      }
      
      s = RecordParser.cleanseNumericField(s);
    }

    switch (op) {
      case GREATER_THAN_EQUAL:
        // ARC: 5/20/08 Enhancement - treat strings as numbers when argument is numeric.
        if (isValueNumeric) {
          try {
            double d = Double.parseDouble(s);
            return (Double.parseDouble(value) >= d);
          } catch (NumberFormatException e) {   // s cannot be converted into a number.
            // s cannot be converted into a number, compare lexicographically.
          }
        }

        return value.compareTo(s) >= 0; //return value >= s;

      case LESS_THAN_EQUAL:
        // ARC: 5/20/08 Enhancement - treat strings as numbers when argument is numeric.
        if (isValueNumeric) {
          try {
            double d = Double.parseDouble(s);
            return (Double.parseDouble(value) <= d);
          } catch (NumberFormatException e) {   // s cannot be converted into a number.
            // s cannot be converted into a number, compare lexicographically.
          }
        }

        return value.compareTo(s) <= 0; //return value <= s;

      case GREATER_THAN:
        if (isValueNumeric) {
          try {
            double f = Double.parseDouble(s);
            return (Double.parseDouble(value) > f);
          } catch (NumberFormatException e) {   // s cannot be converted into a number.
            // s cannot be converted into a number, compare lexicographically.
          }
        }

        return value.compareTo(s) > 0;

      case LESS_THAN:
        // ARC: 5/20/08 Enhancement - treat strings as numbers when argument is numeric.
        if (isValueNumeric) {
          try {
            double f = Double.parseDouble(s);
            return (Double.parseDouble(value) < f);
          } catch (NumberFormatException e) {   // s cannot be converted into a number.
            // s cannot be converted into a number, compare lexicographically.
          }
        }

        return value.compareTo(s) < 0;

      default:
        break;
    }
    return false;
  } // public boolean isSatisfiedBy()

  @Override
  public String toString() {
    return ("<value=" + value + ", op=" + op.toString() + ", field=" + fieldName +
            ", ordering=" + (isValueNumeric? "Numeric":"Lexicographic") + ">");
  }
}