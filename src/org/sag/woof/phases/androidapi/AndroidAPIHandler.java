package org.sag.woof.phases.androidapi;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.sag.common.io.FileHash;
import org.sag.common.io.FileHashList;
import org.sag.common.io.FileHelpers;
import org.sag.main.config.PhaseConfig;
import org.sag.main.phase.AbstractPhaseHandler;
import org.sag.main.phase.IPhaseHandler;
import org.sag.woof.IWoofDataAccessor;
import org.sag.woof.database.androidapi.IAndroidAPIDatabase;

public class AndroidAPIHandler extends AbstractPhaseHandler {
	
	private Path androidAPIJar;
	private Path jimpleJar;
	
	public AndroidAPIHandler(List<IPhaseHandler> depPhases, PhaseConfig pc) {
		super(depPhases, pc);
	}
	
	@Override
	protected void initInner() {
		this.jimpleJar = dependencyFilePaths.get(0);
		this.androidAPIJar = dependencyFilePaths.get(dependencyFilePaths.size()-1);
	}

	@Override
	protected List<FileHash> getOldDependencyFileHashes() throws Exception {
		return ((IWoofDataAccessor)dataAccessor).getAndroidAPIDB().getFileHashList();
	}

	@Override
	protected void loadExistingInformation() throws Exception {
		((IWoofDataAccessor)dataAccessor).setAndroidAPIDB(IAndroidAPIDatabase.Factory.readXML(null, getOutputFilePath()));
	}

	@Override
	protected boolean isSootInitilized() {
		/* This is called when we need to run doWork. However, before we can load soot with the system 
		 * code like normal, we need to analyze the android api using soot. So there is no point in 
		 * loading the system code here.
		 */
		return true;
	}

	@Override
	protected boolean initilizeSoot() { return true; }

	@Override
	protected boolean doWork() {
		try {
			IAndroidAPIDatabase db = IAndroidAPIDatabase.Factory.getNew(false);
			db.addAll(new AndroidAPILoader(ai, androidAPIJar, jimpleJar, logger).run());
			List<Path> realtivePaths = new ArrayList<>(dependencyFilePaths.size());
			for(Path p : dependencyFilePaths) {
				realtivePaths.add(rootPath.relativize(p));
			}
			FileHashList fhl = FileHelpers.genFileHashList(dependencyFilePaths, realtivePaths);
			db.setFileHashList(fhl);
			((IWoofDataAccessor)dataAccessor).setAndroidAPIDB(db);
			db.writeXML(null, getOutputFilePath());
		} catch(Throwable t) {
			logger.fatal("{}: Failed to generate the AndroidAPIDatabase.",t,cn);
			return false;
		}
		return true;
	}

}
