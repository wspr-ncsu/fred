package org.sag.fred.database.ssfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.sag.common.io.FileHelpers;
import org.sag.common.tools.SortingMethods;
import org.sag.common.tuple.Quad;

public class Main {
	
	public static void main(String[] args) throws Exception {
		SecuritySensitiveFilesDatabase sdb = makeDatabase(FileHelpers.getPath("C:\\CS\\Documents\\Work\\Research\\fred\\samsung-s20-10.0.0\\fred\\parts\\ls_s20_db.json"),
				FileHelpers.getPath("C:\\CS\\Documents\\Work\\Research\\fred\\samsung-s20-10.0.0\\fred\\parts\\platform-permissions_db.json"));
		sdb.writeXML("C:\\CS\\Documents\\Work\\Research\\fred\\samsung-s20-10.0.0\\fred\\security_sensitive_files_db.xml", null);
		System.out.println(sdb.toString());
	}
	
	public static final SecuritySensitiveFilesDatabase makeDatabase(Path fileTreeFile, Path platformPermissionsFile) throws Exception {
		Map<String,FileEntry> pathToEntry = parseFileTree(fileTreeFile);
		List<Quad<String,Boolean,Set<String>,Set<FileEntry>>> platformPermissions = parsePlatformPermissions(platformPermissionsFile);
		
		Map<FileEntry,List<FileEntry>> adjLists = new HashMap<>();
		FileEntry rootEntry = pathToEntry.get("/");
		if(rootEntry == null)
			throw new RuntimeException("A entry for the root does not exist!?!");
		
		for(FileEntry entry : pathToEntry.values()) {
			if(entry.equals(rootEntry))
				continue; // Skip root entry because it has no parent entry and is not a simlink
			
			if(!pathToEntry.containsKey(entry.getDirectoryPath()))
				throw new RuntimeException("No entry for parent path '" + entry.getDirectoryPath() + "'.");
			FileEntry parentEntry = pathToEntry.get(entry.getDirectoryPath());
			
			List<FileEntry> adjList = adjLists.get(parentEntry);
			if(adjList == null) {
				adjList = new ArrayList<>();
				adjLists.put(parentEntry, adjList);
			}
			adjList.add(entry);
			
			if(entry.getType().equals(FileType.LINK) && entry.getLinkEntry() != null) {
				List<FileEntry> adj = adjLists.get(entry);
				if(adj == null) {
					adj = new ArrayList<>();
					adjLists.put(entry, adj);
				}
				adj.add(entry.getLinkEntry());
			}
		}
		
		ArrayDeque<FileEntry> stack = new ArrayDeque<>();
		ArrayDeque<Integer> childIndexStack = new ArrayDeque<>();
		stack.add(rootEntry);
		childIndexStack.add(0);
		while(!stack.isEmpty()) {
			FileEntry cur = stack.pop();
			int childIndex = childIndexStack.pop();
			
			// No children, already explored children, or is a symbolic link we have traversed or encountered another symbolic link pointing to the same thing
			// Also remove the file descriptors in proc and the subsystem symlinks in sys because both take forever to iterate over all possible paths and there
			// is nothing there anyways.
			List<FileEntry> adjList = adjLists.get(cur);
			if(adjList != null && childIndex < adjList.size() && (!cur.getType().equals(FileType.LINK) || !(stack.contains(cur) || stack.contains(cur.getLinkEntry()))
					&& !(cur.getName().equals("fd") && stack.size() > 2 && stackHasElement(stack,1,"proc"))
					&& !(cur.getName().equals("subsystem")) && stack.size() > 2 && stackHasElement(stack,1,"sys"))) {
				FileEntry next = adjList.get(childIndex);
				stack.push(cur);
				childIndexStack.push(childIndex + 1);
				stack.push(next);
				childIndexStack.push(0);
			} else {
				for(Quad<String,Boolean,Set<String>,Set<FileEntry>> q : platformPermissions) {
					if((q.getSecond() && q.getFirst().equals(cur.getGroup())) || (!q.getSecond() && q.getFirst().equals(cur.getUser()))) {
						List<FileEntry> path = new ArrayList<>(stack);
						Collections.reverse(path);
						path.add(cur);
						FileEntry collapsed = pathToFileEntry(path, pathToEntry);
						q.getFourth().add(collapsed);
					}
				}
			}
		}
		
		Map<Owner,Owner> owners = new HashMap<>();
		for(Quad<String,Boolean,Set<String>,Set<FileEntry>> q : platformPermissions) {
			Set<FileEntry> entries = q.getFourth();
			Owner o = new Owner(q.getFirst(),q.getSecond(),q.getThird(),entries == null ? null : entries);
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
		
		SecuritySensitiveFilesDatabase ss = new SecuritySensitiveFilesDatabase();
		ss.addAll(owners.keySet());
		
		return ss;
	}
	
	private static final FileEntry pathToFileEntry(List<FileEntry> path, Map<String,FileEntry> pathToEntry) {
		String fileName = null;
		String directoryPath = null;
		FileEntry finalEntry = null;
		if(path == null || path.size() == 0) { // Should not happen
			throw new RuntimeException("Encountered a path with no parts");
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
		FileEntry exist = pathToEntry.get(entry.getFullPath());
		if(exist != null)
			entry = exist;
		else
			pathToEntry.put(entry.getFullPath(), entry);
		return entry;
	}
	
	private static final boolean stackHasElement(ArrayDeque<FileEntry> stack, int index, String name) {
		int i = 0;
		for(FileEntry entry : stack) {
			if(i == index) {
				return entry.getName().equals(name);
			}
			i++;
		}
		return false;
	}
	
	public static final List<Quad<String,Boolean,Set<String>,Set<FileEntry>>> parsePlatformPermissions(Path in) throws Exception {
		JSONObject root = new JSONObject(new JSONTokener(Files.newBufferedReader(in)));
		List<Quad<String,Boolean,Set<String>,Set<FileEntry>>> platformPermissions = new ArrayList<>();
		platformPermissions.addAll(parseUsersOrGroups(root.getJSONArray("users"), false));
		platformPermissions.addAll(parseUsersOrGroups(root.getJSONArray("groups"), true));
		platformPermissions.add(new Quad<>("system", true, Collections.emptySet(), new HashSet<>()));
		platformPermissions.add(new Quad<>("system", false, Collections.emptySet(), new HashSet<>()));
		//platformPermissions.add(new Quad<>("root", true, Collections.emptySet(), new HashSet<>()));
		return platformPermissions;
	}
	
	private static final List<Quad<String,Boolean,Set<String>,Set<FileEntry>>> parseUsersOrGroups(JSONArray arr, boolean isGroup) {
		List<Quad<String,Boolean,Set<String>,Set<FileEntry>>> res = new ArrayList<>();
		for(Object o : arr) {
			JSONObject obj = (JSONObject)o;
			String name = obj.getString("name");
			
			Set<String> perms = new HashSet<>();
			for(Object perm : obj.getJSONArray("permissions"))
				perms.add((String)perm);
			perms = SortingMethods.sortSet(perms,SortingMethods.sComp);
			res.add(new Quad<>(name, isGroup, perms, new HashSet<>()));
		}
		return res;
	}
	
	public static final Map<String,FileEntry> parseFileTree(Path in) throws JSONException, IOException {
		JSONObject root = new JSONObject(new JSONTokener(Files.newBufferedReader(in)));
		
		Map<SELinuxContext,SELinuxContext> contextes = new HashMap<>();
		Map<String,FileEntry> fileEntries = new HashMap<>();
		
		JSONArray entries = root.getJSONArray("entries");
		if(entries != null) {
			for(Object entry : entries) {
				parseFileEntry((JSONObject)entry, fileEntries, contextes);
			}
		}
		return fileEntries;
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

}
