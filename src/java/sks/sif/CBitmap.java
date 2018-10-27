/*
 * CBitmap.java
 *
 * Created on July 26, 2008, 1:35 PM
 *
 */

package sks.sif;

import java.io.Serializable;
import java.util.BitSet;

/**
 * Implements a compressed bitmap object.
 * This is a key object in the spatial keyword index.
 * Modifying this class usually means rebuilding the index.
 *
 * @author Ariel Cary
 */
public class CBitmap implements Serializable {
  static final long serialVersionUID = -8641513433515117242L;
  private WAHBitSet bitmap;

  /** Creates a new instance of CBitmap */
  public CBitmap(BitSet bs) {
    this.bitmap = new WAHBitSet(bs);
  }

  /** Creates a new instance of CBitmap */
  public CBitmap(WAHBitSet cbs) {
    this.bitmap = cbs;
  }

  public int cardinality() {
    if (bitmap != null) {
      return bitmap.cardinality();
    } else {
      return 0;
    }
  }

  public CBitmap and(CBitmap cBitmap) {
    if (cBitmap != null) {
      return new CBitmap(this.bitmap.and(cBitmap.bitmap));
    } else {
      return null;
    }
  } // public CBitmap and()

  public CBitmap sAnd(CBitmap cBitmap, int nbits) {
    if (cBitmap != null) {
      // uncompress bitmaps
      BitSet bs1 = this.getBitSet(nbits);
      BitSet bs2 = cBitmap.getBitSet(nbits);
      bs1.and(bs2);

      if (bs1.cardinality() > 0) {
        return new CBitmap(bs1);
      } else {
        return null;
      }
    } else {  // Nothing to do.
      return null;
    }
  }

  public CBitmap or(CBitmap cBitmap, int nbits) {
    if (cBitmap != null) {
      // uncompress bitmaps
      BitSet bs1 = this.getBitSet(nbits);
      BitSet bs2 = cBitmap.getBitSet(nbits);
      bs1.or(bs2);

      return new CBitmap(bs1);
    } else {  // Nothing to do.
      return null;
    }
  }

  /**
   * Retuns uncompressed version of this bitmap.
   */
  public BitSet getBitSet() {
    return getBitSet(0);
  }

  public WAHBitSet.IndexSet getIndexSet() {
    if (bitmap != null) {
      return this.bitmap.getIndexSet();
    } else {
      return null;
    }
  }

  public WAHBitSet.WAHIterator getIterator() {
    if (bitmap != null) {
      return (WAHBitSet.WAHIterator) this.bitmap.iterator();
    } else {
      return null;
    }
  }

  /**
   * Flips the bits in this CBitmap.
   *
   * @param nBits initial size of the resulting BitSet.
   */
  public CBitmap flip(int nbits) {
    BitSet fbs = getBitSet(nbits);
    fbs.flip(0, nbits);
    return (new CBitmap(fbs));
  }

  public BitSet flipBS(int nbits) {
    BitSet fbs = getBitSet(nbits);
    fbs.flip(0, nbits);
    return fbs;
  }

  /**
   * Returns an uncompressed version of the (super node) bitmap.
   * Note: consider writing getBitSet for an individual node.
   * @param nBits initial size of the resulting BitSet.
   */
  public BitSet getBitSet(int nBits) {
    BitSet bs = null;
    if (nBits > 0) {
      bs = new BitSet(nBits);
    } else {
      bs = new BitSet();
    }

    WAHBitSet.IndexSet is = this.bitmap.getIndexSet();
    while (is.hasMore()) {
      is.next();
      int next[] = is.indices();

      boolean range = is.isRange();
      if (range) {
        int range_start = next[0];
        int range_end = next[1];
        bs.set(range_start, range_end); // "is.nIndices()" is the number of set bits starting at "next[0]".
      } else {
        // You want to invoke set(from, to) as many times as possible...
        int nIndices = is.nIndices();
        for (int j = 0; j < nIndices; j++) {
          int range_start = next[j];
          while (j < nIndices && (next[j+1]-next[j]) == 1 ) {
            j ++;
          }

          if (j < nIndices) {
            bs.set(range_start, next[j]+1);
          } else {
            bs.set(range_start, next[nIndices-1]+1);
          }
        }
      }
    }

    return bs;
  } // public BitSet getBitSet(int nBits)

  @Override
  public String toString() {
    int i = getBitSet().toString().indexOf("{");
    int j = getBitSet().toString().indexOf("}");
    String bits = getBitSet().toString();

    if ((j - i)> 3000) {
      bits = bits.substring(i, i + 3000) + " ...}";
    }
    
    return ("[card=" + bitmap.cardinality() + ", bits=" + bits + "]");
  }
} // public class CBitmap implements Serializable