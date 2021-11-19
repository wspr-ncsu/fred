package org.sag.woof.database.ssfiles;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.sag.common.tools.SortingMethods;
import org.sag.common.xstream.NamedCollectionConverterWithSize;
import org.sag.common.xstream.XStreamInOut;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("Owner")
public class Owner implements XStreamInOutInterface, Comparable<Owner> {
	
	@XStreamAlias("Name")
	private volatile String name;
	@XStreamAlias("Type")
	private volatile String type;
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"Permission"},types={String.class})
	private volatile LinkedHashSet<String> permissions;
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"FileEntry"},types={FileEntry.class})
	private volatile LinkedHashSet<FileEntry> entries;

	//for reading in from xml only
	private Owner() {}
	
	public Owner(String name, boolean isGroup, Set<String> permissions, Set<FileEntry> entries) {
		Objects.requireNonNull(name);
		Objects.requireNonNull(permissions);
		this.name = name;
		this.type = isGroup ? "Group" : "User";
		this.permissions = SortingMethods.sortSet(permissions,SortingMethods.sComp);
		this.entries = entries == null ? null : SortingMethods.sortSet(entries);
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || o.getClass() != this.getClass())
			return false;
		Owner other = (Owner)o;
		return Objects.equals(name, other.name) && Objects.equals(type, other.type);
	}
	
	@Override
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(name);
		i = i * 31 + Objects.hashCode(type);
		return i;
	}
	
	@Override
	public String toString() {
		return toString("");
	}
	
	public String toString(String padding) {
		StringBuilder ret = new StringBuilder();
		ret.append(padding).append(type + ": " + name + "\n");
		ret.append(padding).append("  Permissions: \n");
		for(String p : permissions) {
			ret.append(padding).append("    " + p + "\n");
		}
		if(entries != null)
			ret.append(FileEntry.entriesToString(entries, padding + "  ", false));
		return ret.toString();
	}
	
	@Override
	public int compareTo(Owner o) {
		int ret = SortingMethods.sComp.compare(type, o.type);
		if(ret == 0)
			ret = SortingMethods.sComp.compare(name, o.name);
		return ret;
	}
	
	public void sortData() {
		this.permissions = SortingMethods.sortSet(permissions,SortingMethods.sComp);
		this.entries = entries == null ? null : SortingMethods.sortSet(entries);
	}
	
	public String getName() {
		return name;
	}
	
	public boolean isGroup() {
		return type.equals("Group");
	}

	public Set<String> getPermissions() {
		return new LinkedHashSet<>(permissions);
	}

	public Set<FileEntry> getEntries() {
		if(entries == null)
			return null;
		return new LinkedHashSet<>(entries);
	}

	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}
	
	public Owner readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this,filePath, path);
	}
	
	public static Owner readXMLStatic(String filePath, Path path) throws Exception {
		return new Owner().readXML(filePath, path);
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
			return Collections.singleton(Owner.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
}
