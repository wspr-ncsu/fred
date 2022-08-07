package org.sag.fred.phases.fred;

import java.io.ObjectStreamException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.sag.common.tools.SortingMethods;
import org.sag.common.xstream.XStreamInOut;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("manifest")
public class SystemAndroidManifest implements XStreamInOutInterface {

	@XStreamImplicit
	private List<Element> elements;
	
	@XStreamOmitField
	private Set<PermissionGroup> permissionGroups;
	
	@XStreamOmitField
	private Set<Permission> permissions;
	
	private SystemAndroidManifest() {}
	
	//ReadResolve is always run when reading from XML even if a constructor is run first
	protected Object readResolve() throws ObjectStreamException {
		permissionGroups = new HashSet<>();
		permissions = new HashSet<>();
		for(Element e : elements) {
			if(e instanceof Permission)
				permissions.add((Permission)e);
			else if(e instanceof PermissionGroup)
				permissionGroups.add((PermissionGroup)e);
		}
		permissionGroups = SortingMethods.sortSet(permissionGroups);
		permissions = SortingMethods.sortSet(permissions);
		return this;
	}
	
	protected Object writeReplace() throws ObjectStreamException {
		elements = new ArrayList<>();
		permissionGroups = SortingMethods.sortSet(permissionGroups);
		permissions = SortingMethods.sortSet(permissions);
		elements.addAll(permissionGroups);
		elements.addAll(permissions);
		return this;
	}
	
	public Set<Permission> getPermissions() {
		return permissions;
	}
	
	public Permission getPermission(String permission) {
		for(Permission p : permissions) {
			if(p.getNameField().equals(permission))
				return p;
		}
		return null;
	}
	
	public Set<PermissionGroup> getPermissionGroups() {
		return permissionGroups;
	}
	
	public String toString() {
		return toString("");
	}
	
	public String toString(String spacer) {
		StringBuilder sb = new StringBuilder();
		sb.append(spacer).append("PermissionGroups:\n");
		for(PermissionGroup pg : permissionGroups) {
			sb.append(pg.toString(spacer + "  "));
		}
		sb.append("\n");
		sb.append(spacer).append("Permissions:\n");
		for(Permission p : permissions) {
			sb.append(p.toString(spacer + "  "));
		}
		return sb.toString();
	}
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}

	@Override
	public SystemAndroidManifest readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this, filePath, path);
	}
	
	public static SystemAndroidManifest readXMLStatic(String filePath, Path path) throws Exception {
		return new SystemAndroidManifest().readXML(filePath, path);
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
				Permission.getXStreamSetupStatic().getOutputGraph(in);
				PermissionGroup.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(SystemAndroidManifest.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsNoRef(xstream);
			xstream.ignoreUnknownElements();
		}
		
	}
	
}
