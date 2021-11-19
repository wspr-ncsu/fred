package org.sag.woof.database.filepaths.parts;

import java.io.ObjectStreamException;
import java.nio.file.Path;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.sag.common.xstream.NamedCollectionConverterWithSize;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("AppendPart")
public class AppendPart extends AbstractCollection<Part> implements BranchPart {
	
	public static final String divStr = " + ";
	
	@XStreamAlias("Children")
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"Child"},types={Node.class})
	private volatile ArrayList<Node> children;
	@XStreamOmitField
	private volatile ArrayList<Part> contents;
	
	public AppendPart() {
		children = new ArrayList<>();
		contents = new ArrayList<>();
	}
	
	protected Object readResolve() throws ObjectStreamException {
		contents = new ArrayList<>();
		for(Node n : children) {
			contents.add(n.getPart());
		}
		return this;
	}
	
	protected Object writeReplace() throws ObjectStreamException {
		return this;
	}
	
	public AppendPart(Part p) {
		this();
		add(p);
	}
	
	public AppendPart(Part...parts) {
		this();
		for(Part p : parts) {
			add(p);
		}
	}
	
	public boolean add(Part p) {
		Objects.requireNonNull(p);
		return contents.add(p) && children.add(new Node(p));
	}
	
	public int size() {
		return children.size();
	}
	
	@Override
	public Iterator<Part> iterator() {
		return new Iterator<Part>() {
			
			private int i = 0;

			@Override
			public boolean hasNext() {
				return i < contents.size();
			}

			@Override
			public Part next() {
				return contents.get(i++);
			}
			
			@Override
			public void remove() {
				contents.remove(i);
				children.remove(i);
			}
			
		};
	}
	
	@Override
	public boolean contains(Object o) {
		return contents.contains(o);
	}
	
	@Override
	public boolean containsAll(Collection<?> c) {
		return contents.containsAll(c);
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || getClass() != o.getClass())
			return false;
		AppendPart other = (AppendPart)o;
		return contents.equals(other.contents);
	}
	
	@Override
	public int hashCode() {
		return contents.hashCode();
	}
	
	@Override
	public String toRegexString() {
		StringBuilder sb = new StringBuilder();
		for(Part p : this) {
			//Skip LoopPart because they are not supported in the regex construction
			if(p instanceof LoopPart)
				continue;
			sb.append(p.toRegexString());
		}
		return sb.toString();
	}

	@Override
	public String toSuperSimpleString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean first = true;
		for(Part p : this) {
			if(first)
				first = false;
			else
				sb.append(divStr);
			sb.append(p.toSuperSimpleString());
		}
		sb.append("}");
		return sb.toString();
	}

	@Override
	public String toSimpleString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean first = true;
		for(Part p : this) {
			if(first)
				first = false;
			else
				sb.append(divStr);
			sb.append(p.toSimpleString());
		}
		sb.append("}");
		return sb.toString();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean first = true;
		for(Part p : this) {
			if(first)
				first = false;
			else
				sb.append(divStr);
			sb.append(p.toString());
		}
		sb.append("}");
		return sb.toString();
	}
	
	@Override
	public Part cloneInner(Map<Part,Part> beforeAfterMap) {
		Part ret = beforeAfterMap.get(this);
		if(ret == null) {
			ret = new AppendPart();
			//Always add the new part to the map so that in cases of loops we know we have already been here
			beforeAfterMap.put(this, ret);
			for(Part p : this) {
				//Assumes all cloneInner parts update the beforeAfterMap as needed
				//and that they return the data from beforeAfterMap immediately if it exists
				((AppendPart)ret).add(p.cloneInner(beforeAfterMap));
			}
		}
		return ret;
	}
	
	@Override
	public List<Part> getChildren() {
		return new ArrayList<>(contents);
	}
	
	@Override
	public List<Node> getChildNodes() {
		return new ArrayList<>(children);
	}
	
	@Override
	public boolean swapChild(Node child, Part newChild) {
		Objects.requireNonNull(child);
		Objects.requireNonNull(newChild);
		return swapChild(child, new Node(newChild));
	}
	
	@Override
	public boolean swapChild(Node child, Node newChild) {
		Objects.requireNonNull(child);
		Objects.requireNonNull(newChild);
		int index = children.indexOf(child);
		if(index >= 0) {
			children.set(index, newChild);
			contents.set(index, newChild.getPart());
		}
		return false;
	}
	
	@Override
	public boolean removeChild(Node child) {
		Objects.requireNonNull(child);
		int index = children.indexOf(child);
		if(index >= 0) {
			children.remove(index);
			contents.remove(index);
			return true;
		}
		return false;
	}
	
	//Forces should always be handled before this is called
	@Override
	public boolean mergeChild(Node childNode) {
		Objects.requireNonNull(childNode);
		if(childNode.getPart() instanceof AppendPart) {
			int index = children.indexOf(childNode);
			if(index >= 0) {
				AppendPart child = (AppendPart)childNode.getPart();
				children.addAll(index, child.getChildNodes());
				contents.addAll(index, child.getChildren());
				removeChild(childNode);
				return true;
			}
		}
		return false;
	}
	
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
			return Collections.singleton(AppendPart.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}

}
