package org.sag.fred.database.filepaths.parts;

import java.nio.file.Path;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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

@XStreamAlias("OrPart")
public class OrPart extends AbstractCollection<Part> implements BranchPart {
	
	public static final String divStr = " | ";
	
	@XStreamAlias("Children")
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"Child"},types={Node.class})
	private volatile ArrayList<Node> children;
	@XStreamOmitField
	private volatile Set<Part> contents;
	
	public OrPart() {
		children = new ArrayList<>();
		contents = new HashSet<>();
	}
	
	public OrPart(Part p) {
		this();
		add(p);
	}
	
	public OrPart(Part...parts) {
		this();
		for(Part p : parts) {
			add(p);
		}
	}
	
	//Can't do readResolve because then XStream would try to resolve contents before all parts are read in
	//resulting in null pointer exceptions in the hashCode method. Recursive references in HashSets are not
	//supported in readResolve and in HashSets stored in the XML.
	private Set<Part> getContents() {
		if(contents == null) {
			contents = new HashSet<>();
			for(Node n : children) {
				contents.add(n.getPart());
			}
		}
		return contents;
	}
	
	public boolean add(Part p) {
		Objects.requireNonNull(p);
		if(getContents().add(p)) {
			return children.add(new Node(p));
		}
		return false;
	}
	
	public int size() {
		return children.size();
	}
	
	@Override
	public Iterator<Part> iterator() {
		return new Iterator<Part>() {
			
			private Iterator<Node> it = children.iterator();
			private Node cur = null;

			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public Part next() {
				cur = it.next();
				return cur.getPart();
			}
			
			@Override
			public void remove() {
				it.remove();
				getContents().remove(cur.getPart());
			}
			
		};
	}
	
	@Override
	public boolean contains(Object o) {
		return getContents().contains(o);
	}
	
	@Override
	public boolean containsAll(Collection<?> c) {
		return getContents().containsAll(c);
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || getClass() != o.getClass())
			return false;
		OrPart other = (OrPart)o;
		return getContents().equals(other.getContents());
	}
	
	@Override
	public int hashCode() {
		return getContents().hashCode();
	}

	@Override
	public String toRegexString() {
		Set<String> strs = new HashSet<>();
		for(Part p : this) {
			//Skip LoopPart because they are not supported in the regex construction
			if(!(p instanceof LoopPart))
				strs.add(p.toRegexString());
		}
		//For when they are all loops so nothing gets added
		if(strs.isEmpty())
			return "";
		strs = SortingMethods.sortSet(strs);
		StringBuilder sb = new StringBuilder();
		sb.append("(?:");
		boolean first = true;
		for(String s : strs) {
			if(first)
				first = false;
			else
				sb.append("|");
			sb.append(s);
		}
		sb.append(")");
		return sb.toString();
	}

	@Override
	public String toSuperSimpleString() {
		Set<String> strs = new HashSet<>();
		for(Part p : this) {
			strs.add(p.toSuperSimpleString());
		}
		strs = SortingMethods.sortSet(strs);
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		boolean first = true;
		for(String s : strs) {
			if(first)
				first = false;
			else
				sb.append(divStr);
			sb.append(s);
		}
		sb.append(")");
		return sb.toString();
	}

	@Override
	public String toSimpleString() {
		Set<String> strs = new HashSet<>();
		for(Part p : this) {
			strs.add(p.toSimpleString());
		}
		strs = SortingMethods.sortSet(strs);
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		boolean first = true;
		for(String s : strs) {
			if(first)
				first = false;
			else
				sb.append(divStr);
			sb.append(s);
		}
		sb.append(")");
		return sb.toString();
	}
	
	@Override
	public String toString() {
		Set<String> strs = new HashSet<>();
		for(Part p : this) {
			strs.add(p.toString());
		}
		strs = SortingMethods.sortSet(strs);
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		boolean first = true;
		for(String s : strs) {
			if(first)
				first = false;
			else
				sb.append(divStr);
			sb.append(s);
		}
		sb.append(")");
		return sb.toString();
	}
	
	@Override
	public Part cloneInner(Map<Part,Part> beforeAfterMap) {
		Part ret = beforeAfterMap.get(this);
		if(ret == null) {
			ret = new OrPart();
			//Always add the new part to the map so that in cases of loops we know we have already been here
			beforeAfterMap.put(this, ret);
			for(Part p : this) {
				//Assumes all cloneInner parts update the beforeAfterMap as needed
				//and that they return the data from beforeAfterMap immediately if it exists
				Part newChild = p.cloneInner(beforeAfterMap);
				if(newChild == null)
					System.out.println(p.toString());
				((OrPart)ret).add(newChild);
			}
		}
		return ret;
	}
	
	@Override
	public List<Part> getChildren() {
		List<Part> ret = new ArrayList<>();
		for(Node n : children)
			ret.add(n.getPart());
		return ret;
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
			if(getContents().add(newChild.getPart())) {
				children.set(index, newChild);
				getContents().remove(child.getPart());
				return true;
			} else {
				children.remove(index);
			}
		}
		return false;
	}
	
	@Override
	public boolean removeChild(Node child) {
		Objects.requireNonNull(child);
		boolean removed = children.remove(child);
		if(removed) {
			getContents().remove(child.getPart());
		}
		return removed;
	}
	
	@Override
	public boolean mergeChild(Node childNode) {
		Objects.requireNonNull(childNode);
		if(childNode.getPart() instanceof OrPart) {
			int index = children.indexOf(childNode);
			if(index >= 0) {
				OrPart child = (OrPart)childNode.getPart();
				List<Node> toInsert = new ArrayList<>();
				for(Node n : child.getChildNodes()) {
					if(getContents().add(n.getPart()))
						toInsert.add(n);
				}
				children.addAll(index, toInsert);
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
			return Collections.singleton(OrPart.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}

}
