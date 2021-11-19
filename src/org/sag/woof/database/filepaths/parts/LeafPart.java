package org.sag.woof.database.filepaths.parts;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/** Represents a part who does not reference any other 
 * part and therefore whose value does not change.
 */
@XStreamAlias("LeafPart")
public interface LeafPart extends Part, Comparable<LeafPart> {}
