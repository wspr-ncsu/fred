package org.sag.woof.phases.woof;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sag.common.concurrent.CountingThreadExecutor;
import org.sag.common.concurrent.IgnorableRuntimeException;
import org.sag.common.logging.ILogger;
import org.sag.common.tuple.Pair;
import org.sag.common.tuple.Triple;
import org.sag.woof.database.filepaths.parts.AnyComboPart;
import org.sag.woof.database.filepaths.parts.AnyInfoPart;
import org.sag.woof.database.filepaths.parts.AnyPart;
import org.sag.woof.database.filepaths.parts.AnyPartImpl;
import org.sag.woof.database.filepaths.parts.AppendPart;
import org.sag.woof.database.filepaths.parts.BranchPart;
import org.sag.woof.database.filepaths.parts.ConstantPart;
import org.sag.woof.database.filepaths.parts.EnvVarPart;
import org.sag.woof.database.filepaths.parts.LeafPart;
import org.sag.woof.database.filepaths.parts.LoopPart;
import org.sag.woof.database.filepaths.parts.NamePart;
import org.sag.woof.database.filepaths.parts.NormalizePart;
import org.sag.woof.database.filepaths.parts.OrPart;
import org.sag.woof.database.filepaths.parts.PHArgumentValuePart;
import org.sag.woof.database.filepaths.parts.PHBaseValuePart;
import org.sag.woof.database.filepaths.parts.PHMethodRefValuePart;
import org.sag.woof.database.filepaths.parts.PHPart;
import org.sag.woof.database.filepaths.parts.ParentPart;
import org.sag.woof.database.filepaths.parts.Part;
import org.sag.woof.database.filepaths.parts.SysVarPart;
import org.sag.woof.database.filepaths.parts.UnknownPart;
import org.sag.woof.database.filepaths.parts.AnyPartImpl.AnyAPKInfoPart;
import org.sag.woof.database.filepaths.parts.AnyPartImpl.AnyChildPathPart;
import org.sag.woof.database.filepaths.parts.AnyPartImpl.AnyEPArgPart;
import org.sag.woof.database.filepaths.parts.AnyPartImpl.AnyFieldRefPart;
import org.sag.woof.database.filepaths.parts.AnyPartImpl.AnyMethodReturnPart;
import org.sag.woof.database.filepaths.parts.AnyPartImpl.AnyNumberPart;
import org.sag.woof.database.filepaths.parts.AnyPartImpl.AnyUIDPart;
import org.sag.woof.database.filepaths.parts.AnyPartImpl.AnyUserIdPart;
import org.sag.woof.database.filepaths.parts.ConstantPart.DoubleConstantPart;
import org.sag.woof.database.filepaths.parts.ConstantPart.IntConstantPart;
import org.sag.woof.database.filepaths.parts.ConstantPart.LongConstantPart;
import org.sag.woof.database.filepaths.parts.ConstantPart.NullConstantPart;
import org.sag.woof.database.filepaths.parts.ConstantPart.StringConstantPart;
import org.sag.woof.database.filepaths.parts.Part.Node;
import org.sag.woof.database.ssfiles.FileEntry;
import org.sag.woof.database.ssfiles.Owner;
import org.sag.woof.database.ssfiles.SecuritySensitiveFilesDatabase;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class PerformMatching {
	
	private final NullConstantPart nullConstant;
	private final ILogger logger;
	private final String cn;
	private final Map<EntryPointNode, List<Pair<PHPart,Part>>> epToOrginalMatchingPaths;
	//private  Map<EntryPointNode, List<Triple<PHPart,Part,Part>>> epToMatchingPaths;
	private final SecuritySensitiveFilesDatabase ssdb;
	private final MatchesDatabase madb;
	private final MatchesDatabase mdb;
	private final MatchesDatabase removedMatchesDB;
	private final MatchesDatabase noMatchesDB;
	
	public PerformMatching(SecuritySensitiveFilesDatabase ssdb, Map<EntryPointNode, List<Pair<PHPart,Part>>> epToOrginalMatchingPaths, ILogger logger) {
		this.cn = getClass().getSimpleName();
		this.logger = logger;
		this.nullConstant = new NullConstantPart();
		this.epToOrginalMatchingPaths = epToOrginalMatchingPaths;
		this.ssdb = ssdb;
		this.madb = new MatchesDatabase();
		this.mdb = new MatchesDatabase();
		this.removedMatchesDB = new MatchesDatabase();
		this.noMatchesDB = new MatchesDatabase();
	}
	
	public MatchesDatabase getMatchesDB() {
		return mdb;
	}
	
	public MatchesDatabase getNoMatchesDB() {
		return noMatchesDB;
	}
	
	public MatchesDatabase getMatchesAllDB() {
		return madb;
	}
	
	public MatchesDatabase getRemovedMatchesDB() {
		return removedMatchesDB;
	}
	
	public void run() {
		logger.info("{}: Matching...",cn);
		Map<EntryPointNode, List<Triple<PHPart,Part,Part>>> epToMatchingPaths = simplifyParts(epToOrginalMatchingPaths);
		removeUnwantedSeeds(epToMatchingPaths);//modifies argument and initilizes removedMatchesDB
		filterOutAllStringMatches(epToMatchingPaths);//modified argument and initilizes MatchesAllDatabase
		match(epToMatchingPaths);
		
		//Count
		Set<String> regexes = new HashSet<>();
		Set<IntermediateExpression> ies = new HashSet<>();
		for(EntryPointNode ep : madb.getData().keySet()) {
			for(RegexContainer r : madb.getData().get(ep)) {
				regexes.add(r.getRegex());
				ies.addAll(r.getIntermediateExpressions());
			}
		}
		for(EntryPointNode ep : mdb.getData().keySet()) {
			for(RegexContainer r : mdb.getData().get(ep)) {
				regexes.add(r.getRegex());
				ies.addAll(r.getIntermediateExpressions());
			}
		}
		for(EntryPointNode ep : noMatchesDB.getData().keySet()) {
			for(RegexContainer r : noMatchesDB.getData().get(ep)) {
				regexes.add(r.getRegex());
				ies.addAll(r.getIntermediateExpressions());
			}
		}
		for(EntryPointNode ep : removedMatchesDB.getData().keySet()) {
			for(RegexContainer r : removedMatchesDB.getData().get(ep)) {
				regexes.add(r.getRegex());
				ies.addAll(r.getIntermediateExpressions());
			}
		}
		logger.info("{}: Finished matching. Total IEs: {}, Total Regexes: {}",cn,ies.size(),regexes.size());
	}
	
	public void match(Map<EntryPointNode, List<Triple<PHPart,Part,Part>>> epToMatchingPaths) {
		logger.info("{}: Starting to match all paths to all regexes.",cn);
		
		for(EntryPointNode ep : epToMatchingPaths.keySet()) {
			for(Triple<PHPart,Part,Part> t : epToMatchingPaths.get(ep)) {
				mdb.addIntermediateExpression(ep, t.getFirst(), t.getSecond(), t.getThird());
			}
		}
		
		final Map<EntryPointNode,? extends Set<RegexContainer>> data = mdb.getData();
		Set<String> regexes = new HashSet<>();
		for(Set<RegexContainer> reg : data.values()) {
			for(RegexContainer r : reg) {
				regexes.add(r.getRegex());
			}
		}
		
		boolean successOuter = true;
		final CountingThreadExecutor exe = new CountingThreadExecutor();
		final List<Throwable> errs = new ArrayList<>();
		try {
			for(String regex : regexes) {
				final Pattern p = Pattern.compile(regex);
				Map<FileEntry, Set<Owner>> db = ssdb.getFilesToGroupsOrSystem();
				for(final FileEntry file : db.keySet()) {
					Set<Owner> owners = db.get(file);
					final Set<String> perms = new HashSet<>();
					for(Owner o : owners) {
						perms.addAll(o.getPermissions());
					}
					exe.execute(new Runnable() {
						@Override
						public void run() {
							Matcher m = p.matcher(file.getFullPath());
							if(m.matches()) {
								for(Set<RegexContainer> c : data.values()) {
									for(RegexContainer r : c) {
										if(r.getRegex().equals(regex)) {
											r.addFile(file, perms);
										}
									}
								}
							}
						}
					});
				}
			}
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
					ps.println(cn + ": Failed to successfully match everything. The following exceptions occured:");
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
		
		if(successOuter) {
			logger.info("{}: Finished matching all paths to all regexes.",cn);
			
			logger.info("{}: Removing matches with no matched file paths.",cn);
			for(Iterator<EntryPointNode> it = mdb.getData().keySet().iterator(); it.hasNext();) {
				EntryPointNode ep = it.next();
				for(Iterator<RegexContainer> rit = mdb.getData().get(ep).iterator(); rit.hasNext();) {
					RegexContainer r = rit.next();
					if(r.getFiles().isEmpty()) {
						rit.remove();
						noMatchesDB.add(ep, r);
					}
				}
				if(mdb.getData().get(ep).isEmpty())
					it.remove();
			}
			noMatchesDB.sort();
			logger.info("{}: Finished removing matches with no matched file paths.",cn);
		} else {
			logger.info("{}: Failed to match all paths to all regexes.",cn);
			throw new RuntimeException();
		}
		
	}

	//EP -> [Seed,MatchPart,OrgMatchPart]
	private Map<EntryPointNode, List<Triple<PHPart,Part,Part>>> simplifyParts(Map<EntryPointNode, List<Pair<PHPart,Part>>> in) {
		logger.info("{}: Starting to simplify all intermediate expressions.",cn);
		Map<EntryPointNode, List<Triple<PHPart,Part,Part>>> ret = new LinkedHashMap<>();
		for(EntryPointNode ep : in.keySet()) {
			List<Triple<PHPart,Part,Part>> paths = new ArrayList<>();
			for(Pair<PHPart,Part> p : in.get(ep)) {
				Part newPart = p.getSecond().clonePart();
				newPart = removeLoopsAndNormalize(newPart);
				newPart = forceSubFields(newPart);
				newPart = forceSubMethods(newPart);
				newPart = fixTVDevice(p.getFirst(), newPart);
				newPart = assumeSingleUser(newPart);
				newPart = simplifyNulls(newPart);
				newPart = convertToDNF(newPart);
				newPart = subEnvVarForDefaults(newPart);
				newPart = handleParentAndName(newPart);
				newPart = combineConstantsAndNormalize(newPart);
				newPart = collapseAnyParts(newPart);
				newPart = removeDuplicatesAndRootAnyInOr(newPart);
				if(!testIsDNF(newPart))
					logger.warn("{}: Failed to convert to DNF.\n  {}\n    {}",cn,p.getSecond().toSimpleString(),newPart.toSimpleString());
				paths.add(new Triple<>(p.getFirst(),newPart,p.getSecond()));
			}
			ret.put(ep, paths);
		}
		logger.info("{}: Finished simplifying all intermediate expressions.",cn);
		return ret;
	}
	
	
	
	//Assumes the parts have been processed so that those that will match any string have been set to either null or are a single any part
	//EP -> [Seed,MatchPart,OrgMatchPart]
	private void filterOutAllStringMatches(Map<EntryPointNode, List<Triple<PHPart,Part,Part>>> epsToPaths) {
		logger.info("{}: Starting to remove match all intermediate expressions.",cn);
		Map<EntryPointNode, List<Triple<PHPart,Part,Part>>> removedEpsToPaths = new LinkedHashMap<>();
		for(Iterator<EntryPointNode> epIt = epsToPaths.keySet().iterator(); epIt.hasNext();) {
			EntryPointNode ep = epIt.next();
			List<Triple<PHPart,Part,Part>> paths = epsToPaths.get(ep);
			for(Iterator<Triple<PHPart,Part,Part>> it = paths.iterator(); it.hasNext();) {
				Triple<PHPart,Part,Part> p = it.next();
				Part matchingPart = p.getSecond();
				String regex = matchingPart.toRegexString();
				if(matchingPart instanceof AnyPart || matchingPart instanceof UnknownPart || matchingPart instanceof NullConstantPart 
						|| matchingPart instanceof PHPart || (matchingPart instanceof ConstantPart && matchingPart.toString().equals(""))
						|| regex.contains("\\Q/lib64/\\E.*") || regex.contains("\\Q/proc/\\E.*")
						|| regex.contains("\\Q/lib/\\E.*") || regex.contains("\\Q/acct/uid_\\E") || regex.contains("\\Q/data/system/\\E.*")
						|| regex.contains("|.*\\d+|") || regex.contains("(?:.*\\d+|") || regex.contains("|.*\\d+)")) {
					Part org = p.getThird();
					logger.warn("{}: Removing path matches that matches any string:\n  EntryPoint: {}\n  Regex Pattern: {}\n  Part: {}\n  Seed: {}",
							cn,ep.toString(),org.toRegexString(),org.toString(),p.getFirst().toString());
					List<Triple<PHPart,Part,Part>> rmPaths = removedEpsToPaths.get(ep);
					if(rmPaths == null) {
						rmPaths = new ArrayList<>();
						removedEpsToPaths.put(ep,rmPaths);
					}
					rmPaths.add(p);
					it.remove();
				}
			}
			if(paths.isEmpty()) {
				epIt.remove();
			}
		}
		
		//Initilize the matches all database
		for(EntryPointNode ep : removedEpsToPaths.keySet()) {
			for(Triple<PHPart,Part,Part> t : removedEpsToPaths.get(ep)) {
				madb.addIntermediateExpression(ep, t.getFirst(), t.getSecond(), t.getThird());
			}
		}
		madb.sort();
		logger.info("{}: Finished removing match all intermediate expressions.",cn);
	}
	
	private void removeUnwantedSeeds(Map<EntryPointNode, List<Triple<PHPart,Part,Part>>> epToMatchingPaths) {
		logger.info("{}: Starting to remove filtered intermediate expressions.",cn);
		Set<String> sourcesToRemove = ImmutableSet.<String>builder()
				.add("<com.android.server.wm.TaskSnapshotLoader: android.app.ActivityManager$TaskSnapshot loadTask(int,int,boolean)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: android.os.ParcelFileDescriptor updateWallpaperBitmapLocked(java.lang.String,com.android.server.wallpaper.WallpaperManagerService$WallpaperData,android.os.Bundle)>")
				.add("<com.android.server.tv.PersistentDataStore: void <init>(android.content.Context,int)>")
				.add("<com.android.server.slice.SlicePermissionManager: android.util.AtomicFile getFile(java.lang.String)>")
				.add("<com.android.server.pm.ShortcutService: void saveUserLocked(int)>")
				.add("<com.android.server.pm.ShortcutService: android.util.AtomicFile getBaseStateFile()>")
				.add("<com.android.server.pm.Settings: void writePackageRestrictionsLPr(int)>")
				.add("<com.android.server.pm.Settings: void writeLPr()>")
				.add("<com.android.server.inputmethod.AdditionalSubtypeUtils: void load(android.util.ArrayMap,int)>")
				.add("<com.android.server.DropBoxManagerService$EntryFile: void <init>(java.io.File,int)>")
				.add("<com.android.server.content.SyncStorageEngine: void <init>(android.content.Context,java.io.File,android.os.Looper)>")
				.add("<com.android.server.appwidget.AppWidgetServiceImpl: android.util.AtomicFile getSavedStateFile(int)>")
				.add("<com.android.server.accounts.AccountManagerService$Injector: java.lang.String getPreNDatabaseName(int)>")
				.add("<android.content.pm.RegisteredServicesCache: android.content.pm.RegisteredServicesCache$UserServices findOrCreateUserLocked(int,boolean)>")
				.add("<com.android.internal.util.JournaledFile: java.io.File chooseForRead()>")
				.add("<com.android.internal.util.JournaledFile: java.io.File chooseForWrite()>")
				.add("<com.android.internal.util.JournaledFile: void commit()>")
				.add("<com.android.server.devicepolicy.DevicePolicyManagerService: void loadSettingsLocked(com.android.server.devicepolicy.DevicePolicyManagerService$DevicePolicyData,int)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: void loadSettingsLocked(int,boolean)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: void migrateFromOld()>")
				.add("<com.android.server.wm.TaskPersister: android.util.SparseBooleanArray loadPersistedTaskIdsForUser(int)>")
				.add("<android.content.pm.RegisteredServicesCache: void writePersistentServicesLocked(android.content.pm.RegisteredServicesCache$UserServices,int)>")
				.add("<com.android.internal.os.BatteryStatsImpl: void commitPendingDataToDisk(android.os.Parcel,com.android.internal.os.AtomicFile)>")
				.add("<com.android.server.appwidget.AppWidgetServiceImpl: void loadGroupStateLocked(int[])>")
				.add("<com.android.server.content.SyncStorageEngine: void readStatisticsLocked()>")
				.add("<com.android.server.content.SyncStorageEngine: void readStatusLocked()>")
				.add("<com.android.server.content.SyncStorageEngine: void writeStatusLocked()>")
				.add("<com.android.server.pm.BackgroundDexOptService: boolean shouldDowngrade(long)>")
				.add("<com.android.server.pm.BackgroundDexOptService: int abortIdleOptimizations(long)>")
				.add("<com.android.server.pm.OtaDexoptService: long getAvailableSpace()>")
				.add("<com.android.server.pm.ShortcutService: com.android.server.pm.ShortcutUser loadUserLocked(int)>")
				.add("<com.android.server.pm.ShortcutService: void saveBaseStateLocked()>")
				.add("<com.android.server.role.RoleUserState: void readFile()>")
				.add("<com.android.server.usage.AppIdleHistory: void readAppIdleTimes(int,android.util.ArrayMap)>")
				.add("<android.app.backup.BackupAgent: void onRestoreFile(android.os.ParcelFileDescriptor,long,int,java.lang.String,java.lang.String,long,long)>")
				.add("<com.android.server.pm.UserDataPreparer: int getSerialNumber(java.io.File)>")
				.add("<com.android.server.pm.UserDataPreparer: void setSerialNumber(java.io.File,int)>")
				.add("<com.android.server.pm.UserDataPreparer: void reconcileUsers(java.lang.String,java.util.List,java.util.List)>")
				.add("<com.android.server.pm.UserDataPreparer: void reconcileUsers(java.lang.String,java.util.List)>")
				.add("<com.android.server.pm.PackageManagerService: java.util.List reconcileAppsDataLI(java.lang.String,int,int,boolean,boolean)>")
				.add("<com.android.server.pm.PackageManagerService: int[] performDexOptUpgrade(java.util.List,boolean,int,boolean)>")
				.add("<android.app.SharedPreferencesImpl: boolean hasFileChangedUnexpectedly()>")
				.add("<android.app.ContextImpl: java.io.File ensurePrivateDirExists(java.io.File,int,int,java.lang.String)>")
				.add("<android.app.ContextImpl: java.io.File getPreferencesDir()>")
				.add("<com.android.server.stats.StatsCompanionService: void pullDirectoryUsage(int,long,long,java.util.List)>")
				.add("<com.android.server.pm.PackageInstallerSession: void maybeRenameFile(java.io.File,java.io.File)>")//Oversight not a seed
				.add("<com.android.server.SdpManagerService: void removeDirectoryRecursive(java.io.File)>")
				.add("<com.android.server.SKLogger$SKHandler: java.io.PrintWriter getPrintWriter()>")
				.add("<com.android.server.SKLogger$SKHandler: void copy(java.lang.String,java.lang.String)>")
				.add("<com.android.server.wm.TaskPersister: java.io.File getUserPersistedTaskIdsFile(int)>")
				.add("<com.android.server.pm.PackageManagerSamsungUtils: java.io.File getPackageDumpFile()>")
				.add("<com.android.server.pm.PackageManagerSamsungUtils: java.io.File getPermissionLogFile()>")
				.add("<com.android.server.pm.PackageManagerSamsungUtils: void logSamsungCriticalInfo(java.lang.String,boolean,java.lang.String)>")
				.add("<com.android.server.pm.PackageManagerServiceUtils: void logCriticalInfo(int,java.lang.String)>")
				.add("<com.android.server.pm.PackageManagerSamsungUtils: void logPermissionCriticalInfoToFile(java.lang.String)>")
				.add("<com.android.server.pm.PackageManagerServiceUtils: java.io.File getSettingsProblemFile()>")
				.add("<com.android.internal.os.BatteryStatsImpl: void makeBackupData()>")
				.add("<com.android.internal.os.BatteryStatsImpl: boolean hasAvailableStorage()>")
				.add("<com.android.internal.os.BatteryStatsHistory: boolean hasFreeDiskSpace()>")
				.add("<com.android.server.cocktailbar.CocktailBarManagerServiceImpl: android.util.AtomicFile savedStateFile()>")
				.add("<com.android.server.cocktailbar.CocktailBarManagerServiceImpl: void loadStateLocked()>")
				.add("<com.android.server.cocktailbar.CocktailBarManagerServiceImpl: void readStateFromFileLocked(java.io.FileInputStream)>")
				.add("<com.android.server.cocktailbar.settings.CocktailOrderManager: void readStateFromFileLocked(java.io.FileInputStream)>")
				.add("<com.android.server.cocktailbar.settings.CocktailOrderManager: void saveStateLocked()>")
				.add("<com.android.server.cocktailbar.settings.CocktailOrderManager: android.util.AtomicFile savedStateFile()>")
				.add("<com.android.server.cocktailbar.settings.CocktailOrderManager: void loadStateLocked()>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: android.os.ParcelFileDescriptor getWallpaperIndexOf(android.app.IWallpaperManagerCallback,int,android.os.Bundle,int,int,int,boolean,boolean)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: android.os.ParcelFileDescriptor getWallpaperThumbnailFileDescriptor(int,int,int,int)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: android.os.ParcelFileDescriptor updateWallpaperBitmapLocked(java.lang.String,com.android.server.wallpaper.WallpaperManagerService$WallpaperData,android.os.Bundle,int,int)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: boolean restoreNamedResourceLocked(com.android.server.wallpaper.WallpaperManagerService$WallpaperData)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: boolean saveBackupWallpaperFile(int,int,int,com.android.server.wallpaper.WallpaperManagerService$WallpaperData)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: boolean saveFile(java.io.File,java.io.File)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: java.lang.String getStringFromFile(java.lang.String)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: java.lang.String getWallpaperColorPath(int,int,boolean)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: void cleanUpKWPFiles(int,int)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: void clearWallpaperLocked(boolean,int,int,android.os.IRemoteCallback,java.lang.String)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: void copyFileToWallpaperFile(int,boolean)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: void generateCrop(com.android.server.wallpaper.WallpaperManagerService$WallpaperData,int)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: void generateResizedBitmap(com.android.server.wallpaper.WallpaperManagerService$WallpaperData,int)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: void loadSettingsLocked(int,boolean,int,boolean)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: void loadSettingsLockedForBackupData(int)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService: void migrateSystemToLockWallpaperLocked(int,int)>")
				.add("<com.android.server.wallpaper.WallpaperManagerService$WallpaperData: boolean cropExists()>")
				.add("<com.android.server.appwidget.AppWidgetServiceImpl: void writeLogToFile(java.lang.String)>")
				.build();
		
		//Remove but don't record because we are pretending these were not added as EPS
		for(Iterator<EntryPointNode> it = epToMatchingPaths.keySet().iterator(); it.hasNext();) {
			EntryPointNode ep = it.next();
			if(ep.getEntryPoint().getName().equals("handleMessage")) {
				it.remove();
			}
		}
		
		Map<EntryPointNode, List<Triple<PHPart,Part,Part>>> ret = new LinkedHashMap<>();
		for(Iterator<EntryPointNode> epIt = epToMatchingPaths.keySet().iterator(); epIt.hasNext();) {
			EntryPointNode ep = epIt.next();
			List<Triple<PHPart,Part,Part>> paths = epToMatchingPaths.get(ep);
			for(Iterator<Triple<PHPart,Part,Part>> it = paths.iterator(); it.hasNext();) {
				Triple<PHPart,Part,Part> p = it.next();
				PHPart seed = p.getFirst();
				String source = null;
				if(seed instanceof PHBaseValuePart) {
					source = ((PHBaseValuePart)seed).getSource().getSource().getSignature();
				} else if(seed instanceof PHArgumentValuePart) {
					source = ((PHArgumentValuePart)seed).getSource().getSource().getSignature();
				} else if(seed instanceof PHMethodRefValuePart) {
					source = ((PHMethodRefValuePart)seed).getSource().getSource().getSignature();
				}
				if(source != null && sourcesToRemove.contains(source)) {
					logger.warn("{}: Removing path matches because of filter:\n  EntryPoint: {}\n  Regex Pattern: {}\n  Part: {}\n  Seed: {}",
							cn,ep.toString(),p.getSecond().toRegexString(),p.getSecond().toString(),p.getFirst().toString());
					List<Triple<PHPart,Part,Part>> rmPaths = ret.get(ep);
					if(rmPaths == null) {
						rmPaths = new ArrayList<>();
						ret.put(ep,rmPaths);
					}
					rmPaths.add(p);
					it.remove();
				}
			}
			if(paths.isEmpty()) {
				epIt.remove();
			}
		}
		
		for(EntryPointNode ep : ret.keySet()) {
			for(Triple<PHPart,Part,Part> t : ret.get(ep)) {
				removedMatchesDB.addIntermediateExpression(ep, t.getFirst(), t.getSecond(), t.getThird());
			}
		}
		removedMatchesDB.sort();
		logger.info("{}: Finished removing filtered intermediate expressions.",cn);
	}
	
	//Needs to be run last after all other simplifying procedures
	private Part removeDuplicatesAndRootAnyInOr(Part org) {
		Part[] ret = {org};
		org.getPostOrderIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
			public void accept(Pair<Part,Node> t) {
				Part parent = t.getFirst();
				Node childNode = t.getSecond();
				Part child = childNode.getPart();
				if(child instanceof OrPart) {
					OrPart newOr = new OrPart();
					//Or uses hashset to keep things unique but since these have been modified so much
					//the hashset no longer contains unique entries so we must readd everything to a new
					//set to ensure things are unique
					for(Part p : ((OrPart)child).getChildren()) {
						if(!(p instanceof AnyPart || p instanceof UnknownPart || p instanceof PHPart || p instanceof NullConstantPart))
							newOr.add(p);
					}
					if(newOr.isEmpty()) {
						if(parent == null)
							ret[0] = nullConstant;
						else
							((BranchPart)parent).swapChild(childNode, nullConstant);
					} else if(newOr.size() == 1) {
						if(parent == null)
							ret[0] = newOr.getChildren().get(0);
						else
							((BranchPart)parent).swapChild(childNode, newOr.getChildren().get(0));
					} else {
						if(parent == null)
							ret[0] = newOr;
						else
							((BranchPart)parent).swapChild(childNode, newOr);
					}
				}
			}
		});
		return ret[0];
	}
	
	//Must be run after strings are combined because it assumes any "/" are between two any parts and all others
	//were combined with other strings in the combination phase
	private Part collapseAnyParts(Part org) {
		Part[] ret = {org};
		org.getPostOrderIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
			public void accept(Pair<Part,Node> t) {
				Part parent = t.getFirst();
				Node childNode = t.getSecond();
				Part child = childNode.getPart();
				if(child instanceof AppendPart) {
					List<Node> children = ((AppendPart)child).getChildNodes();
					List<Node> anys = null;
					for(Node n : children) {
						Part p = n.getPart();
						if((p instanceof AnyPart && !p.toRegexString().equals("\\d+")) || p instanceof UnknownPart || p instanceof PHPart || p instanceof NullConstantPart ||
								(p instanceof ConstantPart && p.toString().equals("/"))) {
							if(anys == null)
								anys = new ArrayList<>();
							anys.add(n);
						} else {
							if(anys != null) {
								if(anys.size() > 1) {
									boolean first = true;
									for(Node con : anys) {
										if(first) {
											((AppendPart)child).swapChild(con, new AnyComboPart(anys));
											first = false;
										} else {
											((AppendPart)child).removeChild(con);
										}
									}
								}
								anys = null;
							}
						}
					}
					if(anys != null) {
						if(anys.size() > 1) {
						boolean first = true;
							for(Node con : anys) {
								if(first) {
									((AppendPart)child).swapChild(con, new AnyComboPart(anys));
									first = false;
								} else {
									((AppendPart)child).removeChild(con);
								}
							}
						}
						anys = null;
					}
					if(((AppendPart)child).size() == 0) {
						if(parent == null)
							ret[0] = nullConstant;
						else
							((BranchPart)parent).removeChild(childNode);
					} else if(((AppendPart)child).size() == 1) {
						if(parent == null)
							ret[0] = ((AppendPart)child).getChildren().get(0);
						else
							((BranchPart)parent).swapChild(childNode,((AppendPart)child).getChildren().get(0));
					}
				}
			}
		});
		return ret[0];
	}
	
	private Part combineConstantsAndNormalize(Part org) {
		Part[] ret = {org};
		org.getPostOrderIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
			public void accept(Pair<Part,Node> t) {
				Part parent = t.getFirst();
				Node childNode = t.getSecond();
				Part child = childNode.getPart();
				if(child instanceof AppendPart) {
					List<Node> children = ((AppendPart)child).getChildNodes();
					List<Node> consts = null;
					for(Node n : children) {
						Part p = n.getPart();
						if(p instanceof ConstantPart && !(p instanceof NullConstantPart)) {
							if(consts == null)
								consts = new ArrayList<>();
							consts.add(n);
						} else {
							if(consts != null) {
								combineConstantsAndNormalizeHelper(consts, (AppendPart)child);
								consts = null;
							}
						}
					}
					if(consts != null) {
						combineConstantsAndNormalizeHelper(consts, (AppendPart)child);
						consts = null;
					}
					if(((AppendPart)child).size() == 1) {
						if(parent == null)
							ret[0] = ((AppendPart)child).getChildren().get(0);
						else
							((BranchPart)parent).swapChild(childNode,((AppendPart)child).getChildren().get(0));
					}
				}
			}
		});
		return ret[0];
	}
	
	private void combineConstantsAndNormalizeHelper(List<Node> consts, AppendPart child) {
		if(consts.size() > 1) {
			StringBuilder sb = new StringBuilder();
			boolean first = true;
			for(Node con : consts) {
				if(first)
					first = false;
				else
					child.removeChild(con);
				sb.append(con.getPart().toString());
			}
			String norm = normalize(sb.toString());
			if(!norm.equals(consts.get(0).getPart().toString()))
				child.swapChild(consts.get(0), new StringConstantPart(norm));
		} else if(consts.size() == 1) {
			String norm = normalize(consts.get(0).getPart().toString());
			if(!norm.equals(consts.get(0).getPart().toString()))
				child.swapChild(consts.get(0), new StringConstantPart(norm));
		}
	}
	
	private String normalize(String pathname) {
        int n = pathname.length();
        char[] normalized = pathname.toCharArray();
        int index = 0;
        char prevChar = 0;
        for (int i = 0; i < n; i++) {
            char current = normalized[i];
            // Remove duplicate slashes.
            if (!(current == '/' && prevChar == '/')) {
                normalized[index++] = current;
            }

            prevChar = current;
        }

        return (index != n) ? new String(normalized, 0, index) : pathname;
    }
	
	private Part handleParentAndName(Part org) {
		Part[] ret = {org};
		org.getPostOrderIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
			public void accept(Pair<Part,Node> t) {
				Part parent = t.getFirst();
				Node childNode = t.getSecond();
				Part child = childNode.getPart();
				if(child instanceof ParentPart || child instanceof NamePart) {
					boolean isParent = child instanceof ParentPart;
					Part path = isParent ? ((ParentPart)child).getChild() : ((NamePart)child).getChild();
					Part toSwap = null;
					if(path instanceof AppendPart) {
						toSwap = isParent ? handleParent((AppendPart)path) : handleName((AppendPart)path);
					} else if(path instanceof LeafPart || path instanceof SysVarPart || path instanceof EnvVarPart) { // Single part as child
						if(path instanceof AnyPart || path instanceof UnknownPart || path instanceof PHPart) // Could be anything so just move any up
							toSwap = path;
						else // Is a constant part
							toSwap = isParent ? handleParent(new AppendPart(path)) : handleName(new AppendPart(path));
					} else {
						throw new RuntimeException("Error: Unexpected child part of ParentPart or NamePart.");
					}
					if(parent == null) {
						ret[0] = toSwap;
					} else {
						Node n = new Node(toSwap);
						((BranchPart)parent).swapChild(childNode, n);
						((BranchPart)parent).mergeChild(n);
					}
				}
			}
		});
		return ret[0];
	}
	
	private Part subEnvVarForDefaults(Part org) {
		Part[] ret = {org};
		Map<String,Part> envToDefault = new HashMap<>();
		envToDefault.put("ANDROID_ROOT",                 new StringConstantPart("/system")); //set to default
		envToDefault.put("ANDROID_DATA",                 new StringConstantPart("/data")); //set to default
		envToDefault.put("ANDROID_EXPAND",               new StringConstantPart("/mnt/expand")); //not set
		envToDefault.put("ANDROID_STORAGE",              new StringConstantPart("/storage")); //set to default
		envToDefault.put("DOWNLOAD_CACHE",               new StringConstantPart("/data/cache")); //different from default
		envToDefault.put("OEM_ROOT",                     new StringConstantPart("/oem")); //not set
		envToDefault.put("ODM_ROOT",                     new StringConstantPart("/odm")); //not set
		envToDefault.put("VENDOR_ROOT",                  new StringConstantPart("/vendor")); //not set
		envToDefault.put("PRODUCT_ROOT",                 new StringConstantPart("/product")); //not set
		envToDefault.put("EXTERNAL_STORAGE",             new StringConstantPart("/sdcard")); //no default
		envToDefault.put("PRODUCT_SERVICES_ROOT",        new StringConstantPart("/product_services")); //not set
		envToDefault.put("dalvik.vm.stack-trace-dir",    new StringConstantPart("/data/anr")); //from /system/build.prop
		envToDefault.put("ro.boot.product.hardware.sku", new StringConstantPart("G020G")); //from adb shell getprop
		envToDefault.put("dalvik.vm.stack-trace-file",   new AnyInfoPart("SYSVAR")); //not defined in /system/build.prop
		org.getPostOrderIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
			public void accept(Pair<Part,Node> t) {
				Part parent = t.getFirst();
				Node childNode = t.getSecond();
				Part child = childNode.getPart();
				if(child instanceof EnvVarPart || child instanceof SysVarPart) {
					Part name = child instanceof EnvVarPart ? ((EnvVarPart)child).getChild() : ((SysVarPart)child).getChild();
					Part newChild = null;
					if(!(name instanceof StringConstantPart && (newChild = envToDefault.get(name.toString())) != null)) {
						newChild = child instanceof EnvVarPart ? new AnyInfoPart("ENVVAR") : new AnyInfoPart("SYSVAR");
					}
					if(parent == null)
						ret[0] = newChild;
					else
						((BranchPart)parent).swapChild(childNode, newChild);
				}
			}
		});
		return ret[0];
	}
	
	private Part handleName(AppendPart in) {
		Node lastSlash = null;
		for(ListIterator<Node> it = in.getChildNodes().listIterator(in.size()); it.hasPrevious();) {
			Node cur = it.previous();
			if(cur.getPart() instanceof ConstantPart && cur.getPart().toString().contains("/")) {
				lastSlash = cur;
				break;
			}
		}
		if(lastSlash == null) {
			return new AnyInfoPart("NAMEPATH");
		} else {
			for(Iterator<Node> it = in.getChildNodes().iterator(); it.hasNext();) {
				Node cur = it.next();
				if(cur.equals(lastSlash)) {
					break;
				} else {
					in.removeChild(cur);
				}
			}
			String v = lastSlash.getPart().toString();
			if(v.equals("/") || v.endsWith("/"))
				in.removeChild(lastSlash);
			else
				in.swapChild(lastSlash, new StringConstantPart(v.substring(v.lastIndexOf("/")+1, v.length())));
			if(in.size() == 0)
				return new AnyInfoPart("NAMEPATH");
			else if(in.size() == 1)
				return in.getChildren().get(0);
			else
				return in;
		}
	}
	
	private Part handleParent(AppendPart in) {
		Node lastSlash = null;
		for(ListIterator<Node> it = in.getChildNodes().listIterator(in.size()); it.hasPrevious();) {
			Node cur = it.previous();
			if(cur.getPart() instanceof ConstantPart && cur.getPart().toString().contains("/")) {
				lastSlash = cur;
				break;
			} else {
				in.removeChild(cur);
			}
		}
		if(lastSlash == null) {
			return new AnyInfoPart("PARENTPATH");
		} else {
			String v = lastSlash.getPart().toString();
			if(v.equals("/"))
				in.removeChild(lastSlash);
			else
				in.swapChild(lastSlash, new StringConstantPart(v.substring(0, v.lastIndexOf("/"))));
			//After removing the last slash, make sure there are no more slashes at the end of our path
			for(ListIterator<Node> it = in.getChildNodes().listIterator(in.size()); it.hasPrevious();) {
				Node cur = it.previous();
				if(cur.getPart() instanceof ConstantPart) {
					String value = cur.getPart().toString();
					int i = value.length() - 1;
					for(; i >= 0; i--) {
						if(value.charAt(i) != '/')
							break;
					}
					if(i < 0) {
						in.removeChild(cur);
					} else {
						in.swapChild(cur, new StringConstantPart(value.substring(0,i+1)));
						break;
					}
				} else {
					break;
				}
			}
			if(in.size() == 0)
				return new AnyInfoPart("PARENTPATH");
			else if(in.size() == 1)
				return in.getChildren().get(0);
			else
				return in;
		}
	}
	
	private boolean testIsDNF(Part org) {
		boolean[] isDNF = {true};
		org.getIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
			public void accept(Pair<Part,Node> t) {
				Part parent = t.getFirst();
				Node childNode = t.getSecond();
				Part child = childNode.getPart();
				if(child instanceof OrPart && parent != null) {
					isDNF[0] = false;
				} else if(child instanceof AppendPart && !(parent == null || parent instanceof OrPart 
						|| parent instanceof ParentPart || parent instanceof NamePart || parent instanceof SysVarPart || parent instanceof EnvVarPart)) {
					isDNF[0] = false;
				}
			}
		});
		return isDNF[0];
	}
	
	private Part simplifyNulls(Part org) {
		Part[] ret = {org};
		boolean[] changed = {false};
		
		do {
			changed[0] = false;
			ret[0].getPostOrderIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
				public void accept(Pair<Part,Node> t) {
					Part parent = t.getFirst();
					Node childNode = t.getSecond();
					Part child = childNode.getPart();
					
					if(parent == null) {
						if(child instanceof OrPart || child instanceof AppendPart) {
							List<Node> children = ((BranchPart)child).getChildNodes();
							if(children.isEmpty()) {
								ret[0] = nullConstant;
								changed[0] = true;
							} else if(children.size() == 1) {
								ret[0] = children.get(0).getPart();
								changed[0] = true;
							}
						} else if(child instanceof BranchPart) {
							if(((BranchPart)child).getChildren().isEmpty()) {
								ret[0] = nullConstant;
								changed[0] = true;
							}
						}
					} else if(parent instanceof BranchPart) {
						if(child instanceof NullConstantPart) {
							if(((BranchPart)parent).removeChild(childNode))
								changed[0] = true;
						} else if(child instanceof AppendPart || child instanceof OrPart) {
							List<Node> children = ((BranchPart)child).getChildNodes();
							if(children.isEmpty()) {
								if(((BranchPart)parent).removeChild(childNode))
									changed[0] = true;
							} else if(children.size() == 1) {
								if(((BranchPart)parent).swapChild(childNode, children.get(0)))
									changed[0] = true;
							} else { //If the parent is also an or child then it will merge otherwise nothing happens
								if(((BranchPart)parent).mergeChild(childNode))
									changed[0] = true;
							}
						} else if(child instanceof BranchPart) {
							if(((BranchPart)child).getChildren().isEmpty()) {
								if(((BranchPart)parent).removeChild(childNode))
									changed[0] = true;
							}
						}
					}
				}
			});
		} while(changed[0]);
		return ret[0];
	}
	
	private static final Map<String,Part> fieldToValueMap = ImmutableMap.<String,Part>builder()
			.put("<com.android.timezone.distro.installer.TimeZoneDistroInstaller: java.io.File systemTzDataFile>", new StringConstantPart("/system/usr/share/zoneinfo/tzdata"))
			.put("<com.android.server.wm.WindowTracing: java.io.File mTraceFile>", new StringConstantPart("/data/misc/wmtrace/wm_trace.pb"))
			.put("<com.android.server.wm.DisplaySettings: android.util.AtomicFile mFile>", new StringConstantPart("/data/system/display_settings.xml"))
			.put("<com.android.server.wifi.WifiConfigStore$StoreFile: com.android.internal.os.AtomicFile mAtomicFile>", new OrPart(new StringConstantPart("/data/misc/wifi/WifiConfigStore.xml"), new AppendPart(new StringConstantPart("/data/misc_ce/"), new AnyUserIdPart(null,null), new StringConstantPart("/wifi/WifiConfigStore.xml"))))
			.put("<com.android.server.wifi.WifiConfigStore$StoreFile: java.lang.String mFileName>", new OrPart(new StringConstantPart("/data/misc/wifi/WifiConfigStore.xml"), new AppendPart(new StringConstantPart("/data/misc_ce/"), new AnyUserIdPart(null,null), new StringConstantPart("/wifi/WifiConfigStore.xml"))))
			.put("<com.android.server.wifi.LastMileLogger: java.lang.String mEventBufferPath>", new StringConstantPart("/sys/kernel/debug/tracing/instances/wifi/trace"))
			.put("<com.android.server.wifi.LastMileLogger: java.lang.String mEventReleasePath>", new StringConstantPart("/sys/kernel/debug/tracing/instances/wifi/free_buffer"))
			.put("<com.android.server.wifi.LastMileLogger: java.lang.String mEventEnablePath>", new StringConstantPart("/sys/kernel/debug/tracing/instances/wifi/tracing_on"))
			.put("<com.android.server.wallpaper.WallpaperManagerService$WallpaperData: java.io.File wallpaperFile>", new AppendPart(new StringConstantPart("/data/system/users/"), new AnyUserIdPart(null,null),new OrPart(new StringConstantPart("/wallpaper_lock_orig"), new StringConstantPart("/wallpaper_orig"))))
			.put("<com.android.server.wallpaper.WallpaperManagerService$WallpaperData: java.io.File cropFile>", new AppendPart(new StringConstantPart("/data/system/users/"), new AnyUserIdPart(null,null),new OrPart(new StringConstantPart("/wallpaper_lock"), new StringConstantPart("/wallpaper"))))
			.put("<com.android.server.usage.UsageStatsService: java.io.File mUsageStatsDir>", new StringConstantPart("/data/system/usagestats"))
			.put("<android.os.Message: int arg1>", new AnyUserIdPart(null,null))
			.put("<com.android.server.timezone.PackageStatusStorage: android.util.AtomicFile mPackageStatusFile>", new StringConstantPart("/data/system/timezone/package-status.xml"))
			.put("<com.android.server.SystemUpdateManagerService: android.util.AtomicFile mFile>", new StringConstantPart("/data/system/system-update-info.xml"))
			.put("<com.android.server.StorageManagerService: android.util.AtomicFile mSettingsFile>", new StringConstantPart("/data/system/storage.xml"))
			.put("<com.android.server.StorageManagerService: java.io.File mLastMaintenanceFile>", new StringConstantPart("/data/system/last-fstrim"))
			.put("<com.android.server.storage.AppFuseBridge$MountScope: java.io.File mountPoint>", new AppendPart(new StringConstantPart("/mnt/appfuse/"),new AnyUIDPart(null,null),new StringConstantPart("_"),new AnyNumberPart(null,null,"INT")))
			.put("<com.android.server.slice.SlicePermissionManager: java.io.File mSliceDir>", new StringConstantPart("/data/system/slice"))
			.put("<android.content.pm.UserInfo: int id>", new AnyUserIdPart(null,null))
			.put("<com.android.server.pm.UserManagerService: java.io.File mUsersDir>", new AppendPart(new OrPart(new StringConstantPart("/data"),new AppendPart(new AnyAPKInfoPart(null,null),new StringConstantPart("/cache"))),new StringConstantPart("/system/users")))
			.put("<android.content.pm.UserInfo: java.lang.String iconPath>", new AppendPart(new OrPart(new StringConstantPart("/data"),new AppendPart(new AnyAPKInfoPart(null,null),new StringConstantPart("/cache"))),new StringConstantPart("/system/users/"),new AnyUserIdPart(null,null),new StringConstantPart("photo.png")))
			.put("<com.android.server.pm.Settings: java.io.File mSettingsFilename>", new StringConstantPart("/data/system/packages.xml"))
			.put("<com.android.server.pm.Settings: java.io.File mBackupSettingsFilename>", new StringConstantPart("/data/system/packages-backup.xml"))
			.put("<com.android.server.pm.Settings: java.io.File mPackageListFilename>", new StringConstantPart("/data/system/packages.list"))
			.put("<com.android.server.pm.Settings: java.io.File mStoppedPackagesFilename>", new StringConstantPart("/data/system/packages-stopped.xml"))
			.put("<com.android.server.pm.Settings: java.io.File mBackupStoppedPackagesFilename>", new StringConstantPart("/data/system/packages-stopped-backup.xml"))
			.put("<com.android.server.pm.Settings: java.io.File mKernelMappingFilename>", new StringConstantPart("/config/sdcardfs"))
			.put("<com.android.server.pm.Settings: java.io.File mSystemDir>", new StringConstantPart("/data/system"))
			.put("<android.content.pm.PackageParser$Package: java.lang.String[] splitCodePaths>", new AnyInfoPart("PACKAGECODEPATHS"))
			.put("<com.android.server.pm.PackageManagerService$FileInstallArgs: java.io.File codeFile>", new AnyInfoPart("PACKAGECODEPATHS"))
			.put("<android.content.pm.PackageParser$Package: java.lang.String baseCodePath>", new AnyInfoPart("PACKAGECODEPATHS"))
			.put("<android.content.pm.PackageParser$Package: java.lang.String codePath>", new AnyInfoPart("PACKAGECODEPATHS"))
			.put("<com.android.server.pm.PackageManagerService: java.io.File mCacheDir>", new StringConstantPart("/data/system/package_cache/1"))
			.put("<com.android.server.pm.PackageInstallerSession: java.io.File stageDir>", new AppendPart(new OrPart(new AppendPart(new StringConstantPart("/mnt/expand/"), new AnyInfoPart("VOLUMEID")), new StringConstantPart("/data")), new StringConstantPart("/app/vmdl"), new AnyNumberPart(null,null,"INT"), new StringConstantPart(".tmp")))
			.put("<android.content.pm.ApplicationInfo: java.lang.String sourceDir>", new AnyInfoPart("APPDIR"))
			.put("<com.android.server.pm.PackageInstallerService: android.util.AtomicFile mSessionsFile>", new StringConstantPart("/data/system/install_sessions.xml"))
			.put("<com.android.server.pm.PackageInstallerService: java.io.File mSessionsDir>", new StringConstantPart("/data/system/install_sessions"))
			.put("<com.android.server.pm.CrossProfileIntentFilter: int mTargetUserId>", new AnyUserIdPart(null,null))
			.put("<com.android.server.PersistentDataBlockService: java.lang.String mDataBlockFile>", new StringConstantPart("/dev/block/bootdevice/by-name/frp"))
			.put("<com.android.server.notification.NotificationManagerService: android.util.AtomicFile mPolicyFile>", new StringConstantPart("/data/system/notification_policy.xml"))
			.put("<com.android.server.net.watchlist.WatchlistConfig: java.io.File mXmlFile>", new OrPart(new StringConstantPart("/data/misc/network_watchlist/network_watchlist.xml"), new StringConstantPart("/data/misc/network_watchlist/network_watchlist_for_test.xml")))
			.put("<com.android.server.net.NetworkPolicyManagerService: android.util.AtomicFile mPolicyFile>", new StringConstantPart("/data/system/netpolicy.xml"))
			.put("<com.android.server.InputMethodManagerService$InputMethodFileManager: android.util.AtomicFile mAdditionalInputMethodSubtypeFile>", new OrPart(new StringConstantPart("/data/system/inputmethod/subtypes.xml"), new AppendPart(new StringConstantPart("/data/system/users/"), new AnyUserIdPart(null,null), new StringConstantPart("/inputmethod/subtypes.xml"))))
			.put("<com.android.server.input.PersistentDataStore: android.util.AtomicFile mAtomicFile>", new StringConstantPart("/data/system/input-manager-state.xml"))
			.put("<com.android.server.input.InputManagerService: java.io.File mDoubleTouchGestureEnableFile>", new NullConstantPart()) //A resource string that does not get used
			.put("<com.android.server.EntropyMixer: java.lang.String hwRandomDevice>", new StringConstantPart("/dev/hw_random"))
			.put("<com.android.server.EntropyMixer: java.lang.String randomDevice>", new StringConstantPart("/dev/urandom"))
			.put("<com.android.server.EntropyMixer: java.lang.String entropyFile>", new StringConstantPart("/data/system/entropy.dat"))
			.put("<com.android.server.DropBoxManagerService: java.io.File mDropBoxDir>", new StringConstantPart("/data/system/dropbox"))
			.put("<com.android.server.display.PersistentDataStore$Injector: android.util.AtomicFile mAtomicFile>", new StringConstantPart("/data/system/display-manager-state.xml"))
			.put("<com.android.server.DeviceIdleController: com.android.internal.os.AtomicFile mConfigFile>", new StringConstantPart("/data/system/deviceidle.xml"))
			.put("<com.android.server.content.SyncStorageEngine: android.util.AtomicFile mAccountInfoFile>", new StringConstantPart("/data/system/sync/accounts.xml"))
			.put("<com.android.server.content.SyncStorageEngine: android.util.AtomicFile mStatusFile>", new StringConstantPart("/data/system/sync/status.bin"))
			.put("<com.android.server.content.SyncStorageEngine: android.util.AtomicFile mStatisticsFile>", new StringConstantPart("/data/system/sync/stats.bin"))
			.put("<com.android.server.content.SyncLogger$RotatingFileLogger: java.io.File mLogPath>", new StringConstantPart("/data/system/syncmanager-log"))
			.put("<com.android.server.ConnectivityService: java.io.File mProvisioningUrlFile>", new StringConstantPart("/data/misc/radio/provisioning_urls.xml"))
			.put("<com.android.server.backup.Trampoline: java.io.File mSuppressFile>", new StringConstantPart("/data/backup/backup-suppress"))
			.put("<com.android.server.backup.params.BackupParams: java.lang.String dirName>", new AnyInfoPart("BACKUPDIR"))
			.put("<com.android.server.backup.BackupManagerService: java.io.File mBaseStateDir>", new StringConstantPart("/data/backup"))
			.put("<com.android.server.backup.BackupManagerService: java.io.File mTokenFile>", new StringConstantPart("/data/backup/ancestral"))
			.put("<com.android.server.backup.BackupManagerService: java.io.File mJournalDir>", new StringConstantPart("/data/backup/pending"))
			.put("<com.android.server.AppOpsService: android.util.AtomicFile mFile>", new StringConstantPart("/data/system/appops.xml"))
			.put("<com.android.server.am.TaskPersister: java.io.File mTaskIdsDir>", new StringConstantPart("/data/system_de"))
			.put("<com.android.server.am.ProcessStatsService: java.io.File mBaseDir>", new StringConstantPart("/data/system/procstats"))
			.put("<com.android.server.am.CompatModePackages: android.util.AtomicFile mFile>", new StringConstantPart("/data/system/packages-compat.xml"))
			.put("<com.android.server.am.AppWarnings: android.util.AtomicFile mConfigFile>", new StringConstantPart("/data/system/packages-warnings.xml"))
			.put("<com.android.server.am.ActivityManagerService: android.util.AtomicFile mGrantFile>", new StringConstantPart("/data/system/urigrants.xml"))
			.put("<com.android.internal.util.JournaledFile: java.io.File mReal>", new StringConstantPart("/data/system/batterystats.bin")) //While it is used in 4 places in the code, only occurs in one place in our results (so we just include that one) EP: AMS shutdown -> BatteryStatsImpl
			.put("<com.android.internal.util.JournaledFile: java.io.File mTemp>", new StringConstantPart("/data/system/batterystats.bin.tmp")) //While it is used in 4 places in the code, only occurs in one place in our results (so we just include that one) EP: AMS shutdown -> BatteryStatsImpl
			.put("<com.android.internal.util.FileRotator: java.io.File mBasePath>", new StringConstantPart("/data/system/netstats"))
			.put("<com.android.internal.telephony.SmsUsageMonitor: java.io.File mPatternFile>", new StringConstantPart("/data/misc/sms/codes"))
			.put("<com.android.internal.os.KernelCpuProcReader: java.nio.file.Path mProc>", new OrPart(new StringConstantPart("/proc/uid_cpupower/time_in_state"), new StringConstantPart("/proc/uid_cpupower/concurrent_active_time"), new StringConstantPart("/proc/uid_cpupower/concurrent_policy_time")))
			.put("<com.android.internal.os.KernelCpuSpeedReader: java.lang.String mProcFile>", new AppendPart(new StringConstantPart("/sys/devices/system/cpu/cpu"), new AnyNumberPart(null,null,"INT"), new StringConstantPart("/cpufreq/stats/time_in_state")))
			.put("<com.android.internal.net.NetworkStatsFactory: java.io.File mStatsXtIfaceFmt>", new StringConstantPart("/proc/net/xt_qtaguid/iface_stat_fmt"))
			.put("<com.android.server.net.NetworkStatsFactory: java.io.File mStatsXtIfaceFmt>", new StringConstantPart("/proc/net/xt_qtaguid/iface_stat_fmt"))
			.put("<com.android.internal.net.NetworkStatsFactory: java.io.File mStatsXtIfaceAll>", new StringConstantPart("/proc/net/xt_qtaguid/iface_stat_all"))
			.put("<com.android.server.net.NetworkStatsFactory: java.io.File mStatsXtIfaceAll>", new StringConstantPart("/proc/net/xt_qtaguid/iface_stat_all"))
			.put("<com.android.internal.net.NetworkStatsFactory: java.io.File mStatsXtUid>", new StringConstantPart("/proc/net/xt_qtaguid/stats"))
			.put("<com.android.server.net.NetworkStatsFactory: java.io.File mStatsXtUid>", new StringConstantPart("/proc/net/xt_qtaguid/stats"))
			.put("<com.android.internal.os.SomeArgs: java.lang.Object arg1>", new NullConstantPart()) //Comes from Device$DeviceHandler: void handleMessage which is not callable from an entry point
			.put("<com.android.commands.am.Instrument$ProtoStatusReporter: java.io.File mLog>", new OrPart(new AppendPart(new StringConstantPart("/sdcard/instrument-logs/log-"),new AnyInfoPart("DATETIME"),new StringConstantPart(".instrumentation_data_proto")), new AppendPart(new StringConstantPart("/sdcard/"), new AnyChildPathPart(null,null)))) ///sdcard comes from the env variable EXTERNAL_STORAGE
			.put("<android.os.storage.VolumeInfo: java.lang.String fsUuid>", new AnyInfoPart("VOLUMEID")) //Our image only has one volume and this field is null
			.put("<android.os.storage.VolumeInfo: java.lang.String path>", new StringConstantPart("/storage/emulated")) //Our image only has one volume and this is its value from "dumpsys mount"
			.put("<android.os.storage.StorageVolume: java.io.File mPath>", new StringConstantPart("/storage/emulated")) //Same as above because this is for emulated volumes for a specific user and there is only one user on our system
			.put("<android.util.Pair: java.lang.Object first>", new AnyInfoPart("PACKAGENAME")) // technically generic but only used when separating the package name
			.put("<android.content.pm.PackageParser: java.io.File mCacheDir>", new StringConstantPart("/data/system/package_cache/1")) // Always the same as the default cach dir for PMS
			.put("<android.content.pm.ApplicationInfo: java.lang.String volumeUuid>", new AnyInfoPart("VOLUMEID")) //Field is never set to anything and we only have one volume
			.put("<android.content.pm.ApplicationInfo: java.lang.String scanSourceDir>", new AnyInfoPart("APPDIR"))
			.put("<android.content.AsyncQueryHandler$WorkerArgs: java.lang.String orderBy>", new NullConstantPart()) //Not a file path
			.put("<android.content.AsyncQueryHandler$WorkerArgs: java.lang.String selection>", new NullConstantPart()) //Not a file path
			.put("<android.app.SharedPreferencesImpl: java.io.File mFile>", new AppendPart(new AnyAPKInfoPart(null,null),new StringConstantPart("/shared_prefs/"), new AnyInfoPart("FILENAMESPECIFICTOPACKAGE"), new StringConstantPart(".xml")))
			.put("<android.app.ProfilerInfo: java.lang.String profileFile>", new AnyInfoPart("PROFILEFILE"))
			.put("<android.app.ActivityThread$DumpHeapData: java.lang.String path>", new AnyEPArgPart("<com.android.server.am.ActivityManagerService: boolean dumpHeap(java.lang.String,int,boolean,boolean,boolean,java.lang.String,android.os.ParcelFileDescriptor)>","$r14 := @parameter5: java.lang.String",5))
			.put("<com.android.internal.os.BatteryStatsHistory: java.io.File mHistoryDir>", new StringConstantPart("/data/system/battery-history"))
			.put("<com.android.internal.os.BatteryStatsImpl: com.android.internal.os.AtomicFile mStatsFile>", new StringConstantPart("/data/system/batterystats.bin"))
			.put("<com.android.server.rollback.RollbackStore: java.io.File mRollbackDataDir>", new StringConstantPart("/data/rollback"))
			.put("<com.android.server.stats.StatsCompanionService: java.io.File mBaseDir>", new StringConstantPart("/data/system/stats_companion"))			
			.put("<com.android.server.wm.TaskPersister: java.io.File mTaskIdsDir>", new StringConstantPart("/data/system_de"))			
			.put("<com.android.internal.os.KernelCpuThreadReader: java.nio.file.Path mProcPath>", new StringConstantPart("/proc"))			
			.put("<com.android.internal.os.KernelCpuUidTimeReader$KernelCpuUidFreqTimeReader: java.nio.file.Path mProcFilePath>", new StringConstantPart("/proc/uid_time_in_state"))			
			.put("<com.android.server.appop.AppOpsService: android.util.AtomicFile mFile>", new StringConstantPart("/data/system/appops.xml"))			
			.put("<com.android.server.SensorPrivacyService$SensorPrivacyServiceImpl: android.util.AtomicFile mAtomicFile>", new StringConstantPart("/data/system/sensor_privacy.xml"))			
			.put("<com.android.server.wifi.util.DataIntegrityChecker: java.io.File mIntegrityFile>", new OrPart(new AppendPart(new StringConstantPart("/data/misc/"), new AnyInfoPart("WIFICONFIGSTOREID"), new StringConstantPart(".encrypted-checksum")), new AppendPart(new StringConstantPart("/data/misc_ce/"), new AnyUserIdPart(null,null), new StringConstantPart("/"), new AnyInfoPart("WIFICONFIGSTOREID"), new StringConstantPart(".encrypted-checksum"))))
			.put("<com.android.server.wm.DisplayWindowSettings$AtomicFileStorage: android.util.AtomicFile mAtomicFile>", new StringConstantPart("/data/system/display_settings.xml"))
			.put("<com.android.timezone.distro.installer.TimeZoneDistroInstaller: java.io.File baseVersionFile>", new StringConstantPart("/apex/com.android.runtime/etc/tz/tz_version"))
			.put("<com.android.timezone.distro.installer.TimeZoneDistroInstaller: java.io.File oldStagedDataDir>", new StringConstantPart("/data/misc/zoneinfo/old"))
			.put("<com.android.timezone.distro.installer.TimeZoneDistroInstaller: java.io.File stagedTzDataDir>", new StringConstantPart("/data/misc/zoneinfo/staged"))
			.put("<com.android.timezone.distro.installer.TimeZoneDistroInstaller: java.io.File currentTzDataDir>", new StringConstantPart("/data/misc/zoneinfo/current"))
			.put("<com.android.timezone.distro.installer.TimeZoneDistroInstaller: java.io.File workingDir>", new StringConstantPart("/data/misc/zoneinfo/working"))
			.put("<com.android.server.usage.AppIdleHistory: java.io.File mStorageDir>", new StringConstantPart("/data/system"))
			.put("<com.android.server.pm.UserManagerService: java.io.File mUserListFile>", new StringConstantPart("/data/system/users/userlist.xml"))
			
			.put("<com.android.server.ssrm.platform.paths.AGenSysFsPaths: java.lang.String BUS_MAX_PATH>", new StringConstantPart("/sys/class/devfreq/17000010.devfreq_mif/max_freq"))
			.put("<com.android.server.ssrm.platform.paths.AGenSysFsPaths: java.lang.String BUS_MIN_PATH>", new OrPart(new StringConstantPart("/sys/devices/platform/17000010.devfreq_mif/devfreq/17000010.devfreq_mif/scaling_devfreq_min"), new StringConstantPart("/sys/class/devfreq/17000010.devfreq_mif/min_freq")))
			.put("<com.android.server.ssrm.platform.paths.AGenSysFsPaths: java.lang.String CPU_CORE_MAX_PATH>", new StringConstantPart("/sys/power/cpuhotplug/max_online_cpu"))
			.put("<com.android.server.ssrm.platform.paths.AGenSysFsPaths: java.lang.String CPU_CORE_MIN_PATH>", new StringConstantPart("/sys/power/cpuhotplug/min_online_cpu"))
			.put("<com.android.server.ssrm.platform.paths.AGenSysFsPaths: java.lang.String CPU_IDLE_OFF>", new StringConstantPart("/sys/module/cpuidle/parameters/off"))
			.put("<com.android.server.ssrm.platform.paths.AGenSysFsPaths: java.lang.String GPU_MAX_PATH>", new StringConstantPart("/sys/devices/11400000.mali/dvfs_max_lock"))
			.put("<com.android.server.ssrm.platform.paths.AGenSysFsPaths: java.lang.String GPU_MIN_PATH>", new StringConstantPart("/sys/devices/11400000.mali/dvfs_min_lock"))
			.put("<com.android.server.ssrm.platform.paths.AGenSysFsPaths: java.lang.String HOTPLUG_DISABLE_PATH>", new StringConstantPart(""))
			.put("<com.android.server.ssrm.platform.paths.AGenSysFsPaths: java.lang.String PCIE_PSM_DISABLE_PATH>", new StringConstantPart(""))
			.put("<com.android.server.ssrm.platform.paths.AGenSysFsPaths: java.lang.String SCHED_TUNE_BOOST_PATH>", new StringConstantPart(""))
			.put("<com.android.server.ssrm.platform.paths.AQcSysFsPaths: java.lang.String BUS_MAX_PATH>", new OrPart(new StringConstantPart("/sys/class/devfreq/soc:qcom,mincpubw/max_freq"), new StringConstantPart("/sys/class/devfreq/soc:qcom,cpu6-cpu-ddr-latfloor/max_freq"), new StringConstantPart("/sys/class/devfreq/soc:qcom,cpubw/max_freq"), new StringConstantPart("/sys/class/devfreq/soc:qcom,cpu4-cpu-ddr-latfloor/max_freq")))
			.put("<com.android.server.ssrm.platform.paths.AQcSysFsPaths: java.lang.String BUS_MIN_PATH>", new OrPart( new StringConstantPart("/sys/class/devfreq/soc:qcom,cpu6-cpu-ddr-latfloor/min_freq"), new StringConstantPart("/sys/class/devfreq/soc:qcom,cpubw/min_freq"), new StringConstantPart("/sys/class/devfreq/soc:qcom,mincpubw/min_freq"), new StringConstantPart("/sys/class/devfreq/soc:qcom,cpu4-cpu-ddr-latfloor/min_freq")))
			.put("<com.android.server.ssrm.platform.paths.AQcSysFsPaths: java.lang.String GPU_MAX_PATH>", new StringConstantPart("/sys/class/kgsl/kgsl-3d0/max_pwrlevel"))
			.put("<com.android.server.ssrm.platform.paths.AQcSysFsPaths: java.lang.String GPU_MIN_PATH>", new StringConstantPart("/sys/class/kgsl/kgsl-3d0/min_pwrlevel"))
			.put("<com.android.server.ssrm.platform.paths.AQcSysFsPaths: java.lang.String LEGACY_SCHEDULER_PATH>", new StringConstantPart(""))
			.put("<com.android.server.ssrm.platform.paths.AQcSysFsPaths: java.lang.String PCIE_PSM_DISABLE_PATH>", new StringConstantPart("/sys/devices/virtual/sec/pcie-wifi/pcie_l1ss_ctrl"))
			.put("<com.android.server.ssrm.platform.paths.AQcSysFsPaths: java.lang.String SCHED_TUNE_TA_BOOST_PATH>", new StringConstantPart("/dev/stune/top-app/schedtune.boost"))
			.put("<java.io.File: java.lang.String separator>", new StringConstantPart("/"))
			.put("<java.lang.Boolean: java.lang.Boolean FALSE>", new StringConstantPart("false"))
			.put("<java.lang.Boolean: java.lang.Boolean TRUE>", new StringConstantPart("true"))
			.put("<com.samsung.ucm.ucmservice.CredentialManagerService: android.util.AtomicFile mPersistentAppletInfoFile>", new StringConstantPart("/data/system/appletsConfig.xml"))
			.put("<com.samsung.ucm.ucmservice.CredentialManagerService: android.util.AtomicFile mPersistentServicesFile>", new StringConstantPart("/data/system/registered_ucm_services/com.samsung.ucm.agent.xml"))
			.put("<com.samsung.android.server.wifi.WifiRoamingAssistant: java.io.File mRclFile>", new StringConstantPart("/data/misc/wifi/RCL.json"))
			.put("<com.samsung.android.hqm.f: java.lang.String Rb>", new StringConstantPart("/data/system/hqm_emlogcnt"))
			.put("<com.samsung.android.hardware.context.provider.miscprovider.PedometerInvenImpl: java.lang.String mPrefix>", new AppendPart(new StringConstantPart("/sys/bus/iio/devices/iio:device"), new AnyInfoPart("DEVICENODE")))
			.put("<com.samsung.android.gesture.MotionRecognitionService: java.lang.String mPocketDetectorSysfs>", new StringConstantPart("/sys/class/sec/tsp/cmd"))
			.put("<com.samsung.android.displaysolution.SemDisplaySolutionManagerService: java.lang.String BURN_IN_APPLY_COUNT>", new StringConstantPart("/efs/afc/apply_count"))
			.put("<com.samsung.android.displaysolution.SemDisplaySolutionManagerService: java.lang.String IRC_MODE_NODE>", new StringConstantPart("/sys/class/lcd/panel/irc_mode"))
			.put("<com.samsung.android.displaysolution.MdnieScenarioControlService: java.lang.String ON_PIXEL_RATIO_PATH>", new StringConstantPart("/sys/class/sensors/light_sensor/copr_roix"))
			.put("<com.samsung.android.displaysolution.MdnieScenarioControlService: java.lang.String PIXEL_SELF_MOVE_PATH>", new StringConstantPart("/sys/class/lcd/panel/self_move"))
			.put("<com.samsung.android.displaysolution.MdnieScenarioControlService: java.lang.String READING_SCENARIO_PATH>", new StringConstantPart("/sys/class/mdnie/mdnie/scenario"))
			.put("<com.samsung.android.codecsolution.mp.HdrControlService: java.lang.String WRITE_HDR_PATH>", new StringConstantPart("/sys/class/mdnie/mdnie/hdr"))
			.put("<com.android.server.wifi.WifiConfigManager: java.io.File mFilePathRemovedNwInfo>", new StringConstantPart("/data/misc/wifi/removed_nw.conf"))
			.put("<com.android.server.wifi.iwc.IWCLogFile: java.lang.String mFilePath>", new StringConstantPart("/data/log/wifi/iwc/iwc_dump.txt"))
			.put("<com.android.server.wifi.iwc.IWCFile: java.io.File mFile>", new OrPart(new StringConstantPart("/data/log/wifi/iwc/iwc_dump.txt"), new StringConstantPart("/data/misc/wifi/qtables.json")))
			.put("<com.android.server.sepunion.friends.executable.ExecScreenTurnedOff: java.io.File mStateFile>", new StringConstantPart("/data/system/friends/no_lock_screen"))
			.put("<android.os.RecoverySystem: java.io.File UNCRYPT_PACKAGE_FILE>", new StringConstantPart("/cache/recovery/uncrypt_file"))
			.put("<com.android.server.ReactiveService: java.lang.String mDataBlockFile>", new StringConstantPart("/dev/block/persistent"))
			.put("<android.apex.ApexInfo: java.lang.String packagePath>", new AnyInfoPart("PACKAGEPATH"))
			.put("<com.android.server.net.UrspService: android.util.AtomicFile mPolicyFile>", new StringConstantPart("/data/system/ursppolicy.xml"))
			.put("<com.android.server.lights.LightsService: java.lang.String mWakeLockPath>", new StringConstantPart("/sys/power/wake_lock"))
			.put("<com.android.server.input.ControlWakeKey: java.lang.String mWakeKeyFilePath1>", new StringConstantPart("/sys/power/volkey_wakeup"))
			.put("<com.android.server.input.ControlWakeKey: java.lang.String mWakeKeyFilePath>", new StringConstantPart("/sys/class/sec/sec_key/wakeup_keys"))
			.put("<com.android.server.enterprise.log.FileLogger$LogFileWriter$1: java.io.File val$file>", new AppendPart(new StringConstantPart("/data/system/enterprise/logs/"), new AnyInfoPart("TIMESTAMP")))
			.put("<com.android.server.enterprise.certificate.EdmKeyStore: java.lang.String mPath>", new OrPart(new StringConstantPart("/data/system/enterprise_cacerts.bks"), new StringConstantPart("/data/system/enterprise_untrustedcerts.bks"), new StringConstantPart("/data/system/enterprise_usercerts.bks"), new StringConstantPart("/data/system/enterprise_nativecerts.bks")))
			.put("<com.android.server.BluetoothManagerService: java.lang.String logDirp>", new StringConstantPart("/data/misc/bluedroiddump"))
			.put("<com.android.server.BluetoothManagerService: java.lang.String mainBfp>", new StringConstantPart("/data/misc/bluedroiddump/mainBuffer.log"))
			.put("<com.android.server.BluetoothManagerService: java.lang.String subBfp>", new StringConstantPart("/data/misc/bluedroiddump/subBuffer.log"))
			.put("<com.android.server.asks.ASKSManagerService: android.util.AtomicFile mFile>", new StringConstantPart("/data/system/.aasa/asks.xml"))
			.put("<com.android.server.asks.ASKSManagerService: java.lang.String EE_CERT_FILE>", new StringConstantPart("/system/etc/ASKS_EDGE_1.crt"))
			.put("<com.android.server.asks.ASKSManagerService: java.lang.String ROOT_CERT_FILE>", new StringConstantPart("/system/etc/ASKS_ROOT_1.crt"))
			.put("<android.content.pm.PackageInfo: java.lang.String packageName>", new AnyInfoPart("PACKAGENAME"))
			.put("<com.android.server.pm.PersonaManagerService: java.io.File mPersonaCacheFile>", new StringConstantPart("/data/system/users/persona_cache.xml"))
			.put("<com.android.server.asks.ASKSManagerService: java.lang.String CA_CERT_PATH>", new StringConstantPart("/data/system/.aasa/AASApolicy/ASKS_INTER_"))
			.put("<com.android.server.asks.ASKSManagerService: java.lang.String CA_CERT_SYSTEM_PATH>", new StringConstantPart("/system/etc/ASKS_INTER_"))
			.put("<com.android.server.am.ServiceRecord: int definingUid>", new AnyUIDPart(null,null))
			.put("<com.android.server.am.freecess.FreecessPkgStatus: int uid>", new AnyUIDPart(null,null))
			.put("<com.android.org.conscrypt.TrustedCertificateStore: java.io.File systemDir>", new StringConstantPart("/system/etc/security/cacerts"))
			.put("<com.android.org.conscrypt.TrustedCertificateStore: java.io.File addedDir>", new StringConstantPart("/data/misc/keychain/cacerts-added"))
			.put("<com.android.org.conscrypt.TrustedCertificateStore: java.io.File deletedDir>", new StringConstantPart("/data/misc/keychain/cacerts-removed"))
			
			.build();
	
	private Part fixTVDevice(PHPart seed, Part org) {
		Set<String> tvseeds = ImmutableSet.<String>builder()
				.add("<com.android.server.tv.TvInputManagerService$BinderService: java.util.List getDvbDeviceList()>")
				.add("<com.android.server.tv.TvInputManagerService$BinderService: android.os.ParcelFileDescriptor openDvbDevice(android.media.tv.DvbDeviceInfo,int)>")
				.build();
		
		if(seed instanceof PHBaseValuePart && tvseeds.contains(((PHBaseValuePart)seed).getSource().getSource().getSignature())) {
			Part[] ret = {org};
			org.getPostOrderIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
				public void accept(Pair<Part,Node> t) {
					Part parent = t.getFirst();
					Node childNode = t.getSecond();
					Part child = childNode.getPart();
					if(child instanceof StringConstantPart && ((StringConstantPart)child).getValue().equals("/dev")) {
						Part p = new StringConstantPart("/dev/dvb");
						if(p != null) {
							if(parent == null)
								ret[0] = p;
							else
								((BranchPart)parent).swapChild(childNode, p);
						}
					}
				}
			});
			return ret[0];
		}
		return org;
	}
			
	private Part forceSubFields(Part org) {
		Part[] ret = {org};
		org.getPostOrderIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
			public void accept(Pair<Part,Node> t) {
				Part parent = t.getFirst();
				Node childNode = t.getSecond();
				Part child = childNode.getPart();
				if(child instanceof AnyFieldRefPart) {
					Part p = fieldToValueMap.get(((AnyFieldRefPart)child).getFieldSig());
					if(p != null) {
						if(parent == null)
							ret[0] = p;
						else
							((BranchPart)parent).swapChild(childNode, p);
					}
				}
			}
		});
		return ret[0];
	}
	
	private static final Map<String,Part> targetMethodToValueMap = ImmutableMap.<String,Part>builder()
			.put("<android.os.Environment: java.io.File getDataDirectory()>", new StringConstantPart("/data"))
			.put("<android.os.Environment: java.io.File getRootDirectory()>", new StringConstantPart("/system"))
			.put("<android.os.Environment: java.io.File getOemDirectory()>", new StringConstantPart("/oem"))
			.put("<android.os.Environment: java.io.File getOdmDirectory()>", new StringConstantPart("/odm"))
			.put("<android.os.Environment: java.io.File getVendorDirectory()>", new StringConstantPart("/vendor"))
			.put("<android.os.Environment: java.io.File getProductDirectory()>", new StringConstantPart("/product"))
			.put("<android.os.Environment: java.io.File getProductServicesDirectory()>", new StringConstantPart("/product_services"))
			.put("<android.os.Environment: java.io.File getDataSystemDirectory()>", new StringConstantPart("/data/system"))
			.put("<android.os.Environment: java.io.File getDataPreloadsFileCacheDirectory()>", new StringConstantPart("/data/preloads/file_cache"))
			.put("<android.os.Environment: java.io.File getUserSystemDirectory(int)>", new StringConstantPart("/data/system/users/0"))
			.put("<android.os.Environment: java.io.File getDataSystemCeDirectory(int)>", new StringConstantPart("/data/system_ce/0"))
			.put("<android.os.Environment: java.io.File getDataSystemCeLegacyKnoxDirectory(int)>", new StringConstantPart("/data/knox/system_ce/0"))
			.put("<android.os.Environment: java.io.File getDataSystemDeDirectory(int)>", new StringConstantPart("/data/system_de/0"))
			.put("<android.os.Environment: java.io.File getDataSystemDeLegacyKnoxDirectory(int)>", new StringConstantPart("/data/knox/system_de/0"))
			.put("<android.os.Environment: java.io.File getDataAppDirectory(java.lang.String)>", new StringConstantPart("/data/app"))
			.put("<android.os.Environment: java.io.File getDataVendorDeDirectory(int)>", new StringConstantPart("/data/vendor_de/0"))
			.put("<android.os.Environment: java.io.File getDataVendorCeDirectory(int)>", new StringConstantPart("/data/vendor_ce/0"))
			.put("<android.os.UserHandle: int getUid(int,int)>", new AnyUIDPart(null,null))
			.put("<android.os.Binder: int getCallingUid()>", new AnyUIDPart(null,null))
			.put("<android.os.Process: int myUid()>", new AnyUIDPart(null,null))
			.build();
	
	private static final Map<String,Part> sourceMethodToValueMap = ImmutableMap.<String,Part>builder()
			.put("<com.android.internal.os.StoragedUidIoStatsReader: void readAbsolute(com.android.internal.os.StoragedUidIoStatsReader$Callback)>", new StringConstantPart("/proc/uid_io/stats"))
			.put("<com.android.server.appop.HistoricalRegistry$Persistence: void handlePersistHistoricalOpsRecursiveDLocked(java.io.File,java.io.File,java.util.List,java.util.Set,int)>", new StringConstantPart("/data/system/appops/history"))
			.build();
	
	private Part forceSubMethods(Part org) {
		Part[] ret = {org};
		org.getPostOrderIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
			public void accept(Pair<Part,Node> t) {
				Part parent = t.getFirst();
				Node childNode = t.getSecond();
				Part child = childNode.getPart();
				if(child instanceof AnyMethodReturnPart) {
					Part p = targetMethodToValueMap.get(((AnyMethodReturnPart)child).getTargetMethodSig());
					if(p != null) {
						if(parent == null)
							ret[0] = p;
						else
							((BranchPart)parent).swapChild(childNode, p);
					} else {
						Part p2 = sourceMethodToValueMap.get(((AnyMethodReturnPart)child).getSourceMethodSig());
						if(p2 != null) {
							if(parent == null)
								ret[0] = p2;
							else
								((BranchPart)parent).swapChild(childNode, p2);
						}
					}
				}
			}
		});
		return ret[0];
	}
	
	private Part assumeSingleUser(Part org) {
		Part[] ret = {org};
		Set<String> userIdMethodSigs = ImmutableSet.<String>of(
				"<android.os.UserHandle: int myUserId()>", 
				"<android.os.UserHandle: int getCallingUserId()>",
				"<android.os.UserHandle: int getUserId(int)>",
				"<android.app.ActivityManager: int getCurrentUser()>",
				"<com.samsung.android.knox.sdp.SdpUtil: int extractAndroidDefaultUserId(java.lang.String)>"
				);
		Set<String> userIdFieldSigs = ImmutableSet.<String>of(
				"<com.android.server.accounts.AccountManagerService$UserAccounts: int userId>",
				"<com.android.server.backup.UserBackupManagerService: int mUserId>",
				"<com.android.server.devicepolicy.Owners: int mDeviceOwnerUserId>",
				"<com.android.server.inputmethod.InputMethodUtils$InputMethodSettings: int mCurrentUserId>",
				"<com.android.server.pm.PackageInstallerSession: int userId>",
				"<com.android.server.tv.TvInputManagerService: int mCurrentUserId>",
				"<com.android.server.tv.TvInputManagerService$ServiceCallback: int mUserId>",
				"<com.android.server.tv.TvInputManagerService$SessionState: int userId>",
				"<com.android.server.wallpaper.WallpaperManagerService$WallpaperData: int userId>",
				"<com.android.server.wm.WindowManagerService: int mCurrentUserId>",
				"<com.samsung.android.knox.sdp.core.SdpEngineInfo: int mId>"
				);
		org.getPostOrderIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
			public void accept(Pair<Part,Node> t) {
				Part parent = t.getFirst();
				Node childNode = t.getSecond();
				Part child = childNode.getPart();
				if(child instanceof AnyUserIdPart //Main Indicator
						|| (child instanceof ConstantPart && isNegativeInt(child.toString())) //Comes from when a userid variable is initilized before being given a valid value
						|| (child instanceof AnyMethodReturnPart && userIdMethodSigs.contains(((AnyMethodReturnPart)child).getTargetMethodSig()))
						|| (child instanceof AnyPartImpl && userIdMethodSigs.contains(((AnyPartImpl)child).getSourceMethodSig()))
						|| (child instanceof AnyFieldRefPart && userIdFieldSigs.contains(((AnyFieldRefPart)child).getFieldSig()))
						|| (child instanceof AnyNumberPart && userIdMethodSigs.contains(((AnyNumberPart)child).getSourceMethodSig()))
						) {
					Part p = new StringConstantPart("0");
					if(parent == null)
						ret[0] = p;
					else
						((BranchPart)parent).swapChild(childNode, p);
				}
			}
		});
		return ret[0];
	}
	
	private boolean isNegativeInt(String strNum) {
		if (strNum == null)
		    return false;
		try {
		    int i = Integer.parseInt(strNum);
		    if(i < 0 || i == 10)
		    	return true;
		    return false;
		} catch (Throwable e) {
		    return false;
		}
	}
	
	private Part removeLoopsAndNormalize(Part org) {
		Part[] ret = {org};
		org.getPostOrderIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
			public void accept(Pair<Part,Node> t) {
				Part parent = t.getFirst();
				Node childNode = t.getSecond();
				Part child = childNode.getPart();
				if(parent == null) {
					if(child instanceof LoopPart) {
						ret[0] = nullConstant;
					} else if(child instanceof OrPart) {
						List<Node> children = ((OrPart)child).getChildNodes();
						if(children.isEmpty())
							ret[0] = nullConstant;
						else if(children.size() == 1)
							ret[0] = children.get(0).getPart();
					} else if(child instanceof AppendPart) {
						List<Node> children = ((AppendPart)child).getChildNodes();
						if(children.isEmpty())
							ret[0] = nullConstant;
						else if(children.size() == 1)
							ret[0] = children.get(0).getPart();
					} else if(child instanceof NamePart || child instanceof ParentPart 
							|| child instanceof EnvVarPart || child instanceof SysVarPart) {
						if(((BranchPart)child).getChildren().isEmpty())
							ret[0] = nullConstant;
					} else if(child instanceof NormalizePart) {
						if(((BranchPart)child).getChildren().isEmpty())
							ret[0] = nullConstant;
						else
							ret[0] = ((NormalizePart)child).getChild();
					}
				} else if(parent instanceof BranchPart) {
					if(child instanceof LoopPart) {
						((BranchPart)parent).removeChild(childNode);
					} else if(child instanceof OrPart) {
						List<Node> children = ((OrPart)child).getChildNodes();
						if(children.isEmpty())
							((BranchPart)parent).removeChild(childNode);
						else if(children.size() == 1)
							((BranchPart)parent).swapChild(childNode, children.get(0));
						else //If the parent is also an or child then it will merge otherwise nothing happens
							((BranchPart)parent).mergeChild(childNode);
					} else if(child instanceof AppendPart) {
						List<Node> children = ((AppendPart)child).getChildNodes();
						if(children.isEmpty())
							((BranchPart)parent).removeChild(childNode);
						else if(children.size() == 1)
							((BranchPart)parent).swapChild(childNode, children.get(0));
						else //If the parent is also an or child then it will merge otherwise nothing happens
							((BranchPart)parent).mergeChild(childNode);
					} else if(child instanceof NamePart || child instanceof ParentPart 
							|| child instanceof EnvVarPart || child instanceof SysVarPart) {
						if(((BranchPart)child).getChildren().isEmpty())
							((BranchPart)parent).removeChild(childNode);
					} else if(child instanceof NormalizePart) {
						if(((BranchPart)child).getChildren().isEmpty())
							((BranchPart)parent).removeChild(childNode);
						else
							((BranchPart)parent).swapChild(childNode, ((NormalizePart)child).getChildNode());
					}
				}
			}
		});
		return ret[0];
	}
	
	//Must be run after loops and normalization have been removed
	private Part convertToDNF(Part orgPart) {
		Deque<Node> queue = new ArrayDeque<>();
		Map<Node,List<List<Node>>> data = new HashMap<>();
		Set<Node> leafs = new HashSet<>();
		Map<Node, List<List<Node>>> newLeafs = new HashMap<>();
		Node org = new Node(orgPart);
		queue.add(org);
		while(!queue.isEmpty()) {
			Node cur = queue.poll();
			Part curPart = cur.getPart();
			if(curPart instanceof BranchPart) {
				if(curPart instanceof OrPart) {
					List<List<Node>> curData = new ArrayList<>();
					for(Node n : ((BranchPart)curPart).getChildNodes()) {
						queue.add(n);
						curData.add(Lists.newArrayList(n));
					}
					data.put(cur, curData);
				} else if(curPart instanceof AppendPart) {
					List<Node> children = ((BranchPart)curPart).getChildNodes();
					queue.addAll(children);
					List<List<Node>> temp = new ArrayList<>();
					temp.add(children);
					data.put(cur, temp);
				} else if(curPart instanceof SysVarPart) {
					Node childNode = ((SysVarPart)curPart).getChildNode();
					Part newChild = convertToDNF(childNode.getPart());
					//Child is an or part containing a DNF or something we consider a leaf
					if(newChild instanceof OrPart) {
						List<List<Node>> curData = new ArrayList<>();
						for(Node n : ((BranchPart)newChild).getChildNodes()) {
							Node newLeaf = new Node(new SysVarPart(n.getPart()));
							leafs.add(newLeaf);
							curData.add(Lists.newArrayList(newLeaf));
						}
						newLeafs.put(cur, curData);
					} else {
						List<List<Node>> temp = new ArrayList<>();
						Node newLeaf = new Node(new SysVarPart(newChild));
						if(!newLeaf.getPart().equals(cur.getPart())) {
							temp.add(Lists.newArrayList(newLeaf));
							newLeafs.put(cur, temp);
							leafs.add(newLeaf);
						} else {
							leafs.add(cur);
						}
					}
				} else if(curPart instanceof EnvVarPart) {
					Node childNode = ((EnvVarPart)curPart).getChildNode();
					Part newChild = convertToDNF(childNode.getPart());
					//Child is an or part containing a DNF or something we consider a leaf
					if(newChild instanceof OrPart) {
						List<List<Node>> curData = new ArrayList<>();
						for(Node n : ((BranchPart)newChild).getChildNodes()) {
							Node newLeaf = new Node(new EnvVarPart(n.getPart()));
							leafs.add(newLeaf);
							curData.add(Lists.newArrayList(newLeaf));
						}
						newLeafs.put(cur, curData);
					} else {
						List<List<Node>> temp = new ArrayList<>();
						Node newLeaf = new Node(new EnvVarPart(newChild));
						if(!newLeaf.getPart().equals(cur.getPart())) {
							temp.add(Lists.newArrayList(newLeaf));
							newLeafs.put(cur, temp);
							leafs.add(newLeaf);
						} else {
							leafs.add(cur);
						}
					}
				} else if(curPart instanceof ParentPart) {
					Node childNode = ((ParentPart)curPart).getChildNode();
					Part newChild = convertToDNF(childNode.getPart());
					//Child is an or part containing a DNF or something we consider a leaf
					if(newChild instanceof OrPart) {
						List<List<Node>> curData = new ArrayList<>();
						for(Node n : ((BranchPart)newChild).getChildNodes()) {
							Node newLeaf = new Node(new ParentPart(n.getPart()));
							leafs.add(newLeaf);
							curData.add(Lists.newArrayList(newLeaf));
						}
						newLeafs.put(cur, curData);
					} else {
						List<List<Node>> temp = new ArrayList<>();
						Node newLeaf = new Node(new ParentPart(newChild));
						if(!newLeaf.getPart().equals(cur.getPart())) {
							temp.add(Lists.newArrayList(newLeaf));
							newLeafs.put(cur, temp);
							leafs.add(newLeaf);
						} else {
							leafs.add(cur);
						}
					}
				} else if(curPart instanceof NamePart) {
					Node childNode = ((NamePart)curPart).getChildNode();
					Part newChild = convertToDNF(childNode.getPart());
					//Child is an or part containing a DNF or something we consider a leaf
					if(newChild instanceof OrPart) {
						List<List<Node>> curData = new ArrayList<>();
						for(Node n : ((BranchPart)newChild).getChildNodes()) {
							Node newLeaf = new Node(new NamePart(n.getPart()));
							leafs.add(newLeaf);
							curData.add(Lists.newArrayList(newLeaf));
						}
						newLeafs.put(cur, curData);
					} else {
						List<List<Node>> temp = new ArrayList<>();
						Node newLeaf = new Node(new NamePart(newChild));
						if(!newLeaf.getPart().equals(cur.getPart())) {
							temp.add(Lists.newArrayList(newLeaf));
							newLeafs.put(cur, temp);
							leafs.add(newLeaf);
						} else {
							leafs.add(cur);
						}
					}
				} else {
					throw new RuntimeException("Error: Encountered unhandled BranchPart of type '" + curPart.getClass().getSimpleName() + "'");
				}
			} else if(curPart instanceof LeafPart) {
				leafs.add(cur); //No changes to node so nothing to sub
			} else {
				throw new RuntimeException("Error: Encountered unhandled type '" + curPart.getClass().getSimpleName() + "'");
			}
		}
		
		if(leafs.contains(org)) {
			return org.getPart();
		} else {
			for(Iterator<Node> it = data.keySet().iterator(); it.hasNext();) {
				Node root = it.next();
				List<List<Node>> lists = data.get(root);
				boolean allLeafs = true;
				for(List<Node> list : lists) {
					for(Node n : list) {
						if(!leafs.contains(n))
							allLeafs = false;
					}
				}
				if(allLeafs) {
					newLeafs.put(root,lists);
					it.remove();
				}
			}
			
			Map<Node, List<List<Node>>> curLeafs = newLeafs;
			while(!newLeafs.isEmpty()) {
				curLeafs = newLeafs;
				newLeafs = new HashMap<>();
				Set<Node> wasUpdated = new HashSet<>();
				for(Node leaf : curLeafs.keySet()) {
					List<List<Node>> listsToInsert = curLeafs.get(leaf);
					for(Node root : data.keySet()) {
						List<List<Node>> lists = data.get(root);
						for(int j = 0; j < lists.size(); j++) {
							List<Node> list = lists.get(j);
							int i = list.indexOf(leaf);
							if(i >= 0) {
								for(List<Node> toInsert : listsToInsert) {
									List<Node> newList = new ArrayList<>(list);
									newList.addAll(i, toInsert);
									newList.remove(leaf);
									lists.add(newList);
								}
								lists.remove(j);
								j--;
								wasUpdated.add(root);
							}
						}
					}
				}
				
				for(Node root : wasUpdated) {
					List<List<Node>> lists = data.get(root);
					boolean allLeafs = true;
					for(List<Node> list : lists) {
						for(Node n : list) {
							if(!leafs.contains(n))
								allLeafs = false;
						}
					}
					if(allLeafs) {
						newLeafs.put(root,lists);
						data.remove(root);
					}
				}
			}
			
			if(!data.isEmpty() || !curLeafs.containsKey(org))
				throw new RuntimeException("Error: Exited subbing loop without subbing everything.");
			
			List<List<Node>> finalLists = curLeafs.get(org);
			OrPart ret = new OrPart();
			for(List<Node> list : finalLists) {
				AppendPart app = new AppendPart();
				for(Node n : list)
					app.add(n.getPart());
				if(app.size() == 1)
					ret.add(app.getChildren().get(0));
				else
					ret.add(app);
			}
			
			if(ret.size() == 1)
				return ret.getChildren().get(0);
			return ret;
		}
		
	}
}
