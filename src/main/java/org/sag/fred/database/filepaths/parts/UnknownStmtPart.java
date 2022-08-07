package org.sag.fred.database.filepaths.parts;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.sag.soot.xstream.SootUnitContainer;
import org.sag.soot.xstream.SootUnitContainerFactory;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import soot.SootMethod;
import soot.jimple.Stmt;

@XStreamAlias("UnknownStmtPart")
public final class UnknownStmtPart implements UnknownPart {
	
	private static final String regexStr = ".*";
	private static final String unknownStr = sepStr + indStr + sepStr;
	
	@XStreamOmitField
	private volatile String stringCache;
	@XStreamOmitField
	private volatile String stringCache2;
	@XStreamAlias("Source")
	protected volatile SootUnitContainer source;
	
	public UnknownStmtPart(Stmt stmt, SootMethod sourceMethod) {
		if(stmt == null || sourceMethod == null)
			this.source = null;
		else
			this.source = SootUnitContainerFactory.makeSootUnitContainer(stmt, sourceMethod);
	}
	
	@Override
	public boolean equals(Object o) {
		if(o == this)
			return true;
		if(o == null || !Objects.equals(getClass(), o.getClass()))
			return false;
		UnknownStmtPart other = (UnknownStmtPart)o;
		return Objects.equals(source, other.source);
	}
	
	@Override
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(source);
		return i;
	}
	
	protected String getType() {
		String ret = getClass().getSimpleName().toUpperCase().replaceFirst(indStr, "");
		return ret.substring(0,ret.length() - 4);
	}
	
	@Override
	public String toRegexString() {
		return regexStr;
	}
	
	@Override
	public String toSuperSimpleString() {
		return unknownStr;
	}
	
	@Override
	public String toSimpleString() {
		if(stringCache2 == null)
			stringCache2 = sepStr + indStr + "[" + getType() + "]" + sepStr;
		return stringCache2;
	}
	
	@Override
	public String toString() {
		if(stringCache == null)
			stringCache = sepStr + indStr + "[TYPE=" + getType() + ", SOURCE=" + (source == null ? "null" : source.getSource().getSignature()) 
				+ ", STMT=" + (source == null ? "null" : source.getSignature()) + "]" + sepStr;
		return stringCache;
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
			if(source == null && ((AnyPartImpl)o).source == null) {
				return 0;
			} else if(source == null && ((AnyPartImpl)o).source != null) {
				return -1;
			} else if(source != null && ((AnyPartImpl)o).source == null) {
				return 1;
			} else {
				return source.compareTo(((AnyPartImpl)o).source);
			}
		} else {
			return getClass().getSimpleName().compareTo(o.getClass().getSimpleName());
		}
	}
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public UnknownStmtPart readXML(String filePath, Path path) throws Exception {
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
				SootUnitContainer.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(UnknownStmtPart.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
}
