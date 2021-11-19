package org.sag.woof.phases.woof;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.sag.common.tools.SortingMethods;
import org.sag.common.xstream.NamedCollectionConverterWithSize;
import org.sag.common.xstream.XStreamInOut;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;
import org.sag.woof.database.ssfiles.FileEntry;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("FileContainer")
public final class FileContainer implements XStreamInOutInterface, Comparable<FileContainer> {
	
	@XStreamAlias("FileEntry")
	private FileEntry fileEntry;
	@XStreamAlias("Permissions")
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"Permission"},types={String.class})
	private LinkedHashSet<String> permissions;
	@XStreamAlias("MissingPermissions")
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"Permission"},types={String.class})
	private LinkedHashSet<String> missingPermissions;
	
	private FileContainer() {}
	
	public FileContainer(FileEntry fileEntry, Set<String> permissions) {
		Objects.requireNonNull(fileEntry);
		Objects.requireNonNull(permissions);
		this.fileEntry = fileEntry;
		this.permissions = SortingMethods.sortSet(permissions);
		this.missingPermissions = null;
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || o.getClass() != this.getClass())
			return false;
		FileContainer other = (FileContainer)o;
		return Objects.equals(fileEntry, other.fileEntry);
	}
	
	@Override
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(fileEntry);
		return i;
	}
	
	public FileContainer clone() {
		FileContainer ret = new FileContainer(fileEntry, new LinkedHashSet<>(permissions));
		ret.missingPermissions = missingPermissions == null ? null : new LinkedHashSet<>(missingPermissions);
		return ret;
	}
	
	@Override
	public int compareTo(FileContainer o) {
		return fileEntry.compareTo(o.fileEntry);
	}
	
	public String toString() {
		return fileEntry.getFullPath();
	}
	
	public FileEntry getFileEntry() {
		return fileEntry;
	}

	public Set<String> getPermissions() {
		return permissions;
	}
	
	public Set<String> getMissingPermissions() {
		return missingPermissions;
	}
	
	public void setMissingPermissions(Set<String> perms) {
		if(missingPermissions == null) {
			missingPermissions = new LinkedHashSet<>();
		}
		missingPermissions.addAll(perms);
		missingPermissions = SortingMethods.sortSet(missingPermissions);
	}
	
	public boolean isSystem() {
		return fileEntry.getGroup().equals("system") || fileEntry.getUser().equals("system");
	}
	
	public void sort() {
		permissions = SortingMethods.sortSet(permissions);
		if(missingPermissions != null)
			missingPermissions = SortingMethods.sortSet(missingPermissions);
	}

	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}
	
	public FileContainer readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this,filePath, path);
	}
	
	public static FileContainer readXMLStatic(String filePath, Path path) throws Exception {
		return new FileContainer().readXML(filePath, path);
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
				FileEntry.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(FileContainer.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
}
