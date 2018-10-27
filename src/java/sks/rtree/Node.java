/*
 * Node.java
 */

package sks.rtree;

import java.io.Serializable;

public class Node implements Serializable {
  static final long serialVersionUID = -7825231196628624623L;

  public long ref = -1;
  public long parentRef = -1;
  public short level; // TODO: sksC2, make it private

  // Node's current size.
  public short size = 0; // TODO: sksC2, make it private

  // Reference to inner child node if internal node,
  // or reference to dataset objects if leaf node.
  public long[] refs; // TODO: sksC2, make it private

  // MBRs
  public Rectangle[] rects; // TODO: sksC2, make it private

  // Min/max boxes.
  public NumericRange[] numRanges = null; // TODO: sksC2, make it private

  public Node(short capacity, short level, boolean hasNumericFields) {
    this(capacity, level);

    if (hasNumericFields) {
      numRanges = new NumericRange[capacity];
    }
  }

  /**
   * Computes Min/Max bins for the Node.
   * @param isLeafNode
   * @return
   */
  public NumericRange getNumericRange(boolean isLeafNode, int numFieldCount) {
    if (numFieldCount > 0 && numRanges != null &&
        numRanges[0] != null && numRanges[0].lowerBound != null) {
      // Initialize numeric range.
      NumericRange nodeNumRange = new NumericRange(isLeafNode, numFieldCount);

      // Get min/max bins.
      for (int i = 0; i < size; i++) {
        for (int j = 0; j < numFieldCount; j++) {
          if (numRanges[i].lowerBound[j] < nodeNumRange.lowerBound[j]) {
            nodeNumRange.lowerBound[j] = numRanges[i].lowerBound[j];
          }

          float[] uBound = numRanges[i].upperBound;

          if (uBound == null) {
            uBound = numRanges[i].lowerBound;
          }

          if (!isLeafNode && uBound[j] > nodeNumRange.upperBound[j]) {
            nodeNumRange.upperBound[j] = uBound[j];
          }
        } // for (int j = 0; j < numFieldCount; j++)
      } // for (int i = 0; i < size; i++)

      return nodeNumRange;
    } else {
      return null;
    }
  } // NumericRange getNumericRange()
  
  public Node(short capacity) {
    this(capacity, (short) 0);
  }

  public Node(short capacity, short level) {
    refs = new long[capacity];
    rects = new Rectangle[capacity];

    for(int i = 0; i < capacity; i++) {
      refs[i] = 0;
    }

    this.level = level;
  }

  boolean isRoot() {
    return parentRef == -1;
  }

  boolean isLeaf() {
    return level == 0;
  }

  boolean isFull() {
    return size == refs.length;
  }

  public int size() {
    return size;
  }

  @Override
  public String toString() {
    StringBuffer buff = new StringBuffer("ref=" + ref + ",parentref=" + parentRef + ",level=" + level + ",size=" + size);
    for (int i = 0; i < size; i++) {
      buff.append("\n\t" + i + ":childRef=" + refs[i] + ",rect=" + rects[i]);
    }

    return buff.toString();
  }

  public Rectangle getMinBoundingRect() {
    Rectangle rect = rects[0];

    for (int i = 1; i < size; i++) {
      rect = rect.getMinBoundingRect(rects[i]);
    }

    return rect;
  }

  void insert(long ref, Rectangle rect, NumericRange numRange) {
    refs[size] = ref;
    rects[size] = rect;

    if (numRanges != null) {
      numRanges[size] = numRange;
    }

    size++;
  }

  Node[] split(long ref, Rectangle rect, short maxCapacity, short minCapacity, NumericRange numRange) {
    long []tmpRefs = new long[refs.length + 1];
    System.arraycopy(this.refs, 0, tmpRefs, 0, refs.length);
    tmpRefs[refs.length] = ref;
    this.refs = tmpRefs;

    Rectangle []tmpRects = new Rectangle[rects.length + 1];
    System.arraycopy(this.rects, 0, tmpRects, 0, rects.length);
    tmpRects[rects.length] = rect;
    this.rects = tmpRects;

    if (numRanges != null) {
      NumericRange[] tmpNumRanges = new NumericRange[numRanges.length + 1];
      System.arraycopy(this.numRanges, 0, tmpNumRanges, 0, numRanges.length);
      tmpNumRanges[numRanges.length] = numRange;
      this.numRanges = tmpNumRanges;
    }

    // Generate two nodes with the split groups.
    int[][] indexes = getSplitIndexes(minCapacity);
    Node[] nodes = new Node[2];
    boolean hasNumericFields = false;

    if (numRange != null && numRange.lowerBound != null) {
      hasNumericFields = true;
    }

    for (int i = 0; i < 2; i++) {
      nodes[i] = new Node(maxCapacity, level, hasNumericFields);
      for (int j = 0; j < indexes[i].length; j++) {
        int index = indexes[i][j];
        nodes[i].insert(refs[index], rects[index], (numRanges != null)? numRanges[index] : null);
      }
    }

    return nodes;
  }

  /**
   * Finds the two rectangles such that their MBR has the largest wasted area.
   * @return indexes in refs of such rectangles.
   */
  private int[] getSeeds() {
    double inefficiency = -1;
    int seed1 = 0;
    int seed2 = 0;

    for (int i = 0; i < rects.length - 1; i++) {
      for (int j = i + 1; j < rects.length; j++) {
        Rectangle rect = rects[i].getMinBoundingRect(rects[j]);
        double diff = rect.getArea() - rects[i].getArea() - rects[j].getArea();

        if (diff > inefficiency) {
          inefficiency = diff;
          seed1 = i;
          seed2 = j;
        }
      }
    }

    return new int[] {seed1, seed2};
  }

  /**
   * Strives to find two groups of rectangles such that their MBRs are minimal.
   * @param minCapacity
   * @return
   */
  private int[][] getSplitIndexes(int minCapacity) {
    int[] seeds = getSeeds();

    // quadratic split
    int unassignedCount = rects.length;
    int count1 = 0;
    int count2 = 0;
    int[] indexes1 = new int[rects.length - minCapacity];
    int[] indexes2 = new int[rects.length - minCapacity];
    boolean[] assigned = new boolean[rects.length];
    indexes1[count1++] = seeds[0];
    assigned[seeds[0]] = true;
    unassignedCount--;
    indexes2[count2++] = seeds[1];
    assigned[seeds[1]] = true;
    unassignedCount--;

    while (unassignedCount > 0) {
      if (minCapacity - count1 == unassignedCount) {
        for (int i = 0; i < rects.length; i++) {
          if (!assigned[i]) {
            indexes1[count1++] = i;
          }
        }

        break;
      } else if (minCapacity - count2 == unassignedCount) {
        for (int i = 0; i < rects.length; i++) {
          if (!assigned[i]) {
            indexes2[count2++] = i;
          }
        }

        break;
      }

      Rectangle superRect1 = rects[indexes1[0]];

      // Compute the MBR of rectangles referenced by indexes1.
      for (int i = 1; i < count1; i++) {
        superRect1 = superRect1.getMinBoundingRect(rects[indexes1[i]]);
      }

      Rectangle superRect2 = rects[indexes2[0]];

      // Compute the MBR of rectangles referenced by indexes2.
      for (int i = 1; i < count2; i++) {
        superRect2 = superRect2.getMinBoundingRect(rects[indexes2[i]]);
      }

      double cost = -1;
      double selectedEnlargement1 = 0;
      double selectedEnlargement2 = 0;
      int selectedIndex = 0;

      // From the set of unassigned rectangles,
      // pick the one that generates the largest enlargement
      // when combined with superRect1 or superRect2.
      for (int i = 0; i < rects.length; i++) {
        if (!assigned[i]) {
          Rectangle rect1 = superRect1.getMinBoundingRect(rects[i]);
          double enlargement1 = rect1.getArea() - superRect1.getArea();
          Rectangle rect2 = superRect2.getMinBoundingRect(rects[i]);
          double enlargement2 = rect2.getArea() - superRect2.getArea();
          // Compare individual enlargements.
          double diff = Math.abs(enlargement1 - enlargement2);

          if (diff > cost) {
            cost = diff;
            selectedIndex = i;
            selectedEnlargement1 = enlargement1;
            selectedEnlargement2 = enlargement2;
          }
        }
      } // for (int i = 0; i < rects.length; i++)

      // Assign the selected rectangle to the group
      // where it generates the least enlargement.
      if (selectedEnlargement1 < selectedEnlargement2) {
        indexes1[count1++] = selectedIndex;
      } else if (selectedEnlargement2 < selectedEnlargement1) {
        indexes2[count2++] = selectedIndex;
      } else if (superRect1.getArea() < superRect2.getArea()) {
        indexes1[count1++] = selectedIndex;
      } else if (superRect2.getArea() < superRect1.getArea()) {
        indexes2[count2++] = selectedIndex;
      } else if (count1 < count2) {
        indexes1[count1++] = selectedIndex;
      } else if (count2 < count1) {
        indexes2[count2++] = selectedIndex;
      } else {
        indexes1[count1++] = selectedIndex;
      }

      assigned[selectedIndex] = true;
      unassignedCount--;
    } // while (unassignedCount > 0)

    // pack the arrays.
    int[][] indexes = new int[2][];
    indexes[0] = new int[count1];
    indexes[1] = new int[count2];

    for (int i = 0; i < count1; i++) {
      indexes[0][i] = indexes1[i];
    }

    for (int i = 0; i < count2; i++) {
      indexes[1][i] = indexes2[i];
    }

    return indexes;
  } // private int[][] getSplitIndexes()
}