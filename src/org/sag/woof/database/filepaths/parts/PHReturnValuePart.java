package org.sag.woof.database.filepaths.parts;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.sag.common.tuple.Triple;
import org.sag.soot.callgraph.IJimpleICFG;
import org.sag.soot.xstream.SootMethodContainer;
import org.sag.soot.xstream.SootUnitContainer;
import org.sag.soot.xstream.SootUnitContainerFactory;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;

@XStreamAlias("PHReturnValuePart")
public class PHReturnValuePart extends PHPart {
	
	@XStreamAlias("Source")
	private volatile SootUnitContainer source;
	@XStreamAlias("Target")
	private volatile SootMethodContainer target;
	
	@XStreamOmitField
	private volatile Stmt invokeStmt;
	@XStreamOmitField
	private volatile SootMethod invokeSource;
	@XStreamOmitField
	private volatile SootMethod targetMethod;
	@XStreamOmitField
	private volatile String cacheString;
	@XStreamOmitField
	private volatile String cacheLogString;
	
	public PHReturnValuePart(Stmt invokeStmt, SootMethod invokeSource, SootMethod targetMethod) {
		Objects.requireNonNull(invokeStmt);
		Objects.requireNonNull(invokeSource);
		Objects.requireNonNull(targetMethod);
		
		this.source = SootUnitContainerFactory.makeSootUnitContainer(invokeStmt, invokeSource);
		this.target = SootMethodContainer.makeSootMethodContainer(targetMethod);
		this.invokeStmt = invokeStmt;
		this.invokeSource = invokeSource;
		this.targetMethod = targetMethod;
	}
	
	public Stmt getInvokeStmt() {
		if(invokeStmt == null)
			invokeStmt = (Stmt)source.toUnit();
		return invokeStmt;
	}
	
	public SootMethod getInvokeSource() {
		if(invokeSource == null)
			invokeSource = source.getSource().toSootMethod();
		return invokeSource;
	}
	
	public SootMethod getTarget() {
		if(targetMethod == null)
			targetMethod = target.toSootMethod();
		return targetMethod;
	}
	
	@Override
	public boolean equals(Object o) {
		return super.equals(o) && Objects.equals(source, ((PHReturnValuePart)o).source) 
				&& Objects.equals(target, ((PHReturnValuePart)o).target);
	}
	
	@Override
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(source);
		i = i * 31 + Objects.hashCode(target);
		return i;
	}
	
	public String getAnalysisName() {
		return "Return Value Analysis";
	}
	
	@Override
	public String toString() {
		if(cacheString == null)
			cacheString = sepStr + indStr + "[TYPE=" + getType() + ", SOURCE=" + source.getSource().getSignature() 
				+ ", TARGET=" + target.getSignature() + ", STMT=" + source.getSignature() + "]" + sepStr;
		return cacheString;
	}
	
	@Override
	public String toLogString(String spacer) {
		if(cacheLogString == null) {
			StringBuilder sb = new StringBuilder();
			sb.append(spacer).append("Invoke Source: ").append(source.getSource().getSignature()).append("\n");
			sb.append(spacer).append("Target Method: ").append(target.getSignature()).append("\n");
			sb.append(spacer).append("Invoke Stmt: ").append(source.getSignature()).append("\n");
			cacheLogString = sb.toString();
		}
		return cacheLogString;
	}
	
	@Override
	public int compareTo(LeafPart o) {
		if(getClass() == o.getClass()) {
			int ret = source.compareTo(((PHReturnValuePart)o).source);
			if(ret == 0)
				ret = target.compareTo(((PHReturnValuePart)o).target);
			return ret;
		} else {
			return getClass().getSimpleName().compareTo(o.getClass().getSimpleName());
		}
	}
	
	@Override 
	public void appendStartData(StringBuilder sb, String spacer) {
		sb.append(spacer).append("Start Source: ").append(source.getSource().getSignature()).append("\n");
		sb.append(spacer).append("Start Target: ").append(target.getSignature()).append("\n");
		sb.append(spacer).append("Start Stmt: ").append(source.getSignature()).append("\n");
	}

	@Override
	public Set<Triple<SootMethod, Stmt, Value>> getStartPoints(IJimpleICFG icfg) {
		Set<Triple<SootMethod, Stmt, Value>> ret = new HashSet<>();
		for(Unit u : icfg.getEndPointsOf(getTarget())) {
			if(u instanceof ReturnStmt)
				ret.add(new Triple<>(getTarget(), (ReturnStmt)u, ((ReturnStmt)u).getOp()));
		}
		return ret;
	}
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public PHReturnValuePart readXML(String filePath, Path path) throws Exception {
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
				SootMethodContainer.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(PHReturnValuePart.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
}