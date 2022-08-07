package org.sag.fred.database.filepaths.parts;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.sag.common.tools.SortingMethods;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("AnyInfoPart")
public class AnyInfoPart implements AnyPart, LeafPart {

	@XStreamOmitField
	protected volatile String stringCache;
	@XStreamAlias("Id")
	private volatile String id;
	
	public AnyInfoPart(String id) {
		this.id = id;
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || !Objects.equals(getClass(), o.getClass()))
			return false;
		AnyInfoPart other = (AnyInfoPart)o;
		return Objects.equals(id, other.id);
	}
	
	@Override
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(id);
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
	
	/** Returns the string `ANY[?]` where ? is a representation of the child class. */
	@Override
	public String toSimpleString() {
		if(stringCache == null)
			stringCache = sepStr + indStr + "[" + id + "]" + sepStr;
		 return stringCache;
	}
	
	/** Returns the string `ANY[?]` where ? is a representation of the stmt for this AnyPart object. */
	@Override
	public String toString() {
		return toSimpleString();
	}
	
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
			return SortingMethods.sComp.compare(id, ((AnyInfoPart)o).id);
		} else {
			return getClass().getSimpleName().compareTo(o.getClass().getSimpleName());
		}
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
			return Collections.singleton(AnyInfoPart.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
}
