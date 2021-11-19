package org.sag.woof.phases.filemethods;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.sag.acminer.database.excludedelements.IExcludeHandler;
import org.sag.acminer.database.excludedelements.IExcludedElementsDatabase;
import org.sag.acminer.phases.entrypoints.EntryPoint;
import org.sag.common.concurrent.CountingThreadExecutor;
import org.sag.common.concurrent.IgnorableRuntimeException;
import org.sag.common.graphtools.AlEdge;
import org.sag.common.graphtools.AlNode;
import org.sag.common.graphtools.Formatter;
import org.sag.common.graphtools.GraphmlGenerator;
import org.sag.common.io.FileHash;
import org.sag.common.io.FileHashList;
import org.sag.common.io.FileHelpers;
import org.sag.common.io.PrintStreamUnixEOL;
import org.sag.common.logging.ILogger;
import org.sag.common.tools.HierarchyHelpers;
import org.sag.common.tools.SortingMethods;
import org.sag.common.tuple.Pair;
import org.sag.soot.SootSort;
import org.sag.woof.IWoofDataAccessor;
import org.sag.woof.database.filemethods.FileMethod;
import org.sag.woof.database.filemethods.IFileMethodsDatabase;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.EdgePredicate;
import soot.jimple.toolkits.callgraph.Filter;

public class DiscoverFileMethods {
	
	private final String cn;
	private final ILogger logger;
	private final IWoofDataAccessor dataAccessor;
	private volatile IFileMethodsDatabase db;
	private final IExcludedElementsDatabase excludeDB;
	private final IExcludedElementsDatabase javaAPIIndicatorDB;
	private final List<Path> externalDep;
	private final Path nativeFileAccessMethodsFile;
	private final Path javaAPIFileMethodsFile;
	private final Path androidAPIFileMethodsFile;
	private final Path rootPath;
	
	private final boolean debugJavaEnabled;
	private final boolean debugAndroidEnabled;
	private volatile Map<SootMethod,AtomicInteger> commonJavaMethods;
	private volatile Map<SootMethod,AtomicInteger> commonAndroidMethods;
	private final Path debugJavaDir;
	private final Path debugAndroidDir;
	private final Path debugJavaGraphsDir;
	private final Path debugAndroidGraphsDir;
	private final Path debugJavaPathsDir;
	private final Path debugAndroidPathsDir;
	private final Path debugJavaCommonCGMethodsFile;
	private final Path debugAndroidCommonCGMethodsFile;
	
	public DiscoverFileMethods(IWoofDataAccessor dataAccessor, IExcludedElementsDatabase excludeDB, IExcludedElementsDatabase javaAPIIndicatorDB, 
			List<Path> externalDep, Path nativeFileAccessMethodsFile, Path javaAPIFileMethodsFile, Path androidAPIFileMethodsFile, Path rootPath, 
			Path debugDir, ILogger logger) {
		this.logger = logger;
		this.dataAccessor = dataAccessor;
		this.db = null;
		this.excludeDB = excludeDB;
		this.javaAPIIndicatorDB = javaAPIIndicatorDB;
		this.externalDep = externalDep;
		this.cn = getClass().getSimpleName();
		this.nativeFileAccessMethodsFile = nativeFileAccessMethodsFile;
		this.javaAPIFileMethodsFile = javaAPIFileMethodsFile;
		this.androidAPIFileMethodsFile = androidAPIFileMethodsFile;
		this.rootPath = rootPath;
		
		this.debugJavaEnabled = false;
		this.debugAndroidEnabled = false;
		this.commonJavaMethods = new HashMap<>();
		this.commonAndroidMethods = new HashMap<>();
		this.debugJavaDir = FileHelpers.getPath(debugDir, "java");
		this.debugAndroidDir = FileHelpers.getPath(debugDir, "android");
		this.debugJavaGraphsDir = FileHelpers.getPath(debugJavaDir, "graphs");
		this.debugAndroidGraphsDir = FileHelpers.getPath(debugAndroidDir, "graphs");
		this.debugJavaPathsDir = FileHelpers.getPath(debugJavaDir, "paths");
		this.debugAndroidPathsDir = FileHelpers.getPath(debugAndroidDir, "paths");
		this.debugJavaCommonCGMethodsFile = FileHelpers.getPath(debugJavaDir, "common_cg_methods.txt");
		this.debugAndroidCommonCGMethodsFile = FileHelpers.getPath(debugAndroidDir, "common_cg_methods.txt");
	}
	
	public boolean run() {
		boolean successOuter = true;
		CountingThreadExecutor exe = null;
		final List<Throwable> errs = new ArrayList<>();
		
		logger.info("{}: Begin discovering file methods.",cn);
		
		try {
			logger.info("{}: Parsing the native file methods file at '{}'.",cn,nativeFileAccessMethodsFile);
			this.db = IFileMethodsDatabase.Factory.readTXT(nativeFileAccessMethodsFile);
			dataAccessor.setFileMethodsDB(this.db); //The read in NFM will be used as a root to construct everything else
			logger.info("{}: Successfully parsed the native file methods file.",cn);
			
			exe = new CountingThreadExecutor();
			
			Set<SootMethod> apiMethods = dataAccessor.getAndroidAPIDB().getMethods();
			Set<SootMethod> javaAPIEntryPoints = new HashSet<>();
			Set<SootMethod> androidAPIEntryPoints = new HashSet<>();
			for(SootMethod sm : apiMethods) {
				if(!excludeDB.isExcludedMethod(sm)) { //Make sure entry points that are excluded get ignored
					if(!javaAPIIndicatorDB.isExcludedMethod(sm)) {
						javaAPIEntryPoints.add(sm);
					} else {
						androidAPIEntryPoints.add(sm);
					}
				}
			}
			
			runJavaDiscovery(javaAPIEntryPoints, exe);
			runAndroidDiscovery(androidAPIEntryPoints, exe);
			
		} catch(IgnorableRuntimeException t) {	
			successOuter = false;
		} catch(Throwable t) {
			synchronized(errs) {
				errs.add(t);
			}
		} finally {
			boolean success = true;
			if(exe != null) {
				success = exe.shutdownWhenFinished();
			}
			List<Throwable> snapshot = new ArrayList<>();
			synchronized(errs) {
				snapshot.addAll(errs);
				if(exe != null) {
					snapshot.addAll(exe.getAndClearExceptions());
				}
			}
			if(!snapshot.isEmpty()) {
				successOuter = false;
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try(PrintStream ps = new PrintStream(baos,true,"utf-8")) {
					ps.println(cn + ": Failed to successfully discover file methods. The following exceptions occured:");
					int i = 0;
					for(Throwable t : errs){
						ps.print("Exception ");
						ps.print(i++);
						ps.print(": ");
						t.printStackTrace(ps);
					}
					logger.fatal(new String(baos.toByteArray(), StandardCharsets.UTF_8));
				} catch(Throwable t) {
					logger.fatal("{}: Something went wrong when generating the group exception log entry.",t,cn);
				}
			} else if(!success) {
				successOuter = false;
				logger.fatal("{}: Failed to properly close the counting thread executor.",cn);
			}
		}
		
		return successOuter;
	}
	
	private boolean isRunRequired(Path outFile, List<Path> depFiles) throws Exception {
		//If the file does not exist then we need to generate it
		if(!FileHelpers.checkRWFileExists(outFile)) {
			return true;
		}
		
		FileHashList oldDependencyFileHashes = db.readFileHashListInTXT(outFile);
		List<Path> oldDependencyFilePaths = new ArrayList<>();
		
		//generate the oldDependencyFilePaths
		for(FileHash oldFileHash : oldDependencyFileHashes){
			//from the hash information reconstruct the full path to the old Dependency file and add to set
			oldDependencyFilePaths.add(oldFileHash.getFullPath(rootPath));
		}
		
		//make sure there are no changes the the file dependencies
		//do this before the comparison of the file hashes so we can be sure that all old dependency files are actually needed
		//and if not some change occurred so we need to rerun the analysis anyways
		if(!oldDependencyFilePaths.equals(depFiles))
			return true;
		
		//Since there are no changes in the file dependencies, that means the files must exist and if they don't we have a problem (i.e. an exception gets thrown)
		//Generate the file hashes of each dependency file (under this assumption) and compare the the has of each dependency file on record
		//If they all match then nothing has changed and nothing needs to be done else need to rerun analysis
		for(FileHash oldFileHash : oldDependencyFileHashes){
			if(!oldFileHash.compareHash(FileHelpers.genFileHash(oldFileHash.getFullPath(rootPath), oldFileHash.getPath())))
				return true;
		}
		return false;
	}
	
	private void runJavaDiscovery(Set<SootMethod> javaAPIEntryPoints, CountingThreadExecutor exe) {
		logger.info("{}: Begin discovering file methods for the Java API.",cn);
		
		try {
			if(isRunRequired(javaAPIFileMethodsFile, externalDep)) {
				Map<SootMethod,boolean[]> nonAPISinkMethods = addKnownJavaFileMethods(javaAPIEntryPoints, exe);
				
				Map<SootMethod,FileMethod> sinks = new HashMap<>();
				for(FileMethod fm : db.getOutputData()) {
					sinks.put(fm.getSootMethod(),fm);
				}
				sinks = ImmutableMap.copyOf(sinks);
				
				for(SootMethod javaAPIEP : javaAPIEntryPoints) {
					exe.execute(new DiscoverFileMethodsRunner(javaAPIEP, sinks, FileMethod.javaAPIStr, null, nonAPISinkMethods, false));
				}
				
				exe.awaitCompletion();
				
				Set<FileMethod> javaAPINoNative = db.getJavaAPIMethods();
				javaAPINoNative.removeAll(db.getNativeMethods());
				db.writePartsTXT(javaAPIFileMethodsFile, javaAPINoNative, "Java API", FileHelpers.genFileHashList(externalDep, rootPath));
				
				if(debugJavaEnabled) {
					logger.info("{}: Dumping debug info the Java API file methods.",cn);
					doDebug(javaAPINoNative, sinks, nonAPISinkMethods, exe, FileMethod.javaAPIStr);
					logger.info("{}: Successfully dumped debug info the Java API file methods.",cn);
				}
			} else {
				logger.info("{}: The '{}' already exists. Loading data from file.",cn,javaAPIFileMethodsFile);
				db.readTXT(javaAPIFileMethodsFile);
			}
		} catch(IgnorableRuntimeException t) {
			logger.fatal("{}: Failed to discover all file methods for the Java API.", cn);
			throw t;
		} catch(Throwable t) {
			logger.fatal("{}: Failed to discover all file methods for the Java API.", t, cn);
			throw new IgnorableRuntimeException();
		}
		
		logger.info("{}: Successfully discovered all file methods for the Java API.",cn);
	}
	
	private final int digits(int n) {
		int len = String.valueOf(n).length();
		if(n < 0)
			return len - 1;
		else
			return len;
	}
	
	private final String padNum(int n, int digits) {
		return String.format("%"+digits+"d", n);
	}
	
	private void runAndroidDiscovery(Set<SootMethod> androidAPIEntryPoints, CountingThreadExecutor exe) {
		logger.info("{}: Begin discovering file methods for the Android API.",cn);
		
		try {
			List<Path> javaAndExtDep = new ArrayList<>(externalDep);
			javaAndExtDep.add(javaAPIFileMethodsFile);
			
			if(isRunRequired(androidAPIFileMethodsFile, javaAndExtDep)) {
				Map<SootMethod,boolean[]> nonAPISinkMethods = addKnownAndroidFileMethods(androidAPIEntryPoints, exe);
				Map<SootMethod,FileMethod> sinks = new HashMap<>();
				for(FileMethod fm : db.getOutputData()) {
					sinks.put(fm.getSootMethod(),fm);
				}
				sinks = ImmutableMap.copyOf(sinks);
				
				for(SootMethod androidAPIEP : androidAPIEntryPoints) {
					exe.execute(new DiscoverFileMethodsRunner(androidAPIEP, sinks, FileMethod.androidAPIStr, null, nonAPISinkMethods, false));
				}
				
				exe.awaitCompletion();
				
				Set<FileMethod> androidAPINoNative = db.getAndroidAPIMethods();
				androidAPINoNative.removeAll(db.getNativeMethods());
				db.writePartsTXT(androidAPIFileMethodsFile, androidAPINoNative, "Android API", FileHelpers.genFileHashList(javaAndExtDep, rootPath));
				
				if(debugAndroidEnabled) {
					logger.info("{}: Dumping debug info the Android API file methods.",cn);
					doDebug(androidAPINoNative, sinks, nonAPISinkMethods, exe, FileMethod.androidAPIStr);
					logger.info("{}: Successfully dumped debug info the Android API file methods.",cn);
				}
			} else {
				logger.info("{}: The '{}' already exists. Loading data from file.",cn,androidAPIFileMethodsFile);
				db.readTXT(androidAPIFileMethodsFile);
			}
		} catch(IgnorableRuntimeException t) {
			logger.fatal("{}: Failed to discover all file methods for the Android API.", cn);
			throw t;
		} catch(Throwable t) {
			logger.fatal("{}: Failed to discover all file methods for the Android API.", t, cn);
			throw new IgnorableRuntimeException();
		}
		
		logger.info("{}: Successfully discovered all file methods for the Android API.",cn);
	}
	
	private Map<SootMethod,boolean[]> addKnownAndroidFileMethods(Set<SootMethod> androidAPIEntryPoints, CountingThreadExecutor exe) throws Exception {
		Map<SootMethod,FileMethod> sinks = new HashMap<>();
		for(FileMethod fm : db.getOutputData()) {
			sinks.put(fm.getSootMethod(),fm);
		}
		sinks = ImmutableMap.copyOf(sinks);
		Map<SootMethod,boolean[]> nonAPISinkMethods = new HashMap<>();
		
		{//Handle Context
		Map<String,boolean[]> contextSubSigs = new HashMap<>();
		contextSubSigs.put("android.content.SharedPreferences getSharedPreferences(java.lang.String,int)",new boolean[] {true,true,false});
		contextSubSigs.put("android.content.SharedPreferences getSharedPreferences(java.io.File,int)",new boolean[] {true,true,false});
		contextSubSigs.put("boolean deleteSharedPreferences(java.lang.String)",new boolean[] {false,true,true});
		contextSubSigs.put("boolean moveSharedPreferencesFrom(android.content.Context,java.lang.String)",new boolean[] {true,true,true});
		contextSubSigs.put("void reloadSharedPreferences()",new boolean[] {true,true,false});
		contextSubSigs.put("android.database.sqlite.SQLiteDatabase openOrCreateDatabase(java.lang.String,int,android.database.sqlite.SQLiteDatabase$CursorFactory,android.database.DatabaseErrorHandler)",new boolean[] {true,true,false});
		contextSubSigs.put("android.database.sqlite.SQLiteDatabase openOrCreateDatabase(java.lang.String,int,android.database.sqlite.SQLiteDatabase$CursorFactory)",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.FileInputStream openFileInput(java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.FileOutputStream openFileOutput(java.lang.String,int)",new boolean[] {true,true,false});
		contextSubSigs.put("boolean moveDatabaseFrom(android.content.Context,java.lang.String)",new boolean[] {true,true,true});
		contextSubSigs.put("java.io.File getSharedPreferencesPath(java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File getSharedPrefsFile(java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File[] getObbDirs()",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File getObbDir()",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File getNoBackupFilesDir()",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File getFileStreamPath(java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File getFilesDir()",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File[] getExternalMediaDirs()",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File[] getExternalFilesDirs(java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File getExternalFilesDir(java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File[] getExternalCacheDirs()",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File getExternalCacheDir()",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File getDir(java.lang.String,int)",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File getDataDir()",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File getDatabasePath(java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File getCodeCacheDir()",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.File getCacheDir()",new boolean[] {true,true,false});
		contextSubSigs.put("java.lang.String[] fileList()",new boolean[] {true,true,false});
		contextSubSigs.put("boolean deleteFile(java.lang.String)",new boolean[] {false,true,true});
		contextSubSigs.put("boolean deleteDatabase(java.lang.String)",new boolean[] {false,true,true});
		contextSubSigs.put("java.lang.String[] databaseList()",new boolean[] {true,true,false});
		Set<SootMethod> contextMethods = HierarchyHelpers.getAllImplementingMethods(Scene.v().getSootClass("android.content.Context"));
		contextMethods.addAll(HierarchyHelpers.getAllImplementingMethods(Scene.v().getSootClass("android.app.ContextImpl")));
		for(SootMethod sm : contextMethods) {
			boolean[] defaultTypes = contextSubSigs.get(sm.getSubSignature());
			if(defaultTypes != null) {
				//If the method is an api then processes it and if it contains anything override the actions
				//If it is not an api method then we want it to be a non api sink method if anything is detected
				if(androidAPIEntryPoints.contains(sm)) {
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, defaultTypes, null, false));
				} else {
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, defaultTypes, nonAPISinkMethods, true));
				}
			} else {
				//Exclude all methods in context that do not match our sub sigs
				synchronized(nonAPISinkMethods) {
					nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
				}
			}
			//Remove any api methods we have processed in some way
			androidAPIEntryPoints.remove(sm);
		}
		}
		
		{//SQLiteStatement is access only for all
		for(SootMethod sm : Scene.v().getSootClass("android.database.sqlite.SQLiteStatement").getMethods()) {
			if(androidAPIEntryPoints.contains(sm)) {
				androidAPIEntryPoints.remove(sm);
				exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {false,true,false}, null, false));
			}
		}
		}
		
		{//Only the query methods access everything else does nothing
		for(SootMethod sm : Scene.v().getSootClass("android.database.sqlite.SQLiteQueryBuilder").getMethods()) {
			if(androidAPIEntryPoints.contains(sm)) {
				if(sm.getName().equals("query")) {
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {false,true,false}, null, false));
				} else {
					synchronized(nonAPISinkMethods) {
						nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
					}
				}
				androidAPIEntryPoints.remove(sm);
			}
		}
		}
		
		{//Two methods open a database but the rest do not access or open the file
		for(SootMethod sm : Scene.v().getSootClass("android.database.sqlite.SQLiteOpenHelper").getMethods()) {
			if(androidAPIEntryPoints.contains(sm)) {
				if(sm.getName().equals("getReadableDatabase") || sm.getName().equals("getWritableDatabase")) {
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {true,true,false}, null, false));
				} else {
					synchronized(nonAPISinkMethods) {
						nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
					}
				}
				androidAPIEntryPoints.remove(sm);
			}
		}
		}
		
		{//Most methods in database are accessing the database, only those beginning with open actually open, also deleteDatabase is non api
		for(SootMethod sm : Scene.v().getSootClass("android.database.sqlite.SQLiteDatabase").getMethods()) {
			if(androidAPIEntryPoints.contains(sm)) {
				if(sm.getName().startsWith("open")) {
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {true,true,false}, null, false));
				} else {
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {false,true,false}, null, false));
				}
				androidAPIEntryPoints.remove(sm);
			} else if(sm.getName().equals("deleteDatabase")) {
				exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {false,true,true}, nonAPISinkMethods, true));
			}
		}
		}
		
		{//Cursors are access only or nothing at all because they are reading from a cache
		Set<SootMethod> cursorMethods = HierarchyHelpers.getAllImplementingMethods(Scene.v().getSootClass("android.database.Cursor"));
		cursorMethods.addAll(HierarchyHelpers.getAllImplementingMethods(Scene.v().getSootClass("android.database.CrossProcessCursor")));
		for(SootMethod sm : cursorMethods) {
			String name = sm.getName();
			if(androidAPIEntryPoints.contains(sm) && (name.equals("onMove") || name.equals("getCount") || name.equals("fillWindow") || name.startsWith("move") || name.equals("isAfterLast")
						|| name.equals("isBeforeFirst") || name.equals("isLast") || name.equals("isFirst"))) {
				exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {false,true,false}, null, false));
			} else {
				synchronized(nonAPISinkMethods) {
					nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
				}
			}
			androidAPIEntryPoints.remove(sm);
		}
		}
		
		{// Handle most of android.app which are mostly dumps
		Set<String> fragmentClasses = new HashSet<>();
		fragmentClasses.add("android.app.Fragment");
		fragmentClasses.add("android.app.FragmentController");
		fragmentClasses.add("android.app.FragmentManagerImpl");
		fragmentClasses.add("android.app.FragmentBreadCrumbs");
		fragmentClasses.add("android.app.LoaderManagerImpl");
		fragmentClasses.add("android.app.NotificationChannel");
		fragmentClasses.add("android.app.RemoteAction");
		fragmentClasses.add("android.app.Service");
		fragmentClasses.add("android.app.WallpaperInfo");
		fragmentClasses.add("android.app.WallpaperManager");
		fragmentClasses.add("android.app.DialogFragment");
		fragmentClasses.add("android.app.ApplicationErrorReport");
		fragmentClasses.add("android.app.admin.DeviceAdminInfo");
		fragmentClasses.add("android.app.admin.DeviceAdminInfo");
		fragmentClasses.add("android.app.Activity");
		fragmentClasses.add("android.content.IntentFilter");
		fragmentClasses.add("android.content.pm.ActivityInfo");
		fragmentClasses.add("android.content.pm.ApplicationInfo");
		fragmentClasses.add("android.content.pm.ComponentInfo");
		fragmentClasses.add("android.content.pm.PackageItemInfo");
		fragmentClasses.add("android.content.pm.ProviderInfo");
		fragmentClasses.add("android.content.pm.ResolveInfo");
		fragmentClasses.add("android.content.pm.ServiceInfo");
		fragmentClasses.add("android.location.Location");
		fragmentClasses.add("android.net.wifi.WifiNetworkScoreCache");
		fragmentClasses.add("android.os.TokenWatcher");
		fragmentClasses.add("android.service.dreams.DreamService");
		fragmentClasses.add("android.service.voice.VoiceInteractionService");
		fragmentClasses.add("android.service.voice.VoiceInteractionSession");
		fragmentClasses.add("android.service.voice.VoiceInteractionSessionService");
		fragmentClasses.add("android.service.wallpaper.WallpaperService");
		fragmentClasses.add("android.service.wallpaper.WallpaperService$Engine");
		fragmentClasses.add("android.view.inputmethod.InputMethodInfo");
		Set<SootMethod> methods = new HashSet<>();
		for(String className : fragmentClasses) {
			SootClass sc = Scene.v().getSootClassUnsafe(className, false);
			if(sc != null) {
				methods.addAll(sc.getMethods());
				for(SootClass other : Scene.v().getClasses()) {
					if(other.getName().startsWith(className + "$")) {
						methods.addAll(other.getMethods());
					}
				}
			}
		}
		for(SootMethod sm : methods) {
			String name = sm.getName();
			boolean isAPI = androidAPIEntryPoints.contains(sm);
			if(isAPI && (name.startsWith("dump") || name.equals("populateFromXml") || name.equals("writeXml") || name.equals("dumpPackageState") || 
					name.equals("managedQuery") || name.equals("onDump") || name.equals("readFromXml") || name.equals("writeToXml"))) {
				exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {false,true,false}, null, false));
			} else if(isAPI && (name.equals("setStream"))) {
				exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {true,true,false}, null, false));
			} else {
				synchronized(nonAPISinkMethods) {
					nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
				}
			}
			androidAPIEntryPoints.remove(sm);
		}
		
		SootClass sc = Scene.v().getSootClassUnsafe("android.app.DownloadManager", false);
		if(sc != null) {
			methods = new HashSet<>();
			methods.addAll(sc.getMethods());
			for(SootClass other : Scene.v().getClasses()) {
				if(other.getName().startsWith(sc.getName() + "$")) {
					methods.addAll(other.getMethods());
				}
			}
			for(SootMethod sm : methods) {
				String name = sm.getName();
				boolean isAPI = androidAPIEntryPoints.contains(sm);
				if(isAPI && name.equals("openDownloadedFile")) {
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {true,false,false}, null, false));
				} else if(isAPI) {
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {false,true,false}, null, false));
				} else {
					synchronized(nonAPISinkMethods) {
						nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
					}
				}
				androidAPIEntryPoints.remove(sm);
			}
		}
		}
		
		{// Handle android.app.backup
		Map<String,boolean[]> contextSubSigs = new HashMap<>();
		contextSubSigs.put("void performBackup(android.os.ParcelFileDescriptor,android.app.backup.BackupDataOutput,android.os.ParcelFileDescriptor)",new boolean[] {false,true,false});
		contextSubSigs.put("void restoreEntity(android.app.backup.BackupDataInputStream)",new boolean[] {true,true,false});
		contextSubSigs.put("void writeNewStateDescription(android.os.ParcelFileDescriptor)",new boolean[] {false,true,false});
		Set<SootMethod> contextMethods = HierarchyHelpers.getAllImplementingMethods(Scene.v().getSootClass("android.app.backup.BackupHelper"));
		for(SootMethod sm : contextMethods) {
			boolean[] defaultTypes = contextSubSigs.get(sm.getSubSignature());
			if(defaultTypes != null) {
				//If the method is an api then processes it and if it contains anything override the actions
				//If it is not an api method then we want it to be a non api sink method if anything is detected
				if(androidAPIEntryPoints.contains(sm)) {
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, defaultTypes, null, false));
				} else {
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, defaultTypes, nonAPISinkMethods, true));
				}
			} else {
				//Exclude all methods in context that do not match our sub sigs
				synchronized(nonAPISinkMethods) {
					nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
				}
			}
			//Remove any api methods we have processed in some way
			androidAPIEntryPoints.remove(sm);
		}
		
		Map<String, boolean[]> classes = new HashMap<>();
		classes.put("android.app.backup.BackupAgent", new boolean[] {true,true,false});
		classes.put("android.app.backup.BackupAgentHelper", new boolean[] {true,true,false});
		classes.put("android.app.backup.BackupTransport", new boolean[] {true,true,true});
		for(String className : classes.keySet()) {
			SootClass sc = Scene.v().getSootClassUnsafe(className, false);
			if(sc != null) {
				for(SootMethod sm : sc.getMethods()) {
					if(androidAPIEntryPoints.contains(sm)) {
						exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, classes.get(className), null, false));
						androidAPIEntryPoints.remove(sm);
					}
				}
			}
		}
		
		//Special case for a single method
		for(Iterator<SootMethod> it = androidAPIEntryPoints.iterator(); it.hasNext();) {
			SootMethod sm = it.next();
			if(sm.getSignature().equals("<android.app.backup.FileBackupHelper: void <init>(android.content.Context,java.lang.String[])>")) {
				synchronized(nonAPISinkMethods) {
					nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
				}
				it.remove();
			}
		}
		}
		
		{
		Map<String,boolean[]> contextSubSigs = new HashMap<>();
		contextSubSigs.put("dump",new boolean[] {false,true,false});
		contextSubSigs.put("loadInBackground",new boolean[] {true,true,false});
		contextSubSigs.put("forceLoad",new boolean[] {true,true,false});
		Set<SootMethod> contextMethods = HierarchyHelpers.getAllImplementingMethods(Scene.v().getSootClass("android.content.Loader"));
		contextMethods.addAll(HierarchyHelpers.getAllImplementingMethods(Scene.v().getSootClass("android.content.AsyncTaskLoader")));
		for(SootMethod sm : contextMethods) {
			boolean[] defaultTypes = contextSubSigs.get(sm.getName());
			if(defaultTypes != null) {
				//If the method is an api then processes it and if it contains anything override the actions
				//If it is not an api method then we want it to be a non api sink method if anything is detected
				if(androidAPIEntryPoints.contains(sm)) {
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, defaultTypes, null, false));
				} else {
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, defaultTypes, nonAPISinkMethods, true));
				}
			} else {
				//Exclude all methods in context that do not match our sub sigs
				synchronized(nonAPISinkMethods) {
					nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
				}
			}
			//Remove any api methods we have processed in some way
			androidAPIEntryPoints.remove(sm);
		}
		}
		
		{// Handle ContentProvider
		Map<String,boolean[]> contextSubSigs = new HashMap<>();
		contextSubSigs.put("android.content.ContentProviderResult[] applyBatch(java.util.ArrayList)",new boolean[] {true,true,false});
		contextSubSigs.put("android.content.ContentProviderResult[] applyBatch(java.lang.String,java.util.ArrayList)",new boolean[] {true,true,false});
		contextSubSigs.put("void attachInfo(android.content.Context,android.content.pm.ProviderInfo)",new boolean[] {true,true,false});
		contextSubSigs.put("void attachInfoForTesting(android.content.Context,android.content.pm.ProviderInfo)",new boolean[] {true,true,false});
		contextSubSigs.put("int bulkInsert(android.net.Uri,android.content.ContentValues[])",new boolean[] {true,true,false});
		contextSubSigs.put("android.net.Uri insert(android.net.Uri,android.content.ContentValues)",new boolean[] {true,true,false});
		contextSubSigs.put("int delete(android.net.Uri,java.lang.String,java.lang.String[])",new boolean[] {true,true,false});
		contextSubSigs.put("java.lang.String[] getStreamTypes(android.net.Uri,java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("java.lang.String getType(android.net.Uri)",new boolean[] {true,true,false});
		contextSubSigs.put("android.os.Bundle call(java.lang.String,java.lang.String,android.os.Bundle)",new boolean[] {true,true,false});
		contextSubSigs.put("android.os.Bundle call(android.net.Uri,java.lang.String,java.lang.String,android.os.Bundle)",new boolean[] {true,true,false});
		contextSubSigs.put("void dump(java.io.FileDescriptor,java.io.PrintWriter,java.lang.String[])",new boolean[] {true,true,false});
		contextSubSigs.put("android.content.res.AssetFileDescriptor openAssetFile(android.net.Uri,java.lang.String,android.os.CancellationSignal)",new boolean[] {true,true,false});
		contextSubSigs.put("android.content.res.AssetFileDescriptor openAssetFile(android.net.Uri,java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("android.os.ParcelFileDescriptor openFile(android.net.Uri,java.lang.String,android.os.CancellationSignal)",new boolean[] {true,true,false});
		contextSubSigs.put("android.os.ParcelFileDescriptor openFile(android.net.Uri,java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("android.os.ParcelFileDescriptor openFileHelper(android.net.Uri,java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("android.os.ParcelFileDescriptor openPipeHelper(android.net.Uri,java.lang.String,android.os.Bundle,java.lang.Object,android.content.ContentProvider$PipeDataWriter)",new boolean[] {true,true,false});
		contextSubSigs.put("android.content.res.AssetFileDescriptor openTypedAssetFile(android.net.Uri,java.lang.String,android.os.Bundle,android.os.CancellationSignal)",new boolean[] {true,true,false});
		contextSubSigs.put("android.content.res.AssetFileDescriptor openTypedAssetFile(android.net.Uri,java.lang.String,android.os.Bundle)",new boolean[] {true,true,false});
		contextSubSigs.put("android.database.Cursor query(android.net.Uri,java.lang.String[],android.os.Bundle,android.os.CancellationSignal)",new boolean[] {true,true,false});
		contextSubSigs.put("android.database.Cursor query(android.net.Uri,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String,android.os.CancellationSignal)",new boolean[] {true,true,false});
		contextSubSigs.put("android.database.Cursor query(android.net.Uri,java.lang.String[],java.lang.String,java.lang.String[],java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("boolean refresh(android.net.Uri,android.os.Bundle,android.os.CancellationSignal)",new boolean[] {true,true,false});
		contextSubSigs.put("int update(android.net.Uri,android.content.ContentValues,java.lang.String,java.lang.String[])",new boolean[] {true,true,false});
		contextSubSigs.put("android.content.res.AssetFileDescriptor openAssetFileDescriptor(android.net.Uri,java.lang.String,android.os.CancellationSignal)",new boolean[] {true,true,false});
		contextSubSigs.put("android.content.res.AssetFileDescriptor openAssetFileDescriptor(android.net.Uri,java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("android.os.ParcelFileDescriptor openFileDescriptor(android.net.Uri,java.lang.String,android.os.CancellationSignal)",new boolean[] {true,true,false});
		contextSubSigs.put("android.os.ParcelFileDescriptor openFileDescriptor(android.net.Uri,java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.InputStream openInputStream(android.net.Uri)",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.OutputStream openOutputStream(android.net.Uri,java.lang.String)",new boolean[] {true,true,false});
		contextSubSigs.put("java.io.OutputStream openOutputStream(android.net.Uri)",new boolean[] {true,true,false});
		contextSubSigs.put("android.content.res.AssetFileDescriptor openTypedAssetFileDescriptor(android.net.Uri,java.lang.String,android.os.Bundle)",new boolean[] {true,true,false});
		contextSubSigs.put("android.content.res.AssetFileDescriptor openTypedAssetFileDescriptor(android.net.Uri,java.lang.String,android.os.Bundle,android.os.CancellationSignal)",new boolean[] {true,true,false});
		Set<SootMethod> contextMethods = HierarchyHelpers.getAllImplementingMethods(Scene.v().getSootClass("android.content.ContentProvider"));
		contextMethods.addAll(HierarchyHelpers.getAllImplementingMethods(Scene.v().getSootClass("android.content.ContentResolver")));
		contextMethods.addAll(HierarchyHelpers.getAllImplementingMethods(Scene.v().getSootClass("android.content.ContentProviderClient")));
		for(SootMethod sm : contextMethods) {
			boolean[] defaultTypes = contextSubSigs.get(sm.getSubSignature());
			if(defaultTypes != null) {
				//If the method is an api then processes it and if it contains anything override the actions
				//If it is not an api method then we want it to be a non api sink method if anything is detected
				if(androidAPIEntryPoints.contains(sm)) {
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, defaultTypes, null, false));
				} else {
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, defaultTypes, nonAPISinkMethods, true));
				}
			} else {
				//Exclude all methods in context that do not match our sub sigs
				synchronized(nonAPISinkMethods) {
					nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
				}
			}
			//Remove any api methods we have processed in some way
			androidAPIEntryPoints.remove(sm);
		}
		
		Map<String, boolean[]> classes = new HashMap<>();
		classes.put("android.content.ContentQueryMap", new boolean[] {false,true,false});
		for(String className : classes.keySet()) {
			SootClass sc = Scene.v().getSootClassUnsafe(className, false);
			if(sc != null) {
				for(SootMethod sm : sc.getMethods()) {
					if(androidAPIEntryPoints.contains(sm)) {
						exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, classes.get(className), null, false));
						androidAPIEntryPoints.remove(sm);
					}
				}
			}
		}
		}
		
		{
		SootClass sc = Scene.v().getSootClassUnsafe("android.media.ExifInterface",false);
		if(sc != null) {
			for(SootMethod sm : sc.getMethods()) {
				if(androidAPIEntryPoints.contains(sm)) {
					String name = sm.getName();
					if(name.equals("<init>") || name.equals("saveAttributes")) {
						exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, null, null, false));
					} else {
						synchronized(nonAPISinkMethods) {
							nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
						}
					}
					androidAPIEntryPoints.remove(sm);
				}
			}
		}
		}
		
		{
		SootClass sc = Scene.v().getSootClassUnsafe("android.media.MediaPlayer",false);
		if(sc != null) {
			for(SootMethod sm : sc.getMethods()) {
				if(androidAPIEntryPoints.contains(sm)) {
					String name = sm.getName();
					if(name.equals("create")) {
						exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {true,true,false}, null, false));
						androidAPIEntryPoints.remove(sm);
					} else if(name.equals("deselectTrack") || name.equals("prepare") || name.equals("reset") || name.equals("selectTrack")) {
						synchronized(nonAPISinkMethods) {
							nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
						}
						androidAPIEntryPoints.remove(sm);
					}
				}
			}
		}
		}
		
		{
		Set<SootMethod> contextMethods = HierarchyHelpers.getAllImplementingMethods(Scene.v().getSootClass("android.media.MediaDataSource"));
		for(SootMethod sm : contextMethods) {
			if(sm.getName().equals("readAt") && androidAPIEntryPoints.contains(sm)) {
				exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {false,true,false}, null, false));
			} else {
				synchronized(nonAPISinkMethods) {
					nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
				}
			}
			androidAPIEntryPoints.remove(sm);
		}
		}
		
		{
		Set<SootMethod> contextMethods = HierarchyHelpers.getAllImplementingMethods(Scene.v().getSootClass("android.media.midi.MidiReceiver"));
		for(SootMethod sm : contextMethods) {
			if((sm.getName().equals("onFlush") || sm.getName().equals("onSend") || sm.getName().equals("flush") || sm.getName().equals("send")) && androidAPIEntryPoints.contains(sm)) {
				exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {false,true,false}, null, false));
			} else {
				synchronized(nonAPISinkMethods) {
					nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
				}
			}
			androidAPIEntryPoints.remove(sm);
		}
		}
		
		{
		Set<SootMethod> contextMethods = HierarchyHelpers.getAllImplementingMethods(Scene.v().getSootClass("android.os.ParcelFileDescriptor"));
		for(SootMethod sm : contextMethods) {
			boolean isAPI = androidAPIEntryPoints.contains(sm);
			if(sm.getName().equals("dup") || sm.getName().equals("getStatSize") || sm.getName().equals("seekTo")) {
				if(isAPI)
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {false,true,false}, null, false));
				else
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {false,true,false}, nonAPISinkMethods, true));
			} else if((sm.getName().equals("open") || sm.getName().equals("createReliableSocketPair") ||  sm.getName().equals("createSocketPair"))) {
				if(isAPI)
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {true, false,false}, null, false));
				else
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {true,false,false}, nonAPISinkMethods, true));
			} else if(sm.getName().equals("getFile")) {
				if(isAPI)
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {true, true,false}, null, false));
				else
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {true,true,false}, nonAPISinkMethods, true));
			} else {
				synchronized(nonAPISinkMethods) {
					nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
				}
			}
			androidAPIEntryPoints.remove(sm);
		}
		}
		
		{
		Set<SootMethod> contextMethods = HierarchyHelpers.getAllImplementingMethods(Scene.v().getSootClass("android.os.RecoverySystem"));
		for(SootMethod sm : contextMethods) {
			if(sm.getName().equals("installPackage") || sm.getName().equals("processPackage") || sm.getName().startsWith("verifyPackage")) {
				if(androidAPIEntryPoints.contains(sm))
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {true,true,false}, null, false));
				else
					exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {true,true,false}, nonAPISinkMethods, true));
			} else {
				synchronized(nonAPISinkMethods) {
					nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
				}
			}
			androidAPIEntryPoints.remove(sm);
		}
		}
		
		{
		for(SootClass sc : Scene.v().getClasses()) {
			if(sc.getPackageName().startsWith("android.provider")) {
				for(SootMethod sm : sc.getMethods()) {
					if(androidAPIEntryPoints.contains(sm)) {
						exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {true,true,false}, null, false));
						androidAPIEntryPoints.remove(sm);
					}
				}
			}
		}
		}
		
		{
		SootClass sc = Scene.v().getSootClassUnsafe("android.speech.tts.TextToSpeech",false);
		if(sc != null) {
			for(SootMethod sm : sc.getMethods()) {
				if(androidAPIEntryPoints.contains(sm)) {
					String name = sm.getName();
					if(name.equals("synthesizeToFile")) {
						exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {true,true,false}, null, false));
					} else {
						synchronized(nonAPISinkMethods) {
							nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
						}
					}
					androidAPIEntryPoints.remove(sm);
				}
			}
		}
		}
		
		{// Set all dump and shell methods to access only as they do not open files just write/read from them (assumes these are the only methods left from binder in the api methods)
		Set<SootMethod> allBinderMethods = new HashSet<>();
		SootClass bc = Scene.v().getSootClassUnsafe("android.os.Binder", false);
		if(bc != null)
			allBinderMethods.addAll(HierarchyHelpers.getAllImplementingMethods(bc));
		bc = Scene.v().getSootClassUnsafe("android.os.IBinder", false);
		if(bc != null)
			allBinderMethods.addAll(HierarchyHelpers.getAllImplementingMethods(bc));
		bc = Scene.v().getSootClassUnsafe("android.os.BinderProxy", false);
		if(bc != null)
			allBinderMethods.addAll(HierarchyHelpers.getAllImplementingMethods(bc));
		for(SootMethod sm : allBinderMethods) {
			if(androidAPIEntryPoints.contains(sm)) {
				exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, new boolean[] {false,true,false}, null, false));
				androidAPIEntryPoints.remove(sm);
			}
		}
		}
		
		{//Set default access by signature
		Map<String,boolean[]> sigs = new HashMap<>();
		sigs.put("<android.database.sqlite.SQLiteDirectCursorDriver: android.database.Cursor query(android.database.sqlite.SQLiteDatabase$CursorFactory,java.lang.String[])>", new boolean[] {false,true,false});
		sigs.put("<android.database.DefaultDatabaseErrorHandler: void onCorruption(android.database.sqlite.SQLiteDatabase)>", new boolean[] {false,true,true});
		sigs.put("<android.content.ContentProviderOperation: android.content.ContentProviderResult apply(android.content.ContentProvider,android.content.ContentProviderResult[],int)>", new boolean[] {true,true,false});
		for(Iterator<SootMethod> it = androidAPIEntryPoints.iterator(); it.hasNext();) {
			SootMethod sm = it.next();
			boolean[] defaultTypes = sigs.get(sm.getSignature());
			if(defaultTypes != null) {
				exe.execute(new DiscoverFileMethodsRunner(sm, sinks, FileMethod.androidAPIStr, defaultTypes, null, false));
				it.remove();
			}
		}
		}
		
		{//Remove lambda api methods
		for(Iterator<SootMethod> it = androidAPIEntryPoints.iterator(); it.hasNext();) {
			SootMethod sm = it.next();
			if(sm.getDeclaringClass().getShortName().startsWith("'") || sm.getDeclaringClass().getShortName().startsWith("-") || sm.getDeclaringClass().getShortName().contains("Lambda")) {
				synchronized(nonAPISinkMethods) {
					nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
				}
				it.remove();
			}
		}
		}
		
		exe.awaitCompletion();
		
		return nonAPISinkMethods;
	}
	
	
	/* Return map of sootmethod to action type. The map will include all methods that are not api methods but are 
	 * known to indicate a action type.
	 * 
	 */
	private Map<SootMethod,boolean[]> addKnownJavaFileMethods(Set<SootMethod> javaAPIEntryPoints, CountingThreadExecutor exe) throws Exception {
		
		Map<SootMethod,FileMethod> nativeMethods = new HashMap<>();
		for(FileMethod fm : db.getNativeMethods()) {
			nativeMethods.put(fm.getSootMethod(),fm);
		}
		nativeMethods = ImmutableMap.copyOf(nativeMethods);
		
		SootClass outputStreamClass = Scene.v().getSootClass("java.io.OutputStream");
		SootClass writerClass = Scene.v().getSootClass("java.io.Writer");
		SootClass inputStreamClass = Scene.v().getSootClass("java.io.InputStream");
		SootClass readerClass = Scene.v().getSootClass("java.io.Reader");
		SootClass readableClass = Scene.v().getSootClass("java.lang.Readable");
		SootClass xmlReaderClass = Scene.v().getSootClass("org.xml.sax.XMLReader");
		SootClass xmlParserClass = Scene.v().getSootClass("org.xml.sax.Parser");
		
		Map<SootMethod,boolean[]> nonAPISinkMethods = new HashMap<>();
		
		Set<SootMethod> knownJavaWriteMethods = HierarchyHelpers.getAllImplementingMethods(outputStreamClass);
		knownJavaWriteMethods.addAll(HierarchyHelpers.getAllImplementingMethods(writerClass));
		for(SootMethod sm : knownJavaWriteMethods) {
			if(javaAPIEntryPoints.contains(sm)) {
				if(!sm.getName().equals("close")) {
					exe.execute(new DiscoverFileMethodsRunner(sm, nativeMethods, FileMethod.javaAPIStr, new boolean[] {false,true,false}, null, false));
				} else {
					synchronized(nonAPISinkMethods) {
						nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
					}
				}
				javaAPIEntryPoints.remove(sm);
			} else {
				exe.execute(new DiscoverFileMethodsRunner(sm, nativeMethods, FileMethod.javaAPIStr, new boolean[] {false,true,false}, nonAPISinkMethods, true));
			}
		}
		
		Set<SootMethod> knownJavaReadMethods = HierarchyHelpers.getAllImplementingMethods(inputStreamClass);
		knownJavaReadMethods.addAll(HierarchyHelpers.getAllImplementingMethods(readerClass));
		knownJavaReadMethods.addAll(HierarchyHelpers.getAllImplementingMethods(readableClass));
		for(SootMethod sm : knownJavaReadMethods) {
			if(javaAPIEntryPoints.contains(sm)) {
				if(!sm.getName().equals("close") && !sm.getName().equals("mark") 
						&& !sm.getName().equals("markSupported") && !sm.getName().equals("reset")) {
					exe.execute(new DiscoverFileMethodsRunner(sm, nativeMethods, FileMethod.javaAPIStr, new boolean[] {false,true,false}, null, false));
				} else {
					synchronized(nonAPISinkMethods) {
						nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
					}
				}
				javaAPIEntryPoints.remove(sm);
			} else {
				exe.execute(new DiscoverFileMethodsRunner(sm, nativeMethods, FileMethod.javaAPIStr, new boolean[] {false,true,false}, nonAPISinkMethods, true));
			}
		}
		
		knownJavaReadMethods = HierarchyHelpers.getAllImplementingMethods(xmlReaderClass);
		knownJavaReadMethods.addAll(HierarchyHelpers.getAllImplementingMethods(xmlParserClass));
		knownJavaReadMethods.add(Scene.v().getMethod("<org.xmlpull.v1.sax2.Driver: void parseSubTree(org.xmlpull.v1.XmlPullParser)>"));
		knownJavaReadMethods.add(Scene.v().getMethod("<org.apache.harmony.xml.parsers.DocumentBuilderImpl: org.w3c.dom.Document parse(org.xml.sax.InputSource)>"));
		for(SootMethod sm : knownJavaReadMethods) {
			if(javaAPIEntryPoints.contains(sm)) {
				if(sm.getName().equals("parse")) {
					Type firstParm = sm.getParameterCount() >= 1 ? sm.getParameterType(0) : null;
					if(firstParm != null && firstParm.toString().equals("org.xmlpull.v1.XmlPullParser"))
						exe.execute(new DiscoverFileMethodsRunner(sm, nativeMethods, FileMethod.javaAPIStr, new boolean[] {false,true,false}, null, false));
					else
						exe.execute(new DiscoverFileMethodsRunner(sm, nativeMethods, FileMethod.javaAPIStr, new boolean[] {true,true,false}, null, false));
				} else {
					synchronized(nonAPISinkMethods) {
						nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
					}
				}
				javaAPIEntryPoints.remove(sm);
			} else {
				exe.execute(new DiscoverFileMethodsRunner(sm, nativeMethods, FileMethod.javaAPIStr, new boolean[] {false,true,false}, nonAPISinkMethods, true));
			}
		}
		
		Set<SootClass> knownJavaFileMethodClasses = new HashSet<>();
		knownJavaFileMethodClasses.addAll(HierarchyHelpers.getAllSubClasses(outputStreamClass));
		knownJavaFileMethodClasses.addAll(HierarchyHelpers.getAllSubClasses(writerClass));
		knownJavaFileMethodClasses.addAll(HierarchyHelpers.getAllSubClasses(inputStreamClass));
		knownJavaFileMethodClasses.addAll(HierarchyHelpers.getAllSubClasses(readerClass));
		knownJavaFileMethodClasses.addAll(HierarchyHelpers.getAllSubClassesOfInterface(readableClass));
		knownJavaFileMethodClasses.add(Scene.v().getSootClass("java.io.RandomAccessFile"));
		for(SootClass sc : knownJavaFileMethodClasses) {
			for(SootMethod sm : sc.getMethods()) {
				if(javaAPIEntryPoints.contains(sm) && sm.getName().equals("<init>")) {
					Type firstParm = sm.getParameterCount() >= 1 ? sm.getParameterType(0) : null;
					if(firstParm != null && (firstParm.toString().equals("java.io.File") 
						|| (firstParm.toString().equals("java.lang.String") && !sc.toString().equals("java.io.StringBufferInputStream") 
								&& !sc.toString().equals("java.io.StringReader")))) {
						exe.execute(new DiscoverFileMethodsRunner(sm, nativeMethods, FileMethod.javaAPIStr, new boolean[] {true,false,false}, null, false));
					} else {
						synchronized(nonAPISinkMethods) {
							nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
						}
					}
					javaAPIEntryPoints.remove(sm);
				}
			}
		}
		
		knownJavaFileMethodClasses = new HashSet<>();
		knownJavaFileMethodClasses.add(Scene.v().getSootClass("javax.xml.parsers.SAXParser"));
		knownJavaFileMethodClasses.add(Scene.v().getSootClass("javax.xml.parsers.DocumentBuilder"));
		for(SootClass sc : knownJavaFileMethodClasses) {
			for(SootMethod sm : sc.getMethods()) {
				if(javaAPIEntryPoints.contains(sm) && sm.getName().equals("<init>")) {
					Type firstParm = sm.getParameterCount() >= 1 ? sm.getParameterType(0) : null;
					if(firstParm != null && firstParm.toString().equals("java.io.InputStream")) {
						exe.execute(new DiscoverFileMethodsRunner(sm, nativeMethods, FileMethod.javaAPIStr, new boolean[] {false,true,false}, null, false));
					} else {
						exe.execute(new DiscoverFileMethodsRunner(sm, nativeMethods, FileMethod.javaAPIStr, new boolean[] {true,true,false}, null, false));
					}
					javaAPIEntryPoints.remove(sm);
				}
			}
		}
		
		// Properties, preferences, and manifest are all access methods
		knownJavaFileMethodClasses = new HashSet<>();
		knownJavaFileMethodClasses.add(Scene.v().getSootClass("java.util.Properties"));
		knownJavaFileMethodClasses.add(Scene.v().getSootClass("java.util.prefs.Preferences"));
		knownJavaFileMethodClasses.add(Scene.v().getSootClass("java.util.jar.Manifest"));
		//Anything remaining after Writer and OutputStream subclassesses are processed above are access only
		knownJavaFileMethodClasses.add(Scene.v().getSootClass("java.io.PrintWriter")); 
		knownJavaFileMethodClasses.add(Scene.v().getSootClass("java.io.PrintStream"));
		for(SootClass sc : knownJavaFileMethodClasses) {
			for(SootMethod sm : sc.getMethods()) {
				if(javaAPIEntryPoints.contains(sm) ) {
					exe.execute(new DiscoverFileMethodsRunner(sm, nativeMethods, FileMethod.javaAPIStr, new boolean[] {false,true,false}, null, false));
					javaAPIEntryPoints.remove(sm);
				}
			}
		}
		
		//Special handling for methods that just wont resolve correctly
		Set<SootClass> fileClasses = new HashSet<>();
		fileClasses.add(Scene.v().getSootClass("sun.nio.ch.SocketChannelImpl"));
		fileClasses.add(Scene.v().getSootClass("java.net.Socket"));
		fileClasses.add(Scene.v().getSootClass("java.net.ServerSocket"));
		fileClasses.add(Scene.v().getSootClass("java.net.SocksSocketImpl"));
		fileClasses.add(Scene.v().getSootClass("java.net.DatagramSocket"));
		fileClasses.add(Scene.v().getSootClass("java.net.AbstractPlainDatagramSocketImpl"));
		fileClasses.add(Scene.v().getSootClass("java.net.AbstractPlainSocketImpl"));
		fileClasses.add(Scene.v().getSootClass("sun.nio.ch.SocketAdaptor"));
		fileClasses.add(Scene.v().getSootClass("sun.nio.ch.ServerSocketChannelImpl"));
		fileClasses.add(Scene.v().getSootClass("sun.nio.ch.ServerSocketAdaptor"));
		fileClasses.add(Scene.v().getSootClass("sun.nio.ch.DatagramSocketAdaptor"));
		fileClasses.add(Scene.v().getSootClass("sun.nio.ch.DatagramChannelImpl"));
		fileClasses.add(Scene.v().getSootClass("java.nio.file.Files"));
		fileClasses.add(Scene.v().getSootClass("sun.nio.fs.UnixSecureDirectoryStream"));
		fileClasses.add(Scene.v().getSootClass("java.util.jar.JarInputStream"));
		fileClasses.add(Scene.v().getSootClass("java.util.jar.JarFile$JarFileEntry"));
		fileClasses.add(Scene.v().getSootClass("java.util.jar.JarFile"));
		fileClasses.add(Scene.v().getSootClass("java.util.Formatter"));
		fileClasses.addAll(HierarchyHelpers.getAllSubClasses(Scene.v().getSootClass("java.nio.file.spi.FileSystemProvider")));
		for(SootClass sc : fileClasses) {
			for(SootMethod sm : sc.getMethods()) {
				if(javaAPIEntryPoints.contains(sm)) {
					boolean[] actions = null;
					if(sm.getName().equals("copy") || sm.getName().equals("createFile") || sm.getName().equals("createTempDirectory")
							|| sm.getName().equals("createTempFile") || sm.getName().equals("lines") || sm.getName().equals("readAllBytes")
							|| sm.getName().equals("readAllLines") || sm.getName().equals("write")
							|| sm.getName().equals("send") || sm.getName().equals("receive") 
							|| (sc.toString().equals("java.nio.file.Files") && sm.getName().equals("list"))) {
						actions = new boolean[]{true,true,false};
					} else if(sm.getName().equals("delete") || sm.getName().equals("deleteIfExists")) {
						actions = new boolean[]{false,true,true};
					} else if(sm.getName().equals("newBufferedReader") || sm.getName().equals("newBufferedWriter") || sm.getName().equals("newByteChannel")
							|| sm.getName().equals("newDirectoryStream") || sm.getName().equals("newInputStream") || sm.getName().equals("newOutputStream")
							|| sm.getName().equals("newFileChannel") || sm.getName().equals("newAsynchronousFileChannel") || sm.getName().equals("connect")
							|| sm.getName().equals("bind") || sm.getName().equals("getOutputStream")
							|| (sm.getName().equals("getInputStream") && !sc.toString().equals("java.util.jar.JarFile"))
							|| (sc.toString().equals("java.net.Socket") && sm.getName().equals("<init>") && sm.getParameterCount() > 0)
							|| (sc.toString().equals("java.net.ServerSocket") && sm.getName().equals("<init>") && sm.getParameterCount() > 0)) {
						actions = new boolean[]{true,false,false};
					} else if(sm.getName().equals("sendUrgentData") || sm.getName().equals("accept") || sm.getName().equals("implAccept")
							|| sm.getName().equals("getNextEntry") || sm.getName().equals("getNextJarEntry") || sm.getName().equals("getAttributes")
							|| sm.getName().equals("getCertificates") || sm.getName().equals("getCodeSigners") || sm.getName().equals("getManifest")
							|| (sc.toString().equals("java.util.jar.JarFile") && sm.getName().equals("getInputStream"))
							|| sm.getName().equals("format")
							) {
						actions = new boolean[]{false,true,false};
					} else if(sm.getName().equals("finishConnect") || sm.getName().equals("getKeepAlive") || sm.getName().equals("getInetAddress")
							|| sm.getName().equals("getLocalAddress") || sm.getName().equals("getLocalPort") || sm.getName().equals("getLocalSocketAddress")
							|| sm.getName().equals("getOOBInline") || sm.getName().equals("getPort") || sm.getName().equals("getReceiveBufferSize")
							|| sm.getName().equals("getRemoteSocketAddress") || sm.getName().equals("getReuseAddress") || sm.getName().equals("getSendBufferSize")
							|| sm.getName().equals("getSoLinger") || sm.getName().equals("getSoTimeout") || sm.getName().equals("getTcpNoDelay")
							|| sm.getName().equals("getTrafficClass") || sm.getName().equals("setKeepAlive") || sm.getName().equals("setOOBInline")
							|| sm.getName().equals("setReceiveBufferSize") || sm.getName().equals("setReuseAddress") || sm.getName().equals("setSendBufferSize")
							|| sm.getName().equals("setSoLinger") || sm.getName().equals("setSoTimeout") || sm.getName().equals("setTcpNoDelay")
							|| sm.getName().equals("setTrafficClass") || sm.getName().equals("shutdownInput") || sm.getName().equals("shutdownOutput")
							|| sm.getName().equals("close") || sm.getName().equals("disconnect")) {
						synchronized(nonAPISinkMethods) {
							nonAPISinkMethods.put(sm, new boolean[] {false,false,false});
						}
						javaAPIEntryPoints.remove(sm);
					}
					if(actions != null) {
						db.add(sm, actions, FileMethod.javaAPIStr);
						javaAPIEntryPoints.remove(sm);
					}
				}
			}
		}
		
		exe.awaitCompletion();
		
		return nonAPISinkMethods;
	}
	
	private static class JavaAPIEdgeFilter extends Filter {
		
		public JavaAPIEdgeFilter(Set<SootClass> androidClasses, IExcludeHandler excludeHandler, Set<SootMethod> sinks, Set<SootMethod> nonAPISinks) {
			super(new EdgePredicate() {
				@Override
				public boolean want(Edge e) {
					if(e.kind().isExplicit() && !excludeHandler.isExcludedMethod(e.src()) && !sinks.contains(e.src()) && !nonAPISinks.contains(e.src())) {
						if(androidClasses.contains(e.tgt().getDeclaringClass())) {
							return androidClasses.contains(e.srcStmt().getInvokeExpr().getMethodRef().declaringClass());
						}
						return true;
					}
					return false;
				}
			});
		}
		
	}
	
	private static class AndroidAPIEdgeFilter extends Filter {
		public AndroidAPIEdgeFilter(Set<SootMethod> binderRelatedMethods, Set<SootClass> androidClasses, IExcludeHandler excludeHandler, 
				Set<SootMethod> sinks, Set<SootMethod> nonAPISinks) {
			super(new EdgePredicate() {
				@Override
				public boolean want(Edge e) {
					return e.kind().isExplicit() && androidClasses.contains(e.src().getDeclaringClass()) 
							&& !excludeHandler.isExcludedMethod(e.src()) && !sinks.contains(e.src())
							&& !nonAPISinks.contains(e.src()) && !binderRelatedMethods.contains(e.src());
				}
			});
		}
	}
	
	private final Filter getFilter(String apiType, SootMethod ep, Set<SootMethod> sinks, Set<SootMethod> nonAPISinks) {
		if(apiType.equals(FileMethod.javaAPIStr)) {
			return new JavaAPIEdgeFilter(javaAPIIndicatorDB.getSootExcludedClasses(), excludeDB.createNewExcludeHandler(new EntryPoint(ep,ep.getDeclaringClass())), sinks, nonAPISinks);
		} else {
			Set<SootMethod> binderRelatedMethods = new HashSet<>();
			binderRelatedMethods.addAll(dataAccessor.getEntryPointsAsSootMethods());
			binderRelatedMethods.addAll(dataAccessor.getBinderInterfaceMethodsToEntryPoints().keySet());
			binderRelatedMethods.addAll(dataAccessor.getBinderProxyMethodsToEntryPoints().keySet());
			return new AndroidAPIEdgeFilter(binderRelatedMethods, javaAPIIndicatorDB.getSootExcludedClasses(), 
					excludeDB.createNewExcludeHandler(new EntryPoint(ep,ep.getDeclaringClass())), sinks, nonAPISinks);
		}
	}
	
	private class DiscoverFileMethodsRunner implements Runnable {
		
		private final SootMethod ep;
		private final CallGraph cg;
		private final Map<SootMethod,FileMethod> sinkMethods;
		private final Filter filter;
		private final boolean[] overrideActions;
		private final String apiType;
		private final Map<SootMethod,boolean[]> nonAPISinkMethods;
		private final boolean genNonAPISinkMethods;
		
		public DiscoverFileMethodsRunner(SootMethod ep, Map<SootMethod,FileMethod> sinkMethods, String apiType, 
				boolean[] overrideActions, Map<SootMethod,boolean[]> nonAPISinkMethods, boolean genNonAPISinkMethods) {
			this.ep = ep;
			this.cg = Scene.v().getCallGraph();
			this.sinkMethods = sinkMethods;
			this.apiType = apiType;
			this.filter = getFilter(apiType, ep, sinkMethods.keySet(), 
					(nonAPISinkMethods == null || genNonAPISinkMethods) ? ImmutableSet.of() : nonAPISinkMethods.keySet());
			this.overrideActions = overrideActions;
			this.nonAPISinkMethods = nonAPISinkMethods;
			this.genNonAPISinkMethods = genNonAPISinkMethods;
		}

		@Override
		public void run() {
			try {
				Set<FileMethod> sinks = new HashSet<>();
				boolean[] actions = {false,false,false};
				Set<SootMethod> visited = new HashSet<>();
				Queue<SootMethod> toVisit = new ArrayDeque<>();
				boolean foundSink = false;
				Set<SootMethod> visitedMethodsWithEdges = new HashSet<>();
				
				toVisit.add(ep);
				while(!toVisit.isEmpty()) {
					SootMethod cur = toVisit.poll();
					if(visited.add(cur)) {
						FileMethod fm = sinkMethods.get(cur);
						if(fm != null) {
							foundSink = true;
							sinks.add(fm);
							actions[0] = actions[0] || fm.opens();
							actions[1] = actions[1] || fm.accesses();
							actions[2] = actions[2] || fm.removes();
							if(overrideActions != null)
								break;
						}
						if(!genNonAPISinkMethods && nonAPISinkMethods != null) {
							boolean[] curActions = nonAPISinkMethods.get(cur);
							if(curActions != null && (curActions[0] || curActions[1] || curActions[2])) {
								actions[0] = actions[0] || curActions[0];
								actions[1] = actions[1] || curActions[1];
								actions[2] = actions[2] || curActions[2];
								foundSink = true;
								if(overrideActions != null)
									break;
							}
						}
						
						boolean hasEdges = false;
						Iterator<Edge> it = filter.wrap(cg.edgesOutOf(cur));
						while(it.hasNext()) {
							Edge e = it.next();
							toVisit.add(e.tgt());
							hasEdges = true;
						}
						
						if(hasEdges)
							visitedMethodsWithEdges.add(cur);
					}
				}
				
				if(genNonAPISinkMethods) {
					if(!foundSink || overrideActions == null)
						actions = new boolean[]{false,false,false};
					else
						actions = overrideActions;
					if(nonAPISinkMethods != null) {
						synchronized(nonAPISinkMethods) {
							nonAPISinkMethods.put(ep, actions);
						}
					}
				} else {
					if(foundSink) {
						if(overrideActions == null) {
							FileMethod m = db.add(ep, actions, apiType);
							if(!sinks.isEmpty())
								m.setSinks(SortingMethods.sortSet(sinks));
						} else {
							db.add(ep, overrideActions, apiType);
						}
					}
				}
				
				if(debugAndroidEnabled || debugJavaEnabled) {
					Map<SootMethod, AtomicInteger> commonMethods;
					if(apiType.equals(FileMethod.javaAPIStr))
						commonMethods = commonJavaMethods;
					else
						commonMethods = commonAndroidMethods;
					synchronized(commonMethods) {
						for(SootMethod m : visitedMethodsWithEdges) {
							AtomicInteger i = commonMethods.get(m);
							if(i == null) {
								commonMethods.put(m, new AtomicInteger(1));
							} else {
								i.incrementAndGet();
							}
						}
					}
				}
				
			} catch(IgnorableRuntimeException t) {
				throw t;
			} catch(Throwable t) {
				logger.fatal("{}: An unexpected exception when determining if method '{}' is a file method.",t,cn,ep);
				throw new IgnorableRuntimeException();
			}
			
		}
		
	}
	
	public final GraphFormatRunner getFormatter(SootMethod ep, Map<SootMethod,FileMethod> sinkMethods, String apiType, 
			Map<SootMethod,boolean[]> nonAPISinkMethods) throws Exception {
		Path outputPathDir;
		if(apiType.equals(FileMethod.javaAPIStr)) {
			outputPathDir = debugJavaGraphsDir;
		} else {
			outputPathDir = debugAndroidGraphsDir;
		}
		Path outputPath = FileHelpers.getPath(outputPathDir, FileHelpers.getHashOfString("MD5", ep.toString()) + ".xml");
		nonAPISinkMethods = (nonAPISinkMethods == null) ? ImmutableMap.of() : nonAPISinkMethods;
		return new GraphFormatRunner(ep, sinkMethods, apiType, nonAPISinkMethods, outputPath);
	}
	
	private class GraphFormatRunner extends Formatter implements Runnable {
		
		private final SootMethod ep;
		private final Map<SootMethod, AlNode> nodes;
		private final Map<Pair<SootMethod,SootMethod>,AlEdge> edges;
		private final Filter filter;

		private GraphFormatRunner(SootMethod ep, Map<SootMethod,FileMethod> sinkMethods, String apiType, 
				Map<SootMethod,boolean[]> nonAPISinkMethods, Path outputPath) {
			super(-1, -1, -1, -1, outputPath);
			this.ep = ep;
			this.filter = getFilter(apiType, ep, sinkMethods.keySet(), nonAPISinkMethods.keySet());
			this.nodes = new HashMap<>();
			this.edges = new HashMap<>();
		}
		
		@Override
		public void run() {
			try {
				format();
				GraphmlGenerator.outputGraphStatic(this);
			} catch(IgnorableRuntimeException t) {
				throw t;
			} catch(Throwable t) {
				logger.fatal("{}: An unexpected exception when outputting a graph for method '{}'.",t,cn,ep);
				throw new IgnorableRuntimeException();
			}
		}
		
		@Override
		public String getComment() {
			StringBuilder sb = new StringBuilder();
			sb.append(super.getComment());
			sb.append("  Type: File Method Call Graph\n");
			sb.append("  Entry Point: ").append(ep.getSignature()).append("\n");
			return sb.toString();
		}

		@Override
		public Collection<AlNode> getNodes() {
			return SortingMethods.sortSet(new HashSet<>(nodes.values()));
		}

		@Override
		public Collection<AlEdge> getEdges() {
			return SortingMethods.sortSet(new HashSet<>(edges.values()));
		}

		@Override
		public void format() {
			int id = 0;
			Queue<SootMethod> tovisit = new ArrayDeque<SootMethod>();
			Queue<Integer> depthCount = new ArrayDeque<Integer>();
			Set<SootMethod> visited = new HashSet<SootMethod>();
			CallGraph cg = Scene.v().getCallGraph();
			tovisit.add(ep);
			depthCount.add(1);
			while(!tovisit.isEmpty()){
				SootMethod currMeth = tovisit.poll();
				int curDepth = depthCount.poll();
				
				//if(visited.add(currMeth)) {
				if(visited.add(currMeth) && curDepth < 5) {
					AlNode curNode = nodes.get(currMeth);
					if(curNode == null) {
						curNode = new AlNode(id++,currMeth.getSignature());
						nodes.put(currMeth, curNode);
					}
					
					Iterator<Edge> itEdge = filter.wrap(cg.edgesOutOf(currMeth));
					while(itEdge.hasNext()) {
						Edge e = itEdge.next();
						SootMethod tgt = e.tgt();
						
						Pair<SootMethod,SootMethod> p = new Pair<>(currMeth,tgt);
						AlEdge edge = edges.get(p);
						if(edge == null) {
							AlNode tgtNode = nodes.get(tgt);
							if(tgtNode == null) {
								tgtNode = new AlNode(id++,tgt.getSignature());
								nodes.put(tgt, tgtNode);
							}
							edge = new AlEdge(id++, curNode, tgtNode);
							edges.put(p,edge);
						}
						
						tovisit.add(tgt);	
						depthCount.add(curDepth+1);
					}
				}
				
			}
		}
	}
	
	private void getSomePathsToSink(SootMethod sink, Set<SootMethod> apisWithSinks, Set<SootMethod> sinks, 
			Set<SootMethod> nonAPISinks, Path outputPathDir, String apiStr) throws Exception {
		List<List<SootMethod>> paths = new ArrayList<>();
		Set<SootMethod> visited = new HashSet<>();
		Queue<SootMethod> toVisit = new ArrayDeque<>();
		Queue<List<SootMethod>> pathsToVisit = new ArrayDeque<>();
		CallGraph cg = Scene.v().getCallGraph();
		Filter filter = getFilter(apiStr, sink, sinks, nonAPISinks);
		
		toVisit.add(sink);
		pathsToVisit.add(ImmutableList.of(sink));
		while(!toVisit.isEmpty()) {
			SootMethod cur = toVisit.poll();
			List<SootMethod> path = pathsToVisit.poll();
			if(visited.add(cur)) {
				if(apisWithSinks.contains(cur)) {
					paths.add(path);
				}
				
				Iterator<Edge> it = filter.wrap(cg.edgesInto(cur));
				while(it.hasNext()) {
					Edge e = it.next();
					toVisit.add(e.src());
					pathsToVisit.add(ImmutableList.<SootMethod>builder().addAll(path).add(e.src()).build());
				}
			}
		}
		
		Collections.sort(paths, new Comparator<List<SootMethod>>() {
			public int compare(List<SootMethod> o1, List<SootMethod> o2) {
				int size = Math.min(o1.size(), o2.size());
				for(int i = 0; i < size; i++) {
					SootMethod sm1 = o1.get(i);
					SootMethod sm2 = o2.get(i);
					int ret = SootSort.smComp.compare(sm1, sm2);
					if(ret != 0)
						return ret;
				}
				return Integer.compare(o1.size(), o2.size());
			}
		});
		Path outputPath = FileHelpers.getPath(outputPathDir, FileHelpers.getHashOfString("MD5", sink.toString()) + ".txt");
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(outputPath))) {
			for(List<SootMethod> path : paths) {
				boolean first = true;
				for(SootMethod sm : path) {
					if(first)
						first = false;
					else
						ps.print(" --> ");
					ps.print(sm);
				}
				ps.println();
			}
		}
	}
	
	private void doDebug(Set<FileMethod> fileMethodsAPINoNative, Map<SootMethod,FileMethod> sinks, 
			Map<SootMethod,boolean[]> nonAPISinkMethods, CountingThreadExecutor exe, String apiStr) throws Exception {
		Path debugDir;
		Path debugGraphsDir;
		Path debugPathsDir;
		Path debugCommonCGMethodsFile;
		Map<SootMethod,AtomicInteger> commonMethods;
		if(apiStr.equals(FileMethod.javaAPIStr)) {
			debugDir = debugJavaDir;
			debugGraphsDir = debugJavaGraphsDir;
			debugPathsDir = debugJavaPathsDir; 
			debugCommonCGMethodsFile = debugJavaCommonCGMethodsFile; 
			commonMethods = commonJavaMethods;
		} else {
			debugDir = debugAndroidDir;
			debugGraphsDir = debugAndroidGraphsDir;
			debugPathsDir = debugAndroidPathsDir; 
			debugCommonCGMethodsFile = debugAndroidCommonCGMethodsFile; 
			commonMethods = commonAndroidMethods;
		}
		
		FileHelpers.processDirectory(debugDir, true, true);
		
		//Get some of the paths from all APIs to the sinks the reference
		FileHelpers.processDirectory(debugPathsDir, true, false);
		Set<SootMethod> sinksReferencedByAPIs = new HashSet<>();
		Set<SootMethod> APINoNativeWithSinks = new HashSet<>();
		for(FileMethod fm : fileMethodsAPINoNative) {
			Set<FileMethod> localSinks = fm.getSinks();
			if(localSinks != null && !localSinks.isEmpty()) {
				for(FileMethod ls : localSinks) {
					sinksReferencedByAPIs.add(ls.getSootMethod());
				}
				APINoNativeWithSinks.add(fm.getSootMethod());
			}
		}
		final Set<SootMethod> allSinks = ImmutableSet.copyOf(sinks.keySet());
		final Set<SootMethod> nonAPISinks = ImmutableSet.copyOf(nonAPISinkMethods.keySet());
		for(final SootMethod sink : sinksReferencedByAPIs) {
			exe.execute(new Runnable() {
				public void run() {
					try {
						getSomePathsToSink(sink, APINoNativeWithSinks, 
								allSinks, nonAPISinks, debugPathsDir, apiStr);
					} catch(IgnorableRuntimeException t) {
						throw t;
					} catch(Throwable t) {
						logger.fatal("{}: An unexpected exception when getting paths to sink '{}'.",t,cn,sink);
						throw new IgnorableRuntimeException();
					}
				}
			});
		}
		
		//Dump graph representations of the call graphs for the api methods with sinks
		FileHelpers.processDirectory(debugGraphsDir, true, false);
		for(FileMethod fm : fileMethodsAPINoNative) {
			//Only output graphs for those java apis that we actually scanned without a default policy
			Set<FileMethod> localSinks = fm.getSinks();
			if(localSinks != null && !localSinks.isEmpty()) {
				exe.execute(getFormatter(fm.getSootMethod(), sinks, apiStr, nonAPISinkMethods));
			}
			
		}
		
		//Output a file containing all the methods with edges in the call graph ordered by how may apis they occur on
		commonMethods = SortingMethods.sortMapValue(commonMethods, new Comparator<AtomicInteger>() {
			public int compare(AtomicInteger o1, AtomicInteger o2) {
				return Integer.compare(o2.get(), o1.get());
			}
		});
		int digits = digits(commonMethods.values().iterator().next().get());
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(debugCommonCGMethodsFile))) {
			for(SootMethod sm : commonMethods.keySet()) {
				ps.println(padNum(commonMethods.get(sm).get(), digits) + " " + sm.getSignature());
			}
		}
		
		exe.awaitCompletion();
	}

}
