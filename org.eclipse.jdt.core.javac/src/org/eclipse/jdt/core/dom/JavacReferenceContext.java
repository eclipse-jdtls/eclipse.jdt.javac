package org.eclipse.jdt.core.dom;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.ReferenceContext;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilationUnit;
import org.eclipse.jdt.internal.compiler.problem.AbortMethod;
import org.eclipse.jdt.internal.compiler.problem.AbortType;
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities;
import org.eclipse.jdt.internal.javac.JavacCompilationResult;

public class JavacReferenceContext implements ReferenceContext {

	private ICompilationUnit oneSrcUnit;
	private CompilationUnit dom;
	private JavacCompilationResult compilationResult;
	private boolean ignoreFurtherInvestigation = false;

	public JavacReferenceContext(ICompilationUnit oneSrcUnit, CompilationUnit dom) {
		this.oneSrcUnit = oneSrcUnit;
		this.dom = dom;
		this.compilationResult = new JavacCompilationResult(oneSrcUnit);
	}

	@Override
	public void abort(int abortLevel, CategorizedProblem problem) {
		switch (abortLevel) {
		case ProblemSeverities.AbortCompilation:
			throw new AbortCompilation(this.compilationResult, problem);
		case ProblemSeverities.AbortCompilationUnit:
			throw new AbortCompilationUnit(this.compilationResult, problem);
		case ProblemSeverities.AbortType:
			throw new AbortType(this.compilationResult, problem);
		default:
			throw new AbortMethod(this.compilationResult, problem);
		}
	}

	@Override
	public CompilationResult compilationResult() {
		return compilationResult;
	}

	@Override
	public CompilationUnitDeclaration getCompilationUnitDeclaration() {
		// Not implemented
		return null;
	}

	@Override
	public boolean hasErrors() {
		return this.ignoreFurtherInvestigation;
	}

	@Override
	public void tagAsHavingErrors() {
		ignoreFurtherInvestigation = true;
	}

}
