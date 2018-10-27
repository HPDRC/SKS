package sks.ski;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 *
 * @author acary001
 */
public class SearchEntry implements Comparable<SearchEntry>, Serializable {
  long ref;
  double distance;
  boolean pointsToInnerNode;
  short nodeLevel;
  long parentEntryId = 0;
  int skiIndex = -1; // -1 means there is a single SKI.

  public SearchEntry(long ref, double distance, boolean isNodeReference,
          short nodeLevel, long parentEntryId, int skiIndex) {
    this.ref = ref;
    this.distance = distance;
    this.pointsToInnerNode = isNodeReference;
    this.parentEntryId = parentEntryId;
    this.nodeLevel = nodeLevel;
    this.skiIndex = skiIndex;
  }

  /**
   * Compares objects.
   * @param otherElement
   * @return "Returns a negative integer, zero, or a positive integer as this object is less
   * than, equal to, or greater than the specified object."
   */
  @Override
  public int compareTo(SearchEntry otherElement) {
    return (distance > otherElement.distance)? 1 : -1;
  }

  public SearchEntry(double distance) {
    this.distance = distance;
  }

  @Override
  public String toString() {
    NumberFormat formatter = new DecimalFormat("#0.000");
    StringBuffer buff = new StringBuffer("<ref=" + ref +
            ", parEntryId=" + parentEntryId +
            ", dist=" + formatter.format(distance) +
            ", ref?=" + pointsToInnerNode +
            ", lev=" + nodeLevel + ", ski=" + skiIndex + ">");

    return buff.toString();
  }
} // public class SearchEntry