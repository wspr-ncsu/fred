package org.sag.woof.phases.fileactions;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.sag.acminer.database.excludedelements.IExcludeHandler;
import org.sag.acminer.phases.entrypoints.EntryPoint;
import org.sag.common.concurrent.CountingThreadExecutor;
import org.sag.common.concurrent.IgnorableRuntimeException;
import org.sag.common.cycles.Cycles;
import org.sag.common.io.FileHelpers;
import org.sag.common.io.PrintStreamUnixEOL;
import org.sag.common.logging.ILogger;
import org.sag.common.tools.HierarchyHelpers;
import org.sag.common.tools.SortingMethods;
import org.sag.common.tuple.Pair;
import org.sag.common.tuple.Triple;
import org.sag.soot.analysis.AdvLocalDefs;
import org.sag.soot.analysis.AdvLocalUses;
import org.sag.soot.callgraph.ExcludingJimpleICFG;
import org.sag.soot.callgraph.IJimpleICFG;
import org.sag.soot.callgraph.JimpleICFG;
import org.sag.woof.IWoofDataAccessor;
import org.sag.woof.database.filepaths.parts.*;
import org.sag.woof.database.filepaths.parts.AnyPartImpl.*;
import org.sag.woof.database.filepaths.parts.ConstantPart.*;
import org.sag.woof.database.filepaths.parts.Part.Node;
import org.sag.soot.callgraph.ExcludingJimpleICFG.ExcludingEdgePredicate;
import org.sag.soot.callgraph.IJimpleICFG.IBasicEdgePredicate;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;

import heros.solver.IDESolver;
import soot.Body;
import soot.IntType;
import soot.Local;
import soot.PrimType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.Constant;
import soot.jimple.DefinitionStmt;
import soot.jimple.DoubleConstant;
import soot.jimple.FieldRef;
import soot.jimple.FloatConstant;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.LongConstant;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.NewMultiArrayExpr;
import soot.jimple.NullConstant;
import soot.jimple.ParameterRef;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.UnopExpr;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.scalar.UnitValueBoxPair;

public class FilePathExtractor {
	
	private final NullConstantPart nullConstant = new NullConstantPart();
	private final StringConstantPart unixFileSepConstant = new StringConstantPart("/");
	
	private final Set<String> fileDirListMethods = ImmutableSet.<String>builder()
			.add("<java.io.File: java.lang.String[] list()>")
			.add("<java.io.File: java.io.File[] listFiles()>")
			.add("<java.io.File: java.lang.String[] list(java.io.FilenameFilter)>")
			.add("<java.io.File: java.io.File[] listFiles(java.io.FileFilter)>")
			.add("<java.io.File: java.io.File[] listFiles(java.io.FilenameFilter)>")
			.build();
	private final Set<String> fileUtilsDirListMethods = ImmutableSet.<String>builder()
			.add("<android.os.FileUtils: java.io.File[] listFilesOrEmpty(java.io.File)>")
			.add("<android.os.FileUtils: java.io.File[] listFilesOrEmpty(java.io.File,java.io.FilenameFilter)>")
			.add("<android.os.FileUtils: java.lang.String[] listOrEmpty(java.io.File)>")
			.build();
	
	private final LoadingCache<Constant,ConstantPart<?>> constantCache = IDESolver.DEFAULT_CACHE_BUILDER.build(new CacheLoader<Constant,ConstantPart<?>>() {
		@Override
		public ConstantPart<?> load(Constant c) throws Exception {
			if(c instanceof StringConstant) {
				if(((StringConstant)c).value.equals("/"))
					return unixFileSepConstant;
				return new StringConstantPart(((StringConstant) c).value);
			} else if(c instanceof IntConstant) {
				return new IntConstantPart(((IntConstant) c).value);
			} else if(c instanceof LongConstant) {
				return new LongConstantPart(((LongConstant) c).value);
			} else if(c instanceof FloatConstant) {
				return new FloatConstantPart(((FloatConstant) c).value);
			} else if(c instanceof DoubleConstant) {
				return new DoubleConstantPart(((DoubleConstant) c).value);
			} else if(c instanceof NullConstant) {
				return nullConstant;
			} else {
				return new UnknownConstantPart(c.toString());
			}
		}
	});
	
	//Small cache to load the ICFG for an entry point, can't make this too big or memory blows up so we set it to the number of processors
	//Assumes that the threads will be started in the order they were added (so all of one EP occur before another). This allows us to keep
	//in memory those ICFG for EPs that may still have threads to execute and then evict them when we are finished.
	private final LoadingCache<EntryPoint,IJimpleICFG> icfgCache = CacheBuilder.newBuilder()
			.concurrencyLevel(Runtime.getRuntime().availableProcessors()).initialCapacity(Runtime.getRuntime().availableProcessors())
			.maximumSize(Runtime.getRuntime().availableProcessors()).build(new CacheLoader<EntryPoint,IJimpleICFG>() {
				@Override
				public IJimpleICFG load(EntryPoint ep) throws Exception {
					IExcludeHandler excludeHandler = dataAccessor.getExcludedElementsDB().createNewExcludeHandler(ep);
					return new ExcludingJimpleICFG(ep, baseICFG, new ExcludingEdgePredicate(baseICFG.getCallGraph(), excludeHandler));
				}
				
			});
	
	private final LoadingCache<IJimpleICFG,Set<SootMethod>> methodsInCallGraphCache = CacheBuilder.newBuilder()
			.concurrencyLevel(Runtime.getRuntime().availableProcessors()).initialCapacity(Runtime.getRuntime().availableProcessors())
			.maximumSize(Runtime.getRuntime().availableProcessors()).build(new CacheLoader<IJimpleICFG,Set<SootMethod>>() {
				@Override
				public Set<SootMethod> load(IJimpleICFG icfg) throws Exception {
					Deque<SootMethod> queue = new ArrayDeque<>();
					Set<SootMethod> visited = new HashSet<>();
					CallGraph cg = icfg.getCallGraph();
					IBasicEdgePredicate edgePred = icfg.getEdgePredicate();
					IExcludeHandler excludeHandler = edgePred.getExcludeHandler();
					for(EntryPoint ep : icfg.getEntryPoints()) {
						if(!excludeHandler.isExcludedMethodWithOverride(ep.getEntryPoint()))
							queue.add(ep.getEntryPoint());
					}
					while(!queue.isEmpty()) {
						SootMethod cur = queue.poll();
						if(visited.add(cur)) {
							for(Iterator<Edge> it = cg.edgesOutOf(cur); it.hasNext();) {
								Edge e = it.next();
								SootMethod tgt = e.tgt();
								if(edgePred.want(e) && !excludeHandler.isExcludedMethodWithOverride(tgt))
									queue.add(tgt);
							}
						}
					}
					return visited;
				}
			});
	
	private final ILogger logger;
	private final IWoofDataAccessor dataAccessor;
	private final String cn;
	private final JimpleICFG baseICFG;
	private final CountingThreadExecutor exe;
	private final FieldValueFinder fvf;
	private final Map<String, Part> simulatedContextParts;
	private final Set<String> subClassesOfContext;
	private final Set<String> subClassesOfIBinder;
	private final Map<EntryPoint,List<Pair<PHPart,Part>>> data;
	
	public FilePathExtractor(FieldValueFinder fvf, IWoofDataAccessor dataAccessor, CountingThreadExecutor exe, ILogger logger) {
		this.cn = getClass().getSimpleName();
		this.dataAccessor = dataAccessor;
		this.logger = logger;
		this.exe = exe;
		this.baseICFG = new JimpleICFG(this.dataAccessor.getEntryPoints(),false);
		this.data = new HashMap<>();
		this.fvf = fvf;
		this.simulatedContextParts = new HashMap<>();
		this.subClassesOfContext = new HashSet<>();
		this.subClassesOfIBinder = new HashSet<>();
		init();
	}
	
	public boolean startMethodRefValue(DefinitionStmt startStmt, SootMethod startSource, EntryPoint ep) {
		if(startStmt == null || startSource == null || ep == null) {
			logger.fatal("{}: All arguments must not be null.",cn);
			return false;
		}
		PHPart startData = new PHMethodRefValuePart(startStmt, startSource);
		if(!startStmt.containsInvokeExpr()) {
			logger.fatal("{}: The provided statement is not a invoke statement:\n'{}'", cn, startData.toLogString("\t"));
			return false;
		}
		return extractPaths(startData, ep);
	}
	
	public boolean startInvokeBaseValue(Stmt startStmt, SootMethod startSource, EntryPoint ep) {
		if(startStmt == null || startSource == null || ep == null) {
			logger.fatal("{}: All arguments must not be null.",cn);
			return false;
		}
		PHPart startData = new PHBaseValuePart(startStmt, startSource);
		if(!startStmt.containsInvokeExpr()) {
			logger.fatal("{}: The provided statement is not a invoke statement:\n'{}'", cn, startData.toLogString("\t"));
			return false;
		}
		return extractPaths(startData, ep);
	}
	
	public boolean startArgValue(Stmt startStmt, SootMethod startSource, int argIndex, EntryPoint ep) {
		if(startStmt == null || startSource == null || ep == null || argIndex < 0) {
			logger.fatal("{}: All arguments must not be null and the argIndex >= 0.",cn);
			return false;
		}
		PHPart startData = new PHArgumentValuePart(startStmt, startSource, argIndex);
		if(!startStmt.containsInvokeExpr()) {
			logger.fatal("{}: The provided statement is not a invoke statement:\n'{}'", cn, startData.toLogString("\t"));
			return false;
		}
		return extractPaths(startData, ep);
	}
	
	private boolean extractPaths(PHPart startData, EntryPoint ep) {
		FilePathExtractingRunnable runnable = new FilePathExtractingRunnable(startData, ep);
		try {
			exe.execute(runnable);
			return true;
		} catch(Throwable t) {
			logger.fatal("{}: An unexpected exception occured when starting the file path extraction for:\n{}", cn, runnable.toString("\t"));
			return false;
		}
	}
	
	public Map<EntryPoint,List<Pair<PHPart,Part>>> getSortedData() {
		synchronized(data) {
			for(List<Pair<PHPart,Part>> list : data.values()) {
				Collections.sort(list,new Comparator<Pair<PHPart,Part>>() {
					@Override
					public int compare(Pair<PHPart, Part> o1, Pair<PHPart, Part> o2) {
						return o1.getFirst().compareTo(o2.getFirst());
					}
				});
			}
			return SortingMethods.sortMapKeyAscending(data);
		}
	}
	
	public void dumpData(Path outDir) throws IOException {
		Map<EntryPoint,List<Pair<PHPart,Part>>> data = getSortedData();
		Set<String> filePaths = new HashSet<>();
		Set<String> regexes = new HashSet<>();
		Set<String> simpleFilePaths = new HashSet<>();
		Set<Part> parts = new HashSet<>();
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath(outDir, "file_paths_ep_and_seed.txt")))) {
			for(EntryPoint ep : data.keySet()) {
				ps.println("Stub: " + ep.getStub() + " EP: " + ep.getEntryPoint());
				for(Pair<PHPart,Part> p : data.get(ep)) {
					ps.println("  Seed: " + p.getFirst().toString());
					ps.println("  File: " + p.getSecond().toString());
					filePaths.add(p.getSecond().toString());
					regexes.add(p.getSecond().toRegexString());
					simpleFilePaths.add(p.getSecond().toSimpleString());
					parts.add(p.getSecond());
				}
			}
		}
		filePaths = SortingMethods.sortSet(filePaths,SortingMethods.sComp);
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath(outDir, "file_paths.txt")))) {
			for(String s : filePaths) {
				ps.println(s);
			}
		}
		regexes = SortingMethods.sortSet(regexes,SortingMethods.sComp);
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath(outDir, "file_paths_regex.txt")))) {
			for(String s : regexes) {
				ps.println(s);
			}
		}
		simpleFilePaths = SortingMethods.sortSet(simpleFilePaths,SortingMethods.sComp);
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath(outDir, "file_paths_simple.txt")))) {
			for(String s : simpleFilePaths) {
				ps.println(s);
			}
		}
		
		Set<LeafPart> leafs = new HashSet<>();
		for(Part root : parts) {
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
		try(PrintStream ps = new PrintStreamUnixEOL(Files.newOutputStream(FileHelpers.getPath(outDir, "leaf_parts.txt")))) {
			for(Part p : leafss) {
				ps.println(p.toString());
			}
		}
	}
	
	public class FilePathExtractingRunnable implements Runnable {
		
		private volatile IJimpleICFG icfg;
		private final EntryPoint ep;
		private final PHPart startData;
		
		public FilePathExtractingRunnable(PHPart startData, EntryPoint ep) {
			this.ep = ep;
			this.startData = startData;
		}
		
		@Override
		public String toString() {
			return toString("");
		}
		
		public String toString(String spacer) {
			StringBuilder sb = new StringBuilder();
			sb.append(spacer).append("Start Data Type: ").append(startData.getAnalysisName()).append("\n");
			startData.appendStartData(sb, spacer);
			sb.append(spacer).append("Entry Point: ").append(ep.getEntryPoint()).append("\n");
			sb.append(spacer).append("Stub: ").append(ep.getStub());
			return sb.toString();
		}
		
		@Override
		public void run() {
			try {
				//init the icfg here so its creation occurs when the running thread
				this.icfg = icfgCache.getUnchecked(ep);
				logger.info("{}: Starting file path extraction:\n{}",cn,toString("\t"));
				Part p = proceess();
				if(p != null) {
					//Handle the situations where we are specially wrapping things and those things are our start points
					if(startData instanceof PHBaseValuePart) {
						Stmt invokeStmt = ((PHBaseValuePart)startData).getInvokeStmt();
						SootMethod invokeSource = ((PHBaseValuePart)startData).getInvokeSource();
						if(fileDirListMethods.contains(invokeStmt.getInvokeExpr().getMethodRef().getSignature())) {
							AppendPart app = new AppendPart();
							app.add(p);
							app.add(unixFileSepConstant);
							app.add(new AnyChildPathPart(invokeStmt, invokeSource));
							p = app;
						}
					} else if(startData instanceof PHArgumentValuePart) {
						Stmt invokeStmt = ((PHArgumentValuePart)startData).getInvokeStmt();
						SootMethod invokeSource = ((PHArgumentValuePart)startData).getInvokeSource();
						if(fileUtilsDirListMethods.contains(invokeStmt.getInvokeExpr().getMethodRef().getSignature())) {
							AppendPart app = new AppendPart();
							app.add(p);
							app.add(unixFileSepConstant);
							app.add(new AnyChildPathPart(invokeStmt, invokeSource));
							p = app;
						}
					}
					
					synchronized(data) {
						List<Pair<PHPart,Part>> res = data.get(ep);
						if(res == null) {
							res = new ArrayList<>();
							data.put(ep, res);
						}
						res.add(new Pair<>(startData,p));
					}
					logger.info("{}: Successfully completed file path extraction:\n\t{}\n{}",cn,p.toString(),toString("\t"));
				} else {
					logger.fatal("{}: Failed to extract file paths:\n{}",cn,toString("\t"));
					throw new IgnorableRuntimeException();
				}
			} catch(IgnorableRuntimeException t) {
				throw t;
			} catch(Throwable t) {
				logger.fatal("{}: An unexpected exception occured with extracting file paths:\n{}",t,cn,toString("\t"));
				throw new IgnorableRuntimeException();
			}
		}
		
		private Part proceess() {
			Deque<PHPart> queue = new ArrayDeque<>();
			Set<PHPart> visited = new HashSet<>();
			Map<PHPart,Part> data = new HashMap<>();
			
			queue.add(startData);
			while(!queue.isEmpty()) {
				PHPart cur = queue.poll();
				if(visited.add(cur)) {
					OrPart container = new OrPart();
					for(Triple<SootMethod,Stmt,Value> t : cur.getStartPoints(icfg)) {
						SootMethod sourceMethod = t.getFirst();
						Stmt stmt = t.getSecond();
						Value v = t.getThird();
						Part compPart = handleOuterNoiseCases(cur, stmt, sourceMethod, v);
						if(compPart != null) {
							//Do nothing
						} else if(v instanceof Constant) {
							//Method returns should always be either a local or a constant
							compPart = constantCache.getUnchecked((Constant)v);
						} else {
							Set<DefinitionStmt> defs = cur.getDefs(icfg, sourceMethod, stmt, v);
							if(defs.isEmpty()) {
								compPart = new UnknownValuePart(stmt, sourceMethod, v.toString());
								logger.warn("{}: {} - No definition for local:\n\tPart:{}\n{}\n{}",
										cn,cur.getAnalysisName(),compPart.toString(),cur.toLogString("\t"),toString("\t"));
							} else {
								OrPart orPart = new OrPart();
								for(DefinitionStmt def : defs) {
									Value rightOp = def.getRightOp();
									Part noisePart = handleInnerNoiseCases(cur, def, sourceMethod, rightOp, queue);
									if(noisePart != null) {
										orPart.add(noisePart);
									} else if(rightOp instanceof Constant) {
										orPart.add(constantCache.getUnchecked((Constant)rightOp));
									} else if(rightOp instanceof ArrayRef) {
										orPart.add(handleArrayValues(cur,def, sourceMethod, (ArrayRef)rightOp, queue));
									} else if(rightOp instanceof FieldRef) {
										SootField field = HierarchyHelpers.resolveField(((FieldRef)rightOp).getFieldRef());
										Set<Pair<SootMethod,AssignStmt>> fieldWrites = fvf.getFieldWrites(field);
										fieldWrites = fieldWrites == null ? Collections.emptySet() : new HashSet<>(fieldWrites);
										if(field != null && (!field.isStatic() || !field.isFinal())) {
											Set<SootMethod> methodsInCG = methodsInCallGraphCache.getUnchecked(icfg);
											for(Iterator<Pair<SootMethod,AssignStmt>> it = fieldWrites.iterator(); it.hasNext();) {
												Pair<SootMethod,AssignStmt> p = it.next();
												if(!p.getFirst().getName().equals("<clinit>") && !methodsInCG.contains(p.getFirst()))
													it.remove();
											}
										}
										if(fieldWrites.isEmpty()) {
											orPart.add(new AnyFieldRefPart(def, sourceMethod));
										} else {
											for(Pair<SootMethod,AssignStmt> p : fieldWrites) {
												PHPart next = new PHFieldValuePart(p.getSecond(), p.getFirst());
												queue.add(next);
												orPart.add(next);
											}
										}
									} else if(rightOp instanceof ParameterRef) {
										for(Part p : constructParamRefs(((ParameterRef)rightOp).getIndex(), sourceMethod, cur, def)) {
											orPart.add(p);
											if(p instanceof PHPart)
												queue.add((PHPart)p);
										}
									} else if(rightOp instanceof NewMultiArrayExpr || rightOp instanceof NewArrayExpr) {
										orPart.add(new AnyArrayPart(def, sourceMethod));
									} else if(rightOp instanceof NewExpr) {
										String type = ((NewExpr)rightOp).getType().toString();
										if(type.equals("java.lang.String") || type.equals("java.io.File") 
												|| type.equals("android.util.AtomicFile") || type.equals("com.android.internal.os.AtomicFile")) {
											AdvLocalUses advUses = icfg.getOrMakeLocalUses(sourceMethod);
											Set<UnitValueBoxPair> uses = advUses.getUsesWithAliasesRemoveLocalAndCast(def);
											boolean found = false;
											for(UnitValueBoxPair usePair : uses) {
												Unit use = usePair.getUnit();
												Local usedLocal = (Local)(usePair.getValueBox().getValue());
												if(((Stmt)use).containsInvokeExpr()) {
													InvokeExpr ie = ((Stmt)use).getInvokeExpr();
													if(ie instanceof SpecialInvokeExpr && ((SpecialInvokeExpr)ie).getBase().equals(usedLocal) 
															&& ie.getMethodRef().name().equals("<init>")) {
														List<Value> args = ie.getArgs();
														found = true;
														if(type.equals("java.lang.String")) {
															if(args.isEmpty()) {
																orPart.add(constantCache.getUnchecked(StringConstant.v("")));
															} else if(args.size() == 1 && ie.getMethodRef().parameterType(0).toString().equals("java.lang.String")) {
																PHPart next = new PHArgumentValuePart((Stmt)use,sourceMethod,0);
																queue.add(next);
																orPart.add(next);
															} else {
																orPart.add(new AnyNewInvokePart((Stmt)use, sourceMethod));
															}
														} else if(type.equals("java.io.File")) {
															if(args.size() == 1 && ie.getMethodRef().parameterType(0).toString().equals("java.lang.String")) {
																PHPart next = new PHArgumentValuePart((Stmt)use,sourceMethod,0);
																queue.add(next);
																orPart.add(next);
															} else if(args.size() == 2) {
																PHPart nextArg0 = new PHArgumentValuePart((Stmt)use,sourceMethod,0);
																PHPart nextArg1 = new PHArgumentValuePart((Stmt)use,sourceMethod,1);
																queue.add(nextArg0);
																queue.add(nextArg1);
																AppendPart app = new AppendPart();
																app.add(nextArg0);
																app.add(unixFileSepConstant);
																app.add(nextArg1);
																orPart.add(new NormalizePart(app));
															} else {
																orPart.add(new AnyNewInvokePart((Stmt)use, sourceMethod));
															}
														} else if(type.equals("android.util.AtomicFile") || type.equals("com.android.internal.os.AtomicFile")) {
															PHPart nextArg0 = new PHArgumentValuePart((Stmt)use,sourceMethod,0);
															queue.add(nextArg0);
															orPart.add(nextArg0);
															AppendPart app = new AppendPart();
															app.add(nextArg0);
															app.add(new StringConstantPart(".bak"));
															orPart.add(app);
														}
													}
												}
											}
											if(!found) {
												UnknownValuePart p = new UnknownValuePart(def, sourceMethod, rightOp.toString());
												logger.warn("{}: {} - No init found for new instance expression:\n"
														+ "\tPart:{}\n{}\n{}",
														cn, cur.getAnalysisName(), p.toString(), cur.toLogString("\t"), toString("\t"));
												orPart.add(p);
											}
										} else if(type.equals("java.util.HashSet") || type.equals("java.util.ArrayList")) {
											// Because for some reason samsung is returning object type and sometimes its a 
											// file while other times its a collections of files
											orPart.add(new AnyNewInvokePart(def, sourceMethod));
										} else {
											UnknownValuePart p = new UnknownValuePart(def, sourceMethod, rightOp.toString());
											logger.warn("{}: {} - Encountered non-string new expression:\n"
													+ "\tPart:{}\n{}\n{}",
													cn, cur.getAnalysisName(), p.toString(), cur.toLogString("\t"), toString("\t"));
											orPart.add(p);
										}
									} else if (rightOp instanceof InvokeExpr) {
										InvokeExpr ie = ((InvokeExpr)rightOp);
										SootMethodRef ref = ie.getMethodRef();
										String sig = ref.getSignature();
										String declaringClass = ref.declaringClass().toString();
										String subSig = ref.getSubSignature().toString();
										String name = ref.name();
										List<Type> parmTypes = ref.parameterTypes();
										if(subSig.equals("java.lang.String toString()")
												&& (declaringClass.equals("java.lang.StringBuilder") ||
													declaringClass.equals("java.lang.StringBuffer"))) {
											orPart.add(handleStringBuilder(cur, def, sourceMethod, queue));
										} else if(declaringClass.equals("java.io.File")) {
											orPart.add(handleFileMethods(cur, def, sourceMethod, queue));
										} else if((declaringClass.equals("android.os.Environment") &&
												(name.equals("buildPath") || name.equals("buildPaths")))
												|| (declaringClass.equals("java.nio.file.Paths") && parmTypes.size() == 2)) {
											//Environment and Paths function the same
											orPart.add(handlePathsAndEnvironmentBuilders(cur, def, sourceMethod, queue, 
													declaringClass.equals("java.nio.file.Paths")));
										} else if(sig.equals("<android.os.Environment: java.io.File getDirectory(java.lang.String,java.lang.String)>")) {
											PHArgumentValuePart arg0Part = new PHArgumentValuePart(def, sourceMethod, 0);
											PHArgumentValuePart arg1Part = new PHArgumentValuePart(def, sourceMethod, 1);
											queue.add(arg0Part);
											queue.add(arg1Part);
											orPart.add(new EnvVarPart(arg0Part));
											orPart.add(arg1Part);
										} else if((declaringClass.equals("java.lang.String") && name.equals("valueOf") 
												&& parmTypes.get(0) instanceof PrimType)
												|| (declaringClass.equals("java.lang.Integer") && name.equals("toString"))
												|| (declaringClass.equals("java.lang.Long") && name.equals("toString"))
												|| (declaringClass.equals("java.lang.Integer") && name.equals("parseInt"))) {
											//Pass through to get value
											PHArgumentValuePart arg0Part = new PHArgumentValuePart(def, sourceMethod, 0);
											queue.add(arg0Part);
											orPart.add(arg0Part);
										} else if(sig.equals("<java.lang.System: java.lang.String getenv(java.lang.String)>")) {
											PHArgumentValuePart arg0Part = new PHArgumentValuePart(def, sourceMethod, 0);
											queue.add(arg0Part);
											orPart.add(new EnvVarPart(arg0Part));
										} else if(sig.equals("<android.os.SystemProperties: java.lang.String get(java.lang.String)>")) {
											PHArgumentValuePart arg0Part = new PHArgumentValuePart(def, sourceMethod, 0);
											queue.add(arg0Part);
											orPart.add(new SysVarPart(arg0Part));
										} else if(sig.equals("<android.os.SystemProperties: java.lang.String get(java.lang.String,java.lang.String)>")) {
											PHArgumentValuePart arg0Part = new PHArgumentValuePart(def, sourceMethod, 0);
											PHArgumentValuePart arg1Part = new PHArgumentValuePart(def, sourceMethod, 1);
											queue.add(arg0Part);
											queue.add(arg1Part);
											orPart.add(new SysVarPart(arg0Part));
											orPart.add(arg1Part);
										} else if(fileUtilsDirListMethods.contains(sig)) {
											PHArgumentValuePart arg0Part = new PHArgumentValuePart(def, sourceMethod, 0);
											queue.add(arg0Part);
											AppendPart app = new AppendPart();
											app.add(arg0Part);
											app.add(unixFileSepConstant);
											app.add(new AnyChildPathPart(def, sourceMethod));
											orPart.add(app);
										} else if(sig.equals("<android.util.AtomicFile: java.io.File getBaseFile()>")
												|| sig.equals("<com.android.internal.os.AtomicFile: java.io.File getBaseFile()>")
												|| sig.equals("<java.lang.String: java.lang.String intern()>")
												|| sig.equals("<java.io.File: java.nio.file.Path toPath()>")) {
											PHBaseValuePart next = new PHBaseValuePart(def, sourceMethod);
											queue.add(next);
											orPart.add(next);
										} else if(sig.equals("<java.util.Iterator: java.lang.Object next()>")) {
											orPart.add(handleIterator(cur, def, sourceMethod, (InstanceInvokeExpr)ie, queue));
										} else {
											boolean found = false;
											//clinit has body but we are removing outgoing edges for noise reasons
											//in those cases we actually need the edges we resolve them if static
											Set<SootMethod> targets = new HashSet<>();
											if(sourceMethod.getName().equals("<clinit>") && ie instanceof StaticInvokeExpr) {
												try {
													targets.add(ie.getMethod());
												} catch(Throwable e) {}
											} else {
												targets.addAll(icfg.getAllCalleesOfCallAt(def));
											}
											//Includes everything with or without a body
											for(SootMethod sm : targets) {
												if(!sm.getName().equals("<clinit>") || name.equals("<clinit>")) {
													boolean passthrough = true;
													if(subClassesOfContext.contains(declaringClass)) {
														passthrough = false;
														//Only include ContextImpl occurrences because the others are nothing
														if(sm.getDeclaringClass().toString().equals("android.app.ContextImpl")) {
															Part p = simulatedContextParts.get(sm.toString());
															if(p == null) {
																passthrough = true;
															} else {
																orPart.add(p);
																found = true;
															}
														}
													}
													
													if(passthrough) {
														if(subClassesOfIBinder.contains(sm.getDeclaringClass().toString())
																&& sm.getName().equals("toString") && sm.getParameterCount() == 0) {
															orPart.add(new AnyInfoPart("BINDERTOKEN"));
															passthrough = false;
															found = true;
														}
													}
													
													if(passthrough) {
														if(sm.hasActiveBody()) {
															PHPart next = new PHReturnValuePart(def, sourceMethod, sm);
															queue.add(next);
															orPart.add(next);
															found = true;
														} else if(!sm.isAbstract()) { //Native and excluded methods
															orPart.add(new AnyMethodReturnPart(def, sourceMethod, sm));
															found = true;
														}
													}
												}
											}
											//No outgoing edges then record the invoke statement at the very least
											if(!found)
												orPart.add(new AnyMethodRefPart(def, sourceMethod));
										}
									} else if(rightOp instanceof UnopExpr || rightOp instanceof BinopExpr) {
										orPart.add(new AnyNumberPart(def, sourceMethod, rightOp.getType().toString()));
									} else {
										UnknownValuePart p = new UnknownValuePart(def, sourceMethod, rightOp.toString());
										logger.warn("{}: {} - Unhandled value:\n\tPart:{}\n\tStart Stmt:{}\n\tStart Source:{}\n{}\n{}",
												cn, cur.getAnalysisName(), p.toString(), stmt, sourceMethod, cur.toLogString("\t"), toString("\t"));
										orPart.add(p);
									}
								}
								if(orPart.getChildNodes().size() == 1)
									compPart = orPart.getChildren().get(0);
								else
									compPart = orPart;
							}
						}
						container.add(compPart);
					}
					if(container.getChildNodes().size() == 1)
						data.put(cur, container.getChildren().get(0));
					else
						data.put(cur, container);
				}
			}
			
			Map<PHPart,Part> leafs = new HashMap<>();
			Map<PHPart,Pair<Part,LinkedHashSet<PHPart>>> adjLists = new HashMap<>();
			
			//Separate out those ImdData instances with computed Parts containing no ImdData references from those with references
			for(PHPart node : data.keySet()) {
				ArrayDeque<Part> q = new ArrayDeque<>();
				q.add(data.get(node));
				boolean isLeaf = true;
				while(!q.isEmpty()) {
					Part cur = q.poll();
					if(cur instanceof BranchPart) {
						if(!(cur instanceof LoopPart))
							q.addAll(((BranchPart)cur).getChildren());
					} else if(cur instanceof PHPart) {
						Pair<Part,LinkedHashSet<PHPart>> adjList = adjLists.get(node);
						if(adjList == null) {
							adjList = new Pair<>(data.get(node), new LinkedHashSet<>());
							adjLists.put(node, adjList);
						}
						adjList.getSecond().add((PHPart)cur);
						isLeaf = false;
					}
				}
				if(isLeaf) {
					leafs.put(node, data.get(node));
				}
			}
			
			if(subAndCollapseCycles(adjLists,leafs)) {
				return simplify(leafs.get(startData));
			} else {
				return null;
			}
		}
		
		private Part handleIterator(PHPart curStart, DefinitionStmt invokeStmt, SootMethod sourceMethod, InstanceInvokeExpr ie,  Deque<PHPart> queue) {
			AdvLocalDefs adv = icfg.getOrMakeLocalDefs(sourceMethod);
			Set<DefinitionStmt> iteratorDefs= adv.getDefsWithAliasesRemoveLocalAndCast((Local)ie.getBase(), invokeStmt);
			if(iteratorDefs.isEmpty()) {
				UnknownValuePart compPart = new UnknownValuePart(invokeStmt, sourceMethod, ie.toString());
				logger.warn("{}: {} - No definition for Iterator Object:\n\tPart:{}\n{}\n{}",
						cn,curStart.getAnalysisName(),compPart.toString(),curStart.toLogString("\t"),toString("\t"));
				return compPart;
			}
			
			OrPart orPart = new OrPart();
			for(DefinitionStmt itDef : iteratorDefs) {
				if(itDef.containsInvokeExpr() && itDef.getInvokeExpr().getMethodRef().getSubSignature().toString().equals("java.util.Iterator iterator()")) {
					Set<DefinitionStmt> collDefs = adv.getDefsWithAliasesRemoveLocalAndCast((Local)((InstanceInvokeExpr)itDef.getInvokeExpr()).getBase(), itDef);
					for(DefinitionStmt collDef : collDefs) {
						Value rightOp = collDef.getRightOp();
						if(rightOp instanceof ParameterRef && sourceMethod.getSignature().equals("<com.android.server.pm.UserDataPreparer: void "
								+ "reconcileUsers(java.lang.String,java.util.List,java.util.List)>")) {
							//Special handeling for this case
							boolean found = false;
							for(Unit callerStmt : icfg.getCallersOf(sourceMethod)) {
								SootMethod callerSourceMethod = icfg.getMethodOf(callerStmt);
								for(Unit u : callerSourceMethod.retrieveActiveBody().getUnits()) {
									if(((Stmt)u).containsInvokeExpr() 
											&& fileUtilsDirListMethods.contains(((Stmt)u).getInvokeExpr().getMethodRef().getSignature())) {
										PHArgumentValuePart next = new PHArgumentValuePart((Stmt)u, callerSourceMethod, 0);
										queue.add(next);
										AppendPart app = new AppendPart();
										app.add(next);
										app.add(unixFileSepConstant);
										app.add(new AnyChildPathPart((Stmt)u, callerSourceMethod));
										orPart.add(app);
										found = true;
									}
								}
							}
							if(!found) {
								UnknownValuePart compPart = new UnknownValuePart(collDef, sourceMethod, rightOp.toString());
								logger.warn("{}: {} - Something changed in the code of reconcileUsers:\n\tPart:{}\n{}\n{}",
										cn,curStart.getAnalysisName(),compPart.toString(),curStart.toLogString("\t"),toString("\t"));
								orPart.add(compPart);
							}
						} else if(rightOp instanceof InvokeExpr) {
							String sig = ((InvokeExpr)rightOp).getMethodRef().getSignature();
							if(sig.equals("<com.android.server.pm.PackageInstallerService: android.util.ArraySet newArraySet(java.lang.Object[])>")) {
								PHArgumentValuePart next = new PHArgumentValuePart(collDef, sourceMethod, 0);
								queue.add(next);
								orPart.add(next);
							} else {
								Collection<SootMethod> targets = icfg.getCalleesOfCallAt(collDef);
								if(targets.isEmpty()) {
									orPart.add(new AnyMethodRefPart(collDef, sourceMethod));
								} else {
									for(SootMethod target : targets) {
										orPart.add(new AnyMethodReturnPart(collDef, sourceMethod, target));
									}
								}
							}
						} else if(rightOp instanceof FieldRef 
								&& (((FieldRef)rightOp).getFieldRef().getSignature().equals("<com.android.server.backup.params.BackupParams: java.util.ArrayList kvPackages>")
								|| ((FieldRef)rightOp).getFieldRef().getSignature().equals("<com.android.server.pm.PackageInstallerSession: java.util.List mResolvedStagedFiles>"))) {
							orPart.add(new AnyInfoPart("PACKAGENAME"));
						} else if(rightOp instanceof FieldRef 
								&& (((FieldRef)rightOp).getFieldRef().getSignature().equals("<com.android.server.am.Pageboost$VramdiskHotFileManager: java.util.LinkedList mFiles>") 
								|| ((FieldRef)rightOp).getFieldRef().getSignature().equals("<com.android.server.am.Pageboost$VramdiskBootFileManager: java.util.LinkedList mFiles>"))) {
							orPart.add(new AnyInfoPart("VRAMDISK"));
						} else if(rightOp instanceof NewExpr && 
								(((NewExpr)rightOp).getType().toString().equals("java.util.HashSet") || ((NewExpr)rightOp).getType().toString().equals("java.util.ArrayList"))) {
							orPart.add(new AnyNewInvokePart(collDef, sourceMethod));
						} else {
							UnknownValuePart compPart = new UnknownValuePart(collDef, sourceMethod, rightOp.toString());
							logger.warn("{}: {} - Unhandled definition iterating collection:\n\tPart:{}\n{}\n{}",
									cn,curStart.getAnalysisName(),compPart.toString(),curStart.toLogString("\t"),toString("\t"));
							orPart.add(compPart);
						}
					}
				} else {
					UnknownValuePart compPart = new UnknownValuePart(itDef, sourceMethod, itDef.getRightOp().toString());
					logger.warn("{}: {} - Unhandled definition for Iterator Object:\n\tPart:{}\n{}\n{}",
							cn,curStart.getAnalysisName(),compPart.toString(),curStart.toLogString("\t"),toString("\t"));
					orPart.add(compPart);
				}
			}
			
			if(orPart.size() == 1)
				return orPart.getChildren().get(0);
			return orPart;
		}
		
		private List<Part> constructParamRefs(int index, SootMethod sourceMethod, PHPart cur, DefinitionStmt def) {
			if(def == null) {
				Body b = sourceMethod.retrieveActiveBody();
				Value rightOp = b.getParameterRefs().get(index);
				for(Unit u : b.getUnits()) {
					if(u instanceof DefinitionStmt && ((DefinitionStmt)u).getRightOp().equals(rightOp)) {
						def = (DefinitionStmt)u;
						break;
					}
				}
			}
			
			List<Part> ret = new ArrayList<>();
			if(sourceMethod.equals(ep.getEntryPoint())) {
				ret.add(new AnyEPArgPart(def, sourceMethod, index));
			} else {
				Collection<Unit> callSites = icfg.getCallersOf(sourceMethod);
				if(callSites == null || callSites.isEmpty()) {
					UnknownValuePart p = new UnknownValuePart(def, sourceMethod, ((DefinitionStmt)def).getRightOp().toString());
					logger.warn("{}: {} - The referenced parameter has no resolvable value because this method is never called:\n"
							+ "\tPart:{}\n{}\n{}",
							cn, cur.getAnalysisName(), p.toString(), cur.toLogString("\t"), toString("\t"));
					ret.add(p);
				} else {
					for(Unit u : callSites) {
						PHPart next = new PHArgumentValuePart((Stmt)u, icfg.getMethodOf(u), index);
						ret.add(next);
					}
				}
			}
			return ret;
		}
		
		private Part handleArrayValues(PHPart curStart, DefinitionStmt arrValueUse, SootMethod sourceMethod, ArrayRef arrRef,  Deque<PHPart> queue) {
			AdvLocalDefs adv = icfg.getOrMakeLocalDefs(sourceMethod);
			Set<DefinitionStmt> defs = adv.getDefsWithAliasesRemoveLocalAndCast((Local)arrRef.getBase(), arrValueUse);
			if(defs.isEmpty()) {
				UnknownValuePart compPart = new UnknownValuePart(arrValueUse, sourceMethod, arrRef.toString());
				logger.warn("{}: {} - No definition for base local of the Array:\n\tPart:{}\n{}\n{}",
						cn,curStart.getAnalysisName(),compPart.toString(),curStart.toLogString("\t"),toString("\t"));
				return compPart;
			} else {
				OrPart orPart = new OrPart();
				boolean found = false;
				for(DefinitionStmt def : defs) {
					if(def.containsInvokeExpr()) {
						SootMethodRef ref = def.getInvokeExpr().getMethodRef();
						String sig = ref.getSignature();
						PHPart newNext = null;
						if(fileDirListMethods.contains(sig)) {
							newNext = new PHBaseValuePart(def, sourceMethod);
						} else if(fileUtilsDirListMethods.contains(sig)) {
							newNext = new PHArgumentValuePart(def, sourceMethod, 0);
						}
						
						if(newNext != null) {
							queue.add(newNext);
							AppendPart app = new AppendPart();
							app.add(newNext);
							app.add(unixFileSepConstant);
							app.add(new AnyChildPathPart(def, sourceMethod));
							orPart.add(app);
							found = true;
						}
					}
				}
				
				if(!found)
					return new AnyArrayPart(arrValueUse, sourceMethod);
				
				if(orPart.size() == 1)
					return orPart.getChildren().get(0);
				return orPart;
			}
		}
		
		private Part handleOuterNoiseCases(PHPart cur, Stmt stmt, SootMethod sourceMethod, Value v) {
			String sourceClass = sourceMethod.getDeclaringClass().toString();
			String sourceName = sourceMethod.getName();
			String sourceSig = sourceMethod.getSignature();
			if(sourceClass.startsWith("android.app.ResourcesManager") || sourceClass.startsWith("android.content.res.AssetManager")
					|| sourceClass.startsWith("android.content.pm.split.DefaultSplitAssetLoader") 
					|| sourceClass.startsWith("android.content.pm.split.SplitAssetDependencyLoader")) {
				return new AnyResourceOrAssetPart(stmt, sourceMethod);
			} else if(sourceClass.equals("android.os.ShellCommand") && (sourceName.equals("getNextArg") 
					|| sourceName.equals("getNextArgRequired") || sourceName.equals("getNextOption") || sourceName.equals("peekNextArg"))) {
				return new AnyCommandLineInputPart(stmt, sourceMethod);
			} else if(sourceClass.equals("dalvik.system.VMRuntime")) {
				return new AnyVMRuntimeSettingPart(stmt, sourceMethod);
			} else if(sourceSig.equals("<com.android.server.accounts.AccountManagerService: android.accounts.AccountAndUser[] getAccounts(int[])>")) {
				return new AnyAccountIdPart(stmt, sourceMethod);
			} else if(sourceSig.equals("<com.android.server.am.ActivityManagerService: int broadcastIntentLocked(com.android.server.am.ProcessRecord,"
					+ "java.lang.String,android.content.Intent,java.lang.String,android.content.IIntentReceiver,int,java.lang.String,android.os.Bundle,"
					+ "java.lang.String[],int,android.os.Bundle,boolean,boolean,int,int,int)>") && v.getType() instanceof IntType) {
				return new AnyUserIdPart(stmt, sourceMethod);
			} else if(sourceClass.equals("com.android.server.am.UserController")) {
				return new AnyUserIdPart(stmt, sourceMethod);
			} else if(sourceClass.equals("android.app.LoadedApk")) {
				return new AnyAPKInfoPart(stmt, sourceMethod);
			} else if(sourceClass.equals("com.android.internal.content.NativeLibraryHelper$Handle")) {
				return new AnyInfoPart("PACKAGECODEPATHS");
			} else if(sourceSig.equals("<com.android.server.pm.PackageDexOptimizer: int performDexOptLI(android.content.pm.PackageParser$Package,"
					+ "java.lang.String[],java.lang.String[],com.android.server.pm.CompilerStats$PackageStats,"
					+ "com.android.server.pm.dex.PackageDexUsage$PackageUseInfo,com.android.server.pm.dex.DexoptOptions)>")) {
				return new AnyInfoPart("PACKAGECODEPATHS");
			} else if(sourceSig.equals("<com.android.server.am.ProcessStatsService: byte[] getCurrentStats(java.util.List)>")) {
				AppendPart app = new AppendPart();
				app.add(new StringConstantPart("/data/system/procstats/"));
				app.add(new AnyChildPathPart(null,null));
				return app;
			} else if(sourceSig.equals("<com.android.server.backup.BackupManagerService: void setBackupEnabled(boolean)>")) {
				return new AnyInfoPart("TRANSPORTNAME");
			} else if(sourceSig.equals("<com.android.server.pm.PackageManagerService: void reconcileApps(java.lang.String)>")) {
				OrPart or = new OrPart();
				or.add(new StringConstantPart("/data/"));
				or.add(new StringConstantPart("/mnt/expand/"));
				AppendPart app = new AppendPart();
				app.add(or);
				app.add(new StringConstantPart("app/"));
				app.add( new AnyChildPathPart(null,null));
				return app;
			} else if(sourceSig.equals("<com.android.server.pm.Settings: java.lang.String getRenamedPackageLPr(java.lang.String)>")) {
				return new AnyInfoPart("PACKAGENAME");
			}
			return null;
		}
		
		private Part handleInnerNoiseCases(PHPart cur, DefinitionStmt startDef, SootMethod sourceMethod, Value rightOpStart, Deque<PHPart> queue) {
			if(rightOpStart instanceof ArrayRef) {
				AdvLocalDefs adv = icfg.getOrMakeLocalDefs(sourceMethod);
				Set<DefinitionStmt> defs = adv.getDefsWithAliasesRemoveLocalAndCast((Local)((ArrayRef)rightOpStart).getBase(), startDef);
				if(!defs.isEmpty()) {
					OrPart orPart = new OrPart();
					for(DefinitionStmt def : defs) {
						Value rightOp = def.getRightOp();
						if(rightOp instanceof FieldRef) {
							SootField field = HierarchyHelpers.resolveField(((FieldRef)rightOp).getFieldRef());
							String fieldClass = field.getDeclaringClass().toString();
							if(fieldClass.startsWith("android.content.pm.PackageParser") 
									|| fieldClass.equals("android.content.pm.ApplicationInfo")) {
								orPart.add(new AnyFieldRefPart(def, sourceMethod));
							}
						} else if(rightOp instanceof InvokeExpr) {
							SootMethodRef ref = ((InvokeExpr)rightOp).getMethodRef();
							String sig = ref.getSignature();
							if(sig.equals("<android.content.pm.PackageParser$Callback: java.lang.String[] getOverlayApks(java.lang.String)>")) {
								Collection<SootMethod> targets = icfg.getCalleesOfCallAt(def);
								if(targets.isEmpty()) {
									orPart.add(new AnyMethodRefPart(def, sourceMethod));
								} else {
									for(SootMethod tgt : targets) {
										orPart.add(new AnyMethodReturnPart(def, sourceMethod, tgt));
									}
								}
							} else if(sig.equals("<com.android.server.pm.permission.DefaultPermissionGrantPolicy: java.io.File[] getDefaultPermissionFiles()>")) {
								//Can do this because it is a private method
								SootMethod target = ref.resolve();
								Body b = target.retrieveActiveBody();
								for(Unit u : b.getUnits()) {
									if(((Stmt)u).containsInvokeExpr()) {
										InvokeExpr ie = ((Stmt)u).getInvokeExpr();
										SootMethodRef newExprRef = ie.getMethodRef();
										if(newExprRef.name().equals("<init>") && newExprRef.declaringClass().toString().equals("java.io.File")) {
											List<Value> args = ie.getArgs();
											if(args.size() == 1 && ie.getMethodRef().parameterType(0).toString().equals("java.lang.String")) {
												PHPart next = new PHArgumentValuePart((Stmt)u,target,0);
												queue.add(next);
												AppendPart app = new AppendPart();
												app.add(next);
												app.add(unixFileSepConstant);
												app.add(new AnyChildPathPart((Stmt)u, target));
												orPart.add(app);
											} else if(args.size() == 2) {
												PHPart nextArg0 = new PHArgumentValuePart((Stmt)u,target,0);
												PHPart nextArg1 = new PHArgumentValuePart((Stmt)u,target,1);
												queue.add(nextArg0);
												queue.add(nextArg1);
												AppendPart app = new AppendPart();
												app.add(nextArg0);
												app.add(unixFileSepConstant);
												app.add(nextArg1);
												app.add(unixFileSepConstant);
												app.add(new AnyChildPathPart((Stmt)u, target));
												orPart.add(new NormalizePart(app));
											} else {
												orPart.add(new AnyNewInvokePart((Stmt)u, target));
											}
										}
									}
								}
							}
						}
					}
					
					if(!orPart.isEmpty()) {
						if(orPart.size() == 1)
							return orPart.getChildren().get(0);
						return orPart;
					}
				}
			}
			return null;
		}
		
		/* Both buildPath and buildPaths take in an array of string values and either a file
		 * or an array of Files. The array of string arguments is appended to the file(s) in 
		 * order with each string representing a specific entity (directory or file). Moreover,
		 * the array of strings is one of java's auto constructed arrays so the array should 
		 * always be declared in the method that invokes the builder methods. This is an 
		 * assumption we are making for simplicity anyways. For the files or the array of files 
		 * we treat them as we normally would any value we are trying to resolve. This had to 
		 * have special handling because the array of strings was being appended and not or.
		 */
		private Part handlePathsAndEnvironmentBuilders(PHPart curStart, Stmt callSite, SootMethod sourceMethod, Deque<PHPart> queue, boolean normalize) {
			InvokeExpr ie = callSite.getInvokeExpr();
			AppendPart app = new AppendPart();
			PHArgumentValuePart arg0Part = new PHArgumentValuePart(callSite, sourceMethod, 0);
			app.add(arg0Part);
			queue.add(arg0Part);
			
			OrPart temp = new OrPart();
			Local l = (Local)ie.getArg(1);
			AdvLocalDefs adv = icfg.getOrMakeLocalDefs(sourceMethod);
			Set<DefinitionStmt> defs = adv.getDefsWithAliasesRemoveLocalAndCast(l, callSite);
			if(defs.isEmpty()) {
				UnknownValuePart compPart = new UnknownValuePart(callSite, sourceMethod, l.toString());
				logger.warn("{}: {} - No definition for local of BuildPath Arr:\n\tPart:{}\n{}\n{}",
						cn,curStart.getAnalysisName(),compPart.toString(),curStart.toLogString("\t"),toString("\t"));
				temp.add(compPart);
			} else {
				AdvLocalUses advUses = icfg.getOrMakeLocalUses(sourceMethod);
				for(DefinitionStmt def : defs) {
					Value rightOp = def.getRightOp();
					if(rightOp instanceof NewArrayExpr) {
						Value size = ((NewArrayExpr)rightOp).getSize();
						if(size instanceof IntConstant) {
							OrPart[] toAppend = new OrPart[((IntConstant)size).value];
							for(int i = 0; i < toAppend.length; i++)
								toAppend[i] = new OrPart();
							Set<UnitValueBoxPair> uses = advUses.getUsesWithAliasesRemoveLocalAndCast(def);
							for(UnitValueBoxPair up : uses) {
								Stmt arrStmt = (Stmt)up.getUnit();
								Local arrLocal = (Local)up.getValueBox().getValue();
								if(arrStmt.containsArrayRef() && ((DefinitionStmt)arrStmt).getLeftOp() instanceof ArrayRef
										&& arrStmt.getArrayRef().getBase().equals(arrLocal)) {
									ArrayRef arrRef = arrStmt.getArrayRef();
									if(arrRef.getIndex() instanceof IntConstant) {
										OrPart or = toAppend[((IntConstant)arrRef.getIndex()).value];
										PHArrayValuePart next = new PHArrayValuePart(((DefinitionStmt)arrStmt), sourceMethod);
										or.add(next);
										queue.add(next);
									} else {
										UnknownValuePart compPart = new UnknownValuePart(arrStmt, sourceMethod, 
												((DefinitionStmt)arrStmt).getLeftOp().toString());
										logger.warn("{}: {} - Non-constant index of BuildPath Arr:\n\tPart:{}\n\tStart Stmt:{}\n\tStart Source:{}\n{}\n{}",
												cn, curStart.getAnalysisName(), compPart.toString(), callSite, sourceMethod, 
												curStart.toLogString("\t"), toString("\t"));
										temp.add(compPart);
									}
								}
							}
							AppendPart arrApp = new AppendPart();
							for(int i = 0; i < toAppend.length; i++) {
								OrPart or = toAppend[i];
								if(i > 0)
									arrApp.add(unixFileSepConstant);
								if(or.isEmpty())
									arrApp.add(nullConstant);
								else if(or.size() == 1)
									arrApp.add(or.getChildren().get(0));
								else
									arrApp.add(or);
							}
							//Add nothing if append part is empty because this is the case where an empty array is passed in
							//and therefore just the first argument is returned as a path
							if(arrApp.size() == 1)
								temp.add(arrApp.getChildren().get(0));
							else
								temp.add(arrApp);
						} else {
							UnknownValuePart compPart = new UnknownValuePart(def, sourceMethod, rightOp.toString());
							logger.warn("{}: {} - Non-constant size of BuildPath Arr:\n\tPart:{}\n\tStart Stmt:{}\n\tStart Source:{}\n{}\n{}",
									cn, curStart.getAnalysisName(), compPart.toString(), callSite, sourceMethod, 
									curStart.toLogString("\t"), toString("\t"));
							temp.add(compPart);
						}
					} else {
						UnknownValuePart compPart = new UnknownValuePart(def, sourceMethod, rightOp.toString());
						logger.warn("{}: {} - Unhandled value for def of BuildPath Arr:\n\tPart:{}\n\tStart Stmt:{}\n\tStart Source:{}\n{}\n{}",
								cn, curStart.getAnalysisName(), compPart.toString(), callSite, sourceMethod, 
								curStart.toLogString("\t"), toString("\t"));
						temp.add(compPart);
					}
				}
			}
			
			//All possible arrays were empty so nothing to append on the end
			if(!temp.isEmpty()) {
				app.add(unixFileSepConstant);
				if(temp.size() == 1)
					app.add(temp.getChildren().get(0));
				else
					app.add(temp);
			}
			
			if(normalize)
				return new NormalizePart(app);
			else
				return app;
		}
		
		private Part handleFileMethods(PHPart curStart, Stmt callSite, SootMethod sourceMethod, Deque<PHPart> queue) {
			InvokeExpr ie = callSite.getInvokeExpr();
			SootMethodRef ref = ie.getMethodRef();
			if(ref.name().equals("getAbsoluteFile") || ref.name().equals("getAbsolutePath")
					|| ref.name().equals("getCanonicalPath") || ref.name().equals("getCanonicalFile")) {
				PHBaseValuePart next = new PHBaseValuePart(callSite, sourceMethod);
				AppendPart app = new AppendPart();
				app.add(new AnyParentPathPart(callSite, sourceMethod));
				app.add(unixFileSepConstant);
				app.add(next);
				queue.add(next);
				return new NormalizePart(app);
			} else if(ref.name().equals("getPath") || ref.name().equals("toString")) {
				PHBaseValuePart next = new PHBaseValuePart(callSite, sourceMethod);
				queue.add(next);
				return next;
			} else if(ref.name().equals("getParentFile") || ref.name().equals("getParent")) {
				PHBaseValuePart next = new PHBaseValuePart(callSite, sourceMethod);
				queue.add(next);
				return new ParentPart(next);
			} else if(ref.name().equals("getName")) {
				PHBaseValuePart next = new PHBaseValuePart(callSite, sourceMethod);
				queue.add(next);
				return new NamePart(next);
			} else if(fileDirListMethods.contains(ref.getSignature())) {
				PHPart next = new PHBaseValuePart(callSite, sourceMethod);
				queue.add(next);
				AppendPart app = new AppendPart();
				app.add(next);
				app.add(unixFileSepConstant);
				app.add(new AnyChildPathPart(callSite, sourceMethod));
				return app;
			} else {
				//Only one resolution possible for file so we can do this here
				return new AnyMethodReturnPart(callSite, sourceMethod, ie.getMethod());
			}
		}
		
		private Part handleStringBuilder(PHPart curStart, Stmt toStringCallSite, SootMethod sourceMethod, Deque<PHPart> queue) {
			Local sbObjectRef = ((Local)(((InstanceInvokeExpr)toStringCallSite.getInvokeExpr()).getBase()));
			AdvLocalDefs adv = icfg.getOrMakeLocalDefs(sourceMethod);
			Set<DefinitionStmt> defs = adv.getDefsWithAliasesRemoveLocalAndCast(sbObjectRef, toStringCallSite);
			AdvLocalUses advUses = icfg.getOrMakeLocalUses(sourceMethod);
			OrPart orPart = new OrPart();
			for(DefinitionStmt def : defs) {
				Value rightOp = def.getRightOp();
				AppendPart app = new AppendPart();
				orPart.add(app);
				if(!(rightOp instanceof NewExpr)) {
					logger.warn("{}: StringBuilder was not created in the current method. Appending Unknown Part to represent previous possible contents."
							+ "\n\tDef:{}\n\tStart Stmt:{}\n\tStart Source:{}\n{}\n{}",
							cn, def, toStringCallSite, sourceMethod, curStart.toLogString("\t"), toString("\t"));
					UnknownValuePart p = new UnknownValuePart(def, sourceMethod, rightOp.toString());
					app.add(p);
				}
				
				Set<UnitValueBoxPair> uses = advUses.getUsesWithAliasesRemoveLocalAndCast(def);
				Set<Unit> usesUnits = new HashSet<>();
				for(UnitValueBoxPair p : uses) {
					usesUnits.add(p.getUnit());
				}
				Deque<Unit> q = new ArrayDeque<>();
				Deque<BranchPart> parents = new ArrayDeque<>();
				Set<Unit> visited = new HashSet<>();
				q.add(def);
				parents.add(app);
				while(!q.isEmpty()) {
					Unit cur = q.poll();
					BranchPart parent = parents.poll();
					if(visited.add(cur)) {
						if(usesUnits.contains(cur) && !cur.equals(toStringCallSite)) {
							if(((Stmt)cur).containsInvokeExpr()) {
								InvokeExpr ie = ((Stmt)cur).getInvokeExpr();
								SootMethodRef ref = ie.getMethodRef();
								if((ref.name().equals("append") || ref.name().equals("<init>")) && ref.parameterTypes().size() == 1 
										&& (ref.declaringClass().toString().equals("java.lang.StringBuilder") 
										|| ref.declaringClass().toString().equals("java.lang.StringBuffer"))) {
									//Any append or init with a string only
									if(!ref.name().equals("<init>") || !(ref.parameterType(0) instanceof IntType)) {
										PHPart next = new PHArgumentValuePart((Stmt)cur,sourceMethod,0);
										queue.add(next);
										parent.add(next);
									}
								} else if(!ie.getMethodRef().name().equals("<init>")) {
									logger.warn("{}: Encountered unhandled StringBuilder method."
											+ "\n\tUse:{}\n\tStart Stmt:{}\n\tStart Source:{}\n{}\n{}",
											cn, cur, toStringCallSite, sourceMethod, curStart.toLogString("\t"), toString("\t"));
									AnyMethodRefPart p = new AnyMethodRefPart((Stmt)cur, sourceMethod);
									parent.add(p);
								}
							} else {
								logger.warn("{}: Encountered use of StringBuilder that is not handled."
										+ "\n\tUse:{}\n\tStart Stmt:{}\n\tStart Source:{}\n{}\n{}",
										cn, cur, toStringCallSite, sourceMethod, curStart.toLogString("\t"), toString("\t"));
								UnknownStmtPart p = new UnknownStmtPart((Stmt)cur, sourceMethod);
								parent.add(p);
							}
						}
						
						if(!cur.equals(toStringCallSite)) {
							List<Unit> succs = icfg.getSuccsOf(cur);
							if(succs.size() > 1) {
								OrPart or = new OrPart();
								parent.add(or);
								for(Unit succ : succs) {
									q.add(succ);
									AppendPart newApp = new AppendPart();
									or.add(newApp);
									parents.add(newApp);
								}
							} else if(succs.size() == 1) {
								q.add(succs.get(0));
								parents.add(parent);
							}
						}
					}
				}
			}
			
			if(orPart.size() == 1)
				return orPart.iterator().next();
			return orPart;
		}
		
		private boolean subAndCollapseCycles(Map<PHPart, Pair<Part, LinkedHashSet<PHPart>>> adjLists, Map<PHPart,Part> leafs) {
			preformSubstitution(adjLists,leafs,leafs);
			//If we end up with an empty adjList then there are no cycles
			int itCount = 0;
			while(!adjLists.isEmpty()) {
				itCount += 1;
				if(itCount > 100) {
					logger.warn("{}: AdjList is not empty after {} iterations.\n{}\n{}",
							cn, itCount, adjListsToString(adjLists), toString("\t"));
				}
				List<Pair<PHPart,PHPart>> cycles = Cycles.findSimpleCyclesStartEnd(adjLists);
				Map<PHPart,Part> newLeafs = new HashMap<>();
				Set<PHPart> toRemove = new HashSet<>();
				for(Pair<PHPart,PHPart> startEnd : cycles) {
					final PHPart start = startEnd.getFirst();
					final PHPart end = startEnd.getSecond();
					
					if(!toRemove.contains(end)) {
						@SuppressWarnings("unchecked")
						Pair<Part,LinkedHashSet<PHPart>>[] endPair = new Pair[1];
						endPair[0] = adjLists.get(end);
						Pair<Part,LinkedHashSet<PHPart>> startPair = adjLists.get(start);
						endPair[0].getFirst().getIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
							public void accept(Pair<Part,Node> p) {
								Part parent = p.getFirst();
								Node childNode = p.getSecond();
								Part child = childNode.getPart();
								if(child.equals(start)) {
									LoopPart loop = new LoopPart(startPair.getFirst());
									if(parent == null) {
										endPair[0] = new Pair<>(loop,endPair[0].getSecond());
										adjLists.put(end, endPair[0]);
									} else if(parent instanceof BranchPart) {
										//We are subbing in loop references not traversing them
										if(!(parent instanceof LoopPart))
											((BranchPart)parent).swapChild(childNode, loop);
									} else {
										logger.warn("{}: Collapsing loops, encountered a parent Part that is not a BranchPart!?!"
												+ "\n\tParent:{}\n\tChild:{}\n{}\n{}", cn, parent.toString(), child.toString(), 
												adjListsToString(adjLists), FilePathExtractingRunnable.this.toString("\t"));
									}
								}
							}
						});

						endPair[0].getSecond().remove(start);
						if(endPair[0].getSecond().isEmpty()) {
							leafs.put(end, endPair[0].getFirst());
							newLeafs.put(end, endPair[0].getFirst());
							toRemove.add(end);
						}
					}
				}
				
				adjLists.keySet().removeAll(toRemove);
				
				if(!newLeafs.isEmpty()) {
					preformSubstitution(adjLists,leafs,newLeafs);
				} else if(newLeafs.isEmpty() && itCount > 100) {
					logger.warn("{}: No new leafs generated on iterations {}.\n{}\n{}",
							cn, itCount, adjListsToString(adjLists), toString("\t"));
				}
			}
			
			if(!adjLists.isEmpty()) {
				logger.warn("{}: AdjList is not empty. Cycles still exist!?!\n{}\n{}",
						cn, adjListsToString(adjLists), toString("\t"));
				return false;
			}
			return true;
		}
		
		private Part simplify(Part in) {
			Part[] ret = {in.clonePart()};
			boolean[] changed = {false};
			int count = 0;
			
			do {
				changed[0] = false;
				Map<Part,LoopPart> loops = new HashMap<>();
				Map<Part,Part> swaps = new HashMap<>();
				ret[0].getPostOrderIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
					public void accept(Pair<Part,Node> t) {
						Part parent = t.getFirst();
						Node childNode = t.getSecond();
						Part child = childNode.getPart();
						if(parent == null) {
							if(child instanceof LoopPart) {	
								ret[0] = nullConstant;
								changed[0] = true;
							} else if(child instanceof OrPart) {
								List<Node> children = ((OrPart)child).getChildNodes();
								if(children.isEmpty()) {
									ret[0] = nullConstant;
									swaps.put(child, nullConstant);
									changed[0] = true;
								} else if(children.size() == 1) {
									ret[0] = children.get(0).getPart();
									swaps.put(child, children.get(0).getPart());
									changed[0] = true;
								}
							} else if(child instanceof AppendPart) {
								List<Node> children = ((AppendPart)child).getChildNodes();
								if(children.isEmpty()) {
									ret[0] = nullConstant;
									swaps.put(child, nullConstant);
									changed[0] = true;
								} else if(children.size() == 1) {
									ret[0] = children.get(0).getPart();
									swaps.put(child, children.get(0).getPart());
									changed[0] = true;
								}
							} else if(child instanceof NormalizePart || child instanceof NamePart || child instanceof ParentPart 
									|| child instanceof EnvVarPart || child instanceof SysVarPart) {
								if(((BranchPart)child).getChildren().isEmpty()) {
									ret[0] = nullConstant;
									swaps.put(child, nullConstant);
									changed[0] = true;
								}
							}
						} else if(parent instanceof BranchPart) {
							if(child instanceof NullConstantPart) {
								if(((BranchPart)parent).removeChild(childNode)) {
									swaps.put(child, null);
									changed[0] = true;
								}
							} else if(child instanceof LoopPart) {
								if(((LoopPart)child).getChildren().isEmpty()) {
									((BranchPart)parent).removeChild(childNode);
									changed[0] = true;
								} else {
									loops.put(((LoopPart)child).getLoopStart(),((LoopPart)child));
								}
							} else if(child instanceof NormalizePart || child instanceof NamePart || child instanceof ParentPart 
									|| child instanceof EnvVarPart || child instanceof SysVarPart) {
								if(((BranchPart)child).getChildren().isEmpty()) {
									if(((BranchPart)parent).removeChild(childNode)) {
										swaps.put(child, null);
										changed[0] = true;
									}
								}
							} else if(child instanceof OrPart) {
								List<Node> children = ((OrPart)child).getChildNodes();
								if(children.isEmpty()) {
									if(((BranchPart)parent).removeChild(childNode)) {
										swaps.put(child, null);
										changed[0] = true;
									}
								} else if(children.size() == 1) {
									if(((BranchPart)parent).swapChild(childNode, children.get(0))) {
										swaps.put(child, children.get(0).getPart());
										changed[0] = true;
									}
								} else { //If the parent is also an or child then it will merge otherwise nothing happens
									if(((BranchPart)parent).mergeChild(childNode)) {
										swaps.put(child, parent);
										changed[0] = true;
									}
								}
							} else if(child instanceof AppendPart) {
								List<Node> children = ((AppendPart)child).getChildNodes();
								if(children.isEmpty()) {
									if(((BranchPart)parent).removeChild(childNode)) {
										swaps.put(child, null);
										changed[0] = true;
									}
								} else if(children.size() == 1) {
									if(((BranchPart)parent).swapChild(childNode, children.get(0))) {
										swaps.put(child, children.get(0).getPart());
										changed[0] = true;
									}
								} else { //If the parent is also an or child then it will merge otherwise nothing happens
									if(((BranchPart)parent).mergeChild(childNode)) {
										swaps.put(child, parent);
										changed[0] = true;
									}
								}
							}
						} else {
							logger.warn("{}: Simplifying, encountered a parent Part that is not a BranchPart!?!\n\tParent:{}\n\tChild:{}\n\tStart:{}\n{}",
									cn, parent.toString(), child.toString(), in.toString(), FilePathExtractingRunnable.this.toString("\t"));
						}
					}
				});
				
				if(!loops.isEmpty() && !swaps.isEmpty()) {
					Set<LoopPart> emptyLoops = new HashSet<>();
					for(Part orgSwp : swaps.keySet()) {
						LoopPart part = loops.get(orgSwp);
						if(part != null) {
							Part newSwp = swaps.get(orgSwp);
							Node childNode = part.getLoopStartNode();
							if(childNode == null) {
								emptyLoops.add(part);
							} else {
								if(newSwp == null) {
									if(part.removeChild(childNode))
										emptyLoops.add(part);
								} else {
									part.swapChild(childNode, newSwp);
								}
							}
						}
					}
					
					if(!emptyLoops.isEmpty()) {
						ret[0].getPostOrderIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
							public void accept(Pair<Part,Node> t) {
								Part parent = t.getFirst();
								Node childNode = t.getSecond();
								Part child = childNode.getPart();
								if(parent == null) {
									if(emptyLoops.contains(child))
										ret[0] = nullConstant;
								} else if(parent instanceof BranchPart) {
									if(emptyLoops.contains(child))
										((BranchPart)parent).removeChild(childNode);
								} else {
									logger.warn("{}: Simplifying 2, encountered a parent Part that is not a BranchPart!?!\n\tParent:{}\n\tChild:{}\n\tStart:{}\n{}",
											cn, parent.toString(), child.toString(), in.toString(), FilePathExtractingRunnable.this.toString("\t"));
								}
							}
						});
					}
				}
				if(count > 100) {
					logger.warn("{}: Simplifying, is stuck in a loop it {}.\n\tStart:{}\n{}",
							cn, count, in.toString(), FilePathExtractingRunnable.this.toString("\t"));
				}
				count++;
			} while(changed[0]);
			
			return ret[0];
		}
		
		private String adjListsToString(Map<PHPart,Pair<Part,LinkedHashSet<PHPart>>> adjLists) {
			StringBuilder sb = new StringBuilder();
			boolean first = true;
			for(PHPart p : adjLists.keySet()) {
				Pair<Part,LinkedHashSet<PHPart>> pair = adjLists.get(p);
				if(first)
					first = false;
				else
					sb.append("\n");
				sb.append("\tNode: ").append(p).append("\n");
				sb.append("\t\tPair: ").append(pair.getFirst()).append("\n");
				sb.append("\t\tAdjList: ").append(pair.getSecond());
			}
			return sb.toString();
		}
		
		//Substitute all ImdData references pointing to leaf Parts (parts with no other ImdData references) with their references in other instances
		//If this causes these ImdData references to point to leaf Parts the repeat the process
		private void preformSubstitution(Map<PHPart, Pair<Part, LinkedHashSet<PHPart>>> adjLists, 
				Map<PHPart,Part> leafs, Map<PHPart,Part> curLeafs) {
			Map<PHPart,Part> newLeafs = new HashMap<>();
			while(!curLeafs.isEmpty()) {
				for(Iterator<PHPart> itAdjLists = adjLists.keySet().iterator(); itAdjLists.hasNext();) {
					PHPart node = itAdjLists.next();
					Pair<Part,LinkedHashSet<PHPart>> pair = adjLists.get(node);
					Part[] start = {pair.getFirst()};
					Set<PHPart> adjList = pair.getSecond();
					Set<PHPart> toRemove = new HashSet<>();
					Map<PHPart,Part> curLeafsTemp = curLeafs;
					start[0].getIterator().forEachRemaining(new Consumer<Pair<Part,Node>>() {
						public void accept(Pair<Part,Node> p) {
							Part parent = p.getFirst();
							Node childNode = p.getSecond();
							Part child = childNode.getPart();
							if(child instanceof PHPart) {
								Part leafData = curLeafsTemp.get(child);
								if(leafData != null) {
									if(parent == null) {
										//The start data is the ImdData so there can only be one
										//Setting the start data to the leaf data for this ImdData object
										start[0] = leafData;
									} else if(parent instanceof BranchPart) {
										//At this point in the analysis there should not be loop parts but even if there are
										//they should not contain a instance of ImdData as their child
										//For all others we simply swap out the ImdData node with the actual leaf data start point
										if(!(parent instanceof LoopPart))
											((BranchPart)parent).swapChild(childNode, leafData);
									} else {
										logger.warn("{}: Subbing leaf data, encountered a parent Part that is not a BranchPart!?!\n\tParent:{}\n\tChild:{}\n{}\n{}",
												cn, parent.toString(), child.toString(), adjListsToString(adjLists), FilePathExtractingRunnable.this.toString("\t"));
									}
									toRemove.add((PHPart)child);
								}
							}
						}
					});
					adjList.removeAll(toRemove);
					if(adjList.isEmpty()) {
						leafs.put(node, start[0]);
						newLeafs.put(node, start[0]);
						itAdjLists.remove();
					}
				}
				curLeafs = newLeafs;
				newLeafs = new HashMap<>();
			}
		}
		
	}
	
	private void init() {
		SootClass context = Scene.v().getSootClassUnsafe("android.content.Context", false);
		if(context != null) {
			for(SootClass sc : HierarchyHelpers.getAllSubClasses(context)) {
				this.subClassesOfContext.add(sc.toString());
			}
		}
		
		SootClass ibinder = Scene.v().getSootClassUnsafe("android.os.IBinder", false);
		if(ibinder != null) {
			for(SootClass sc : HierarchyHelpers.getAllSubClassesOfInterface(ibinder)) {
				this.subClassesOfIBinder.add(sc.toString());
			}
		}
		
		OrPart dataDirPart = new OrPart();
		AppendPart app = new AppendPart();
		app.add(new StringConstantPart("/data/user/"));
		app.add(new AnyNumberPart(null,null,"INT"));
		app.add(unixFileSepConstant);
		app.add(new AnyInfoPart("PACKAGENAME"));
		dataDirPart.add(app);
		app = new AppendPart();
		app.add(new StringConstantPart("/data/user_de/"));
		app.add(new AnyNumberPart(null,null,"INT"));
		app.add(unixFileSepConstant);
		app.add(new AnyInfoPart("PACKAGENAME"));
		dataDirPart.add(app);
		app = new AppendPart();
		app.add(new StringConstantPart("/data/system/"));
		app.add(new AnyInfoPart("PACKAGENAME"));
		dataDirPart.add(app);
		app = new AppendPart();
		app.add(new StringConstantPart("/data/system_ce/"));
		app.add(new AnyInfoPart("PACKAGENAME"));
		dataDirPart.add(app);
		app = new AppendPart();
		app.add(new StringConstantPart("/data/system_de/"));
		app.add(new AnyInfoPart("PACKAGENAME"));
		dataDirPart.add(app);
		simulatedContextParts.put("<android.app.ContextImpl: java.io.File getDataDir()>", dataDirPart);
		
		OrPart databaseDirPart = new OrPart();
		databaseDirPart.add(new StringConstantPart("/data/system"));
		app = new AppendPart();
		app.add(dataDirPart);
		app.add(unixFileSepConstant);
		app.add(new StringConstantPart("databases"));
		databaseDirPart.add(app);
		simulatedContextParts.put("<android.app.ContextImpl: java.io.File getDatabasesDir()>", databaseDirPart);
		
		AppendPart databasePathPart = new AppendPart();
		databasePathPart.add(databaseDirPart);
		databasePathPart.add(unixFileSepConstant);
		databasePathPart.add(new AnyInfoPart("DATABASENAME"));
		simulatedContextParts.put("<android.app.ContextImpl: java.io.File getDatabasePath(java.lang.String)>", databasePathPart);
		
		AppendPart filesDirPart = new AppendPart();
		filesDirPart.add(dataDirPart);
		filesDirPart.add(unixFileSepConstant);
		filesDirPart.add(new StringConstantPart("files"));
		simulatedContextParts.put("<android.app.ContextImpl: java.io.File getFilesDir()>",filesDirPart);
		
		AppendPart cacheDirPart = new AppendPart();
		cacheDirPart.add(dataDirPart);
		cacheDirPart.add(unixFileSepConstant);
		cacheDirPart.add(new StringConstantPart("cache"));
		simulatedContextParts.put("<android.app.ContextImpl: java.io.File getCacheDir()>",cacheDirPart);
		
		AppendPart nobDirPart = new AppendPart();
		nobDirPart.add(dataDirPart);
		nobDirPart.add(unixFileSepConstant);
		nobDirPart.add(new StringConstantPart("no_backup"));
		simulatedContextParts.put("<android.app.ContextImpl: java.io.File getNoBackupFilesDir()>",nobDirPart);
		
		AppendPart extFilesDirPart = new AppendPart();
		extFilesDirPart.add(new AnyInfoPart("EXTERNALFILESDIR"));
		extFilesDirPart.add(new StringConstantPart("/Android/data/"));
		extFilesDirPart.add(new AnyInfoPart("PACKAGENAME"));
		extFilesDirPart.add(new StringConstantPart("/files/"));
		extFilesDirPart.add(new AnyInfoPart("FILETYPE"));
		simulatedContextParts.put("<android.app.ContextImpl: java.io.File getExternalFilesDir(java.lang.String)>", extFilesDirPart);
		simulatedContextParts.put("<android.app.ContextImpl: java.io.File[] getExternalFilesDir(java.lang.String)>", extFilesDirPart);
	}

}
