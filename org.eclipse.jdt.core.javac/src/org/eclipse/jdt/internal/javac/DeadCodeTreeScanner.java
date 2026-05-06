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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

import shaded.com.sun.source.tree.BinaryTree;
import shaded.com.sun.source.tree.BlockTree;
import shaded.com.sun.source.tree.CompilationUnitTree;
import shaded.com.sun.source.tree.ConditionalExpressionTree;
import shaded.com.sun.source.tree.IfTree;
import shaded.com.sun.tools.javac.code.Symbol;
import shaded.com.sun.tools.javac.code.Symbol.VarSymbol;
import shaded.com.sun.tools.javac.tree.JCTree;
import shaded.com.sun.tools.javac.tree.JCTree.JCBinary;
import shaded.com.sun.tools.javac.tree.JCTree.JCBlock;
import shaded.com.sun.tools.javac.tree.JCTree.JCBreak;
import shaded.com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import shaded.com.sun.tools.javac.tree.JCTree.JCConditional;
import shaded.com.sun.tools.javac.tree.JCTree.JCContinue;
import shaded.com.sun.tools.javac.tree.JCTree.JCExpression;
import shaded.com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import shaded.com.sun.tools.javac.tree.JCTree.JCIdent;
import shaded.com.sun.tools.javac.tree.JCTree.JCIf;
import shaded.com.sun.tools.javac.tree.JCTree.JCLiteral;
import shaded.com.sun.tools.javac.tree.JCTree.JCParens;
import shaded.com.sun.tools.javac.tree.JCTree.JCReturn;
import shaded.com.sun.tools.javac.tree.JCTree.JCStatement;
import shaded.com.sun.tools.javac.tree.JCTree.JCSynchronized;
import shaded.com.sun.tools.javac.tree.JCTree.JCThrow;
import shaded.com.sun.tools.javac.tree.JCTree.JCUnary;
import shaded.com.sun.tools.javac.tree.JCTree.Tag;
import shaded.javax.lang.model.element.ElementKind;
import shaded.javax.lang.model.element.TypeElement;

class DeadCodeTreeScanner extends TopLevelTreeScanner<Void, Void> {

	private final IProblemFactory problemFactory;
	private final CompilerOptions compilerOptions;

	private final List<CategorizedProblem> deadCodeProblems = new ArrayList<>();
	private final ArrayDeque<Map<Symbol, Boolean>> nullAssumptionsStack = new ArrayDeque<>();

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
	public Void visitBinary(BinaryTree node, Void p) {
		if (node instanceof JCBinary binary) {
			JCExpression left = binary.getLeftOperand();
			JCExpression right = binary.getRightOperand();
			Map<Symbol, Boolean> nullAssumptions = this.nullAssumptionsStack.isEmpty() ? Map.of() : this.nullAssumptionsStack.peek();
			switch (binary.getTag()) {
				case AND -> {
					int problemCount = this.deadCodeProblems.size();
					scanWithNullAssumptions(left, nullAssumptions);
					boolean leftReportedDeadCode = this.deadCodeProblems.size() != problemCount;
					if (isDefinitelyFalse(left, nullAssumptions)) {
						if (!leftReportedDeadCode) {
							this.deadCodeProblems.add(toCategorizedProblem(right, IProblem.DeadCode));
						}
						return null;
					}
					scanWithNullAssumptions(right, nullAssumptionsWhenTrue(left, nullAssumptions));
					return null;
				}
				case OR -> {
					int problemCount = this.deadCodeProblems.size();
					scanWithNullAssumptions(left, nullAssumptions);
					boolean leftReportedDeadCode = this.deadCodeProblems.size() != problemCount;
					if (isDefinitelyTrue(left, nullAssumptions)) {
						if (!leftReportedDeadCode) {
							this.deadCodeProblems.add(toCategorizedProblem(right, IProblem.DeadCode));
						}
						return null;
					}
					scanWithNullAssumptions(right, nullAssumptionsWhenFalse(left, nullAssumptions));
					return null;
				}
				default -> {
					scanWithNullAssumptions(left, nullAssumptions);
					scanWithNullAssumptions(right, nullAssumptions);
					return null;
				}
			}
		}
		return super.visitBinary(node, p);
	}

	@Override
	public Void visitBlock(BlockTree node, Void p) {
		if (node instanceof JCBlock block) {
			Map<Symbol, Boolean> nullAssumptions = this.nullAssumptionsStack.isEmpty() ? Map.of() : this.nullAssumptionsStack.peek();
			boolean nextStatementIsDeadCode = false;
			for (JCStatement statement : block.stats) {
				if (nextStatementIsDeadCode) {
					this.deadCodeProblems.add(toCategorizedProblem(statement, IProblem.DeadCode));
					return null;
				}
				scanWithNullAssumptions(statement, nullAssumptions);
				nextStatementIsDeadCode = causesDeadCodeAfter(statement, nullAssumptions);
			}
			return null;
		}
		return super.visitBlock(node, p);
	}

	@Override
	public Void visitConditionalExpression(ConditionalExpressionTree node, Void p) {
		if (node instanceof JCConditional conditional) {
			Map<Symbol, Boolean> nullAssumptions = this.nullAssumptionsStack.isEmpty() ? Map.of() : this.nullAssumptionsStack.peek();
			scanWithNullAssumptions(conditional.cond, nullAssumptions);
			JCExpression deadBranch = getDeadBranch(conditional, nullAssumptions);
			if (deadBranch != null) {
				this.deadCodeProblems.add(toCategorizedProblem(deadBranch, IProblem.DeadCode));
			}
			if (!isDefinitelyFalse(conditional.cond, nullAssumptions)) {
				scanWithNullAssumptions(conditional.truepart, nullAssumptionsWhenTrue(conditional.cond, nullAssumptions));
			}
			if (!isDefinitelyTrue(conditional.cond, nullAssumptions)) {
				scanWithNullAssumptions(conditional.falsepart, nullAssumptionsWhenFalse(conditional.cond, nullAssumptions));
			}
			return null;
		}
		return super.visitConditionalExpression(node, p);
	}

	@Override
	public Void visitIf(IfTree node, Void p) {
		if (node instanceof JCIf ifStatement) {
			Map<Symbol, Boolean> nullAssumptions = this.nullAssumptionsStack.isEmpty() ? Map.of() : this.nullAssumptionsStack.peek();
			scanWithNullAssumptions(ifStatement.cond, nullAssumptions);
			JCStatement deadBranch = getDeadBranch(ifStatement, nullAssumptions);
			if (deadBranch != null) {
				this.deadCodeProblems.add(toCategorizedProblem(deadBranch, IProblem.DeadCode));
			}
			if (!isDefinitelyFalse(ifStatement.cond, nullAssumptions)) {
				scanWithNullAssumptions(ifStatement.thenpart, nullAssumptionsWhenTrue(ifStatement.cond, nullAssumptions));
			}
			if (!isDefinitelyTrue(ifStatement.cond, nullAssumptions)) {
				scanWithNullAssumptions(ifStatement.elsepart, nullAssumptionsWhenFalse(ifStatement.cond, nullAssumptions));
			}
			return null;
		}
		return super.visitIf(node, p);
	}

	private boolean completesNormally(JCStatement statement, Map<Symbol, Boolean> nullAssumptions) {
		if (statement instanceof JCReturn
				|| statement instanceof JCThrow
				|| statement instanceof JCBreak
				|| statement instanceof JCContinue) {
			return false;
		}
		if (statement instanceof JCBlock block) {
			for (JCStatement blockStat : block.stats) {
				if (!completesNormally(blockStat, nullAssumptions) || causesDeadCodeAfter(blockStat, nullAssumptions)) {
					return false;
				}
			}
			return true;
		}
		if (statement instanceof JCSynchronized synchronizedStatement) {
			return completesNormally(synchronizedStatement.body, nullAssumptions);
		}
		if (statement instanceof JCIf ifStatement) {
			if (isDefinitelyTrue(ifStatement.cond, nullAssumptions)) {
				return completesNormally(ifStatement.thenpart, nullAssumptionsWhenTrue(ifStatement.cond, nullAssumptions));
			}
			if (isDefinitelyFalse(ifStatement.cond, nullAssumptions)) {
				return ifStatement.elsepart == null || completesNormally(ifStatement.elsepart, nullAssumptionsWhenFalse(ifStatement.cond, nullAssumptions));
			}
			return ifStatement.elsepart == null
					|| completesNormally(ifStatement.thenpart, nullAssumptionsWhenTrue(ifStatement.cond, nullAssumptions))
					|| completesNormally(ifStatement.elsepart, nullAssumptionsWhenFalse(ifStatement.cond, nullAssumptions));
		}
		return true;
	}

	private boolean causesDeadCodeAfter(JCStatement statement, Map<Symbol, Boolean> nullAssumptions) {
		if (statement instanceof JCIf ifStatement) {
			if (isDefinitelyTrue(ifStatement.cond, nullAssumptions)) {
				return !completesNormally(ifStatement.thenpart, nullAssumptionsWhenTrue(ifStatement.cond, nullAssumptions));
			}
			if (isDefinitelyFalse(ifStatement.cond, nullAssumptions)) {
				return ifStatement.elsepart != null
						&& !completesNormally(ifStatement.elsepart, nullAssumptionsWhenFalse(ifStatement.cond, nullAssumptions));
			}
			return ifStatement.elsepart != null
					&& !completesNormally(ifStatement.thenpart, nullAssumptionsWhenTrue(ifStatement.cond, nullAssumptions))
					&& !completesNormally(ifStatement.elsepart, nullAssumptionsWhenFalse(ifStatement.cond, nullAssumptions));
		}
		return false;
	}

	private JCStatement getDeadBranch(JCIf ifStatement, Map<Symbol, Boolean> nullAssumptions) {
		if (isDefinitelyTrue(ifStatement.cond, nullAssumptions)) {
			return ifStatement.elsepart;
		}
		if (isDefinitelyFalse(ifStatement.cond, nullAssumptions)) {
			return ifStatement.thenpart;
		}
		return null;
	}

	private JCExpression getDeadBranch(JCConditional conditional, Map<Symbol, Boolean> nullAssumptions) {
		if (isDefinitelyTrue(conditional.cond, nullAssumptions)) {
			return conditional.falsepart;
		}
		if (isDefinitelyFalse(conditional.cond, nullAssumptions)) {
			return conditional.truepart;
		}
		return null;
	}

	private Void scanWithNullAssumptions(JCTree tree, Map<Symbol, Boolean> nullAssumptions) {
		this.nullAssumptionsStack.push(nullAssumptions);
		try {
			return scan(tree, null);
		} finally {
			this.nullAssumptionsStack.pop();
		}
	}

	private boolean isDefinitelyTrue(JCExpression expression, Map<Symbol, Boolean> nullAssumptions) {
		expression = unwrapParens(expression);
		if (expression.type != null) {
			if (expression.type.isTrue()) {
				return true;
			}
			if (expression.type.isFalse()) {
				return false;
			}
		}
		if (expression instanceof JCUnary unary && unary.getTag() == Tag.NOT) {
			return isDefinitelyFalse(unary.getExpression(), nullAssumptions);
		}
		if (expression instanceof JCBinary binary) {
			JCExpression left = binary.getLeftOperand();
			JCExpression right = binary.getRightOperand();
			return switch (binary.getTag()) {
				case AND, BITAND -> isDefinitelyTrue(left, nullAssumptions) && isDefinitelyTrue(right, nullAssumptions);
				case OR, BITOR -> isDefinitelyTrue(left, nullAssumptions) || isDefinitelyTrue(right, nullAssumptions);
				case EQ -> isDefinitelyEqual(left, right, nullAssumptions);
				case NE -> isDefinitelyNotEqual(left, right, nullAssumptions);
				default -> false;
			};
		}
		return false;
	}

	private boolean isDefinitelyFalse(JCExpression expression, Map<Symbol, Boolean> nullAssumptions) {
		expression = unwrapParens(expression);
		if (expression.type != null) {
			if (expression.type.isFalse()) {
				return true;
			}
			if (expression.type.isTrue()) {
				return false;
			}
		}
		if (expression instanceof JCUnary unary && unary.getTag() == Tag.NOT) {
			return isDefinitelyTrue(unary.getExpression(), nullAssumptions);
		}
		if (expression instanceof JCBinary binary) {
			JCExpression left = binary.getLeftOperand();
			JCExpression right = binary.getRightOperand();
			return switch (binary.getTag()) {
				case AND, BITAND -> isDefinitelyFalse(left, nullAssumptions) || isDefinitelyFalse(right, nullAssumptions);
				case OR, BITOR -> isDefinitelyFalse(left, nullAssumptions) && isDefinitelyFalse(right, nullAssumptions);
				case EQ -> isDefinitelyNotEqual(left, right, nullAssumptions);
				case NE -> isDefinitelyEqual(left, right, nullAssumptions);
				default -> false;
			};
		}
		return false;
	}

	private boolean isDefinitelyEqual(JCExpression left, JCExpression right, Map<Symbol, Boolean> nullAssumptions) {
		if (isDefinitelyNull(left, nullAssumptions)) {
			return isDefinitelyNull(right, nullAssumptions);
		}
		if (isDefinitelyNonNull(left, nullAssumptions)) {
			return isDefinitelyNonNull(right, nullAssumptions);
		}
		boolean leftTrue = isDefinitelyTrue(left, nullAssumptions);
		boolean leftFalse = isDefinitelyFalse(left, nullAssumptions);
		boolean rightTrue = isDefinitelyTrue(right, nullAssumptions);
		boolean rightFalse = isDefinitelyFalse(right, nullAssumptions);
		if ((leftTrue || leftFalse) && (rightTrue || rightFalse)) {
			return (leftTrue && rightTrue) || (leftFalse && rightFalse);
		}
		return false;
	}

	private boolean isDefinitelyNotEqual(JCExpression left, JCExpression right, Map<Symbol, Boolean> nullAssumptions) {
		if (isDefinitelyNull(left, nullAssumptions)) {
			return isDefinitelyNonNull(right, nullAssumptions);
		}
		if (isDefinitelyNonNull(left, nullAssumptions)) {
			return isDefinitelyNull(right, nullAssumptions);
		}
		boolean leftTrue = isDefinitelyTrue(left, nullAssumptions);
		boolean leftFalse = isDefinitelyFalse(left, nullAssumptions);
		boolean rightTrue = isDefinitelyTrue(right, nullAssumptions);
		boolean rightFalse = isDefinitelyFalse(right, nullAssumptions);
		if ((leftTrue || leftFalse) && (rightTrue || rightFalse)) {
			return (leftTrue && rightFalse) || (leftFalse && rightTrue);
		}
		return false;
	}

	private Map<Symbol, Boolean> nullAssumptionsWhenTrue(JCExpression expression, Map<Symbol, Boolean> nullAssumptions) {
		expression = unwrapParens(expression);
		if (expression instanceof JCUnary unary && unary.getTag() == Tag.NOT) {
			return nullAssumptionsWhenFalse(unary.getExpression(), nullAssumptions);
		}
		if (expression instanceof JCBinary binary) {
			JCExpression left = binary.getLeftOperand();
			JCExpression right = binary.getRightOperand();
			return switch (binary.getTag()) {
				case AND, BITAND -> nullAssumptionsWhenTrue(right, nullAssumptionsWhenTrue(left, nullAssumptions));
				case OR, BITOR -> isDefinitelyFalse(left, nullAssumptions) ? nullAssumptionsWhenTrue(right, nullAssumptionsWhenFalse(left, nullAssumptions)) : nullAssumptions;
				case EQ -> applyNullAssumptions(left, right, true, nullAssumptions);
				case NE -> applyNullAssumptions(left, right, false, nullAssumptions);
				default -> nullAssumptions;
			};
		}
		return nullAssumptions;
	}

	private Map<Symbol, Boolean> nullAssumptionsWhenFalse(JCExpression expression, Map<Symbol, Boolean> nullAssumptions) {
		expression = unwrapParens(expression);
		if (expression instanceof JCUnary unary && unary.getTag() == Tag.NOT) {
			return nullAssumptionsWhenTrue(unary.getExpression(), nullAssumptions);
		}
		if (expression instanceof JCBinary binary) {
			JCExpression left = binary.getLeftOperand();
			JCExpression right = binary.getRightOperand();
			return switch (binary.getTag()) {
				case AND, BITAND -> isDefinitelyTrue(left, nullAssumptions) ? nullAssumptionsWhenFalse(right, nullAssumptionsWhenTrue(left, nullAssumptions)) : nullAssumptions;
				case OR, BITOR -> nullAssumptionsWhenFalse(right, nullAssumptionsWhenFalse(left, nullAssumptions));
				case EQ -> applyNullAssumptions(left, right, false, nullAssumptions);
				case NE -> applyNullAssumptions(left, right, true, nullAssumptions);
				default -> nullAssumptions;
			};
		}
		return nullAssumptions;
	}

	private Map<Symbol, Boolean> applyNullAssumptions(JCExpression left, JCExpression right, boolean equalsNull, Map<Symbol, Boolean> nullAssumptions) {
		Symbol symbol = symbolOf(left);
		if (symbol != null && isDefinitelyNull(right, nullAssumptions)) {
			return withNullAssumption(nullAssumptions, symbol, equalsNull);
		}
		symbol = symbolOf(right);
		if (symbol != null && isDefinitelyNull(left, nullAssumptions)) {
			return withNullAssumption(nullAssumptions, symbol, equalsNull);
		}
		return nullAssumptions;
	}

	private Map<Symbol, Boolean> withNullAssumption(Map<Symbol, Boolean> nullAssumptions, Symbol symbol, boolean isNull) {
		Map<Symbol, Boolean> updatedNullAssumptions = new HashMap<>(nullAssumptions);
		updatedNullAssumptions.put(symbol, isNull);
		return updatedNullAssumptions;
	}

	private boolean isDefinitelyNull(JCExpression expression, Map<Symbol, Boolean> nullAssumptions) {
		expression = unwrapParens(expression);
		if (expression instanceof JCLiteral literal && literal.getValue() == null) {
			return true;
		}
		Symbol symbol = symbolOf(expression);
		return symbol != null && Boolean.TRUE.equals(nullAssumptions.get(symbol));
	}

	private boolean isDefinitelyNonNull(JCExpression expression, Map<Symbol, Boolean> nullAssumptions) {
		expression = unwrapParens(expression);
		Symbol symbol = symbolOf(expression);
		return symbol != null && Boolean.FALSE.equals(nullAssumptions.get(symbol));
	}

	private Symbol symbolOf(JCExpression expression) {
		expression = unwrapParens(expression);
		Symbol symbol = null;
		if (expression instanceof JCIdent ident) {
			symbol = ident.sym;
		} else if (expression instanceof JCFieldAccess fieldAccess) {
			symbol = fieldAccess.sym;
		}
		if (symbol instanceof VarSymbol varSymbol) {
			ElementKind kind = varSymbol.getKind();
			if (kind == ElementKind.LOCAL_VARIABLE
					|| kind == ElementKind.PARAMETER
					|| kind == ElementKind.EXCEPTION_PARAMETER
					|| kind == ElementKind.RESOURCE_VARIABLE
					|| kind == ElementKind.BINDING_VARIABLE) {
				return varSymbol;
			}
		}
		return null;
	}

	private JCExpression unwrapParens(JCExpression expression) {
		while (expression instanceof JCParens parens) {
			expression = parens.expr;
		}
		return expression;
	}

	public List<CategorizedProblem> getDeadCodeProblems() {
		return this.deadCodeProblems;
	}

	private CategorizedProblem toCategorizedProblem(JCTree tree, int problemId) {
		char[] fileName = this.unit.getSourceFile().getName().toCharArray();
		int startPos = tree.getStartPosition();
		int endPos = tree.getEndPosition(this.unit.endPositions) - 1;
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
