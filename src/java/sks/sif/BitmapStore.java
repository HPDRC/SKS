package sks.sif;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.RecordManagerOptions;
import jdbm.btree.BTree;
import jdbm.helper.Tuple;
import jdbm.helper.TupleBrowser;
import jdbm.recman.BaseRecordManager;
import jdbm.recman.CacheRecordManager;

/**
 * Manages B+tree and record manager.
 * @author acary001
 */
public class BitmapStore {
  private RecordManager recordManager;
  private BTree bTree;
  private String dbName;
  private String storePath;

  public BitmapStore(String category, String indexPath) {
    this.dbName = category;
    this.storePath = indexPath + category;
  }

  public boolean isStoreOpen() {
    return (recordManager != null && bTree != null);
  }

  public boolean startup() {
    shutdown();

    try {
      Properties props = new Properties();
      props.setProperty(RecordManagerOptions.DISABLE_TRANSACTIONS, "true");
      //props.setProperty(RecordManagerOptions.CACHE_SIZE, "10000");
      //props.setProperty(RecordManagerOptions.CACHE_SIZE, "20000");
      recordManager = RecordManagerFactory.createRecordManager(storePath + "/" + dbName, props);
      long recId = recordManager.getNamedObject(dbName);

      // Try to reload the instance or create a new one if it does not exist.
      if (recId != 0) {
        bTree = BTree.load(recordManager, recId);
      } else if (recId == 0) {
        // B+tree does not exist. Create a new instance.
        // EXP bTree = BTree.createInstance(recordManager, new TermComparator());
        bTree = BTree.createInstance(recordManager, new TermAtSNComparator());
        recordManager.setNamedObject(dbName, bTree.getRecid());
        recordManager.commit();
      }
      
      return isStoreOpen();
    } catch (IOException e) {
      Logger.getLogger(BitmapStore.class.getName()).log(Level.SEVERE, null, e);
      return false;
    }
  } // public boolean startup()
  
  public boolean shutdown() {
    if (recordManager != null) {
      try {
        recordManager.commit();
        recordManager.close();
      } catch (IllegalStateException e) {
        // recman had already been closed.
      } catch (IOException e) {
        return false;
      }
      recordManager = null;
      bTree = null;
    }

    return true;
  } // public boolean shutdown()

  public boolean commit() {
    if (recordManager != null) {
      try {
        recordManager.commit();
      } catch (IOException ex) {
        Logger.getLogger(BitmapStore.class.getName()).log(Level.SEVERE, null, ex);
        return false;
      }
    }

    return true;
  } // public boolean commit()

  public void emptyCache() throws IOException {
    if (recordManager != null) {
      CacheRecordManager cachedTextRecman = (CacheRecordManager) recordManager;
      cachedTextRecman.emptyCache();
    }
  }
  
  /**
   * Returns the number of random I/O operations
   * executed on the spatial inverted file.
   * @return
   */
  public int getIoReads() {
    if (recordManager != null) {
      CacheRecordManager cachedTextRecman = (CacheRecordManager) recordManager;
      if (cachedTextRecman.getRecordManager() instanceof BaseRecordManager) {
        BaseRecordManager brm = (BaseRecordManager) cachedTextRecman.getRecordManager();
        return brm.getIoReads();
      } else {
        return 0;
      }
    } else {
      return 0;
    }
  }
  
  public void resetIoReads() {
    if (recordManager != null) {
      CacheRecordManager cachedTextRecman = (CacheRecordManager) recordManager;
      if (cachedTextRecman.getRecordManager() instanceof BaseRecordManager) {
        BaseRecordManager brm = (BaseRecordManager) cachedTextRecman.getRecordManager();
        brm.resetIoReads();
      }
    }
  }

  /**
   * Inserts a record in the store. It replaces current value if any exists.
   * @param term
   * @param snId
   * @param bm
   * @throws IOException
   */
  public void insert(String term, int snId, SuperNodeBitmap bm) throws IOException {
    TermAtSN termAtSN = new TermAtSN(term, snId);
    bTree.insert(termAtSN, bm, false);
  }

  /**
   * 
   * @param key
   * @return
   * @throws IOException
   */
  public TupleBrowser browse(TermAtSN key) throws IOException {
    if (isStoreOpen()) {
      return bTree.browse((Object) key);
    } else {
      return null;
    }
  }

  public SuperNodeBitmap find(TermAtSN key) throws IOException {
    if (!isStoreOpen()) {
      if (!startup()) {
        return null;
      }
    }

    Object object = bTree.find((Object) key);

    if (object != null && object instanceof SuperNodeBitmap) {
      return ((SuperNodeBitmap) object);
    } else {
      return null;
    }
  }

  public Tuple findGreaterOrEqual(TermAtSN key) throws IOException {
    if (!isStoreOpen()) {
      if (!startup()) {
        return null;
      }
    }

    Tuple tuple = bTree.findGreaterOrEqual((Object) key);
    if (tuple != null && tuple.getKey() instanceof TermAtSN &&
        tuple.getValue() instanceof SuperNodeBitmap) {
      return tuple;
    } else {
      return null;
    }
  } // public Tuple findGreaterOrEqual()

  public void warmUpCache(int levels) throws IOException {
    bTree.warmUpCache(levels);
  }
} // public class BitmapStore