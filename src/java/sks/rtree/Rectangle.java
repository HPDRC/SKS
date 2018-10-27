/*
 * Rectangle.java
 */

package sks.rtree;
import java.io.Serializable;

public class Rectangle implements Serializable {
  static final long serialVersionUID = -2226647265621524347L;
  static final double EARTH_RADIUS = 6.371229*1e6;

  Point southwest;
  Point northeast;

  public Rectangle() {
    southwest = null;
    northeast = null;
  }

  public Rectangle(Point southwest, Point northeast) {
    if (southwest.x > northeast.x || southwest.y > northeast.y) {
      throw new IllegalArgumentException("southwest must be less than or equal than northeast");
    }
    
    if (southwest.equals(northeast)) {
      this.southwest = southwest;
      // Optimization: Keep only SW point for leaf nodes.
      this.northeast = null; // NE is assumed to be equal to SW.
    } else {
      this.southwest = southwest;
      this.northeast = northeast;
    }
  }

  /**
   * Returns NE point of the rectangle.
   * @return
   */
  public Point getNorthEastPoint() {
    if (northeast != null) {
      return northeast;
    } else {
      return southwest;
    }
  }

  public Rectangle getMinBoundingRect(Rectangle rect) {
    float minX = Math.min(southwest.x, rect.southwest.x);
    float minY = Math.min(southwest.y, rect.southwest.y);
    Point thisNorthEast = this.getNorthEastPoint();
    Point anotherNorthEast = rect.getNorthEastPoint();
    float maxX = Math.max(thisNorthEast.x, anotherNorthEast.x);
    float maxY = Math.max(thisNorthEast.y, anotherNorthEast.y);

    return new Rectangle(new Point(minX, minY), new Point(maxX, maxY));
  }

  public double getDistance(Point point) {
    double x;
    Point thisNorthEast = this.getNorthEastPoint();

    if (point.x < southwest.x) {
      x = southwest.x;
    } else if (point.x > thisNorthEast.x) {
      x = thisNorthEast.x;
    } else {
      x = point.x;
    }

    double y;

    if (point.y < southwest.y) {
      y = southwest.y;
    } else if (point.y > thisNorthEast.y) {
      y = thisNorthEast.y;
    } else {
      y = point.y;
    }

    // Calculate distance on earth's surface.
    double long1Radian = x * Math.PI / 180;
    double long2Radian = point.x * Math.PI / 180;
    double lat1Radian = y *Math.PI / 180;
    double lat2Radian = point.y * Math.PI / 180;

    // ARC: >NEW-CODE> - 22-APR-2008 bug fix for acos argument out of domain.
    double acosArg = Math.cos(lat1Radian) * Math.cos(lat2Radian) *
                    Math.cos(long1Radian - long2Radian) +
                    Math.sin(lat1Radian) * Math.sin(lat2Radian);

    if (acosArg > 1) {
      acosArg = 1;
    } else if (acosArg < -1) {
      acosArg = -1;
    }

    double distance = EARTH_RADIUS * Math.acos(acosArg);
    return distance;
  }

  public double getArea() {
    Point thisNorthEast = this.getNorthEastPoint();
    return (thisNorthEast.x - southwest.x) * (thisNorthEast.y - southwest.y);
  }

  public String toString() {
    Point thisNorthEast = this.getNorthEastPoint();
    return "[" + southwest + "][" + thisNorthEast + "]";
  }

  public boolean isPointInside(Point point) {
    if (point.x >= southwest.x && point.x <= northeast.x &&
        point.y >= southwest.y && point.y <= northeast.y) {
      return true;
    } else {
      return false;
    }
  }
}