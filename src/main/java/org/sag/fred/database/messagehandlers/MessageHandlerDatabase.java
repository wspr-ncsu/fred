package org.sag.fred.database.messagehandlers;

import java.io.ObjectStreamException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.sag.acminer.database.FileHashDatabase;
import org.sag.common.tools.SortingMethods;
import org.sag.common.xstream.XStreamInOut;
import org.sag.soot.SootSort;
import org.sag.soot.xstream.SootMethodContainer;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import soot.SootMethod;

@XStreamAlias("MessageHandlerDatabase")
public class MessageHandlerDatabase extends FileHashDatabase implements IMessageHandlerDatabase {
	
	@XStreamOmitField
	private final ReadWriteLock rwlock;
	@XStreamOmitField
	private volatile boolean loaded;
	@XStreamOmitField
	private volatile Set<SootMethod> data;
	
	@XStreamImplicit
	private volatile LinkedHashSet<SootMethodContainer> output;
	
	protected MessageHandlerDatabase(boolean newDB) {
		if(newDB) {
			data = new LinkedHashSet<>();
			loaded = true;
		} else {
			data = null;
			loaded = false;
		}
		rwlock = new ReentrantReadWriteLock();
		output = null;
	}
	
	protected Object readResolve() throws ObjectStreamException {
		//Want to be able to load this without soot so call loadSootResolvedData separately
		return this;
	}
	
	protected Object writeReplace() throws ObjectStreamException {
		rwlock.writeLock().lock();
		try {
			writeSootResolvedDataWLocked();
		} finally {
			rwlock.writeLock().unlock();
		}
		return this;
	}
	
	private void sortDataLocked() {
		data = SortingMethods.sortSet(data,SootSort.smComp);
	}
	
	@Override
	public void clearSootResolvedData() {
		rwlock.writeLock().lock();
		try {
			writeSootResolvedDataWLocked();
			data = null;
			loaded = false;
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	@Override
	public void loadSootResolvedData() {
		rwlock.writeLock().lock();
		try {
			loadSootResolvedDataWLocked();
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	private void writeSootResolvedDataWLocked() {
		if(loaded) {
			sortDataLocked();
			output = new LinkedHashSet<>();
			for(SootMethod sm : data)
				output.add(SootMethodContainer.makeSootMethodContainer(sm));
		}
	}
	
	private void loadSootResolvedDataWLocked() {
		if(!loaded) {
			data = new LinkedHashSet<>();
			//Note this will fail with an exception if the data cannot be loaded 
			//as this should always be the same or be recreated 
			for(SootMethodContainer smContainer : output) {
				data.add(smContainer.toSootMethod());
			}
			sortDataLocked();
			loaded = true;
		}
	}
	
	private void loadSootResolvedDataRLocked() {
		if(!loaded) {
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
		sb.append(spacer).append("Message Handler Database:\n");
		for(SootMethodContainer sm : getOutputData()) {
			sb.append(sm.toString(spacer + "  "));
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
		if(o == null || !(o instanceof IMessageHandlerDatabase))
			return false;
		IMessageHandlerDatabase other = (IMessageHandlerDatabase)o;
		return Objects.equals(getOutputData(), other.getOutputData());
	}
	
	@Override
	public void sortData() {
		rwlock.writeLock().lock();
		try {
			loadSootResolvedDataWLocked();
			sortDataLocked();
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	@Override
	public void add(SootMethod sm) {
		rwlock.writeLock().lock();
		try {
			loadSootResolvedDataWLocked();
			addInner(sm);
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	@Override
	public void addAll(Set<SootMethod> methods) {
		rwlock.writeLock().lock();
		try {
			loadSootResolvedDataWLocked();
			for(SootMethod sm : methods)
				addInner(sm);
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	private void addInner(SootMethod sm) {
		Objects.requireNonNull(sm);
		data.add(sm);
	}
	
	@Override
	public Set<SootMethod> getMethods() {
		rwlock.readLock().lock();
		loadSootResolvedDataRLocked();
		try {
			return new LinkedHashSet<>(data);
		} finally {
			rwlock.readLock().unlock();
		}
	}
	
	@Override
	public boolean contains(SootMethod sm) {
		Objects.requireNonNull(sm);
		rwlock.readLock().lock();
		loadSootResolvedDataRLocked();
		try {
			return data.contains(sm);
		} finally {
			rwlock.readLock().unlock();
		}
	}
	
	@Override
	public Set<SootMethodContainer> getOutputData() {
		Set<SootMethodContainer> ret = new LinkedHashSet<>();
		rwlock.writeLock().lock(); //Prevent changes and because we may end up writing something
		try {
			//Need to write any changes to the loaded data back to output before copying
			writeSootResolvedDataWLocked(); //Will only write if loaded = true, otherwise nothing happens
			ret.addAll(output);
		} finally {
			rwlock.writeLock().unlock();
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
	public MessageHandlerDatabase readXML(String filePath, Path path) throws Exception {
		rwlock.writeLock().lock();
		try {
			return XStreamInOut.readXML(this, filePath, path);
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	public static MessageHandlerDatabase readXMLStatic(String filePath, Path path) throws Exception {
		return new MessageHandlerDatabase(false).readXML(filePath, path);
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
				SootMethodContainer.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(MessageHandlerDatabase.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsNoRef(xstream);
		}
		
	}

}
