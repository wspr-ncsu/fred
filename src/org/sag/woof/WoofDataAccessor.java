package org.sag.woof;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.sag.acminer.ACMinerDataAccessor;
import org.sag.acminer.phases.bindergroups.BinderGroupsDatabase;
import org.sag.acminer.phases.entrypoints.EntryPoint;
import org.sag.acminer.phases.entrypoints.EntryPointsDatabase;
import org.sag.acminer.phases.entrypoints.EntryPointsDatabase.IntegerWrapper;
import org.sag.common.tools.SortingMethods;
import org.sag.main.config.Config;
import org.sag.soot.SootSort;
import org.sag.soot.xstream.SootMethodContainer;
import org.sag.woof.database.androidapi.IAndroidAPIDatabase;
import org.sag.woof.database.filemethods.IFileMethodsDatabase;
import org.sag.woof.database.messagehandlers.IMessageHandlerDatabase;

import soot.SootClass;
import soot.SootMethod;
import soot.jimple.InvokeExpr;

public class WoofDataAccessor extends ACMinerDataAccessor implements IWoofDataAccessor {
	
	protected volatile IAndroidAPIDatabase androidAPIDatabase;
	protected volatile IFileMethodsDatabase fileMethodsDatabase;
	protected volatile IMessageHandlerDatabase messageHandlerDatabase;

	public WoofDataAccessor(Config config) {
		super(config);
		androidAPIDatabase = IAndroidAPIDatabase.Factory.getNew(true);
		fileMethodsDatabase = IFileMethodsDatabase.Factory.getNew(true);
		messageHandlerDatabase = IMessageHandlerDatabase.Factory.getNew(true);
	}
	
	@Override
	public Set<SootMethod> getEntryPointsAsSootMethods() {
		Set<SootMethod> ret = new LinkedHashSet<>();
		for(EntryPoint ep : getEntryPoints()) {
			ret.add(ep.getEntryPoint());
		}
		return ret;
	}
	
	@Override
	public Set<EntryPoint> getEntryPoints() {
		Set<EntryPoint> ret = new HashSet<>();
		synchronized(EntryPointsDatabase.v()) {
			ret.addAll(EntryPointsDatabase.v().getEntryPoints());
		}
		for(SootMethod sm : getMessageHandlerDB().getMethods()) {
			ret.add(new EntryPoint(sm, sm.getDeclaringClass()));
		}
		return SortingMethods.sortSet(ret);
	}
	
	@Override
	public Set<SootMethod> getEntryPointsForStub(SootClass stub) {
		Set<SootMethod> ret = null;
		synchronized(EntryPointsDatabase.v()) {
			ret = EntryPointsDatabase.v().getSootResolvedEntryPointsForStub(stub);
		}
		if(ret == null) {
			for(SootMethod sm : getMessageHandlerDB().getMethods()) {
				if(sm.getDeclaringClass().equals(stub)) {
					if(ret == null)
						ret = new HashSet<>();
					ret.add(sm);
				}
			}
			ret = ret == null ? null : SortingMethods.sortSet(ret, SootSort.smComp);
		}
		return ret;
	}
	
	@Override
	public Set<SootMethod> getEntryPointsFromBinderMethod(InvokeExpr ie) {
		Set<SootMethod> ret = null;
		synchronized(BinderGroupsDatabase.v()) {
			ret = BinderGroupsDatabase.v().getSootResolvedEntryPointsFromBinderMethod(ie);
		}
		if(ret == null) {
			String sig = ie.getMethodRef().getSignature();
			for(SootMethod sm : getMessageHandlerDB().getMethods()) {
				if(sm.getSignature().equals(sig))
					return Collections.singleton(sm);
			}
		}
		return ret;
	}
	
	@Override
	public Map<SootClass, Map<SootMethod, Set<IntegerWrapper>>> getEntryPointsByStubWithTransactionId() {
		Map<SootClass, Map<SootMethod, Set<IntegerWrapper>>> map = null;
		Map<SootClass, Map<SootMethod, Set<IntegerWrapper>>> ret = new LinkedHashMap<>();
		synchronized(EntryPointsDatabase.v()) {
			map = EntryPointsDatabase.v().getSootResolvedEntryPointsByStubWithTransactionId();
		}
		for(SootClass sc : map.keySet()) {
			Map<SootMethod, Set<IntegerWrapper>> retl2 = new LinkedHashMap<>();
			retl2.putAll(map.get(sc));
			ret.put(sc, retl2);
		}
		for(SootMethod sm : getMessageHandlerDB().getMethods()) {
			Map<SootMethod, Set<IntegerWrapper>> retl2 = ret.get(sm.getDeclaringClass());
			if(retl2 == null) {
				retl2 = new LinkedHashMap<>();
				ret.put(sm.getDeclaringClass(),retl2);
			}
			retl2.put(sm, Collections.emptySet());
		}
		for(SootClass sc : ret.keySet()) {
			ret.put(sc, SortingMethods.sortMapKey(ret.get(sc), SootSort.smComp));
		}
		return SortingMethods.sortMapKey(ret, SootSort.scComp);
	}

	@Override
	public Map<SootClass, Set<IntegerWrapper>> getStubsToAllTransactionIds() {
		Map<SootClass, Set<IntegerWrapper>> ret = new LinkedHashMap<>();
		synchronized(EntryPointsDatabase.v()) {
			ret.putAll(EntryPointsDatabase.v().getSootResolvedStubsToAllTransactionIds());
		}
		for(SootMethod sm : getMessageHandlerDB().getMethods()) {
			if(ret.get(sm.getDeclaringClass()) == null)
				ret.put(sm.getDeclaringClass(), Collections.emptySet());
		}
		return SortingMethods.sortMapKey(ret, SootSort.scComp);
	}
	
	@Override
	public Set<String> getAllEntryPointClasses() {
		Set<String> ret = new HashSet<>();
		synchronized(EntryPointsDatabase.v()) {
			ret.addAll(EntryPointsDatabase.v().getAllEpClasses());
		}
		for(SootMethodContainer sm : getMessageHandlerDB().getOutputData()) {
			ret.add(sm.getDeclaringClass());
		}
		return SortingMethods.sortSet(ret, SortingMethods.sComp);
	}
	
	@Override
	public Set<String> getAllEntryPointMethods() {
		Set<String> ret = new HashSet<>();
		synchronized(EntryPointsDatabase.v()) {
			ret.addAll(EntryPointsDatabase.v().getAllEpMethods());
		}
		for(SootMethodContainer sm : getMessageHandlerDB().getOutputData()) {
			ret.add(sm.getSignature());
		}
		return SortingMethods.sortSet(ret, SootSort.smStringComp);
	}
	
	@Override
	protected void resetAllSootDataLocked(boolean resetSootInstance) {
		getAndroidAPIDB().clearSootResolvedData();
		getFileMethodsDB().clearSootResolvedData();
		getMessageHandlerDB().clearSootResolvedData();
		super.resetAllSootDataLocked(resetSootInstance);
	}
	
	@Override
	protected void resetAllDatabasesAndDataLocked() {
		androidAPIDatabase = IAndroidAPIDatabase.Factory.getNew(true);
		fileMethodsDatabase = IFileMethodsDatabase.Factory.getNew(true);
		messageHandlerDatabase = IMessageHandlerDatabase.Factory.getNew(true);
		super.resetAllDatabasesAndDataLocked();
	}
	
	@Override
	public Map<SootClass, Set<SootMethod>> getEntryPointsByDeclaringClass() {
		Map<SootClass, Set<SootMethod>> map = null;
		Map<SootClass, Set<SootMethod>> ret = new LinkedHashMap<>();
		synchronized(EntryPointsDatabase.v()) {
			map = EntryPointsDatabase.v().getSootResolvedEntryPointsByDeclaringClass();
		}
		for(SootClass sc : map.keySet()) {
			ret.put(sc, SortingMethods.sortSet(map.get(sc), SootSort.smComp));
		}
		for(SootMethod sm : getMessageHandlerDB().getMethods()) {
			Set<SootMethod> retl2 = ret.get(sm.getDeclaringClass());
			if(retl2 == null) {
				retl2 = new LinkedHashSet<>();
				ret.put(sm.getDeclaringClass(),retl2);
			}
			retl2.add(sm);
		}
		for(SootClass sc : ret.keySet()) {
			ret.put(sc, SortingMethods.sortSet(ret.get(sc), SootSort.smComp));
		}
		return SortingMethods.sortMapKey(ret, SootSort.scComp);
	}
	
	//Start android api database
	
	@Override
	public IAndroidAPIDatabase getAndroidAPIDB() {
		rwlock.readLock().lock();
		try {
			return androidAPIDatabase;
		} finally {
			rwlock.readLock().unlock();
		}
	}
	
	@Override
	public void setAndroidAPIDB(IAndroidAPIDatabase db) {
		if(db != null) {
			rwlock.writeLock().lock();
			try {
				this.androidAPIDatabase = db;
			} finally {
				rwlock.writeLock().unlock();
			}
			
		}
	}
	
	//End android api database
	
	//Start file methods database
	
	@Override
	public IFileMethodsDatabase getFileMethodsDB() {
		rwlock.readLock().lock();
		try {
			return fileMethodsDatabase;
		} finally {
			rwlock.readLock().unlock();
		}
	}
	
	@Override
	public void setFileMethodsDB(IFileMethodsDatabase db) {
		if(db != null) {
			rwlock.writeLock().lock();
			try {
				this.fileMethodsDatabase = db;
			} finally {
				rwlock.writeLock().unlock();
			}
			
		}
	}
	
	//End file methods database
	
	//Start message handler database
	
	@Override
	public IMessageHandlerDatabase getMessageHandlerDB() {
		rwlock.readLock().lock();
		try {
			return messageHandlerDatabase;
		} finally {
			rwlock.readLock().unlock();
		}
	}
	
	@Override
	public void setMessageHandlerDB(IMessageHandlerDatabase db) {
		if(db != null) {
			rwlock.writeLock().lock();
			try {
				this.messageHandlerDatabase = db;
			} finally {
				rwlock.writeLock().unlock();
			}
		}
	}
	
	//End message handler database

}
