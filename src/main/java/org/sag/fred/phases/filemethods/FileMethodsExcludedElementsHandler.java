package org.sag.fred.phases.filemethods;

import java.util.List;
import java.util.Set;

import org.sag.acminer.database.excludedelements.IExcludedElementsDatabase;
import org.sag.acminer.phases.excludedelements.ExcludedElementsHandler;
import org.sag.main.config.PhaseConfig;
import org.sag.main.phase.IPhaseHandler;

public class FileMethodsExcludedElementsHandler extends ExcludedElementsHandler {

	public FileMethodsExcludedElementsHandler(List<IPhaseHandler> depPhases, PhaseConfig pc) {
		super(depPhases, pc);
	}
	
	@Override
	protected IExcludedElementsDatabase genExcludedElementsDatabase(Set<String> otherClasses, Set<String> otherMethods) throws Exception {
		return IExcludedElementsDatabase.Factory.readTXT(excludedElementsTextFile);
	}

}
