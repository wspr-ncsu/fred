package org.sag.fred.database.filepaths;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.sag.common.xstream.XStreamInOut;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;
import org.sag.fred.database.filepaths.parts.PHPart;
import org.sag.fred.database.filepaths.parts.Part;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("PathContainer")
public final class PathContainer implements XStreamInOutInterface, Comparable<PathContainer> {
	
	@XStreamAlias("SeedPart")
	private PHPart seedPart;
	
	@XStreamAlias("PathPart")
	private Part pathPart;
	
	private PathContainer() {}
	
	public PathContainer(PHPart seedPart, Part pathPart) {
		Objects.requireNonNull(seedPart);
		Objects.requireNonNull(pathPart);
		this.seedPart = seedPart;
		this.pathPart = pathPart;
	}
	
	@Override
	public boolean equals(Object o){
		if(this == o)
			return true;
		if(o == null || getClass() != o.getClass())
			return false;
		PathContainer other = (PathContainer)o;
		return Objects.equals(seedPart, other.seedPart) && Objects.equals(pathPart, other.pathPart);
	}
	
	@Override
	public int hashCode(){
		int hash = 17;
		hash = 31 * hash + Objects.hashCode(seedPart);
		hash = 31 * hash + Objects.hashCode(pathPart);
		return hash;
	}
	
	@Override
	public String toString() {
		return toString("");
	}
	
	public String toString(String spacer) {
		StringBuilder sb = new StringBuilder();
		sb.append(spacer).append(seedPart.toString()).append("\n");
		sb.append(spacer).append(pathPart.toString()).append("\n");
		return sb.toString();
	}
	
	@Override
	public int compareTo(PathContainer o) {
		return seedPart.compareTo(o.seedPart);
	}
	
	public PHPart getSeedPart() {
		return seedPart;
	}
	
	public Part getPathPart() {
		return pathPart;
	}
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}
	
	public PathContainer readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this,filePath, path);
	}
	
	public static PathContainer readXMLStatic(String filePath, Path path) throws Exception {
		return new PathContainer().readXML(filePath, path);
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
				PHPart.getXStreamSetupStatic().getOutputGraph(in);
				Part.XStreamSetup.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(PathContainer.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsNoRef(xstream);
		}
		
	}

}
