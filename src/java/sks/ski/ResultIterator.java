package sks.ski;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.PriorityQueue;
import sks.NumericParameter;
import sks.QueryTextPredicate;
import sks.dataset.Record;
import sks.rtree.Node;
import sks.rtree.NumericRange;
import sks.rtree.Point;

/**
 *
 * @author acary001
 */
public abstract class ResultIterator implements Iterator<Record> {
  protected Index index;
  protected Point queryPoint;
  protected PriorityQueue<SearchEntry> queue;
  protected SearchEntry nextResult;
  protected double distanceLimit;

  protected ArrayList<NumericParameter> numericParams = null;
  protected ArrayList<QueryTextPredicate> queryTextPredicates = null;
  protected boolean queryHasTextPredicates = false;

  //yun
  boolean _debug_mode = false;
  protected static final int MAX_SEARCH_TIME_MSEC = 5000;

  public ResultIterator(Index index, Point point, double distance,
                   ArrayList<NumericParameter> numericParams,
                   ArrayList<QueryTextPredicate> queryTextPredicates,
                   boolean _debug_mode) {
    this.index = index;
    this.queryPoint = point;
    this.distanceLimit = distance;
    this.numericParams = numericParams;
    this.queryTextPredicates = queryTextPredicates;
    this.queue = new PriorityQueue<SearchEntry>();
    this._debug_mode = _debug_mode;

    if (this.queryTextPredicates != null && this.queryTextPredicates.size() > 0) {
      queryHasTextPredicates = true;
    } else {
      queryHasTextPredicates = false;
    }
  }

  /**
   * Tests if a range satisfies query numeric predicates.
   * @param numericRange
   * @return True if the range satisfies numeric predicates, false otherwise.
   */
  protected boolean rangeSatisfiesPredicates(NumericRange numericRange) {
    for (int j = 0; numericRange != null && j < numericParams.size(); j++) {
      NumericParameter numericParameter = numericParams.get(j);

      if (!numericParameter.isSatisfiedByRange(numericRange)) {
        return false;
      }
    }
    
    return true;
  }

  protected void enqueueEntries(Node node, BitSet[] queryBitmap,
          long entryId, int skiIndex) {
    int nodeSize = node.size();
    
    if (node.level == 0 && queryHasTextPredicates) {
      // Select only candidate objects from this leaf node.
      // (queryBitmap != null)
      for (int i = queryBitmap[0].nextSetBit(0); i >= 0 && i < nodeSize;
           i = queryBitmap[0].nextSetBit(i + 1)) {
        if (node.numRanges != null && !rangeSatisfiesPredicates(node.numRanges[i])) {
          continue;
        }
        
        queue.add(new SearchEntry(node.refs[i],
                                  node.rects[i].getDistance(queryPoint),
                                  false,      // pointsToInnerNode
                                  (short) 0,  // nodeLevel    // less relevant
                                  entryId + i, // parentEntryId // less relevant
                                  skiIndex)); // skiIndex
      }

      return;
    } // if (node.level == 0)

    for (int i = 0; i < nodeSize; i++) {
      double distance = node.rects[i].getDistance(queryPoint);

      // Query radius filter.
      if (distance > distanceLimit) {
        continue;
      }

      // Numeric predicates.
      if (node.numRanges != null && !rangeSatisfiesPredicates(node.numRanges[i])) {
        continue;
      }
      
      if (node.level == 1 && queryHasTextPredicates) {
        // Prune level-0 nodes.
        if (queryBitmap == null || queryBitmap[i] == null || queryBitmap[i].cardinality() == 0) {
          // Not a candidate node.
          // System.out.println("Prunning subtree: entryId=" + (entryId + i) + ", nodeRef=" + node.refs[i] + ", level=" + node.level);
          continue;
        }
      }

      queue.add(new SearchEntry(node.refs[i],
                                distance,
                                (node.level == 0)? false:true,
                                (short) Math.max((node.level - 1), 0), // nodeLevel
                                entryId + i, // parentEntry
                                skiIndex)); // skiIndex
    } // for (int i = 0; i < nodeSize; i++)
  } // protected void enqueueEntries(Node node)

  /**
   * Gets query bitmaps for level 0 and 1 nodes of a given entry
   * from the buffered bitmap table.
   * @param searchEntry
   * @return array of uncompressed bitmaps.
   */
  protected BitSet[] getQueryBitmap(SearchEntry searchEntry,
          Hashtable<Integer, BitSet[]> bufferedQueryBitmaps, int M) {
    BitSet[] queryBitmap = null;

    if (searchEntry.nodeLevel == 1) {
      // Get super node query bitmap.
      queryBitmap = bufferedQueryBitmaps.get((int) searchEntry.parentEntryId);
    }

    if (searchEntry.nodeLevel == 0) {
      // Get the individual node query bitmap.
      int snId = (int) (searchEntry.parentEntryId / M);
      BitSet[] nodeBitmaps = bufferedQueryBitmaps.get(snId);
      queryBitmap = new BitSet[1];
      queryBitmap[0] = nodeBitmaps[(int) (searchEntry.parentEntryId % M)];

      if (queryBitmap == null || queryBitmap[0] == null ||
          queryBitmap[0].cardinality() == 0) {
        // This should not happen in level-0 entries.
        return null;
      }
    }

    return queryBitmap;
  } // protected BitSet[] getQueryBitmap()

  public void clear() {
  }
}