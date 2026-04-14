package org.eclipse.jdt.internal.javac.doclint;

import com.sun.source.util.JavacTask;
import com.sun.tools.doclint.DocLint;
public class JavacDocLintImpl extends DocLint {
	private shaded.jdk.javadoc.internal.doclint.DocLint delegate = new shaded.jdk.javadoc.internal.doclint.DocLint();
	@Override
	public String getName() {
		return delegate.getName();
	}

	@Override
	public void init(JavacTask arg0, String... arg1) {
		delegate.init(arg0, arg1);
	}

	@Override
	public boolean isValidOption(String arg0) {
		return delegate.isValidOption(arg0);
	}
}
