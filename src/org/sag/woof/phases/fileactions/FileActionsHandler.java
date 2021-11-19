package org.sag.woof.phases.fileactions;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.sag.acminer.sootinit.IPASootLoader;
import org.sag.common.io.FileHash;
import org.sag.main.config.PhaseConfig;
import org.sag.main.phase.AbstractPhaseHandler;
import org.sag.main.phase.IPhaseHandler;
import org.sag.woof.IWoofDataAccessor;

public class FileActionsHandler extends AbstractPhaseHandler {
	
	private Path jimpleJar;
	
	public FileActionsHandler(List<IPhaseHandler> depPhases, PhaseConfig pc) {
		super(depPhases, pc);
	}
	
	@Override
	protected void initInner() {
		this.jimpleJar = dependencyFilePaths.get(0);
	}
	
	@Override
	protected List<FileHash> getOldDependencyFileHashes() throws Exception {
		return Collections.emptyList();
	}
	
	@Override
	protected void loadExistingInformation() throws Exception {
		
	}
	
	@Override
	protected boolean isSootInitilized() {
		return IPASootLoader.v().isSootLoaded();
	}

	@Override
	protected boolean initilizeSoot() {
		return IPASootLoader.v().load((IWoofDataAccessor)dataAccessor, jimpleJar, ai.getJavaVersion(), logger);
	}
	
	@Override
	protected boolean doWork() {
		try {
			FileActionsFinder runner = new FileActionsFinder((IWoofDataAccessor)dataAccessor, logger);
			if(!runner.run()) {
				logger.fatal("{}: Failed to find all file actions.",cn);
				return false;
			}
			
			if(!runner.extractFilePaths()) {
				logger.fatal("{}: Failed to extract file paths.",cn);
				return false;
			}
		} catch(Throwable t) {
			logger.fatal("{}: Failed.",t,cn);
			return false;
		}
		return true;
	}

}
