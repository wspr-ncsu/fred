package org.sag.fred.database.filepaths.parts;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.sag.common.tuple.Triple;
import org.sag.soot.analysis.AdvLocalDefs;
import org.sag.soot.callgraph.IJimpleICFG;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import soot.Local;
import soot.SootMethod;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.Stmt;

@XStreamAlias("PHPart")
public abstract class PHPart implements LeafPart {
	
	private static final String regexStr = ".*";
	public static final String indStr = "PH";
	private static final String phStr = sepStr + indStr + sepStr;
	
	@XStreamOmitField
	private volatile String cacheString2;
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || o.getClass() != getClass())
			return false;
		return true;
	}
	
	@Override
	public abstract int hashCode();
	
	public abstract String getAnalysisName();
	
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
		return phStr;
	}
	
	@Override
	public String toSimpleString() {
		if(cacheString2 == null)
			cacheString2 = sepStr + indStr + "[" + getType() + "]" + sepStr;
		return cacheString2;
	}
	
	@Override
	public abstract String toString();
	public abstract String toLogString(String spacer);
	public abstract Set<Triple<SootMethod,Stmt,Value>> getStartPoints(IJimpleICFG icfg);
	public abstract void appendStartData(StringBuilder sb, String spacer);
	
	@Override
	public Part cloneInner(Map<Part,Part> beforeAfterMap) {
		Part ret = beforeAfterMap.get(this);
		if(ret == null) {
			beforeAfterMap.put(this, this);
			ret = this;
		}
		return ret;
	}
	
	public Set<DefinitionStmt> getDefs(IJimpleICFG icfg, SootMethod sourceMethod, Stmt stmt, Value v) {
		Local l = (Local)v;
		AdvLocalDefs adv = icfg.getOrMakeLocalDefs(sourceMethod);
		return adv.getDefsWithAliasesRemoveLocalAndCast(l, stmt);
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
				PHArgumentValuePart.getXStreamSetupStatic().getOutputGraph(in);
				PHArrayValuePart.getXStreamSetupStatic().getOutputGraph(in);
				PHBaseValuePart.getXStreamSetupStatic().getOutputGraph(in);
				PHFieldValuePart.getXStreamSetupStatic().getOutputGraph(in);
				PHMethodRefValuePart.getXStreamSetupStatic().getOutputGraph(in);
				PHReturnValuePart.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(PHPart.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}

}
