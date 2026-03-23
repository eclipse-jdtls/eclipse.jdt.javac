package org.eclipse.jdt.internal.javac.javadoc;

import java.util.ArrayList;
import java.util.List;

public class JavacJdtMarkupParser {

	/**
	 * Parses markup containing @tags with attributes. Throws
	 * IllegalArgumentException for malformed tags or attributes.
	 */
	public List<JavacJdtMarkupTag> parse(String markup) {
		List<JavacJdtMarkupTag> tags = new ArrayList<>();
		if (markup == null || markup.isEmpty())
			return tags;

		String[] lines = markup.split("\\r?\\n");
		int lineNumber = 0;

		while (lineNumber < lines.length) {
			String line = lines[lineNumber].trim();
			lineNumber++;

			if (line.isEmpty() || !line.startsWith("@"))
				continue;

			// Extract tag name
			int firstSpace = line.indexOf(' ');
			String tagName;
			String attrString;
			if (firstSpace > 0) {
				tagName = line.substring(1, firstSpace).trim();
				attrString = line.substring(firstSpace + 1).trim();
			} else {
				tagName = line.substring(1).trim();
				attrString = "";
			}

			if (tagName.isEmpty()) {
				throw new IllegalArgumentException("Empty tag name at line " + lineNumber);
			}

			// Parse attributes
			List<JavacJdtMarkupTagAttribute> attributes = parseAttributes(attrString, lines, lineNumber);

			tags.add(new JavacJdtMarkupTag(tagName, attributes));
		}

		return tags;
	}

	/**
	 * Parses a string of attributes into a list. Supports multi-line quoted values.
	 */
	private List<JavacJdtMarkupTagAttribute> parseAttributes(String attrString, String[] lines, int startLineNumber) {
		List<JavacJdtMarkupTagAttribute> attrs = new ArrayList<>();
		int i = 0;
		int n = attrString.length();
		int lineNumber = startLineNumber;

		while (i < n) {
			// Skip whitespace
			while (i < n && Character.isWhitespace(attrString.charAt(i)))
				i++;
			if (i >= n)
				break;

			// Read key
			int eq = attrString.indexOf('=', i);
			if (eq < 0) {
				throw new IllegalArgumentException("Missing '=' for attribute at line " + lineNumber);
			}
			String key = attrString.substring(i, eq).trim();
			if (key.isEmpty()) {
				throw new IllegalArgumentException("Empty attribute name at line " + lineNumber);
			}

			// Read value in quotes
			i = eq + 1;
			if (i >= n || attrString.charAt(i) != '"') {
				throw new IllegalArgumentException("Attribute value must start with '\"' at line " + lineNumber);
			}
			i++; // skip opening quote
			StringBuilder value = new StringBuilder();
			boolean closed = false;

			while (!closed) {
				while (i < n) {
					char c = attrString.charAt(i);
					if (c == '"') {
						closed = true;
						i++; // skip closing quote
						break;
					}
					value.append(c);
					i++;
				}
				// Handle multi-line value
				if (!closed) {
					if (lineNumber >= lines.length) {
						throw new IllegalArgumentException(
								"Unterminated attribute value for '" + key + "' starting at line " + lineNumber);
					}
					value.append("\n");
					attrString = lines[lineNumber].trim();
					n = attrString.length();
					i = 0;
					lineNumber++;
				}
			}

			attrs.add(new JavacJdtMarkupTagAttribute(key, value.toString()));
		}

		return attrs;
	}
}