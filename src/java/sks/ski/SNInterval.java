package sks.ski;

/**
 *
 * @author acary001
 */
public class SNInterval {
  private int start;
  private int end;

  public SNInterval(int start, int end) {
    this.start = start;
    this.end = end;
  }

  public int getStart() {
    return start;
  }

  public int getEnd() {
    return end;
  }

  @Override
  public String toString() {
    return ("<" + start + ", " + end + ">");
  }
}