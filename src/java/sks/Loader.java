package sks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import sks.dataset.*;
import sks.rtree.Point;
import sks.rtree.Rtree;
import sks.rtree.NumericRange;
import sks.util.RunOSCommand;
import sks.sif.SpatialInvertedFile;
import sks.ski.SKIManager;
import sks.ski.SpatialKeywordIndex;


/**
 * Loader
 * <p>
 * Load a data set to build index
 *
 * @author Ariel Cary
 * @version $Id: Loader.java,v 1.0 December 28, 2007
 */

public class Loader {
  static final private short NODE_CAPACITY = 80;
  static public final String FIELD_SEPARATOR = "\t";
  static public final int IO_BUFFER_SIZE = 65536;

  private File logFile;
  private Dataset dataset;
  private SpatialInvertedFile sif = null;
  private long recordsProcessed;
  private String loadStage = "";
  private String binStore;
  private String tmpStore;
  private String category;


  /**
   * get the current Date
   */
  public static String getCurrentDate() {
    final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    Calendar cal = Calendar.getInstance();
    SimpleDateFormat d = new SimpleDateFormat(DATE_FORMAT);
    return d.format(cal.getTime());
  }

   /**
   * set the Load Stage
   */
  public void setLoadStage(String loadStage) {
    this.loadStage = loadStage;
  }
  
  /**
   * get the Load Stage
   */
  public String getLoadStage() {
    return loadStage;
  }

  /**
   * Loader class
   */
  public Loader(File datasetFile, File headerFile, File logFile,
          String category, String binStore, String tmpStore) throws IOException {
    dataset = new Dataset(datasetFile, headerFile);
    this.logFile = logFile;
    this.category = category;
    this.binStore = binStore;
    this.tmpStore = tmpStore;
  }

  /**
   * main function for data set load
   * @throws IOException
   * @throws Exception
   */
  public boolean load() throws IOException, Exception {
    ArrayList<Integer> numFieldIndexes = dataset.getSchema().getNumberFieldIndexes();
    int numFieldCount = numFieldIndexes.size();
    Rtree rTree = new Rtree(NODE_CAPACITY, 0.5f, numFieldCount);
    DatasetReader reader = new DatasetReader(dataset);
    BufferedWriter logWriter = new BufferedWriter(new FileWriter(logFile));

    // Build R-tree with augmented min/max bins.
    loadStage = "RT";
    recordsProcessed = 0;
    
    while (true) {
      try {
        // Read record without text parsing.
        Record rec = reader.readRecord(false, true);
        if (rec == null) {
          break;
        }
        
        Point p = new Point((float) rec.getLongitude(), (float) rec.getLatitude());
        double[] numericValues = rec.getNumericValues();
        NumericRange numRange = null;

        if (numericValues != null) {
          numRange = new NumericRange();
          numRange.initializeBound(numericValues);
        }
        
        // Index record.
        rTree.insert(rec.getReference(), p, numRange);
        //output to webpage
        if (recordsProcessed > 0 && recordsProcessed % 1000000 == 0) {
          logWriter.write(Loader.getCurrentDate() + " records processed: " + recordsProcessed);
          logWriter.newLine();
        }
      } catch (MalformedRecordException ex) {
        logWriter.write(Loader.getCurrentDate() + " Line " + (recordsProcessed + 1) + ": " + ex.getMessage());
        logWriter.newLine();
      }

      recordsProcessed++;
    }

    // Generate <documentId, nodeId> file. The dnode file
    reader.close();
    File docNodeFile = new File(tmpStore + category + "/" + category + ".dnode");
    loadStage = "RT-dmpObjNodeId";
    rTree.dumpDocumentNodeIds(docNodeFile);

    // Persist R-tree and release memory.
    SKIManager skiManager = new SKIManager(tmpStore, tmpStore, category, true);
    loadStage = "RT-wNode";
    skiManager.writeNodes(rTree);
    rTree.emptyStorage();
    System.gc();

    // Platform information.
    String platformInfo = System.getProperty("os.name") + "\t" +
            System.getProperty("os.version") + "\t" +
            System.getProperty("os.arch");


    logWriter.write("Platform:\t" + platformInfo);
    logWriter.newLine();
    String osScriptSuffix = ".sh";

    if (platformInfo.toLowerCase().contains("windows")) {
      osScriptSuffix = ".bat";
    }

    // Sort docNode file by documentId.
    String sortCmd[] = {binStore + "sort_dnode" + osScriptSuffix,
                        docNodeFile.getCanonicalPath()};
    RunOSCommand runCmd = null;
    runCmd = new sks.util.RunOSCommand();
    int exitValue = 0;
    loadStage = "RT-srtObjNode";

    if ((exitValue = runCmd.run(sortCmd)) > 0) {
      // TODO: Close file streams.
      String cmdMessage = sortCmd[0] + " " + sortCmd[1];
      logWriter.write(Loader.getCurrentDate() + " OS Error " + exitValue +
              ", while executing OS command: " + cmdMessage);
      logWriter.newLine();
      logWriter.close();
      throw new Exception("OS Error " + exitValue + ", while executing OS command 1.");
    }

    // Generate forward index.
    runCmd = null;
    File forwardIndexFile = new File(tmpStore + category + "/" + category + ".fidx");
    File forwardIndexFile0 = new File(tmpStore + category + "/" + category + ".fidx0");
    loadStage = "SIF-fIndex";

    if (!buildForwardIndex(forwardIndexFile, forwardIndexFile0)) {
      throw new Exception("Error while generating forward index.");
    }

    // Generate spatial inverted file.
    // Augment fidx file with references in dnode file.
    File spatialInvertedTextFile = new File(tmpStore + category + "/" + category + ".text");
    //String joinCmd[] = {binStore + "join_dnode_fidx.bat",
    String joinCmd[] = {binStore + "join_dnode_fidx" + osScriptSuffix,
                        docNodeFile.getCanonicalPath(),
                        forwardIndexFile.getCanonicalPath(),
                        spatialInvertedTextFile.getCanonicalPath()};

    runCmd = new RunOSCommand();
    exitValue = 0;
    loadStage = "SIF-jObjNodeFIndex";

    if ((exitValue = runCmd.run(joinCmd)) > 0) {
      String cmdMessage = sortCmd[0] + " " + sortCmd[1] + " " + sortCmd[2] + " " + sortCmd[3];
      logWriter.write(Loader.getCurrentDate() + " OS Error " + exitValue +
              ", while executing OS command: " + cmdMessage);

      logWriter.write(Loader.getCurrentDate() + " OS Error " +
              exitValue + ", while executing OS command: " + cmdMessage);
      logWriter.newLine();
      logWriter.close();
      throw new Exception("OS Error " + exitValue + ", while executing OS command: join_dnode_fidx.");
    }

    // Remove intermediate files: .dnode, .fidx
    runCmd = null;
    // EXP docNodeFile.delete();
    forwardIndexFile.delete();
    forwardIndexFile0.delete();
    docNodeFile = null;
    forwardIndexFile = null;
    forwardIndexFile0 = null;

    // Build term bitmaps.
    loadStage = "SIF";
    sif = new SpatialInvertedFile(category, tmpStore, rTree);

    // DEBUG
    logWriter.write("Building Bitmap Store for category = " + category);
    logWriter.newLine();
    logWriter.write("Temporary directory: " + tmpStore);
    logWriter.newLine();

    if (!sif.buildTermBitmaps(spatialInvertedTextFile)) {
      // TODO: Close file streams.
      logWriter.write(Loader.getCurrentDate() + " Error while building term bitmaps.");
      logWriter.newLine();
      logWriter.close();
      throw new Exception("Error while building term bitmaps.");
    }

    // Write SKI structure, timestamp, and Dataset.
    String indexTimeStamp = getCurrentDate();
    SpatialKeywordIndex ski = new SpatialKeywordIndex(dataset, rTree, sif);
    ski.setLastUpdatedOn(indexTimeStamp);
    loadStage = "wSKI";
    skiManager.writeSKI(ski);
    sif = null;

    logWriter.write(Loader.getCurrentDate() + " MSG: Index timestamped at " + indexTimeStamp);
    logWriter.newLine();
    logWriter.close();
    ski.clear();
    ski = null;
    loadStage = "mvSKI";
    return true;
  } // public boolean load()

  public long getRecordsProcessed() {
    if (sif == null) {
      return recordsProcessed;
    } else {
      return sif.getRecordsProcessed();
    }
  }

  /**
   * Builds database forward index.
   * @param forwardIndexFile
   * @return
   * @throws IOException
   */
  private boolean buildForwardIndex(File forwardIndexFile,
          File forwardIndexFile0) throws IOException {
    if (dataset == null) {
      // "createForwardIndex(File): cannot build fidx. dataset is null";
      return false;
    }

    recordsProcessed = 0;
    DatasetReader reader = new DatasetReader(dataset);
    Writer output = new BufferedWriter(new FileWriter(forwardIndexFile), Loader.IO_BUFFER_SIZE);
    Writer output0 = new BufferedWriter(new FileWriter(forwardIndexFile0), Loader.IO_BUFFER_SIZE);
    ArrayList<Integer> textFieldIndexes = dataset.getSchema().getTextFieldIndexes();
    int textFieldCount = 0;
    
    if (textFieldIndexes != null) {
      textFieldCount = textFieldIndexes.size();
    }

    try {
      while (textFieldCount > 0) {
        try {
          Record rec = reader.readRecord(true, true); // TODO: parse numbers to index.

          if (rec == null) {
            break;
          }

          // Document vocabulary.
          HashSet <String> docTerms = new java.util.HashSet(100);
          long docRef = rec.getReference();
          String[] textValues = rec.getTextValues();
          
          for (int i = 0; textValues != null && i < textValues.length; i++) {
            int index = textFieldIndexes.get(i);
            String[] terms = textValues[i].split(" ", -1);

            for (String term : terms) {
              // Add unique terms into the forward index.
              if (!docTerms.contains(term + "@" + index)) {
                docTerms.add(term + "@" + index);

                if (term.length() > 0) {
                  // write: "docRef term fieldNbr"
                  output.write(docRef + FIELD_SEPARATOR + // docId
                               term + FIELD_SEPARATOR + // term
                               index + "\n"); // fieldNbr
                } else {
                  // write: "docRef term fieldNbr"
                  output0.write(docRef + FIELD_SEPARATOR + // docId
                               term + FIELD_SEPARATOR + // term
                               index + "\n"); // fieldNbr
                } // if (term.length() > 0)
              } // if (!docTerms.contains(term + "@" + index))
            } // for (String term : terms)
          }

          docTerms.clear();
          docTerms = null;

          // Index numeric null values.
          double[] numericValues = rec.getNumericValues();
          ArrayList<Integer> numericFieldIndexes = dataset.getSchema().getNumberFieldIndexes();

          for (int i = 0; numericValues != null && i < numericValues.length; i++) {
            int index = numericFieldIndexes.get(i);

            if (numericValues[i] == 0) {
              // write: "docRef term fieldNbr"
              output0.write(docRef + FIELD_SEPARATOR + // docId
                           "" + FIELD_SEPARATOR + // term
                           index + "\n"); // fieldNbr
            }
          }
        } catch (MalformedRecordException ex) {
          // Ignore document.
        }

        recordsProcessed++;
      } // while (true)
    } finally {
      output.close();
      output0.close();
      reader.close();
    }
    
    return true;
  } // private boolean buildForwardIndex(

} // class Loader