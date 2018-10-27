/*
 * TermComparator.java
 *
 * Created on May 19, 2008, 12:16 PM
 *
 */

package sks.sif;

import java.io.Serializable;
import java.util.Comparator;

/**
 *
 * @author Ariel Cary
 */
public class TermAtSNComparator implements Comparator, Serializable {
  final static long serialVersionUID = -8423670814958389708L;

  public TermAtSNComparator() {
  }

  /**
   * Compare objects.
   *
   * @param term1 First object
   * @param term2 Second object
   * @return 1 if lexicographically term1 > term2 OR (term1 == term2 AND sn1 > sn2),
   *         0 if term1 == term2 AND sn1 == sn2,
   *         -1 if term1 < term2 OR (term1 == term2 AND sn1 < sn2)
   */
  @Override
  public int compare(Object term1, Object term2) {
    if (!(term1 instanceof TermAtSN)) {
      throw new IllegalArgumentException("term1 is of invalid type");
    }

    if (!(term2 instanceof TermAtSN)) {
      throw new IllegalArgumentException("term2 is of invalid type");
    }

    TermAtSN termAtSN1 = (TermAtSN) term1;
    TermAtSN termAtSN2 = (TermAtSN) term2;

    int termComp = termAtSN1.getTerm().compareTo(termAtSN2.getTerm());

    if (termComp < 0) {
      return -1;
    } else if (termComp > 0) {
      return 1;
    } else { // Base order on SN Ids.
      if (termAtSN1.getSNId() < termAtSN2.getSNId()) {
        return -1;
      } else if (termAtSN1.getSNId() > termAtSN2.getSNId()) {
        return 1;
      } else {
        return 0;
      }
    }
  } // public int compare(Object term1, Object term2)
}