package org.sag.woof.database.filemethods;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sag.common.xstream.NamedCollectionConverterWithSize;
import org.sag.common.xstream.XStreamInOut;
import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;
import org.sag.main.sootinit.SootInstanceWrapper;
import org.sag.soot.xstream.SootMethodContainer;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import soot.Scene;
import soot.SootMethod;

@XStreamAlias("FileMethod")
public class FileMethod implements XStreamInOutInterface, Comparable<FileMethod> {
	
	public static final String openStr = "open";
	public static final String accessStr = "access";
	public static final String removeStr = "remove";
	private static final String possibleActionsStr = openStr + ":" + accessStr + ":" + removeStr;
	public static final String javaAPIStr = "JavaAPI";
	public static final String androidAPIStr = "AndroidAPI";
	public static final String androidSystemStr = "AndroidSystem";
	private static final Pattern pat = Pattern.compile("^((?:open\\:?|access\\:?|remove\\:?)+)\\s+(JavaAPI|AndroidAPI|AndroidSystem)\\s+(<.+?)$");
	
	@XStreamAlias("Actions")
	@XStreamAsAttribute
	private String actions;
	@XStreamAlias("APIType")
	@XStreamAsAttribute
	private String apiType;
	@XStreamAlias("SootMethodContainer")
	private SootMethodContainer sootMethodContainer;
	@XStreamAlias("Sinks")
	@XStreamConverter(value=NamedCollectionConverterWithSize.class,strings={"FileMethod"},types={FileMethod.class})
	private volatile LinkedHashSet<FileMethod> sinks;
	
	@XStreamOmitField
	private volatile SootMethod sootMethod;
	
	//for reading in from xml only
	private FileMethod() {}
	
	private FileMethod(SootMethod sm, String actions, String apiType) {
		Objects.requireNonNull(actions);
		Objects.requireNonNull(apiType);
		Objects.requireNonNull(sm);
		this.sootMethodContainer = SootMethodContainer.makeSootMethodContainer(sm);
		this.sootMethod = sm;
		this.actions = actions;
		this.apiType = apiType;
	}
	
	@Override
	public boolean equals(Object o){
		if(this == o)
			return true;
		if(o == null || !(o instanceof FileMethod))
			return false;
		FileMethod other = (FileMethod)o;
		return Objects.equals(sootMethodContainer, other.sootMethodContainer);
	}
	
	@Override
	public int hashCode(){
		int hash = 17;
		hash = 31 * hash + Objects.hashCode(sootMethodContainer);
		return hash;
	}
	
	private final String padStr(String toFormat, int minSize) {
		return String.format("%-"+minSize+"s", toFormat);
	}
	
	@Override
	public String toString() {
		return toString("");
	}
	
	public String toString(String spacer) {
		StringBuilder sb = new StringBuilder();
		sb.append(toStringNoSinks(spacer));
		if(sinks != null && !sinks.isEmpty()) {
			for(FileMethod m : sinks) {
				sb.append("\n").append(spacer).append("  ").append(m.getSignature());
			}
		}
		return sb.toString();
	}
	
	public String toStringNoSinks() {
		return toStringNoSinks("");
	}
	
	public String toStringNoSinks(String spacer) {
		StringBuilder sb = new StringBuilder();
		sb.append(spacer).append(padStr(actions, possibleActionsStr.length()));
		sb.append(" ").append(padStr(apiType, androidSystemStr.length()));
		sb.append(" ").append(sootMethodContainer.getSignature());
		return sb.toString();
	}
	
	@Override
	public int compareTo(FileMethod o) {
		int ret = sootMethodContainer.compareTo(o.sootMethodContainer);
		if(ret == 0) {
			ret = actions.compareTo(o.actions);
			if(ret == 0)
				ret = apiType.compareTo(o.apiType);
		}
		return ret;
	}
	
	public SootMethodContainer getSootMethodContainer() {
		return sootMethodContainer;
	}
	
	public SootMethod getSootMethod() {
		if(sootMethod == null)
			sootMethod = sootMethodContainer.toSootMethod();
		return sootMethod;
	}
	
	protected void clearSootData() {
		sootMethod = null;
	}
	
	public boolean isNative() {
		return sootMethodContainer.isNative();
	}
	
	public boolean opens() {
		return actions.contains(openStr);
	}
	
	public boolean removes() {
		return actions.contains(removeStr);
	}
	
	public boolean accesses() {
		return actions.contains(accessStr);
	}
	
	public boolean isJavaAPI() {
		return apiType.equals(javaAPIStr);
	}
	
	public boolean isAndroidAPI() {
		return apiType.equals(androidAPIStr);
	}
	
	public boolean isAndroidSystem() {
		return apiType.equals(androidSystemStr);
	}
	
	public String getSignature() {
		return sootMethodContainer.getSignature();
	}
	
	public Set<FileMethod> getSinks() {
		return sinks;
	}
	
	public void setSinks(LinkedHashSet<FileMethod> sinks) {
		if(!isNative())
			this.sinks = sinks;
	}
	
	public static final FileMethod getNewFileMethod(SootMethod sm, boolean[] actions, String apiType) {
		Objects.requireNonNull(sm);
		Objects.requireNonNull(actions);
		Objects.requireNonNull(apiType);
		if(!SootInstanceWrapper.v().isSootInitSet())
			throw new RuntimeException("Error: Some instance of Soot must be initilized first.");
		if(actions.length != 3 || (!actions[0] && !actions[1] && !actions[2]))
			throw new RuntimeException("Error: A value has not been provided for each action.");
		if(!apiType.equals(javaAPIStr) && !apiType.equals(androidAPIStr) && !apiType.equals(androidSystemStr))
			throw new RuntimeException("Error: Unrecongized value for the api type.");
		
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		if(actions[0]) {
			sb.append(openStr);
			first = false;
		}
		if(actions[1]) {
			if(!first)
				sb.append(":");
			else
				first = false;
			sb.append(accessStr);
		}
		if(actions[2]) {
			if(!first)
				sb.append(":");
			else
				first = false;
			sb.append(removeStr);
		}
		return new FileMethod(sm, sb.toString(), apiType);
	}
	
	public static final FileMethod parseFileMethodStr(String entry) {
		Matcher m = pat.matcher(entry);
		if(m.matches()) {
			String actions = m.group(1);
			String apiType = m.group(2);
			String signature = m.group(3);
			
			if(!SootInstanceWrapper.v().isSootInitSet())
				throw new RuntimeException("Error: Some instance of Soot must be initilized first.");
			return new FileMethod(Scene.v().getMethod(signature), actions, apiType);
		}
		throw new RuntimeException("Error: Cannot parse FileMethod entry from '" + entry + "'.");
	}
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		XStreamInOut.writeXML(this, filePath, path);
	}
	
	public FileMethod readXML(String filePath, Path path) throws Exception {
		return XStreamInOut.readXML(this,filePath, path);
	}
	
	public static FileMethod readXMLStatic(String filePath, Path path) throws Exception {
		return new FileMethod().readXML(filePath, path);
	}
	
	@XStreamOmitField
	private static AbstractXStreamSetup xstreamSetup = null;

	public static AbstractXStreamSetup getXStreamSetupStatic(){
		if(xstreamSetup == null)
			xstreamSetup = new XStreamSetup();
		return xstreamSetup;
	}
	
	@Override
	public AbstractXStreamSetup getXStreamSetup() {
		return getXStreamSetupStatic();
	}
	
	public static class XStreamSetup extends AbstractXStreamSetup {
		
		@Override
		public void getOutputGraph(LinkedHashSet<AbstractXStreamSetup> in) {
			if(!in.contains(this)) {
				in.add(this);
				SootMethodContainer.getXStreamSetupStatic().getOutputGraph(in);
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(FileMethod.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}

}
