package org.sag.woof.database.filemethods;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.sag.acminer.database.FileHashDatabase.XStreamSetup;
import org.sag.common.io.FileHash;
import org.sag.common.io.FileHashList;
import org.sag.common.io.PrintStreamUnixEOL;
import org.sag.common.tools.SortingMethods;
import org.sag.common.tuple.Triple;
import org.sag.common.xstream.XStreamInOut;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import soot.SootMethod;

@XStreamAlias("EmptyFileMethodsDatabase")
public class EmptyFileMethodsDatabase implements IFileMethodsDatabase {
	
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
		sb.append(spacer).append("File Methods Database:\n");
		for(FileMethod m : getOutputData()) {
			sb.append(m.toString(spacer + "  "));
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
		if(o == null || !(o instanceof IFileMethodsDatabase))
			return false;
		IFileMethodsDatabase other = (IFileMethodsDatabase)o;
		return Objects.equals(getOutputData(), other.getOutputData());
	}
	
	@Override
	public void sortData() {}
	
	@Override
	public FileMethod add(SootMethod sm, boolean[] actions, String apiType) {
		return FileMethod.getNewFileMethod(sm, actions, apiType);
	}
	
	@Override
	public Set<FileMethod> addAll(Set<Triple<SootMethod, boolean[], String>> methods) {
		Set<FileMethod> ret = new HashSet<>();
		for(Triple<SootMethod,boolean[],String> t : methods)
			ret.add(FileMethod.getNewFileMethod(t.getFirst(),t.getSecond(),t.getThird()));
		return SortingMethods.sortSet(ret);
	}

	@Override
	public Set<FileMethod> getOutputData() {
		return Collections.emptySet();
	}

	@Override
	public Set<FileMethod> getNativeMethods() {
		return Collections.emptySet();
	}

	@Override
	public Set<FileMethod> getOpenMethods() {
		return Collections.emptySet();
	}

	@Override
	public Set<FileMethod> getRemoveMethods() {
		return Collections.emptySet();
	}

	@Override
	public Set<FileMethod> getAccessMethods() {
		return Collections.emptySet();
	}

	@Override
	public Set<FileMethod> getJavaAPIMethods() {
		return Collections.emptySet();
	}

	@Override
	public Set<FileMethod> getAndroidAPIMethods() {
		return Collections.emptySet();
	}

	@Override
	public Set<FileMethod> getAndroidSystemMethods() {
		return Collections.emptySet();
	}
	
	@Override
	public EmptyFileMethodsDatabase readTXT(Path path) throws Exception {
		return this;
	}
	
	@Override
	public FileHashList readFileHashListInTXT(Path path) throws Exception {
		return null;
	}
	
	public static EmptyFileMethodsDatabase readTXTStatic(Path path) throws Exception {
		return new EmptyFileMethodsDatabase();
	}
	
	@Override
	public void writeTXT(Path path) throws Exception {
		try(BufferedWriter br = Files.newBufferedWriter(path)) {
			//locking handled in toString
			br.write(toString());
		}
	}
	
	@Override
	public void writePartsTXT(Path path, Set<FileMethod> parts, String name, FileHashList dep) throws Exception {
		Objects.requireNonNull(path);
		Objects.requireNonNull(parts);
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(path), false, "UTF-8")) {
			if(name != null)
				ps.println("#" + name + " File Methods Intermediate File");
			else
				ps.println("# File Methods Intermediate File");
			if(dep != null) {
				String depStr = dep.writeXMLToString().trim();
				try(BufferedReader brDep = new BufferedReader(new StringReader(depStr))) {
					String line;
					while((line = brDep.readLine()) != null) {
						ps.println("# " + line);
					}
					
				}
			}
			for(FileMethod m : parts) {
				ps.println(m.toString());
			}
		}
	}
	
	@Override
	public EmptyFileMethodsDatabase readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this, filePath, path);
	}
	
	public static EmptyFileMethodsDatabase readXMLStatic(String filePath, Path path) throws Exception {
		return new EmptyFileMethodsDatabase().readXML(filePath, path);
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
			return Collections.singleton(EmptyFileMethodsDatabase.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsNoRef(xstream);
		}
		
	}

}
