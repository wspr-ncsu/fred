package org.sag.woof.database.filepaths.parts;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("SysVarPart")
public class SysVarPart implements BranchPart, AnyPart {
	
	@XStreamAlias("Child")
	private volatile Node child;
	
	public static final String indStr = "SYSVAR";
	private static final String anyStr = sepStr + indStr + sepStr;
	
	public SysVarPart(Part child) {
		this.child = new Node(child);
	}
	
	private SysVarPart() {
		this.child = null;
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || getClass() != o.getClass())
			return false;
		SysVarPart other = (SysVarPart)o;
		return Objects.equals(getChild(), other.getChild());
	}
	
	@Override
	public int hashCode() {
		return Objects.hashCode(getChild());
	}
	
	@Override
	public String toRegexString() {
		return regexStr;
	}
	
	@Override
	public String toSuperSimpleString() {
		return anyStr;
	}
	
	@Override
	public String toSimpleString() {
		StringBuilder sb = new StringBuilder();
		Part p = getChild();
		sb.append(indStr).append("[");
		if(p != null) {
			sb.append(p.toSimpleString());
		}
		sb.append("]");
		return sb.toString();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		Part p = getChild();
		sb.append(indStr).append("[");
		if(p != null) {
			sb.append(p.toString());
		}
		sb.append("]");
		return sb.toString();
	}
	
	@Override
	public Part cloneInner(Map<Part,Part> beforeAfterMap) {
		Part ret = beforeAfterMap.get(this);
		if(ret == null) {
			ret = new SysVarPart();
			//Always add the new part to the map so that in cases of loops we know we have already been here
			beforeAfterMap.put(this, ret);
			((SysVarPart)ret).child = new Node(getChild().cloneInner(beforeAfterMap));
		}
		return ret;
	}
	
	public Part getChild() {
		if(child == null)
			return null;
		return child.getPart();
	}
	
	public Node getChildNode() {
		return child;
	}
	
	@Override
	public List<Part> getChildren() {
		if(child == null)
			return Collections.emptyList();
		return Collections.singletonList(child.getPart());
	}
	
	@Override
	public List<Node> getChildNodes() {
		if(child == null)
			return Collections.emptyList();
		return Collections.singletonList(child);
	}
	
	@Override
	public boolean swapChild(Node child, Part newChild) {
		Objects.requireNonNull(child);
		Objects.requireNonNull(newChild);
		return swapChild(child,new Node(newChild));
	}
	
	@Override
	public boolean swapChild(Node child, Node newChild) {
		Objects.requireNonNull(child);
		Objects.requireNonNull(newChild);
		if(child.equals(this.child)) {
			this.child = newChild;
			return true;
		}
		return false;
	}
	
	@Override
	public boolean removeChild(Node child) {
		Objects.requireNonNull(child);
		if(child.equals(this.child)) {
			this.child = null;
			return true;
		}
		return false;
	}
	
	@Override
	public boolean mergeChild(Node childNode) { return false; }
	
	@Override
	public boolean add(Part p) { return false; }
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public Part readXML(String filePath, Path path) throws Exception {
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
				Node.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(SysVarPart.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}

}
