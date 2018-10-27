package sks.ski;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import sks.rtree.*;
import java.util.Hashtable;
import java.util.Iterator;
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.channels.FileChannel;
import sks.Loader;
import sks.dataset.Dataset;
import sks.sif.SpatialInvertedFile;

public class SKIManager {
  private String dataPath;
  private String indexPath;
  private String category;
  private String dataFilename;
  private String headerFilename;
  private String skiFilename;
  private String nodesFilename;
  private String mapFilename;
  private String tBitmapFilename;
  // private String logFilename;
  
  // Map: k=nodeId, value=<offset, size>.
  private transient Hashtable<Long, Pair<Long, Integer>> nodesMap = new Hashtable<Long, Pair<Long, Integer>>();

  // IO stats
  private transient int _io_reads = 0;

  static public final String DATA_SUFFIX = ".asc";
  static public final String HEADER_SUFFIX = ".asc.header";
  static public final String SKI_SUFFIX = ".index";
  static public final String MTC_PREDICATES = ".mtc_preds";
  static public final String RTREE_NODES_SUFFIX = ".rtn";
  static public final String RTREE_NODE_MAP_SUFFIX = ".rtm";
  static public final String DB_SUFFIX = ".db";
  static public final String LOG_SUFFIX = ".log";
  static public final String TEMP_SUFFIX = ".tmp";

  public String getNodesFilename() {
    return nodesFilename;
  }

  public int getIoReads() {
    return _io_reads;
  }

  public void resetIoReads() {
    _io_reads = 0;
  }
  
  public void clear() {
    if (nodesMap != null) {
      nodesMap.clear();
      nodesMap = null;
    }
  }

  /**
   * Serializes SKI data structure.
   * @param dataPath
   * @param indexPath
   * @param category
   * @param temporaryFiles used only during constructions.
   */
  public SKIManager(String dataPath, String indexPath, String category, boolean temporaryFiles) {
    this.dataPath = dataPath;
    this.indexPath = indexPath;
    this.category = category;
    dataFilename = dataPath + category + "/" + category + DATA_SUFFIX;
    headerFilename = dataPath + category + "/" + category + HEADER_SUFFIX;
    skiFilename = indexPath + category + "/" + category + SKI_SUFFIX;
    nodesFilename = indexPath + category + "/" + category + RTREE_NODES_SUFFIX;
    tBitmapFilename = indexPath + category + "/" + category + DB_SUFFIX;
    mapFilename = indexPath + category + "/" + category + RTREE_NODE_MAP_SUFFIX;
    //logFilename = indexPath + category + "/" + category + LOG_SUFFIX;

    if (temporaryFiles) {
      dataFilename += TEMP_SUFFIX;
      headerFilename += TEMP_SUFFIX;
      skiFilename += TEMP_SUFFIX;
      nodesFilename += TEMP_SUFFIX;
      //tBitmapFilename += TEMP_SUFFIX;
      mapFilename += TEMP_SUFFIX;
      //logFilename += TEMP_SUFFIX;

      // Create temporary directories if necessary.
      if (!(new File(dataPath + category)).exists()) {
        boolean success = (new File(dataPath + category)).mkdirs();
        if (!success) {
          // Directory creation failed!
        }
      }

      if (!(new File(indexPath + category)).exists()) {
        boolean success = (new File(indexPath + category)).mkdirs();
        if (!success) {
          // Directory creation failed!
        }
      }
    } // if (temporaryFiles)
  } // public SKIManager()

  /**
   * Serializes R-tree nodes in no particular order.
   * @param nodes
   */
  public boolean writeNodes(Rtree rTree) {
    // Delete previous files if any.
    if ((new File(nodesFilename)).exists()) {
      (new File(nodesFilename)).delete();
    }

    if ((new File(mapFilename)).exists()) {
      (new File(mapFilename)).delete();
    }

    RandomAccessFile nodesFile = null;

    try {
      nodesFile = new RandomAccessFile(nodesFilename, "rw");
    } catch(IOException ex) {
      ex.printStackTrace();
      return false;
    }

    Hashtable<Long, Node> nodes = rTree.getNodesInStorage();
    Iterator<Long> keyIndex = nodes.keySet().iterator();
    Hashtable<Long, Pair<Long, Integer>> nodesMap = new Hashtable<Long, Pair<Long, Integer>>();
    long fileLen = 0;

    while(keyIndex.hasNext()) {
      Long tmpNodeRef = keyIndex.next();
      Node tmpNode = nodes.get(tmpNodeRef);
      byte[] nodeInBytes = this.nodeToBytes(tmpNode);
      
      try {
        fileLen = nodesFile.length();
        //nodesFile.setLength(fileLen + nodeInBytes.length); // needed if file is written sequentially?
        //nodesFile.seek(fileLen); // needed if file is written sequentially?
        nodesFile.write(nodeInBytes);
        Pair<Long, Integer> p = new Pair<Long, Integer>((long) fileLen, nodeInBytes.length);
        nodesMap.put(tmpNodeRef, p);
      } catch(IOException ex) {
        ex.printStackTrace();
      }
    }

    try {
      nodesFile.close();
    } catch(IOException ex) {
      ex.printStackTrace();
      return false;
    }

    // Persist node map.
    FileOutputStream fos = null;
    ObjectOutputStream os = null;

    try {
      fos = new FileOutputStream(mapFilename);
      os = new ObjectOutputStream(fos);
      os.writeObject(nodesMap);
      os.close();
    } catch(IOException ex) {
      ex.printStackTrace();
      return false;
    }

    nodesMap.clear();
    nodesMap = null;
    
    return true;
  } // public boolean writeNodes()

  private byte[] nodeToBytes(Node node) {
    ByteArrayOutputStream bos = null;
    ObjectOutputStream os = null;

    try {
      bos = new ByteArrayOutputStream();
      os = new ObjectOutputStream(bos);
      os.writeObject(node);
    } catch(IOException ex) {
      ex.printStackTrace();
    }

    return bos.toByteArray();
  }

  private Node bytesToNode(byte[] srcBytes) {
    Node tmpNode = null;
    ByteArrayInputStream bis = null;
    ObjectInputStream is = null;

    try {
      bis = new ByteArrayInputStream(srcBytes);
      is = new ObjectInputStream(bis);
      tmpNode = (Node)is.readObject();
      is.close();
      bis.close();
    } catch(IOException ex) {
      ex.printStackTrace();
    } catch(ClassNotFoundException ex) {
      ex.printStackTrace();
    }

    bis = null;
    is = null;
    return tmpNode;
  }

  /**
   * Loads node map into memory.
   * @return
   */
  public boolean loadNodeMap() {
    FileInputStream fis = null;
    ObjectInputStream is = null;

    try {
      fis = new FileInputStream(mapFilename);
      is = new ObjectInputStream(fis);
      nodesMap = (Hashtable<Long, Pair<Long, Integer>>) is.readObject();
      is.close();
      fis.close();
    } catch(IOException ex) {
      ex.printStackTrace();
      return false;
    } catch(ClassNotFoundException ex) {
      ex.printStackTrace();
      return false;
    }

    return true;
  } // public boolean loadNodeMap()

  /**
   * Reads an R-tree node from persistent storage.
   * nodesMap must have been loaded before this function is invoked.
   * @param ref node Id.
   * @return requested R-tree node.
   */
  public Node readNode(long ref, RandomAccessFile nodesFH) {
    long offset = nodesMap.get(ref).getLeft();
    byte[] nodeInBytes = null;
    long startTimeNOD = 0, elaTimeNOD = 0; // DEBUG
    
    try {
      startTimeNOD = System.nanoTime();
      nodeInBytes = new byte[this.nodesMap.get(ref).getRight()];

      if (nodesFH == null) {
        RandomAccessFile nodesFile = new RandomAccessFile(nodesFilename, "r");
        nodesFile.seek(offset);
        nodesFile.read(nodeInBytes);
        nodesFile.close();
        nodesFile = null;
      } else {
        nodesFH.seek(offset);
        nodesFH.read(nodeInBytes);
      }

      _io_reads++;
      elaTimeNOD = System.nanoTime() - startTimeNOD;
    } catch(IOException ex) {
      ex.printStackTrace();
      return null;
    }

    Node node = bytesToNode(nodeInBytes);
    return node;
  }

  /**
   *
   * @param ski
   * @return
   */
  public boolean writeSKI(SpatialKeywordIndex ski) {
    if ((new File(skiFilename)).exists()) {
      new File(skiFilename).delete();
    }

    FileOutputStream fo = null;
    ObjectOutputStream oo = null;

    try {
      fo = new FileOutputStream(skiFilename);
      oo = new ObjectOutputStream(fo);
      oo.writeObject(ski);
      oo.close();
    } catch(IOException ex) {
      ex.printStackTrace();
      return false;
    }

    fo = null;
    oo = null;
    return true;
  }

  /**
   *
   * @return
   */
  public SpatialKeywordIndex readSKI() {
    SpatialKeywordIndex ski = null;
    FileInputStream fi = null;
    ObjectInputStream oi = null;

    try {
      fi = new FileInputStream(skiFilename);
      oi = new ObjectInputStream(fi);
      ski = (SpatialKeywordIndex) oi.readObject();
      oi.close();
      fi = null;
      oi = null;
    } catch(IOException ex) {
      //ex.printStackTrace();
      return null;
    } catch(ClassNotFoundException ex) {
      //ex.printStackTrace();
      return null;
    }

    // Set data files.
    Dataset dataset = ski.getDataset();
    dataset.setDataFile(dataPath + category + "/" + category + DATA_SUFFIX);
    dataset.setHeaderFile(dataPath + category + "/" + category + HEADER_SUFFIX);

    // Set bitmap store file.
    SpatialInvertedFile sif = ski.getSIF();
    sif.setBitmapStore(category, indexPath);
    ski.setSKIManager(this);
    
    return ski;
  } // public SpatialKeywordIndex readSKI()

  /**
   *
   * @param filename
   * @return
   */
  private boolean deleteFile(String filename) {
    File f = new File(filename);
    if (f.exists()) {
      return f.delete();
    }
    return true;
  }

  /**
   *
   * @param oldname
   * @param newname
   * @return
   */
  private boolean renameFile(String oldname, String newname) {
    // File (or directory) with old name
    File file = new File(oldname);

    // File (or directory) with new name
    File file2 = new File(newname);

    if (file2.getParentFile() == null ||
       (!file2.getParentFile().exists() && !file2.getParentFile().mkdir() )) {
      return false;
    }

    // Rename file.
    return (file.renameTo(file2));
  }

  public static boolean copyFile(File sourceFile, File destFile) throws IOException {
    if (destFile.getParentFile() == null ||
       (!destFile.getParentFile().exists() && !destFile.getParentFile().mkdir() )) {
      return false;
    }

    if (!destFile.exists()) {
      destFile.createNewFile();
    }

    FileChannel source = null;
    FileChannel destination = null;
    boolean success = true;

    try {
      source = new FileInputStream(sourceFile).getChannel();
      destination = new FileOutputStream(destFile).getChannel();
      destination.transferFrom(source, 0, source.size());
    } catch (IOException e) {
      success = false;
    } finally {
      if (source != null) {
        source.close();
      }
      if (destination != null) {
        destination.close();
      }
    }

    return success;
  }

  /**
   *
   * @param newDataPath
   * @param newIndexPath
   * @param logFile
   * @return
   * @throws IOException
   */
  public boolean moveSKIfiles(String newDataPath, String newIndexPath, File logFile) throws IOException {
    final String BACKUP_SUFFIX = ".bkp";
    final String newFiles[] = {newDataPath + category + "/" + category + DATA_SUFFIX,
                               newDataPath + category + "/" + category + HEADER_SUFFIX,
                               newIndexPath + category + "/" + category + SKI_SUFFIX,
                               newIndexPath + category + "/" + category + RTREE_NODES_SUFFIX,
                               newIndexPath + category + "/" + category + RTREE_NODE_MAP_SUFFIX,
                               newIndexPath + category + "/" + category + DB_SUFFIX
                              };

    final String thisFiles[] = {dataFilename,
                                headerFilename,
                                skiFilename,
                                nodesFilename,
                                mapFilename,
                                tBitmapFilename
                               };

    String newLogFile = newDataPath + category + "/" + category + LOG_SUFFIX;

    // Open error logging file.
    BufferedWriter errorWriter = new BufferedWriter(new FileWriter(logFile, true));
    errorWriter.write(Loader.getCurrentDate() + " MSG: Starting file switch.");
    errorWriter.newLine();
    
    // Remove any existing backup files.
    errorWriter.write(Loader.getCurrentDate() + " MSG: Removing old backup files.");
    errorWriter.newLine();
    for (int i = 0;  i < newFiles.length; i++) {
      deleteFile(newFiles[i] + BACKUP_SUFFIX);
    }
    
    // Check if all new files exist.
    boolean newFilesExist = true;
    for (int i = 0; i < newFiles.length; i++) {
      if (!(new File(newFiles[i])).exists()) {
        newFilesExist = false;
        break;
      }
    }
    
    if (newFilesExist) {
      // Backup existing new files.
      errorWriter.write(Loader.getCurrentDate() + " MSG: Backing up current index.");
      errorWriter.newLine();

      boolean isBackupComplete = true;
      for (int i = 0; i < newFiles.length; i++) {
        //if (renameFile(newFiles[i], newFiles[i] + BACKUP_SUFFIX)) {
        if (copyFile(new File(newFiles[i]), new File(newFiles[i] + BACKUP_SUFFIX))) {
          errorWriter.write(Loader.getCurrentDate() + " MSG: File '" + newFiles[i] + "' backed up.");
          errorWriter.newLine();
        } else {
          isBackupComplete = false;
          errorWriter.write(Loader.getCurrentDate() + " ERR: Unable to backup file '" +
                            newFiles[i] + "' in '" + newFiles[i] + BACKUP_SUFFIX + "'");
          errorWriter.newLine();
          break;
        }
      }

      if (!isBackupComplete) {
        // Restore files.
        errorWriter.write(Loader.getCurrentDate() + " MSG: Restoring original files and exiting process.");
        errorWriter.newLine();
        for (int i = 0; i < newFiles.length; i++) {
          //if (renameFile(newFiles[i] + BACKUP_SUFFIX, newFiles[i])) {
          if (copyFile(new File(newFiles[i] + BACKUP_SUFFIX), new File(newFiles[i]))) {
            errorWriter.write(Loader.getCurrentDate() + " MSG: Restored '" + newFiles[i] + "'");
            errorWriter.newLine();
          }
        }

        errorWriter.close();
        deleteFile(newLogFile);
        //renameFile(logFile.getPath(), newLogFile);  // error file.
        copyFile(new File(logFile.getPath()), new File(newLogFile));  // error file.
        return false;
      } else {
        errorWriter.write(Loader.getCurrentDate() + " MSG: File backup is complete.");
        errorWriter.newLine();
      }
    } else { // if (newFilesExist)
      // Delete any existing new file.
      for (int i = 0;  i < newFiles.length; i++) {
        deleteFile(newFiles[i]);
      }
    }

    errorWriter.write(Loader.getCurrentDate() + " MSG: Replacing data and index files now.");
    errorWriter.newLine();
    boolean replaceSucceeded = true;
    
    if (thisFiles.length == newFiles.length) {
      for (int i = 0;  i < thisFiles.length; i++) {
        //if (renameFile(thisFiles[i], newFiles[i])) {
        if (copyFile(new File(thisFiles[i]), new File(newFiles[i]))) {
          errorWriter.write(Loader.getCurrentDate() + " MSG: Replaced file '" + newFiles[i] + "'");
          errorWriter.newLine();
        } else {
          errorWriter.write(Loader.getCurrentDate() + " ERR: Could not replace file '" + thisFiles[i] + "' into '" + newFiles[i] + "'");
          errorWriter.newLine();
          replaceSucceeded = false;
          break;
        }
      }
    } else {
      replaceSucceeded = false;
    }

    if (replaceSucceeded) {
      // SKS index is fresh and consistent.
      errorWriter.write(Loader.getCurrentDate() + " MSG: Index is fresh.");
      errorWriter.newLine();
      errorWriter.write(Loader.getCurrentDate() + " MSG: Ending file switch successfully.");
      errorWriter.newLine();

      // Remove backup files.
      for (int i = 0;  i < newFiles.length; i++) {
        deleteFile(newFiles[i] + BACKUP_SUFFIX);
      }

      // Remove old files.
      for (int i = 0;  i < thisFiles.length; i++) {
        deleteFile(thisFiles[i]);
      }

      errorWriter.close();
      deleteFile(newLogFile);
      //renameFile(logFile.getPath(), newLogFile);  // log file.
      copyFile(new File(logFile.getPath()), new File(newLogFile));  // log file.
      deleteFile(logFile.getPath());
      return true;
    } else {
      // Error while renaming tmp files into NEW files.
      errorWriter.write(Loader.getCurrentDate() + " ERR: Error while superseding files. Rolling changes back.");
      errorWriter.newLine();

      // Move tmp files back to their original place.
      for (int i = 0;  i < newFiles.length; i++) {
        //renameFile(newFiles[i], thisFiles[i]);
        copyFile(new File(newFiles[i]), new File(thisFiles[i]));
      }
      
      // Recover new files from backup.
      for (int i = 0;  i < newFiles.length; i++) {
        //if (renameFile(newFiles[i] + BACKUP_SUFFIX, newFiles[i])) {
        if (copyFile(new File(newFiles[i] + BACKUP_SUFFIX), new File(newFiles[i]))) {
          errorWriter.write(Loader.getCurrentDate() + " MSG: '" + newFiles[i] + "' rolled back from backup.");
          errorWriter.newLine();
        } else {
          errorWriter.write(Loader.getCurrentDate() + " ERR: '" + newFiles[i] + "' could not be recovered from backup.");
          errorWriter.newLine();
        }
      }

      errorWriter.write(Loader.getCurrentDate() + " ERR: Ending file switch with errors");
      errorWriter.newLine();
    } // if (replaceSucceeded)

    errorWriter.close();
    deleteFile(newLogFile);
    //renameFile(logFile.getPath(), newLogFile);  // error file.
    copyFile(new File(logFile.getPath()), new File(newLogFile));  // error file.
    return false;
  } // public boolean moveSKIfiles()

  public void loadNodesInStorage(VolatileNodeStorage storage, Node node,
          int levelsToReload) {
    if (levelsToReload < 2 || node == null || node.level == 0) {
      return; // Nothing to do.
    }

    // Reload upper level nodes in depth-first order.
    for (int i = 0; i < node.size; i++) {
      Node childNode = readNode(node.refs[i], null);
      storage.writeNode(node.refs[i], childNode);

      if (node.level > 0) {
        loadNodesInStorage(storage, childNode, levelsToReload - 1);
      }
    }
  } // public void loadNodesInStorage(
} // public class SKIManager