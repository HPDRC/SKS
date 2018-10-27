package sks.rtree;

import java.io.Serializable;

/**
 *
 * @author acary001
 */
public class SuperNodeBoundary implements Serializable {
  static final long serialVersionUID = -1355673049442085977L;
  
  private short[] boundaries = null;

  public SuperNodeBoundary(int nodes) {
    boundaries = new short[nodes];
  }

  public void set(int index, short size) {
    if (boundaries != null && index < boundaries.length) {
      boundaries[index] = size;
    }
  }

  public short get(int index) {
    if (boundaries != null && index < boundaries.length) {
      return boundaries[index];
    } else {
      return -1;
    }
  }

  public int length() {
    if (boundaries != null) {
      return boundaries.length;
    } else {
      return 0;
    }
  }
  
  @Override
  public String toString() {
    if (boundaries == null || boundaries.length == 0) {
      return "[]";
    }

    String s = "{length=" + length() + ", boundaries=[";

    for (int i = 0; i < boundaries.length; i++) {
      s += boundaries[i] + ", ";
    }

    s = s.substring(0, s.length() - 2);
    s += "]}";
    
    return s;
  }
} // public class SuperNodeBoundary