/*
 * SpatialInvertedFile.java
 *
 * Created on May 17, 2008, 11:34 AM
 *
 * This class implements a spatial inverted index as defined in the SKI paper.
 * 
 */

package sks.sif;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import jdbm.helper.Tuple;
import jdbm.helper.TupleBrowser;
import sks.QueryTextPredicate;
import sks.ComparisonOperator;
import sks.Loader;
import sks.rtree.Rtree;
import sks.rtree.SuperNodeBoundary;
import sks.ski.SNInterval;

/**
 *
 * @author acary001
 */

public class SpatialInvertedFile {
  // Term bitmap store.
  private BitmapStore bitmapStore;

  // R-Tree dependent fields.
  private final int M;
  private final int SUPER_NODE_SIZE;
  private Hashtable<Integer, SuperNodeBoundary> snNodeBoundaries = null;
  private final CBitmap CBM_ALL_BITS_SET;
  
  // Progress counter.
  transient private long recordsProcessed;

  /**
   * 
   * @return
   */
  public BitmapStore getBitmapStore() {
    return bitmapStore;
  }

  /**
   * 
   * @param snId
   * @return
   */
  private boolean isSNValid(int snId) {
    if (snNodeBoundaries != null) {
      if (snNodeBoundaries.containsKey(snId)) {
        return true;
      } else {
        return false;
      }
    } else {
      return true;
    }
  }

  /**
   * Eliminates set bits that are outside the boundaries of actual leaf nodes
   * inside a super node.
   * @param snId
   * @param cBitmap
   * @return
   */
  private CBitmap cleanseSNBitmap(int snId, BitSet bs) {
    if (snNodeBoundaries == null || snNodeBoundaries.get(snId) == null) {
      return new CBitmap(bs);
    }

    SuperNodeBoundary snBoundary = snNodeBoundaries.get(snId);

    for (int i = 0; i < snBoundary.length(); i++) {
      if (snBoundary.get(i) == M) {
        // Node is full.
        continue;
      }
      
      int from = i * M + snBoundary.get(i);
      int to = (i + 1) * M;
      bs.clear(from, to);
    }

    int from = (snBoundary.length() - 1) * M + snBoundary.get((snBoundary.length() - 1));
    bs.clear(from, SUPER_NODE_SIZE);

    if (bs.cardinality() == 0) {
      return null;
    } else {
      return new CBitmap(bs);
    }
  } // private CBitmap cleanseSNBitmap()

  // DEBUG
  public SpatialInvertedFile() {
    M = 80;
    SUPER_NODE_SIZE = M * M;
    bitmapStore = null;
    BitSet allSetBS = new BitSet(SUPER_NODE_SIZE);
    allSetBS.set(0, SUPER_NODE_SIZE);
    CBM_ALL_BITS_SET = new CBitmap(allSetBS);
  }

  /**
   * Creates a new instance of SpatialInvertedFile.
   *
   * @param dataset
   * @param category
   * @param indexPath
   * @param M
   */
  public SpatialInvertedFile(String category, String indexPath, Rtree rTree) throws IOException {
    this(rTree);
    bitmapStore = new BitmapStore(category, indexPath);
  }
  
  public SpatialInvertedFile(Rtree rTree) throws IOException {
    M = rTree.getMaxCapacity();
    SUPER_NODE_SIZE = M * M;
    recordsProcessed = 0;
    bitmapStore = null;
    snNodeBoundaries = rTree.getSNNodeBoundaries();
    
    BitSet allSetBS = new BitSet(SUPER_NODE_SIZE);
    allSetBS.set(0, SUPER_NODE_SIZE);
    CBM_ALL_BITS_SET = new CBitmap(allSetBS);
  }

  public void setBitmapStore(String category, String indexPath) {
    if (bitmapStore != null) {
      bitmapStore.shutdown();
      bitmapStore = null;
    }

    bitmapStore = new BitmapStore(category, indexPath);
  }
  
  public long getRecordsProcessed() {
    return recordsProcessed;
  }

  // DEBUG
//  public static void main(String[] args) {
//    SpatialInvertedFile sif = new SpatialInvertedFile();
//    sif.bitmapStore = new BitmapStore("Street_segment_Streets-p4", "G:/sksC2/tmp/");
//
//    try {
//      sif.buildTermBitmaps(new File("G:/sksC2/tmp/Street_segment_Streets-p4/Street_segment_Streets-p4.text"));
//    } catch (FileNotFoundException ex) {
//      Logger.getLogger(SpatialInvertedFile.class.getName()).log(Level.SEVERE, null, ex);
//    } catch (IOException ex) {
//      Logger.getLogger(SpatialInvertedFile.class.getName()).log(Level.SEVERE, null, ex);
//    }
//  }

  /**
   * Builds term bitmaps and persists them in a B-Tree: <key="term superNodeId", value=termBitmap>.
   *
   * @param spatialInvertedIndexFile
   * @return Boolean state of the process outcome.
   * @throws java.io.FileNotFoundException
   * @throws java.io.IOException
   */
  public boolean buildTermBitmaps(File spatialInvertedIndexFile) throws FileNotFoundException, IOException {
    // Check if the bitmap store is open.
    if (bitmapStore != null && !bitmapStore.startup()) {
      return false;
    }
    
    // counts the number of term bitmaps built so far.
    recordsProcessed = 0; //buildStage = "siidx_bld_tsnbm";

    String line;
    BufferedReader input = null;

    try {
      try {
        // Larger commit frequency values require more RAM.
        final int COMMIT_FREQUENCY = 200000;
        input = new BufferedReader(new FileReader(spatialInvertedIndexFile), Loader.IO_BUFFER_SIZE);

        String term = null, currTerm = null;
        //long currEntryId = 0;
        long currEntryId = 0;
        int superNodeId = 0;
        int superNodeIdCount = 0;
        Short fieldNbr = null;
        Hashtable<Short, BitSet> termSNbs = new Hashtable<Short, BitSet>();
        long firstEntryId = 0;
        int nextBitToSet = 0;

        while ((line = input.readLine()) != null) {
          // line format is: "term:String FS fieldNbr:Number FS nodeId:Number"
          String fields[] = line.split(Loader.FIELD_SEPARATOR);

          // Get term and fieldNbr.
          currTerm = fields[0];
          fieldNbr = new Short(fields[1]);

          // Get entry Id.
          currEntryId = Long.valueOf(fields[2]);

          // Calculate next bit to set.
          nextBitToSet = (int) (currEntryId - firstEntryId);

          if (term != null && term.equals(currTerm) &&
              superNodeId == (currEntryId / SUPER_NODE_SIZE)) {
            // term continuation
            if (termSNbs.get(fieldNbr) == null) {
              termSNbs.put(fieldNbr, new BitSet(SUPER_NODE_SIZE));
            }

            // Set bit in the corresponding nodeId.
            termSNbs.get(fieldNbr).set(nextBitToSet);
          } else { // term or super node or both switch has occurred.
            // Check term switch
            if (term != null && !term.equals(currTerm)) {
              // Term switch
              superNodeIdCount = 0;
              recordsProcessed++;
            }

            // Store term bitmap at super node.
            if (term != null && termSNbs.size() > 0) {
              SuperNodeBitmap termBitmap = new SuperNodeBitmap(termSNbs);

              // Insert pair ("term snId", termBitmap).
              if (bitmapStore != null) {
                bitmapStore.insert(term, superNodeId, termBitmap);
              }
            }
            
            if (recordsProcessed > 0 && recordsProcessed%COMMIT_FREQUENCY == 0) {
              // Commit changes to the database.
              if (bitmapStore != null) {
                bitmapStore.commit();
              }
            }

            term = currTerm;
            superNodeId = (int) (currEntryId / SUPER_NODE_SIZE);
            superNodeIdCount++;
            firstEntryId = superNodeId * SUPER_NODE_SIZE;

            // calculate next bit to set.
            nextBitToSet = (int) (currEntryId - firstEntryId);

            // Allocate memory for new term's super node bitmaps.
            termSNbs.clear();
            termSNbs = null;
            termSNbs = new Hashtable<Short, BitSet>();
            termSNbs.put(fieldNbr, new BitSet(SUPER_NODE_SIZE));

            // Set bit at the node where the term is found.
            termSNbs.get(fieldNbr).set(nextBitToSet);
          }
        } // while ((line = input.readLine()) != null)

        if (input != null) {
          input.close();
        }

        // Store the last term bitmap at SN.
        if (term != null && termSNbs.size() > 0) {
          SuperNodeBitmap termBitmap = new SuperNodeBitmap(termSNbs);
          
          // Insert pair ("term snId", TS).
          if (bitmapStore != null) {
            bitmapStore.insert(term, superNodeId, termBitmap);
          }
          
          recordsProcessed++;
        }
      } catch (Exception e) {
        // Close database.
        if (bitmapStore != null) {
          bitmapStore.shutdown();
        }
        
        return false;
      }
    } catch (Exception e) {
      // Close database.
      bitmapStore.shutdown();
      return false;
    }
    
    // Close and make data persistent in the database.
    if (bitmapStore != null) {
      return bitmapStore.shutdown();
    } else {
      return true;
    }
  } // public boolean buildTermBitmaps()

  /**
   * Partitions cBitmap into an array of M-Sized, uncompressed BitSets.
   * The array itself has size equal to M.
   * @param cBitmap
   * @return
   */
  private BitSet[] CBitmap2BitSets(CBitmap cBitmap) {
    if (cBitmap == null) {
      return null;
    }

    BitSet bitmapBS = cBitmap.getBitSet(SUPER_NODE_SIZE);
    BitSet[] partitionedBS = new BitSet[M];
    int nextSetBit = bitmapBS.nextSetBit(0);

    for (int i = nextSetBit / M; i < M && nextSetBit >= 0; i++) {
      // Take M-sized chunks and create a BitSet with it.
      int startBit = i * M; // inclusive
      int endBit = startBit + M; // exclusive

      BitSet nodeBS = bitmapBS.get(startBit, endBit);
      if (nodeBS.cardinality() > 0) {
        partitionedBS[i] = nodeBS;
      }

      nextSetBit = bitmapBS.nextSetBit(endBit);
      if (nextSetBit / M > i + 1) {
        i = nextSetBit / M - 1;
      }
    } // for (int i = nextSetBit/M; i < M && nextSetBit >= 0; i++)
    
    return partitionedBS;
  }

  /**
   * Check if tuple contains term for fieldNumber within searchInterval.
   * 
   * @param tuple
   * @param term
   * @param fieldNumber
   * @param searchInterval SN search interval.
   * @param atSNId output containing the SN where term was found in searchInterval,
   * or empty if tuple does not qualify.
   * @return term bitmap if tuple contains term for fieldNumber within searchInterval,
   * null otherwise. atSNId contains the qualifying SN where term was found.
   * 
   * When the return value is null, atSNId may be:
   *   - empty: if the term was not found at all.
   *   - a SN id inside the search interval, but term was found in a different field.
   *   - a SN id outside of search interval.
   */
  private CBitmap getTermBitmap(Tuple tuple, String term, short fieldNumber,
          SNInterval searchInterval, ArrayList<Integer> atSNId) {
    TermAtSN termAtSN = (TermAtSN) tuple.getKey();
    atSNId.clear();

    if (!term.equals(termAtSN.getTerm())) {
      // Different term.
      return null;
    }

    // term found. It may be outside the interval.
    atSNId.add(0, termAtSN.getSNId());

    if (termAtSN.getSNId() >= searchInterval.getStart() &&
        termAtSN.getSNId() <= searchInterval.getEnd()) {
      // term found within search interval.
      // Check if a bitmap is available for the field.
      SuperNodeBitmap bitmap = (SuperNodeBitmap) tuple.getValue();

      if (fieldNumber == -1) {
        return bitmap.getAnyFieldBitmap();
      } else {
        return bitmap.getAFieldBitmap(fieldNumber);
      }
    }
    
    return null;
  } // private boolean tupleContainsTerm()


  /**
   * Returns the bitmap of the smallest SNId in the SN interval (inclusive)
   * where the AND-semantics predicate is satisfied.
   * Ordering: least frequent at the beginning.
   * @param queryTextPredicate
   * @param atSNId
   * @return A bitmap or null if no SN in the interval satisfies the predicate.
   * atSNId contains the qualifying snId or is empty when the returned bitmap
   * is null.
   */
  private CBitmap andSemantics(QueryTextPredicate queryTextPredicate,
          SNInterval snInterval, ArrayList<Integer> atSNId) throws IOException {
    int numberOfTerms = queryTextPredicate.getKeywordList().size();
    int endSN = snInterval.getEnd();
    int currSNId = snInterval.getStart();
    short fieldNumber = queryTextPredicate.getFieldNumber();
    TupleBrowser[] browsers = new TupleBrowser[numberOfTerms];
    
    // Initialize term browsers and current SN.
    for (int i = 0; i < numberOfTerms; i++) {
      String term = queryTextPredicate.getKeywordList().get(i);
      TermAtSN termAtSN = new TermAtSN(term, currSNId);
      browsers[i] = bitmapStore.browse(termAtSN);
      
      // Check if tuple contains term.
      Tuple tuple = new Tuple(null, null);

      if (!browsers[i].getNext(tuple)) {
        // term not found. No need to search further.
        atSNId.clear();
        return null;
      }

      SNInterval searchInterval = new SNInterval(currSNId, endSN);
      getTermBitmap(tuple, term, fieldNumber, searchInterval, atSNId);

      if (atSNId.size() == 0 || atSNId.get(0) > endSN) {
        // term not found in the search interval.
        return null;
      }
      // (atSNId >= currSNId)

      // Re-position browser.
      browsers[i].getPrevious(tuple);

      if (atSNId.get(0) > currSNId) {
        // Start search at the greater SN.
        currSNId = atSNId.get(0);
      }
    } // for (int i = 0; i < numberOfTerms; i++)
    
    CBitmap predicateBitmap = null;
    int lastFoundTermIndex = -1; // -1 means no last term found.
    int matches = 0;

    // Combine posting lists.
    while (currSNId <= endSN) {
      for (int i = 0; i < numberOfTerms; i++) {
        if (i == lastFoundTermIndex) {
          // Term already computed. Skip it.
          lastFoundTermIndex = -1;
          continue;
        }

        Tuple tuple = new Tuple(null, null);
        CBitmap termBitmap = null;
        SNInterval searchInterval = new SNInterval(currSNId, endSN);
        String term = queryTextPredicate.getKeywordList().get(i);

        while (termBitmap == null) {
          if (!browsers[i].getNext(tuple)) {
            // No more terms. No need to search further.
            atSNId.clear();
            return null;
          }

          // Check if tuple contains term and is within the SN interval.
          termBitmap = getTermBitmap(tuple, term, fieldNumber,
                        searchInterval, atSNId);

          if (atSNId.size() == 0) {
            // term not found in the search interval.
            return null;
          }

          // (atSNId != null)
          if (atSNId.get(0) > currSNId) {
            if (termBitmap != null) {
              // Re-start search at the greater SN.
              currSNId = atSNId.get(0);

              // Discard previous bitmaps.
              predicateBitmap = null;
              matches = 0;
              lastFoundTermIndex = i;
            } else {
              // Re-start search at the next SN.
              currSNId = atSNId.get(0) + 1;

              // Discard previous bitmaps.
              predicateBitmap = null;
              matches = 0;
              lastFoundTermIndex = -1;
            }
          }
        } // while (termBitmap)

        // (termBitmap != null at currSNId)

        // Combine bitmaps.
        if (predicateBitmap == null) {
          predicateBitmap = termBitmap;
        } else {
          predicateBitmap = combineSuperNodeBitmaps(predicateBitmap, termBitmap,
                            ComparisonOperator.EQUAL);
          lastFoundTermIndex = -1;
        }

        if (predicateBitmap == null || predicateBitmap.cardinality() == 0) {
          // No candidates found in current SN. Check next SN.
          predicateBitmap = null;
          matches = 0;
          lastFoundTermIndex = -1;
          currSNId++;
          
          // No need to check other terms.
          break;
        } else {
          matches++;

          if (matches == numberOfTerms) {
            // Predicate satisfied.
            break;
          }
        }
      } // for (int i = 0; i < numberOfTerms; i++)

      if (matches == numberOfTerms && predicateBitmap != null &&
          predicateBitmap.cardinality() > 0) {
        // Candidate found.
        atSNId.clear();
        atSNId.add(0, currSNId);
        return predicateBitmap;
      }
    } // while (currSNId <= endSN)

    // Interval was exhausted.
    atSNId.clear();
    return null;
  } // private CBitmap andSemantics()

  /**
   * Returns the bitmap of the smallest SNId in the SN interval (inclusive)
   * where the OR-semantics predicate is satisfied.
   * Ordering: most frequent at the beginning.
   * @param queryTextPredicate
   * @param atSNId
   * @return A bitmap or null if no SN in the interval satisfies the predicate.
   * atSNId contains the qualifying snId or is empty when the returned bitmap
   * is null.
   */
  private CBitmap orSemantics(QueryTextPredicate queryTextPredicate,
          SNInterval snInterval, ArrayList<Integer> atSNId) throws IOException {
    int numberOfTerms = queryTextPredicate.getKeywordList().size();
    int startSN = snInterval.getStart();
    int endSN = snInterval.getEnd();
    int currSNId = endSN + 1;
    short fieldNumber = queryTextPredicate.getFieldNumber();
    TupleBrowser[] browsers = new TupleBrowser[numberOfTerms];
    SNInterval searchInterval = new SNInterval(startSN, endSN);

    // Keeps terms not found in the interval.
    ArrayList<String> exhaustedTerms = new ArrayList<String>();

    // Initialize term browsers and current SN.
    for (int i = 0; i < numberOfTerms; i++) {
      String term = queryTextPredicate.getKeywordList().get(i);
      TermAtSN termAtSN = new TermAtSN(term, startSN);
      browsers[i] = bitmapStore.browse(termAtSN);
      
      // Check if tuple contains term.
      Tuple tuple = new Tuple(null, null);

      if (!browsers[i].getNext(tuple)) {
        // term not found. No need to search it any further.
        exhaustedTerms.add(term);

        // Check next term.
        continue;
      }

      getTermBitmap(tuple, term, fieldNumber, searchInterval, atSNId);

      if (atSNId.size() == 0 || atSNId.get(0) > endSN) {
        // term not found in the search interval. No need to search it any further.
        exhaustedTerms.add(term);

        // Check next term.
        continue;
      }
      // (atSNId >= startSN)

      // Re-position browser.
      browsers[i].getPrevious(tuple);

      if (atSNId.get(0) < currSNId) {
        // Start search at the lowest SN.
        currSNId = atSNId.get(0);
      }
    } // for (int i = 0; i < numberOfTerms; i++)

    if (exhaustedTerms.size() == numberOfTerms) {
      // No term found in the search interval.
      atSNId.clear();
      return null;
    }

    // (currSNId >= startSN && currSNId <= endSN)

    // TODO: case for aField. bitmap at currSN may be null.
    // Then you need to iterate until a not-null bitmap is found.

    while (currSNId <= endSN) {
      CBitmap predicateBitmap = null;
      searchInterval = new SNInterval(currSNId, endSN);
      int nextSNId = endSN;

      // Combine posting lists.
      for (int i = 0; i < numberOfTerms; i++) {
        String term = queryTextPredicate.getKeywordList().get(i);

        if (exhaustedTerms.contains(term)) {
          // term does not exist in the interval. Skip it.
          continue;
        }

        Tuple tuple = new Tuple(null, null);

        if (!browsers[i].getNext(tuple)) {
          // term not found. No need to search it any further.
          exhaustedTerms.add(term);

          // Check next term.
          continue;
        }

        // Check if tuple contains term and is within the SN interval.
        CBitmap termBitmap = null;
        termBitmap = getTermBitmap(tuple, term, fieldNumber, searchInterval, atSNId);

        if (atSNId.size() == 0) {
          // term not found in the search interval.
          exhaustedTerms.add(term);

          // Check next term.
          continue;
        }

        if (atSNId.get(0) == currSNId) {
          // Combine bitmaps.
          if (predicateBitmap == null) {
            predicateBitmap = termBitmap;
          } else {
            predicateBitmap = combineSuperNodeBitmaps(predicateBitmap, termBitmap,
                              ComparisonOperator.OR);
          }
        }

        if (atSNId.get(0) < nextSNId) {
          nextSNId = atSNId.get(0);
        }
      } // for (int i = 0; i < numberOfTerms; i++)

      if (predicateBitmap != null && predicateBitmap.cardinality() > 0) {
        // Candidate found.
        atSNId.clear();
        atSNId.add(0, currSNId);
        return predicateBitmap;
      }
      
      if (nextSNId > currSNId + 1) {
        currSNId = nextSNId;
      } else {
        currSNId++;
      }
    } // while (currSNId <= endSN)
    
    // Interval was exhausted.
    atSNId.clear();
    return null;
  } // private CBitmap orSemantics()

  /**
   * Returns the bitmap of smallest SNId in the SN interval (inclusive)
   * where the NOT-semantics predicate is satisfied.
   * Ordering: most frequent at the beginning.
   * 
   * @param queryTextPredicate
   * @param atSNId
   * @return A bitmap or null if no SN in the interval satisfies the predicate.
   * atSNId contains the qualifying snId or is empty when the returned bitmap
   * is null.
   */
  private CBitmap notSemantics(QueryTextPredicate queryTextPredicate,
          SNInterval snInterval, ArrayList<Integer> atSNId) throws IOException {
    int numberOfTerms = queryTextPredicate.getKeywordList().size();
    int startSNId = snInterval.getStart();
    int endSN = snInterval.getEnd();
    int currSNId = endSN + 1;
    short fieldNumber = queryTextPredicate.getFieldNumber();
    TupleBrowser[] browsers = new TupleBrowser[numberOfTerms];
    ArrayList<String> exhaustedTerms = new ArrayList<String>();

    // Initialize term browsers and current SN.
    for (int i = 0; i < numberOfTerms; i++) {
      String term = queryTextPredicate.getKeywordList().get(i);
      TermAtSN termAtSN = new TermAtSN(term, startSNId);
      browsers[i] = bitmapStore.browse(termAtSN);

      // Check if tuple contains term.
      Tuple tuple = new Tuple(null, null);

      if (!browsers[i].getNext(tuple)) {
        // term not found in the search interval. Check next term.
        exhaustedTerms.add(term);
        continue;
      }

      SNInterval searchInterval = new SNInterval(startSNId, endSN);
      getTermBitmap(tuple, term, fieldNumber, searchInterval, atSNId);

      if (atSNId.size() == 0 || atSNId.get(0) > endSN) {
        // term not found in the search interval. Check next term.
        exhaustedTerms.add(term);
        continue;
      }
      // (atSNId >= startSNId && atSNId <= endSNId)

      // Re-position browser.
      browsers[i].getPrevious(tuple);

      if (atSNId.get(0) < currSNId) {
        // Start search at the lowest SN.
        currSNId = atSNId.get(0);
      }
    } // for (int i = 0; i < numberOfTerms; i++)

    if (currSNId > startSNId && isSNValid(startSNId)) {
      // All objects in startSN qualify.
      atSNId.clear();
      atSNId.add(startSNId);
      return cleanseSNBitmap(startSNId, CBM_ALL_BITS_SET.getBitSet());
    }

    // (currSNId == startSNId)
    CBitmap predicateBitmap = null;
    int matches = 0;

    // Combine posting lists.
    while (currSNId <= endSN) {
      // Query only valid SNs.
      if (!isSNValid(currSNId)) {
        currSNId++;
        continue;
      }
      
      SNInterval searchInterval = new SNInterval(currSNId, endSN);
      
      for (int i = 0; i < numberOfTerms; i++) {
        Tuple tuple = new Tuple(null, null);
        CBitmap termBitmap = null;
        String term = queryTextPredicate.getKeywordList().get(i);

        // Flip bits as needed.
        while (termBitmap == null && currSNId <= endSN) {
          if (!browsers[i].getNext(tuple)) {
            // term not found in the search interval. All objects qualify.
            termBitmap = cleanseSNBitmap(currSNId, CBM_ALL_BITS_SET.getBitSet());
            break;
          }

          // Check if tuple contains term and is within the SN interval.
          termBitmap = getTermBitmap(tuple, term, fieldNumber,
                        searchInterval, atSNId);

          if (atSNId.size() == 0 || atSNId.get(0) > endSN) {
            // term not found in the search interval. All objects qualify.
            termBitmap = cleanseSNBitmap(currSNId, CBM_ALL_BITS_SET.getBitSet());
            break;
          }

          // (atSNId != null)
          if (atSNId.get(0) == currSNId) {
            if (termBitmap != null) {
              termBitmap = cleanseSNBitmap(currSNId, termBitmap.flipBS(SUPER_NODE_SIZE));
            } else {
              // term not found in the search interval. All objects qualify.
              termBitmap = cleanseSNBitmap(currSNId, CBM_ALL_BITS_SET.getBitSet());
            }

            // Check next term.
            break;
          }

          if (atSNId.get(0) > currSNId) {
            // term not found at currSNId. All objects qualify.
            termBitmap = cleanseSNBitmap(currSNId, CBM_ALL_BITS_SET.getBitSet());

            // Re-position browser. (Wait for other postings.)
            browsers[i].getPrevious(tuple);
          }
        } // while (termBitmap == null)

        // (termBitmap != null at currSNId)

        // Combine bitmaps.
        if (predicateBitmap == null) {
          predicateBitmap = termBitmap;
        } else {
          predicateBitmap = combineSuperNodeBitmaps(predicateBitmap, termBitmap,
                            ComparisonOperator.NOT_EQUAL);
        }

        if (predicateBitmap == null || predicateBitmap.cardinality() == 0) {
          // No candidates found in current SN. Check next SN.
          predicateBitmap = null;
          matches = 0;
          currSNId++;

          // No need to check other terms.
          break;
        } else {
          matches++;

          if (matches == numberOfTerms) {
            // Predicate satisfied.
            break;
          }
        }
      } // for (int i = 0; i < numberOfTerms; i++)

      if (matches == numberOfTerms && predicateBitmap != null &&
          predicateBitmap.cardinality() > 0) {
        // Candidate found.
        atSNId.clear();
        atSNId.add(0, currSNId);
        return predicateBitmap;
      }
    } // while (currSNId <= endSN)

    // Interval was exhausted.
    atSNId.clear();
    return null;
  } // private CBitmap notSemantics()
  
  /**
   * Computes the SN bitmap as an array of BitSets of a given query.
   * Every entry represents and individual node bitmap. Some node bitmaps
   * may be null, meaning that no object within it satisfies the query predicates.
   *
   * @param snId a super node id.
   * @param queryTextPredicates query predicates.
   * @return
   * @throws java.io.IOException
   */
  public BitSet[] getQuerySNbitmap(int startSN, int endSN,
          ArrayList<QueryTextPredicate> queryTextPredicates,
          ArrayList<Integer> atSNId) throws IOException {
    CBitmap querySNbitmap = null;
    int currSNId = startSN;
    int lastEvaluatedPredicate = -1;
    int matches = 0;
    int numberOfPredicates = queryTextPredicates.size();

    while (currSNId <= endSN) {
      if (snNodeBoundaries != null && !snNodeBoundaries.containsKey(currSNId)) {
        currSNId++;
      }

      for (int i = 0; i < numberOfPredicates && currSNId <= endSN; i++) {
        if (i == lastEvaluatedPredicate) {
          // Predicate already evaluated. Skip it.
          lastEvaluatedPredicate = -1;
          continue;
        }

        QueryTextPredicate queryTextPredicate = queryTextPredicates.get(i);
        ComparisonOperator op = queryTextPredicates.get(i).getOperator();
        CBitmap aPredSNbitmap = null;

        if (op == ComparisonOperator.EQUAL) {
          aPredSNbitmap = andSemantics(queryTextPredicate,
               new SNInterval(currSNId, endSN), atSNId);
        } else if (op == ComparisonOperator.NOT_EQUAL) {
          aPredSNbitmap = notSemantics(queryTextPredicate,
               new SNInterval(currSNId, endSN), atSNId);
        } else  if (op == ComparisonOperator.OR) {
          aPredSNbitmap = orSemantics(queryTextPredicate,
               new SNInterval(currSNId, endSN), atSNId);
        }

        if (aPredSNbitmap == null || atSNId.size() == 0) {
          // No object qualifies within the current interval.
          return null;
        }

        // Apply conjunctive logic with previous predicates.
        if (atSNId.get(0) == currSNId) {
          if (querySNbitmap == null) {
            querySNbitmap = aPredSNbitmap;
          } else {
            // Combine predicate bitmaps.
            querySNbitmap = combineSuperNodeBitmaps(querySNbitmap, aPredSNbitmap,
                              ComparisonOperator.EQUAL);
            lastEvaluatedPredicate = -1;
          }
          
          if (querySNbitmap == null || querySNbitmap.cardinality() == 0) {
            // No qualifying objects found. Check next SN.
            querySNbitmap = null;
            currSNId++;
            lastEvaluatedPredicate = -1;
            matches = 0;
            break;
          } else {
            matches++;
          }

          if (matches == numberOfPredicates) {
            // Query predicates satisfied.
            break;
          }
        } else { // (atSNId.get(0) > currSNId)
          // Discard current bitmap.
          querySNbitmap = null;

          // Remember predicate bitmap.
          querySNbitmap = aPredSNbitmap;
          lastEvaluatedPredicate = i;
          matches = 1;

          // Re-start search from the greater SN.
          currSNId = atSNId.get(0);
          break;
        }
      } // for (int i = 0; i < queryTextPredicates.size(); i++)

      if (matches == numberOfPredicates && querySNbitmap != null &&
          querySNbitmap.cardinality() > 0) {
        atSNId.clear();

        // Candidate found.
        atSNId.add(0, currSNId);

        return CBitmap2BitSets(querySNbitmap);
      }
    } // while (currSNId <= endSN)

    // Interval exhausted.
    return null;
  } // public BitSet[] getQuerySNbitmap()

  /**
   * Combines term super node bitmaps according to the op semantics.
   * Assumptions:
   *   - Bitmaps have already been flipped for NOT-semantics.
   * @param bm1
   * @param bm2
   * @param op
   * @return combined bitmap. "null" means no object qualifies in the combined bitmap.
   */
  private CBitmap combineSuperNodeBitmaps(CBitmap bm1, CBitmap bm2, ComparisonOperator op) {
  

    CBitmap combinedBM = null;
    switch (op) {
      case EQUAL: // "=" - AND operator is also used to merge predicate bitmaps (conjunctive normal form).
      case NOT_EQUAL: // "!=" - negation is implemented identically to AND.
        // The main assumptions are that bitmaps have already been flipped.
        // And neither of the bitmaps is null.
        // No bitmap is expected to be null.
        if (bm1 == null || bm2 == null) {
          return null;
        }
          
        // Intersection of bitmaps.
        combinedBM = bm1.sAnd(bm2, SUPER_NODE_SIZE);
        break;
        
      case OR: // "|=" OR operator.
        // Union of bitmaps.

        if (bm1 == null) {
          combinedBM = bm2;
          break;
        }
        if (bm2 == null) {
          combinedBM = bm1;
          break;
        }
        combinedBM = bm1.or(bm2, SUPER_NODE_SIZE);
        break;

      default:
        return null;
    } // switch (op)

    if (combinedBM == null || combinedBM.cardinality() == 0) {
      // No object qualifies in this super node.
      return null;
    } else {
      return combinedBM;
    }
  } // private CBitmap combineSuperNodeBitmaps()

  public boolean startupStore() {
    if (bitmapStore != null) {
      return bitmapStore.startup();
    } else {
      return false;
    }
  }

  public boolean shutdownStore() {
    if (bitmapStore != null) {
      return bitmapStore.shutdown();
    } else {
      return false;
    }
  }
} // public class SpatialInvertedFile