package org.sag.woof.phases.woof;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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
import org.sag.woof.database.filepaths.parts.LeafPart;
import org.sag.woof.database.filepaths.parts.PHPart;
import org.sag.woof.database.filepaths.parts.Part;
import org.sag.woof.database.filepaths.parts.Part.Node;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("MatchesAllDatabase")
public class MatchesAllDatabase implements XStreamInOutInterface {
	
	@XStreamAlias("Data")
	LinkedHashMap<EntryPointNode, LinkedHashSet<IntermediateExpression>> data;
	
	public MatchesAllDatabase() {
		this.data = new LinkedHashMap<>();
	}
	
	public void addIntermediateExpression(EntryPointNode ep, PHPart seed, Part simpleMatchPath, Part originalMatchPath) {
		LinkedHashSet<IntermediateExpression> ies = data.get(ep);
		if(ies == null) {
			ies = new LinkedHashSet<>();
			data.put(ep, ies);
		}
		ies.add(new IntermediateExpression(seed, simpleMatchPath, originalMatchPath));
	}
	
	public void sort() {
		for(EntryPointNode ep : data.keySet()) {
			data.put(ep, SortingMethods.sortSet(data.get(ep)));
		}
		data = (LinkedHashMap<EntryPointNode, LinkedHashSet<IntermediateExpression>>)SortingMethods.sortMapKeyAscending(data);
	}
	
	public LinkedHashMap<EntryPointNode, LinkedHashSet<IntermediateExpression>> getData() {
		return data;
	}
	
	public LinkedHashSet<IntermediateExpression> getIntermediateExpressions() {
		Set<IntermediateExpression> ret = new HashSet<>();
		for(LinkedHashSet<IntermediateExpression> ies : data.values()) {
			ret.addAll(ies);
		}
		return SortingMethods.sortSet(ret);
	}
	
	public void dump(Path outDir) throws Exception {
		dumpData(FileHelpers.getPath(outDir,  "matches_all_ie_dump.txt"));
		dumpEpData(FileHelpers.getPath(outDir,  "matches_all_ie_ep_dump.txt"));
		dumpCounts(FileHelpers.getPath(outDir,  "matches_all_count.txt"));
		dumpcsv(FileHelpers.getPath(outDir, "matches_all.csv"));
		dumpLeafs(FileHelpers.getPath(outDir, "matches_all_leaf_parts.txt"));
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
	
	private void dumpcsv(Path p) throws Exception {
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(p))) {
			ps.println("Entry Point\tStub\tRegex\tExpression\tEntry Point Permissions");
			for(EntryPointNode ep : data.keySet()) {
				for(IntermediateExpression ie : data.get(ep)) {
					ps.print(containsSep(ep.getEntryPoint().toString()) + "\t");
					ps.print(containsSep(ep.getStub().toString()) + "\t");
					ps.print(containsSep(ie.getSimpleMatchPath().toRegexString()) + "\t");
					ps.print(containsSep(ie.getSimpleMatchPath().toString()) + "\t");
					ps.print(containsSep(printList(ep.getPermissions())));
					ps.println();
				}
			}
		}
	}
	
	private void dumpEpData(Path p) throws Exception {
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(p))) {
			for(EntryPointNode ep : data.keySet()) {
				ps.println("EntryPoint: " + ep.toString());
				for(IntermediateExpression ie : data.get(ep)) {
					ps.println(ie.toString("  "));
				}
			}
		}
	}
	
	private void dumpData(Path p) throws Exception {
		Set<IntermediateExpression> ies = getIntermediateExpressions();
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(p))) {
			for(IntermediateExpression ie : ies) {
				ps.println(ie.toString());
			}
		}
	}
	
	private void dumpLeafs(Path p) throws Exception {
		Set<IntermediateExpression> ies = getIntermediateExpressions();
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
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(p))) {
			for(Part l : leafss) {
				ps.println(l.toString());
			}
		}
	}
	
	private void dumpCounts(Path p) throws Exception {
		Set<IntermediateExpression> ies = getIntermediateExpressions();
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(p))) {
			ps.println("Unique IEs: " + ies.size());
			Map<String,Set<IntermediateExpression>> stubCounts = new LinkedHashMap<>();
			Map<String,Integer> epCounts = new LinkedHashMap<>();
			//int maxEpDigits = 0;
			//int maxStubDigits = 0;
			for(EntryPointNode ep : data.keySet()) {
				Set<IntermediateExpression> i = stubCounts.get(ep.getStub().getSignature());
				if(i == null) {
					i = new HashSet<>();
					stubCounts.put(ep.getStub().getSignature(), i);
				}
				int size = data.get(ep).size();
				i.addAll(data.get(ep));
				epCounts.put(ep.toString(), size);
				//maxEpDigits = digits(size) > maxEpDigits ? digits(size) : maxEpDigits;
			}
			/*for(Set<IntermediateExpression> i : stubCounts.values()) {
				maxStubDigits = digits(i.size()) > maxStubDigits ? digits(i.size()) : maxStubDigits;
			}*/
			stubCounts = SortingMethods.sortMapValue(stubCounts, new Comparator<Set<IntermediateExpression>>() {
				@Override
				public int compare(Set<IntermediateExpression> o1, Set<IntermediateExpression> o2) {
					return Integer.compare(o2.size(), o1.size());
				}
			});
			epCounts = SortingMethods.sortMapValue(epCounts, new Comparator<Integer>() {
				@Override
				public int compare(Integer o1, Integer o2) {
					return Integer.compare(o2, o1);
				}
			});
			ps.println("Stub Counts: ");
			for(String s : stubCounts.keySet()) {
				ps.println("  "  + s + " & " + stubCounts.get(s).size());
				//ps.println("  " + padNum(stubCounts.get(s).size(),maxStubDigits) + " : " + s);
			}
			ps.println("Entry Point Counts: ");
			for(String s : epCounts.keySet()) {
				ps.println("  "  + s + " & " + epCounts.get(s));
				//ps.println("  " + padNum(epCounts.get(s),maxEpDigits) + " : " + s);
			}
		}
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
	
	public MatchesAllDatabase readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this,filePath, path);
	}
	
	public static MatchesAllDatabase readXMLStatic(String filePath, Path path) throws Exception {
		return new MatchesAllDatabase().readXML(filePath, path);
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
				EntryPointNode.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(MatchesAllDatabase.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
	public static void main(String[] args) throws Exception {
		MatchesAllDatabase db = MatchesAllDatabase.readXMLStatic("C:\\CS\\Documents\\Work\\Research\\woof\\aosp-10.0.0\\woof\\woof\\matches_all_db.xml", null);
		Set<LeafPart> leafs = new HashSet<>();
		for(Set<IntermediateExpression> temp : db.getData().values()) {
			for(IntermediateExpression ie : temp) {
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
		}
		Set<LeafPart> leafss = SortingMethods.sortSet(leafs);
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath("C:\\CS\\Documents\\Work\\Research\\woof\\aosp-10.0.0\\debug\\2020-10-04_11-40-49\\woof", "matches_all_leaf_parts.txt")))) {
			for(Part p : leafss) {
				ps.println(p.toString());
			}
		}
	}

}
