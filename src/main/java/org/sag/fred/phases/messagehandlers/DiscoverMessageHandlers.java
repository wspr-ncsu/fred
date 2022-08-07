package org.sag.fred.phases.messagehandlers;

import java.util.HashSet;
import java.util.Set;

import org.sag.common.logging.ILogger;
import org.sag.common.tools.HierarchyHelpers;
import org.sag.fred.IFredDataAccessor;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

public class DiscoverMessageHandlers {
	
	private final String cn;
	private final ILogger logger;
	private final IFredDataAccessor dataAccessor;
	private volatile Set<SootMethod> messageHandlers;
	private final SootClass handlerClass;
	
	public DiscoverMessageHandlers(IFredDataAccessor dataAccessor, ILogger logger) {
		this.logger = logger;
		this.dataAccessor = dataAccessor;
		this.cn = getClass().getSimpleName();
		this.messageHandlers = new HashSet<>();
		this.handlerClass = Scene.v().getSootClassUnsafe("android.os.Handler", false);
	}
	
	public boolean discoverMessageHandlerMethods() {
		logger.info("{}: Finding message handler handleMessage methods.",cn);
		try {
			if(handlerClass != null) {
				for(SootClass subClass : HierarchyHelpers.getAllSubClasses(handlerClass)) {
					SootMethod handleMessage = subClass.getMethodUnsafe("void handleMessage(android.os.Message)");
					if(handleMessage != null && handleMessage.isConcrete())
						messageHandlers.add(handleMessage);
				}
				dataAccessor.getMessageHandlerDB().addAll(messageHandlers);
			} else {
				logger.fatal("{}: There is no android.os.Handler class!?!",cn);
				return false;
			}
		} catch(Throwable t) {
			logger.fatal("{}: Failed to find message handler handleMessage methods.",t,cn);
			return false;
		}
		logger.info("{}: Successfully found message handler handleMessage methods.",cn);
		return true;
	}

}
