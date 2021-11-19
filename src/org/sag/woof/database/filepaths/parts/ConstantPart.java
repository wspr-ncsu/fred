package org.sag.woof.database.filepaths.parts;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.sag.common.tools.SortingMethods;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

@XStreamAlias("ConstantPart")
public abstract class ConstantPart<A> implements LeafPart {
	
	@Override
	public boolean equals(Object o) {
		if(o == this)
			return true;
		if(o == null || !Objects.equals(getClass(), o.getClass()))
			return false;
		ConstantPart<?> other = (ConstantPart<?>)o;
		return Objects.equals(getValue(), other.getValue());
	}
	
	@Override
	public int hashCode() {
		return Objects.hashCode(getValue());
	}
	
	@Override
	public String toRegexString() {
		return Pattern.quote(toString());
	}
	
	@Override
	public String toSuperSimpleString() {
		return toString();
	}
	
	@Override
	public String toSimpleString() {
		return toString();
	}
	
	@Override
	public String toString() {
		return Objects.toString(getValue());
	}
	
	@Override
	public Part cloneInner(Map<Part,Part> beforeAfterMap) {
		Part ret = beforeAfterMap.get(this);
		if(ret == null) {
			beforeAfterMap.put(this, this);
			ret = this;
		}
		return ret;
	}
	
	@Override
	public int compareTo(LeafPart o) {
		if(getClass() == o.getClass()) {
			return compareToInner(o);
		} else {
			return getClass().getSimpleName().compareTo(o.getClass().getSimpleName());
		}
	}
	
	protected abstract int compareToInner(LeafPart o);
	public abstract A getValue();
	
	@Override
	public void writeXML(String filePath, Path path) throws Exception {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public ConstantPart<?> readXML(String filePath, Path path) throws Exception {
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
			Set<Class<?>> ret = new HashSet<>();
			ret.add(ConstantPart.class);
			ret.add(UnknownConstantPart.class);
			ret.add(NullConstantPart.class);
			ret.add(StringConstantPart.class);
			ret.add(IntConstantPart.class);
			ret.add(LongConstantPart.class);
			ret.add(FloatConstantPart.class);
			ret.add(DoubleConstantPart.class);
			return ret;
		}
		
		@Override
		public void setXStreamOptions(XStream xstream) {
			defaultOptionsXPathRelRef(xstream);
		}
		
	}
	
	@XStreamAlias("UnknownConstantPart")
	public static class UnknownConstantPart extends ConstantPart<String> implements UnknownPart {
		
		private static final String regexStr = ".*";
		private static final String unknownStr = sepStr + indStr + sepStr;
		
		@XStreamOmitField
		protected volatile String stringCache;
		@XStreamOmitField
		protected volatile String stringCache2;
		@XStreamAlias("Value")
		@XStreamAsAttribute
		private final String value;
		
		public UnknownConstantPart(String value) {
			this.value = value;
		}
		
		protected String getType() {
			String ret = getClass().getSimpleName().toUpperCase().replaceFirst(indStr, "");
			return ret.substring(0,ret.length() - 4);
		}
		
		@Override
		public String toRegexString() {
			return regexStr;
		}
		
		@Override
		public String toSuperSimpleString() {
			return unknownStr;
		}
		
		@Override
		public String toSimpleString() {
			if(stringCache2 == null)
				stringCache2 = sepStr + indStr + "[" + getType() + "]" + sepStr;
			return stringCache2;
		}
		
		@Override
		public String toString() {
			if(stringCache == null)
				stringCache = sepStr + indStr + "[TYPE=" + getType() + ", CONSTANT=" + value + "]" + sepStr;
			return stringCache;
		}
		
		public String getValue() {
			return value;
		}
		
		@Override
		protected int compareToInner(LeafPart o) {
			return SortingMethods.sComp.compare(value, ((UnknownConstantPart)o).value);
		}
		
	}

	@XStreamAlias("NullConstantPart")
	public static class NullConstantPart extends ConstantPart<Object> {
		
		public Object getValue() {
			return null;
		}
		
		@Override
		protected int compareToInner(LeafPart o) {
			return 0;
		}
		
	}

	@XStreamAlias("StringConstantPart")
	public static class StringConstantPart extends ConstantPart<String> {

		@XStreamAlias("Value")
		@XStreamAsAttribute
		private final String value;
		
		public StringConstantPart(String value) {
			this.value = value;
		}
		
		public String getValue() {
			return value;
		}
		
		@Override
		protected int compareToInner(LeafPart o) {
			return SortingMethods.sComp.compare(value, ((StringConstantPart)o).value);
		}
		
	}

	@XStreamAlias("IntConstantPart")
	public static class IntConstantPart extends ConstantPart<Integer> {
		
		@XStreamAlias("Value")
		@XStreamAsAttribute
		private final int value;
		
		public IntConstantPart(int value) {
			this.value = value;
		}
		
		public Integer getValue() {
			return value;
		}
		
		@Override
		protected int compareToInner(LeafPart o) {
			return Integer.compare(value, ((IntConstantPart)o).value);
		}

	}

	@XStreamAlias("LongConstantPart")
	public static class LongConstantPart extends ConstantPart<Long> {
		
		@XStreamAlias("Value")
		@XStreamAsAttribute
		private final long value;
		
		public LongConstantPart(long value) {
			this.value = value;
		}
		
		public Long getValue() {
			return value;
		}
		
		@Override
		protected int compareToInner(LeafPart o) {
			return Long.compare(value, ((LongConstantPart)o).value);
		}

	}
		
	@XStreamAlias("FloatConstantPart")
	public static class FloatConstantPart extends ConstantPart<Float> {
		
		@XStreamAlias("Value")
		@XStreamAsAttribute
		private final float value;
		
		public FloatConstantPart(float value) {
			this.value = value;
		}
		
		public Float getValue() {
			return value;
		}
		
		@Override
		protected int compareToInner(LeafPart o) {
			return Float.compare(value, ((FloatConstantPart)o).value);
		}

	}
		
	@XStreamAlias("DoubleConstantPart")
	public static class DoubleConstantPart extends ConstantPart<Double> {
		
		@XStreamAlias("Value")
		@XStreamAsAttribute
		private final double value;
		
		public DoubleConstantPart(double value) {
			this.value = value;
		}
		
		public Double getValue() {
			return value;
		}
		
		@Override
		protected int compareToInner(LeafPart o) {
			return Double.compare(value, ((DoubleConstantPart)o).value);
		}
		
	}
	
}
