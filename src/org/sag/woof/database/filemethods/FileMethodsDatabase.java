package org.sag.woof.database.filemethods;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ObjectStreamException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.sag.acminer.database.FileHashDatabase;
import org.sag.common.io.FileHashList;
import org.sag.common.io.PrintStreamUnixEOL;
import org.sag.common.tools.SortingMethods;
import org.sag.common.tuple.Triple;
import org.sag.common.xstream.XStreamInOut;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import soot.SootMethod;

@XStreamAlias("FileMethodsDatabase")
public class FileMethodsDatabase extends FileHashDatabase implements IFileMethodsDatabase {
	
	@XStreamImplicit
	private volatile Set<FileMethod> data;
	
	@XStreamOmitField
	private final ReadWriteLock rwlock;
	@XStreamOmitField
	private volatile Set<FileMethod> nativeMethods;
	@XStreamOmitField
	private volatile Set<FileMethod> openMethods;
	@XStreamOmitField
	private volatile Set<FileMethod> removeMethods;
	@XStreamOmitField
	private volatile Set<FileMethod> accessMethods;
	@XStreamOmitField 
	private volatile Set<FileMethod> javaAPIMethods;
	@XStreamOmitField
	private volatile Set<FileMethod> androidAPIMethods;
	@XStreamOmitField
	private volatile Set<FileMethod> androidSystemMethods;
	@XStreamOmitField 
	private final boolean[] sortedIndicators;
	@XStreamOmitField
	private volatile boolean allSorted;
	
	protected FileMethodsDatabase(boolean newDB) {
		if(newDB) {
			sortedIndicators = new boolean[] {true,true,true,true,true,true,true,true};
			allSorted = true;
		} else {
			sortedIndicators = new boolean[] {false,false,false,false,false,false,false,false};
			allSorted = false;
		}
		data = new LinkedHashSet<>();
		nativeMethods = new LinkedHashSet<>();
		openMethods = new LinkedHashSet<>();
		removeMethods = new LinkedHashSet<>();
		accessMethods = new LinkedHashSet<>();
		javaAPIMethods = new LinkedHashSet<>();
		androidAPIMethods = new LinkedHashSet<>();
		androidSystemMethods = new LinkedHashSet<>();
		rwlock = new ReentrantReadWriteLock();
	}
	
	protected Object readResolve() throws ObjectStreamException {
		//Want to be able to load this without soot so call loadSootResolvedData separately
		rwlock.writeLock().lock();
		try {
			for(FileMethod m : data) {
				updateSubDataWLocked(m);
			}
			sortDataWLocked();
		} finally {
			rwlock.writeLock().unlock();
		}
		return this;
	}
	
	protected Object writeReplace() throws ObjectStreamException {
		rwlock.writeLock().lock();
		try {
			sortDataWLocked();
		} finally {
			rwlock.writeLock().unlock();
		}
		return this;
	}
	
	private void updateSubDataWLocked(FileMethod m) {
		if(m.isNative()) {
			nativeMethods.add(m);
			sortedIndicators[1] = false;
			allSorted = false;
		}
		if(m.opens()) {
			openMethods.add(m);
			sortedIndicators[2] = false;
			allSorted = false;
		}
		if(m.accesses()) {
			accessMethods.add(m);
			sortedIndicators[3] = false;
			allSorted = false;
		}
		if(m.removes()) {
			removeMethods.add(m);
			sortedIndicators[4] = false;
			allSorted = false;
		}
		if(m.isJavaAPI()) {
			javaAPIMethods.add(m);
			sortedIndicators[5] = false;
			allSorted = false;
		}
		if(m.isAndroidAPI()) {
			androidAPIMethods.add(m);
			sortedIndicators[6] = false;
			allSorted = false;
		}
		if(m.isAndroidSystem()) {
			androidSystemMethods.add(m);
			sortedIndicators[7] = false;
			allSorted = false;
		}
	}
	
	private void sortDataWLocked() {
		if(!sortedIndicators[0]) {
			data = SortingMethods.sortSet(data);
			sortedIndicators[0] = true;
		}
		if(!sortedIndicators[1]) {
			nativeMethods = SortingMethods.sortSet(nativeMethods);
			sortedIndicators[1] = true;
		}
		if(!sortedIndicators[2]) {
			openMethods  = SortingMethods.sortSet(openMethods);
			sortedIndicators[2] = true;
		}
		if(!sortedIndicators[3]) {
			accessMethods = SortingMethods.sortSet(accessMethods);
			sortedIndicators[3] = true;
		}
		if(!sortedIndicators[4]) {
			removeMethods = SortingMethods.sortSet(removeMethods);
			sortedIndicators[4] = true;
		}
		if(!sortedIndicators[5]) {
			javaAPIMethods = SortingMethods.sortSet(javaAPIMethods);
			sortedIndicators[5] = true;
		}
		if(!sortedIndicators[6]) {
			androidAPIMethods = SortingMethods.sortSet(androidAPIMethods);
			sortedIndicators[6] = true;
		}
		if(!sortedIndicators[7]) {
			androidSystemMethods = SortingMethods.sortSet(androidSystemMethods);
			sortedIndicators[7] = true;
		}
		allSorted = true;
	}
	
	private void sortDataRLocked() {
		if(!allSorted) {
			// Must release read lock before acquiring write lock
			rwlock.readLock().unlock();
			rwlock.writeLock().lock();
			try {
				sortDataWLocked();
				rwlock.readLock().lock(); // Downgrade by acquiring read lock before releasing write lock
			} finally {
				rwlock.writeLock().unlock(); // Unlock write, still hold read
			}
		}
	}
	
	@Override
	public void clearSootResolvedData() {
		rwlock.writeLock().lock();
		try {
			for(FileMethod m : data) {
				m.clearSootData();
			}
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	@Override
	public void loadSootResolvedData() {
		rwlock.writeLock().lock();
		try {
			for(FileMethod m : data) {
				m.getSootMethod();
			}
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	@Override
	public String toString() {
		return toString("");
	}
	
	@Override
	public String toString(String spacer) {
		StringBuilder sb = new StringBuilder();
		sb.append(spacer).append("# File Methods Database:\n");
		for(FileMethod m : getOutputData()) {
			sb.append(m.toString(spacer)).append("\n");
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
	public void sortData() {
		rwlock.writeLock().lock();
		try {
			sortDataWLocked();
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	@Override
	public FileMethod add(SootMethod sm, boolean[] actions, String apiType) {
		rwlock.writeLock().lock();
		try {
			return addInner(FileMethod.getNewFileMethod(sm, actions, apiType));
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	@Override
	public Set<FileMethod> addAll(Set<Triple<SootMethod,boolean[],String>> methods) {
		rwlock.writeLock().lock();
		try {
			Set<FileMethod> ret = new HashSet<>();
			for(Triple<SootMethod,boolean[],String> t : methods) {
				ret.add(addInner(FileMethod.getNewFileMethod(t.getFirst(),t.getSecond(),t.getThird())));
			}
			return SortingMethods.sortSet(ret);
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	private FileMethod addInner(FileMethod m) {
		FileMethod cur = null;
		for(FileMethod fm : data) {
			if(fm.equals(m)) {
				cur = fm;
				break;
			}
		}
		if(cur == null) {
			data.add(m);
			sortedIndicators[0] = false;
			allSorted = false;
			updateSubDataWLocked(m);
			cur = m;
		}
		return cur;
	}
	
	@Override
	public Set<FileMethod> getOutputData() {
		Set<FileMethod> ret = new LinkedHashSet<>();
		rwlock.readLock().lock();
		try {
			sortDataRLocked();
			ret.addAll(data);
		} finally {
			rwlock.readLock().unlock();
		}
		return ret;
	}
	
	@Override
	public Set<FileMethod> getNativeMethods() {
		Set<FileMethod> ret = new LinkedHashSet<>();
		rwlock.readLock().lock();
		try {
			sortDataRLocked();
			ret.addAll(nativeMethods);
		} finally {
			rwlock.readLock().unlock();
		}
		return ret;
	}
	
	@Override
	public Set<FileMethod> getOpenMethods() {
		Set<FileMethod> ret = new LinkedHashSet<>();
		rwlock.readLock().lock();
		try {
			sortDataRLocked();
			ret.addAll(openMethods);
		} finally {
			rwlock.readLock().unlock();
		}
		return ret;
	}
	
	@Override
	public Set<FileMethod> getRemoveMethods() {
		Set<FileMethod> ret = new LinkedHashSet<>();
		rwlock.readLock().lock();
		try {
			sortDataRLocked();
			ret.addAll(removeMethods);
		} finally {
			rwlock.readLock().unlock();
		}
		return ret;
	}
	
	@Override
	public Set<FileMethod> getAccessMethods() {
		Set<FileMethod> ret = new LinkedHashSet<>();
		rwlock.readLock().lock();
		try {
			sortDataRLocked();
			ret.addAll(accessMethods);
		} finally {
			rwlock.readLock().unlock();
		}
		return ret;
	}
	
	@Override
	public Set<FileMethod> getJavaAPIMethods() {
		Set<FileMethod> ret = new LinkedHashSet<>();
		rwlock.readLock().lock();
		try {
			sortDataRLocked();
			ret.addAll(javaAPIMethods);
		} finally {
			rwlock.readLock().unlock();
		}
		return ret;
	}
	
	@Override
	public Set<FileMethod> getAndroidAPIMethods() {
		Set<FileMethod> ret = new LinkedHashSet<>();
		rwlock.readLock().lock();
		try {
			sortDataRLocked();
			ret.addAll(androidAPIMethods);
		} finally {
			rwlock.readLock().unlock();
		}
		return ret;
	}
	
	@Override
	public Set<FileMethod> getAndroidSystemMethods() {
		Set<FileMethod> ret = new LinkedHashSet<>();
		rwlock.readLock().lock();
		try {
			sortDataRLocked();
			ret.addAll(androidSystemMethods);
		} finally {
			rwlock.readLock().unlock();
		}
		return ret;
	}
	
	@Override
	public FileHashList readFileHashListInTXT(Path path) throws Exception {
		try(BufferedReader br = Files.newBufferedReader(path)) {
			String line;
			boolean isFirstOfHeader = true;
			StringBuilder fileHashListSB = new StringBuilder();
			FileHashList fhl = null;
			while((line = br.readLine()) != null) {
				line = line.trim();
				if(line.startsWith("#")) {
					if(isFirstOfHeader) { //Skip first line of header as it is a description
						isFirstOfHeader = false;
					} else {
						fileHashListSB.append(line.substring(2)).append("\n"); //Remove the "# " at the beginning of every line
					}
				} else if(line.isEmpty()) {
					String fileHashListString = fileHashListSB.toString().trim();
					if(!fileHashListString.isEmpty()) {
						fhl = FileHashList.readXMLFromStringStatic(fileHashListString);
					}
					break;
				} else {
					throw new RuntimeException("Error: Failed to read the header of the FileMethodsDatabase TXT file '" + path + "'");
				}
				
			}
			return fhl;
		}
	}
	
	@Override
	public FileMethodsDatabase readTXT(Path path) throws Exception {
		rwlock.writeLock().lock();
		try(BufferedReader br = Files.newBufferedReader(path)) {
			String line;
			FileMethod cur = null;
			StringBuilder sb = new StringBuilder();
			int i = 0;
			Map<FileMethod,Set<String>> sinkMap = new HashMap<>();
			while((line = br.readLine()) != null) {
				try {
					line = line.trim();
					if(line.startsWith("#") || line.isEmpty())
						continue;
					if(line.startsWith("<")) {
						if(cur == null)
							throw new RuntimeException("Error: The no FileMethod specified for the sink '" + line + "'");
						Set<String> sinks = sinkMap.get(cur);
						if(sinks == null) {
							sinks = new HashSet<>();
							sinkMap.put(cur, sinks);
						}
						sinks.add(line);
					} else {
						cur = addInner(FileMethod.parseFileMethodStr(line));
					}
				} catch(Throwable t) {
					sb.append("Exception ").append(i++).append(": ").append(t.getMessage().trim()).append("\n");
				}
			}
			if(i != 0) {
				throw new RuntimeException(sb.toString());
			}
			
			for(FileMethod container : sinkMap.keySet()) {
				Set<String> sinksigs = sinkMap.get(container);
				Set<FileMethod> sinks = new HashSet<>();
				for(FileMethod m : data) {
					if(sinksigs.contains(m.getSignature()))
						sinks.add(m);
				}
				container.setSinks(SortingMethods.sortSet(sinks));
			}
			
			sortDataWLocked();
			
			return this;
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	public static FileMethodsDatabase readTXTStatic(Path path) throws Exception {
		return new FileMethodsDatabase(false).readTXT(path);
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
				ps.println("# " + name + " File Methods Intermediate File");
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
			ps.println();
			for(FileMethod m : parts) {
				ps.println(m.toString());
			}
		}
	}
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		rwlock.writeLock().lock();
		try {
			XStreamInOut.writeXML(this, filePath, path);
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	@Override
	public FileMethodsDatabase readXML(String filePath, Path path) throws Exception {
		rwlock.writeLock().lock();
		try {
			return XStreamInOut.readXML(this, filePath, path);
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	public static FileMethodsDatabase readXMLStatic(String filePath, Path path) throws Exception {
		return new FileMethodsDatabase(false).readXML(filePath, path);
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
				FileMethod.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(FileMethodsDatabase.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
	public static void main(String[] args) throws Exception {
		FileMethodsDatabase db = readXMLStatic("C:\\CS\\Documents\\Work\\Research\\woof\\aosp-10.0.0\\woof\\file_methods_db.xml", null);
		int android = 0;
		int java = 0;
		for(FileMethod fm : db.getAndroidAPIMethods()) {
			if(!fm.isNative())
				android++;
		}
		for(FileMethod fm : db.getAndroidSystemMethods()) {
			if(!fm.isNative())
				android++;
		}
		for(FileMethod fm : db.getJavaAPIMethods()) {
			if(!fm.isNative())
				java++;
		}
		System.out.println("Android: " + android);
		System.out.println("Native: " + db.getNativeMethods().size());
		System.out.println("Java: " + java);
		System.out.println("Total: " + db.getOutputData().size());
		/*FileMethodsDatabase fullRunDB = readXMLStatic("C:\\CS\\Documents\\Work\\Research\\woof\\aosp-9.0.0\\input\\woof\\file_methods_db__.xml", null);
		FileMethodsDatabase imdRunDB = readXMLStatic("C:\\CS\\Documents\\Work\\Research\\woof\\aosp-9.0.0\\input\\woof\\file_methods_db.xml", null);
		
		for(FileMethod fm : fullRunDB.data) {
			if(!imdRunDB.data.contains(fm))
				System.out.println("Missing " + fm.toString());
		}
		
		for(FileMethod fm : imdRunDB.data) {
			if(!fullRunDB.data.contains(fm))
				System.out.println("2222Missing2 " + fm.toString());
		}
		System.out.println("...");
		for(FileMethod fm : fullRunDB.data) {
			if(fm.getSignature().equals("<libcore.io.Linux: java.io.FileDescriptor accept(java.io.FileDescriptor,java.net.SocketAddress)>"))
				System.out.println(fm);
		}
		System.out.println("...");
		for(FileMethod fm : imdRunDB.data) {
			if(fm.getSignature().equals("<libcore.io.Linux: java.io.FileDescriptor accept(java.io.FileDescriptor,java.net.SocketAddress)>"))
				System.out.println(fm);
		}*/
	}
	
}
