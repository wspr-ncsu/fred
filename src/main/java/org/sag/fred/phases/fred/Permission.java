package org.sag.fred.phases.fred;

import java.io.ObjectStreamException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.sag.common.xstream.XStreamInOut;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("permission")
public class Permission implements XStreamInOutInterface, Element {
	
	@XStreamAlias("android:name")
	@XStreamAsAttribute
	private String nameField;
	@XStreamAlias("android:permissionGroup")
	@XStreamAsAttribute
	private String permissionGroupField;
	@XStreamAlias("android:label")
	@XStreamAsAttribute
	private String labelField;
	@XStreamAlias("android:description")
	@XStreamAsAttribute
	private String descriptionField;
	@XStreamAlias("android:permissionFlags")
	@XStreamAsAttribute
	private String permissionFlagsField;
	@XStreamAlias("android:protectionLevel")
	@XStreamAsAttribute
	private String protectionLevelField;
	@XStreamOmitField
	private Set<String> protectionLevels;
	
	private Permission() {}
	
	//ReadResolve is always run when reading from XML even if a constructor is run first
	protected Object readResolve() throws ObjectStreamException {
		if(protectionLevelField == null)
			throw new RuntimeException("Error: There is no protection level for '" + nameField + "'!?!");
		protectionLevels = new LinkedHashSet<>();
		if(!protectionLevelField.matches("^([a-zA-Z0-9_\\-]+\\|?)+$"))
			throw new RuntimeException("Error: Unhandled protection level string '" + protectionLevelField + "'");
		String[] plevels = protectionLevelField.split("\\|");
		for(String s : plevels) {
			protectionLevels.add(s);
		}
		return this;
	}
	
	protected Object writeReplace() throws ObjectStreamException {
		
		return this;
	}
	
	public String getNameField() {
		return nameField;
	}
	
	public String getPermissionGroupField() {
		return permissionGroupField;
	}
	
	public String getLabelField() {
		return labelField;
	}
	
	public String getDescriptionField() {
		return descriptionField;
	}
	
	public String getPermissionFlagsField() {
		return permissionFlagsField;
	}
	
	public String getProtectionLevelField() {
		return protectionLevelField;
	}
	
	public Set<String> getProtectionLevels() {
		return new LinkedHashSet<>(protectionLevels);
	}
	
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(nameField);
		i = i * 31 + Objects.hashCode(permissionGroupField);
		i = i * 31 + Objects.hashCode(labelField);
		i = i * 31 + Objects.hashCode(descriptionField);
		i = i * 31 + Objects.hashCode(permissionFlagsField);
		i = i * 31 + Objects.hashCode(protectionLevelField);
		return i;
	}
	
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || !(o instanceof Permission))
			return false;
		Permission p = (Permission)o;
		return Objects.equals(nameField, p.nameField) && Objects.equals(permissionGroupField, p.permissionGroupField) 
				&& Objects.equals(labelField, p.labelField) && Objects.equals(descriptionField, p.descriptionField) 
				&& Objects.equals(permissionFlagsField, p.permissionFlagsField) && Objects.equals(protectionLevelField, p.protectionLevelField);
	}
	
	@Override
	public int compareTo(Element o) {
		if(o instanceof Permission)
			return nameField.compareToIgnoreCase(((Permission)o).nameField);
		return o.getClass().getSimpleName().compareTo(getClass().getSimpleName());
	}
	
	public String toString() {
		return toString("");
	}
	
	public String toString(String spacer) {
		StringBuilder sb = new StringBuilder();
		sb.append(spacer).append("Permission: ").append(nameField).append("\n");
		if(permissionGroupField != null)
			sb.append(spacer).append("  PermissionGroup: ").append(permissionGroupField).append("\n");
		if(labelField != null)
			sb.append(spacer).append("  Label: ").append(labelField).append("\n");
		if(descriptionField != null)
			sb.append(spacer).append("  Description: ").append(descriptionField).append("\n");
		if(permissionFlagsField != null)
			sb.append(spacer).append("  PermissionFlags: ").append(permissionFlagsField).append("\n");
		if(protectionLevelField != null)
			sb.append(spacer).append("  ProtectionLevel: ").append(protectionLevelField).append("\n");
		return sb.toString();
	}

	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}

	@Override
	public Permission readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this, filePath, path);
	}
	
	public static Permission readXMLStatic(String filePath, Path path) throws Exception {
		return new Permission().readXML(filePath, path);
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
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(Permission.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsNoRef(xstream);
		}
		
	}

}
