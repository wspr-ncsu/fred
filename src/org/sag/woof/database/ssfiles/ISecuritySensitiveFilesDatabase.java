package org.sag.woof.database.ssfiles;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public interface ISecuritySensitiveFilesDatabase {

	String toString(String spacer);

	void sortData();

	Set<Owner> getOutputData();

	void add(Owner owner);

	void addAll(Set<Owner> owners);

	SecuritySensitiveFilesDatabase readJSON(Path in) throws Exception;

	Map<FileEntry, Set<Owner>> getFilesToOwners();

	Map<FileEntry, Set<Owner>> getFilesToGroups();
	
	Map<FileEntry, Set<Owner>> getFilesToGroupsOrSystem();

	Map<FileEntry, Set<Owner>> getFilesToUsers();

}
