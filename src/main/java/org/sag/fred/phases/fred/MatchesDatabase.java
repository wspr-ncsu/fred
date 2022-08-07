package org.sag.fred.phases.fred;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.sag.common.io.FileHelpers;
import org.sag.common.io.PrintStreamUnixEOL;
import org.sag.common.tools.SortingMethods;
import org.sag.common.tuple.Pair;
import org.sag.common.xstream.XStreamInOut;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;
import org.sag.fred.database.filepaths.parts.LeafPart;
import org.sag.fred.database.filepaths.parts.PHPart;
import org.sag.fred.database.filepaths.parts.Part;
import org.sag.fred.database.filepaths.parts.Part.Node;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("MatchesDatabase")
public class MatchesDatabase implements XStreamInOutInterface {
	
	@XStreamAlias("Data")
	private LinkedHashMap<EntryPointNode, LinkedHashSet<RegexContainer>> data;
	
	public MatchesDatabase() {
		this.data = new LinkedHashMap<>();
	}
	
	public MatchesDatabase clone() {
		MatchesDatabase ret = new MatchesDatabase();
		for(EntryPointNode ep : data.keySet()) {
			LinkedHashSet<RegexContainer> temp = new LinkedHashSet<>();
			for(RegexContainer r : data.get(ep)) {
				temp.add(r.clone());
			}
			ret.data.put(ep, temp);
		}
		return ret;
	}
	
	public void add(EntryPointNode ep, RegexContainer regex) {
		LinkedHashSet<RegexContainer> regexes = data.get(ep);
		if(regexes == null) {
			regexes = new LinkedHashSet<>();
			data.put(ep, regexes);
		}
		regexes.add(regex);
	}
	
	public void addAll(EntryPointNode ep, LinkedHashSet<RegexContainer> regexes) {
		data.put(ep, regexes);
	}
	
	public void addIntermediateExpression(EntryPointNode ep, PHPart seed, Part simpleMatchPath, Part originalMatchPath) {
		LinkedHashSet<RegexContainer> regexes = data.get(ep);
		if(regexes == null) {
			regexes = new LinkedHashSet<>();
			data.put(ep, regexes);
		}
		String regex = simpleMatchPath.toRegexString();
		boolean found = false;
		for(RegexContainer r : regexes) {
			if(r.getRegex().equals(regex)) {
				found = true;
				r.addIE(new IntermediateExpression(seed, simpleMatchPath, originalMatchPath));
			}
		}
		if(!found) {
			RegexContainer r = new RegexContainer(regex);
			regexes.add(r);
			r.addIE(new IntermediateExpression(seed, simpleMatchPath, originalMatchPath));
		}
	}
	
	public void sort() {
		for(EntryPointNode ep : data.keySet()) {
			for(RegexContainer r: data.get(ep))
				r.sort();
			data.put(ep, SortingMethods.sortSet(data.get(ep)));
		}
		data = (LinkedHashMap<EntryPointNode, LinkedHashSet<RegexContainer>>)SortingMethods.sortMapKeyAscending(data);
	}
	
	public LinkedHashMap<EntryPointNode, LinkedHashSet<RegexContainer>> getData() {
		return data;
	}
	
	public LinkedHashSet<RegexContainer> getRegexContainers() {
		Map<RegexContainer,RegexContainer> ret = new HashMap<>();
		for(LinkedHashSet<RegexContainer> ies : data.values()) {
			for(RegexContainer re : ies) {
				RegexContainer cur = ret.get(re);
				if(cur == null) {
					cur = re.clone();
					ret.put(cur, cur);
				} else {
					for(IntermediateExpression ie : re.getIntermediateExpressions()) {
						cur.addIE(ie.clone());
					}
					for(FileContainer fe : re.getFiles()) {
						cur.addFile(fe.getFileEntry(), fe.getPermissions(), fe.getMissingPermissions());
					}
				}
			}
		}
		return SortingMethods.sortSet(new HashSet<>(ret.values()));
	}
	
	public LinkedHashSet<IntermediateExpression> getIntermediateExpressions() {
		Set<IntermediateExpression> ret = new HashSet<>();
		for(LinkedHashSet<RegexContainer> ies : data.values()) {
			for(RegexContainer r : ies) {
				ret.addAll(r.getIntermediateExpressions());
			}
		}
		return SortingMethods.sortSet(ret);
	}
	
	public LinkedHashSet<FileContainer> getFiles() {
		Set<FileContainer> ret = new HashSet<>();
		for(LinkedHashSet<RegexContainer> ies : data.values()) {
			for(RegexContainer r : ies) {
				ret.addAll(r.getFiles());
			}
		}
		return SortingMethods.sortSet(ret);
	}
	
	public Map<EntryPointNode,Set<IntermediateExpression>> getEpToIes() {
		Map<EntryPointNode, Set<IntermediateExpression>> ret = new LinkedHashMap<>();
		for(EntryPointNode ep : data.keySet()) {
			Set<IntermediateExpression> temp = new HashSet<>();
			for(RegexContainer r : data.get(ep)) {
				temp.addAll(r.getIntermediateExpressions());
			}
			ret.put(ep, SortingMethods.sortSet(temp));
		}
		return ret;
	}
	
	public Map<EntryPointNode,Set<FileContainer>> getEpToFiles() {
		Map<EntryPointNode, Set<FileContainer>> ret = new LinkedHashMap<>();
		for(EntryPointNode ep : data.keySet()) {
			Set<FileContainer> temp = new HashSet<>();
			for(RegexContainer r : data.get(ep)) {
				temp.addAll(r.getFiles());
			}
			ret.put(ep, SortingMethods.sortSet(temp));
		}
		return ret;
	}
	
	public void dump(Path outDir, String dbName) throws Exception {
		Set<RegexContainer> regexes = getRegexContainers();
		Set<IntermediateExpression> ies = getIntermediateExpressions();
		Set<FileContainer> files = getFiles();
		Map<EntryPointNode,Set<IntermediateExpression>> epToIes = getEpToIes();
		Map<EntryPointNode,Set<FileContainer>> epToFiles = getEpToFiles();
		dumpCounts(regexes, ies, files, epToIes, epToFiles, outDir, dbName);
		dumpDataUniq(regexes,outDir,dbName,"regex");
		dumpDataUniq(ies,outDir,dbName,"ie");
		dumpDataByEp(data, outDir, dbName,"regex");
		dumpDataByEp(epToIes, outDir, dbName,"ie");
		dumpLeafs(ies, outDir, dbName);
		dumpcsv(data, outDir, dbName);
		dumptsv(data, outDir, dbName);
		dumpDataByEp(epToFiles, outDir, dbName,"file");
		dumpDataUniq(files,outDir,dbName,"file");
	}
	
	private String containsSep(String in) {
		if(in == null || in.indexOf('\t') < 0)
			return Objects.toString(in);
		throw new RuntimeException("Attempted to ouput '" + in + "' to a csv but it contains the sep char '\\t' already.");
	}
	
	private String printList(Set<String> in) {
		if(in == null || in.isEmpty())
			return "";
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for(String s : in) {
			if(first)
				first = false;
			else
				sb.append(", ");
			sb.append(s);
		}
		return sb.toString();
	}
	
	private void dumpLeafs(Set<IntermediateExpression> ies, Path outDir, String dbName) throws Exception {
		Set<LeafPart> leafs = new HashSet<>();
		for(IntermediateExpression ie : ies) {
			Part root = ie.getSimpleMatchPath();
			root.getIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
				@Override
				public void accept(Pair<Part,Node> t) {
					Part child = t.getSecond().getPart();
					if(child instanceof LeafPart)
						leafs.add((LeafPart)child);
				}
			});
		}
		Set<LeafPart> leafss = SortingMethods.sortSet(leafs);
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath(outDir, dbName + "_leaf_parts.txt")))) {
			for(Part l : leafss) {
				ps.println(l.toString());
			}
		}
	}
	
	private void dumpcsv(Map<EntryPointNode, ? extends Set<RegexContainer>> data, Path outDir, String dbName) throws Exception {
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath(outDir, dbName + ".csv")))) {
			ps.println("Entry Point\tStub\tRegex\tFile\tGID\tEntry Point Permissions\tGID Permissions\tMissing Permissions\tSeeds\tSimple Expressions\tExpressions");
			for(EntryPointNode ep : data.keySet()) {
				for(RegexContainer re : data.get(ep)) {
					for(IntermediateExpression ie : re.getIntermediateExpressions()) {
						String seed = ie.getSeed().toString();
						String org = ie.getOriginalMatchPath().toString();
						String simple = ie.getSimpleMatchPath().toString();
						for(FileContainer file : re.getFiles()) {
							ps.print(containsSep(ep.getEntryPoint().toString()) + "\t");
							ps.print(containsSep(ep.getStub().toString()) + "\t");
							ps.print(containsSep(re.getRegex()) + "\t");
							ps.print(containsSep(file.toString()) + "\t");
							ps.print(containsSep(file.getFileEntry().getGroup()) + "\t");
							ps.print(containsSep(printList(ep.getPermissions())) + "\t");
							ps.print(containsSep(printList(file.getPermissions())) + "\t");
							ps.print(containsSep(printList(file.getMissingPermissions())) + "\t");
							ps.print(containsSep(seed) + "\t");
							ps.print(containsSep(simple) + "\t");
							ps.print(containsSep(org));
							ps.println();
						}
						if(re.getFiles().isEmpty()) {
							ps.print(containsSep(ep.getEntryPoint().toString()) + "\t");
							ps.print(containsSep(ep.getStub().toString()) + "\t");
							ps.print(containsSep(re.getRegex()) + "\t");
							ps.print("\t");
							ps.print("\t");
							ps.print(containsSep(printList(ep.getPermissions())) + "\t");
							ps.print("\t");
							ps.print("\t");
							ps.print(containsSep(seed) + "\t");
							ps.print(containsSep(simple) + "\t");
							ps.print(containsSep(org));
							ps.println();
						}
					}
				}
			}
		}
	}
	
	private String containsSepTSV(String in) {
		if(in == null || in.indexOf('\t') < 0) {
			if(in == null || in.indexOf('"') < 0)
				return Objects.toString(in);
			throw new RuntimeException("Attempted to ouput '" + in + "' to a tsv but it contains the sep char '\\t' already.");
		}
		throw new RuntimeException("Attempted to ouput '" + in + "' to a tsv but it contains the sep char '\\t' already.");
	}
	
	private String printListTSV(Set<String> in) {
		if(in == null || in.isEmpty())
			return "";
		StringBuilder sb = new StringBuilder();
		sb.append("\"");
		boolean first = true;
		for(String s : in) {
			containsSepTSV(s);
			if(first)
				first = false;
			else
				sb.append("\n");
			sb.append(s);
		}
		sb.append("\"");
		return sb.toString();
	}
	
	private void dumptsv(Map<EntryPointNode, ? extends Set<RegexContainer>> data, Path outDir, String dbName) throws Exception {
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath(outDir, dbName + ".tsv")))) {
			ps.print("Entry Point\tStub\tRegex\tFile\tGID\tEntry Point Permissions\tGID Permissions\tMissing Permissions\tSeeds\tSimple Expressions\tExpressions\r");
			for(EntryPointNode ep : data.keySet()) {
				for(RegexContainer re : data.get(ep)) {
					StringBuilder seeds = new StringBuilder();
					StringBuilder orgs = new StringBuilder();
					StringBuilder simples = new StringBuilder();
					seeds.append("\"");
					orgs.append("\"");
					simples.append("\"");
					boolean first = true;
					for(IntermediateExpression ie : re.getIntermediateExpressions()) {
						String org = ie.getOriginalMatchPath().toString().replace('"', '\'');
						String simple = ie.getSimpleMatchPath().toString().replace('"', '\'');
						String seed = ie.getSeed().toString().replace('"', '\'');
						containsSepTSV(org);
						containsSepTSV(simple);
						containsSepTSV(seed);
						if(first) {
							first = false;
						} else {
							seeds.append("\n");
							orgs.append("\n");
							simples.append("\n");
						}
						seeds.append(seed);
						orgs.append(org);
						simples.append(simple);
					}
					seeds.append("\"");
					orgs.append("\"");
					simples.append("\"");
					String seed = seeds.toString();
					String simple = simples.toString();
					String org = orgs.toString();
					for(FileContainer file : re.getFiles()) {
						ps.print(containsSepTSV(ep.getEntryPoint().toString()) + "\t");
						ps.print(containsSepTSV(ep.getStub().toString()) + "\t");
						ps.print(containsSepTSV(re.getRegex().replace('"', '\'')) + "\t");
						ps.print(containsSepTSV(file.toString()) + "\t");
						ps.print(containsSepTSV(file.getFileEntry().getGroup()) + "\t");
						ps.print(printListTSV(ep.getPermissions()) + "\t");
						ps.print(printListTSV(file.getPermissions()) + "\t");
						ps.print(printListTSV(file.getMissingPermissions()) + "\t");
						ps.print(seed + "\t");
						ps.print(simple + "\t");
						ps.print(org);
						ps.print("\r");
					}
					if(re.getFiles().isEmpty()) {
						ps.print(containsSepTSV(ep.getEntryPoint().toString()) + "\t");
						ps.print(containsSepTSV(ep.getStub().toString()) + "\t");
						ps.print(containsSepTSV(re.getRegex().replace('"', '\'')) + "\t");
						ps.print("\t");
						ps.print("\t");
						ps.print(printListTSV(ep.getPermissions()) + "\t");
						ps.print("\t");
						ps.print("\t");
						ps.print(seed + "\t");
						ps.print(simple + "\t");
						ps.print(org);
						ps.print("\r");
					}
				}
			}
		}
	}
	
	private void dumpDataByEp(Map<EntryPointNode,? extends Set<? extends Object>> data, Path outDir, String dbName, String type) throws Exception {
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath(outDir, dbName + "_" + type.toLowerCase() + "_ep_dump.txt")))) {
			for(EntryPointNode ep : data.keySet()) {
				ps.println("EntryPoint: " + ep.toString());
				ps.println("  EntryPoint Permissions: " + ep.getPermissions());
				for(Object ie : data.get(ep)) {
					if(type.equals("regex"))
						ps.print(((RegexContainer)ie).toString("  "));
					else if(type.equals("ie"))
						ps.println(((IntermediateExpression)ie).toString("  "));
					else if(type.equals("file"))
						ps.println(((FileContainer)ie).toString());
					else
						ps.println(ie.toString());
				}
			}
		}
	}
	
	private void dumpDataUniq(Set<? extends Object> data, Path outDir, String dbName, String type) throws Exception {
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath(outDir, dbName + "_" + type.toLowerCase() + "_dump.txt")))) {
			for(Object ie : data) {
				ps.println(ie.toString());
			}
		}
	}
	
	private void dumpCounts(Set<RegexContainer> regexes, Set<IntermediateExpression> ies, Set<FileContainer> files, 
			Map<EntryPointNode,Set<IntermediateExpression>> epToIes, Map<EntryPointNode,Set<FileContainer>> epToFiles, 
			Path outDir, String name) throws Exception {
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath(outDir, name + "_count.txt")))) {
			ps.println("Unique Regexes: " + regexes.size());
			ps.println("Unique IEs: " + ies.size());
			ps.println("Unique Files: " + files.size());
		}
		
		dumpCounts(data, "Regex", outDir, name);
		dumpCounts(epToIes, "IE", outDir, name);
		dumpCounts(epToFiles, "File", outDir, name);
	}
	
	private void dumpCounts(Map<EntryPointNode, ? extends Set<? extends Object>> in, String type, Path outDir, String name) throws Exception {
		Map<String,Set<Object>> stubCounts = new LinkedHashMap<>();
		Map<String,Integer> epCounts = new LinkedHashMap<>();
		//int maxEpDigits = 0;
		//int maxStubDigits = 0;
		for(EntryPointNode ep : in.keySet()) {
			Set<Object> i = stubCounts.get(ep.getStub().getSignature());
			if(i == null) {
				i = new HashSet<>();
				stubCounts.put(ep.getStub().getSignature(), i);
			}
			int size = in.get(ep).size();
			i.addAll(in.get(ep));
			epCounts.put(ep.toString(), size);
			//maxEpDigits = digits(size) > maxEpDigits ? digits(size) : maxEpDigits;
		}
		/*for(Set<Object> i : stubCounts.values()) {
			maxStubDigits = digits(i.size()) > maxStubDigits ? digits(i.size()) : maxStubDigits;
		}*/
		stubCounts = SortingMethods.sortMapValue(stubCounts, new Comparator<Set<Object>>() {
			@Override
			public int compare(Set<Object> o1, Set<Object> o2) {
				return Integer.compare(o2.size(), o1.size());
			}
		});
		epCounts = SortingMethods.sortMapValue(epCounts, new Comparator<Integer>() {
			@Override
			public int compare(Integer o1, Integer o2) {
				return Integer.compare(o2, o1);
			}
		});
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath(outDir, name + "_" + type.toLowerCase() + "_stub_count.txt")))) {
			ps.println(type + " Stub Counts: ");
			for(String s : stubCounts.keySet()) {
				ps.println("  "  + s + " & " + stubCounts.get(s).size());
				//ps.println("  " + padNum(stubCounts.get(s).size(),maxStubDigits) + " & " + s);
			}
		}
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath(outDir, name + "_" + type.toLowerCase() + "_ep_count.txt")))) {
			ps.println(type + " Entry Point Counts: ");
			for(String s : epCounts.keySet()) {
				ps.println("  "  + s + " & " + epCounts.get(s));
				//ps.println("  " + padNum(epCounts.get(s),maxEpDigits) + " & " + s);
			}
		}
	}
	
	private static MatchesDatabase diffDatabases(MatchesDatabase originalDB, MatchesDatabase newDB) {
		Objects.requireNonNull(originalDB);
		Objects.requireNonNull(newDB);
		MatchesDatabase newDBCopy = newDB.clone();
		MatchesDatabase ret = new MatchesDatabase();
		for(EntryPointNode ep : newDBCopy.data.keySet()) {
			Set<RegexContainer> originalRegexes = originalDB.data.get(ep);
			LinkedHashSet<RegexContainer> newRegexes = newDBCopy.data.get(ep);
			if(originalRegexes != null && !originalRegexes.isEmpty()) {
				for(RegexContainer re : newRegexes) {
					if(originalRegexes.contains(re)) {
						RegexContainer originalRE = null;
						for(RegexContainer r : originalRegexes) {
							if(re.equals(r)) {
								originalRE = r;
								break;
							}
						}
						Set<FileContainer> originalFiles = originalRE.getFiles();
						for(Iterator<FileContainer> it = re.getFiles().iterator(); it.hasNext();) {
							FileContainer f = it.next();
							if(originalFiles.contains(f))
								it.remove();
						}
						if(!re.getFiles().isEmpty())
							ret.add(ep, re);
					} else {
						ret.add(ep, re);
					}
				}
			} else {
				ret.addAll(ep, newRegexes);
			}
		}
		ret.sort();
		return ret;
	}
	
	/*private final int digits(int n) {
		int len = String.valueOf(n).length();
		if(n < 0)
			return len - 1;
		else
			return len;
	}
	
	private final String padNum(int n, int digits) {
		return String.format("%"+digits+"d", n);
	}*/
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}
	
	public MatchesDatabase readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this,filePath, path);
	}
	
	public static MatchesDatabase readXMLStatic(String filePath, Path path) throws Exception {
		return new MatchesDatabase().readXML(filePath, path);
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
				RegexContainer.getXStreamSetupStatic().getOutputGraph(in);
				EntryPointNode.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(MatchesDatabase.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
	public static void main(String[] args) throws Exception {
		MatchesDatabase db = MatchesDatabase.readXMLStatic("C:\\CS\\Documents\\Work\\Research\\fred\\aosp-10.0.0\\fred\\fred\\third_party_redelegation_db.xml", null);
		MatchesDatabase systemDB = MatchesDatabase.readXMLStatic("C:\\CS\\Documents\\Work\\Research\\fred\\aosp-10.0.0\\fred\\fred\\system_redelegation_db.xml", null);
		MatchesDatabase newDB = MatchesDatabase.readXMLStatic("C:\\CS\\Documents\\Work\\Research\\fred\\samsung-s20-10.0.0\\fred\\fred\\third_party_redelegation_db.xml", null);
		MatchesDatabase newSystemDB = MatchesDatabase.readXMLStatic("C:\\CS\\Documents\\Work\\Research\\fred\\samsung-s20-10.0.0\\fred\\fred\\system_redelegation_db.xml", null);
		MatchesDatabase diffDB = MatchesDatabase.diffDatabases(db, newDB);
		MatchesDatabase diffSystemDB = MatchesDatabase.diffDatabases(systemDB, newSystemDB);
		diffDB.dump(FileHelpers.getPath("C:\\CS\\Documents\\Work\\Research\\fred\\samsung-s20-10.0.0\\debug\\2021-06-08_18-41-26\\fred"), "diff_third_party_redelegation");
		diffSystemDB.dump(FileHelpers.getPath("C:\\CS\\Documents\\Work\\Research\\fred\\samsung-s20-10.0.0\\debug\\2021-06-08_18-41-26\\fred"), "diff_system_redelegation");
	}

}
