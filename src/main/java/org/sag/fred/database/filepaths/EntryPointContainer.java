package org.sag.fred.database.filepaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.sag.acminer.phases.entrypoints.EntryPoint;
import org.sag.common.tuple.Pair;
import org.sag.common.xstream.NamedCollectionConverterWithSize;
import org.sag.common.xstream.XStreamInOut;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;
import org.sag.soot.xstream.SootClassContainer;
import org.sag.soot.xstream.SootMethodContainer;
import org.sag.fred.database.filepaths.parts.PHPart;
import org.sag.fred.database.filepaths.parts.Part;

import soot.SootClass;
import soot.SootMethod;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("EntryPointContainer")
public final class EntryPointContainer implements XStreamInOutInterface, Comparable<EntryPointContainer> {

	@XStreamAlias("EntryPoint")
	private SootMethodContainer entryPoint;
	
	@XStreamAlias("Stub")
	private SootClassContainer stub;
	
	@XStreamAlias("Paths")
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"PathContainer"},types={PathContainer.class})
	private ArrayList<PathContainer> paths;
	
	@XStreamOmitField
	private boolean sorted;
	
	//for reading in from xml only
	private EntryPointContainer() { sorted = false; }
	
	public EntryPointContainer(EntryPoint ep, List<Pair<PHPart,Part>> data) {
		this.entryPoint = ep.getEntryPoint() == null ? null : SootMethodContainer.makeSootMethodContainer(ep.getEntryPoint());
		this.stub = ep.getStub() == null ? null : SootClassContainer.makeSootClassContainer(ep.getStub());
		if(data != null && !data.isEmpty()) {
			this.paths = new ArrayList<>();
			for(Pair<PHPart,Part> p : data) {
				paths.add(new PathContainer(p.getFirst(), p.getSecond()));
			}
			Collections.sort(paths);
		}
		sorted = true;
	}
	
	@Override
	public boolean equals(Object o){
		if(this == o)
			return true;
		if(o == null || !(o instanceof EntryPointContainer))
			return false;
		EntryPointContainer other = (EntryPointContainer)o;
		return Objects.equals(entryPoint, other.entryPoint) && Objects.equals(stub, other.stub);
	}
	
	@Override
	public int hashCode(){
		int hash = 17;
		hash = 31 * hash + Objects.hashCode(entryPoint);
		hash = 31 * hash + Objects.hashCode(stub);
		return hash;
	}
	
	@Override
	public String toString() {
		return toString("");
	}
	
	public String toString(String spacer) {
		StringBuilder sb = new StringBuilder();
		sb.append(spacer).append("{" + Objects.toString(stub) + " : " + Objects.toString(entryPoint) + "}").append("\n");
		if(paths != null && !paths.isEmpty()) {
			for(PathContainer p : paths) {
				sb.append(p.toString(spacer + "  "));
			}
		}
		return sb.toString();
	}
	
	@Override
	public int compareTo(EntryPointContainer o) {
		if(stub == null && ((EntryPointContainer)o).stub == null) {
			return 0;
		} else if(stub == null && ((EntryPointContainer)o).stub != null) {
			return -1;
		} else if(stub != null && ((EntryPointContainer)o).stub == null) {
			return 1;
		} else {
			int ret = stub.compareTo(((EntryPointContainer)o).stub);
			if(ret == 0) {
				if(entryPoint == null && ((EntryPointContainer)o).entryPoint == null) {
					return 0;
				} else if(entryPoint == null && ((EntryPointContainer)o).entryPoint != null) {
					return -1;
				} else if(entryPoint != null && ((EntryPointContainer)o).entryPoint == null) {
					return 1;
				} else {
					ret = entryPoint.compareTo(((EntryPointContainer)o).entryPoint);
				}
			}
			return ret;
		}
	}
	
	public SootMethod getEntryPoint() {
		if(entryPoint == null)
			return null;
		return entryPoint.toSootMethod();
	}
	
	public SootClass getStub() {
		if(stub == null)
			return null;
		return stub.toSootClass();
	}
	
	public SootMethodContainer getEntryPointContainer() {
		return entryPoint;
	}
	
	public SootClassContainer getStubContainer() {
		return stub;
	}
	
	public EntryPoint getSootEntryPoint() {
		return new EntryPoint(getEntryPoint(),getStub());
	}
	
	public List<Pair<PHPart,Part>> getData() {
		List<Pair<PHPart,Part>> ret = new ArrayList<>();
		if(paths != null && !paths.isEmpty()) {
			sortPaths();
			for(PathContainer p : paths) {
				ret.add(new Pair<>(p.getSeedPart(), p.getPathPart()));
			}
		}
		return ret;
	}
	
	public Set<Part> getPaths() {
		Set<Part> ret = new LinkedHashSet<>();
		if(paths != null && !paths.isEmpty()) {
			sortPaths();
			for(PathContainer p : paths) {
				ret.add(p.getPathPart());
			}
		}
		return ret;
	}
	
	protected void sortPaths() {
		if(paths != null && !paths.isEmpty()) {
			if(!sorted) {
				Collections.sort(paths);
				sorted = true;
			}
		}
	}
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}
	
	public EntryPointContainer readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this,filePath, path);
	}
	
	public static EntryPointContainer readXMLStatic(String filePath, Path path) throws Exception {
		return new EntryPointContainer().readXML(filePath, path);
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
				SootClassContainer.getXStreamSetupStatic().getOutputGraph(in);
				PathContainer.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(EntryPointContainer.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsNoRef(xstream);
		}
		
	}
	
}
