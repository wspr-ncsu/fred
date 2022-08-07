package org.sag.fred.database.androidapi;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.sag.common.io.FileHash;
import org.sag.common.io.FileHashList;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;
import org.sag.soot.xstream.SootMethodContainer;

import com.thoughtworks.xstream.annotations.XStreamAlias;

import soot.SootMethod;

@XStreamAlias("IAndroidAPIDatabase")
public interface IAndroidAPIDatabase extends XStreamInOutInterface {
	
	void clearSootResolvedData();
	void loadSootResolvedData();
	List<FileHash> getFileHashList();
	void setFileHashList(FileHashList fhl);
	String toString();
	String toString(String spacer);
	int hashCode();
	boolean equals(Object o);
	IAndroidAPIDatabase readXML(String filePath, Path path) throws Exception;
	
	void sortData();
	Set<SootMethodContainer> getOutputData();
	void add(SootMethod sm);
	void addAll(Set<SootMethod> methods);
	Set<SootMethod> getMethods();
	boolean contains(SootMethod sm);

	public static final class Factory {
		
		public static IAndroidAPIDatabase getNew(boolean isEmpty) {
			if(isEmpty)
				return new EmptyAndroidAPIDatabase();
			return new AndroidAPIDatabase(true);
		}
		
		public static IAndroidAPIDatabase readXML(String filePath, Path path) throws Exception {
			return AndroidAPIDatabase.readXMLStatic(filePath, path);
		}
		
	}
	
}
