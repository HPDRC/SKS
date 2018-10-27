/*
 * Point.java
 */

package sks.rtree;
import java.io.Serializable;

public class Point implements Serializable {
  static final long serialVersionUID = 9030748129005472782L;
  public static final float EPSILON = 0.00001f;

  public float x;
  public float y;

  public Point() {
  }

  public Point(float x, float y) {
    this.x = x;
    this.y = y;
  }

  @Override
  public String toString() {
    return x + "," + y;
  }

  /**
   * Tests if this point is equal to another.
   * @param another
   * @return
   */
  public boolean equals(Point another) {
    if (Math.abs(x - another.x) <= EPSILON &&
        Math.abs(y - another.y) <= EPSILON) {
      return true;
    } else {
      return false;
    }
  }
}