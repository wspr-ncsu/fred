package org.sag.fred.database.filepaths.parts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.sag.common.tools.SortingMethods;
import org.sag.common.xstream.NamedCollectionConverterWithSize;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("AnyComboPart")
public class AnyComboPart implements AnyPart, LeafPart {
	
	@XStreamOmitField
	protected volatile String stringCache;
	@XStreamOmitField
	protected volatile String stringCache2;
	@XStreamAlias("Contents")
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"Part"},types={Part.class})
	private volatile ArrayList<LeafPart> contents;
	
	public AnyComboPart(List<Node> contents) {
		if(contents == null) {
			this.contents = null;
		} else {
			this.contents = new ArrayList<>();
			for(Node n : contents) {
				this.contents.add((LeafPart)n.getPart());
			}
		}
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || !Objects.equals(getClass(), o.getClass()))
			return false;
		AnyComboPart other = (AnyComboPart)o;
		return Objects.equals(contents, other.contents);
	}
	
	@Override
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(contents);
		return i;
	}
	
	/**Returns the string .* representing that this could be any value. */
	@Override
	public String toRegexString() {
		return regexStr;
	}
	
	/** Returns the string `ANY`.*/
	@Override
	public String toSuperSimpleString() {
		return anyStr;
	}
	
	protected String getType() {
		String ret = getClass().getSimpleName().toUpperCase().replaceFirst(indStr, "");
		return ret.substring(0,ret.length() - 4);
	}
	
	/** Returns the string `ANY[?]` where ? is a representation of the child class. */
	@Override
	public String toSimpleString() {
		if(stringCache2 == null)
			stringCache2 = sepStr + indStr + "[" + getType() + "]" + sepStr;
		 return stringCache2;
	}
	
	protected String getDataSegmentString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[TYPE=").append(getType()).append(", CONTENTS=").append((contents == null) ? "null" : contents.toString()).append("]");
		return sb.toString();
	}
	
	/** Returns the string `ANY[?]` where ? is a representation of the stmt for this AnyPart object. */
	@Override
	public String toString() {
		if(stringCache == null)
			stringCache = sepStr + indStr + getDataSegmentString() + sepStr;
		return stringCache;
	}
	
	//When combined they are leafs that are removed from the path and just treated like a single entity
	//so no need to clone them
	@Override
	public Part cloneInner(Map<Part,Part> beforeAfterMap) {
		Part ret = beforeAfterMap.get(this);
		if(ret == null) {
			beforeAfterMap.put(this, this);
			ret = this;
		}
		return ret;
	}
	
	@Override
	public int compareTo(LeafPart o) {
		if(getClass() == o.getClass()) {
			return SortingMethods.sComp.compare(toString(), ((AnyComboPart)o).toString());
		} else {
			return getClass().getSimpleName().compareTo(o.getClass().getSimpleName());
		}
	}
	
	public List<LeafPart> getContents() {
		if(contents == null)
			return Collections.emptyList();
		return new ArrayList<>(contents);
	}
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public AnyInfoPart readXML(String filePath, Path path) throws Exception {
		throw new UnsupportedOperationException();
	}
	
	@XStreamOmitField
	private static AbstractXStreamSetup xstreamSetup = null;

	public static AbstractXStreamSetup getXStreamSetupStatic(){
		if(xstreamSetup == null)
			xstreamSetup = new XStreamSetup();
		return xstreamSetup;
	}
	
	@Override
	public AbstractXStreamSetup getXStreamSetup() {
		return getXStreamSetupStatic();
	}
	
	public static class XStreamSetup extends AbstractXStreamSetup {
		
		@Override
		public void getOutputGraph(LinkedHashSet<AbstractXStreamSetup> in) {
			if(!in.contains(this)) {
				in.add(this);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(AnyComboPart.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
}
