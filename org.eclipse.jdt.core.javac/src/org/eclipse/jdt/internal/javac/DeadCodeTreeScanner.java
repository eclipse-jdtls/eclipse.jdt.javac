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

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.TypeElement;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.javac.JavacUtils;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCExpression;

class DeadCodeTreeScanner extends TopLevelTreeScanner<Void, Void> {

	private final IProblemFactory problemFactory;
	private final CompilerOptions compilerOptions;

	private final List<CategorizedProblem> deadCodeProblems = new ArrayList<>();

	private JCCompilationUnit unit = null;

	DeadCodeTreeScanner(IProblemFactory problemFactory, CompilerOptions compilerOptions, TypeElement currentTopLevelType) {
		super(currentTopLevelType);
		this.problemFactory = problemFactory;
		this.compilerOptions = compilerOptions;
	}

	@Override
	public Void visitCompilationUnit(CompilationUnitTree node, Void p) {
		this.unit = (JCCompilationUnit) node;
		return super.visitCompilationUnit(node, p);
	}

	@Override
	public Void visitConditionalExpression(ConditionalExpressionTree node, Void p) {
		if (node instanceof JCConditional conditional) {
			JCExpression ternaryDeadBranch = getTernaryDeadCode(conditional);
			if (ternaryDeadBranch != null) {
				this.deadCodeProblems.add(toCategorizedProblem(ternaryDeadBranch, IProblem.DeadCode));
			}
		}
		return super.visitConditionalExpression(node, p);
	}

	private JCExpression getTernaryDeadCode(JCConditional conditional) {
		boolean conditionTrue = conditional.cond.type.isTrue();
		boolean conditionFalse = conditional.cond.type.isFalse();
		if (conditionTrue || conditionFalse) {
			return conditionTrue ? conditional.falsepart : conditional.truepart;
		}
		return null;
	}

	public List<CategorizedProblem> getDeadCodeProblems() {
		return this.deadCodeProblems;
	}

	private CategorizedProblem toCategorizedProblem(JCExpression expression, int problemId) {
		char[] fileName = this.unit.getSourceFile().getName().toCharArray();
		int startPos = expression.getStartPosition();
		int endPos = expression.getEndPosition(this.unit.endPositions) - 1;
		int line = this.unit.getLineMap().getLineNumber(startPos);
		int column = this.unit.getLineMap().getColumnNumber(startPos);
		return this.problemFactory.createProblem(fileName,
				problemId,
				new String[0],
				new String[0],
				JavacUtils.toSeverity(this.compilerOptions, problemId),
				startPos, endPos,
				line, column);
	}
}
