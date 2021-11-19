package org.sag.woof.database.filepaths.parts;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/** Represents a part where we encountered a situation in the code analysis
 * that we have not handled.
 */
@XStreamAlias("UnknownPart")
public interface UnknownPart extends LeafPart {
	
	public static final String indStr = "UNKNOWN";

}
