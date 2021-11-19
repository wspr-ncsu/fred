package org.sag.woof.database.filepaths.parts;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import org.sag.common.tuple.Pair;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("Part")
public interface Part extends XStreamInOutInterface {

	public static final String sepStr = "`";
	
	public String toRegexString();
	public String toSuperSimpleString();
	public String toSimpleString();
	public String toString();
	
	/** Creates a clone of the current object if needed. If leaf nodes
	 * (i.e. those without references to other nodes) this just returns
	 * the current object since there is no need to create a copy. For
	 * the rest (e.g. OrPart) a copy is created so that changes can be
	 * made without affecting other parts.
	 */
	default Part clonePart() {
		return cloneInner(new HashMap<>());
	}
	
	/** Creates a clone of the current object and adds it to the beforeAfterMap where
	 * the key is the object being cloned and the value is the object that was cloned.
	 * It then returns the cloned object. If the object is a leaf node (i.e. those
	 * without references to other nodes) then the cloned and original object are
	 * the same. For the rest, (e.g. OrPart and LoopPart) a copy is created so that
	 * changes can be made outside of the tree represented by the root node (i.e. the
	 * Part we first called clone on). Note if the original objects already exists in 
	 * the beforeAfterMap then the value for this object in the map is returned and no
	 * updates are made to the beforeAfterMap. This is to prevent going in loops especially
	 * when dealing with LoopParts but also reduces the number of copies of objects that we
	 * have in relation to the starting Part (i.e. the part we first called clone on).
	 */
	public Part cloneInner(Map<Part,Part> beforeAfterMap);
	
	default PartIterator getIterator() {
		return new PartIterator(this,false);
	}
	
	default PartIterator getIteratorWithLoops() {
		return new PartIterator(this,true,false);
	}
	
	default PartIterator getIteratorWithLoopsVisitOnce() {
		return new PartIterator(this,true,true,false);
	}
	
	default PartIterator getIteratorDFS() {
		return new PartIterator(this,true);
	}
	
	default PartIterator getIteratorWithLoopsDFS() {
		return new PartIterator(this,true,true);
	}
	
	default PartIterator getIteratorWithLoopsVisitOnceDFS() {
		return new PartIterator(this,true,true,true);
	}
	
	default PostOrderPartIterator getPostOrderIterator() {
		return new PostOrderPartIterator(this);
	}
	
	@XStreamAlias("Node")
	public final static class Node implements XStreamInOutInterface {
		
		@XStreamAlias("Part")
		private volatile Part part;
		
		public Node(Part part) {
			this.part = part;
		}
		
		public Part getPart() {
			return part;
		}
		
		public String toString() {
			return Objects.toString(part);
		}
		
		@Override
		public void writeXML(String filePath, Path path) throws Exception {
			throw new UnsupportedOperationException();
		}
		
		public Node readXML(String filePath, Path path) throws Exception {
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
				return Collections.singleton(Node.class);
			}
			
			@Override
			public void setXStreamOptions(XStream xstream) {
				defaultOptionsXPathRelRef(xstream);
			}
			
		}
		
	}
	
	public static class XStreamSetup extends AbstractXStreamSetup {
		
		private static AbstractXStreamSetup xstreamSetup = null;
		
		public static AbstractXStreamSetup getXStreamSetupStatic(){
			if(xstreamSetup == null)
				xstreamSetup = new XStreamSetup();
			return xstreamSetup;
		}
		
		@Override
		public void getOutputGraph(LinkedHashSet<AbstractXStreamSetup> in) {
			if(!in.contains(this)) {
				in.add(this);
				AnyPartImpl.getXStreamSetupStatic().getOutputGraph(in);
				AnyInfoPart.getXStreamSetupStatic().getOutputGraph(in);
				UnknownStmtPart.getXStreamSetupStatic().getOutputGraph(in);
				UnknownValuePart.getXStreamSetupStatic().getOutputGraph(in);
				ConstantPart.getXStreamSetupStatic().getOutputGraph(in);
				SysVarPart.getXStreamSetupStatic().getOutputGraph(in);
				ParentPart.getXStreamSetupStatic().getOutputGraph(in);
				NormalizePart.getXStreamSetupStatic().getOutputGraph(in);
				NamePart.getXStreamSetupStatic().getOutputGraph(in);
				LoopPart.getXStreamSetupStatic().getOutputGraph(in);
				EnvVarPart.getXStreamSetupStatic().getOutputGraph(in);
				AppendPart.getXStreamSetupStatic().getOutputGraph(in);
				OrPart.getXStreamSetupStatic().getOutputGraph(in);
				PHPart.getXStreamSetupStatic().getOutputGraph(in);
				AnyComboPart.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			Set<Class<?>> ret = new HashSet<>();
			ret.add(Part.class);
			ret.add(AnyPart.class);
			ret.add(BranchPart.class);
			ret.add(UnknownPart.class);
			ret.add(LeafPart.class);
			return ret;
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
	public static class PartIterator implements Iterator<Pair<Part,Node>> {
		
		private final ArrayDeque<Pair<Part,Node>> queue;
		private volatile Set<Pair<Part,Node>> visited;
		private final boolean includeLoops;
		private final boolean visitOnce;
		private final boolean dfs;
		private volatile Pair<Part,Node> next;
		
		public PartIterator(Part start, boolean dfs) {
			this(start,false,false,dfs);
		}
		
		public PartIterator(Part start, boolean includeLoops, boolean dfs) {
			this(start,true,false,dfs);
		}
		
		public PartIterator(Part start, boolean includeLoops, boolean visitOnce, boolean dfs) {
			Objects.requireNonNull(start);
			this.queue = new ArrayDeque<>();
			add(new Pair<>(null,new Node(start)));
			this.includeLoops = includeLoops;
			this.visitOnce = includeLoops ? visitOnce : false;
			this.dfs = dfs;
			if(visitOnce) {
				visited = new HashSet<>();
			}
			findNext();
		}
		
		private void add(Pair<Part,Node> toAdd) {
			if(dfs)
				this.queue.push(toAdd);
			else
				this.queue.add(toAdd);
		}
		
		private Pair<Part,Node> removeNext() {
			return this.queue.poll();
		}
		
		private void findNext() {
			Pair<Part,Node> p = removeNext();
			if(visitOnce) {
				while(p != null && !visited.add(p))
					p = removeNext();
			}
			next = p;
		}

		@Override
		public boolean hasNext() {
			return next != null;
		}

		@Override
		public Pair<Part,Node> next() {
			if(next == null)
				throw new NoSuchElementException();
			Pair<Part,Node> ret = next;
			Node childNode = ret.getSecond();
			Part child = childNode.getPart();
			if(child instanceof BranchPart && (!(child instanceof LoopPart) || includeLoops)) {
				for(Node n : ((BranchPart)child).getChildNodes()) {
					add(new Pair<>(child,n));
				}
			}
			findNext();
			return ret;
		}
		
	}
	
	public static class PostOrderPartIterator implements Iterator<Pair<Part,Node>> {
		
		private final ArrayDeque<Pair<Node,Node>> queue;
		private final Set<Pair<Node,Node>> visited;
		private final Set<Pair<Node,Node>> seenBranch;
		private volatile Pair<Node,Node> next;
		
		public PostOrderPartIterator(Part start) {
			Objects.requireNonNull(start);
			this.queue = new ArrayDeque<>();
			this.visited = new HashSet<>();
			this.seenBranch = new HashSet<>();
			add(new Pair<>(null,new Node(start)));
			findNext();
		}
		
		private void add(Pair<Node,Node> toAdd) {
			this.queue.push(toAdd);
		}
		
		private Pair<Node,Node> removeNext() {
			return this.queue.poll();
		}
		
		//Assumes no cycles, if cycles (outside of those listed by LoopPart) then branch parts may be visited before their children are
		//But the iterator will not get stuck in a loop itself
		private void findNext() {
			Pair<Node,Node> cur;
			while((cur = removeNext()) != null) {
				Node childNode = cur.getSecond();
				Part child = childNode.getPart();
				if(child instanceof BranchPart && !(child instanceof LoopPart)) {
					if(seenBranch.add(cur)) {
						add(cur);
						for(Node n : ((BranchPart)child).getChildNodes())
							add(new Pair<>(childNode,n));
					} else {
						if(visited.add(cur))
							break;
					}
				} else { //LeafPart or LoopPart
					if(visited.add(cur))
						break;
				}
			}
			next = cur;
		}
		
		@Override
		public boolean hasNext() {
			return next != null;
		}
		
		@Override
		public Pair<Part,Node> next() {
			if(next == null)
				throw new NoSuchElementException();
			Pair<Part,Node> ret = new Pair<>(next.getFirst() ==  null ? null : next.getFirst().getPart(),next.getSecond());
			findNext();
			return ret;
		}
		
	}
	
}
