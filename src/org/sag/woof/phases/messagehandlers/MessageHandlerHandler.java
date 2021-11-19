package org.sag.woof.phases.messagehandlers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.sag.acminer.sootinit.BasicSootLoader;
import org.sag.common.io.FileHash;
import org.sag.common.io.FileHashList;
import org.sag.common.io.FileHelpers;
import org.sag.main.config.PhaseConfig;
import org.sag.main.phase.AbstractPhaseHandler;
import org.sag.main.phase.IPhaseHandler;
import org.sag.woof.IWoofDataAccessor;
import org.sag.woof.database.messagehandlers.IMessageHandlerDatabase;

public class MessageHandlerHandler extends AbstractPhaseHandler {
	
	private Path jimpleJar;
	
	public MessageHandlerHandler(List<IPhaseHandler> depPhases, PhaseConfig pc) {
		super(depPhases, pc);
	}
	
	@Override
	protected void initInner() {
		this.jimpleJar = dependencyFilePaths.get(0);
	}
	
	@Override
	protected List<FileHash> getOldDependencyFileHashes() throws Exception {
		return ((IWoofDataAccessor)dataAccessor).getMessageHandlerDB().getFileHashList();
	}

	@Override
	protected void loadExistingInformation() throws Exception {
		((IWoofDataAccessor)dataAccessor).setMessageHandlerDB(IMessageHandlerDatabase.Factory.readXML(null, getOutputFilePath()));
	}

	@Override
	protected boolean isSootInitilized() {
		return BasicSootLoader.v().isSootLoaded();
	}

	@Override
	protected boolean initilizeSoot() {
		return BasicSootLoader.v().load(jimpleJar,true,ai.getJavaVersion(),logger);
	}
	
	@Override
	protected boolean doWork() {
		try {
			((IWoofDataAccessor)dataAccessor).setMessageHandlerDB(IMessageHandlerDatabase.Factory.getNew(false));
			DiscoverMessageHandlers finder = new DiscoverMessageHandlers(((IWoofDataAccessor)dataAccessor), logger);
			if(!finder.discoverMessageHandlerMethods()) {
				logger.fatal("{}: Failed to find all message handlers.",cn);
				return false;
			}
			
			List<Path> fullPaths = dependencyFilePaths;
			List<Path> realtivePaths = new ArrayList<>(fullPaths.size());
			for(Path p : fullPaths) {
				realtivePaths.add(rootPath.relativize(p));
			}
			FileHashList fhl = FileHelpers.genFileHashList(fullPaths, realtivePaths);
			((IWoofDataAccessor)dataAccessor).getMessageHandlerDB().setFileHashList(fhl);
			((IWoofDataAccessor)dataAccessor).getMessageHandlerDB().writeXML(null, getOutputFilePath());
		} catch(Throwable t) {
			logger.fatal("{}: Unexpected exception when find all message handlers. Failed to output '{}'",t,cn,getOutputFilePath());
			return false;
		}
		return true;
	}

}
