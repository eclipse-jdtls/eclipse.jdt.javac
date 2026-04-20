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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotatedType;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCNewArray;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCParens;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.sun.tools.javac.tree.JCTree.JCTypeIntersection;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCUnary;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.JCWildcard;
import com.sun.tools.javac.tree.JCTree.Tag;

class NullAnalysisTreeScanner extends TopLevelTreeScanner<Void, Void> {

	// Same as constants defined in from org.eclipse.jdt.annotation.DefaultLocation
	private enum DefaultLocation {
		FIELD,
		PARAMETER,
		RETURN_TYPE,
		TYPE_PARAMETER,
		TYPE_BOUND,
		TYPE_ARGUMENT,
		ARRAY_CONTENTS,
		RECORD_COMPONENT
	}

	private enum NullState {
		NULL,
		NONNULL,
		UNKNOWN;

		static NullState merge(NullState left, NullState right) {
			return left == right ? left : UNKNOWN;
		}
	}

	private final IProblemFactory problemFactory;
	private final CompilerOptions compilerOptions;

	private final Set<String> nonNullAnnotationNames = new LinkedHashSet<>();
	private final Set<String> nonNullByDefaultAnnotationNames = new LinkedHashSet<>();
	private final ArrayDeque<EnumSet<DefaultLocation>> nullDefaultValuesStack = new ArrayDeque<>();
	private final ArrayDeque<JCTree> nullDefaultDeclarationsStack = new ArrayDeque<>();
	private final ArrayDeque<Map<Symbol, NullState>> nullStatesStack = new ArrayDeque<>();
	private final List<CategorizedProblem> potentialNullReferenceProblems = new ArrayList<>();
	private final List<CategorizedProblem> redundantNullAnnotationProblems = new ArrayList<>();
	private final List<CategorizedProblem> redundantNullDefaultAnnotationProblems = new ArrayList<>();

	private JCCompilationUnit unit;

	NullAnalysisTreeScanner(IProblemFactory problemFactory, CompilerOptions compilerOptions, TypeElement currentTopLevelType) {
		super(currentTopLevelType);
		this.problemFactory = problemFactory;
		this.compilerOptions = compilerOptions;
		addConfiguredAnnotationName(this.nonNullAnnotationNames, compilerOptions.nonNullAnnotationName, compilerOptions.nonNullAnnotationSecondaryNames);
		addConfiguredAnnotationName(this.nonNullByDefaultAnnotationNames, compilerOptions.nonNullByDefaultAnnotationName, compilerOptions.nonNullByDefaultAnnotationSecondaryNames);
	}

	@Override
	public Void visitCompilationUnit(CompilationUnitTree node, Void p) {
		this.unit = (JCCompilationUnit) node;
		EnumSet<DefaultLocation> declaredDefaultLocations = getDeclaredDefaultLocationsOf(this.unit.getPackageAnnotations());
		return withDefaults(declaredDefaultLocations, this.unit, () -> super.visitCompilationUnit(node, p));
	}

	@Override
	public Void visitClass(ClassTree node, Void p) {
		if (node instanceof JCClassDecl classDecl) {
			EnumSet<DefaultLocation> declaredDefaultLocations = getDeclaredDefaultLocationsOf(classDecl.mods.annotations);
			addRedundantNullDefaultProblem(classDecl.mods.annotations, declaredDefaultLocations);
			return withDefaults(declaredDefaultLocations, classDecl, () -> super.visitClass(node, p));
		}
		return super.visitClass(node, p);
	}

	@Override
	public Void visitMethod(MethodTree node, Void p) {
		if (node instanceof JCMethodDecl method) {
			EnumSet<DefaultLocation> declaredDefaultLocations = getDeclaredDefaultLocationsOf(method.mods.annotations);
			addRedundantNullDefaultProblem(method.mods.annotations, declaredDefaultLocations);
			EnumSet<DefaultLocation> resolvedDefaultLocations = resolveDefaultLocations(declaredDefaultLocations);
			if (method.restype != null) {
				addRedundantNonNullProblem(method.mods.annotations, method.restype, DefaultLocation.RETURN_TYPE, resolvedDefaultLocations);
			}
			return withDefaults(declaredDefaultLocations, method, () -> {
				Map<Symbol, NullState> nullStates = new HashMap<>();
				return withNullStates(nullStates, () -> {
					for (JCVariableDecl parameter : method.params) {
						scan(parameter, p);
					}
					if (method.body != null) {
						scan(method.body, p);
					}
					return null;
				});
			});
		}
		return super.visitMethod(node, p);
	}

	@Override
	public Void visitVariable(VariableTree node, Void p) {
		if (node instanceof JCVariableDecl variable) {
			EnumSet<DefaultLocation> declaredDefaults = getDeclaredDefaultLocationsOf(variable.mods.annotations);
			addRedundantNullDefaultProblem(variable.mods.annotations, declaredDefaults);
			EnumSet<DefaultLocation> resolvedDefaultLocations = resolveDefaultLocations(declaredDefaults);
			if (variable.sym instanceof VarSymbol varSymbol) {
				ElementKind kind = varSymbol.getKind();
				if (kind == ElementKind.FIELD) {
					addRedundantNonNullProblem(variable.mods.annotations, variable.vartype, DefaultLocation.FIELD, resolvedDefaultLocations);
				} else if (kind == ElementKind.PARAMETER) {
					addRedundantNonNullProblem(variable.mods.annotations, variable.vartype, DefaultLocation.PARAMETER, resolvedDefaultLocations);
				} else if (kind == ElementKind.RECORD_COMPONENT) {
					addRedundantNonNullProblem(variable.mods.annotations, variable.vartype, DefaultLocation.RECORD_COMPONENT, resolvedDefaultLocations);
				}
			}
			return withDefaults(declaredDefaults, variable, () -> {
				if (variable.init != null) {
					scan(variable.init, p);
				}
				Map<Symbol, NullState> currentStates = this.nullStatesStack.peek();
				if (currentStates != null && isTrackedVariable(variable.sym)) {
					currentStates.put(variable.sym, declaredNullState(variable, currentStates));
				}
				return null;
			});
		}
		return super.visitVariable(node, p);
	}

	@Override
	public Void visitMemberSelect(MemberSelectTree node, Void p) {
		if (node instanceof JCFieldAccess fieldAccess) {
			addPotentialNullReferenceProblem(fieldAccess.selected);
		}
		return super.visitMemberSelect(node, p);
	}

	@Override
	public Void visitTypeParameter(com.sun.source.tree.TypeParameterTree node, Void p) {
		if (node instanceof JCTypeParameter typeParameter) {
			EnumSet<DefaultLocation> defaults = resolveDefaultLocations(null);
			reportRedundantNonNullIfPresent(typeParameter.annotations, typeParameter, DefaultLocation.TYPE_PARAMETER, defaults);
			for (JCExpression bound : typeParameter.bounds) {
				addRedundantNonNullProblem(List.of(), bound, DefaultLocation.TYPE_BOUND, defaults);
			}
		}
		return super.visitTypeParameter(node, p);
	}

	@Override
	public Void visitIf(IfTree node, Void p) {
		if (node instanceof JCIf ifStatement) {
			scan(ifStatement.cond, p);
			Map<Symbol, NullState> currentStates = this.nullStatesStack.peek();
			if (currentStates == null) {
				return null;
			}
			Map<Symbol, NullState> thenStates = new HashMap<>(currentStates);
			applyConditionAssumptions(ifStatement.cond, thenStates, true);
			withNullStates(thenStates, () -> scan(ifStatement.thenpart, p));

			Map<Symbol, NullState> elseStates = new HashMap<>(currentStates);
			if (ifStatement.elsepart != null) {
				applyConditionAssumptions(ifStatement.cond, elseStates, false);
				withNullStates(elseStates, () -> scan(ifStatement.elsepart, p));
			}

			for (Map.Entry<Symbol, NullState> entry : new ArrayList<>(currentStates.entrySet())) {
				NullState thenState = thenStates.getOrDefault(entry.getKey(), entry.getValue());
				NullState elseState = elseStates.getOrDefault(entry.getKey(), entry.getValue());
				currentStates.put(entry.getKey(), NullState.merge(thenState, elseState));
			}
			return null;
		}
		return super.visitIf(node, p);
	}

	@Override
	public Void visitAssignment(AssignmentTree node, Void p) {
		if (node instanceof JCAssign assign) {
			scan(assign.rhs, p);
			scan(assign.lhs, p);
			Map<Symbol, NullState> currentStates = this.nullStatesStack.peek();
			Symbol assignedSymbol = null;
			JCExpression leftHandSide = (JCExpression) unwrapParens(assign.lhs);
			if (leftHandSide instanceof JCIdent ident && isTrackedVariable(ident.sym)) {
				assignedSymbol = ident.sym;
			}
			if (currentStates != null && assignedSymbol != null) {
				currentStates.put(assignedSymbol, nullStateOf(assign.rhs, currentStates));
			}
			return null;
		}
		return super.visitAssignment(node, p);
	}

	private void addConfiguredAnnotationName(Set<String> annotationNames, char[][] primaryName, String[] secondaryNames) {
		if (primaryName != null) {
			annotationNames.add(String.valueOf(CharOperation.concatWith(primaryName, '.')));
		}
		for (String secondaryName : secondaryNames) {
			if (secondaryName.length() > 0) {
				annotationNames.add(secondaryName);
			}
		}
	}

	private EnumSet<DefaultLocation> getDeclaredDefaultLocationsOf(List<JCAnnotation> annotations) {
		EnumSet<DefaultLocation> defaultLocations = null;
		for (JCAnnotation annotation : annotations) {
			if (this.nonNullByDefaultAnnotationNames.contains(annotation.annotationType.type.tsym.toString())) {
				if (defaultLocations == null) {
					defaultLocations = EnumSet.noneOf(DefaultLocation.class);
				}
				if (annotation.getArguments().isEmpty()) {
					defaultLocations.addAll(EnumSet.of(
						DefaultLocation.PARAMETER,
						DefaultLocation.RETURN_TYPE,
						DefaultLocation.FIELD,
						DefaultLocation.TYPE_BOUND,
						DefaultLocation.TYPE_ARGUMENT,
						DefaultLocation.RECORD_COMPONENT
					));
					continue;
				}
				for (JCExpression argument : annotation.getArguments()) {
					addDefaultLocations(argument, defaultLocations);
				}
			}
		}
		return defaultLocations;
	}

	private void addDefaultLocations(JCExpression expression, EnumSet<DefaultLocation> defaults) {
		expression = (JCExpression) unwrapParens(expression);
		if (expression instanceof JCAssign assign) {
			if (assign.lhs instanceof JCIdent lhs && "value".equals(lhs.name.toString())) {
				addDefaultLocations(assign.rhs, defaults);
			}
		} else if (expression instanceof JCNewArray array) {
			for (JCExpression element : array.elems) {
				addDefaultLocations(element, defaults);
			}
		} else if (expression instanceof JCFieldAccess fieldAccess) {
			addDefaultLocation(fieldAccess.name.toString(), defaults);
		} else if (expression instanceof JCIdent ident) {
			addDefaultLocation(ident.name.toString(), defaults);
		}
	}

	private void addDefaultLocation(String name, EnumSet<DefaultLocation> defaults) {
		switch (name) {
			case "FIELD" -> defaults.add(DefaultLocation.FIELD);
			case "PARAMETER" -> defaults.add(DefaultLocation.PARAMETER);
			case "RETURN_TYPE" -> defaults.add(DefaultLocation.RETURN_TYPE);
			case "TYPE_PARAMETER" -> defaults.add(DefaultLocation.TYPE_PARAMETER);
			case "TYPE_BOUND" -> defaults.add(DefaultLocation.TYPE_BOUND);
			case "TYPE_ARGUMENT" -> defaults.add(DefaultLocation.TYPE_ARGUMENT);
			case "ARRAY_CONTENTS" -> defaults.add(DefaultLocation.ARRAY_CONTENTS);
			case "RECORD_COMPONENT" -> defaults.add(DefaultLocation.RECORD_COMPONENT);
		}
	}

	private Void withDefaults(EnumSet<DefaultLocation> declaredDefaults, JCTree declaringNode, Supplier<Void> scopedScan) {
		if (declaredDefaults == null) {
			return scopedScan.get();
		}
		this.nullDefaultValuesStack.push(declaredDefaults);
		this.nullDefaultDeclarationsStack.push(declaringNode);
		try {
			return scopedScan.get();
		} finally {
			this.nullDefaultValuesStack.pop();
			this.nullDefaultDeclarationsStack.pop();
		}
	}

	private EnumSet<DefaultLocation> resolveDefaultLocations(EnumSet<DefaultLocation> declaredDefaults) {
		if (declaredDefaults != null) {
			return declaredDefaults;
		}
		EnumSet<DefaultLocation> currentDefaults = this.nullDefaultValuesStack.peek();
		if (currentDefaults != null) {
			return currentDefaults;
		}
		return EnumSet.noneOf(DefaultLocation.class);
	}

	private Void withNullStates(Map<Symbol, NullState> nullStates, Supplier<Void> scopedScan) {
		this.nullStatesStack.push(nullStates);
		try {
			return scopedScan.get();
		} finally {
			this.nullStatesStack.pop();
		}
	}

	private NullState declaredNullState(JCVariableDecl variable, Map<Symbol, NullState> nullStates) {
		if (findNonNullAnnotation(variable.mods.annotations) != null
				|| findNonNullAnnotationOnType(variable.vartype) != null
				|| variable.vartype.type.isPrimitive()
				|| (variable.sym instanceof VarSymbol varSymbol
						&& varSymbol.getKind() == ElementKind.PARAMETER
						&& resolveDefaultLocations(null).contains(DefaultLocation.PARAMETER))) {
			return NullState.NONNULL;
		}
		return variable.init != null ? nullStateOf(variable.init, nullStates) : NullState.UNKNOWN;
	}

	private void addPotentialNullReferenceProblem(JCExpression expression) {
		expression = (JCExpression) unwrapParens(expression);
		if (!(expression instanceof JCIdent ident)
				|| !(ident.sym instanceof VarSymbol varSymbol)
				|| !isReportableLocalVariable(varSymbol)) {
			return;
		}
		Map<Symbol, NullState> currentStates = this.nullStatesStack.peek();
		NullState nullState = currentStates != null ?
				currentStates.getOrDefault(varSymbol, NullState.UNKNOWN)
				: NullState.UNKNOWN;
		if (nullState != NullState.UNKNOWN) {
			return;
		}
		String[] arguments = new String[] { varSymbol.name.toString() };
		this.potentialNullReferenceProblems.add(
				toCategorizedProblem(
						expression,
						expression,
						IProblem.PotentialNullLocalVariableReference,
						arguments));
	}

	private NullState nullStateOf(JCExpression expression, Map<Symbol, NullState> nullStates) {
		expression = (JCExpression) unwrapParens(expression);
		if (expression == null) {
			return NullState.UNKNOWN;
		}
		if (expression.type.isPrimitive() ||
				expression instanceof JCNewClass ||
				expression instanceof JCNewArray) {
			return NullState.NONNULL;
		}
		if (expression instanceof JCLiteral literal) {
			return literal.getValue() == null ? NullState.NULL : NullState.NONNULL;
		}
		if (expression instanceof JCNewClass || expression instanceof JCNewArray) {
			return NullState.NONNULL;
		}
		if (expression instanceof JCTypeCast cast) {
			return nullStateOf(cast.expr, nullStates);
		}
		if (expression instanceof JCConditional conditional) {
			return NullState.merge(
					nullStateOf(conditional.truepart, nullStates),
					nullStateOf(conditional.falsepart, nullStates));
		}
		if (expression instanceof JCAssign assign) {
			NullState rhsState = nullStateOf(assign.rhs, nullStates);
			Symbol assignedSymbol = null;
			JCExpression leftHandSide = (JCExpression) unwrapParens(assign.lhs);
			if (leftHandSide instanceof JCIdent ident && isTrackedVariable(ident.sym)) {
				assignedSymbol = ident.sym;
			}
			if (assignedSymbol != null) {
				nullStates.put(assignedSymbol, rhsState);
			}
			return rhsState;
		}
		if (expression instanceof JCIdent ident) {
			if ("this".equals(ident.name.toString()) || "super".equals(ident.name.toString())) {
				return NullState.NONNULL;
			}
			if (isTrackedVariable(ident.sym)) {
				return nullStates.getOrDefault(ident.sym, NullState.UNKNOWN);
			}
		}
		return NullState.UNKNOWN;
	}

	private void applyConditionAssumptions(JCExpression condition, Map<Symbol, NullState> nullStates, boolean conditionTrue) {
		condition = (JCExpression) unwrapParens(condition);
		if (condition instanceof JCUnary unary && unary.getTag() == Tag.NOT) {
			applyConditionAssumptions(unary.getExpression(), nullStates, !conditionTrue);
			return;
		}
		if (condition instanceof JCBinary binary) {
			switch (binary.getTag()) {
				case EQ -> applyNullComparison(binary.getLeftOperand(), binary.getRightOperand(),
						conditionTrue ? NullState.NULL : NullState.NONNULL, nullStates);
				case NE -> applyNullComparison(binary.getLeftOperand(), binary.getRightOperand(),
						conditionTrue ? NullState.NONNULL : NullState.NULL, nullStates);
				default -> {}
			}
		}
	}

	private void applyNullComparison(JCExpression left, JCExpression right, NullState assumedState, Map<Symbol, NullState> nullStates) {
		Symbol symbol = null;
		left = (JCExpression) unwrapParens(left);
		if (left instanceof JCIdent ident && isTrackedVariable(ident.sym)) {
			symbol = ident.sym;
		}
		if (symbol != null && nullStateOf(right, nullStates) == NullState.NULL) {
			nullStates.put(symbol, assumedState);
			return;
		}
		symbol = null;
		right = (JCExpression) unwrapParens(right);
		if (right instanceof JCIdent ident && isTrackedVariable(ident.sym)) {
			symbol = ident.sym;
		}
		if (symbol != null && nullStateOf(left, nullStates) == NullState.NULL) {
			nullStates.put(symbol, assumedState);
		}
	}

	private boolean isTrackedVariable(Symbol symbol) {
		if (symbol instanceof VarSymbol varSymbol) {
			return switch (varSymbol.getKind()) {
				case LOCAL_VARIABLE, PARAMETER, EXCEPTION_PARAMETER, RESOURCE_VARIABLE, BINDING_VARIABLE -> true;
				default -> false;
			};
		}
		return false;
	}

	private boolean isReportableLocalVariable(VarSymbol varSymbol) {
		return switch (varSymbol.getKind()) {
			case LOCAL_VARIABLE, EXCEPTION_PARAMETER, RESOURCE_VARIABLE, BINDING_VARIABLE -> true;
			default -> false;
		};
	}

	private void addRedundantNullDefaultProblem(List<JCAnnotation> annotations, EnumSet<DefaultLocation> declaredDefaultLocations) {
		if (declaredDefaultLocations == null || declaredDefaultLocations.isEmpty()) {
			return;
		}
		EnumSet<DefaultLocation> enclosingDefaults = this.nullDefaultValuesStack.peek();
		if (enclosingDefaults == null || !declaredDefaultLocations.equals(enclosingDefaults)) {
			return;
		}
		JCAnnotation nonNullByDefaultAnnotation = null;
		for (JCAnnotation annotation : annotations) {
			if (this.nonNullByDefaultAnnotationNames.contains(annotation.annotationType.type.tsym.toString())) {
				nonNullByDefaultAnnotation = annotation;
				break;
			}
		}
		if (nonNullByDefaultAnnotation == null) {
			return;
		}
		JCTree declaringNode = this.nullDefaultDeclarationsStack.peek();
		this.redundantNullDefaultAnnotationProblems.add(toRedundantNullDefaultProblem(nonNullByDefaultAnnotation, declaringNode));
	}

	private void addRedundantNonNullProblem(List<JCAnnotation> declarationAnnotations, JCTree type,
			DefaultLocation location, EnumSet<DefaultLocation> defaults) {
		if (type == null) {
			return;
		}
		reportRedundantNonNullIfPresent(declarationAnnotations, type, location, defaults);

		JCTree unwrappedType = unwrapParens(type);
		if (unwrappedType instanceof JCAnnotatedType annotatedType) {
			if (location != DefaultLocation.ARRAY_CONTENTS
					&& getUnderlyingType(annotatedType.underlyingType) instanceof JCArrayTypeTree) {
				reportRedundantNonNullIfPresent(List.of(), annotatedType, DefaultLocation.ARRAY_CONTENTS, defaults);
			}
			addRedundantNonNullProblem(List.of(), annotatedType.underlyingType, location, defaults);
			return;
		}
		if (unwrappedType instanceof JCArrayTypeTree arrayType) {
			addRedundantNonNullProblem(declarationAnnotations, arrayType.elemtype, DefaultLocation.ARRAY_CONTENTS, defaults);
			return;
		}
		if (unwrappedType instanceof JCTypeApply typeApply) {
			for (JCExpression typeArgument : typeApply.arguments) {
				addRedundantNonNullProblem(List.of(), typeArgument, DefaultLocation.TYPE_ARGUMENT, defaults);
			}
			return;
		}
		if (unwrappedType instanceof JCWildcard wildcard && wildcard.inner instanceof JCTree wildcardBound) {
			addRedundantNonNullProblem(List.of(), wildcardBound, DefaultLocation.TYPE_BOUND, defaults);
			return;
		}
		if (unwrappedType instanceof JCTypeIntersection intersectionType) {
			for (JCExpression bound : intersectionType.bounds) {
				addRedundantNonNullProblem(List.of(), bound, DefaultLocation.TYPE_BOUND, defaults);
			}
		}
	}

	private void reportRedundantNonNullIfPresent(List<JCAnnotation> declarationAnnotations, JCTree tree,
			DefaultLocation location, EnumSet<DefaultLocation> defaults) {
		if (!defaults.contains(location) || !supportsDefaultLocation(tree, location)) {
			return;
		}
		JCAnnotation nonNullAnnotation = findNonNullAnnotation(declarationAnnotations);
		if (nonNullAnnotation == null) {
			nonNullAnnotation = findNonNullAnnotationOnType(tree);
		}
		if (nonNullAnnotation != null) {
			this.redundantNullAnnotationProblems.add(
				toCategorizedProblem(nonNullAnnotation, unwrapParens(tree), IProblem.RedundantNullAnnotation, new String[0]));
		}
	}

	// is this specific kind of type one where that default should apply
	private boolean supportsDefaultLocation(JCTree type, DefaultLocation location) {
		JCTree unwrappedType = getUnderlyingType(unwrapParens(type));
		return switch (location) {
			case TYPE_ARGUMENT -> !(unwrappedType instanceof JCWildcard) && unwrappedType.type.tsym.getKind() != ElementKind.TYPE_PARAMETER;
			case TYPE_BOUND -> !"java.lang.Object".equals(unwrappedType.type.tsym.toString());
			default -> true;
		};
	}

	private JCAnnotation findNonNullAnnotationOnType(JCTree type) {
		if ((unwrapParens(type) instanceof JCAnnotatedType annotatedType)) {
			return findNonNullAnnotation(annotatedType.annotations);
		}
		return null;
	}

	private JCAnnotation findNonNullAnnotation(List<JCAnnotation> annotations) {
		for (JCAnnotation annotation : annotations) {
			if (this.nonNullAnnotationNames.contains(annotation.annotationType.type.tsym.toString())) {
				return annotation;
			}
		}
		return null;
	}

	private JCTree getUnderlyingType(JCTree type) {
		while (type instanceof JCAnnotatedType annotatedType) {
			type = annotatedType.underlyingType;
		}
		return type;
	}

	private JCTree unwrapParens(JCTree expression) {
		while (expression instanceof JCParens parens) {
			expression = parens.expr;
		}
		return expression;
	}

	private CategorizedProblem toCategorizedProblem(JCTree startTree, JCTree endTree, int problemId, String[] arguments) {
		char[] fileName = this.unit.getSourceFile().getName().toCharArray();
		int startPos = startTree.getStartPosition();
		int endPos = endTree.getEndPosition(this.unit.endPositions) - 1;
		if (endPos < startPos && startTree == endTree && startTree instanceof JCIdent ident) {
			endPos = startPos + ident.name.length() - 1;
		}
		int line = this.unit.getLineMap().getLineNumber(startPos);
		int column = this.unit.getLineMap().getColumnNumber(startPos);
		return this.problemFactory.createProblem(fileName,
				problemId,
				arguments,
				arguments,
				JavacUtils.toSeverity(this.compilerOptions, problemId),
				startPos, endPos,
				line, column);
	}

	private CategorizedProblem toRedundantNullDefaultProblem(JCAnnotation annotation, JCTree declaringNode) {
		int problemId = IProblem.RedundantNullDefaultAnnotation;
		String[] arguments = new String[0];
		if (declaringNode instanceof JCCompilationUnit compilationUnit) {
			problemId = IProblem.RedundantNullDefaultAnnotationPackage;
			arguments = new String[] { compilationUnit.getPackageName().toString() };
		} else if (declaringNode instanceof JCTree.JCClassDecl classDecl) {
			problemId = IProblem.RedundantNullDefaultAnnotationType;
			arguments = new String[] { classDecl.sym.getQualifiedName().toString() };
		} else if (declaringNode instanceof JCMethodDecl method) {
			problemId = IProblem.RedundantNullDefaultAnnotationMethod;
			arguments = new String[] { method.sym.toString() };
		} else if (declaringNode instanceof JCVariableDecl variable) {
			problemId = IProblem.RedundantNullDefaultAnnotationLocal;
			if (variable.sym instanceof VarSymbol varSymbol && varSymbol.getKind() == ElementKind.FIELD) {
				problemId = IProblem.RedundantNullDefaultAnnotationField;
			}
			arguments = new String[] { variable.sym.name.toString() };
		}
		return toCategorizedProblem(annotation, annotation, problemId, arguments);
	}

	public List<CategorizedProblem> getRedundantNullAnnotationProblems() {
		List<CategorizedProblem> allRedundantNullAnnotationProblems = new ArrayList<>();
		allRedundantNullAnnotationProblems.addAll(this.redundantNullAnnotationProblems);
		allRedundantNullAnnotationProblems.addAll(this.redundantNullDefaultAnnotationProblems);
		return allRedundantNullAnnotationProblems;
	}

	public List<CategorizedProblem> getPotentialNullReferenceProblems() {
		return this.potentialNullReferenceProblems;
	}
}
