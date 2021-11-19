package org.sag.woof.database.filepaths.parts;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.sag.common.tools.SortingMethods;
import org.sag.soot.SootSort;
import org.sag.soot.xstream.SootMethodContainer;
import org.sag.soot.xstream.SootUnitContainer;
import org.sag.soot.xstream.SootUnitContainerFactory;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import soot.SootMethod;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.Stmt;

/** A part the represents the fact that the value may be any string. */
@XStreamAlias("AnyPartImpl")
public abstract class AnyPartImpl implements LeafPart, AnyPart {
	
	@XStreamOmitField
	protected volatile String stringCache;
	@XStreamOmitField
	protected volatile String stringCache2;
	@XStreamAlias("Source")
	protected volatile SootUnitContainer source;
	
	public AnyPartImpl(Stmt stmt, SootMethod sourceMethod) {
		if(stmt == null || sourceMethod == null)
			this.source = null;
		else
			this.source = SootUnitContainerFactory.makeSootUnitContainer(stmt, sourceMethod);
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || !Objects.equals(getClass(), o.getClass()))
			return false;
		AnyPartImpl other = (AnyPartImpl)o;
		return Objects.equals(source, other.source);
	}
	
	@Override
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(source);
		return i;
	}
	
	/** Returns the statement for this part. */
	public Stmt getStmt() {
		if(source == null)
			return null;
		return (Stmt)source.toUnit();
	}
	
	/** Returns the source method of the statement for this part. */
	public SootMethod getSourceMethod() {
		if(source == null)
			return null;
		return source.getSource().toSootMethod();
	}
	
	public String getSourceMethodSig() {
		if(source == null || source.getSource() == null || source.getSource().getSignature() == null)
			return null;
		return source.getSource().getSignature();
	}
	
	public String getStmtSig() {
		if(source == null || source.getSignature() == null)
			return null;
		return source.getSignature();
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
		return "[TYPE=" + getType() + ", SOURCE=" + (source == null ? "null" : source.getSource().getSignature()) + ", STMT=" 
				+ (source == null ? "null" : source.getSignature()) + "]";
	}
	
	/** Returns the string `ANY[?]` where ? is a representation of the stmt for this AnyPart object. */
	@Override
	public String toString() {
		if(stringCache == null)
			stringCache = sepStr + indStr + getDataSegmentString() + sepStr;
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
	
	/** Convince method for retrieving the right hand value of the definition statement which is 
	 * what should contain the actual data we are interested in. This assumed the statement is 
	 * a definition statement. This may change in the future.
	 */
	public Value getValue() {
		Stmt stmt = getStmt();
		if(stmt != null && stmt instanceof DefinitionStmt) {
			return ((DefinitionStmt)stmt).getRightOp();
		}
		return null;
	}
	
	protected int compareToInner(LeafPart o) {
		return 0;
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
				int ret = source.compareTo(((AnyPartImpl)o).source);
				if(ret == 0)
					ret = compareToInner(o);
				return ret;
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
	public AnyPartImpl readXML(String filePath, Path path) throws Exception {
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
				SootMethodContainer.getXStreamSetupStatic().getOutputGraph(in);
				SootUnitContainer.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			Set<Class<?>> ret = new HashSet<>();
			ret.add(AnyPartImpl.class);
			ret.add(AnyAPKInfoPart.class);
			ret.add(AnyUserIdPart.class);
			ret.add(AnyAccountIdPart.class);
			ret.add(AnyVMRuntimeSettingPart.class);
			ret.add(AnyCommandLineInputPart.class);
			ret.add(AnyResourceOrAssetPart.class);
			ret.add(AnyNumberPart.class);
			ret.add(AnyChildPathPart.class);
			ret.add(AnyParentPathPart.class);
			ret.add(AnyEPArgPart.class);
			ret.add(AnyNewInvokePart.class);
			ret.add(AnyMethodRefPart.class);
			ret.add(AnyMethodReturnPart.class);
			ret.add(AnyFieldRefPart.class);
			ret.add(AnyArrayPart.class);
			return ret;
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
	/** A part representing the situation where a local is assigned anything
	 * from an array as we do not track data in Arrays.
	 */
	@XStreamAlias("AnyArrayPart")
	public final static class AnyArrayPart extends AnyPartImpl {
		
		public AnyArrayPart(Stmt stmt, SootMethod sourceMethod) {
			super(stmt, sourceMethod);
		}
		
	}
	
	/** A part representing the situation where a local is assigned a value
	 * from a Field as we do not currently track fields.
	 */
	@XStreamAlias("AnyFieldRefPart")
	public final static class AnyFieldRefPart extends AnyPartImpl {
		
		public AnyFieldRefPart(Stmt stmt, SootMethod sourceMethod) {
			super(stmt, sourceMethod);
		}
		
		public String getFieldSig() {
			String ret = getStmtSig();
			if(ret == null)
				return null;
			return ret.substring(ret.indexOf('<'));
		}
		
	}
	
	/** This part represents the situation where a method is invoked and its return value is used
	 * in a way we care about but the target method has no method body so we have no way of knowing
	 * what the return value is.
	 */
	@XStreamAlias("AnyMethodReturnPart")
	public final static class AnyMethodReturnPart extends AnyPartImpl {
		
		@XStreamAlias("Target")
		private volatile SootMethodContainer target;
		
		public AnyMethodReturnPart(Stmt stmt, SootMethod sourceMethod, SootMethod targetMethod) {
			super(stmt, sourceMethod);
			if(target == null)
				this.target = null;
			this.target = SootMethodContainer.makeSootMethodContainer(targetMethod);
		}
		
		@Override
		public boolean equals(Object o) {
			return super.equals(o) && Objects.equals(target, ((AnyMethodReturnPart)o).target);
		}
		
		@Override
		public int hashCode() {
			return super.hashCode() * 31 + Objects.hashCode(target);
		}
		
		@Override
		public String getDataSegmentString() {
			return "[TYPE=" + getType() + ", SOURCE=" + (source == null ? "null" : source.getSource().getSignature()) 
					+ ", TARGET=" + (target == null ? "null" : target.getSignature()) 
					+ ", STMT=" + (source == null ? "null" : source.getSignature()) + "]";
		}
		
		public SootMethod getTargetMethod() {
			if(target == null)
				return null;
			return target.toSootMethod();
		}
		
		public String getTargetMethodSig() {
			if(target == null)
				return null;
			return target.getSignature();
		}
		
		@Override
		protected int compareToInner(LeafPart o) {
			if(target == null && ((AnyMethodReturnPart)o).target == null)
				return 0;
			else if(target == null && ((AnyMethodReturnPart)o).target != null)
				return -1;
			else if(target != null && ((AnyMethodReturnPart)o).target == null)
				return 1;
			else
				return target.compareTo(((AnyMethodReturnPart)o).target);
		}
		
	}
	
	/** This part represents the situation where a method is invoked and its
	 * return value is used in a way we care about but there are no outgoing
	 * edges in the call graph for whatever reason so we have no way of knowing
	 * what the return value is.
	 */
	@XStreamAlias("AnyMethodRefPart")
	public final static class AnyMethodRefPart extends AnyPartImpl {
		
		public AnyMethodRefPart(Stmt stmt, SootMethod sourceMethod) {
			super(stmt, sourceMethod);
		}
		
	}
	
	@XStreamAlias("AnyNewInvokePart")
	public final static class AnyNewInvokePart extends AnyPartImpl {
		
		public AnyNewInvokePart(Stmt stmt, SootMethod sourceMethod) {
			super(stmt, sourceMethod);
		}
		
	}
	
	/** This part represents the situation where a the value resolves to an argument
	 * that is passed into the entry point which could be anything.
	 */
	@XStreamAlias("AnyEPArgPart")
	public final static class AnyEPArgPart extends AnyPartImpl {
		
		@XStreamAlias("Index")
		private volatile int index;
		@XStreamAlias("SourceMethodSig")
		private volatile String sourceMethodSig;
		@XStreamAlias("StmtSig")
		private volatile String stmtSig;
		
		public AnyEPArgPart(Stmt stmt, SootMethod sourceMethod, int index) {
			super(stmt, sourceMethod);
			this.index = index;
			this.sourceMethodSig = null;
			this.stmtSig = null;
		}
		
		public AnyEPArgPart(String sourceMethodSig, String stmtSig, int index) {
			super(null,null);
			this.index = index;
			this.sourceMethodSig = sourceMethodSig;
			this.stmtSig = stmtSig;
		}
		
		@Override
		public boolean equals(Object o) {
			return super.equals(o) && index == ((AnyEPArgPart)o).index && Objects.equals(sourceMethodSig, ((AnyEPArgPart)o).sourceMethodSig)
					&& Objects.equals(stmtSig, ((AnyEPArgPart)o).stmtSig);
		}
		
		@Override
		public int hashCode() {
			int i = super.hashCode();
			i = i * 31 + index;
			i = i * 31 + Objects.hashCode(sourceMethodSig);
			i = i * 31 + Objects.hashCode(stmtSig);
			return i;
		}
		
		@Override
		public String getDataSegmentString() {
			StringBuilder sb = new StringBuilder();
			sb.append("[TYPE=").append(getType()).append(", SOURCE=");
			if(source == null) {
				sb.append(Objects.toString(sourceMethodSig));
			} else {
				sb.append(source.getSource().getSignature());
			}
			sb.append(", INDEX=").append(index).append(", STMT=");
			if(source == null) {
				sb.append(Objects.toString(stmtSig));
			} else {
				sb.append(source.getSignature());
			}
			sb.append("]");
			return sb.toString();
		}
		
		public int getIndex() {
			return index;
		}
		
		@Override
		public String getSourceMethodSig() {
			String ret = super.getSourceMethodSig();
			if(ret == null)
				return sourceMethodSig;
			return ret;
		}
		
		@Override
		public String getStmtSig() {
			String ret = super.getStmtSig();
			if(ret == null)
				return stmtSig;
			return ret;
		}
		
		@Override
		protected int compareToInner(LeafPart o) {
			int ret = Integer.compare(index, ((AnyEPArgPart)o).index);
			if(ret == 0)
				ret = SootSort.smStringComp.compare(sourceMethodSig, ((AnyEPArgPart)o).sourceMethodSig);
			if(ret == 0)
				ret = SortingMethods.sComp.compare(stmtSig, ((AnyEPArgPart)o).stmtSig);
			return ret;
		}
		
	}
	
	@XStreamAlias("AnyParentPathPart")
	public final static class AnyParentPathPart extends AnyPartImpl {
		
		public AnyParentPathPart(Stmt stmt, SootMethod sourceMethod) {
			super(stmt, sourceMethod);
		}
		
		protected String getDataSegmentString() {
			return "[" + getType() + "]";
		}
		
	}
	
	@XStreamAlias("AnyChildPathPart")
	public final static class AnyChildPathPart extends AnyPartImpl {
		
		public AnyChildPathPart(Stmt stmt, SootMethod sourceMethod) {
			super(stmt, sourceMethod);
		}
		
		protected String getDataSegmentString() {
			return "[" + getType() + "]";
		}
		
	}
	
	@XStreamAlias("AnyNumberPart")
	public final static class AnyNumberPart extends AnyPartImpl {
		
		@XStreamAlias("Type")
		private volatile String type;
		
		public AnyNumberPart(Stmt stmt, SootMethod sourceMethod, String type) {
			super(stmt, sourceMethod);
			this.type = type;
		}
		
		@Override
		public boolean equals(Object o) {
			return super.equals(o) && Objects.equals(type, ((AnyNumberPart)o).type);
		}
		
		@Override
		public int hashCode() {
			return super.hashCode() * 31 + Objects.hashCode(type);
		}
		
		@Override
		public String toRegexString() {
			return "\\d+";
		}
		
		@Override
		public String toSuperSimpleString() {
			return "NUM";
		}
		
		protected String getType() {
			return type.toUpperCase();
		}
		
		@Override
		public String toSimpleString() {
			if(stringCache2 == null)
				stringCache2 = sepStr + "NUM" + "[" + getType() + "]" + sepStr;
			 return stringCache2;
		}
		
		public String toString() {
			if(stringCache == null)
				stringCache = sepStr + "NUM" + "[TYPE=" + getType() + ", SOURCE=" + (source == null ? "null" : source.getSource().getSignature()) 
					+ ", STMT=" + (source == null ? "null" : source.getSignature()) + "]" + sepStr;
			return stringCache;
		}
		
		@Override
		protected int compareToInner(LeafPart o) {
			return SortingMethods.sComp.compare(type, ((AnyNumberPart)o).type);
		}
		
	}
	
	@XStreamAlias("AnyResourceOrAssetPart")
	public final static class AnyResourceOrAssetPart extends AnyPartImpl {
		
		public AnyResourceOrAssetPart(Stmt stmt, SootMethod sourceMethod) {
			super(stmt, sourceMethod);
		}
		
	}
	
	@XStreamAlias("AnyCommandLineInputPart")
	public final static class AnyCommandLineInputPart extends AnyPartImpl {
		
		public AnyCommandLineInputPart(Stmt stmt, SootMethod sourceMethod) {
			super(stmt, sourceMethod);
		}
		
	}
	
	@XStreamAlias("AnyVMRuntimeSettingPart")
	public final static class AnyVMRuntimeSettingPart extends AnyPartImpl {
		
		public AnyVMRuntimeSettingPart(Stmt stmt, SootMethod sourceMethod) {
			super(stmt, sourceMethod);
		}
		
	}
	
	@XStreamAlias("AnyAccountIdPart")
	public final static class AnyAccountIdPart extends AnyPartImpl {
		
		public AnyAccountIdPart(Stmt stmt, SootMethod sourceMethod) {
			super(stmt, sourceMethod);
		}
		
		@Override
		public String toRegexString() {
			return "\\d+";
		}
		
	}
	
	@XStreamAlias("AnyUserIdPart")
	public final static class AnyUserIdPart extends AnyPartImpl {
		
		public AnyUserIdPart(Stmt stmt, SootMethod sourceMethod) {
			super(stmt, sourceMethod);
		}
		
		@Override
		public String toRegexString() {
			return "\\d+";
		}
		
	}
	
	@XStreamAlias("AnyUIDPart")
	public final static class AnyUIDPart extends AnyPartImpl {
		
		public AnyUIDPart(Stmt stmt, SootMethod sourceMethod) {
			super(stmt, sourceMethod);
		}
		
		@Override
		public String toRegexString() {
			return "\\d+";
		}
		
	}
	
	@XStreamAlias("AnyAPKInfoPart")
	public final static class AnyAPKInfoPart extends AnyPartImpl {
		
		public AnyAPKInfoPart(Stmt stmt, SootMethod sourceMethod) {
			super(stmt, sourceMethod);
		}
		
	}

}
