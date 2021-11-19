package org.sag.woof.database.filepaths;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sag.acminer.phases.entrypoints.EntryPoint;
import org.sag.common.io.FileHash;
import org.sag.common.io.FileHashList;
import org.sag.common.tuple.Pair;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;
import org.sag.woof.database.filepaths.parts.PHPart;
import org.sag.woof.database.filepaths.parts.Part;

public interface IFilePathsDatabase extends XStreamInOutInterface {

	void clearSootResolvedData();

	void loadSootResolvedData();

	String toString();

	String toString(String spacer);

	int hashCode();

	boolean equals(Object o);

	void sortData();

	void add(EntryPoint ep, List<Pair<PHPart, Part>> paths);

	void addAll(Map<EntryPoint, List<Pair<PHPart, Part>>> data);

	Set<EntryPointContainer> getOutputData();

	Map<EntryPoint, List<Pair<PHPart, Part>>> getData();
	
	List<FileHash> getFileHashList();
	void setFileHashList(FileHashList fhl);
	
	public static final class Factory {
		
		public static IFilePathsDatabase getNew(boolean isEmpty) {
			if(isEmpty)
				return new EmptyFilePathsDatabase();
			return new FilePathsDatabase(true);
		}
		
		public static IFilePathsDatabase readXML(String filePath, Path path) throws Exception {
			return FilePathsDatabase.readXMLStatic(filePath, path);
		}
		
	}

}