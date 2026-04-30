package org.eclipse.jdt.internal.javac.doclint;

import shaded.com.sun.source.util.JavacTask;
import shaded.com.sun.tools.doclint.DocLint;
public class JavacDocLintImpl extends DocLint {
	private shaded.jdk.javadoc.internal.doclint.DocLint nameOptionDelegate = new shaded.jdk.javadoc.internal.doclint.DocLint();
	@Override
	public String getName() {
		return nameOptionDelegate.getName();
	}

	@Override
	public boolean isValidOption(String arg0) {
		return nameOptionDelegate.isValidOption(arg0);
	}

	@Override
	public void init(JavacTask arg0, String... arg1) {
		// new instance every time
		new shaded.jdk.javadoc.internal.doclint.DocLint().init(arg0, arg1);
	}

}
