package org.sag.woof.database.ssfiles;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.sag.acminer.database.FileHashDatabase;
import org.sag.common.tools.SortingMethods;
import org.sag.common.tuple.Quad;
import org.sag.common.xstream.XStreamInOut;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("SecuritySensitiveFilesDatabase")
public class SecuritySensitiveFilesDatabase extends FileHashDatabase implements ISecuritySensitiveFilesDatabase {
	
	@XStreamImplicit
	private volatile Set<Owner> data;
	
	@XStreamOmitField
	Map<FileEntry,Set<Owner>> fileEntriesToOwners;
	
	@XStreamOmitField
	private final ReadWriteLock rwlock;
	@XStreamOmitField
	private volatile boolean sorted;
	
	protected SecuritySensitiveFilesDatabase() {
		sorted = false;
		data = new LinkedHashSet<>();
		fileEntriesToOwners = new LinkedHashMap<>();
		rwlock = new ReentrantReadWriteLock();
	}
	
	protected Object readResolve() throws ObjectStreamException {
		//Want to be able to load this without soot so call loadSootResolvedData separately
		rwlock.writeLock().lock();
		try {
			for(Owner o : data) {
				Set<FileEntry> entries = o.getEntries();
				if(entries != null) {
					for(FileEntry e : entries) {
						Set<Owner> owners = fileEntriesToOwners.get(e);
						if(owners == null) {
							owners = new HashSet<>();
							fileEntriesToOwners.put(e, owners);
						}
						owners.add(o);
					}
				}
			}
			sorted = false;
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
		data = SortingMethods.sortSet(data);
		for(Owner o : data) {
			o.sortData();
		}
		for(FileEntry fe : fileEntriesToOwners.keySet()) {
			fileEntriesToOwners.put(fe, SortingMethods.sortSet(fileEntriesToOwners.get(fe)));
		}
		fileEntriesToOwners = SortingMethods.sortMapKeyAscending(fileEntriesToOwners);
		sorted = true;
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
	public String toString() {
		return toString("");
	}
	
	@Override
	public String toString(String spacer) {
		StringBuilder sb = new StringBuilder();
		sb.append(spacer).append("# Security Sensitive Files Database:\n");
		for(Owner m : getOutputData()) {
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
		if(o == null || !(o instanceof ISecuritySensitiveFilesDatabase))
			return false;
		ISecuritySensitiveFilesDatabase other = (ISecuritySensitiveFilesDatabase)o;
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
	public void add(Owner owner) {
		if(owner != null) {
			rwlock.writeLock().lock();
			try {
				addInner(owner);
			} finally {
				rwlock.writeLock().unlock();
			}
		}
	}
	
	@Override
	public void addAll(Set<Owner> owners) {
		if(owners != null) {
			rwlock.writeLock().lock();
			try {
				for(Owner owner : owners)
					addInner(owner);
			} finally {
				rwlock.writeLock().unlock();
			}
		}
	}
	
	private void addInner(Owner o) {
		if(data.add(o)) {
			Set<FileEntry> entries = o.getEntries();
			if(entries != null) {
				for(FileEntry e : entries) {
					Set<Owner> owners = fileEntriesToOwners.get(e);
					if(owners == null) {
						owners = new HashSet<>();
						fileEntriesToOwners.put(e, owners);
					}
					owners.add(o);
				}
			}
			sorted = false;
		}
	}
	
	@Override
	public Set<Owner> getOutputData() {
		Set<Owner> ret = new LinkedHashSet<>();
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
	public Map<FileEntry,Set<Owner>> getFilesToOwners() {
		Map<FileEntry,Set<Owner>> ret = new LinkedHashMap<>();
		rwlock.readLock().lock();
		try {
			sortDataRLocked();
			for(FileEntry fe : fileEntriesToOwners.keySet()) {
				ret.put(fe, new LinkedHashSet<>(fileEntriesToOwners.get(fe)));
			}
		} finally {
			rwlock.readLock().unlock();
		}
		return ret;
	}
	
	@Override
	public Map<FileEntry,Set<Owner>> getFilesToGroups() {
		Map<FileEntry,Set<Owner>> ret = new LinkedHashMap<>();
		rwlock.readLock().lock();
		try {
			sortDataRLocked();
			for(FileEntry fe : fileEntriesToOwners.keySet()) {
				Set<Owner> groups = new LinkedHashSet<>();
				for(Owner o : fileEntriesToOwners.get(fe)) {
					if(o.isGroup()) {
						groups.add(o);
					}
				}
				if(!groups.isEmpty())
					ret.put(fe, groups);
			}
		} finally {
			rwlock.readLock().unlock();
		}
		return ret;
	}
	
	public Map<FileEntry,Set<Owner>> getFilesToGroupsOrSystem() {
		Map<FileEntry,Set<Owner>> ret = new LinkedHashMap<>();
		rwlock.readLock().lock();
		try {
			sortDataRLocked();
			for(FileEntry fe : fileEntriesToOwners.keySet()) {
				Set<Owner> groups = new LinkedHashSet<>();
				for(Owner o : fileEntriesToOwners.get(fe)) {
					if(o.isGroup() || o.getName().equals("system")) {
						groups.add(o);
					}
				}
				if(!groups.isEmpty())
					ret.put(fe, groups);
			}
		} finally {
			rwlock.readLock().unlock();
		}
		return ret;
	}
	
	@Override
	public Map<FileEntry,Set<Owner>> getFilesToUsers() {
		Map<FileEntry,Set<Owner>> ret = new LinkedHashMap<>();
		rwlock.readLock().lock();
		try {
			sortDataRLocked();
			for(FileEntry fe : fileEntriesToOwners.keySet()) {
				Set<Owner> users = new LinkedHashSet<>();
				for(Owner o : fileEntriesToOwners.get(fe)) {
					if(!o.isGroup()) {
						users.add(o);
					}
				}
				if(!users.isEmpty())
					ret.put(fe, users);
			}
		} finally {
			rwlock.readLock().unlock();
		}
		return ret;
	}
	
	@Override
	public SecuritySensitiveFilesDatabase readJSON(Path in) throws Exception {
		Map<String,FileEntry> fileEntries = new HashMap<>();
		Map<Owner,Owner> owners = new HashMap<>();
		List<Quad<String,Boolean,Set<String>,List<List<FileEntry>>>> data = parseJson(in, fileEntries);
		for(Quad<String,Boolean,Set<String>,List<List<FileEntry>>> q : data) {
			String name = q.getFirst();
			boolean isGroup = q.getSecond();
			Set<String> permissions = q.getThird();
			List<List<FileEntry>> paths = q.getFourth();
			Set<FileEntry> entries = null;
			if(paths != null && !paths.isEmpty()) {
				entries = new HashSet<>();
				for(List<FileEntry> path : paths) {
					String fileName = null;
					String directoryPath = null;
					FileEntry finalEntry = null;
					if(path == null || path.size() == 0) { // Should not happen
						throw new Exception("Encountered a path with no parts for user '" + name + "'");
					} else if(path.size() == 1) { // Can only be the root path
						fileName = "/";
						directoryPath = "";
						finalEntry = path.get(0);
					} else {
						FileEntry parent = null;
						for(FileEntry cur : path) {
							if(parent == null) {
								directoryPath = cur.getName();
								fileName = null;
							} else {
								if(fileName == null) {
									fileName = cur.getName();
								} else {
									if(!parent.getType().equals(FileType.LINK)) {
										if(directoryPath.length() == 0 || directoryPath.endsWith("/"))
											directoryPath = directoryPath + fileName;
										else
											directoryPath = directoryPath + "/" + fileName;
										fileName = cur.getName();
									}
								}
							}
							parent = cur;
						}
						finalEntry = parent;
					}
					FileEntry entry = new FileEntry(fileName, directoryPath, finalEntry);
					FileEntry exist = fileEntries.get(entry.getFullPath());
					if(exist != null)
						entry = exist;
					else
						fileEntries.put(entry.getFullPath(), entry);
					entries.add(entry);
				}
				if(entries.isEmpty())
					entries = null;
			}
			Owner o = new Owner(name, isGroup, permissions, entries);
			Owner e = owners.get(o);
			if(e != null) {
				Set<String> perms = new HashSet<>();
				perms.addAll(o.getPermissions());
				perms.addAll(e.getPermissions());
				Set<FileEntry> ents = new HashSet<>();
				if(o.getEntries() != null)
					ents.addAll(o.getEntries());
				if(e.getEntries() != null)
					ents.addAll(e.getEntries());
				if(ents.isEmpty())
					ents = null;
				o = new Owner(o.getName(),o.isGroup(),perms,ents);
			}
			owners.put(o, o);
		}
		addAll(owners.keySet());
		return this;
	}
	
	public static SecuritySensitiveFilesDatabase readJSONStatic(Path in) throws Exception {
		return new SecuritySensitiveFilesDatabase().readJSON(in);
	}
	
	private static final List<Quad<String,Boolean,Set<String>,List<List<FileEntry>>>> parseJson(Path in, 
			Map<String,FileEntry> fileEntries) throws IOException {
		JSONObject root = new JSONObject(new JSONTokener(Files.newBufferedReader(in)));
		
		Map<SELinuxContext,SELinuxContext> contextes = new HashMap<>();
		List<Quad<String,Boolean,Set<String>,List<List<FileEntry>>>> ret = new ArrayList<>();
		ret.addAll(parseUsersOrGroups(root.getJSONArray("users"), fileEntries, contextes, false));
		ret.addAll(parseUsersOrGroups(root.getJSONArray("groups"), fileEntries, contextes, true));
		return ret;
	}
	
	private static final List<Quad<String,Boolean,Set<String>,List<List<FileEntry>>>> parseUsersOrGroups(JSONArray arr, 
			Map<String,FileEntry> fileEntries, Map<SELinuxContext,SELinuxContext> contextes, boolean isGroup) {
		List<Quad<String,Boolean,Set<String>,List<List<FileEntry>>>> res = new ArrayList<>();
		for(Object o : arr) {
			JSONObject obj = (JSONObject)o;
			String name = obj.getString("name");
			
			Set<String> perms = new HashSet<>();
			for(Object perm : obj.getJSONArray("permissions"))
				perms.add((String)perm);
			perms = SortingMethods.sortSet(perms,SortingMethods.sComp);
			
			JSONArray paths = obj.optJSONArray("paths");
			List<List<FileEntry>> pathsRes = null;
			if(paths != null) {
				pathsRes = new ArrayList<>();
				for(Object path : paths) {
					List<FileEntry> pathRes = new ArrayList<>();
					for(Object fileEntry : (JSONArray)path) {
						pathRes.add(parseFileEntry((JSONObject)fileEntry, fileEntries, contextes));
					}
					if(!pathRes.isEmpty())
						pathsRes.add(pathRes);
				}
				if(pathsRes.isEmpty())
					pathsRes = null;
			}
			
			res.add(new Quad<>(name, isGroup, perms, pathsRes));
		}
		return res;
	}
	
	private static final FileEntry parseFileEntry(JSONObject fileEntry, Map<String,FileEntry> existingFileEntry, 
			Map<SELinuxContext,SELinuxContext> contextes) {
		if(fileEntry == null)
			return null;
		String name = fileEntry.getString("name");
		String directoryPath = fileEntry.getString("directoryPath");
		boolean isNoAccess = fileEntry.optBoolean("isNoAccess", false);
		FileType type = FileType.toFileType(fileEntry.getString("type"));
		String linkPath = fileEntry.optString("linkPath", null);
		FileEntry linkEntry = parseFileEntry(fileEntry.optJSONObject("linkEntry"), existingFileEntry, contextes);
		String user = fileEntry.getString("user");
		String group = fileEntry.getString("group");
		int userPermissions = fileEntry.getInt("userPermissions");
		int groupPermissions = fileEntry.getInt("groupPermissions");
		int globalPermissions = fileEntry.getInt("globalPermissions");
		boolean isSetUID = fileEntry.optBoolean("isSetUID", false);
		boolean isSetGID = fileEntry.optBoolean("isSetGID", false);
		boolean isSticky = fileEntry.optBoolean("isSticky", false);
		SELinuxContext selinuxContext = parseSELinuxContext(fileEntry.getJSONObject("selinuxContext"), contextes);
		String hardLinks = fileEntry.optString("hardLinks", null);
		String size = fileEntry.optString("size", null);
		String majorDeviceId = fileEntry.optString("majorDeviceId", null);
		String minorDeviceId = fileEntry.optString("minorDeviceId", null);
		String modDateTime = fileEntry.optString("modDateTime", null);
		
		FileEntry ret = new FileEntry(name, directoryPath, isNoAccess, type, linkPath, linkEntry, user, group, userPermissions, groupPermissions, 
				globalPermissions, isSetUID, isSetGID, isSticky, selinuxContext, hardLinks, size, majorDeviceId, minorDeviceId, modDateTime);
		FileEntry exist = existingFileEntry.get(ret.getFullPath());
		if(exist != null)
			ret = exist;
		else
			existingFileEntry.put(ret.getFullPath(), ret);
		return ret;
	}
	
	private static final SELinuxContext parseSELinuxContext(JSONObject selinuxContext, Map<SELinuxContext,SELinuxContext> contextes) {
		String seUser = selinuxContext.getString("seUser");
		String seRole = selinuxContext.getString("seRole");
		String seType = selinuxContext.getString("seType");
		String seLevel = selinuxContext.getString("seLevel");
		List<String> seCatagories = null;
		JSONArray cats = selinuxContext.optJSONArray("seCatagories");
		if(cats != null) {
			seCatagories = new ArrayList<>();
			for(Object cat : cats)
				seCatagories.add((String)cat);
			if(seCatagories.isEmpty())
				seCatagories = null;
		}
		SELinuxContext ret = new SELinuxContext(seUser, seRole, seType, seLevel, seCatagories);
		SELinuxContext exist = contextes.get(ret);
		if(exist != null)
			ret = exist;
		else
			contextes.put(ret, ret);
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
	public SecuritySensitiveFilesDatabase readXML(String filePath, Path path) throws Exception {
		rwlock.writeLock().lock();
		try {
			return XStreamInOut.readXML(this, filePath, path);
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	public static SecuritySensitiveFilesDatabase readXMLStatic(String filePath, Path path) throws Exception {
		return new SecuritySensitiveFilesDatabase().readXML(filePath, path);
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
				Owner.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(SecuritySensitiveFilesDatabase.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
	public static void main(String[] args) throws Exception {
		SecuritySensitiveFilesDatabase ssFilesDB = readXMLStatic("C:\\CS\\Documents\\Work\\Research\\woof\\aosp-10.0.0\\woof\\security_sensitive_files_db.xml", null);
		System.out.println(ssFilesDB.getFilesToGroupsOrSystem().keySet().size());
	}

}
