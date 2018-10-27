package sks.ski;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import sks.NumericParameter;
import sks.QueryTextPredicate;
import sks.dataset.MalformedRecordException;
import sks.dataset.RandomDatasetReader;
import sks.dataset.Record;
import sks.rtree.Node;
import sks.rtree.Point;

/**
 *
 * @author acary001
 */
public class SKIResultIterator extends ResultIterator {
  // Uncompressed super node bitmaps computed along the query processing path.
  private Hashtable<Integer, BitSet[]> bufferedQueryBitmaps = null;
  
  // Non-candidate super node intervals.
  private ArrayList<SNInterval> ncSNIntervals = null;

  private SpatialKeywordIndex ski;
  private RandomAccessFile nodesFile;
  private RandomDatasetReader datasetReader;

  public SKIResultIterator(Index index, Point point, double distance,
                   ArrayList<NumericParameter> numericParams,
                   ArrayList<QueryTextPredicate> queryTextPredicates,
                   boolean _debug_mode) throws FileNotFoundException, IOException {
    super(index, point, distance, numericParams, queryTextPredicates, _debug_mode);

    bufferedQueryBitmaps = new Hashtable<Integer, BitSet[]>();
    ncSNIntervals = new ArrayList<SNInterval>();
    ski = (SpatialKeywordIndex) index;
    nodesFile = new RandomAccessFile(ski.getSKIManager().getNodesFilename(), "r");
    datasetReader = new RandomDatasetReader(ski.getDataset());

    BitSet[] queryBitmap = null;
    
    if (ski.getRtree().getRootNode().level < 2 && queryHasTextPredicates) {
      // Call isSubtreeCandidate() is needed for small R-trees, e.g. levels < 2.
      if (ski.getRtree().isSubtreeCandidate(0, (short) (ski.getRtree().getRootNode().level + 1),
          ncSNIntervals, bufferedQueryBitmaps, queryTextPredicates, ski.getSIF())) {
        queryBitmap = getQueryBitmap(new SearchEntry(ski.getRtree().getRootNode().ref,
          ski.getRtree().getRootNode().getMinBoundingRect().getDistance(queryPoint), // distance
          false, // isNodeReference,
          ski.getRtree().getRootNode().level, // nodeLevel
          0, // parentEntryId
          -1), //skiIndex),
          bufferedQueryBitmaps,
          ski.getRtree().getMaxCapacity());
      } else {
        // No candidates. Leave queue empty.
        return;
      }
    }
    
    enqueueEntries(ski.getRtree().getRootNode(), queryBitmap, 0, -1);
  } // public SKIResultIterator()
  
  @Override
  public boolean hasNext() {
    long startTime = System.currentTimeMillis();

    while (!queue.isEmpty() &&
          ((System.currentTimeMillis() - startTime) < MAX_SEARCH_TIME_MSEC ||
           true))  {
        //_debug_mode
      SearchEntry searchEntry = queue.poll();
      // ARC: >Debug>
      //                System.out.print("q_size=" + queue.size() + ":\t");
      //                System.out.println(element.toString());

      if (!searchEntry.pointsToInnerNode) {
        nextResult = searchEntry;
        return true;
      }

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
      //23-MAY-2011: Node node = ski.getSKIManager().readNode(searchEntry.ref, nodesFile);
      Node node = null;

      // Retrieve candidate R-tree node.
      if (ski.getRtree().getNodesInStorage().containsKey(searchEntry.ref)) {
        // Read node from main memory.
        node = ski.getRtree().getNodesInStorage().get(searchEntry.ref);
      } else {
        // Read node from the persistent storage.
        node = ski.getSKIManager().readNode(searchEntry.ref, nodesFile);
      }

      if (node == null) {
        return false;
      }

      long entryId = searchEntry.parentEntryId * ski.getRtree().getMaxCapacity();
      BitSet[] queryBitmap = null;

      if (queryHasTextPredicates) {
        queryBitmap = getQueryBitmap(searchEntry, bufferedQueryBitmaps,
                      ski.getRtree().getMaxCapacity());
      }

      enqueueEntries(node, queryBitmap, entryId, -1);
    } // while (!queue.isEmpty())

    // ARC: >Debug>
//      System.out.print("ncSNIntervals = " + ncSNIntervals.toString());
//      System.out.print("bufferedSNbitmaps = " + bufferedSNbitmaps.toString());

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
      record = datasetReader.recordAt(nextResult.ref);
    } catch (IOException e) {
      // IO error.
    } catch (MalformedRecordException e) {
      // This should not happen here. Only well-formed records are indexed.
    }

    nextResult = null;
    return record;
  } // public Record next()

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void clear() {
    if (nodesFile != null) {
      try {
        nodesFile.close();
        nodesFile = null;
      } catch (IOException ex) {
        Logger.getLogger(SKIResultIterator.class.getName()).log(Level.SEVERE, null, ex);
      }
    }

    if (datasetReader != null) {
      try {
        datasetReader.close();
        datasetReader = null;
      } catch (IOException ex) {
        Logger.getLogger(SKIResultIterator.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
  } // public void clear()
}