import os;
import argparse;
import errno;
import simplejson;
import jsonpickle;
import re;
from datetime import datetime;
import posixpath;
import pickle;
import xml.etree.ElementTree;
import copy;

class FileTree:
	
	def __init__(self, input):
		self.entries = [];
		self.parseFile(input);
	
	def jsonPickle(self, output):
		output = testFileWrite(output);
		jsonString = jsonpickle.encode(self, unpicklable=False);
		with open(output,'w') as fp:
			fp.write(jsonString);
			
	def pickle(self, output):
		output = testFileWrite(output);
		with open(output,'wb') as fp:
			pickle.dump(self, fp, protocol=4)
	
	@staticmethod
	def unpickle(input):
		input = testFileRead(input);
		with open(input,'rb') as fp:
			return pickle.load(fp);
			
	def parseFile(self, input):
		noAccessPat = re.compile("^ls:\s+(.+):\s+Permission\s+denied$", re.IGNORECASE);
		longFormatPat = re.compile("^([^\s]+)\s+(\d+)\s+([^\s)]+)\s+([^\s]+)\s+([^\s]+)\s+(\d+|\d+,\s+\d+)\s+([\d-]+)\s+([\d:]+)\s+(.+)$");
		doesNotExistPat = re.compile("^ls:\s+(.+):\s+No\s+such\s+file\s+or\s+directory$", re.IGNORECASE);
		perms = {'r' : 4, 'w' : 2, 'x' : 1, '-' : 0, 'S' : 0, 's' : 1, 't' : 1, 'T' : 0};
		with open(input, 'r') as fp:
			curDir = None;
			for line in fp:
				line = line.strip();
				match = noAccessPat.search(line);
				doesNotExistMatch = doesNotExistPat.search(line);
				if(not line or line.startswith('#') or line.startswith('total') or doesNotExistMatch != None): #line is empty, begins with a commment char, or starts with total \d
					continue;
				elif(line.startswith('.')): #line is the directory of following files
					s = list(line);
					if(s[1] == ':'): #the directory is .: which means /
						line = '/';
					else:
						del s[-1]; #Remove the : from the end of the string
						del s[0]; #Remove the . from the start of the string
						line = ''.join(s);
					curDir = line;
				elif(match != None):
					line = match.group(1);
					#Only those that start with ./ represent files, links, sockets, etc. that were not already listed. Those not
					#starting with ./ are directories that were listed before in their parent's list but cannot be traversed which
					#generates the permission denied error when it tried too.
					if(line.startswith('.')): 
						#Completly inaccessable create a new entry with just the name, dir, and inaccessable set
						s = list(line);
						del s[0];
						line = ''.join(s);
						dir = posixpath.dirname(line);
						name = posixpath.basename(line);
						self.entries.append(FileEntry(name,dir,FileType.UNKNOWN,True));
					else:
						#Directory listed but inaccessable, note that it is inaccessable
						#Find the last instance of a directory with no child entries
						#ls does not travers symbolic links so only need to look at directories
						setNoAccess = False;
						for entry in reversed(self.entries):
							if(entry.name == line and entry.type == FileType.DIR): #Found a directory with the same name as the one we are looking for
								path = entry.getFullPath();
								found = False;
								for e2 in reversed(self.entries): #See if there are any entries that are children of this directory (if so that means we could traverse it)
									if(e2.directoryPath == path):
										found = True;
										break;
								if(not found): #No children for this directory and it was the last found with the name we are looking for -> this is the one that could not be accessed
									entry.isNoAccess = True;
									setNoAccess = True;
									break;
						if(not setNoAccess):
							raise Exception('Unable find a directory named \'' + line + '\' no child entries (i.e. indicating it could not be accessed).');
				else:
					match = longFormatPat.search(line);
					if(match == None):
						raise Exception('Unable to parse line \'' + line + '\'.');
					unixPerms = match.group(1).strip();
					hardLinks = match.group(2).strip();
					uid = match.group(3).strip();
					gid = match.group(4).strip();
					selinuxPerms = match.group(5).strip();
					size = match.group(6).strip();
					modDate = match.group(7).strip();
					modTime = match.group(8).strip();
					fileName = match.group(9).strip();

					#The unix permissions should always be 10 characters long with the first being the file type. Possible file types are shown
					#in the FileType class. There may be additional file types depending on the system. We will add file types as needed and if 
					#any are devices make sure to append the conditional for handeling size of devices.
					#http://man.openbsd.org/cgi-bin/man.cgi/OpenBSD-current/man1/ls.1?query=ls%26sec=1
					#https://www.cyberciti.biz/faq/explain-the-nine-permissions-bits-on-files/
					if(len(unixPerms) != 10):
						raise Exception('Not a properly formated unixPerms String \'' + line + '\'.');

					fileType = FileType.shortToLong(unixPerms[0]);

					#Standard unix permission are represented by the characters 'r','w','x','-'. In addition to these there are the setuid and 
					#setgid permissions which replace the executable bit in the output for the user and group permissions respictively. A 
					#value of 'S' means setuid/setgid without the executable bit set (i.e. '-') and a value of 's' means the executable bit is
					#set in addition to setuid/setgid. Similarly, the sticky permission replaces the executable bit in the output for the gloabl
					#permissions. A value of 'T' means sticky without the executable bit set (i.e. '-') and a value of 't' means the executable
					#bit is set in addition to the sticky.
					#https://en.wikipedia.org/wiki/File_system_permissions
					if(fileType == FileType.UNKNOWN):
						raise Exception('Unknown file type of \'' + line + '\'.');
					if(re.search("^[rwxSsTt-]+$",unixPerms[1:]) == None):
						raise Exception('Unknown unix permissions \'' + line + '\'.');
					
					userPerms = perms.get(unixPerms[1]) + perms.get(unixPerms[2]) + perms.get(unixPerms[3]);
					groupPerms = perms.get(unixPerms[4]) + perms.get(unixPerms[5]) + perms.get(unixPerms[6]);
					globalPerms = perms.get(unixPerms[7]) + perms.get(unixPerms[8]) + perms.get(unixPerms[9]);
					setuid = unixPerms[3] == 'S' or unixPerms[3] == 's';
					setgid = unixPerms[6] == 'S' or unixPerms[6] == 's';
					sticky = unixPerms[9] == 'T' or unixPerms[9] == 't';

					#Entries for selinux are always made up of 4 values seperated by a : and an optional catagories value. For more info on these see:
					#https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/6/html/security-enhanced_linux/sect-security-enhanced_linux-working_with_selinux-selinux_contexts_labeling_files
					#https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/6/html/security-enhanced_linux/chap-security-enhanced_linux-selinux_contexts
					temp = selinuxPerms.split(':');
					if(len(temp) != 4 and len(temp) != 5):
						raise Exception('Unknown selinux format \'' + line + '\'.');

					selinuxUser = temp[0].strip();
					selinuxRole = temp[1].strip();
					selinuxType = temp[2].strip();
					selinuxLevel = temp[3].strip();
					selinuxCatagories = None;
					if(len(temp) == 5):
						selinuxCatagories = temp[4].split(',');
					selinuxContext = SELinuxContext(selinuxUser, selinuxRole, selinuxType, selinuxLevel, selinuxCatagories);

					#Devices list a major and minor device id instead of size so we replace them here.
					#http://man.openbsd.org/cgi-bin/man.cgi/OpenBSD-current/man1/ls.1?query=ls%26sec=1
					majorDeviceId = None;
					minorDeviceId = None;
					if(fileType == FileType.BFILE or fileType == FileType.CFILE):
						temp = re.split(",\s+", size);
						majorDeviceId = temp[0].strip();
						minorDeviceId = temp[1].strip();
						size = None;

					#Make sure it is a value time value and convert it to a standard format
					modDateTime = datetime.strptime(modDate + " " + modTime, '%Y-%m-%d %H:%M').strftime('[%Y-%m-%d %H:%M]');

					#Links have the format 'file_name -> link_path'. The link_path can be relative to the current directory or a full path.
					#Here we parse out the file name of the link and then generate the full path of the file the link points to based on the
					# current directory.
					linkPath = None;
					if(fileType == FileType.LINK):
						temp = re.split("\s+->\s+",fileName);
						fileName = temp[0].strip();
						linkPath = temp[1].strip();
						if(linkPath[0] != '/'):
							linkPath = posixpath.abspath(curDir + '/' + linkPath);
					
					self.entries.append(FileEntry(fileName, curDir, fileType, False, linkPath, uid, gid, userPerms, groupPerms, globalPerms, setuid, 
							  setgid, sticky, selinuxContext, hardLinks, size, majorDeviceId, minorDeviceId, modDateTime));
		
		#There is no entry for the root directory except . and .. which get removed in the next step
		#Modify the . entry so it is not removed and correctly points to the root directory
		#This way we can resolve links to the root directory and know the permissions of the root directory
		for entry in self.entries:
			if(entry.directoryPath == '/' and entry.name == '.'):
				entry.directoryPath = '';
				entry.name = '/';
				break;

		#Remove .. and . entries once all entries have been loaded because we do not need them anymore
		self.entries[:] = [entry for entry in self.entries if not (entry.name == '.' or entry.name == '..')];
		
		#Locate entries for links once all files entries have been parsed and point the links to the entries
		links = [];
		tempmap = {};
		for entry in self.entries:
			if(entry.type == FileType.LINK):
				links.append(entry);
			tempmap[entry.getFullPath()] = entry;
		for entry in links:
			entry.linkEntry = tempmap.get(entry.linkPath,None);
	
	@staticmethod
	def sortJsonFields(kv):
		return {
			'name' : 0,
			'directoryPath' : 1,
			'isNoAccess' : 2,
			'type' : 3,
			'linkPath' : 4,
			'linkEntry' : 5,
			'user' : 6,
			'group' : 7,
			'userPermissions' : 8,
			'groupPermissions' : 9,
			'globalPermissions' : 10,
			'isSetUID' : 11,
			'isSetGID' : 12,
			'isSticky' : 13,
			'selinuxContext' : 14,
			'hardLinks' : 15,
			'size' : 16,
			'majorDeviceId' : 17,
			'minorDeviceId' : 18,
			'modDateTime' : 19,
			'seUser' : 20,
			'seRole' : 21,
			'seType' : 22,
			'seLevel' : 23,
			'seCatagories' : 24
		}.get(kv[0],25);

class FileEntry:
	
	def __init__(self, name, directoryPath, type, isNoAccess=False, linkPath=None, user=None, group=None, userPermissions=None, groupPermissions=None, globalPermissions=None, 
				 isSetUID=None, isSetGID=None, isSticky=None, selinuxContext=None, hardLinks=None, size=None, majorDeviceId=None, minorDeviceId=None, modDateTime=None):
		self.name = name;
		self.directoryPath = directoryPath;
		self.isNoAccess = isNoAccess;
		self.type = type;
		self.linkPath = linkPath;
		self.linkEntry = None;
		self.user = user;
		self.group = group;
		self.userPermissions = userPermissions;
		self.groupPermissions = groupPermissions;
		self.globalPermissions = globalPermissions;
		self.isSetUID = isSetUID;
		self.isSetGID = isSetGID;
		self.isSticky = isSticky;
		self.selinuxContext = selinuxContext;
		self.hardLinks = hardLinks;
		self.size = size;
		self.majorDeviceId = majorDeviceId;
		self.minorDeviceId = minorDeviceId;
		self.modDateTime = modDateTime;
		
	def __eq__(self, other):
		if(not isinstance(other, FileEntry)):
			return NotImplemented;
		return self.name == other.name and self.directoryPath == other.directoryPath;
		
	def __hash__(self):
		return hash((self.name, self.directoryPath));	
	
	def getFullPath(self):
		if(len(self.directoryPath) == 0 or self.directoryPath.endswith('/')):
			return self.directoryPath + self.name;
		return self.directoryPath + '/' + self.name;
		
	def __getstate__(self): 
		return dict((k,v) for k,v in self.__dict__.items() if(not (v == None or (isinstance(v, bool) and v == False))));
		
	def __setstate__(self, inDict):
		#Add in values not listed which means they were None
		inDict['name'] = inDict.get('name',None);
		inDict['directoryPath'] = inDict.get('directoryPath',None);
		inDict['isNoAccess'] = inDict.get('isNoAccess',False);
		inDict['type'] = inDict.get('type',FileType.UNKNOWN);
		inDict['linkPath'] = inDict.get('linkPath',None);
		inDict['linkEntry'] = inDict.get('linkEntry',None);
		inDict['user'] = inDict.get('user',None);
		inDict['group'] = inDict.get('group',None);
		inDict['userPermissions'] = inDict.get('userPermissions',None);
		inDict['groupPermissions'] = inDict.get('groupPermissions',None);
		inDict['globalPermissions'] = inDict.get('globalPermissions',None);
		inDict['isSetUID'] = inDict.get('isSetUID',False);
		inDict['isSetGID'] = inDict.get('isSetGID',False);
		inDict['isSticky'] = inDict.get('isSticky',False);
		inDict['selinuxContext'] = inDict.get('selinuxContext',None);
		inDict['hardLinks'] = inDict.get('hardLinks',None);
		inDict['size'] = inDict.get('size',None);
		inDict['majorDeviceId'] = inDict.get('majorDeviceId',None);
		inDict['minorDeviceId'] = inDict.get('minorDeviceId',None);
		inDict['modDateTime'] = inDict.get('modDateTime',None);
		self.__dict__ = inDict;
				
class SELinuxContext:
	
	def __init__(self, user, role, type, level, catagories):
		self.seUser = user;
		self.seRole = role;
		self.seType = type;
		self.seLevel = level;
		self.seCatagories = catagories;
		
	def __eq__(self, other):
		if(not isinstance(other, SELinuxContext)):
			return NotImplemented;
		return self.user == other.user and self.role == other.role and self.type == other.type and self.level == other.level and self.catagories == other.catagories;
		
	def __hash__(self):
		return hash((self.user, self.role, self.type, self.level, str(self.catagories)));
	
	def toString(self):
		ret = self.seUser + ':' + self.seRole + ':' + self.seType + ':' + self.seLevel;
		if(self.seCatagories != None):
			temp = '';
			first = True;
			for c in self.seCatagories:
				if(first):
					first = False;
				else:
					temp += ','
				temp += c;
			ret += ':' + temp;
		return ret;
		
	def __getstate__(self):
		return dict((k,v) for k,v in self.__dict__.items() if(not (v == None or (isinstance(v, bool) and v == False))));
	
	def __setstate__(self, inDict):
		#Add in values not listed which means they were None
		inDict['seUser'] = inDict.get('seUser',None);
		inDict['seRole'] = inDict.get('seRole',None);
		inDict['seType'] = inDict.get('seType',None);
		inDict['seLevel'] = inDict.get('seLevel',None);
		inDict['seCatagories'] = inDict.get('seCatagories',None);
		self.__dict__ = inDict;

class FileType:
	UNKNOWN = 'unknown';
	FILE = 'regular file';
	DIR = 'directory';
	BFILE = 'block device file';
	CFILE = 'character device file';
	LINK = 'symbolic link';
	SOCKET = 'socket file';
	PIPE = 'named pipe';
	
	@staticmethod
	def shortToLong(c):
		return {
			'-' : FileType.FILE,
			'd' : FileType.DIR,
			'b' : FileType.BFILE,
			'c' : FileType.CFILE,
			'l' : FileType.LINK,
			's' : FileType.SOCKET,
			'p' : FileType.PIPE
		}.get(c,FileType.UNKNOWN);
	
	def longtoShort(c):
		return {
			FileType.FILE : '-',
			FileType.DIR : 'd',
			FileType.BFILE : 'b',
			FileType.CFILE : 'c',
			FileType.LINK : 'l',
			FileType.SOCKET : 's',
			FileType.PIPE : 'p'
		}.get(c,'?');

class PlatformPermissions:
	
	def __init__(self):
		self.users = [];
		self.groups = [];
	
	def jsonPickle(self, output):
		output = testFileWrite(output);
		jsonString = jsonpickle.encode(self, unpicklable=False);
		with open(output,'w') as fp:
			fp.write(jsonString);
			
	def pickle(self, output):
		output = testFileWrite(output);
		with open(output,'wb') as fp:
			pickle.dump(self, fp, protocol=4)
	
	@staticmethod
	def unpickle(input):
		input = testFileRead(input);
		with open(input,'rb') as fp:
			return pickle.load(fp);
	
	def add(self, name, permission, isUser=False):
		ug = self.groups;
		if(isUser):
			ug = self.users;
			
		found = False;
		for entry in ug:
			if(entry.name == name):
				entry.addPermission(permission);
				found = True;
				break;
		
		if(not found):
			toAdd = None;
			if(isUser):
				toAdd = User(name);
			else:
				toAdd = Group(name);
			toAdd.addPermission(permission);
			ug.append(toAdd);
			
	def parseAndAddFromXml(self,input):
		input = testFileRead(input);
		tree = xml.etree.ElementTree.parse(input).getroot();
		for ptype in tree.findall('permission'):
			permStr = ptype.get('name');
			if(permStr == None):
				raise Exception('Unable to find permission name for \'permission\' entry in \'' + input + '\'.');
			groups = [];
			for gtype in ptype.findall('group'):
				groupName = gtype.get('gid');
				if(groupName == None):
					raise Exception('Found a group with no gid!?! in \'' + input + '\'.');
				self.add(groupName, permStr);
		for utype in tree.findall('assign-permission'):
			permStr = utype.get('name');
			if(permStr == None):
				raise Exception('Unable to find permission name for \'assign-permission\' entry in \'' + input + '\'.');
			userName = utype.get('uid');
			if(userName == None):
				raise Exception('Unable to find uid name for \'assign-permission\' entry in \'' + input + '\'.');
			self.add(userName, permStr, True);
		
	@staticmethod
	def findEntriesForPlatformPermissions(platformPermissions, fileTree):
		ret = copy.deepcopy(platformPermissions);
		
		pathToEntry = {};
		adjLists = {};
		for entry in fileTree.entries:
			pathToEntry[entry.getFullPath()] = entry;
		
		rootEntry = pathToEntry['/'];
		if(rootEntry == None):
			raise Exception('A entry for the root does not exist!?!');
		
		for entry in fileTree.entries:
			if(entry == rootEntry):
				continue; # Skip root entry because it has no parent entry and is not a simlink
			
			if(not entry.directoryPath in pathToEntry.keys()):
				raise Exception('No entry for parent path \'' + entry.directoryPath + '\'.');
			parentEntry = pathToEntry[entry.directoryPath];
				
			if(not parentEntry in adjLists.keys()):	
				adjList = [];
				adjLists[parentEntry] = adjList;
			else:
				adjList = adjLists[parentEntry];
			adjList.append(entry);
			
			if(entry.type == FileType.LINK and entry.linkEntry != None):
				if(not entry in adjLists.keys()):
					adjList = [];
					adjLists[entry] = adjList;
				else:
					adjList = adjLists[entry];
				adjList.append(entry.linkEntry);
		
		for key, value in adjLists.items():
			print(key.getFullPath());
			for i in value:
				print("\t" + i.getFullPath());
		
		stack = [rootEntry];
		childIndexStack = [0];
		while(stack):
			cur = stack.pop();
			childIndex = childIndexStack.pop();
			# No children, already explored children, or is a symbolic link we have traversed or encountered another symbolic link pointing to the same thing
			# Also remove the file descriptors in proc and the subsystem symlinks in sys because both take forever to iterate over all possible paths and there
			# is nothing there anyways.
			if(cur in adjLists.keys() and childIndex < len(adjLists[cur]) and (cur.type != FileType.LINK or not (cur in stack or cur.linkEntry in stack))
					and not (cur.name == 'fd' and len(stack) > 2 and stack[1].name == 'proc') and not (cur.name == 'subsystem' and len(stack) > 2 and stack[1].name == 'sys')):
				adjList = adjLists[cur];
				next = adjList[childIndex];
				stack.append(cur);
				childIndexStack.append(childIndex + 1);
				stack.append(next);
				childIndexStack.append(0);
			else:
				for user in ret.users:
					if(cur.user == user.name):
						path = stack.copy();
						path.append(cur);
						user.addPath(path);
				for group in ret.groups:
					if(cur.group == group.name):
						path = stack.copy();
						path.append(cur);
						group.addPath(path);
		return ret;
			
	def __getstate__(self):
		return dict((k,v) for k,v in self.__dict__.items() if(not (v == None or (isinstance(v, bool) and v == False))));
	
	def __setstate__(self, inDict):
		#Add in values not listed which means they were None
		inDict['users'] = inDict.get('users',None);
		inDict['groups'] = inDict.get('groups',None);
		self.__dict__ = inDict;
		
	@staticmethod
	def sortJsonFields(kv):
		return {
			'users' : 0,
			'groups' : 1,
			'name' : 2,
			'permissions' : 3,
			'paths' : 4
		}.get(kv[0],5);

class User:
	
	def __init__(self, name):
		self.name = name;
		self.permissions = [];
		self.paths = None;
		
	def __eq__(self, other):
		if(not isinstance(other, User)):
			return NotImplemented;
		return self.name == other.name;
		
	def __hash__(self):
		return hash(self.name);
		
	def addPermission(self, permission):
		self.permissions.append(permission);
		
	def addPath(self, path):
		if(self.paths == None):
			self.paths = [];
		self.paths.append(path);
		
	def toString(self, excludePathStart=None):
		ret = [];
		ret.append('User: ' + self.name + '\n');
		ret.append('  Permissions: \n');
		for p in self.permissions:
			ret.append('    ' + p + '\n');
		ret.append(entriesToString(makeEntriesFromPaths(self.paths), '  ', excludePathStart))
		return ''.join(ret);
		
	def __getstate__(self):
		return dict((k,v) for k,v in self.__dict__.items() if(not (v == None or (isinstance(v, bool) and v == False))));
	
	def __setstate__(self, inDict):
		#Add in values not listed which means they were None
		inDict['name'] = inDict.get('name',None);
		inDict['permissions'] = inDict.get('permissions',None);
		inDict['paths'] = inDict.get('paths',None);
		self.__dict__ = inDict;
		
class Group:
	
	def __init__(self, name):
		self.name = name;
		self.permissions = [];
		self.paths = None;
		
	def __eq__(self, other):
		if(not isinstance(other, Group)):
			return NotImplemented;
		return self.name == other.name;
		
	def __hash__(self):
		return hash(self.name);
		
	def addPermission(self, permission):
		self.permissions.append(permission);
		
	def addPath(self, path):
		if(self.paths == None):
			self.paths = [];
		self.paths.append(path);
		
	def toString(self, excludePathStart=None):
		ret = [];
		ret.append('Group: ' + self.name + '\n');
		ret.append('  Permissions: \n');
		for p in self.permissions:
			ret.append('    ' + p + '\n');
		ret.append(entriesToString(makeEntriesFromPaths(self.paths), '  ', excludePathStart))
		return ''.join(ret);
			
	def __getstate__(self):
		return dict((k,v) for k,v in self.__dict__.items() if(not (v == None or (isinstance(v, bool) and v == False))));
	
	def __setstate__(self, inDict):
		#Add in values not listed which means they were None
		inDict['name'] = inDict.get('name',None);
		inDict['permissions'] = inDict.get('permissions',None);
		inDict['paths'] = inDict.get('paths',None);
		self.__dict__ = inDict;

def makeEntryFromPath(path):
	name = None;
	directoryPath = None;
	finalEntry = None;
	if(len(path) == 0): # Should not happen
		raise Exception('Encountered a path with no parts for user \'' + self.name + '\'');
	elif(len(path) == 1): # Can only be the root path
		name = '/';
		directoryPath = '';
		finalEntry = path[0];
	else:
		parent = None;
		for cur in path:
			if(parent == None):
				directoryPath = cur.name;
				name = None;
			else:
				if(name == None):
					name = cur.name;
				else:
					if(parent.type != FileType.LINK):
						directoryPath = posixpath.join(directoryPath, name);
						name = cur.name;
			parent = cur;
		finalEntry = parent;
	newEntry = FileEntry(name, directoryPath, finalEntry.type, finalEntry.isNoAccess, finalEntry.linkPath, finalEntry.user, finalEntry.group, finalEntry.userPermissions, 
		finalEntry.groupPermissions, finalEntry.globalPermissions, finalEntry.isSetUID, finalEntry.isSetGID, finalEntry.isSticky, finalEntry.selinuxContext, finalEntry.hardLinks,
		finalEntry.size, finalEntry.majorDeviceId, finalEntry.minorDeviceId, finalEntry.modDateTime);
	newEntry.linkEntry = finalEntry.linkEntry;
	return newEntry;
		
def makeEntriesFromPaths(paths):
	entries = None;
	if(paths != None):
		entries = [];
		for path in paths:
			entries.append(makeEntryFromPath(path));
	return entries;
		
def entriesToString(entries, padding, excludePathStart=None, onlyType=None):
	unixPerms = []; # Always len 10
	mHardLinks = 0;
	hardLinks = [];
	mUsers = 0;
	users = [];
	mGroups = 0;
	groups = [];
	mSELinux = 0;
	seLinux = [];
	mSizes = 0;
	sizes = [];
	dates = []; # Always len 18
	paths = []; # Don't pad this last one
	
	if(entries != None):
		for e in entries:
			if((excludePathStart != None and e.getFullPath().startswith(excludePathStart)) or (onlyType != None and e.type != onlyType)):
				continue;
			if(e.type == FileType.UNKNOWN):
				unixPerms.append('??????????');
				hardLinks.append('?');
				mHardLinks = 1 if 1 > mHardLinks else mHardLinks;
				users.append('?');
				mUsers = 1 if 1 > mUsers else mUsers;
				groups.append('?');
				mGroups = 1 if 1 > mGroups else mGroups;
				seLinux.append('?');
				mSELinux = 1 if 1 > mSELinux else mSELinux;
				sizes.append('?');
				mSizes = 1 if 1 > mSizes else mSizes;
				dates.append('[????-??-?? ??:??]');
				paths.append(e.getFullPath());
			else:
				unixPerm = FileType.longtoShort(e.type);
				unixPerm += 'r' if (e.userPermissions & 4) == 4 else '-';
				unixPerm += 'w' if (e.userPermissions & 2) == 2 else '-';
				unixPerm += ('s' if e.isSetUID else 'x') if (e.userPermissions & 1) == 1 else ('S' if e.isSetUID else '-');
				unixPerm += 'r' if (e.groupPermissions & 4) == 4 else '-';
				unixPerm += 'w' if (e.groupPermissions & 2) == 2 else '-';
				unixPerm += ('s' if e.isSetGID else 'x') if (e.groupPermissions & 1) == 1 else ('S' if e.isSetGID else '-');
				unixPerm += 'r' if (e.globalPermissions & 4) == 4 else '-';
				unixPerm += 'w' if (e.globalPermissions & 2) == 2 else '-';
				unixPerm += ('t' if e.isSticky else 'x') if (e.globalPermissions & 1) == 1 else ('T' if e.isSticky else '-');
				unixPerms.append(unixPerm);
				
				hardLink = str(e.hardLinks);
				hardLinks.append(hardLink);
				mHardLinks = len(hardLink) if len(hardLink) > mHardLinks else mHardLinks;
				
				users.append(e.user);
				mUsers = len(e.user) if len(e.user) > mUsers else mUsers;
				
				groups.append(e.group);
				mGroups = len(e.group) if len(e.group) > mGroups else mGroups;
				
				seLinuxContext = e.selinuxContext.toString();
				seLinux.append(seLinuxContext);
				mSELinux = len(seLinuxContext) if len(seLinuxContext) > mSELinux else mSELinux;
				
				size = e.size if e.size != None else e.majorDeviceId + ', ' + e.minorDeviceId;
				sizes.append(size);
				mSizes = len(size) if len(size) > mSizes else mSizes;
				
				dates.append(e.modDateTime);
				
				path = e.getFullPath();
				if(e.type == FileType.LINK):
					path += ' -> ' + e.linkPath;
				paths.append(path);
		
	ret = [];
	ret.append(padding + 'File Entries [Size=' + str(len(unixPerms)) + ']:\n');
	for i, unixPerm in enumerate(unixPerms):
		ret.append(padding + '  ' + unixPerm + ' ' + hardLinks[i].rjust(mHardLinks) + ' ' + users[i].rjust(mUsers) + ' ' + groups[i].rjust(mGroups) + ' ' 
			  + seLinux[i].rjust(mSELinux) + ' ' + sizes[i].rjust(mSizes) + ' ' + dates[i] + ' ' + paths[i] + '\n');
			  
	return ''.join(ret);
		
def mkdir_p(path):
	try:
		os.makedirs(path)
	except OSError as exc:  # Python >2.5
		if exc.errno == errno.EEXIST and os.path.isdir(path):
			pass
		else:
			raise

def testFileRead(input):
	input = os.path.abspath(input);
	if(not (os.path.exists(input) and os.path.isfile(input) and os.access(input, os.R_OK))):
		raise Exception('Unable to access input file \'' + input + '\'.');
	return input;
	
def testFileWrite(output):
	output = os.path.abspath(output);
	if(os.path.exists(output) and not (os.path.isfile(output) and os.access(output, os.W_OK))):
		raise Exception('Unable to write to output \'' + output + '\'.');
	return output;

# Expected input files are dumps from running the command 'ls -laRZ' on the '/' directory
def main():
	parser = argparse.ArgumentParser(description='');
	
	reqName = parser.add_argument_group('Required Arguments');
	reqName.add_argument('-f','--file',help='Input File Name (e.g. \'aosp_8.1.0_file_tree_root.txt\')',required=True,action='append');
	reqName.add_argument('-d','--dir',help='The working directory where output will be written and containing the input file names.',required=True);
	
	#Optional Arguments
	parser.add_argument("--json",help='Write input files to a json format.',action="store_true");
	parser.add_argument('-p','--platformpermissions',help='Realtive to the working directory, a XML file or a directory containing XML files that declare the platform permissions.');
	parser.add_argument('--findusersandgroups',help='Locate any file with a user or group matching one of those in the platform permissions.',action='store_true');
	
	args = parser.parse_args();
	
	workingDir = os.path.abspath(args.dir);
	if(os.path.exists(workingDir)):
		if(not (os.path.isdir(workingDir) and os.access(workingDir,os.R_OK | os.W_OK | os.X_OK))):
			raise Exception('Unable to access the working directory \'' + workingDir + '\'.');
	else:
		raise Exception('Unable to find the working directory \'' + workingDir + '\'.');
	
	# Parse the input XML files that contain the platform permissions either from the given directory or from
	# a single XML file and then pickle the data. If the pickle file already exists then just load the data from the 
	# pickle file. If the json option is declared then dump the data to a json file unless the json file already exists.
	platformPermissions = None;
	if(args.json):
		jsonpickle.set_preferred_backend('simplejson');
		jsonpickle.set_encoder_options('simplejson', indent=2, item_sort_key=PlatformPermissions.sortJsonFields);
	if(args.platformpermissions != None):
		outputPathPickle = os.path.join(workingDir, 'platform-permissions_db.pickle');
		outputPathJson = os.path.join(workingDir, 'platform-permissions_db.json');
		if(not os.path.exists(outputPathPickle)):
			platformPath = os.path.join(workingDir, args.platformpermissions);
			if(not os.path.exists(platformPath)):
				raise Exception('Unable to find the platform permissions path at \'' + platformPath + '\'.');
			platformPermissions = PlatformPermissions();
			if(os.path.isfile(platformPath)):
				if(os.path.splitext(platformPath)[1].lower() == '.xml'):
					platformPath = testFileRead(platformPath);
					platformPermissions.parseAndAddFromXml(platformPath);
			elif(os.path.isdir(platformPath)):
				for dirName, subdirList, fileList in os.walk(platformPath):
					for fname in fileList:
						filePath = os.path.join(dirName,fname);
						if(os.path.splitext(filePath)[1].lower() == '.xml'):
							filePath = testFileRead(filePath);
							platformPermissions.parseAndAddFromXml(filePath);
			else:
				raise Exception('The platform permissions path is not a file or directory \'' + platformPath + '\'.');
			platformPermissions.pickle(outputPathPickle);
		else:
			platformPermissions = PlatformPermissions.unpickle(outputPathPickle);
		
		if(args.json and not os.path.exists(outputPathJson)):
			platformPermissions.jsonPickle(outputPathJson);
		
	
	# Parse the input txt files which contain the records from ls -laRZ and then pickle the data. If the pickle file 
	# already exists then just load the data from the pickle file. If the json option is declared then dump the data
	# to a json file unless the json file already exists.
	if(args.json):
		jsonpickle.set_preferred_backend('simplejson');
		jsonpickle.set_encoder_options('simplejson', indent=2, item_sort_key=FileTree.sortJsonFields);
	for file in args.file:
		outputPathPickle = os.path.join(workingDir, os.path.splitext(file)[0] + '_db.pickle');
		outputPathJson = os.path.join(workingDir, os.path.splitext(file)[0] + '_db.json');
		
		fileTree = None;
		if(not os.path.exists(outputPathPickle)):
			file = testFileRead(file);
			fileTree = FileTree(file);
			fileTree.pickle(outputPathPickle);
		else:
			fileTree = FileTree.unpickle(outputPathPickle);
		
		if(args.json and not os.path.exists(outputPathJson)):
			fileTree.jsonPickle(outputPathJson);
			
		if(args.findusersandgroups and platformPermissions != None):
			outputPathPlatformPickle = os.path.join(workingDir, os.path.splitext(file)[0] + '_and_platform-permissions_db.pickle');
			outputPathPlatformJson = os.path.join(workingDir, os.path.splitext(file)[0] + '_and_platform-permissions_db.json');
			outputPathPlatformTxt = os.path.join(workingDir, os.path.splitext(file)[0] + '_and_platform-permissions.txt');
			outputPathPlatformNoProcTxt = os.path.join(workingDir, os.path.splitext(file)[0] + '_and_platform-permissions_no_proc.txt');
			platformPermissionsWithEntries = PlatformPermissions.findEntriesForPlatformPermissions(platformPermissions,fileTree);
			platformPermissionsWithEntries.pickle(outputPathPlatformPickle);
			if(args.json):
				jsonpickle.set_preferred_backend('simplejson');
				jsonpickle.set_encoder_options('simplejson', indent=2, item_sort_key=PlatformPermissions.sortJsonFields);
				platformPermissionsWithEntries.jsonPickle(outputPathPlatformJson);
				
			userEntryCount = 0;
			dumpstr = [];
			for u in platformPermissionsWithEntries.users:
				userEntryCount += 0 if u.paths == None else len(u.paths);
				dumpstr.append(u.toString());
			groupEntryCount = 0;
			for g in platformPermissionsWithEntries.groups:
				groupEntryCount += 0 if g.paths == None else len(g.paths);
				dumpstr.append(g.toString());
			outputPathPlatformTxt = testFileWrite(outputPathPlatformTxt);
			with open(outputPathPlatformTxt,'w') as fp:
				fp.write('Total File Entries: ' + str(userEntryCount + groupEntryCount) + '\n');
				fp.write('Total User File Entries: ' + str(userEntryCount) + '\n');
				fp.write('Total Group File Entries: ' + str(groupEntryCount) + '\n\n');
				fp.write('\n'.join(dumpstr));
				
			userEntryCount = 0;
			dumpstr = [];
			for u in platformPermissionsWithEntries.users:
				userEntryCount += 0 if u.paths == None else sum(1 for i in makeEntriesFromPaths(u.paths) if not i.getFullPath().startswith('/proc/'));
				dumpstr.append(u.toString('/proc/'));
			groupEntryCount = 0;
			for g in platformPermissionsWithEntries.groups:
				groupEntryCount += 0 if g.paths == None else sum(1 for i in makeEntriesFromPaths(g.paths) if not i.getFullPath().startswith('/proc/'));
				dumpstr.append(g.toString('/proc/'));
			outputPathPlatformNoProcTxt = testFileWrite(outputPathPlatformNoProcTxt);
			with open(outputPathPlatformNoProcTxt,'w') as fp:
				fp.write('Total File Entries: ' + str(userEntryCount + groupEntryCount) + '\n');
				fp.write('Total User File Entries: ' + str(userEntryCount) + '\n');
				fp.write('Total Group File Entries: ' + str(groupEntryCount) + '\n\n');
				fp.write('\n'.join(dumpstr));
				
			outputPathLinksOnlyNoProcTxt = os.path.join(workingDir, os.path.splitext(file)[0] + '_links_only_no_proc.txt');
			outputPathLinksOnlyTxt = os.path.join(workingDir, os.path.splitext(file)[0] + '_links_only.txt');
			with open(outputPathLinksOnlyNoProcTxt,'w') as fp:
				fp.write(entriesToString(fileTree.entries, '', '/proc/', FileType.LINK));
			with open(outputPathLinksOnlyTxt,'w') as fp:
				fp.write(entriesToString(fileTree.entries, '', None, FileType.LINK));
		
# dac_db.py -f aosp_8.1.0_file_tree_no_root.txt -f aosp_8.1.0_file_tree_root.txt -d .\ -p permissions --json --findusersandgroups
if __name__ == '__main__':
	main();
	#tempmain();
