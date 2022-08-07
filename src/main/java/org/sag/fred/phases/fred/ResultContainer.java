package org.sag.fred.phases.fred;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.sag.acminer.database.acminer.Doublet;
import org.sag.common.tools.SortingMethods;
import org.sag.common.xstream.NamedCollectionConverterWithSize;
import org.sag.common.xstream.XStreamInOut;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;
import org.sag.fred.database.filepaths.parts.PHPart;
import org.sag.fred.database.filepaths.parts.Part;
import org.sag.fred.database.ssfiles.FileEntry;
import org.sag.fred.database.ssfiles.Owner;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("Result")
public final class ResultContainer implements XStreamInOutInterface, Comparable<ResultContainer> {

	@XStreamAlias("EntryPointNode")
	private volatile EntryPointNode ep;
	
	@XStreamAlias("FileEntry")
	private volatile FileEntry fe;
	
	@XStreamAlias("Owners")
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"Owner"},types={String.class})
	private volatile ArrayList<String> owners;
	
	@XStreamAlias("MatchingPart")
	private volatile Part matchingPart;
	
	@XStreamAlias("OriginalPart")
	private volatile Part originalPart;
	
	@XStreamAlias("SeedPart")
	private volatile PHPart seedPart;
	
	@XStreamAlias("MissingPermissions")
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"Permission"},types={Doublet.class})
	private volatile LinkedHashSet<Doublet> missingPerms;
	
	@XStreamAlias("ExistingPermissions")
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"Permission"},types={Doublet.class})
	private volatile LinkedHashSet<Doublet> existingPerms;
	
	private ResultContainer() {}
	
	public ResultContainer(EntryPointNode ep, FileEntry fe, Set<Owner> owners, PHPart seedPart, Part matchingPart, Part originalPart, 
			Set<Doublet> missingPerms, Set<Doublet> existingPerms) {
		Objects.requireNonNull(ep);
		Objects.requireNonNull(fe);
		Objects.requireNonNull(seedPart);
		Objects.requireNonNull(matchingPart);
		Objects.requireNonNull(originalPart);
		Objects.requireNonNull(missingPerms);
		Objects.requireNonNull(existingPerms);
		this.ep = ep;
		this.fe = fe;
		this.seedPart = seedPart;
		this.matchingPart = matchingPart;
		this.originalPart = originalPart;
		this.missingPerms = SortingMethods.sortSet(missingPerms);
		this.existingPerms = SortingMethods.sortSet(existingPerms);
		this.owners = new ArrayList<>();
		for(Owner o : owners) {
			boolean found = false;
			for(String perm : o.getPermissions()) {
				perm = "`\"" + perm + "\"`";
				for(Doublet d : missingPerms) {
					if(d.toString().equals(perm)) {
						found = true;
						break;
					}
				}
				if(found)
					break;
			}
			if(found)
				this.owners.add("Name=" + o.getName() + ", Type=" + (o.isGroup() ? "Group" : "User"));
		}
		if(this.owners.isEmpty()) {
			for(Owner o : owners) {
				this.owners.add("Name=" + o.getName() + ", Type=" + (o.isGroup() ? "Group" : "User"));
			}
		}
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o == null || !(o instanceof ResultContainer))
			return false;
		ResultContainer other = (ResultContainer)o;
		//If ep is the same then existingPerms is the same so not need to compare both
		//If seedPart is the same then matchingPart is the same
		//If missingPerms, ep, and fe are the same then owners will be the same
		return Objects.equals(ep, other.ep) && Objects.equals(fe, other.fe) && Objects.equals(missingPerms, other.missingPerms) && Objects.equals(seedPart, other.seedPart);
	}
	
	@Override
	public int hashCode() {
		int i = 17;
		i = i * 31 + Objects.hashCode(ep);
		i = i * 31 + Objects.hashCode(fe);
		i = i * 31 + Objects.hashCode(missingPerms);
		i = i * 31 + Objects.hashCode(seedPart);
		return i;
	}
	
	@Override
	public String toString() {
		return toString("");
	}
	
	public String toString(String spacer) {
		StringBuilder sb = new StringBuilder();
		sb.append(spacer).append("Result: ").append(fe.getFullPath()).append("\n");
		sb.append(spacer).append("  EntryPoint: ").append(ep.toString()).append("\n");
		sb.append(spacer).append("  Regex: ").append(matchingPart.toRegexString()).append("\n");
		sb.append(spacer).append("  Owners: ").append(owners.toString()).append("\n");
		sb.append(spacer).append("  Missing Permissions: \n");
		for(Doublet d : missingPerms) {
			sb.append(spacer).append("    ").append(d.toString()).append("\n");
		}
		sb.append(spacer).append("  Existing Permissions: \n");
		for(Doublet d : existingPerms) {
			sb.append(spacer).append("    ").append(d.toString()).append("\n");
		}
		sb.append(spacer).append("  SeedPart: ").append(seedPart.toString()).append("\n");
		sb.append(spacer).append("  MatchPart: ").append(matchingPart.toString()).append("\n");
		sb.append(spacer).append("  OriginalPart: ").append(originalPart.toString());
		return sb.toString();
	}
	
	@Override
	public int compareTo(ResultContainer o) {
		int ret = ep.compareTo(o.ep);
		if(ret == 0)
			ret = fe.compareTo(o.fe);
		if(ret == 0)
			ret = missingPerms.toString().compareTo(o.missingPerms.toString());
		if(ret == 0)
			ret = seedPart.compareTo(o.seedPart);
		return ret;
	}
	
	public EntryPointNode getEp() {
		return ep;
	}

	public FileEntry getFe() {
		return fe;
	}

	public ArrayList<String> getOwners() {
		return owners;
	}

	public Part getMatchingPart() {
		return matchingPart;
	}

	public Part getOriginalPart() {
		return originalPart;
	}

	public PHPart getSeedPart() {
		return seedPart;
	}

	public LinkedHashSet<Doublet> getMissingPerms() {
		return missingPerms;
	}

	public LinkedHashSet<Doublet> getExistingPerms() {
		return existingPerms;
	}
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}
	
	public ResultContainer readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this,filePath, path);
	}
	
	public static ResultContainer readXMLStatic(String filePath, Path path) throws Exception {
		return new ResultContainer().readXML(filePath, path);
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
				EntryPointNode.getXStreamSetupStatic().getOutputGraph(in);
				FileEntry.getXStreamSetupStatic().getOutputGraph(in);
				Part.XStreamSetup.getXStreamSetupStatic().getOutputGraph(in);
				PHPart.getXStreamSetupStatic().getOutputGraph(in);
				Doublet.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(ResultContainer.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsNoRef(xstream);
		}
		
	}
	
}
