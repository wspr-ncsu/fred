package org.sag.fred.database.filepaths;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.sag.acminer.database.FileHashDatabase.XStreamSetup;
import org.sag.acminer.phases.entrypoints.EntryPoint;
import org.sag.common.io.FileHash;
import org.sag.common.io.FileHashList;
import org.sag.common.tuple.Pair;
import org.sag.common.xstream.XStreamInOut;
import org.sag.fred.database.filepaths.parts.PHPart;
import org.sag.fred.database.filepaths.parts.Part;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

public class EmptyFilePathsDatabase implements IFilePathsDatabase {
	
	@Override
	public void clearSootResolvedData() {}

	@Override
	public void loadSootResolvedData() {}
	
	@Override
	public List<FileHash> getFileHashList() {
		return Collections.emptyList();
	}

	@Override
	public void setFileHashList(FileHashList fhl) {}
	
	@Override
	public String toString() {
		return toString("");
	}
	
	@Override
	public String toString(String spacer) {
		StringBuilder sb = new StringBuilder();
		sb.append(spacer).append("# File Paths Database:\n");
		for(EntryPointContainer ep : getOutputData()) {
			sb.append(ep.toString(spacer));
		}
		return sb.toString();
	}
	
	@Override
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(getOutputData());
		return i;
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || !(o instanceof IFilePathsDatabase))
			return false;
		IFilePathsDatabase other = (IFilePathsDatabase)o;
		return Objects.equals(getOutputData(), other.getOutputData());
	}
	
	@Override
	public void sortData() {}

	@Override
	public void add(EntryPoint ep, List<Pair<PHPart, Part>> paths) {}

	@Override
	public void addAll(Map<EntryPoint, List<Pair<PHPart, Part>>> data) {}

	@Override
	public Set<EntryPointContainer> getOutputData() {
		return Collections.emptySet();
	}

	@Override
	public Map<EntryPoint, List<Pair<PHPart, Part>>> getData() {
		return Collections.emptyMap();
	}
	
	@Override
	public EmptyFilePathsDatabase readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this, filePath, path);
	}
	
	public static EmptyFilePathsDatabase readXMLStatic(String filePath, Path path) throws Exception {
		return new EmptyFilePathsDatabase().readXML(filePath, path);
	}
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}

	@XStreamOmitField
	private static AbstractXStreamSetup xstreamSetup = null;

	public static AbstractXStreamSetup getXStreamSetupStatic(){
		if(xstreamSetup == null)
			xstreamSetup = new SubXStreamSetup();
		return xstreamSetup;
	}
	
	@Override
	public AbstractXStreamSetup getXStreamSetup() {
		return getXStreamSetupStatic();
	}
	
	public static class SubXStreamSetup extends XStreamSetup {
		
		@Override
		public void getOutputGraph(LinkedHashSet<AbstractXStreamSetup> in) {
			if(!in.contains(this)) {
				in.add(this);
				super.getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(EmptyFilePathsDatabase.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsNoRef(xstream);
		}
		
	}

}
