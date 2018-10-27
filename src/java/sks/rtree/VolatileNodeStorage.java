/*
 * VolatileNodeStorage.java
 */

package sks.rtree;

import java.util.Hashtable;

public class VolatileNodeStorage {
	private int nextRef = 0;
	private Hashtable<Long, Node> nodes = new Hashtable<Long, Node>();

	VolatileNodeStorage() {
	}

	public Node readNode(long nodeRef) {
		return nodes.get(nodeRef);
	}

	public long writeNode(Node node) {
		if (node.ref == -1) {
			node.ref = nextRef++;
    }

		nodes.put(node.ref, node);
		return node.ref;
	}

  // Used during R-tree deserialization.
	public void writeNode(long nodeRef, Node node) {
		nodes.put(nodeRef, node);
	}
	
	public int size() {
		return nodes.size();
	}
        
  public void setStorePath(String path) {
    return;
  }

  public Hashtable<Long, Node> getNodes() {
    return nodes;
  }

  public void EmptyHashtable() {
    nodes.clear();
  }
} // class VolatileNodeStorage