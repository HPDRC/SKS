package sks.ski;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import sks.NumericParameter;
import sks.QueryTextPredicate;
import sks.dataset.Dataset;
import sks.rtree.Point;
import sks.rtree.Rtree;
import sks.sif.SpatialInvertedFile;

/**
 * An efficient data structure for k-NN Boolean queries based on the article:
 * A.~Cary et al. "Efficient and Scalable Method for Processing Top-k Spatial
 * Boolean Queries" in SSDBM 2010.
 * 
 * @author acary001
 */
public class SpatialKeywordIndex implements Index, Serializable {
  static final long serialVersionUID = 1856835860954L;
  private Dataset dataset;
  private Rtree rTree;
  private String lastUpdatedOn;
  private transient SpatialInvertedFile sif;

  // Persists changes in SKI.
  transient private SKIManager skiManager;

  /**
   * 
   * @return
   */
  public SKIManager getSKIManager() {
    return skiManager;
  }

  public Rtree getRtree() {
    return rTree;
  }

  @Override
  public void resetIoReads() {
    if (skiManager != null) {
      skiManager.resetIoReads();
    }

    if (sif != null) {
      sif.getBitmapStore().resetIoReads();
    }
  } // public void resetIoReads()

  @Override
  public int getSpatialIoReads() {
    if (skiManager != null) {
      return skiManager.getIoReads();
    } else {
      return 0;
    }
  }

  @Override
  public int getTextIoReads() {
    if (sif != null) {
      return sif.getBitmapStore().getIoReads();
    } else {
      return 0;
    }
  }

  /**
   * Releases resources.
   */
  @Override
  public void clear() {
    dataset = null;

    if (rTree != null) {
      rTree.emptyStorage();
      rTree = null;
    }

    if (sif != null) {
      sif.shutdownStore();
      sif = null;
    }

    if (skiManager != null) {
      skiManager.clear();
      skiManager = null;
    }
  } // public void clear()

  @Override
  public Dataset getDataset() {
    return dataset;
  }

  public SpatialInvertedFile getSIF() {
    return sif;
  }

  /**
   * Serializes this {@code SpatialKeywordIndex} instance.
   * @param s
   * @serialData The following objects are emitted in sequence:
   *  dataset ({@code Dataset})
   *  rTtree ({@code Rtree})
   *  lastUpdatedOn ({@code String})
   * @throws IOException
   */
  private void writeObject(ObjectOutputStream s) throws IOException {
    // Serialize state.
    s.defaultWriteObject();
    s.writeObject(dataset);
    s.writeObject(rTree);
    s.writeObject(lastUpdatedOn);
  }

  private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
    s.defaultReadObject();
    dataset = (Dataset) s.readObject();
    rTree = (Rtree) s.readObject();
    lastUpdatedOn = (String) s.readObject();
    sif = new SpatialInvertedFile(rTree);
  }

  public SpatialKeywordIndex() { // Default serializers.
  }

  public SpatialKeywordIndex(Dataset dataset, Rtree rTree, SpatialInvertedFile sif) {
    this.dataset = dataset;
    this.rTree = rTree;
    this.sif = sif;
  }

  /**
   * 
   * @param skiManager
   */
  public void setSKIManager(SKIManager skiManager) {
    this.skiManager = skiManager;
  }

  public void setLastUpdatedOn(String lastUpdatedOn) {
    this.lastUpdatedOn = lastUpdatedOn;
  }

  public String getLastUpdatedOn() {
    return lastUpdatedOn;
  }

  /**
   * k-SB query processing algorithm.
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
    return new SKIResultIterator(this, point, distance, numericParams,
                queryTextPredicates, _debug_mode);
  }
} // public class SpatialKeywordIndex