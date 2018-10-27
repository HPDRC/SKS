package sks.rtree;
import java.io.Serializable;

/**
 * this class is just a c++ like pair class. 
 * @quoted and modified from http://forum.java.sun.com/thread.jspa?threadID=5132045&tstart=165
 */
public class Pair<L, R> implements Serializable {
  static final long serialVersionUID = -1858711111871644640L;
  private final L left;
  private final R right;

  public R getRight() {
    return right;
  }

  public L getLeft() {
    return left;
  }

  public Pair(final L left, final R right) {
    this.left = left;
    this.right = right;
  }

  public static <A, B> Pair<A, B> create(A left, B right) {
    return new Pair<A, B>(left, right);
  }

  public final boolean equals(Object o) {
    if (!(o instanceof Pair)) {
      return false;
    }
    final Pair<?, ?> other = (Pair) o;
    return equal(getLeft(), other.getLeft()) && equal(getRight(), other.getRight());
  }

  public static final boolean equal(Object o1, Object o2) {
    if (o1 == null) {
      return o2 == null;
    }
    return o1.equals(o2);
  }

  @Override
  public int hashCode() {
    int hLeft = getLeft() == null ? 0 : getLeft().hashCode();
    int hRight = getRight() == null ? 0 : getRight().hashCode();
    return hLeft + (57 * hRight);
  }

  @Override
  public String toString() {
    String s = "<L=";

    if (left != null) {
      s += left.toString();
    }

    s += ", R=";

    if (right != null) {
      s += right.toString();
    }

    s += ">";

    return s;
  }
}