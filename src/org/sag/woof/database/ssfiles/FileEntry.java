package org.sag.woof.database.ssfiles;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.sag.common.tools.SortingMethods;
import org.sag.common.xstream.XStreamInOut;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("FileEntry")
public final class FileEntry implements XStreamInOutInterface, Comparable<FileEntry> {
	
	@XStreamAlias("Name")
	private volatile String name;
	@XStreamAlias("DirectoryPath")
	private volatile String directoryPath;
	@XStreamAlias("IsNoAccess")
	private volatile boolean isNoAccess;
	@XStreamAlias("Type")
	private volatile FileType type;
	@XStreamAlias("LinkPath")
	private volatile String linkPath;
	@XStreamAlias("LinkEntry")
	private volatile FileEntry linkEntry;
	@XStreamAlias("User")
	private volatile String user;
	@XStreamAlias("Group")
	private volatile String group;
	@XStreamAlias("UserPermissions")
	private volatile int userPermissions;
	@XStreamAlias("GroupPermissions")
	private volatile int groupPermissions;
	@XStreamAlias("GlobalPermissions")
	private volatile int globalPermissions;
	@XStreamAlias("IsSetUID")
	private volatile boolean isSetUID;
	@XStreamAlias("IsSetGID")
	private volatile boolean isSetGID;
	@XStreamAlias("IsSticky")
	private volatile boolean isSticky;
	@XStreamAlias("SELinuxContext")
	private volatile SELinuxContext selinuxContext;
	@XStreamAlias("HardLinks")
	private volatile String hardLinks;
	@XStreamAlias("Size")
	private volatile String size;
	@XStreamAlias("MajorDeviceId")
	private volatile String majorDeviceId;
	@XStreamAlias("MinorDeviceId")
	private volatile String minorDeviceId;
	@XStreamAlias("ModDateTime")
	private volatile String modDateTime;
	
	//for reading in from xml only
	private FileEntry() {}
	
	public FileEntry(String name, String directoryPath, boolean isNoAccess, FileType type, String linkPath, FileEntry linkEntry, String user, String group, 
			int userPermissions, int groupPermissions, int globalPermissions, boolean isSetUID, boolean isSetGID, boolean isSticky, 
			SELinuxContext selinuxContext, String hardLinks, String size, String majorDeviceId, String minorDeviceId, String modDateTime) {
		Objects.requireNonNull(name);
		Objects.requireNonNull(directoryPath);
		this.name = name;
		this.directoryPath = directoryPath;
		this.isNoAccess = isNoAccess;
		this.type = type;
		this.linkPath = linkPath;
		this.linkEntry = linkEntry;
		this.user = user;
		this.group = group;
		this.userPermissions = userPermissions;
		this.groupPermissions = groupPermissions;
		this.globalPermissions = globalPermissions;
		this.isSetUID = isSetUID;
		this.isSetGID = isSetGID;
		this.isSticky = isSticky;
		this.selinuxContext = selinuxContext;
		this.hardLinks = hardLinks;
		this.size = size;
		this.majorDeviceId = majorDeviceId;
		this.minorDeviceId = minorDeviceId;
		this.modDateTime = modDateTime;
	}
	
	public FileEntry(String name, String directoryPath, FileEntry cur) {
		this(name,directoryPath,cur.isNoAccess,cur.type,cur.linkPath,cur.linkEntry,cur.user,cur.group,cur.userPermissions,
				cur.groupPermissions,cur.globalPermissions,cur.isSetUID,cur.isSetGID,cur.isSticky,cur.selinuxContext,
				cur.hardLinks,cur.size,cur.majorDeviceId,cur.minorDeviceId,cur.modDateTime);
	}

	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || o.getClass() != this.getClass())
			return false;
		FileEntry other = (FileEntry)o;
		return Objects.equals(name, other.name) && Objects.equals(directoryPath, other.directoryPath);
	}
	
	@Override
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(name);
		i = i * 31 + Objects.hashCode(directoryPath);
		return i;
	}
	
	@Override
	public String toString() {
		return toString("");
	}
	
	public String toString(String spacer) {
		return entriesToString(Collections.singleton(this), spacer, true);
	}
	
	@Override
	public int compareTo(FileEntry o) {
		int ret = SortingMethods.sComp.compare(directoryPath, o.directoryPath);
		if(ret == 0)
			ret = SortingMethods.sComp.compare(name, o.name);
		return ret;
	}
	
	public String getFullPath() {
		if(directoryPath.length() == 0 || directoryPath.endsWith("/"))
			return directoryPath + name;
		return directoryPath + '/' + name;
	}
	
	public boolean fullEquals(FileEntry o) {
		if(this == o)
			return true;
		if(o == null)
			return false;
		boolean ret = Objects.equals(name, o.name) && Objects.equals(directoryPath, o.directoryPath) && isNoAccess == o.isNoAccess
				&& Objects.equals(type, o.type) && Objects.equals(linkPath, o.linkPath) && Objects.equals(user, o.user) && Objects.equals(group, o.group)
				&& userPermissions == o.userPermissions && groupPermissions == o.groupPermissions && globalPermissions == o.globalPermissions
				&& isSetUID == o.isSetUID && isSetGID == o.isSetGID && isSticky == o.isSticky && Objects.equals(selinuxContext, o.selinuxContext)
				&& Objects.equals(hardLinks, o.hardLinks) && Objects.equals(size, o.size) && Objects.equals(majorDeviceId, o.majorDeviceId)
				&& Objects.equals(minorDeviceId, o.minorDeviceId) && Objects.equals(modDateTime, o.modDateTime);
		if(ret)
			ret = (linkEntry == o.linkEntry) ? true : (linkEntry == null || o.linkEntry == null ? false : (linkEntry.fullEquals(o.linkEntry)));
		return ret;
	}

	public String getName() {
		return name;
	}

	public String getDirectoryPath() {
		return directoryPath;
	}

	public boolean isNoAccess() {
		return isNoAccess;
	}

	public FileType getType() {
		return type;
	}

	public String getLinkPath() {
		return linkPath;
	}

	public FileEntry getLinkEntry() {
		return linkEntry;
	}

	public String getUser() {
		return user;
	}

	public String getGroup() {
		return group;
	}

	public int getUserPermissions() {
		return userPermissions;
	}

	public int getGroupPermissions() {
		return groupPermissions;
	}

	public int getGlobalPermissions() {
		return globalPermissions;
	}

	public boolean isSetUID() {
		return isSetUID;
	}

	public boolean isSetGID() {
		return isSetGID;
	}

	public boolean isSticky() {
		return isSticky;
	}

	public SELinuxContext getSelinuxContext() {
		return selinuxContext;
	}

	public String getHardLinks() {
		return hardLinks;
	}

	public String getSize() {
		return size;
	}

	public String getMajorDeviceId() {
		return majorDeviceId;
	}

	public String getMinorDeviceId() {
		return minorDeviceId;
	}

	public String getModDateTime() {
		return modDateTime;
	}
	
	public static String entriesToString(Collection<FileEntry> entries, String padding, boolean omitHeader) {
		List<String> unixPerms = new ArrayList<>(); // Always len 10
		int mHardLinks = 0;
		List<String> hardLinks = new ArrayList<>();
		int mUsers = 0;
		List<String> users = new ArrayList<>();
		int mGroups = 0;
		List<String> groups = new ArrayList<>();
		int mSELinux = 0;
		List<String> seLinux = new ArrayList<>();
		int mSizes = 0;
		List<String> sizes = new ArrayList<>();
		List<String> dates = new ArrayList<>(); // Always len 18
		List<String> paths = new ArrayList<>(); // Don't pad this last one
		
		for(FileEntry e : entries) {
			if(e.type.equals(FileType.UNKNOWN)) {
				unixPerms.add("??????????");
				hardLinks.add("?");
				mHardLinks = 1 > mHardLinks ? 1 : mHardLinks;
				users.add("?");
				mUsers = 1 > mUsers ? 1 : mUsers;
				groups.add("?");
				mGroups = 1 > mGroups ? 1 : mGroups;
				seLinux.add("?");
				mSELinux = 1 > mSELinux ? 1 : mSELinux;
				sizes.add("?");
				mSizes = 1 > mSizes ? 1 : mSizes;
				dates.add("[????-??-?? ??:??]");
				paths.add(e.getFullPath());
			} else {
				String unixPerm =  e.type.toShortString();
				unixPerm += (e.userPermissions & 4) == 4 ? 'r' : '-';
				unixPerm += (e.userPermissions & 2) == 2 ? 'w' : '-';
				unixPerm += (e.userPermissions & 1) == 1 ? (e.isSetUID ? 's' : 'x') : (e.isSetUID ? 'S' : '-');
				unixPerm += (e.groupPermissions & 4) == 4 ? 'r' : '-';
				unixPerm += (e.groupPermissions & 2) == 2 ? 'w' : '-';
				unixPerm += (e.groupPermissions & 1) == 1 ? (e.isSetGID ? 's' : 'x') : (e.isSetGID ? 'S' : '-');
				unixPerm += (e.globalPermissions & 4) == 4 ? 'r' : '-';
				unixPerm += (e.globalPermissions & 2) == 2 ? 'w' : '-';
				unixPerm += (e.globalPermissions & 1) == 1 ? (e.isSticky ? 't' : 'x') : (e.isSticky ? 'T' : '-');
				unixPerms.add(unixPerm);
				
				hardLinks.add(e.hardLinks);
				mHardLinks = e.hardLinks.length() > mHardLinks ? e.hardLinks.length() : mHardLinks;
				
				users.add(e.user);
				mUsers = e.user.length() > mUsers ? e.user.length() : mUsers;
				
				groups.add(e.group);
				mGroups = e.group.length() > mGroups ? e.group.length() : mGroups;
				
				String seLinuxContext = e.selinuxContext.toString();
				seLinux.add(seLinuxContext);
				mSELinux = seLinuxContext.length() > mSELinux ? seLinuxContext.length() : mSELinux;
				
				String size = e.size != null ? e.size : e.majorDeviceId + ", " + e.minorDeviceId;
				sizes.add(size);
				mSizes = size.length() > mSizes ? size.length() : mSizes;
				
				dates.add(e.modDateTime);
				
				String path = e.getFullPath();
				if(e.type.equals(FileType.LINK))
					path += " -> " + e.linkPath;
				paths.add(path);
			}
		}
		
		StringBuilder ret = new StringBuilder();
		if(!omitHeader)
			ret.append(padding).append("File Entries [Size=").append(unixPerms.size()).append("]:\n");
		for(int i = 0; i < unixPerms.size(); i++) {
			ret.append(padding + (!omitHeader ? "  " : "") + unixPerms.get(i) + ' ' + padString(hardLinks.get(i),mHardLinks) + ' ' 
					+ padString(users.get(i),mUsers) + ' ' + padString(groups.get(i),mGroups) + ' ' + padString(seLinux.get(i),mSELinux) + ' ' 
					+ padString(sizes.get(i),mSizes) + ' ' + dates.get(i) + ' ' + paths.get(i) + '\n');
		}
		return ret.toString();
	}
	
	private final static String padString(String s, int spaces) {
		return String.format("%"+spaces+"s", s);
	}
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}
	
	public FileEntry readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this,filePath, path);
	}
	
	public static FileEntry readXMLStatic(String filePath, Path path) throws Exception {
		return new FileEntry().readXML(filePath, path);
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
				SELinuxContext.getXStreamSetupStatic().getOutputGraph(in);
				FileType.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(FileEntry.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
}
