package org.sag.woof.database.filepaths.parts;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.sag.common.tuple.Triple;
import org.sag.soot.callgraph.IJimpleICFG;
import org.sag.soot.xstream.SootUnitContainer;
import org.sag.soot.xstream.SootUnitContainerFactory;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import soot.SootMethod;
import soot.Value;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.Stmt;

@XStreamAlias("PHBaseValuePart")
public class PHBaseValuePart extends PHPart {

	@XStreamAlias("Source")
	private volatile SootUnitContainer source;
	
	@XStreamOmitField
	private volatile Stmt invokeStmt;
	@XStreamOmitField
	private volatile SootMethod invokeSource;
	@XStreamOmitField
	private volatile String cacheString;
	@XStreamOmitField
	private volatile String cacheLogString;
	
	public PHBaseValuePart(Stmt invokeStmt, SootMethod invokeSource) {
		Objects.requireNonNull(invokeStmt);
		Objects.requireNonNull(invokeSource);
		
		this.source = SootUnitContainerFactory.makeSootUnitContainer(invokeStmt, invokeSource);
		this.invokeStmt = invokeStmt;
		this.invokeSource = invokeSource;
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
	
	public SootUnitContainer getSource() {
		return source;
	}
	
	@Override
	public boolean equals(Object o) {
		return super.equals(o) && Objects.equals(source, ((PHBaseValuePart)o).source);
	}
	
	@Override
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(source);
		return i;
	}
	
	public String getAnalysisName() {
		return "Base Value Analysis";
	}
	
	@Override
	public String toString() {
		if(cacheString == null)
			cacheString = sepStr + indStr + "[TYPE=" + getType() + ", SOURCE=" + source.getSource().getSignature() 
				+ ", STMT=" + source.getSignature() + "]" + sepStr;
		return cacheString;
	}
	
	@Override
	public String toLogString(String spacer) {
		if(cacheLogString == null) {
			StringBuilder sb = new StringBuilder();
			sb.append(spacer).append("Invoke Source: ").append(source.getSource().getSignature()).append("\n");
			sb.append(spacer).append("Invoke Stmt: ").append(source.getSignature()).append("\n");
			cacheLogString = sb.toString();
		}
		return cacheLogString;
	}
	
	@Override 
	public void appendStartData(StringBuilder sb, String spacer) {
		sb.append(spacer).append("Start Source: ").append(source.getSource().getSignature()).append("\n");
		sb.append(spacer).append("Start Stmt: ").append(source.getSignature()).append("\n");
	}
	
	@Override
	public int compareTo(LeafPart o) {
		if(getClass() == o.getClass()) {
			return source.compareTo(((PHBaseValuePart)o).source);
		} else {
			return getClass().getSimpleName().compareTo(o.getClass().getSimpleName());
		}
	}
	
	@Override
	public Set<Triple<SootMethod, Stmt, Value>> getStartPoints(IJimpleICFG icfg) {
		return Collections.singleton(new Triple<>(getInvokeSource(), getInvokeStmt(), ((InstanceInvokeExpr)getInvokeStmt().getInvokeExpr()).getBase()));
	}
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public PHBaseValuePart readXML(String filePath, Path path) throws Exception {
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
			return Collections.singleton(PHBaseValuePart.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
}
