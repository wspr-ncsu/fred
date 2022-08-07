package org.sag.fred.phases.fileactions;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.sag.acminer.sootinit.IPASootLoader;
import org.sag.common.io.FileHash;
import org.sag.main.config.PhaseConfig;
import org.sag.main.phase.AbstractPhaseHandler;
import org.sag.main.phase.IPhaseHandler;
import org.sag.fred.IFredDataAccessor;

public class FileActionsCallGraphHandler extends AbstractPhaseHandler {
	
private Path jimpleJar;
	
	public FileActionsCallGraphHandler(List<IPhaseHandler> depPhases, PhaseConfig pc) {
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
	protected void loadExistingInformation() throws Exception {}

	@Override
	protected boolean isSootInitilized() {
		return IPASootLoader.v().isSootLoaded();
	}

	@Override
	protected boolean initilizeSoot() {
		return IPASootLoader.v().load((IFredDataAccessor)dataAccessor, jimpleJar, ai.getJavaVersion(), logger);
	}

	@Override
	protected boolean doWork() {
		try{
			FileActionsCallGraphModifier mod = new FileActionsCallGraphModifier((IFredDataAccessor)dataAccessor,logger);
			if(!mod.run()){
				logger.fatal("{}: The CallGraphModifier encountered errors during executation.",cn);
				return false;
			}
		}catch(Throwable t){
			logger.fatal("{}: Unexpected exception during the run of the CallGraphModifier.",t,cn);
			return false;
		}
		return true;
	}

	//Hardcode in forced run so that if the phase is enabled it is always run without looking at anything else
	@Override
	public boolean isForcedRun(){
		return true;
	}

}
