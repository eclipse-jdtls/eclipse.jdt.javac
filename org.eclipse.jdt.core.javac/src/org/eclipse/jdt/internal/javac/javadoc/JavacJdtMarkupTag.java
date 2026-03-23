package org.eclipse.jdt.internal.javac.javadoc;

import java.util.Collections;
import java.util.List;

public class JavacJdtMarkupTag {
	private final String name;
	private final List<JavacJdtMarkupTagAttribute> attributes;

	public JavacJdtMarkupTag(String name, List<JavacJdtMarkupTagAttribute> attributes) {
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Tag name cannot be null or empty");
		}
		this.name = name;
		this.attributes = attributes != null ? attributes : Collections.emptyList();
	}

	public String name() {
		return name;
	}

	public List<JavacJdtMarkupTagAttribute> attributes() {
		return attributes;
	}

	@Override
	public String toString() {
		return "@" + name + " " + attributes;
	}
}