package sks.util;

/**
 * Encodes and decodes numeric data into/from a byte-sequence representation.
 * The algorithm is based on the following paper:
 * "Interval-based approach to lexicographic representation and compression of numeric data"
 * Data and Knowledge Engineering, Volume 8 (4), pp. 339-351, September 1992.
 *
 * Methods in this class assume intervals are partitioned in up to
 * 128 sub-intervals numbered from 0 to 127.
 * The highest-order bits of a byte are used to represent the partition number.
 * 
 * @author acary001
 */
public class NumberConverter {
  public NumberConverter() {

  }

  /**
   * Partitioning of the interval (-oo, +oo) (Table-1).
   * @param v
   * @return the partition number from where v falls in.
   */
  private byte partitioning1(double v) {
    if (v < -1) { // Interval #1
      return 0;
    }

    if (v < 0) { // Interval #2
      return 1;
    }

    return 1;
  }

  public byte[] encode(double v) {
    return null;
  }

  public double decode(byte[] ev) {
    return 0;
  }

}
