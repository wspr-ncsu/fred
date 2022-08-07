package org.sag.fred.database.ssfiles;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map.Entry;

import org.sag.common.xstream.XStreamInOut.XStreamInOutInterface;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.converters.enums.EnumToStringConverter;

@XStreamAlias("FileType")
@XStreamConverter(EnumToStringConverter.class)
public enum FileType implements XStreamInOutInterface {
	UNKNOWN, FILE, DIR, BFILE, CFILE, LINK, SOCKET, PIPE;
	private final static EnumMap<FileType, String> strings;
	private final static EnumMap<FileType, String> shortStrings;
	
	static {
		strings = new EnumMap<>(FileType.class);
		strings.put(UNKNOWN, "unknown");
		strings.put(FILE, "regular file");
		strings.put(DIR, "directory");
		strings.put(BFILE, "block device file");
		strings.put(CFILE, "character device file");
		strings.put(LINK, "symbolic link");
		strings.put(SOCKET, "socket file");
		strings.put(PIPE, "named pipe");
		shortStrings = new EnumMap<>(FileType.class);
		shortStrings.put(UNKNOWN, "?");
		shortStrings.put(FILE, "-");
		shortStrings.put(DIR, "d");
		shortStrings.put(BFILE, "b");
		shortStrings.put(CFILE, "c");
		shortStrings.put(LINK, "l");
		shortStrings.put(SOCKET, "s");
		shortStrings.put(PIPE, "p");
	}
	
	@Override
	public String toString() {
		return strings.get(this);
	};
	
	public final String toShortString() {
		return shortStrings.get(this);
	}
	
	public final static FileType toFileType(String c) {
		for(Entry<FileType,String> e : strings.entrySet()) {
			if(e.getValue().equals(c))
				return e.getKey();
		}
		return UNKNOWN;
	}
	
	public final static FileType shortStringToFileType(String c) {
		for(Entry<FileType,String> e : shortStrings.entrySet()) {
			if(e.getValue().equals(c))
				return e.getKey();
		}
		return UNKNOWN;
	}

	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public FileType readXML(String filePath, Path path) throws Exception {
		throw new UnsupportedOperationException();
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
			}
		}

		@Override
		public Set<Class<?>> getAnnotatedClasses() {
			return Collections.singleton(FileType.class);
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
}
