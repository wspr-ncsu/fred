package org.sag.woof.database.ssfiles;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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

@XStreamAlias("SELinuxContext")
public final class SELinuxContext implements XStreamInOutInterface, Comparable<SELinuxContext> {

	@XStreamAlias("SEUser")
	private volatile String seUser;
	@XStreamAlias("SERole")
	private volatile String seRole;
	@XStreamAlias("SEType")
	private volatile String seType;
	@XStreamAlias("SELevel")
	private volatile String seLevel;
	@XStreamAlias("SECatagories")
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"Catagory"},types={String.class})
	private volatile ArrayList<String> seCatagories;
	
	//for reading in from xml only
	private SELinuxContext() {}
	
	public SELinuxContext(String seUser, String seRole, String seType, String seLevel, List<String> seCatagories) {
		this.seUser = seUser;
		this.seRole = seRole;
		this.seType = seType;
		this.seLevel = seLevel;
		this.seCatagories = seCatagories == null ? null : new ArrayList<>(seCatagories);
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || o.getClass() != this.getClass())
			return false;
		SELinuxContext other = (SELinuxContext)o;
		return Objects.equals(seUser, other.seUser) && Objects.equals(seRole, other.seRole) && Objects.equals(seType, other.seType)
				&& Objects.equals(seLevel, other.seLevel) && Objects.equals(seCatagories, other.seCatagories);
	}
	
	@Override
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(seUser);
		i = i * 31 + Objects.hashCode(seRole);
		i = i * 31 + Objects.hashCode(seType);
		i = i * 31 + Objects.hashCode(seLevel);
		i = i * 31 + Objects.hashCode(seCatagories);
		return i;
	}
	
	@Override
	public String toString() {
		return toString("");
	}
	
	public String toString(String spacer) {
		StringBuilder sb = new StringBuilder();
		sb.append(spacer);
		sb.append(Objects.toString(seUser)).append(":");
		sb.append(Objects.toString(seRole)).append(":");
		sb.append(Objects.toString(seType)).append(":");
		sb.append(Objects.toString(seLevel));
		if(seCatagories != null && !seCatagories.isEmpty()) {
			sb.append(":");
			boolean first = true;
			for(String s : seCatagories) {
				if(first)
					first = false;
				else
					sb.append(',');
				sb.append(s);
			}
		}
		return sb.toString();
	}
	
	@Override
	public int compareTo(SELinuxContext o) {
		int ret = SortingMethods.sComp.compare(seUser, o.seUser);
		if(ret == 0)
			ret = SortingMethods.sComp.compare(seRole, o.seRole);
		if(ret == 0)
			ret = SortingMethods.sComp.compare(seType, o.seType);
		if(ret == 0)
			ret = SortingMethods.sComp.compare(seLevel, o.seLevel);
		if(ret == 0)
			ret = SortingMethods.sComp.compare(Objects.toString(seCatagories), Objects.toString(o.seCatagories));
		return ret;
	}
	
	public String getUser() {
		return seUser;
	}
	
	public String getRole() {
		return seRole;
	}

	public String getType() {
		return seType;
	}

	public String getLevel() {
		return seLevel;
	}

	public List<String> getCatagories() {
		if(seCatagories == null)
			return null;
		return new ArrayList<>(seCatagories);
	}
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}
	
	public SELinuxContext readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this,filePath, path);
	}
	
	public static SELinuxContext readXMLStatic(String filePath, Path path) throws Exception {
		return new SELinuxContext().readXML(filePath, path);
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
			return Collections.singleton(SELinuxContext.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
}
