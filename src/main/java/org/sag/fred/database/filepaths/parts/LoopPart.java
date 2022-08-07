package org.sag.fred.database.filepaths.parts;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("LoopPart")
public class LoopPart implements BranchPart {
	
	private static final AtomicLong idCount = new AtomicLong();
	
	public static final String indStr = "LOOP";
	private static final String loopStr = sepStr + indStr + sepStr;

	@XStreamAlias("Start")
	private volatile Node start;
	@XStreamAlias("Id")
	private volatile long id;
	
	public LoopPart(Part start) {
		this.start = new Node(start);
		this.id = idCount.incrementAndGet();
	}
	
	//Only for use with clone (start is set in clone)
	private LoopPart(long id) {
		this.start = null;
		this.id = id;
	}
	
	//Since these are nodes that cause loops we cannot compare the stored start part so we assign a
	//unique id to each loop part we create to represent the loop
	@Override
	public boolean equals(Object o) {
		if(o == this)
			return true;
		if(o == null || getClass() != o.getClass())
			return false;
		LoopPart other = (LoopPart)o;
		return id == other.id;
	}
	
	@Override
	public int hashCode() {
		return Long.hashCode(id);
	}
	
	@Override
	public String toRegexString() {
		throw new UnsupportedOperationException("Regex strings are not supported for loop parts.");
	}
	
	@Override
	public String toSuperSimpleString() {
		return loopStr;
	}
	
	@Override
	public String toSimpleString() {
		return sepStr + indStr + "[" + id + "]" + sepStr;
	}
	
	@Override
	public String toString() {
		return sepStr + indStr + "[ID=" + id + ", PARTHASH=" + start.hashCode() + "]" + sepStr;
	}
	
	@Override
	public Part cloneInner(Map<Part,Part> beforeAfterMap) {
		Part ret = beforeAfterMap.get(this);
		if(ret == null) {
			ret = new LoopPart(id);
			beforeAfterMap.put(this, ret);
			if(start != null)
				((LoopPart)ret).start = new Node(start.getPart().cloneInner(beforeAfterMap));
		}
		return ret;
	}
	
	public Part getLoopStart() {
		if(start == null)
			return null;
		return start.getPart();
	}
	
	public Node getLoopStartNode() {
		return start;
	}
	
	@Override
	public List<Part> getChildren() {
		if(start == null)
			return Collections.emptyList();
		return Collections.singletonList(start.getPart());
	}
	
	@Override
	public List<Node> getChildNodes() {
		if(start == null)
			return Collections.emptyList();
		return Collections.singletonList(start);
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
		if(child.equals(start)) {
			start = newChild;
			return true;
		}
		return false;
	}
	
	@Override
	public boolean removeChild(Node child) {
		Objects.requireNonNull(child);
		if(child.equals(start)) {
			start = null;
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
			return Collections.singleton(LoopPart.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
}
