package org.sag.woof.phases.fileactions;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.sag.acminer.database.excludedelements.IExcludedElementsDatabase;
import org.sag.acminer.phases.excludedelements.ExcludedElementsHandler;
import org.sag.main.config.PhaseConfig;
import org.sag.main.phase.IPhaseHandler;
import org.sag.woof.IWoofDataAccessor;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

public class FileActionsExcludedElementsHandler extends ExcludedElementsHandler {

	public FileActionsExcludedElementsHandler(List<IPhaseHandler> depPhases, PhaseConfig pc) {
		super(depPhases, pc);
	}
	
	@Override 
	protected IExcludedElementsDatabase genExcludedElementsDatabase(Set<String> otherClasses, Set<String> otherMethods) throws Exception {
		//Exclude all the API methods as they have already be processed by us in FIleMethodsHandler
		Set<String> epMethods = ((IWoofDataAccessor)dataAccessor).getAllEntryPointMethods();
		Set<SootMethod> apiMethods = ((IWoofDataAccessor)dataAccessor).getAndroidAPIDB().getMethods();
		//Remove methods that are in the api but that we don't want to exclude
		for(Iterator<SootMethod> it = apiMethods.iterator(); it.hasNext();) {
			SootMethod apiMethod = it.next();
			if(apiMethod.getDeclaringClass().getName().equals("android.os.Environment"))
				it.remove();
		}
		
		for(SootClass sc : Scene.v().getClasses()) {
			boolean allExcluded = true;
			for(SootMethod sm : sc.getMethods()) {
				if(apiMethods.contains(sm) && !epMethods.contains(sm.getSignature())) {
					otherMethods.add(sm.getSignature());
				} else {
					allExcluded = false;
				}
			}
			if(allExcluded) {
				otherClasses.add(sc.getName());
			}
		}
		
		//Everything is reverts to what ACMiner used to generate excluded information
		return super.genExcludedElementsDatabase(otherClasses, otherMethods);
	}

}
