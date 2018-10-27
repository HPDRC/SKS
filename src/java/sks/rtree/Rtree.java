package sks.rtree;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Hashtable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import sks.Loader;
import sks.QueryTextPredicate;
import sks.sif.SpatialInvertedFile;
import sks.ski.SNInterval;

public class Rtree implements Serializable {
  static final long serialVersionUID = -5185597343178749638L;
  
  private Node root;
  private short maxCapacity; // R-tree fanout (M).
  private short minCapacity;
  private int numFieldCount = 0;

  // Sizes of individual nodes inside each super node.
  // Key = SN id
  // Value = An array of size O(M), where each entry i is the size of
  // the underlying leaf node at position i.
  private Hashtable<Integer, SuperNodeBoundary> snNodeBoundaries = null;
  
  // In-memory node storage.
  transient VolatileNodeStorage storage;

  public Hashtable<Integer, SuperNodeBoundary> getSNNodeBoundaries() {
    return snNodeBoundaries;
  }

  public Node getRootNode() {
    return root;
  }
  
  public int getMaxCapacity() {
    return maxCapacity;
  }

  public Rtree(short capacity, float fillFactor) {
    this(capacity, fillFactor, 0);
  }

  public Rtree(short capacity, float fillFactor, int numFieldCount) {
    // Validate capacity values.
    if (capacity < 2) {
      throw new IllegalArgumentException("Capacity must be greater than 1");
    }

    if (fillFactor < 0 || fillFactor > 0.5f) {
      throw new IllegalArgumentException("fill factor must be between 0 and 0.5");
    }

    maxCapacity = capacity;
    minCapacity = (short) Math.round(capacity * fillFactor);
    storage = new VolatileNodeStorage();
    root = new Node(capacity, (short) 0, (numFieldCount > 0)? true:false);
    this.numFieldCount = numFieldCount;
    storage.writeNode(root);
  }

  public void emptyStorage() {
    if (storage != null) {
      storage.EmptyHashtable();
    }
  }

  public Hashtable<Long, Node> getNodesInStorage() {
    return storage.getNodes();
  }

  public int height() {
    return root.level + 1;
  }

  public int size() {
    return storage.size();
  }

  public int getNumFieldCount() {
    return numFieldCount;
  }

  public void insert(long ref, Point point, NumericRange numRange) {
    insert(ref, new Rectangle(point, point), numRange);
  }

  public void insert(long ref, Rectangle rect, NumericRange numRange) {
    if (rect == null) {
      throw new NullPointerException("rect cannot be null");
    }

    Node node = chooseLeaf(rect);
    adjustNode(node, ref, rect, numRange);
  }

  private Node chooseLeaf(Rectangle rect) {
    Node node = root;

    while (!node.isLeaf()) {
      // Find least enlargement
      int index = 0;
      double minArea = Double.POSITIVE_INFINITY;

      for (int i = 0; i < node.size(); i++) {
        Rectangle r = node.rects[i].getMinBoundingRect(rect);
        double enlargement = r.getArea() - node.rects[i].getArea();

        if (enlargement < minArea) {
          minArea = enlargement;
          index = i;
        } else if (enlargement == minArea) {
          if (node.rects[i].getArea() < node.rects[index].getArea()) {
            index = i;
          }
        }
      }

      node = storage.readNode(node.refs[index]);
    }

    return node;
  } // private Node chooseLeaf()

  private void adjustNode(Node node, long ref, Rectangle rect, NumericRange numRange) {
    if (!node.isFull()) {
      node.insert(ref, rect, numRange);
      storage.writeNode(node);

      if (!node.isRoot()) {
        Node parent = storage.readNode(node.parentRef);
        adjustSubTree(parent, node, null);
      }
    } else {
      Node[] nodes;
      nodes = node.split(ref, rect, maxCapacity, minCapacity, numRange);
      
      if (node.isRoot()) {
        nodes[0].ref = node.ref; // re-use node reference.
        storage.writeNode(nodes[0]);
        long nodeRef = storage.writeNode(nodes[1]);

        if (!nodes[1].isLeaf()) {
          for (int i = 0; i < nodes[1].size(); i++) {
            Node child = storage.readNode(nodes[1].refs[i]);
            child.parentRef = nodeRef;
            storage.writeNode(child);
          }
        }

        root = new Node(maxCapacity, (short) (node.level + 1), (numFieldCount > 0)? true:false);
        root.insert(nodes[0].ref, nodes[0].getMinBoundingRect(), nodes[0].getNumericRange(root.isLeaf(), numFieldCount));
        root.insert(nodes[1].ref, nodes[1].getMinBoundingRect(), nodes[1].getNumericRange(root.isLeaf(), numFieldCount));
        storage.writeNode(root);
        nodes[0].parentRef = root.ref;
        storage.writeNode(nodes[0]);
        nodes[1].parentRef = root.ref;
        storage.writeNode(nodes[1]);
      } else { // if (node.isRoot())
        nodes[0].ref = node.ref; // re-use node reference.
        nodes[0].parentRef = node.parentRef;
        storage.writeNode(nodes[0]);
        nodes[1].parentRef = node.parentRef;
        long nodeRef = storage.writeNode(nodes[1]);

        if (!nodes[1].isLeaf()) {
          for (int i = 0; i < nodes[1].size(); i++) {
            Node child = storage.readNode(nodes[1].refs[i]);
            child.parentRef = nodeRef;
            storage.writeNode(child);
          }
        }

        Node parent = storage.readNode(node.parentRef);
        adjustSubTree(parent, nodes[0], nodes[1]);
      }
    }
  } // private void adjustNode()

  /**
   * Adjusts a node's MBRs and NumericRanges after insertion of a child node.
   * Adjustments are carried out recursively on nodes up in the tree.
   * @param node
   * @param child
   * @param newChild
   */
  private void adjustSubTree(Node node, Node child, Node newChild) {
    for (int i = 0; ; i++) {
      assert i < node.size();
      if (node.refs[i] == child.ref) {
        node.rects[i] = child.getMinBoundingRect();

        if (node.numRanges != null) {
          node.numRanges[i] = child.getNumericRange(node.isLeaf(), numFieldCount);
        }

        storage.writeNode(node);
        break;
      }
    } // for (int i = 0; ; i++)

    if (newChild != null) {
      adjustNode(node, newChild.ref, newChild.getMinBoundingRect(),
              newChild.getNumericRange(node.isLeaf(), numFieldCount));
    } else if (!node.isRoot()) {
      Node parent = storage.readNode(node.parentRef);
      adjustSubTree(parent, node, null); // recursive adjustment.
    }
  } // private void adjustSubTree

  public void printLeftBranch(PrintWriter out) {
    Node n = root;

    while (!n.isLeaf()) {
      out.println(n);
      n = storage.readNode(n.refs[0]);
    }

    out.println(n);
  } // public void printLeftBranch()

  /**
   * Traverses the R-tree in depth-first order writing out <docRef, nodeId> pairs.
   * 
   * @param node subtree root node.
   * @param parentEntryId subtree's parent node ID.
   * @param docOutput a documentNode writer.
   * @throws java.io.IOException
   */
  private void dumpNodeId(Node node, long parentEntryId, Writer docOutput) throws IOException {
    int nodeSize = 0;
    long entryId = 0;
    long startEntryId = parentEntryId * maxCapacity;

    if (node != null) {
      nodeSize = node.size();
    } else {
      return;
    }

    if (node.isLeaf()) {
      for (int i = 0; i < nodeSize; i++) {
        // write: "docRef entryID"
        entryId = startEntryId + i;
        docOutput.write(node.refs[i] + Loader.FIELD_SEPARATOR + entryId + "\n");
      }

      return;
    }

    SuperNodeBoundary snBoundaries = null;

    if (node.level == 1 && snNodeBoundaries != null) {
      snBoundaries = new SuperNodeBoundary(nodeSize);
      snNodeBoundaries.put((int) parentEntryId, snBoundaries);
    }

    for (int i = 0; i < nodeSize; i++) {
      entryId = startEntryId + i;
      Node childNode = storage.readNode(node.refs[i]);
      dumpNodeId(childNode, entryId, docOutput);

      if (snBoundaries != null) {
        snBoundaries.set(i, childNode.size);
      }
    }
  } // private void dumpNodeID()

  /**
   * Writes <docRef, nodeId> pairs into a file.
   * 
   * @param docNodeFile stores the pairs sorted by docRef.
   * @throws java.io.IOException
   */
  public void dumpDocumentNodeIds(File docNodeFile) throws IOException {
    Writer docOutput = null;
    
    try {
      // "doc entry_id"
      docOutput = new BufferedWriter(new FileWriter(docNodeFile), Loader.IO_BUFFER_SIZE);
      
      if (root.level > 2) {
        snNodeBoundaries = new Hashtable<Integer, SuperNodeBoundary>();
      }

      for (int i = 0; i < root.size(); i++) {
        dumpNodeId(storage.readNode(root.refs[i]), i, docOutput);
      }
    } finally {
      if (docOutput != null) {
        docOutput.close();
      }
    }
  } // public void dumpDocumentNodeIds()
  
  public void printTree(java.io.PrintWriter out) {
    int count = storage.size();
    for (int i = 0; i < count; i++) {
      out.println(storage.readNode(i));
    }
  }

  /**
   * 
   * @param entryId
   * @param level
   * @param ncSNIntervals
   * @param bufferedSNbitmaps
   * @param queryTextPredicates
   * @param sif
   * @return
   * @throws IOException
   */
  public boolean isSubtreeCandidate(long entryId, short level,
          ArrayList<SNInterval> ncSNIntervals,
          Hashtable<Integer, BitSet[]> bufferedSNbitmaps,
          ArrayList<QueryTextPredicate> queryTextPredicates,
          SpatialInvertedFile sif) throws IOException {
    // Algorithm to compute super node interval to scan.
    //   if (level > 2) : multiply till level=2, then interval is (start,end).
    //   if (level == 2): return nodeId.
    //   if (level == 1): nodeId/M. if level==0 then nodeId/M^2.
    int startSN = (int) (entryId * maxCapacity);
    int endSN = (int) (entryId * maxCapacity + maxCapacity - 1);

    if (level > 2) { 
      // SuperNode interval.
      // Note: SuperNode Id of a leaf entry is the node's grandparent entry Id.
      // Calculate minimum and maximum children node Ids of subtree rooted at nodeId.
      for (int i = level - 2; i >= 2; i--) {
        startSN = (int) (startSN * maxCapacity);
        endSN = (int) (endSN * maxCapacity + maxCapacity - 1);
      }

      // Check if the SN interval is marked as non-candidate.
      for (int i = 0; i < ncSNIntervals.size(); i++) {
        SNInterval snInterval = ncSNIntervals.get(i);

        // Look for containment.
        if (startSN >= snInterval.getStart()) {
          if (endSN <= snInterval.getEnd()) {
            return false;
          } else {
            if (startSN < snInterval.getEnd()) {
              startSN = (int) (snInterval.getEnd() + 1);
            }
          }
        }
      } // for (int i = 0; i < ncSNInterval.size(); i++)
    } else if (level == 2) {
      startSN = (int) entryId;
      endSN = startSN;
    } else if (level == 1) {
      startSN = (int) (entryId / maxCapacity);
      endSN = startSN;
    } else if (level == 0) {
      startSN = (int) (entryId / (maxCapacity * maxCapacity));
      endSN = startSN;
    }

    // Check if the SN interval is buffered.
    for (int snId = startSN; snId <= endSN && !bufferedSNbitmaps.isEmpty(); snId++) {
      if (bufferedSNbitmaps.containsKey(snId)) {
        return true;
      }
    }

    ArrayList<Integer> atSNId = new ArrayList<Integer>();
    BitSet[] querySNbitmap = sif.getQuerySNbitmap(startSN, endSN,
                              queryTextPredicates, atSNId);

    if (querySNbitmap != null) {
      // Candidate object(s) found within the SN interval.
      bufferedSNbitmaps.put(atSNId.get(0).intValue(), querySNbitmap);

      // Mark non-candidate SN sub-interval.
      if (atSNId.get(0) > startSN) {
        ncSNIntervals.add(new SNInterval(startSN, atSNId.get(0) - 1));
      }
      
      if (atSNId.get(0) <= endSN) {
        return true;
      } else {
        return false;
      }
    }

    // Mark the entire SN interval as non-candidate.
    ncSNIntervals.add(new SNInterval(startSN, endSN));
    
    return false;
  } // public boolean IsSubtreeCandidate()

  public VolatileNodeStorage getVolatileStorage() {
    return storage;
  }

  private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
    s.defaultReadObject();
    storage = new VolatileNodeStorage();
  }
} // class Rtree