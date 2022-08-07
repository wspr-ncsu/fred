package org.sag.fred.phases.fred;

import java.io.BufferedReader;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sag.acminer.database.acminer.Doublet;
import org.sag.acminer.database.acminer.IACMinerDatabase;
import org.sag.acminer.database.excludedelements.IExcludeHandler;
import org.sag.acminer.phases.entrypoints.EntryPoint;
import org.sag.fred.phases.fred.Permission;
import org.sag.fred.phases.fred.SystemAndroidManifest;
import org.sag.common.io.FileHelpers;
import org.sag.common.io.PrintStreamUnixEOL;
import org.sag.common.logging.ILogger;
import org.sag.common.logging.LoggerWrapperSLF4J;
import org.sag.common.tools.SortingMethods;
import org.sag.common.tuple.Pair;
import org.sag.main.AndroidInfo;
import org.sag.main.config.Config;
import org.sag.main.logging.CentralLogger;
import org.sag.main.phase.PhaseManager;
import org.sag.soot.analysis.AdvLocalDefs;
import org.sag.soot.xstream.SootClassContainer;
import org.sag.soot.xstream.SootMethodContainer;
import org.sag.soot.xstream.SootUnitContainer;
import org.sag.fred.IFredDataAccessor;
import org.sag.fred.database.filepaths.EntryPointContainer;
import org.sag.fred.database.filepaths.IFilePathsDatabase;
import org.sag.fred.database.filepaths.parts.PHPart;
import org.sag.fred.database.filepaths.parts.Part;
import org.sag.fred.database.ssfiles.FileEntry;
import org.sag.fred.database.ssfiles.Owner;
import org.sag.fred.database.ssfiles.SecuritySensitiveFilesDatabase;

import com.google.common.collect.ImmutableSet;
import soot.Body;
import soot.Local;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.BinopExpr;
import soot.jimple.DefinitionStmt;
import soot.jimple.IfStmt;
import soot.jimple.NewExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.LiveLocals;

public class FredAnalysis {
	
	private volatile ILogger logger;
	private final String cn;
	private final Config config;
	private AndroidInfo ai;
	private IACMinerDatabase acminerDB;
	private IFilePathsDatabase filePathsDB;
	private SecuritySensitiveFilesDatabase ssFilesDB;
	private SystemAndroidManifest systemAndroidManifest;
	private Set<String> registeredServices;
	private Map<String,Pair<Set<String>,Set<String>>> specialCallerContextQueries;
	
	public FredAnalysis() {
		this.logger = new LoggerWrapperSLF4J(this.getClass());;
		this.cn = getClass().getSimpleName();
		this.ai = null;
		config = Config.getConfigFromResources(logger);
		//this.excludeSystemProtectedEps = true;
	}
	
	public boolean init(String[] args) {
		try {
			logger.info("{}: Initilizing with the arguments\n    {}",cn,Arrays.toString(args));
			if(parseAndSetArguments(args)) {
				logger.info("{}: Verifying options and performing further initilization procedures.",cn);
				if(verifyOptions()) {
					logger.info("{}: Options verified successfully. Initilized successfully.",cn);
					return true;
				}
			}
			return false;
		} catch(Throwable t) {
			logger.fatal("{}: Something went wrong when initilizing with the arguments\n    {}",t,cn,Arrays.toString(args));
			return false;
		}
	}
	
	//A return of false here means do not continue because either the help dialog was requested or an error occurred
	private boolean parseAndSetArguments(String[] args) {
		if(args == null || args.length <= 0) {
			logger.info("{}: No arguments to parse. Skipping parsing procedure.",cn);
		} else {
			logger.info("{}: Parsing arguments and setting options...",cn);
			for(int i = 0; i < args.length; i++) {
				switch(args[i]){
					case "-h":
					case "--help":
						logger.info("{}: Help dialog requested. Outputting dialog then exiting.\n\n"
								+ "Written by Sigmund A. Gorski III.\n{}",cn,"");
						return false;
					case "-i":
						String inPath = args[++i];
						if(inPath.length() > 0 && inPath.charAt(inPath.length()-1) == File.separatorChar)
							inPath = inPath.substring(0, inPath.length()-1);
						config.setFilePathEntry("work-dir", inPath);
						break;
					default:
						logger.fatal("{}: Invalid input '{}'.",cn,args[i]);
						return false;
				}
			}
		}
		logger.info("{}: All arguments were parsed successfully.",cn);
		return true;
	}
	
	private boolean verifyOptions() {
		if(verifyAndSetInput()) {
			return verifyAndSetOutput();
		}
		return false;
	}
	
	private boolean verifyAndSetInput() {
		Path androidInfo = config.getFilePath("work_android-info-file");
		try {
			FileHelpers.verifyRWFileExists(androidInfo);
		} catch(Throwable t) {
			logger.fatal("{}: Could not access the AndroidInfo file at '{}'.",t,cn,
					androidInfo);
			return false;
		}
		
		try {
			ai = AndroidInfo.readXMLStatic(null, androidInfo);
		} catch (Throwable t) {
			logger.fatal("{}: Could not read in the AndroidInfo file at '{}'.",t,cn,
					androidInfo);
			return false;
		}
		
		Path acminerDBFile = config.getFilePath("acminer_acminer_db-file");
		try {
			FileHelpers.verifyRWFileExists(acminerDBFile);
		} catch(Throwable t) {
			logger.fatal("{}: Could not access the IACMinerDatabase file at '{}'.",t,cn,
					acminerDBFile);
			return false;
		}
		
		try {
			acminerDB = IACMinerDatabase.Factory.readXML(null, 
					acminerDBFile);
		} catch (Throwable t) {
			logger.fatal("{}: Could not read in the IACMinerDatabase file at '{}'.",t,cn,
					acminerDBFile);
			return false;
		}
		
		Path filePathsDBFile = config.getFilePath("fred_file-paths-db-file");
		try {
			FileHelpers.verifyRWFileExists(filePathsDBFile);
		} catch(Throwable t) {
			logger.fatal("{}: Could not access the IFilePathsDatabase file at '{}'.",t,cn,
					filePathsDBFile);
			return false;
		}
		
		try {
			filePathsDB = IFilePathsDatabase.Factory.readXML(null, filePathsDBFile);
		} catch (Throwable t) {
			logger.fatal("{}: Could not read in the IFilePathsDatabase file at '{}'.",t,cn,
					filePathsDBFile);
			return false;
		}
		
		Path ssFilesDBFile = config.getFilePath("fred_security-sensitive-files-db-file");
		try {
			FileHelpers.verifyRWFileExists(ssFilesDBFile);
		} catch(Throwable t) {
			logger.fatal("{}: Could not access the SecuritySensitiveFilesDatabase file at '{}'.",t,cn,
					ssFilesDBFile);
			return false;
		}
		
		try {
			ssFilesDB = SecuritySensitiveFilesDatabase.readXMLStatic(null, ssFilesDBFile);
		} catch (Throwable t) {
			logger.fatal("{}: Could not read in the SecuritySensitiveFilesDatabase file at '{}'.",t,cn,
					ssFilesDBFile);
			return false;
		}
		
		Path androidManifestFile = config.getFilePath("work_system-android-manifest-file");
		try {
			FileHelpers.verifyRWFileExists(androidManifestFile);
		} catch(Throwable t) {
			logger.fatal("{}: Could not access the system AndroidManifest file at '{}'.",t,cn,
					androidManifestFile);
			return false;
		}
		
		try {
			systemAndroidManifest = SystemAndroidManifest.readXMLStatic(null, androidManifestFile);
		} catch(Throwable t) {
			logger.fatal("{}: Could not read the system AndroidManifest file at '{}'.",t,cn,
					androidManifestFile);
			return false;
		}
		
		Path regServicesFile = config.getFilePath("acminer_registered-services-temp-file");
		try {
			FileHelpers.verifyRWFileExists(regServicesFile);
		} catch(Throwable t) {
			logger.fatal("{}: Could not access the registered services file at '{}'.",t,cn,
					regServicesFile);
			return false;
		}
		
		try {
			try(BufferedReader br = Files.newBufferedReader(regServicesFile)) {
				this.registeredServices = new HashSet<>();
				String line;
				while((line = br.readLine()) != null) {
					line = line.trim();
					if(!line.isEmpty() && !line.startsWith("//"))
						this.registeredServices.add(line);
				}
			}
			this.registeredServices = SortingMethods.sortSet(this.registeredServices,SortingMethods.sComp);
		} catch(Throwable t) {
			logger.fatal("{}: Could not read the registered services file at '{}'.",t,cn,
					regServicesFile);
			return false;
		}
		
		Path specialCallers = config.getFilePath("arf_special-caller-context-queries-temp-file");
		try(BufferedReader br = Files.newBufferedReader(specialCallers)) {
			specialCallerContextQueries = new HashMap<>();
			String line;
			while((line = br.readLine()) != null) {
				line = line.trim();
				if(!line.isEmpty() && !line.startsWith("//")) {
					String[] temp = line.split("\\t");
					Pair<Set<String>,Set<String>> p = specialCallerContextQueries.get(temp[0].trim());
					if(p == null) {
						p = new Pair<>(new HashSet<>(),new HashSet<>());
						specialCallerContextQueries.put(temp[0].trim(),p);
					}
					p.getFirst().add(temp[1].trim());
					if(temp.length > 2) {
						for(int i = 2; i < temp.length; i++) {
							p.getSecond().add(temp[i].trim());
						}
					}
				}
			}
		} catch(Throwable t) {
			logger.fatal("{}: Could not read the special caller context query file at '{}'.",t,cn,
					specialCallers);
			return false;
		}
		
		return true;
	}
	
	private boolean verifyAndSetOutput(){
		Path debugDir = config.getFilePath("debug-dir");
		try {
			FileHelpers.processDirectory(debugDir, true, false);
		} catch(Throwable t) {
			logger.fatal("{}: Could not access the debug directory '{}'.",t,cn,debugDir);
			return false;
		}
		
		Path fredFredDir = config.getFilePath("fred_fred-dir");
		try {
			FileHelpers.processDirectory(fredFredDir, true, false);
		} catch(Throwable t) {
			logger.fatal("{}: Could not access the fred fred directory '{}'.",t,cn,fredFredDir);
			return false;
		}
		
		Path outDir = config.getFilePath("debug_fred-dir");
		try {
			FileHelpers.processDirectory(outDir, true, false);
		} catch(Throwable t) {
			logger.fatal("{}: Could not access analysis_out output directory '{}'.",t,cn,
					outDir);
			return false;
		}
		
		Path logDir = config.getFilePath("log-dir");
		try {
			FileHelpers.processDirectory(logDir, true, false);
		} catch(Throwable t) {
			logger.fatal("{}: Could not access log directory '{}'.",t,cn,
					logDir);
			return false;
		}
		
		logger.info("{}: Starting the main logger.",cn);
		ILogger mainLogger = null;
		try {
			CentralLogger.setLogDir(logDir);
			CentralLogger.setMainLogFile(CentralLogger.getLogPath("MainLog"));
			CentralLogger.setAndroidInfo(ai);
			mainLogger = CentralLogger.startLogger(this.getClass().getName(), CentralLogger.getMainLogFile(), 
					CentralLogger.getLogLevelMain(), true);
		} catch(Throwable t) {
			logger.fatal("{}: Failed to start the main logger.",t,cn);
			return false;
		}
		if(mainLogger == null){
			logger.fatal("{}: Failed to start the main logger.",cn);
			return false;
		}
		logger.info("{}: Switching to main logger.",cn);
		logger.close();
		logger = mainLogger;
		logger.info("{}: Successfully switched to main logger.",cn);
		return true;
	}
	
	public static void main(String[] args) {
		FredAnalysis anal = new FredAnalysis();
		if(anal.init(args)) {
			anal.run();
		}
	}

	private void removeIfProtectedBySpecialCallerContextQueries(MatchesDatabase thirdPartydDB, MatchesDatabase systemRestrictedDB, 
			IFredDataAccessor dataAccessor) {
		CallGraph cg = Scene.v().getCallGraph();
	
		for(Iterator<EntryPointNode> it = thirdPartydDB.getData().keySet().iterator(); it.hasNext();) {
			EntryPointNode deputy = it.next();
			
			boolean hasSpecialCallerContextQuery = false;
			Pair<Set<String>,Set<String>> p = specialCallerContextQueries.get(deputy.getEntryPoint().getDeclaringClass());
			if(p != null) {
				Set<String> specialCallers = p.getFirst();
				Set<SootMethod> visited = new HashSet<>();
				ArrayDeque<SootMethod> toVisit = new ArrayDeque<>();
				EntryPoint ep = deputy.getSootEntryPoint();
				//Assume 1-1 mapping between method and entry point which should be true because Binder methods have been removed
				IExcludeHandler excludeHandler = dataAccessor.getExcludedElementsDB().createNewExcludeHandler(ep);
				toVisit.add(ep.getEntryPoint());
				while(!toVisit.isEmpty()) {
					SootMethod cur = toVisit.poll();
					if(specialCallers.contains(cur.toString())) {
						hasSpecialCallerContextQuery = true;
						break;
					}
					if(visited.add(cur) && !excludeHandler.isExcludedMethodWithOverride(cur)) {
						Iterator<Edge> itit = cg.edgesOutOf(cur);
						while(itit.hasNext()) {
							Edge e = itit.next();
							toVisit.add(e.tgt());
						}
					}
				}
			}
			
			if(hasSpecialCallerContextQuery) {
				systemRestrictedDB.addAll(deputy,thirdPartydDB.getData().get(deputy));
				it.remove();
			}
		}
	}
	
	private void removeIfFirstIfIsSystemRestricting(MatchesDatabase thirdPartydDB, MatchesDatabase systemRestrictedDB) {
		for(Iterator<EntryPointNode> it = thirdPartydDB.getData().keySet().iterator(); it.hasNext();) {
			EntryPointNode deputy = it.next();
			
			boolean keep = true;
			Body b = deputy.getEntryPoint().toSootMethod().retrieveActiveBody();
			UnitGraph g = new BriefUnitGraph(b);
			Set<IfStmt> firstIfs = new HashSet<>();
			
			//The first if throws a security exception
			for(Unit u : b.getUnits()) {
				if(u instanceof IfStmt) {
					firstIfs.add((IfStmt)u);
					break;
				}
			}
			
			//Some number of check Preconditions.checkArgument functions occur before the actual first if
			//the others are used as part of this method
			int numOfIf = 0;
			int numOfInvoke = 0;
			for(Unit u : b.getUnits()) {
				if(((Stmt)u).containsInvokeExpr() && 
						((Stmt)u).getInvokeExpr().getMethodRef().getSignature().equals("<com.android.internal.util.Preconditions: void checkArgument(boolean,java.lang.Object)>")) {
					numOfInvoke++;
				} else if(u instanceof IfStmt) {
					if(numOfIf == numOfInvoke) {
						firstIfs.add((IfStmt)u);
						numOfIf++;
					} else {
						break;
					}
				}
			}
			
			
			if(!firstIfs.isEmpty()) {
				boolean hasSE = false;
				for(IfStmt firstIf : firstIfs) {
					IfStmt cur = firstIf;
					while(cur != null) {
						IfStmt next = null;
						for(Unit succ : g.getSuccsOf(cur)) {
							if(succ instanceof DefinitionStmt) {
								Value v = ((DefinitionStmt)succ).getRightOp();
								if(v instanceof NewExpr && ((NewExpr)v).getBaseType().toString().equals("java.lang.SecurityException")) {
									hasSE = true;
									break;
								}
							} else if(succ instanceof IfStmt) {
								next = (IfStmt)succ;
							}
						}
						if(hasSE)
							break;
						else if(next != null)
							cur = next;
						else
							cur = null;
					}
					if(hasSE)
						break;
				}
				if(hasSE)
					keep = false;
			}
			
			if(keep) {
				IfStmt firstIf = null;
				Stmt invokeStmt = null;
				for(Unit u : b.getUnits()) {
					if(u instanceof IfStmt && firstIf == null) {
						firstIf = (IfStmt)u;
					} else if(((Stmt)u).containsInvokeExpr() && 
							((Stmt)u).getInvokeExpr().getMethodRef().getSignature().equals("<com.android.internal.util.Preconditions: void checkArgument(boolean,java.lang.Object)>") && invokeStmt == null) {
						invokeStmt = (Stmt)u;
					}
				}
				if(firstIf != null && invokeStmt != null) {
					Value v = invokeStmt.getInvokeExpr().getArg(0);
					boolean areLinked = false;
					if(v instanceof Local) {
						for(Unit succ : g.getSuccsOf(firstIf)) {
							if(succ instanceof DefinitionStmt && ((DefinitionStmt)succ).getLeftOp().equals(v)) {
								areLinked = true;
							}
						}
					}
					if(areLinked) {
						Value cond = firstIf.getCondition();
						if(cond instanceof BinopExpr) {
							AdvLocalDefs f = new AdvLocalDefs(g,LiveLocals.Factory.newLiveLocals(g));
							List<Value> ops = new ArrayList<>();
							ops.add(((BinopExpr)cond).getOp1());
							ops.add(((BinopExpr)cond).getOp2());
							for(Value op : ops) {
								if(op instanceof Local) {
									for(Unit u : f.getDefsOfAt((Local)op, firstIf)) {
										DefinitionStmt def = ((DefinitionStmt)u);
										if(def.containsInvokeExpr() && def.getInvokeExpr().getMethodRef().name().equals("binderGetCallingUid")) {
											keep = false;
											break;
										}
									}
								}
								if(!keep)
									break;
							}
						}
					}
				}
			}
			
			if(!keep) {
				systemRestrictedDB.addAll(deputy,thirdPartydDB.getData().get(deputy));
				it.remove();
			}
		}
	}
	
	private void removeIfNotCallableFromThirdParty(IFredDataAccessor dataAccessor,
                                                   MatchesDatabase thirdPartydDB, MatchesDatabase systemRestrictedDB) {
		Set<String> allowedProtectionLevels = ImmutableSet.of("normal","dangerous","instant","runtime","pre23");
		for(Iterator<EntryPointNode> it = thirdPartydDB.getData().keySet().iterator(); it.hasNext();) {
			EntryPointNode ep = it.next();
			Set<String> acPerms = ep.getPermissions();
			
			boolean hasSystemPermission = false;
			for(String perm : acPerms) {
				Permission permission = systemAndroidManifest.getPermission(perm);
				if(permission != null) {
					boolean hasUserProtectionLevel = false;
					Set<String> protectionLevels = permission.getProtectionLevels();
					for(String s : allowedProtectionLevels) {
						if(protectionLevels.contains(s))
							hasUserProtectionLevel = true;
					}
					if(!hasUserProtectionLevel) {
						hasSystemPermission = true;
						break;
					}
				} else if(perm.equals("com.android.printspooler.permission.ACCESS_ALL_PRINT_JOBS")) {
					//Not defined in the system android manifest but in the app itself?
					//Defined as signature in Android 8.0.1
					//Used in the PrintManagerService
					hasSystemPermission = true;
					break;
				}
			}
			
			//Skip handleMessage fake entry points because we have no way of knowing if they have permissions
			//Remove unregistered services because they are not callable from a third party (or anyone really)
			if(hasSystemPermission || !registeredServices.contains(ep.getEntryPoint().getDeclaringClass())
					|| ep.getEntryPoint().getSignature().endsWith("void handleMessage(android.os.Message)>")) {
				systemRestrictedDB.addAll(ep,thirdPartydDB.getData().get(ep));
				it.remove();
			}
		}
		
		removeIfFirstIfIsSystemRestricting(thirdPartydDB,systemRestrictedDB);
		removeIfProtectedBySpecialCallerContextQueries(thirdPartydDB,systemRestrictedDB,dataAccessor);
	}
	
	/*private void doSystemRootMatching(Map<FileEntry,Owner> fileEntriesForSystemRoot, Map<String,DataContainer> results,
			Map<EntryPointNode, Set<String>> epsToPerms, Map<EntryPointNode, Set<Doublet>> epsToAuthLogic) {
		for(FileEntry fe : fileEntriesForSystemRoot.keySet()) {
			String fullPath = fe.getFullPath();
			for(DataContainer cont : results.values()) {
				if(cont.matchCount < 20 && cont.pattern.matcher(fullPath).matches()) {
					for(EntryPointNode ep : cont.data.keySet()) {
						Set<Doublet> logic = epsToAuthLogic.get(ep);
						if(logic == null)
							logic = Collections.emptySet();
						
						Set<Owner> owners = Collections.singleton(fileEntriesForSystemRoot.get(fe));
						Map<PHPart,Quad<List<ResultContainer>,List<ResultContainer>,List<ResultContainer>,List<ResultContainer>>> seedToResults = cont.data.get(ep);
						Map<PHPart,Pair<Part,Part>> matchInfo = cont.matchInfo.get(ep);
						for(PHPart seed : seedToResults.keySet()) {
							cont.matchCount++;
							Quad<List<ResultContainer>,List<ResultContainer>,List<ResultContainer>,List<ResultContainer>> quad = seedToResults.get(seed);
							Pair<Part,Part> info = matchInfo.get(seed);
							ResultContainer r = new ResultContainer(ep,fe,owners,seed,info.getFirst(),info.getSecond(),Collections.emptySet(),logic);
							quad.getFirst().add(r);
							logger.info("{}: Found match without system permissions:\n{}",cn,r.toString("  "));
						}
					}
				}
			}
		}
	}*/
	
	private void doRedelegation(MatchesDatabase dbIn, boolean isThirdParty,
			MatchesDatabase redelegation, MatchesDatabase notRdelegation) {
		for(Iterator<EntryPointNode> it = dbIn.getData().keySet().iterator(); it.hasNext();) {
			EntryPointNode ep = it.next();
			Set<String> acPerms = ep.getPermissions();
			for(RegexContainer r : dbIn.getData().get(ep)) {
				for(FileContainer f : r.getFiles()) {
					Set<String> missing = new HashSet<>(f.getPermissions());
					missing.removeAll(acPerms);
					
					MatchesDatabase db = null;
					if(!missing.isEmpty() || (isThirdParty && f.isSystem()))
						db = redelegation;
					else
						db = notRdelegation;
					
					for(IntermediateExpression ie : r.getIntermediateExpressions())
						db.addIntermediateExpression(ep, ie.getSeed(), ie.getSimpleMatchPath(), ie.getOriginalMatchPath());
					for(RegexContainer newR : db.getData().get(ep)) {
						if(r.equals(newR)) {
							FileContainer added = newR.addFile(f.getFileEntry(), f.getPermissions());
							if(!missing.isEmpty())
								added.setMissingPermissions(missing);
						}
					}
				}
			}
		}
	}
	
	public boolean run() {
		try {
			logger.info("{}: Starting the fred analysis.",cn);
			Map<EntryPointNode, Set<Doublet>> epsToAuthLogicOrg = getEpsToAuthLogic(acminerDB.getValuePairs());
			//The permission strings are stored in the EntryPointNode object
			Map<EntryPointNode, EntryPointNode> epsWithPerms = getEpsToPerms(keepOnlyPermString(epsToAuthLogicOrg));
			//Ensures that new EntryPointNodes are only created if we did not have permissions for them
			//So we know all EntryPointNodes will have permissions if the entry point has permissions
			Map<EntryPointNode, List<Pair<PHPart,Part>>> epToOrginalMatchingPaths = getEpsToPaths(filePathsDB.getOutputData(), epsWithPerms);
			
			logger.info("{}: Total Security Sensitive Files: {}",cn,ssFilesDB.getFilesToGroupsOrSystem().keySet().size());
			
			MatchesDatabase madb = null;
			MatchesDatabase mdb = null;
			MatchesDatabase noMatchesDB = null;
			MatchesDatabase removedMatchesDB = null;
			if(FileHelpers.checkRWFileExists(config.getFilePath("fred_fred_matches-db-file"))) {
				madb = MatchesDatabase.readXMLStatic(null, config.getFilePath("fred_fred_matches-all-db-file"));
				mdb = MatchesDatabase.readXMLStatic(null, config.getFilePath("fred_fred_matches-db-file"));
				noMatchesDB = MatchesDatabase.readXMLStatic(null, config.getFilePath("fred_fred_matches-no-db-file"));
				removedMatchesDB = MatchesDatabase.readXMLStatic(null, config.getFilePath("fred_fred_matches-removed-db-file"));
				madb.dump(config.getFilePath("debug_fred-dir"), "matches_all");
				mdb.dump(config.getFilePath("debug_fred-dir"), "matches");
				noMatchesDB.dump(config.getFilePath("debug_fred-dir"), "no_matches");
				removedMatchesDB.dump(config.getFilePath("debug_fred-dir"), "removed_matches");
			} else {
				PerformMatching pm = new PerformMatching(ssFilesDB, epToOrginalMatchingPaths, logger);
				pm.run();
				madb = pm.getMatchesAllDB();
				mdb = pm.getMatchesDB();
				noMatchesDB = pm.getNoMatchesDB();
				removedMatchesDB = pm.getRemovedMatchesDB();
				madb.dump(config.getFilePath("debug_fred-dir"), "matches_all");
				madb.writeXML(null, config.getFilePath("fred_fred_matches-all-db-file"));
				mdb.dump(config.getFilePath("debug_fred-dir"), "matches");
				mdb.writeXML(null, config.getFilePath("fred_fred_matches-db-file"));
				noMatchesDB.dump(config.getFilePath("debug_fred-dir"), "no_matches");
				noMatchesDB.writeXML(null, config.getFilePath("fred_fred_matches-no-db-file"));
				removedMatchesDB.dump(config.getFilePath("debug_fred-dir"), "removed_matches");
				removedMatchesDB.writeXML(null, config.getFilePath("fred_fred_matches-removed-db-file"));
			}
			
			Set<String> users = new HashSet<>();
			Set<String> groups = new HashSet<>();
			for(Owner o : ssFilesDB.getOutputData()) {
				if(o.isGroup())
					groups.add(o.getName());
				else
					users.add(o.getName());
			}
			Map<String,Set<FileContainer>> groupToFiles = new HashMap<>();
			Map<String,Set<FileContainer>> userToFiles = new HashMap<>();
			Map<String,Set<FileContainer>> bothToFiles = new HashMap<>();
			for(Iterator<EntryPointNode> it = mdb.getData().keySet().iterator(); it.hasNext();) {
				EntryPointNode ep = it.next();
				for(Iterator<RegexContainer> rit = mdb.getData().get(ep).iterator(); rit.hasNext();) {
					RegexContainer r = rit.next();
					for(FileContainer f : r.getFiles()) {
						FileEntry ff = f.getFileEntry();
						if(users.contains(ff.getUser())) {
							Set<FileContainer> files = userToFiles.get(ff.getUser());
							if(files == null) {
								files = new HashSet<>();
								userToFiles.put(ff.getUser(), files);
							}
							files.add(f);
							files = bothToFiles.get(ff.getUser());
							if(files == null) {
								files = new HashSet<>();
								bothToFiles.put(ff.getUser(), files);
							}
							files.add(f);
						}
						if(groups.contains(ff.getGroup())) {
							Set<FileContainer> files = groupToFiles.get(ff.getGroup());
							if(files == null) {
								files = new HashSet<>();
								groupToFiles.put(ff.getGroup(), files);
							}
							files.add(f);
							files = bothToFiles.get(ff.getGroup());
							if(files == null) {
								files = new HashSet<>();
								bothToFiles.put(ff.getGroup(), files);
							}
							files.add(f);
						}
					}
				}
			}
			try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath(config.getFilePath("debug_fred-dir"),
					"matches" + "_" + "bargraph" + ".txt")))) {
				ps.println("Users:");
				for(String user : users) {
					Set<FileContainer> files = userToFiles.get(user);
					if(files != null) {
						ps.println(user + " " + files.size());
					}
				}
				ps.println("Groups:");
				for(String group : groups) {
					Set<FileContainer> files = groupToFiles.get(group);
					if(files != null) {
						ps.println(group + " " + files.size());
					}
				}
				ps.println("Both:");
				for(String o : bothToFiles.keySet()) {
					ps.println(o + " " + bothToFiles.get(o).size());
				}
			}
			
			IFredDataAccessor dataAccessor = ((IFredDataAccessor)config.getNewDataAccessor());
			PhaseManager pm = config.getNewPhaseManager();
			pm.enablePhaseGroup("ACMiner");
			pm.setQuickOptionsFromInput("--ACMinerVariedCallGraphAnalysis");
			pm.init(dataAccessor, ai, logger);
			pm.run();
			
			MatchesDatabase thirdPartydDB = mdb.clone();
			MatchesDatabase systemRestrictedDB = new MatchesDatabase();
			removeIfNotCallableFromThirdParty(dataAccessor,thirdPartydDB,systemRestrictedDB);
			
			systemRestrictedDB.dump(config.getFilePath("debug_fred-dir"), "system_matches");
			systemRestrictedDB.writeXML(null, config.getFilePath("fred_fred_system-matches-db-file"));
			thirdPartydDB.dump(config.getFilePath("debug_fred-dir"), "third_party_matches");
			thirdPartydDB.writeXML(null, config.getFilePath("fred_fred_third-party-matches-db-file"));
			
			MatchesDatabase thirdPartyRedelegationDB = new MatchesDatabase();
			MatchesDatabase thirdPartyNotRedelegationDB = new MatchesDatabase();
			doRedelegation(thirdPartydDB, true,
					thirdPartyRedelegationDB, thirdPartyNotRedelegationDB);
			thirdPartyRedelegationDB.dump(config.getFilePath("debug_fred-dir"), "third_party_redelegation");
			thirdPartyRedelegationDB.writeXML(null, config.getFilePath("fred_fred_third-party-redelegation-db-file"));
			thirdPartyNotRedelegationDB.dump(config.getFilePath("debug_fred-dir"), "third_party_no_redelegation");
			thirdPartyNotRedelegationDB.writeXML(null, config.getFilePath("fred_fred_third-party-no-redelegation-db-file"));
			
			MatchesDatabase systemRedelegationDB = new MatchesDatabase();
			MatchesDatabase systemNotRedelegationDB = new MatchesDatabase();
			doRedelegation(systemRestrictedDB, false,
					systemRedelegationDB, systemNotRedelegationDB);
			systemRedelegationDB.dump(config.getFilePath("debug_fred-dir"), "system_redelegation");
			systemRedelegationDB.writeXML(null, config.getFilePath("fred_fred_system-redelegation-db-file"));
			systemNotRedelegationDB.dump(config.getFilePath("debug_fred-dir"), "system_no_redelegation");
			systemNotRedelegationDB.writeXML(null, config.getFilePath("fred_fred_system-no-redelegation-db-file"));
			
			return true;
		} catch(Throwable t) {
			logger.fatal("{}: Unexpected error occured during the fred analysis.",t,cn);
			return false;
		}
	}
	
	private Map<EntryPointNode, List<Pair<PHPart,Part>>> getEpsToPaths(Set<EntryPointContainer> outputData, Map<EntryPointNode,EntryPointNode> epsWithPerms) {
		Map<EntryPointNode, List<Pair<PHPart,Part>>> ret = new LinkedHashMap<>();
		for(EntryPointContainer ep : outputData) {
			EntryPointNode temp = new EntryPointNode(ep.getEntryPointContainer(),ep.getStubContainer());
			EntryPointNode cur = epsWithPerms.get(temp);
			if(cur == null)
				cur = temp;
			ret.put(cur,ep.getData());
		}
		return ret;
	}

	private Map<EntryPointNode, Set<Doublet>> getEpsToAuthLogic(
			Map<SootClassContainer, Map<SootMethodContainer, Set<Doublet>>> stubToEpsToAuthLogic) {
		Map<EntryPointNode, Set<Doublet>> ret = new HashMap<>();
		for(SootClassContainer stub : stubToEpsToAuthLogic.keySet()) {
			Map<SootMethodContainer, Set<Doublet>> epToAuthLogic = stubToEpsToAuthLogic.get(stub);
			for(SootMethodContainer ep : epToAuthLogic.keySet()) {
				ret.put(new EntryPointNode(ep,stub), epToAuthLogic.get(ep));
			}
		}
		for(EntryPointNode ep : ret.keySet()) {
			ret.put(ep, SortingMethods.sortSet(ret.get(ep)));
		}
		return SortingMethods.sortMapKeyAscending(ret);
	}
	
	private Map<EntryPointNode,EntryPointNode> getEpsToPerms(Map<EntryPointNode, Set<Doublet>> epsToAuthLogic) {
		Pattern p = Pattern.compile("^`\"([^`\"]+)\"`$");
		LinkedHashMap<EntryPointNode, EntryPointNode> ret = new LinkedHashMap<>();
		for(EntryPointNode ep : epsToAuthLogic.keySet()) {
			Set<String> perms = new LinkedHashSet<>();
			for(Doublet d : epsToAuthLogic.get(ep)) {
				String perm = d.toString();
				Matcher m = p.matcher(perm);
				if(m.matches())
					perm = m.group(1);
				perms.add(perm);
			}
			ep.setPermissions(perms);
			ret.put(ep, ep);
		}
		return SortingMethods.sortMapKeyAscending(ret);
	}
	
	private Map<EntryPointNode, Set<Doublet>> keepOnlyPermString(Map<EntryPointNode, Set<Doublet>> in) {
		String permCheckSig = "<com.android.server.pm.PackageManagerService: int checkUidPermission(java.lang.String,int)>(";
		Map<EntryPointNode, Set<Doublet>> ret = new LinkedHashMap<>();
		for(EntryPointNode ep : in.keySet()) {
			String epSig = ep.getEntryPoint().getSignature();
			Map<String,Map<SootMethodContainer,Map<String,SootUnitContainer>>> exprToSources = new HashMap<>();
			for(Doublet d : in.get(ep)) {
				String expr = d.toString();
				if(expr.contains(permCheckSig)) {
					int i = expr.indexOf(permCheckSig);
					if(i != 1)
						throw new RuntimeException(d.toString());
					i += permCheckSig.length();
					int startIndex = i;
					int endIndex = -1;
					char startChar = expr.charAt(i);
					
					if(startChar == '"') {
						boolean inQuote = false;
						for(; i < expr.length(); i++) {
							char cur = expr.charAt(i);
							if(cur == startChar) {
								if(!inQuote) {
									inQuote = true;
								} else {
									inQuote = false;
									break;
								}
							}
						}
						if(inQuote || i >= expr.length())
							throw new RuntimeException("Error: Failed to parse '" + expr + "'.");
					} else if(startChar == '<') {
						int depth = 0;
						char endChar = '>';
						for(; i < expr.length(); i++) {
							char cur = expr.charAt(i);
							if(cur == startChar)
								depth++;
							else if(cur == endChar)
								depth--;
							if(depth <= 0)
								break;
						}
						if(depth > 0 || i >= expr.length())
							throw new RuntimeException("Error: Failed to parse '" + expr + "'.");
					} else if(startChar == 'A') {
						if(expr.length() < i + 4 || expr.charAt(i+1) != 'L' || expr.charAt(i+2) != 'L' || expr.charAt(i+3) != ',')
							throw new RuntimeException("Error: Failed to parse '" + expr + "'.");
						i += 2;
					} else if(startChar == 'N') {
						if(expr.length() < i + 5 || expr.charAt(i+1) != 'U' || expr.charAt(i+2) != 'L' || expr.charAt(i+3) != 'L' || expr.charAt(i+4) != ',')
							throw new RuntimeException("Error: Failed to parse '" + expr + "'.");
						i += 3;
					} else {
						throw new RuntimeException("Error: Unhandled start char '" + startChar + "' in '" + expr + "'.");
					}
					endIndex = i;
					
					Set<String> newExprs = new HashSet<>();
					String newExpr = "`" + expr.substring(startIndex, endIndex + 1) + "`";
					if(newExpr.equals("`<android.provider.Settings: java.lang.String[] PM_WRITE_SETTINGS>`")) {
						newExprs.add("`\"android.permission.WRITE_SETTINGS\"`"); //Array only has one permission in it in Android 9
					} else if(newExpr.equals("`<android.provider.Settings: java.lang.String[] PM_CHANGE_NETWORK_STATE>`")) {
						//Array only has two permissions in it in Android 9
						newExprs.add("`\"android.permission.WRITE_SETTINGS\"`");
						newExprs.add("`\"android.permission.CHANGE_NETWORK_STATE\"`");
					} else if(newExpr.equals("`<android.provider.Settings: java.lang.String[] PM_SYSTEM_ALERT_WINDOW>`")) {
						//Array only has one permission in it in Android 9
						newExprs.add("`\"android.permission.SYSTEM_ALERT_WINDOW\"`");
					} else if(newExpr.equals("`<com.android.server.biometrics.face.FaceService: java.lang.String getManageBiometricPermission()>`")) {
						newExprs.add("`\"android.permission.MANAGE_BIOMETRIC\"`");
					} else if(newExpr.equals("`<com.android.server.biometrics.fingerprint.FingerprintService: java.lang.String getManageBiometricPermission()>`")) {
						newExprs.add("`\"android.permission.MANAGE_FINGERPRINT\"`");
					} else if(newExpr.equals("`ALL`")) {
						//All because the permission check function takes in an array of permissions that if any pass then the caller is allowed
						if(epSig.equals("<com.android.server.accounts.AccountManagerService: void startAddAccountSession(android.accounts.IAccountManagerResponse,java.lang.String,java.lang.String,java.lang.String[],boolean,android.os.Bundle)>")) {
							newExprs.add("`\"android.permission.GET_PASSWORD\"`");
						} else if(epSig.equals("<com.android.server.accounts.AccountManagerService: void startUpdateCredentialsSession(android.accounts.IAccountManagerResponse,android.accounts.Account,java.lang.String,boolean,android.os.Bundle)>")) {
							newExprs.add("`\"android.permission.GET_PASSWORD\"`");
						} else if(epSig.equals("<com.android.server.vr.VrManagerService$4: boolean getPersistentVrModeEnabled()>")) {
							newExprs.add("`\"android.permission.ACCESS_VR_MANAGER\"`");
							newExprs.add("`\"android.permission.ACCESS_VR_STATE\"`");
						} else if(epSig.equals("<com.android.server.vr.VrManagerService$4: boolean getVrModeState()>")) {
							newExprs.add("`\"android.permission.ACCESS_VR_MANAGER\"`");
							newExprs.add("`\"android.permission.ACCESS_VR_STATE\"`");
						} else if(epSig.equals("<com.android.server.vr.VrManagerService$4: void registerListener(android.service.vr.IVrStateCallbacks)>")) {
							newExprs.add("`\"android.permission.ACCESS_VR_MANAGER\"`");
							newExprs.add("`\"android.permission.ACCESS_VR_STATE\"`");
						} else if(epSig.equals("<com.android.server.vr.VrManagerService$4: void registerPersistentVrStateListener(android.service.vr.IPersistentVrStateCallbacks)>")) {
							newExprs.add("`\"android.permission.ACCESS_VR_MANAGER\"`");
							newExprs.add("`\"android.permission.ACCESS_VR_STATE\"`");
						} else if(epSig.equals("<com.android.server.vr.VrManagerService$4: void setAndBindCompositor(java.lang.String)>")) {
							newExprs.add("`\"android.permission.RESTRICTED_VR_ACCESS\"`");
						} else if(epSig.equals("<com.android.server.vr.VrManagerService$4: void setPersistentVrModeEnabled(boolean)>")) {
							newExprs.add("`\"android.permission.RESTRICTED_VR_ACCESS\"`");
						} else if(epSig.equals("<com.android.server.vr.VrManagerService$4: void setVr2dDisplayProperties(android.app.Vr2dDisplayProperties)>")) {
							newExprs.add("`\"android.permission.RESTRICTED_VR_ACCESS\"`");
						} else if(epSig.equals("<com.android.server.vr.VrManagerService$4: void unregisterListener(android.service.vr.IVrStateCallbacks)>")) {
							newExprs.add("`\"android.permission.ACCESS_VR_MANAGER\"`");
							newExprs.add("`\"android.permission.ACCESS_VR_STATE\"`");
						} else if(epSig.equals("<com.android.server.vr.VrManagerService$4: void unregisterPersistentVrStateListener(android.service.vr.IPersistentVrStateCallbacks)>")) {
							newExprs.add("`\"android.permission.ACCESS_VR_MANAGER\"`");
							newExprs.add("`\"android.permission.ACCESS_VR_STATE\"`");
						} else if(epSig.equals("<com.android.server.vr.VrManagerService$4: void setStandbyEnabled(boolean)>")) {
							newExprs.add("`\"android.permission.ACCESS_VR_MANAGER\"`");
						} else if(epSig.equals("<com.android.server.vr.VrManagerService$4: void setVrInputMethod(android.content.ComponentName)>")) {
							newExprs.add("`\"android.permission.RESTRICTED_VR_ACCESS\"`");
						} else if(epSig.equals("<com.android.server.net.NetworkStatsService: void forceUpdateIfaces(android.net.Network[],com.android.internal.net.VpnInfo[],android.net.NetworkState[],java.lang.String)>")) {
							newExprs.add("`\"android.permission.NETWORK_STACK\"`");
							newExprs.add("`\"android.permission.MAINLINE_NETWORK_STACK\"`");
						} else if(epSig.equals("<com.android.server.usage.UsageStatsService$BinderService: void registerAppUsageLimitObserver(int,java.lang.String[],long,long,android.app.PendingIntent,java.lang.String)>")) {
							newExprs.add("`\"android.permission.SUSPEND_APPS\"`");
							newExprs.add("`\"android.permission.OBSERVE_APP_USAGE\"`");
						} else if(epSig.equals("<com.android.server.usage.UsageStatsService$BinderService: void unregisterAppUsageLimitObserver(int,java.lang.String)>")) {
							newExprs.add("`\"android.permission.SUSPEND_APPS\"`");
							newExprs.add("`\"android.permission.OBSERVE_APP_USAGE\"`");
						} else if(epSig.equals("<com.android.server.ConnectivityService$CaptivePortalImpl: void appResponse(int)>")) {
							newExprs.add("`\"android.permission.NETWORK_SETTINGS\"`");
							newExprs.add("`\"android.permission.MAINLINE_NETWORK_STACK\"`");
						} else if(epSig.equals("<com.android.server.ConnectivityService$CaptivePortalImpl: void logEvent(int,java.lang.String)>")
								|| epSig.equals("<com.android.server.ConnectivityService: boolean getMultiNetwork()>")
								|| epSig.equals("<com.android.server.ConnectivityService: void setMultiNetwork(boolean,int)>")
								) {
							newExprs.add("`\"android.permission.NETWORK_SETTINGS\"`");
							newExprs.add("`\"android.permission.MAINLINE_NETWORK_STACK\"`");
						} else if(epSig.equals("<com.android.server.ConnectivityService: void factoryReset()>")
								|| epSig.equals("<com.android.server.ConnectivityService: android.net.Network getActiveNetworkForUid(int,boolean)>")
								|| epSig.equals("<com.android.server.ConnectivityService: android.net.NetworkInfo getActiveNetworkInfoForUid(int,boolean)>")
								|| epSig.equals("<com.android.server.ConnectivityService: android.net.NetworkState[] getAllNetworkState()>")
								|| epSig.equals("<com.android.server.ConnectivityService: java.lang.String getCaptivePortalServerUrl()>")
								|| epSig.equals("<com.android.server.ConnectivityService: java.lang.String getMobileProvisioningUrl()>")
								|| epSig.equals("<com.android.server.ConnectivityService: java.lang.String[] getTetheredDhcpRanges()>")
								|| epSig.equals("<com.android.server.ConnectivityService: int registerNetworkAgent(android.os.Messenger,android.net.NetworkInfo,android.net.LinkProperties,android.net.NetworkCapabilities,int,android.net.NetworkMisc,int)>")
								|| epSig.equals("<com.android.server.ConnectivityService: int registerNetworkFactory(android.os.Messenger,java.lang.String)>")
								|| epSig.equals("<com.android.server.ConnectivityService: boolean requestRouteToHostAddress(int,byte[])>")
								|| epSig.equals("<com.android.server.ConnectivityService: void setGlobalProxy(android.net.ProxyInfo)>")
								|| epSig.equals("<com.android.server.ConnectivityService: void setProvisioningNotificationVisible(boolean,int,java.lang.String)>")
								|| epSig.equals("<com.android.server.ConnectivityService: void startCaptivePortalApp(android.net.Network)>")
								|| epSig.equals("<com.android.server.ConnectivityService: void unregisterNetworkFactory(android.os.Messenger)>")
								|| epSig.equals("<com.android.server.ConnectivityService: boolean updateLockdownVpn()>")
								|| epSig.equals("<com.android.server.ConnectivityService: int getActiveEnterpriseNetworkType(java.lang.String)>")
								|| epSig.equals("<com.android.server.ConnectivityService: int[] getUidsForApnType(java.lang.String)>")
								|| epSig.equals("<com.android.server.ConnectivityService: int[] getUsersForEnterpriseNetwork(int)>")
								|| epSig.equals("<com.android.server.ConnectivityService: boolean isEntApnEnabled(int)>")
								|| epSig.equals("<com.android.server.ConnectivityService: boolean isEnterpriseApn(java.lang.String,java.lang.String,java.lang.String)>")
								|| epSig.equals("<com.android.server.ConnectivityService: boolean isSplitBillingEnabled()>")
								) {
							newExprs.add("`\"android.permission.CONNECTIVITY_INTERNAL\"`");
							newExprs.add("`\"android.permission.MAINLINE_NETWORK_STACK\"`");
						} else if(epSig.equals("<com.android.server.ConnectivityService: int getConnectionOwnerUid(android.net.ConnectionInfo)>")
								|| epSig.equals("<com.android.server.ConnectivityService: boolean shouldAvoidBadWifi()>")
								|| epSig.equals("<com.android.server.net.NetworkStatsService: void forceUpdateIfaces(android.net.Network[],com.android.internal.net.VpnInfo[],android.net.NetworkState[],java.lang.String)>")) {
							newExprs.add("`\"android.permission.NETWORK_STACK\"`");
							newExprs.add("`\"android.permission.MAINLINE_NETWORK_STACK\"`");
						} else if(epSig.equals("<com.android.server.ConnectivityService: boolean isAlwaysOnVpnPackageSupported(int,java.lang.String)>")) {
							newExprs.add("`\"android.permission.NETWORK_SETTINGS\"`");
							newExprs.add("`\"android.permission.MAINLINE_NETWORK_STACK\"`");
							newExprs.add("`\"android.permission.INTERACT_ACROSS_USERS_FULL\"`");
						} else if(epSig.equals("<com.android.server.ConnectivityService: android.net.NetworkRequest listenForNetwork(android.net.NetworkCapabilities,android.os.Messenger,android.os.IBinder)>")
								|| epSig.equals("<com.android.server.ConnectivityService: void pendingListenForNetwork(android.net.NetworkCapabilities,android.app.PendingIntent)>")) {
							newExprs.add("`\"android.permission.NETWORK_SETTINGS\"`");
							newExprs.add("`\"android.permission.MAINLINE_NETWORK_STACK\"`");
							newExprs.add("`\"android.permission.ACCESS_WIFI_STATE\"`");
							newExprs.add("`\"android.permission.ACCESS_NETWORK_STATE\"`");
							newExprs.add("`\"android.permission.NETWORK_SIGNAL_STRENGTH_WAKEUP\"`");
						} else if(epSig.equals("<com.android.server.ConnectivityService: android.net.NetworkRequest pendingRequestForNetwork(android.net.NetworkCapabilities,android.app.PendingIntent)>")
								|| epSig.equals("<com.android.server.ConnectivityService: android.net.NetworkRequest requestNetwork(android.net.NetworkCapabilities,android.os.Messenger,int,android.os.IBinder,int)>")) {
							newExprs.add("`\"android.permission.NETWORK_SETTINGS\"`");
							newExprs.add("`\"android.permission.MAINLINE_NETWORK_STACK\"`");
							newExprs.add("`\"android.permission.ACCESS_WIFI_STATE\"`");
							newExprs.add("`\"android.permission.ACCESS_NETWORK_STATE\"`");
							newExprs.add("`\"android.permission.NETWORK_SIGNAL_STRENGTH_WAKEUP\"`");
							newExprs.add("`\"android.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS\"`");
							newExprs.add("`\"android.permission.CONNECTIVITY_INTERNAL\"`");
						} else if(epSig.equals("<com.android.server.ConnectivityService: void setAcceptPartialConnectivity(android.net.Network,boolean,boolean)>")
								|| epSig.equals("<com.android.server.ConnectivityService: void setAcceptUnvalidated(android.net.Network,boolean,boolean)>")
								|| epSig.equals("<com.android.server.ConnectivityService: void setAirplaneMode(boolean)>")
								|| epSig.equals("<com.android.server.ConnectivityService: void setAvoidUnvalidated(android.net.Network)>")) {
							newExprs.add("`\"android.permission.NETWORK_SETTINGS\"`");
							newExprs.add("`\"android.permission.NETWORK_SETUP_WIZARD\"`");
							newExprs.add("`\"android.permission.NETWORK_STACK\"`");
							newExprs.add("`\"android.permission.MAINLINE_NETWORK_STACK\"`");
						}
					} else if(newExpr.equals("`<java.util.List: java.lang.Object get(int)>`")) {
						if(epSig.equals("<com.android.server.enterprise.remotecontrol.RemoteInjectionService: boolean injectTrackballEvent(android.view.MotionEvent,boolean)>")
								|| epSig.equals("<com.android.server.enterprise.remotecontrol.RemoteInjectionService: boolean injectPointerEvent(android.view.MotionEvent,boolean)>")
								|| epSig.equals("<com.android.server.enterprise.remotecontrol.RemoteInjectionService: boolean injectKeyEvent(android.view.KeyEvent,boolean)>")) {
							newExprs.add("`\"com.samsung.android.knox.permission.KNOX_REMOTE_CONTROL\"`");
							newExprs.add("`\"android.permission.sec.MDM_REMOTE_CONTROL\"`");
						} else if(epSig.equals("<com.android.server.enterprise.remotecontrol.RemoteInjectionService: boolean injectPointerEventDex(android.view.MotionEvent,boolean)>")
								|| epSig.equals("<com.android.server.enterprise.remotecontrol.RemoteInjectionService: boolean injectKeyEventDex(android.view.KeyEvent,boolean)>")) {
							newExprs.add("`\"com.samsung.android.knox.permission.KNOX_ADVANCED_RESTRICTION\"`");
						} else if(epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: void updateProxyAdmin(android.app.admin.ProxyDeviceAdminInfo,int,android.content.ComponentName,int)>")
								|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: void updateProxyAdmin(android.app.admin.ProxyDeviceAdminInfo,int,android.content.ComponentName,int)>")
								|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: void removeProxyAdmin(int)>")
								|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: java.util.List getProxyAdmins(int)>")
								|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: java.util.List getActiveAdmins(int)>")
								|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: java.util.List getActiveAdminsInfo(int)>")
								|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: void activateAdminForUser(android.content.ComponentName,boolean,int)>")
								|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: void addProxyAdmin(android.app.admin.ProxyDeviceAdminInfo,int,android.content.ComponentName,int)>")
								|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: void deactivateAdminForUser(android.content.ComponentName,int)>")) {
							newExprs.add("`\"com.sec.enterprise.permission.MDM_PROXY_ADMIN_INTERNAL\"`");
							newExprs.add("`\"com.samsung.android.knox.permission.KNOX_PROXY_ADMIN_INTERNAL\"`");
						} else if(epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: void setActiveAdminSilent(android.content.ComponentName)>")) {
							newExprs.add("`\"com.sec.enterprise.mdm.permission.MDM_SILENT_ACTIVATION\"`");
							newExprs.add("`\"com.samsung.android.knox.permission.KNOX_SILENT_ACTIVATION_INTERNAL\"`");
						} else if(epSig.equals("<com.android.server.enterprise.container.KnoxMUMContainerPolicy: int removeContainerInternal(int)>")) {
							newExprs.add("`\"com.samsung.android.knox.permission.KNOX_CONTAINER\"`");
						}
					} else if(newExpr.equals("`<java.util.Iterator: java.lang.Object next()>`")) {
						if(epSig.equals("<com.android.server.enterprise.container.KnoxMUMContainerPolicy: void doSelfUninstall()>")
								|| epSig.equals("<com.android.server.enterprise.container.KnoxMUMContainerPolicy: boolean addConfigurationType(com.samsung.android.knox.ContextInfo,java.util.List)>")
								|| epSig.equals("<com.android.server.enterprise.container.KnoxMUMContainerPolicy: int createContainer(com.samsung.android.knox.ContextInfo,com.samsung.android.knox.container.CreationParams,int)>")) {
							newExprs.add("`\"com.samsung.android.knox.permission.KNOX_CONTAINER\"`");
						}
					} else if(newExpr.equals("`<java.util.ArrayList: java.lang.Object[] toArray(java.lang.Object[])>`")) {
						if(epSig.equals("<com.android.server.NetworkManagementService: void setIPv6AddrGenMode(java.lang.String,int)>")
								|| epSig.equals("<com.android.server.net.NetworkStatsService: void forceUpdateIfaces(android.net.Network[],com.android.internal.net.VpnInfo[],android.net.NetworkState[],java.lang.String)>")) {
							newExprs.add("`\"android.permission.NETWORK_STACK\"`");
							newExprs.add("`\"android.permission.MAINLINE_NETWORK_STACK\"`");
						}
					} else if(newExpr.equals("`<com.android.server.smartclip.SpenGestureManagerService: java.lang.String PERMISSION_EXTRACT_SMARTCLIP_DATA>`")) {
						newExprs.add("`\"com.samsung.android.permission.EXTRACT_SMARTCLIP_DATA\"`");
					} else if(newExpr.equals("`<com.android.server.smartclip.SpenGestureManagerService: java.lang.String PERMISSION_INJECT_INPUT_EVENT>`")) {
						newExprs.add("`\"android.permission.INJECT_EVENTS\"`");
					} else if(newExpr.equals("`<com.android.server.smartclip.SpenGestureManagerService: java.lang.String PERMISSION_CHANGE_SPEN_THEME>`")) {
						newExprs.add("`\"com.samsung.android.permission.CHANGE_SPEN_THEME\"`");
					}
					if(newExprs.isEmpty()) {
						if(newExpr.startsWith("`\"")) {
							newExprs.add(newExpr);
						} else if(!(((newExpr.equals("`ALL`") || newExpr.equals("`NULL`")) && (epSig.equals("<com.android.server.am.ActivityManagerService: int checkPermission(java.lang.String,int,int)>")
								|| epSig.equals("<com.android.server.am.ActivityManagerService: int checkPermissionWithToken(java.lang.String,int,int,android.os.IBinder)>")
								|| epSig.equals("<com.android.server.slice.SliceManagerService: int checkSlicePermission(android.net.Uri,java.lang.String,int,int,java.lang.String[])>")
								|| epSig.equals("<com.android.server.slice.SliceManagerService: int checkSlicePermission(android.net.Uri,java.lang.String,java.lang.String,int,int,java.lang.String[])>")
								|| epSig.equals("<com.android.server.ConnectivityService: void setWcmAcceptUnvalidated(android.net.Network,boolean)>")
								|| ep.getEntryPoint().getDeclaringClass().equals("com.android.server.devicepolicy.DevicePolicyManagerService")
								|| epSig.equals("<com.android.server.am.ActivityManagerService$PermissionController: boolean checkPermission(java.lang.String,int,int)>")))
								|| (newExpr.equals("`<java.util.Iterator: java.lang.Object next()>`") && 
										(epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: com.samsung.android.knox.ContextInfo getAdminContextIfCallerInCertWhiteList(java.util.List)>")
										|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: void enforceActiveAdminPermission(java.util.List)>")
										|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: com.samsung.android.knox.ContextInfo enforceActiveAdminPermissionByContext(com.samsung.android.knox.ContextInfo,java.util.List)>")
										|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: com.samsung.android.knox.ContextInfo enforceContainerOwnerShipPermissionByContext(com.samsung.android.knox.ContextInfo,java.util.List)>")
										|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: com.samsung.android.knox.ContextInfo enforceDoPoOnlyPermissionByContext(com.samsung.android.knox.ContextInfo,java.util.List)>")
										|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: com.samsung.android.knox.ContextInfo enforceOwnerOnlyAndActiveAdminPermission(com.samsung.android.knox.ContextInfo,java.util.List)>")
										|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: com.samsung.android.knox.ContextInfo enforceOwnerOnlyPermissionByContext(com.samsung.android.knox.ContextInfo,java.util.List)>")
										|| epSig.equals("<com.android.server.enterprise.EnterpriseDeviceManagerService: com.samsung.android.knox.ContextInfo enforcePermissionByContext(com.samsung.android.knox.ContextInfo,java.util.List)>")))
								|| newExpr.equals("`<com.android.server.am.ServiceRecord: java.lang.String permission>`")
								|| newExpr.equals("`<com.android.server.firewall.SenderPermissionFilter: java.lang.String mPermission>`")
								|| newExpr.equals("`<android.content.pm.PathPermission: java.lang.String getReadPermission()>`")
								|| newExpr.equals("`<android.content.pm.PathPermission: java.lang.String getWritePermission()>`")
								|| newExpr.equals("`<android.content.pm.ProviderInfo: java.lang.String readPermission>`")
								|| newExpr.equals("`<android.content.pm.ProviderInfo: java.lang.String writePermission>`")
								|| newExpr.equals("`<android.content.pm.ActivityInfo: java.lang.String permission>`")
								|| newExpr.equals("`<com.android.server.am.BroadcastFilter: java.lang.String requiredPermission>`")
								|| newExpr.equals("`<com.android.server.am.BroadcastRecord: java.lang.String[] requiredPermissions>`")
								|| newExpr.equals("`<com.android.server.biometrics.iris.IrisService: java.lang.String getManageBiometricPermission()>`"))) {
							logger.warn("{}: Unrecongized permission string {} in {}. It will be ignored.",cn,newExpr,ep.toString());
						}
					}
						
					for(String ne : newExprs) {
						Map<SootMethodContainer,Map<String,SootUnitContainer>> sources = exprToSources.get(ne);
						if(sources == null) {
							sources = new HashMap<>();
							exprToSources.put(ne, sources);
						}
						Map<SootMethodContainer,Map<String,SootUnitContainer>> temp = d.getSources();
						for(SootMethodContainer sourceMethod : temp.keySet()) {
							Map<String,SootUnitContainer> units = sources.get(sourceMethod);
							if(units == null) {
								units = new HashMap<>();
								sources.put(sourceMethod, units);
							}
							units.putAll(temp.get(sourceMethod));
						}
					}
				}
			}
			Set<Doublet> keep = new HashSet<>();
			for(String expr : exprToSources.keySet()) {
				keep.add(new Doublet(expr, exprToSources.get(expr)));
			}
			ret.put(ep, SortingMethods.sortSet(keep));
		}
		return ret;
	}

}
