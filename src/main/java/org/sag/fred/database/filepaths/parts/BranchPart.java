package org.sag.fred.database.filepaths.parts;

import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/** Represents a part where the part contains a reference to
 * another part and therefore may change its value depending
 * on modifications.
 */
@XStreamAlias("BranchPart")
public interface BranchPart extends Part {
	
	public boolean add(Part p);
	
	public List<Part> getChildren();
	
	public List<Node> getChildNodes();
	
	/** Replaces the part at index with newChild.*/
	public boolean swapChild(Node child, Node newChild);
	
	/** Replaces the part at index with newChild.*/
	public boolean swapChild(Node child, Part newChild);

	boolean removeChild(Node child);
	
	public boolean mergeChild(Node childNode);
	
}
