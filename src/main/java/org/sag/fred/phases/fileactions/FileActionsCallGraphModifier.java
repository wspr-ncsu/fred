package org.sag.fred.phases.fileactions;

import java.util.Set;

import org.sag.acminer.phases.callgraph.AbstractCallGraphModifier;
import org.sag.common.logging.ILogger;
import org.sag.fred.IFredDataAccessor;

import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

public class FileActionsCallGraphModifier extends AbstractCallGraphModifier {
	
	public FileActionsCallGraphModifier(IFredDataAccessor dataAccessor, ILogger mainLogger){
		super(dataAccessor, mainLogger);
	}
	
	public boolean run() {
		CallGraph oldCallGraph = null;
		CallGraph newCallGraph = null;
		
		mainLogger.info("{}: Modifying the callgraph.",cn);
		
		if((oldCallGraph = getCurrentCallGraph()) == null)
			return false;
		
		try {
			Set<Edge> edges = getEdgesFromCallGraph(oldCallGraph);
			int totalEdges = edges.size();
			mainLogger.info("{}: The current callgraph has {} edges.",cn,totalEdges);
			//edges = removeEdgesToClinit(edges, false);
			//int toClinitEdges = totalEdges - edges.size();
			//mainLogger.info("{}: Removed {} to clinit edges.",cn,toClinitEdges);
			edges = removeExcludedEdges(edges, true);
			int excludedEdges = totalEdges - edges.size() /*- toClinitEdges*/;
			mainLogger.info("{}: Removed {} excluded edges.",cn,excludedEdges);
			int beforeBinderEdges = edges.size();
			edges = adjustBinderEdges(edges);
			int binderEdges = edges.size() - beforeBinderEdges;
			mainLogger.info("{}: Adjusted {} binder edges.",cn,binderEdges);
			mainLogger.info("{}: The new call graph has {} edges.",cn,edges.size());
			newCallGraph = makeCallGraph(edges);
		} catch(Throwable t) {
			mainLogger.fatal("{}: Unexpected exception when modifying the callgraph.",t,cn);
			return false;
		}
		
		if(!setCallGraph(newCallGraph))
			return false;
		
		mainLogger.info("{}: Successfully modified the callgraph.",cn);
		return true;
	}

}
