package sks.ski;

import java.io.IOException;
import java.util.ArrayList;
import sks.NumericParameter;
import sks.QueryTextPredicate;
import sks.dataset.Dataset;
import sks.rtree.Point;

/**
 *
 * @author acary001
 */
public interface Index {
  public Dataset getDataset();

  public void resetIoReads();

  /**
   * 
   * @param point
   * @param distance
   * @param topK
   * @param numericParams
   * @param queryTextPredicates
   * @param _debug_mode
   * @return
   * @throws IOException
   */
  public ResultIterator search(Point point, double distance, int topK,
          ArrayList<NumericParameter> numericParams,
          ArrayList<QueryTextPredicate> queryTextPredicates,
          boolean _debug_mode) throws IOException;

  public int getSpatialIoReads();

  public int getTextIoReads();

  public void clear();
} // public interface Index