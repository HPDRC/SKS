package sks.ski;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Hashtable;
import java.util.logging.Level;
import sks.NumericParameter;
import sks.QueryTextPredicate;
import sks.dataset.Record;
import sks.rtree.Node;
import sks.rtree.Point;
import java.util.logging.Logger;
import sks.dataset.MalformedRecordException;
import sks.dataset.RandomDatasetReader;

/**
 *
 * @author acary001
 */
public class BSKIResultIterator extends ResultIterator {
  private BigSpatialKeywordIndex bSki;
  private ArrayList<Hashtable<Integer, BitSet[]>> allBufferedSNbitmaps = null;
  private ArrayList<ArrayList<SNInterval>> allNcSNRanges = null;
  private RandomAccessFile[] allNodesFile;
  private RandomDatasetReader[] allDatasetReader;

  public BSKIResultIterator(Index index, Point point, double distance,
                   ArrayList<NumericParameter> numericParams,
                   ArrayList<QueryTextPredicate> queryTextPredicates,
                   boolean _debug_mode) throws FileNotFoundException, IOException {
    super(index, point, distance, numericParams, queryTextPredicates, _debug_mode);

    bSki = (BigSpatialKeywordIndex) index;
    allBufferedSNbitmaps = new ArrayList<Hashtable<Integer, BitSet[]>>();
    allNcSNRanges = new ArrayList<ArrayList<SNInterval>>();
    allNodesFile = new RandomAccessFile[bSki.getRootNode().size()];
    allDatasetReader = new RandomDatasetReader[bSki.getRootNode().size()];

    enqueueSKIRoots(bSki.getRootNode());
  } // public SKIResultIterator()

  private void enqueueSKIRoots(Node root) throws FileNotFoundException, IOException {
    // Put all SKI root nodes in the queue.
    int nodeSize = root.size();

    for (int i = 0; i < nodeSize; i++) {
      allBufferedSNbitmaps.add(i, new Hashtable<Integer, BitSet[]>());
      allNcSNRanges.add(i, new ArrayList<SNInterval>());
      double distance = root.rects[i].getDistance(queryPoint);

      // Query radius filter.
      if (distance > distanceLimit) {
        continue;
      }

      // Numeric predicates.
      if (root.numRanges != null && !rangeSatisfiesPredicates(root.numRanges[i])) {
        continue;
      }

      allNodesFile[i] = new RandomAccessFile(bSki.getSKIs()[i].getSKIManager().getNodesFilename(), "r");
      allDatasetReader[i] = new RandomDatasetReader(bSki.getSKIs()[i].getDataset());

      queue.add(new SearchEntry(root.refs[i],
                                distance,
                                (root.level == 0)? false:true,
                                (short) Math.max((root.level - 1), 0),
                                -1, // long parentEntryId. -1 indicates the parent is BigSKI.
                                i));
    } // for (int i = 0; i < nodeSize; i++)
  } // private void enqueueSKIRoots()

  @Override
  public boolean hasNext() {
    long startTime = System.currentTimeMillis();

    while (!queue.isEmpty() &&
          ((System.currentTimeMillis() - startTime) < MAX_SEARCH_TIME_MSEC ||
           _debug_mode))  {
      SearchEntry searchEntry = queue.poll();
      // ARC: >Debug>
      //                System.out.print("q_size=" + queue.size() + ":\t");
      //                System.out.println(element.toString());

      if (!searchEntry.pointsToInnerNode) {
        nextResult = searchEntry;
        return true;
      }

      if (searchEntry.parentEntryId == -1) {
        // Root node of a SKI subtree.
        searchEntry.parentEntryId = 0;
      }

      Hashtable<Integer, BitSet[]> bufferedQueryBitmaps = allBufferedSNbitmaps.get(searchEntry.skiIndex);
      ArrayList<SNInterval> ncSNIntervals = allNcSNRanges.get(searchEntry.skiIndex);
      SpatialKeywordIndex ski = bSki.getSKIs()[searchEntry.skiIndex];

      // Apply Lazy Filter (LF).
      try {
        if (queryHasTextPredicates &&
            !ski.getRtree().isSubtreeCandidate(searchEntry.parentEntryId,
              (short) (searchEntry.nodeLevel + 1), ncSNIntervals,
              bufferedQueryBitmaps, queryTextPredicates, ski.getSIF())) {
          // Check the next NN entry.
          continue;
        }
      } catch (IOException ex) {
        Logger.getLogger(SpatialKeywordIndex.class.getName()).log(Level.SEVERE, null, ex);
        return false;
      }

      // Invariant: bufferedQueryBitmaps contains a super node bitmap for subtree
      //            rooted at <searchEntry.parentEntryId> on level=(searchEntry.nodeLevel + 1)

      // Retrieve candidate R-tree node.
      Node node = ski.getSKIManager().readNode(searchEntry.ref, allNodesFile[searchEntry.skiIndex]);

      if (node == null) {
        return false;
      }

      long entryId = searchEntry.parentEntryId * ski.getRtree().getMaxCapacity();
      BitSet[] queryBitmap = null;

      if (queryHasTextPredicates) {
        queryBitmap = getQueryBitmap(searchEntry, bufferedQueryBitmaps,
                      ski.getRtree().getMaxCapacity());
      }

      enqueueEntries(node, queryBitmap, entryId, searchEntry.skiIndex);
    } // while (!queue.isEmpty())

    // ARC: >Debug>
//      System.out.print("ncSNIntervals = " + ncSNIntervals.toString());
//      System.out.print("allBufferedSNbitmaps = " + allBufferedSNbitmaps.toString());

    return false;
  }

  @Override
  public Record next() {
    if (nextResult == null) {
      if (!hasNext()) {
        throw new java.util.NoSuchElementException();
      }
    }

    Record record = null;

    // Retrieve next record.
    try {
      record = allDatasetReader[nextResult.skiIndex].recordAt(nextResult.ref);
    } catch (IOException e) {
      // IO error.
    } catch (MalformedRecordException e) {
      // This should not happen here. Only well-formed records are indexed.
    }

    nextResult = null;
    return record;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void clear() {
    if (allNodesFile != null) {
      try {
        for (int i = 0; i < allNodesFile.length; i++) {
          if (allNodesFile[i] != null) {
            allNodesFile[i].close();
            allNodesFile[i] = null;
          }
        }
      } catch (IOException ex) {
        Logger.getLogger(SKIResultIterator.class.getName()).log(Level.SEVERE, null, ex);
      }
    }

    if (allDatasetReader != null) {
      try {
        for (int i = 0; i < allDatasetReader.length; i++) {
          if (allDatasetReader[i] != null) {
            allDatasetReader[i].close();
            allDatasetReader[i] = null;
          }
        }
      } catch (IOException ex) {
        Logger.getLogger(SKIResultIterator.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
  } // public void clear()
}