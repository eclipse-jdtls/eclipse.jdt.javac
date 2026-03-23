package org.eclipse.jdt.internal.javac.javadoc;

public class JavacJdtMarkupTagAttribute {
	private final String name;
	private final String value;

	public JavacJdtMarkupTagAttribute(String name, String value) {
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Attribute name cannot be null or empty");
		}
		if (value == null) {
			throw new IllegalArgumentException("Attribute value cannot be null");
		}
		this.name = name;
		this.value = value;
	}

	public String name() {
		return name;
	}

	public String value() {
		return value;
	}

	@Override
	public String toString() {
		return name + "=\"" + value + "\"";
	}
}