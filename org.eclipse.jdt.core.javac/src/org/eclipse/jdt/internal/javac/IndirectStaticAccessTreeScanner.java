/*******************************************************************************
* Copyright (c) 2026 IBM Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License 2.0
* which accompanies this distribution, and is available at
* https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     IBM Corporation - initial API and implementation
*******************************************************************************/
package org.eclipse.jdt.internal.javac;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.util.Context;

public class IndirectStaticAccessTreeScanner extends TreeScanner<Void, Void> {

	private final IProblemFactory problemFactory;
	private final CompilerOptions compilerOptions;
	private final Types types;

	private final Deque<ClassSymbol> enclosingClasses = new ArrayDeque<>();
	private final List<CategorizedProblem> indirectStaticAccessProblems = new ArrayList<>();

	private JCCompilationUnit unit = null;

	IndirectStaticAccessTreeScanner(Context context, IProblemFactory problemFactory, CompilerOptions compilerOptions) {
		this.problemFactory = problemFactory;
		this.compilerOptions = compilerOptions;
		this.types = Types.instance(context);
	}

	@Override
	public Void visitCompilationUnit(CompilationUnitTree node, Void p) {
		this.unit = (JCCompilationUnit) node;
		return super.visitCompilationUnit(node, p);
	}

	@Override
	public Void visitClass(ClassTree node, Void p) {
		if (node instanceof JCClassDecl classDecl) {
			this.enclosingClasses.push(classDecl.sym);
			try {
				return super.visitClass(node, p);
			} finally {
				this.enclosingClasses.pop();
			}
		}
		return super.visitClass(node, p);
	}

	@Override
	public Void visitMemberSelect(MemberSelectTree node, Void p) {
		if (node instanceof JCFieldAccess fieldAccess) {
			if (!this.enclosingClasses.isEmpty()
					&& fieldAccess.selected.type.tsym instanceof ClassSymbol selectedType
					&& fieldAccess.sym.isStatic()
					&& fieldAccess.sym.owner instanceof ClassSymbol declaringType
					&& fieldAccess.sym.isInheritedIn(selectedType, this.types)
					&& !selectedType.equals(declaringType)
					&& selectedType.isSubClass(declaringType, this.types)) {
				ClassSymbol currentClass = this.enclosingClasses.peek();
				if (!declaringType.isAccessibleIn(declaringType.owner instanceof PackageSymbol ? currentClass.packge() : currentClass, this.types)) {
					return null;
				}
				if (fieldAccess.sym instanceof VarSymbol field) {
					String declaringTypeName = declaringType.getQualifiedName().toString();
					String shortDeclaringTypeName = declaringType.name.toString();
					String fieldName = field.name.toString();
					CategorizedProblem problem = toCategorizedProblem(
							fieldAccess,
							IProblem.IndirectAccessToStaticField,
							new String[] { declaringTypeName, fieldName },
							new String[] { shortDeclaringTypeName, fieldName },
							toSeverity(IProblem.IndirectAccessToStaticField));
					this.indirectStaticAccessProblems.add(problem);
				} else if (fieldAccess.sym instanceof MethodSymbol method) {
					String parameters = getParameterTypes(method);
					String declaringTypeName = declaringType.getQualifiedName().toString();
					String shortDeclaringTypeName = declaringType.name.toString();
					String methodName = method.name.toString();
					CategorizedProblem problem = toCategorizedProblem(
							fieldAccess,
							IProblem.IndirectAccessToStaticMethod,
							new String[] { declaringTypeName, methodName, parameters },
							new String[] { shortDeclaringTypeName, methodName, parameters },
							toSeverity(IProblem.IndirectAccessToStaticMethod));
					this.indirectStaticAccessProblems.add(problem);
				}
			}
		}
		return super.visitMemberSelect(node, p);
	}

	List<CategorizedProblem> getIndirectStaticAccessProblems() {
		return this.indirectStaticAccessProblems;
	}

	private CategorizedProblem toCategorizedProblem(JCFieldAccess fieldAccess, int problemId, String[] problemArguments, String[] messageArguments, int severity) {
		char[] fileName = this.unit.getSourceFile().getName().toCharArray();
		int startPos = fieldAccess.selected.getEndPosition(this.unit.endPositions) + 1;
		int endPos = startPos + fieldAccess.name.length() - 1;
		int line = this.unit.getLineMap().getLineNumber(startPos);
		int column = this.unit.getLineMap().getColumnNumber(startPos);
		return this.problemFactory.createProblem(fileName,
					problemId,
					problemArguments,
					messageArguments,
					severity, startPos, endPos, line, column);
	}

	private String getParameterTypes(MethodSymbol method) {
		StringBuilder result = new StringBuilder();
		for (VarSymbol parameter : method.params()) {
			if (result.length() > 0) {
				result.append(", ");
			}
			result.append(parameter.type.toString());
		}
		return result.toString();
	}

	private int toSeverity(int jdtProblemId) {
		int irritant = ProblemReporter.getIrritant(jdtProblemId);
		if (irritant != 0) {
			int res = this.compilerOptions.getSeverity(irritant);
			res &= ~ProblemSeverities.Optional; // reject optional flag at this stage
			return res;
		}

		return ProblemSeverities.Warning;
	}
}
