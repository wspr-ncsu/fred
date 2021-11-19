package org.sag.woof.phases.woof;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.sag.common.tools.SortingMethods;
import org.sag.common.xstream.XStreamInOut;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;
import org.sag.woof.database.filepaths.parts.PHPart;
import org.sag.woof.database.filepaths.parts.Part;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("IntermediateExpression")
public final class IntermediateExpression implements XStreamInOutInterface, Comparable<IntermediateExpression> {
	
	@XStreamAlias("Seed")
	private PHPart seed;
	@XStreamAlias("SimpleMatchPath")
	private Part simpleMatchPath;
	@XStreamAlias("OriginalMatchPath")
	private Part originalMatchPath;
	
	private IntermediateExpression() {}
	
	public IntermediateExpression(PHPart seed, Part simpleMatchPath, Part originalMatchPath) {
		Objects.requireNonNull(seed);
		Objects.requireNonNull(simpleMatchPath);
		Objects.requireNonNull(originalMatchPath);
		this.seed = seed;
		this.simpleMatchPath = simpleMatchPath;
		this.originalMatchPath = originalMatchPath;
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || !(o instanceof IntermediateExpression))
			return false;
		IntermediateExpression other = (IntermediateExpression)o;
		return Objects.equals(seed, other.seed) && Objects.equals(simpleMatchPath, other.simpleMatchPath) 
				&& Objects.equals(originalMatchPath, other.originalMatchPath);
	}
	
	@Override
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(seed);
		i = i * 31 + Objects.hashCode(simpleMatchPath);
		i = i * 31 + Objects.hashCode(originalMatchPath);
		return i;
	}
	
	@Override
	public int compareTo(IntermediateExpression o) {
		int ret = SortingMethods.sComp.compare(simpleMatchPath.toRegexString(), o.simpleMatchPath.toRegexString());
		if(ret == 0) {
			ret = seed.compareTo(o.seed);
		}
		return ret;
	}
	
	public IntermediateExpression clone() {
		return new IntermediateExpression(seed, simpleMatchPath, originalMatchPath);
	}

	public PHPart getSeed() {
		return seed;
	}

	public Part getSimpleMatchPath() {
		return simpleMatchPath;
	}

	public Part getOriginalMatchPath() {
		return originalMatchPath;
	}
	
	public String toString() {
		return toString("");
	}
	
	public String toString(String spacer) {
		StringBuilder sb = new StringBuilder();
		sb.append(spacer).append("SimpleMatchPath: ").append(simpleMatchPath.toString()).append("\n");
		sb.append(spacer).append("  OriginalMatchPath: ").append(originalMatchPath.toString()).append("\n");
		sb.append(spacer).append("  Seed: ").append(seed.toString());
		return sb.toString();
	}

	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}
	
	public IntermediateExpression readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this,filePath, path);
	}
	
	public static IntermediateExpression readXMLStatic(String filePath, Path path) throws Exception {
		return new IntermediateExpression().readXML(filePath, path);
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
				Part.XStreamSetup.getXStreamSetupStatic().getOutputGraph(in);
				PHPart.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(IntermediateExpression.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}

}
