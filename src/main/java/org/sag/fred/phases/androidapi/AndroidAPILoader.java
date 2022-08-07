package org.sag.fred.phases.androidapi;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.sag.acminer.sootinit.BasicSootLoader;
import org.sag.common.logging.ILogger;
import org.sag.common.tools.HierarchyHelpers;
import org.sag.common.tools.SortingMethods;
import org.sag.main.AndroidInfo;
import org.sag.soot.SootSort;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

public class AndroidAPILoader {
	
	private ILogger logger;
	private AndroidInfo ai;
	private Path androidAPIJar;
	private Path jimpleJar;
	
	public AndroidAPILoader(AndroidInfo ai, Path androidAPIJar, Path jimpleJar, ILogger logger) {
		this.logger = logger;
		this.ai = ai;
		this.androidAPIJar = androidAPIJar;
		this.jimpleJar = jimpleJar;
	}
	
	public Set<SootMethod> run() {
		BasicSootLoader.v().load(androidAPIJar, false, ai.getJavaVersion(), logger);
		Set<String> apimethods = new HashSet<>();
		for(SootClass sc : Scene.v().getClasses()) {
			if(!sc.isPhantom()) {
				for(SootMethod sm : sc.getMethods()) {
					if(!sm.isPhantom())
						apimethods.add(sm.toString());
				}
			}
		}
		
		logger.info("{}: API Methods from API Jar count - {}", this.getClass().getSimpleName(), apimethods.size());
		
		BasicSootLoader.v().load(jimpleJar, true, ai.getJavaVersion(), logger);
		Set<SootMethod> resolvedMethods = new HashSet<>();
		for(String s : apimethods) {
			SootMethod m = Scene.v().grabMethod(s);
			if(m != null && !m.isPhantom() && (m.isPublic() || m.isProtected())) {
				resolvedMethods.add(m);
			}
		}
		
		logger.info("{}: Resolved API Methods count - {}", this.getClass().getSimpleName(), resolvedMethods.size());
		
		resolvedMethods = addNonStandardAPIMethods(resolvedMethods);
		resolvedMethods = getAllAPIMethods(resolvedMethods);
		resolvedMethods = removeUnwantedBinderMethods(resolvedMethods);
		resolvedMethods = removeHandleMessageMethods(resolvedMethods);
		resolvedMethods = SortingMethods.sortSet(resolvedMethods,SootSort.smComp);
		
		logger.info("{}: Fully Resolved API Methods count - {}", this.getClass().getSimpleName(), resolvedMethods.size());
		
		return resolvedMethods;
	}
	
	private Set<SootMethod> addNonStandardAPIMethods(Set<SootMethod> resolvedMethods) {
		Set<SootMethod> extraMethods = new HashSet<>();
		SootClass sc = Scene.v().getSootClassUnsafe("android.system.Os",false);
		if(sc != null)
			extraMethods.addAll(sc.getMethods());
		sc = Scene.v().getSootClassUnsafe("libcore.io.Os", false);
		if(sc != null)
			extraMethods.addAll(HierarchyHelpers.getAllImplementingMethods(sc));
		sc = Scene.v().getSootClassUnsafe("android.os.FileUtils", false);
		if(sc != null)
			extraMethods.addAll(sc.getMethods());
		sc = Scene.v().getSootClassUnsafe("android.util.AtomicFile", false);
		if(sc != null)
			extraMethods.addAll(sc.getMethods());
		sc = Scene.v().getSootClassUnsafe("com.android.internal.os.AtomicFile", false);
		if(sc != null)
			extraMethods.addAll(sc.getMethods());
		for(SootMethod sm : extraMethods) {
			if(sm.isConcrete() && (sm.isPublic() || sm.isProtected())) {
				resolvedMethods.add(sm);
			}
		}
		return resolvedMethods;
	}
	
	//Remove all binder methods except those for dump and shell commands because 
	//they are not file related and they make it difficult to exclude binder but
	//keep the dump and shell command ones
	private Set<SootMethod> removeUnwantedBinderMethods(Set<SootMethod> resolvedMethods) {
		Set<SootMethod> allBinderMethods = new HashSet<>();
		SootClass bc = Scene.v().getSootClassUnsafe("android.os.Binder", false);
		if(bc != null) {
			allBinderMethods.addAll(HierarchyHelpers.getAllImplementingMethods(bc));
			allBinderMethods.addAll(bc.getMethods());
		}
		bc = Scene.v().getSootClassUnsafe("android.os.IBinder", false);
		if(bc != null) {
			allBinderMethods.addAll(HierarchyHelpers.getAllImplementingMethods(bc));
			allBinderMethods.addAll(bc.getMethods());
		}
		bc = Scene.v().getSootClassUnsafe("android.os.BinderProxy", false);
		if(bc != null) {
			allBinderMethods.addAll(HierarchyHelpers.getAllImplementingMethods(bc));
			allBinderMethods.addAll(bc.getMethods());
		}
		for(Iterator<SootMethod> it = allBinderMethods.iterator(); it.hasNext();) {
			SootMethod sm = it.next();
			String ss = sm.getSubSignature();
			if(ss.equals("void doDump(java.io.FileDescriptor,java.io.PrintWriter,java.lang.String[])")
					|| ss.equals("void dump(java.io.FileDescriptor,java.io.PrintWriter,java.lang.String[])")
					|| ss.equals("void dump(java.io.FileDescriptor,java.lang.String[])")
					|| ss.equals("void dumpAsync(java.io.FileDescriptor,java.lang.String[])")
					|| ss.equals("void shellCommand(java.io.FileDescriptor,java.io.FileDescriptor,java.io.FileDescriptor,java.lang.String[],android.os.ShellCallback,android.os.ResultReceiver)")
					|| ss.equals("void onShellCommand(java.io.FileDescriptor,java.io.FileDescriptor,java.io.FileDescriptor,java.lang.String[],android.os.ShellCallback,android.os.ResultReceiver)")) {
				it.remove();
			}
		}
		resolvedMethods.removeAll(allBinderMethods);
		return resolvedMethods;
	}
	
	private Set<SootMethod> removeHandleMessageMethods(Set<SootMethod> resolvedMethods) {
		Set<SootMethod> toRemove = new HashSet<>();
		SootClass sc = Scene.v().getSootClassUnsafe("android.os.Handler", false);
		if(sc != null) {
			for(SootClass subClass : HierarchyHelpers.getAllSubClasses(sc)) {
				SootMethod handleMessage = subClass.getMethodUnsafe("void handleMessage(android.os.Message)");
				if(handleMessage != null)
					toRemove.add(handleMessage);
			}
		}
		resolvedMethods.removeAll(toRemove);
		return resolvedMethods;
	}
	
	/* Need to make sure for all super classes and interfaces that all overriding
	 * methods in their child classes are analyzed as well since even if these
	 * classes are hidden, objects of their type may be returned by the public
	 * api methods and used externally.
	 */
	private Set<SootMethod> getAllAPIMethods(Set<SootMethod> apiMethods) {
		// Start by grouping the api classes with their known api methods which
		// may or may not have a body
		Map<SootClass,Set<String>> apiClassesToMethods = new HashMap<>();
		for(Iterator<SootMethod> it = apiMethods.iterator(); it.hasNext();) {
			SootMethod sm = it.next();
			Set<String> methods = apiClassesToMethods.get(sm.getDeclaringClass());
			if(methods == null) {
				methods = new HashSet<>();
				apiClassesToMethods.put(sm.getDeclaringClass(), methods);
			}
			methods.add(sm.getSubSignature());
			if(!sm.isConcrete()) {
				// As we now know what methods belong in what class, we can remove
				// those without bodies at this point as they will not be included in our
				// overall api methods list
				it.remove(); 
			}
		}
		
		// Find any overriding methods in child classes of api classes that override a known api method.
		// These can be accessed externally so we include them in out analysis.
		for(SootClass sc : apiClassesToMethods.keySet()) {
			Set<String> curMethods = apiClassesToMethods.get(sc);
			Set<SootMethod> otherMethods = HierarchyHelpers.getAllImplementingMethods(sc);
			for(SootMethod sm : otherMethods) {
				if(sm.isConcrete() && (sm.isPublic() || sm.isProtected()) 
						&& curMethods.contains(sm.getSubSignature())) {
					apiMethods.add(sm);
				}
			}
		}
		return apiMethods;
	}

}
