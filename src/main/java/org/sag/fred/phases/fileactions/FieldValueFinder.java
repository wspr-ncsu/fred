package org.sag.fred.phases.fileactions;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sag.common.concurrent.CountingThreadExecutor;
import org.sag.common.concurrent.IgnorableRuntimeException;
import org.sag.common.logging.ILogger;
import org.sag.common.tools.HierarchyHelpers;
import org.sag.common.tuple.Pair;
import soot.Body;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.Stmt;

public class FieldValueFinder {
	
	private final String cn;
	private final ILogger logger;
	private final Map<SootField,Set<Pair<SootMethod,AssignStmt>>> data;
	
	public FieldValueFinder(ILogger logger) {
		this.cn = getClass().getSimpleName();
		this.logger = logger;
		this.data = new HashMap<>();
	}
	
	public Set<Pair<SootMethod,AssignStmt>> getFieldWrites(SootField field) {
		return data.get(field);
	}
	
	public boolean initData() {
		boolean successOuter = true;
		final CountingThreadExecutor exe = new CountingThreadExecutor();
		final List<Throwable> errs = new ArrayList<>();
		
		logger.info("{}: Finding all field writes for all fields.",cn);
		
		try {
			for(final SootClass sc : Scene.v().getClasses()) {
				if(!sc.isPhantom()) {
					for(final SootMethod sm : sc.getMethods()) {
						if(sm.isConcrete()) {
							final Body b = sm.retrieveActiveBody();
							exe.execute(new Runnable() {
								@Override
								public void run() {
									for(Unit u : b.getUnits()) {
										Stmt s = (Stmt)u;
										if(s.containsFieldRef()) {
											if(((AssignStmt)s).getLeftOp() instanceof FieldRef) {
												SootField field = HierarchyHelpers.resolveField(s.getFieldRef().getFieldRef());
												if(field != null) {
													synchronized(data) {
														Set<Pair<SootMethod,AssignStmt>> assigns = data.get(field);
														if(assigns == null) {
															assigns = new HashSet<>();
															data.put(field, assigns);
														}
														assigns.add(new Pair<>(sm, (AssignStmt)s));
													}
												}
											}
										}
									}
								}
							});
						}
					}
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
					ps.println(cn + ": Failed to successfully find all field writes. The following exceptions occured:");
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
		
		if(successOuter)
			logger.info("{}: Finished finding all field writes for all fields.",cn);
		else
			logger.fatal("{}: Failed to find all field writes for all fields.",cn);
		return successOuter;
	}
	
}
