package org.sag.fred.database.filemethods;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.sag.common.io.FileHash;
import org.sag.common.io.FileHashList;
import org.sag.common.tuple.Triple;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import soot.SootMethod;

@XStreamAlias("IFileMethodsDatabase")
public interface IFileMethodsDatabase extends XStreamInOutInterface {
	
	void clearSootResolvedData();
	void loadSootResolvedData();
	List<FileHash> getFileHashList();
	void setFileHashList(FileHashList fhl);
	String toString();
	String toString(String spacer);
	int hashCode();
	boolean equals(Object o);
	IFileMethodsDatabase readXML(String filePath, Path path) throws Exception;
	
	void sortData();
	Set<FileMethod> getOutputData();
	FileMethod add(SootMethod sm, boolean[] actions, String apiType);
	Set<FileMethod> addAll(Set<Triple<SootMethod, boolean[], String>> methods);
	Set<FileMethod> getNativeMethods();
	Set<FileMethod> getOpenMethods();
	Set<FileMethod> getRemoveMethods();
	Set<FileMethod> getAccessMethods();
	Set<FileMethod> getJavaAPIMethods();
	Set<FileMethod> getAndroidAPIMethods();
	Set<FileMethod> getAndroidSystemMethods();
	IFileMethodsDatabase readTXT(Path path) throws Exception;
	FileHashList readFileHashListInTXT(Path path) throws Exception;
	void writeTXT(Path path) throws Exception;
	void writePartsTXT(Path path, Set<FileMethod> parts, String name, FileHashList dep) throws Exception;
	
	public static final class Factory {
		
		public static IFileMethodsDatabase getNew(boolean isEmpty) {
			if(isEmpty)
				return new EmptyFileMethodsDatabase();
			return new FileMethodsDatabase(true);
		}
		
		public static IFileMethodsDatabase readXML(String filePath, Path path) throws Exception {
			return FileMethodsDatabase.readXMLStatic(filePath, path);
		}
		
		public static IFileMethodsDatabase readTXT(Path path) throws Exception {
			return FileMethodsDatabase.readTXTStatic(path);
		}
		
	}

}
