package sks.sif;

import java.io.IOException;
import java.io.Serializable;

/**
 * Represents a pair <term, SN>.
 *
 * @author acary001
 */
public class TermAtSN implements Serializable {
  static final long serialVersionUID = 8101289728867192051L;
  byte[] term;
  byte[] snId;

  /**
   *
   * @param term
   * @param snId range is positive integers.
   * @throws IOException
   */
  public TermAtSN(String term, int snId) throws IOException {
    this.term = term.getBytes();
    int size = (int) Math.ceil(Math.log10(snId + 1) / Math.log10(2) / 8f);

    if (size <= 0) {
      size = 1;
    }

    this.snId = new byte[size];

    for (int i = 0; i < size && i < (Integer.SIZE / 8); i++) {
      this.snId[i] = (byte) snId;
      snId >>= 8;
    }
  }

  public String getTerm() {
    return new String(term);
  }

  public int getSNId() {
    int snId = 0;

    for (int i = (this.snId.length - 1); i >= 0; i--) {
      snId <<= 8;
      if (this.snId[i] < 0) {
        snId |= (this.snId[i] + 256);
      } else {
        snId |= this.snId[i];
      }
    }

    return snId;
  }

  @Override
  public String toString() {
    return (getTerm() + "@" + getSNId());
  }
}