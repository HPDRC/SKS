package sks.ski;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import sks.NumericParameter;
import sks.QueryTextPredicate;
import sks.dataset.Dataset;
import sks.rtree.Node;
import sks.rtree.Point;

/**
 * A collection of spatial keyword indexes built on a large database.
 * It overrides the search method to efficiently combine pages of
 * the underlying indexes to answer one k-SB query.
 * 
 * @author acary001
 */
public class BigSpatialKeywordIndex implements Index, Serializable {
  private SpatialKeywordIndex[] skis = null;
  private Node root = null;

  public BigSpatialKeywordIndex() {
  }

  public SpatialKeywordIndex[] getSKIs() {
    return skis;
  }

  public Node getRootNode() {
    return root;
  }

  /**
   *
   * @param skis
   */
  public BigSpatialKeywordIndex(SpatialKeywordIndex[] skis) {
    initializeIndexes(skis);
  }

  public void updateIndex(int index, SpatialKeywordIndex ski) {
    if (skis != null && index < skis.length) {
      skis[index] = ski;
      initializeIndexes(skis);
    }
  }

  public void initializeIndexes(SpatialKeywordIndex[] skis) {
    this.skis = skis;

    if (skis != null) {
      int numFieldCount = 0;
      // Get numeric field count.
      for (int i = 0; i < skis.length; i++) {
        if (skis[i].getRtree().getNumFieldCount() > numFieldCount) {
          numFieldCount = skis[i].getRtree().getNumFieldCount();
        }
      }

      // Construct a new Rtree, allocate new root, as the root of matecategory trees.
      boolean hasNumericFields = (numFieldCount > 0)? true:false;
      root = new Node((short) skis.length, (short) 0, hasNumericFields);

      for (int i = 0; i < skis.length; i++) {
        Node skiRootNode = skis[i].getRtree().getRootNode();
        // References to subtrees.
        root.refs[i] = skiRootNode.ref;
        root.rects[i] = skiRootNode.getMinBoundingRect();

        // Compute root numeric ranges.
        if (hasNumericFields) {
          root.numRanges[i] = skiRootNode.getNumericRange(false, skis[i].getRtree().getNumFieldCount());
        }

        // Adjust root node level.
        if (skis[i].getRtree().height() > root.level) {
          root.level = (short) skis[i].getRtree().height();
        }

        root.size++;
      } // for (int i = 0; i < skis.length; i++)
    } // if (skis != null)
  } // public BigSpatialKeywordIndex(SpatialKeywordIndex[] skis)

  @Override
  public Dataset getDataset() {
    // Assume all dataset are identical.
    if (skis != null && skis.length > 0) {
      return skis[0].getDataset();
    } else {
      return null;
    }
  }

  @Override
  public void resetIoReads() {
    for (int i = 0; skis != null && i < skis.length; i++) {
      skis[i].resetIoReads();
    }
  }

  @Override
  public int getSpatialIoReads() {
    int ioReads = 0;

    for (int i = 0; skis != null && i < skis.length; i++) {
      ioReads += skis[i].getSpatialIoReads();
    }

    return ioReads;
  } // public int getSpatialIoReads()

  @Override
  public int getTextIoReads() {
    int ioReads = 0;

    for (int i = 0; skis != null && i < skis.length; i++) {
      ioReads += skis[i].getTextIoReads();
    }

    return ioReads;
  } // public int getTextIoReads()

  @Override
  public void clear() {
    for (int i = 0; skis != null && i < skis.length; i++) {
      skis[i].clear();
    }
  }

  /**
   * k-NN search algorithm.
   *
   * @param point
   * @param distance
   * @param topK
   * @param numericParams
   * @param queryTextPredicates
   * @param iostats
   * @return an interator of NN objects satisfying the predicates.
   * @throws IOException
   */
  @Override
  public ResultIterator search(Point point, double distance, int topK,
                             ArrayList<NumericParameter> numericParams,
                             ArrayList<QueryTextPredicate> queryTextPredicates,
                             boolean _debug_mode) throws IOException {
    return new BSKIResultIterator(this, point, distance, numericParams,
                   queryTextPredicates, _debug_mode);
  }
}