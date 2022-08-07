package org.sag.fred.phases.fred;

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

@XStreamAlias("permission-group")
public class PermissionGroup implements XStreamInOutInterface, Element {
	
	@XStreamAlias("android:name")
	@XStreamAsAttribute
	private String nameField;
	@XStreamAlias("android:icon")
	@XStreamAsAttribute
	private String iconField;
	@XStreamAlias("android:label")
	@XStreamAsAttribute
	private String labelField;
	@XStreamAlias("android:description")
	@XStreamAsAttribute
	private String descriptionField;
	@XStreamAlias("android:priority")
	@XStreamAsAttribute
	private String priorityField;
	
	private PermissionGroup() {}
	
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(nameField);
		i = i * 31 + Objects.hashCode(iconField);
		i = i * 31 + Objects.hashCode(labelField);
		i = i * 31 + Objects.hashCode(descriptionField);
		i = i * 31 + Objects.hashCode(priorityField);
		return i;
	}
	
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || !(o instanceof PermissionGroup))
			return false;
		PermissionGroup p = (PermissionGroup)o;
		return Objects.equals(nameField, p.nameField) && Objects.equals(iconField, p.iconField) 
				&& Objects.equals(labelField, p.labelField) && Objects.equals(descriptionField, p.descriptionField) 
				&& Objects.equals(priorityField, p.priorityField);
	}
	
	@Override
	public int compareTo(Element o) {
		if(o instanceof PermissionGroup)
			return nameField.compareToIgnoreCase(((PermissionGroup)o).nameField);
		return o.getClass().getSimpleName().compareTo(getClass().getSimpleName());
	}
	
	public String toString() {
		return toString("");
	}
	
	public String toString(String spacer) {
		StringBuilder sb = new StringBuilder();
		sb.append(spacer).append("PermissionGroup: ").append(nameField).append("\n");
		if(iconField != null)
			sb.append(spacer).append("  Icon: ").append(iconField).append("\n");
		if(labelField != null)
			sb.append(spacer).append("  Label: ").append(labelField).append("\n");
		if(descriptionField != null)
			sb.append(spacer).append("  Description: ").append(descriptionField).append("\n");
		if(priorityField != null)
			sb.append(spacer).append("  Priority: ").append(priorityField).append("\n");
		return sb.toString();
	}
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}

	@Override
	public PermissionGroup readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this, filePath, path);
	}
	
	public static PermissionGroup readXMLStatic(String filePath, Path path) throws Exception {
		return new PermissionGroup().readXML(filePath, path);
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
			return Collections.singleton(PermissionGroup.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsNoRef(xstream);
		}
		
	}

}
