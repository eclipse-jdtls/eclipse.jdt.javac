/*******************************************************************************
* Copyright (c) 2024 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License 2.0
* which accompanies this distribution, and is available at
* https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.internal.javac.problem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities;
import org.eclipse.jdt.internal.javac.JavacUtils;
import org.eclipse.jdt.internal.javac.UnusedTreeScanner.CloseableState;

import shaded.com.sun.source.tree.CompilationUnitTree;
import shaded.com.sun.source.tree.Tree;
import shaded.com.sun.tools.javac.code.Symbol;
import shaded.com.sun.tools.javac.code.Symbol.VarSymbol;
import shaded.com.sun.tools.javac.tree.JCTree;
import shaded.com.sun.tools.javac.tree.JCTree.JCAssign;
import shaded.com.sun.tools.javac.tree.JCTree.JCClassDecl;
import shaded.com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import shaded.com.sun.tools.javac.tree.JCTree.JCExpression;
import shaded.com.sun.tools.javac.tree.JCTree.JCImport;
import shaded.com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import shaded.com.sun.tools.javac.tree.JCTree.JCTypeCast;
import shaded.com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import shaded.com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import shaded.javax.lang.model.element.ElementKind;
import shaded.javax.tools.JavaFileObject;

public class UnusedProblemFactory {
	private Map<JavaFileObject, Map<String, List<CategorizedProblem>>> filesToUnusedImports = new HashMap<>();
	private IProblemFactory problemFactory;
	private CompilerOptions compilerOptions;

	public UnusedProblemFactory(IProblemFactory problemFactory, CompilerOptions compilerOptions) {
		this.problemFactory = problemFactory;
		this.compilerOptions = compilerOptions;
	}

	public UnusedProblemFactory(IProblemFactory problemFactory, Map<String, String> compilerOptions) {
		this.problemFactory = problemFactory;
		this.compilerOptions = new CompilerOptions(compilerOptions);
	}

	public List<CategorizedProblem> addUnusedImports(CompilationUnitTree unit, Map<String, List<JCImport>> unusedImports) {
		int severity = JavacUtils.toSeverity(this.compilerOptions, IProblem.UnusedImport);
		if (severity == ProblemSeverities.Ignore || severity == ProblemSeverities.Optional) {
			return Collections.emptyList();
		}

		Map<String, List<CategorizedProblem>> unusedWarning = new LinkedHashMap<>();
		final char[] fileName = unit.getSourceFile().getName().toCharArray();
		for (Entry<String, List<JCImport>> unusedImport : unusedImports.entrySet()) {
			String importName = unusedImport.getKey();
			List<CategorizedProblem> unusedWarningList = unusedWarning.get(importName);
			if (unusedWarningList == null) {
				unusedWarningList = new ArrayList<>();
			}
			for (JCImport importNode : unusedImport.getValue()) {
				int pos = importNode.qualid.getStartPosition();
				int endPos = pos + importName.length() - 1;
				if (importName.endsWith(".*")) {
					/**
					 * For the unused star imports (e.g., java.util.*), display the
					 * diagnostic on the java.util part instead of java.util.* to
					 * be compatible with 'remove unused import' quickfix in JDT.
					 */
					importName = importName.substring(0, importName.length() - 2);
					endPos = endPos - 2;
				}
				int line = (int) unit.getLineMap().getLineNumber(pos);
				int column = (int) unit.getLineMap().getColumnNumber(pos);
				String[] arguments = new String[] { importName };
				CategorizedProblem problem = problemFactory.createProblem(fileName,
							IProblem.UnusedImport,
							arguments,
							arguments,
							severity, pos, endPos, line, column);
				unusedWarningList.add(problem);
			}
			unusedWarning.put(importName, unusedWarningList);
		}

		JavaFileObject file = unit.getSourceFile();
		Map<String, List<CategorizedProblem>> newUnusedImports = mergeUnusedImports(filesToUnusedImports.get(file), unusedWarning);
		filesToUnusedImports.put(file, newUnusedImports);
		List<CategorizedProblem> result = new ArrayList<>();
		for (List<CategorizedProblem> newUnusedImportList : newUnusedImports.values()) {
		    result.addAll(newUnusedImportList);
		}
		return result;
	}

	public List<CategorizedProblem> addUnusedPrivateMembers(CompilationUnitTree unit, List<Tree> unusedPrivateDecls) {
		if (unit == null) {
			return Collections.emptyList();
		}

		final char[] fileName = unit.getSourceFile().getName().toCharArray();
		List<CategorizedProblem> problems = new ArrayList<>();
		for (Tree decl : unusedPrivateDecls) {
			CategorizedProblem problem = null;
			if (decl instanceof JCClassDecl classDecl) {
				int severity = JavacUtils.toSeverity(this.compilerOptions, IProblem.UnusedPrivateType);
				if (severity == ProblemSeverities.Ignore || severity == ProblemSeverities.Optional) {
					continue;
				}

				int pos = classDecl.getPreferredPosition();
				int startPos = pos;
				int endPos = pos;
				String shortName = classDecl.name.toString();
				JavaFileObject fileObject = unit.getSourceFile();
				try {
					CharSequence charContent = fileObject.getCharContent(true);
					String content = charContent.toString();
					if (content != null && content.length() > pos) {
						String temp = content.substring(pos);
						int index = temp.indexOf(shortName);
						if (index >= 0) {
							startPos = pos + index;
							endPos = startPos + shortName.length() - 1;
						}
					}
				} catch (IOException e) {
					// ignore
				}

				int line = (int) unit.getLineMap().getLineNumber(startPos);
				int column = (int) unit.getLineMap().getColumnNumber(startPos);
				problem = problemFactory.createProblem(fileName,
						IProblem.UnusedPrivateType, new String[] {
							shortName
						}, new String[] {
							shortName
						},
						severity, startPos, endPos, line, column);
			} else if (decl instanceof JCMethodDecl methodDecl) {
				int problemId = methodDecl.sym.isConstructor() ? IProblem.UnusedPrivateConstructor
						: IProblem.UnusedPrivateMethod;
				int severity = JavacUtils.toSeverity(this.compilerOptions, problemId);
				if (severity == ProblemSeverities.Ignore || severity == ProblemSeverities.Optional) {
					continue;
				}

				String selector = methodDecl.name.toString();
				String typeName = methodDecl.sym.owner.name.toString();
				String[] params = methodDecl.params.stream().map(variableDecl -> {
					return variableDecl.vartype.toString();
				}).toArray(String[]::new);
				String[] arguments = new String[] {
						typeName, selector, String.join(", ", params)
				};

				int pos = methodDecl.getPreferredPosition();
				int endPos = pos + methodDecl.name.toString().length() - 1;
				int line = (int) unit.getLineMap().getLineNumber(pos);
				int column = (int) unit.getLineMap().getColumnNumber(pos);
				problem = problemFactory.createProblem(fileName,
						problemId, arguments, arguments,
						severity, pos, endPos, line, column);
			} else if (decl instanceof JCVariableDecl variableDecl) {
				VarSymbol varSymbol = variableDecl.sym;
				if (varSymbol == null || varSymbol.getKind() != ElementKind.FIELD) {
					continue;
				}

				int pos = variableDecl.getPreferredPosition();
				int endPos = pos + variableDecl.name.toString().length() - 1;
				int line = (int) unit.getLineMap().getLineNumber(pos);
				int column = (int) unit.getLineMap().getColumnNumber(pos);
				String typeName = varSymbol.owner.name.toString();
				String name = variableDecl.name.toString();
				String[] arguments = new String[] {
					typeName, name
				};
				int severity = JavacUtils.toSeverity(this.compilerOptions, IProblem.UnusedPrivateField);
				if (severity == ProblemSeverities.Ignore || severity == ProblemSeverities.Optional) {
					continue;
				}

				problem = problemFactory.createProblem(fileName,
						IProblem.UnusedPrivateField, arguments, arguments,
						severity, pos, endPos, line, column);
			}

			problems.add(problem);
		}

		return problems;
	}

	public List<CategorizedProblem> addUnusedLocalVariables(CompilationUnitTree unit, List<JCVariableDecl> unusedVariables) {
		if (unit == null) {
			return Collections.emptyList();
		}

		final char[] fileName = unit.getSourceFile().getName().toCharArray();
		List<CategorizedProblem> problems = new ArrayList<>();
		for (JCVariableDecl variableDecl : unusedVariables) {
			int pos = variableDecl.getPreferredPosition();
			int endPos = pos + variableDecl.name.toString().length() - 1;
			int line = (int) unit.getLineMap().getLineNumber(pos);
			int column = (int) unit.getLineMap().getColumnNumber(pos);
			int problemId = IProblem.LocalVariableIsNeverUsed;
			String name = variableDecl.name.toString();
			String[] arguments = new String[] { name };
			VarSymbol varSymbol = variableDecl.sym;
			ElementKind varKind = varSymbol == null ? null : varSymbol.getKind();
			if (varKind == ElementKind.PARAMETER) {
				problemId = IProblem.ArgumentIsNeverUsed;
			} else if (varKind == ElementKind.EXCEPTION_PARAMETER) {
				problemId = IProblem.ExceptionParameterIsNeverUsed;
			}

			int severity = JavacUtils.toSeverity(this.compilerOptions, problemId);
			if (severity == ProblemSeverities.Ignore || severity == ProblemSeverities.Optional) {
				continue;
			}

			CategorizedProblem problem = problemFactory.createProblem(fileName,
					problemId, arguments, arguments,
					severity, pos, endPos, line, column);
			problems.add(problem);
		}

		return problems;
	}

	public List<CategorizedProblem> addUnusedTypeParameters(CompilationUnitTree unit, List<JCTypeParameter> unusedTypeParameters) {
		if (unit == null) {
			return Collections.emptyList();
		}

		int severity = JavacUtils.toSeverity(this.compilerOptions, IProblem.UnusedTypeParameter);
		if (severity == ProblemSeverities.Ignore || severity == ProblemSeverities.Optional) {
			return Collections.emptyList();
		}

		final char[] fileName = unit.getSourceFile().getName().toCharArray();
		List<CategorizedProblem> problems = new ArrayList<>();
		for (JCTypeParameter typeParameter : unusedTypeParameters) {
			String name = typeParameter.name.toString();
			int startPos = typeParameter.getStartPosition();
			int endPos = startPos + name.length() - 1;
			int line = (int) unit.getLineMap().getLineNumber(startPos);
			int column = (int) unit.getLineMap().getColumnNumber(startPos);
			String[] arguments = new String[] { name };
			CategorizedProblem problem = problemFactory.createProblem(fileName,
					IProblem.UnusedTypeParameter,
					arguments,
					arguments,
					severity, startPos, endPos, line, column);
			problems.add(problem);
		}
		return problems;
	}

	public List<CategorizedProblem> addUnnecessaryCasts(CompilationUnitTree unit, List<JCTypeCast> unnecessaryCasts) {
		int severity = JavacUtils.toSeverity(this.compilerOptions, IProblem.UnnecessaryCast);
		if (severity == ProblemSeverities.Ignore || severity == ProblemSeverities.Optional) {
			return Collections.emptyList();
		}

		final char[] fileName = unit.getSourceFile().getName().toCharArray();
		List<CategorizedProblem> problems = new ArrayList<>();
		for (JCTypeCast cast : unnecessaryCasts) {
			JCExpression expr = cast.expr;
			int pos = cast.getStartPosition();
			int endPos = expr.getStartPosition() - 1;

			String castType = cast.clazz.type.toString();
			String exprType = expr.type.toString();
			String[] arguments = new String[] { exprType, castType };

			int line = (int) unit.getLineMap().getLineNumber(pos);
			int column = (int) unit.getLineMap().getColumnNumber(pos);

			CategorizedProblem problem = problemFactory.createProblem(fileName,
					IProblem.UnnecessaryCast,
					arguments,
					arguments,
					severity, pos, endPos, line, column);
			problems.add(problem);
		}
		return problems;
	}

	public List<CategorizedProblem> addNoEffectAssignments(CompilationUnitTree unit, List<JCAssign> noEffectAssignments) {
		int severity = JavacUtils.toSeverity(this.compilerOptions, IProblem.AssignmentHasNoEffect);
		if (severity == ProblemSeverities.Ignore || severity == ProblemSeverities.Optional) {
			return Collections.emptyList();
		}

		final char[] fileName = unit.getSourceFile().getName().toCharArray();
		List<CategorizedProblem> problems = new ArrayList<>();
		for (JCAssign assign : noEffectAssignments) {
			int pos = assign.getStartPosition();
			int endPos = assign.getEndPosition(((JCCompilationUnit) unit).endPositions) - 1;

			String varName = assign.lhs.toString();
			String[] arguments = new String[] { varName };

			int line = (int) unit.getLineMap().getLineNumber(pos);
			int column = (int) unit.getLineMap().getColumnNumber(pos);

			CategorizedProblem problem = problemFactory.createProblem(fileName,
					IProblem.AssignmentHasNoEffect,
					arguments,
					arguments,
					severity, pos, endPos, line, column);
			problems.add(problem);
		}
		return problems;
	}

	public List<CategorizedProblem> addUnclosedCloseables(CompilationUnitTree unit, Map<Symbol, CloseableState> unclosedCloseables) {
		if (unit == null) {
			return Collections.emptyList();
		}

		final char[] fileName = unit.getSourceFile().getName().toCharArray();
		List<CategorizedProblem> problems = new ArrayList<>();
		for (Entry<Symbol, CloseableState> entry : unclosedCloseables.entrySet()) {
			Symbol symbol = entry.getKey();
			CloseableState problemInfo = entry.getValue();
			int problemId = problemInfo.potential() ? IProblem.PotentiallyUnclosedCloseable : IProblem.UnclosedCloseable;
			int severity = JavacUtils.toSeverity(this.compilerOptions, problemId);
			if (severity == ProblemSeverities.Ignore || severity == ProblemSeverities.Optional) continue;

			String varName = symbol.name.toString();
			String[] arguments = new String[] { varName };

			JCTree location = problemInfo.location();
			int pos = location.getStartPosition();
			int endPos = location.getEndPosition(((JCCompilationUnit) unit).endPositions) - 1;

			int line = (int) unit.getLineMap().getLineNumber(pos);
			int column = (int) unit.getLineMap().getColumnNumber(pos);

			CategorizedProblem problem = problemFactory.createProblem(fileName,
					problemId,
					arguments,
					arguments,
					severity, pos, endPos, line, column);
			problems.add(problem);
		}
		return problems;
	}

	// Merge the entries that exist in both maps
	private Map<String, List<CategorizedProblem>> mergeUnusedImports(Map<String, List<CategorizedProblem>> map1, Map<String, List<CategorizedProblem>> map2) {
		if (map1 == null) {
			return map2;
		} else if (map2 == null) {
			return map2;
		}

		Map<String, List<CategorizedProblem>> mergedMap = new LinkedHashMap<>();
		for (Entry<String, List<CategorizedProblem>> entry : map1.entrySet()) {
			if (map2.containsKey(entry.getKey())) {
				mergedMap.put(entry.getKey(), entry.getValue());
			}
		}

		return mergedMap;
	}
}
