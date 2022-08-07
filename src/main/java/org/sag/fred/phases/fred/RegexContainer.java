package org.sag.fred.phases.fred;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.sag.common.tools.SortingMethods;
import org.sag.common.xstream.NamedCollectionConverterWithSize;
import org.sag.common.xstream.XStreamInOut;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;
import org.sag.fred.database.ssfiles.FileEntry;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("RegexContainer")
public final class RegexContainer implements XStreamInOutInterface, Comparable<RegexContainer> {
	
	@XStreamAlias("Regex")
	private String regex;
	@XStreamAlias("IntermediateExpressions")
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"IntermediateExpression"},types={IntermediateExpression.class})
	private LinkedHashSet<IntermediateExpression> ies;
	@XStreamAlias("Files")
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"FileContainer"},types={FileContainer.class})
	private LinkedHashSet<FileContainer> files;
	
	private RegexContainer() {}
	
	public RegexContainer(String regex) {
		Objects.requireNonNull(regex);
		this.regex = regex;
		this.ies = new LinkedHashSet<>();
		this.files = new LinkedHashSet<>();
	}
	
	public RegexContainer clone() {
		RegexContainer r = new RegexContainer(regex);
		for(FileContainer f : files) {
			r.files.add(f.clone());
		}
		for(IntermediateExpression i : ies) {
			r.ies.add(i.clone());
		}
		return r;
	}
	
	public synchronized void addIE(IntermediateExpression ie) {
		Objects.requireNonNull(ie);
		ies.add(ie);
	}
	
	public synchronized FileContainer addFile(FileEntry fileEntry, Set<String> permissions) {
		return addFile(fileEntry, permissions, null);
	}
	
	public synchronized FileContainer addFile(FileEntry fileEntry, Set<String> permissions, Set<String> missingPermissions) {
		Objects.requireNonNull(fileEntry);
		if(permissions == null)
			permissions = Collections.emptySet();
		for(FileContainer f : files) {
			if(f.getFileEntry().equals(fileEntry)) {
				f.getPermissions().addAll(permissions);
				if(missingPermissions != null && !missingPermissions.isEmpty()) {
					if(f.getMissingPermissions() == null)
						f.setMissingPermissions(missingPermissions);
					else
						f.getMissingPermissions().addAll(missingPermissions);
				}
				f.sort();
				return f;
			}
		}
		FileContainer ret = new FileContainer(fileEntry, permissions == null ? Collections.emptySet() : permissions);
		if(missingPermissions != null && !missingPermissions.isEmpty())
			ret.setMissingPermissions(missingPermissions);
		files.add(ret);
		return ret;
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || !(o instanceof RegexContainer))
			return false;
		RegexContainer other = (RegexContainer)o;
		return Objects.equals(regex, other.regex);
	}
	
	@Override
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(regex);
		return i;
	}
	
	@Override
	public int compareTo(RegexContainer o) {
		return regex.compareTo(o.regex);
	}
	
	@Override
	public String toString() {
		return toString("");
	}
	
	public String toString(String spacer) {
		StringBuilder sb = new StringBuilder();
		sb.append(spacer).append(regex).append("\n");
		if(!files.isEmpty()) {
			sb.append(spacer).append("  Files: \n");
			for(FileContainer f : files) {
				sb.append(spacer).append("    ").append(f.toString()).append(" - User: ").append(f.getFileEntry().getUser())
				.append(" - Group: ").append(f.getFileEntry().getGroup()).append("\n");
				if(f.getMissingPermissions() != null) 
					sb.append(spacer).append("      Missing Permissions: ").append(f.getMissingPermissions()).append("\n");
				if(!f.getPermissions().isEmpty())
					sb.append(spacer).append("      UID/GID Permissions: ").append(f.getPermissions()).append("\n");
			}
		}
		if(!ies.isEmpty()) {
			sb.append(spacer).append("  Intermediate Expressions: \n");
			for(IntermediateExpression ie : ies) {
				sb.append(ie.toString(spacer + "    ")).append("\n");
			}
		}
		return sb.toString();
	}

	public String getRegex() {
		return regex;
	}

	public Set<IntermediateExpression> getIntermediateExpressions() {
		return ies;
	}

	public Set<FileContainer> getFiles() {
		return files;
	}
	
	public synchronized void sort() {
		ies = SortingMethods.sortSet(ies);
		files = SortingMethods.sortSet(files);
	}

	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}
	
	public RegexContainer readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this,filePath, path);
	}
	
	public static RegexContainer readXMLStatic(String filePath, Path path) throws Exception {
		return new RegexContainer().readXML(filePath, path);
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
				IntermediateExpression.getXStreamSetupStatic().getOutputGraph(in);
				FileContainer.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(RegexContainer.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}

}
