package org.sag.woof.database.filepaths.parts;

import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("AnyPart")
public interface AnyPart extends Part {
	
	public static final String regexStr = ".*";
	public static final String indStr = "ANY";
	public static final String anyStr = sepStr + indStr + sepStr;

}
