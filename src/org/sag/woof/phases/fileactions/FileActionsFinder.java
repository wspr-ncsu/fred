package org.sag.woof.phases.fileactions;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.sag.acminer.database.excludedelements.IExcludeHandler;
import org.sag.acminer.phases.entrypoints.EntryPoint;
import org.sag.common.concurrent.CountingThreadExecutor;
import org.sag.common.concurrent.IgnorableRuntimeException;
import org.sag.common.io.FileHelpers;
import org.sag.common.io.PrintStreamUnixEOL;
import org.sag.common.logging.ILogger;
import org.sag.common.tools.HierarchyHelpers;
import org.sag.common.tools.SortingMethods;
import org.sag.soot.SootSort;
import org.sag.woof.IWoofDataAccessor;
import org.sag.woof.database.filemethods.FileMethod;
import org.sag.woof.database.filepaths.IFilePathsDatabase;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.jimple.DefinitionStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

public class FileActionsFinder {
	
	private final String cn;
	private final ILogger logger;
	private final IWoofDataAccessor dataAccessor;
	private volatile Map<EntryPoint,Map<SootMethod,Map<Unit,Set<FileMethod>>>> data;
	
	public FileActionsFinder(IWoofDataAccessor dataAccessor, ILogger logger) {
		this.logger = logger;
		this.dataAccessor = dataAccessor;
		this.cn = getClass().getSimpleName();
		this.data = new LinkedHashMap<>();
	}
	
	public boolean run() {
		boolean successOuter = true;
		CountingThreadExecutor exe = null;
		final List<Throwable> errs = new ArrayList<>();
		
		logger.info("{}: Begin finding file actions.",cn);
		
		try {
			exe = new CountingThreadExecutor();
			//Init the data map so it is sorted
			for(EntryPoint ep : dataAccessor.getEntryPoints()) {
				data.put(ep, Collections.emptyMap());
			}
			
			for(final EntryPoint ep : dataAccessor.getEntryPoints()) {
				exe.execute(new Runnable() {
					public void run() {
						try {
							Map<SootMethod,Map<Unit,Set<FileMethod>>> sourceToUnitToFileMethod = new HashMap<>();
							Set<SootMethod> visited = new HashSet<>();
							Queue<SootMethod> toVisit = new ArrayDeque<>();
							CallGraph cg = Scene.v().getCallGraph();
							IExcludeHandler excludeHandler = dataAccessor.getExcludedElementsDB().createNewExcludeHandler(ep);
							Map<SootMethod, FileMethod> fileMethods = new HashMap<>();
							for(FileMethod fm : dataAccessor.getFileMethodsDB().getOutputData()) {
								fileMethods.put(fm.getSootMethod(), fm);
							}
							
							toVisit.add(ep.getEntryPoint());
							while(!toVisit.isEmpty()) {
								SootMethod cur = toVisit.poll();
								if(visited.add(cur)) {
									Iterator<Edge> it = cg.edgesOutOf(cur);
									while(it.hasNext()) {
										Edge e = it.next();
										FileMethod fm = fileMethods.get(e.tgt());
										if(fm != null) {
											Map<Unit,Set<FileMethod>> unitToFileMethod = sourceToUnitToFileMethod.get(e.src());
											if(unitToFileMethod == null) {
												unitToFileMethod = new HashMap<>();
												sourceToUnitToFileMethod.put(e.src(), unitToFileMethod);
											}
											Set<FileMethod> fileMethod = unitToFileMethod.get(e.srcUnit());
											if(fileMethod == null) {
												fileMethod = new HashSet<>();
												unitToFileMethod.put(e.srcUnit(), fileMethod);
											}
											fileMethod.add(fm);
										} else {
											if(!excludeHandler.isExcludedMethodWithOverride(e.tgt())) {
												toVisit.add(e.tgt());
											}
										}
									}
								}
							}
							
							if(sourceToUnitToFileMethod.isEmpty()) {
								sourceToUnitToFileMethod = Collections.emptyMap();
							} else {
								for(SootMethod sm : sourceToUnitToFileMethod.keySet()) {
									Map<Unit,Set<FileMethod>> unitToFileMethod = sourceToUnitToFileMethod.get(sm);
									for(Unit u : unitToFileMethod.keySet()) {
										unitToFileMethod.put(u, SortingMethods.sortSet(unitToFileMethod.get(u)));
									}
									sourceToUnitToFileMethod.put(sm, SortingMethods.sortMapKey(unitToFileMethod, SootSort.unitComp));
								}
								sourceToUnitToFileMethod = SortingMethods.sortMapKey(sourceToUnitToFileMethod, SootSort.smComp);
							}
							
							synchronized(data) {
								data.put(ep, sourceToUnitToFileMethod);
							}
						} catch(IgnorableRuntimeException t) {
							throw t;
						} catch(Throwable t) {
							logger.fatal("{}: An unexpected exception occured when getting file actions for entry point '{}'.",t,cn,ep.getEntryPoint());
							throw new IgnorableRuntimeException();
						}
					}
				});
			}
			
			exe.awaitCompletion();
			logger.info("{}: Successfully found all file actions.",cn);
			
			logger.info("{}: Begin dumping file actions to file.",cn);
			FileHelpers.processDirectory(dataAccessor.getConfig().getFilePath("debug_woof-file-actions-dir"), true, false);
			try(PrintStreamUnixEOL ps = new PrintStreamUnixEOL(Files.newOutputStream(
					FileHelpers.getPath(dataAccessor.getConfig().getFilePath("debug_woof-file-actions-dir"), "file_actions_dump.txt")))) {
				for(EntryPoint ep : data.keySet()) {
					Map<SootMethod,Map<Unit,Set<FileMethod>>> sourceToUnitToFileMethod = data.get(ep);
					if(!sourceToUnitToFileMethod.isEmpty()) {
						ps.println(ep.toString());
						Set<FileMethod> fms = new HashSet<>();
						for(Map<Unit,Set<FileMethod>> temp : sourceToUnitToFileMethod.values()) {
							for(Set<FileMethod> temp2 : temp.values()) {
								fms.addAll(temp2);
							}
						}
						for(FileMethod fm : SortingMethods.sortSet(fms)) {
							ps.println(fm.toStringNoSinks("  "));
						}
					}
					
				}
				
			}
			logger.info("{}: Finished dumping file actions to file.",cn);
			
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
					ps.println(cn + ": Failed to successfully find file actions. The following exceptions occured:");
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
	
	//TODO Move this to a seperate phase
	public boolean extractFilePaths() {
		boolean successOuter = true;
		CountingThreadExecutor exe = null;
		FilePathExtractor fpe = null;
		Set<FileMethod> skippedOpens = new HashSet<>();
		Set<FileMethod> allFileOpens = new HashSet<>();
		final List<Throwable> errs = new ArrayList<>();
		
		FieldValueFinder fvf = new FieldValueFinder(logger);
		if(!fvf.initData())
			return false;
		
		logger.info("{}: Begin extracting file paths.",cn);
		
		try {
			exe = new CountingThreadExecutor();
			fpe = new FilePathExtractor(fvf, dataAccessor, exe, logger);
			Set<String> subClassesOfContext = new HashSet<>();
			SootClass context = Scene.v().getSootClassUnsafe("android.content.Context", false);
			if(context != null) {
				for(SootClass sc : HierarchyHelpers.getAllSubClasses(context)) {
					subClassesOfContext.add(sc.toString());
				}
			}
			
			
			for(EntryPoint ep : data.keySet()) {
				Map<SootMethod, Map<Unit, Set<FileMethod>>> sourceToUnit = data.get(ep);
				for(SootMethod source : sourceToUnit.keySet()) {
					Map<Unit, Set<FileMethod>> unitToFileMethod = sourceToUnit.get(source);
					for(Unit u : unitToFileMethod.keySet()) {
						Set<FileMethod> opens = new HashSet<>();
						for(FileMethod fm : unitToFileMethod.get(u)) {
							if(fm.opens()) {
								opens.add(fm);
								allFileOpens.add(fm);
							}
						}
						if(!opens.isEmpty()) {
							boolean found = false;
							Stmt startStmt = (Stmt)u;
							InvokeExpr ie = startStmt.getInvokeExpr();
							SootMethodRef ref = ie.getMethodRef();
							List<Type> types = ref.parameterTypes();
							for(int i = 0; i < types.size(); i++) {
								Type t = types.get(i);
								if(t.toString().equals("java.lang.String") || t.toString().equals("java.io.File") 
										|| t.toString().equals("java.nio.file.Path")) {
									fpe.startArgValue(startStmt, source, i, ep);
									found = true;
								}
							}
							if(ref.declaringClass().toString().equals("java.io.File") && (!found 
									|| ref.getSignature().equals("<java.io.File: boolean renameTo(java.io.File)>"))) {
								fpe.startInvokeBaseValue(startStmt, source, ep);
								found = true;
							} else if(!found && subClassesOfContext.contains(ref.declaringClass().toString())) {
								//Only include ContextImpl because the rest are not important
								if(ref.declaringClass().toString().equals("android.app.ContextImpl"))
									fpe.startMethodRefValue((DefinitionStmt)startStmt, source, ep);
								found = true;
							} else if(!found && (ref.declaringClass().toString().equals("android.util.AtomicFile") 
									|| ref.declaringClass().toString().equals("com.android.internal.os.AtomicFile"))) {
								fpe.startInvokeBaseValue(startStmt, source, ep);
								found = true;
							}
							
							if(!found) {
								skippedOpens.addAll(opens);
							}
						}
					}
				}
			}
			
			allFileOpens = SortingMethods.sortSet(allFileOpens);
			StringBuilder sb = new StringBuilder();
			for(FileMethod fm : allFileOpens) {
				sb.append(fm.toStringNoSinks("  ")).append("\n");
			}
			logger.info("{}: Extracting file paths with {} unique open methods:\n{}",cn,allFileOpens.size(),sb.toString());
			
			if(!skippedOpens.isEmpty()) {
				skippedOpens = SortingMethods.sortSet(skippedOpens);
				sb = new StringBuilder();
				for(FileMethod fm : skippedOpens) {
					sb.append(fm.toStringNoSinks("  ")).append("\n");
				}
				logger.warn("{}: Skipping unhandled open methods:\n{}",cn,sb.toString());
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
					ps.println(cn + ": Failed to successfully find file actions. The following exceptions occured:");
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
			
			Path pappth = dataAccessor.getConfig().getFilePath("woof_file-paths-db-file");
			try {
				IFilePathsDatabase db = IFilePathsDatabase.Factory.getNew(false);
				db.addAll(fpe.getSortedData());
				db.writeXML(null, pappth);
			} catch(Throwable t) {
				logger.fatal("{}: Failed to write the extracted file paths database to '{}'.",t,cn,pappth);
			}
			
			//Always dump the extracted file paths for now
			if(fpe != null) {
				Path path = dataAccessor.getConfig().getFilePath("debug_woof-file-actions-dir");
				try {
					FileHelpers.processDirectory(path, true, false);
					fpe.dumpData(path);
				} catch(Throwable t) {
					successOuter = false;
					logger.fatal("{}: Failed to dump the extracted file paths.",t,cn);
				}
			}
		}
		return successOuter;
	}

}
