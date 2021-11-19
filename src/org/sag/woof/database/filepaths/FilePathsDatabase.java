package org.sag.woof.database.filepaths;

import java.io.ObjectStreamException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.sag.acminer.database.FileHashDatabase;
import org.sag.acminer.phases.entrypoints.EntryPoint;
import org.sag.common.tools.SortingMethods;
import org.sag.common.tuple.Pair;
import org.sag.common.xstream.XStreamInOut;
import org.sag.woof.database.filepaths.parts.PHPart;
import org.sag.woof.database.filepaths.parts.Part;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("FilePathsDatabase")
public class FilePathsDatabase extends FileHashDatabase implements IFilePathsDatabase {

	@XStreamImplicit
	private volatile LinkedHashSet<EntryPointContainer> data;
	
	@XStreamOmitField
	private volatile Map<EntryPoint,List<Pair<PHPart,Part>>> entryPointToPaths;
	@XStreamOmitField
	private final ReadWriteLock rwlock;
	@XStreamOmitField
	private volatile boolean sorted;
	
	protected FilePathsDatabase(boolean newDB) {
		if(newDB) {
			entryPointToPaths = new LinkedHashMap<>();
			data = new LinkedHashSet<>();
			sorted = true;
		} else {
			entryPointToPaths = null;
			data = null;
			sorted = false;
		}
		rwlock = new ReentrantReadWriteLock();
	}
	
	protected Object readResolve() throws ObjectStreamException {
		//Want to be able to load this without soot so call loadSootResolvedData separately
		rwlock.writeLock().lock();
		try {
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
	
	private void sortDataWLocked() {
		if(!sorted) {
			for(EntryPointContainer ep : data) {
				ep.sortPaths();
			}
			data = SortingMethods.sortSet(data);
			sorted = true;
		}
	}
	
	private void sortDataRLocked() {
		if(!sorted) {
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
		if(entryPointToPaths != null) {
			rwlock.writeLock().lock();
			try {
				entryPointToPaths = null;
			} finally {
				rwlock.writeLock().unlock();
			}
		}
	}
	
	@Override
	public void loadSootResolvedData() {
		if(entryPointToPaths == null) {
			rwlock.writeLock().lock();
			try {
				loadSootResolvedDataWLocked();
			} finally {
				rwlock.writeLock().unlock();
			}
		}
	}
	
	private void loadSootResolvedDataWLocked() {
		if(entryPointToPaths == null) {
			entryPointToPaths = new LinkedHashMap<>();
			sortDataWLocked();
			for(EntryPointContainer ep : data) {
				entryPointToPaths.put(ep.getSootEntryPoint(), ep.getData());
			}
		}
	}
	
	private void loadSootResolvedDataRLocked() {
		if(entryPointToPaths == null) {
			// Must release read lock before acquiring write lock
			rwlock.readLock().unlock();
			rwlock.writeLock().lock();
			try {
				loadSootResolvedDataWLocked();
				rwlock.readLock().lock(); // Downgrade by acquiring read lock before releasing write lock
			} finally {
				rwlock.writeLock().unlock(); // Unlock write, still hold read
			}
		}
	}
	
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
	public void sortData() {
		rwlock.writeLock().lock();
		try {
			sortDataWLocked();
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	@Override
	public void add(EntryPoint ep, List<Pair<PHPart, Part>> paths) {
		rwlock.writeLock().lock();
		try {
			addInner(ep, paths);
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	@Override
	public void addAll(Map<EntryPoint,List<Pair<PHPart,Part>>> data) {
		Objects.requireNonNull(data);
		rwlock.writeLock().lock();
		try {
			for(EntryPoint ep : data.keySet()) {
				addInner(ep, data.get(ep));
			}
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	private void addInner(EntryPoint ep, List<Pair<PHPart, Part>> paths) {
		Objects.requireNonNull(ep);
		data.add(new EntryPointContainer(ep, paths));
		entryPointToPaths.put(ep, paths);
		sorted = false;
	}
	
	@Override
	public Set<EntryPointContainer> getOutputData() {
		Set<EntryPointContainer> ret = new LinkedHashSet<>();
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
	public Map<EntryPoint,List<Pair<PHPart,Part>>> getData() {
		Map<EntryPoint,List<Pair<PHPart,Part>>> ret = new LinkedHashMap<>();
		rwlock.readLock().lock();
		try {
			loadSootResolvedDataRLocked();
			for(EntryPoint ep : entryPointToPaths.keySet()) {
				ret.put(ep, new ArrayList<>(entryPointToPaths.get(ep)));
			}
		} finally {
			rwlock.readLock().unlock();
		}
		return ret;
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
	public FilePathsDatabase readXML(String filePath, Path path) throws Exception {
		rwlock.writeLock().lock();
		try {
			return XStreamInOut.readXML(this, filePath, path);
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	public static FilePathsDatabase readXMLStatic(String filePath, Path path) throws Exception {
		return new FilePathsDatabase(false).readXML(filePath, path);
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
				EntryPointContainer.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(FilePathsDatabase.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
}
