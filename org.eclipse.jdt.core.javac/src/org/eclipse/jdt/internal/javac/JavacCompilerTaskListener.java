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

package org.eclipse.jdt.internal.javac;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.internal.compiler.ClassFile;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities;
import org.eclipse.jdt.internal.core.builder.SourceFile;
import org.eclipse.jdt.internal.javac.problem.UnusedProblemFactory;

import shaded.com.sun.source.tree.ClassTree;
import shaded.com.sun.source.tree.CompilationUnitTree;
import shaded.com.sun.source.tree.IdentifierTree;
import shaded.com.sun.source.tree.MemberSelectTree;
import shaded.com.sun.source.util.TaskEvent;
import shaded.com.sun.source.util.TaskListener;
import shaded.com.sun.tools.javac.code.Symbol;
import shaded.com.sun.tools.javac.code.Symbol.ClassSymbol;
import shaded.com.sun.tools.javac.code.Symbol.PackageSymbol;
import shaded.com.sun.tools.javac.code.Symbol.TypeSymbol;
import shaded.com.sun.tools.javac.code.Symbol.VarSymbol;
import shaded.com.sun.tools.javac.code.Type;
import shaded.com.sun.tools.javac.code.Type.ArrayType;
import shaded.com.sun.tools.javac.code.Type.MethodType;
import shaded.com.sun.tools.javac.main.Option;
import shaded.com.sun.tools.javac.tree.JCTree.JCClassDecl;
import shaded.com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import shaded.com.sun.tools.javac.tree.JCTree.JCIdent;
import shaded.com.sun.tools.javac.tree.JCTree.JCModuleDecl;
import shaded.com.sun.tools.javac.util.Context;
import shaded.com.sun.tools.javac.util.Options;
import shaded.javax.lang.model.element.TypeElement;
import shaded.javax.tools.JavaFileObject;

public class JavacCompilerTaskListener implements TaskListener {
	private Map<ICompilationUnit, JavacCompilationResult> results = new HashMap<>();
	private IProblemFactory problemFactory;
	private UnusedProblemFactory unusedProblemFactory;
	private JavacConfig config;
	private final Map<JavaFileObject, ICompilationUnit> fileObjectToCUMap;
	private final JavacCompiler javacCompiler;
	private final Context context;
	public final Path tempDir;
	private static final Set<String> PRIMITIVE_TYPES = new HashSet<String>(Arrays.asList(
		"byte",
		"short",
		"int",
		"long",
		"float",
		"double",
		"char",
		"boolean"
	));

	private static final char[] MODULE_INFO_NAME = "module-info".toCharArray();

	public JavacCompilerTaskListener(JavacCompiler javacCompiler, JavacConfig config, IProblemFactory problemFactory, Map<JavaFileObject, ICompilationUnit> fileObjectToCUMap, Context context) {
		this.javacCompiler = javacCompiler;
		this.config = config;
		this.problemFactory = problemFactory;
		this.unusedProblemFactory = new UnusedProblemFactory(problemFactory, config.compilerOptions());
		this.context = context;
		this.fileObjectToCUMap = fileObjectToCUMap;
		Path dir = null;
		try {
			dir = Files.createTempDirectory("javac-build");
		} catch (IOException e) {
			ILog.get().error(e.getMessage(), e);
		}
		tempDir = dir;
	}

	@Override
	public void finished(TaskEvent e) {
		if (e.getKind() == TaskEvent.Kind.GENERATE) {
			final JavaFileObject file = e.getSourceFile();
			final ICompilationUnit cu = this.fileObjectToCUMap.get(file);
			if (cu == null && e.getTypeElement() instanceof ClassSymbol clazz && isGeneratedSource(file)) {
				try {
					// Write the class files for the generated sources.
					writeClassFile(clazz);
				} catch (CoreException e1) {
					// TODO
				}
			} else if (cu != null && e.getTypeElement() instanceof ClassSymbol clazz) {
				var classFile = getJavacClassFile(clazz);
				var resultForCU = this.results.computeIfAbsent(cu, JavacCompilationResult::new);
				if (!resultForCU.compiledTypes.values().contains(classFile)) {
					resultForCU.record(clazz.flatName().toString().replace('.', '/').toCharArray(), classFile);
				}
			}
		} else if (e.getKind() == TaskEvent.Kind.ANALYZE) {
			final JavaFileObject file = e.getSourceFile();
			final ICompilationUnit cu = this.fileObjectToCUMap.get(file);
			if (cu == null) {
				return;
			}
			final JavacCompilationResult result = this.results.computeIfAbsent(cu, JavacCompilationResult::new);
			final Map<Symbol, ClassFile> visitedClasses = new HashMap<Symbol, ClassFile>();
			final Set<ClassSymbol> hierarchyRecorded = new HashSet<>();
			final TypeElement currentTopLevelType = e.getTypeElement();
			final CompilationUnitTree unit = e.getCompilationUnit();

			boolean getUnusedPrivateMembers = this.javacCompiler.options.getSeverity(CompilerOptions.UnusedPrivateMember) != ProblemSeverities.Ignore;
			boolean getUnusedLocalVariables = this.javacCompiler.options.getSeverity(CompilerOptions.UnusedLocalVariable) != ProblemSeverities.Ignore;
			boolean getUnusedImports = this.javacCompiler.options.getSeverity(CompilerOptions.UnusedImport) != ProblemSeverities.Ignore;
			boolean getUnnecessaryCasts = this.javacCompiler.options.getSeverity(CompilerOptions.UnnecessaryTypeCheck) != ProblemSeverities.Ignore;
			boolean getNoEffectAssignments = this.javacCompiler.options.getSeverity(CompilerOptions.NoEffectAssignment) != ProblemSeverities.Ignore;
			boolean getUnclosedCloseables = this.javacCompiler.options.getSeverity(CompilerOptions.UnclosedCloseable) != ProblemSeverities.Ignore;
			boolean getUnusedTypeParameters = this.javacCompiler.options.getSeverity(CompilerOptions.UnusedTypeParameter) != ProblemSeverities.Ignore;
			boolean getAccessRestrictions = Options.instance(context).get(Option.XLINT_CUSTOM).contains("all");
			boolean getIndirectStaticAccessProblems = this.javacCompiler.options.getSeverity(CompilerOptions.IndirectStaticAccess) != ProblemSeverities.Ignore;
			boolean getUnqualifiedFieldAccessProblems = this.javacCompiler.options.getSeverity(CompilerOptions.UnqualifiedFieldAccess) != ProblemSeverities.Ignore;
			boolean getDeadCodeProblems = this.javacCompiler.options.getSeverity(CompilerOptions.DeadCode) != ProblemSeverities.Ignore;

			UnusedTreeScanner<Void, Void> unusedTreeScanner = null;
			if (getUnusedPrivateMembers || getUnusedLocalVariables || getUnusedImports || getUnnecessaryCasts
					|| getNoEffectAssignments || getUnclosedCloseables || getUnusedTypeParameters) {
				unusedTreeScanner = new UnusedTreeScanner<>() {

					@Override
					public Void visitModule(shaded.com.sun.source.tree.ModuleTree node, Void p) {
						if (node instanceof JCModuleDecl moduleDecl) {
							IContainer expectedOutputDir = computeOutputDirectory(cu);
							ClassFile currentClass = new JavacClassFile(moduleDecl, expectedOutputDir, tempDir);
							result.record(MODULE_INFO_NAME, currentClass);
						}
						return super.visitModule(node, p);
					}

					@Override
					public Void visitClass(ClassTree node, Void p) {
						if (node instanceof JCClassDecl classDecl) {
							/**
							 * If a Java file contains multiple top-level types, it will
							 * trigger multiple ANALYZE taskEvents for the same compilation
							 * unit. Each ANALYZE taskEvent corresponds to the completion
							 * of analysis for a single top-level type. Therefore, in the
							 * ANALYZE task event listener, we only visit the class and nested
							 * classes that belong to the currently analyzed top-level type.
							 */
							if (Objects.equals(currentTopLevelType, classDecl.sym)
								|| !(classDecl.sym.owner instanceof PackageSymbol)) {
								String fullName = classDecl.sym.flatName().toString();
								String compoundName = fullName.replace('.', '/');
								Symbol enclosingClassSymbol = this.getEnclosingClass(classDecl.sym);
								ClassFile enclosingClassFile = enclosingClassSymbol == null ? null : visitedClasses.get(enclosingClassSymbol);
								IContainer expectedOutputDir = computeOutputDirectory(cu);
								ClassFile currentClass = new JavacClassFile(fullName, enclosingClassFile, expectedOutputDir, tempDir);
								visitedClasses.put(classDecl.sym, currentClass);
								result.record(compoundName.toCharArray(), currentClass);
								recordTypeHierarchy(classDecl.sym);
							} else {
								return null; // Skip if it does not belong to the currently analyzed top-level type.
							}
						}

						return super.visitClass(node, p);
					}

					@Override
					public Void visitIdentifier(IdentifierTree node, Void p) {
						if (node instanceof JCIdent id
								&& id.sym instanceof TypeSymbol typeSymbol) {
							String qualifiedName = typeSymbol.getQualifiedName().toString();
							recordQualifiedReference(qualifiedName, false);
						}
						return super.visitIdentifier(node, p);
					}

					@Override
					public Void visitMemberSelect(MemberSelectTree node, Void p) {
						if (node instanceof JCFieldAccess field) {
							if (field.sym != null &&
								!(field.type instanceof MethodType || "<any?>".equals(field.type.toString()))) {
								recordQualifiedReference(node.toString(), false);
								if (field.sym instanceof VarSymbol) {
									TypeSymbol elementSymbol = field.type.tsym;
									if (field.type instanceof ArrayType arrayType) {
										elementSymbol = getElementType(arrayType);
									}
									if (elementSymbol instanceof ClassSymbol classSymbol) {
										recordQualifiedReference(classSymbol.className(), true);
									}
								}
							}
						}
						return super.visitMemberSelect(node, p);
					}

					private Symbol getEnclosingClass(Symbol symbol) {
						while (symbol != null) {
							if (symbol.owner instanceof ClassSymbol) {
								return symbol.owner;
							} else if (symbol.owner instanceof PackageSymbol) {
								return null;
							}

							symbol = symbol.owner;
						}

						return null;
					}

					private TypeSymbol getElementType(ArrayType arrayType) {
						if (arrayType.elemtype instanceof ArrayType subArrayType) {
							return getElementType(subArrayType);
						}

						return arrayType.elemtype.tsym;
					}

					private void recordQualifiedReference(String qualifiedName, boolean recursive) {
						if (PRIMITIVE_TYPES.contains(qualifiedName)) {
							return;
						}

						String[] nameParts = qualifiedName.split("\\.");
						int length = nameParts.length;
						if (length == 1) {
							result.addRootReference(nameParts[0]);
							result.addSimpleNameReference(nameParts[0]);
							return;
						}

						if (!recursive) {
							result.addRootReference(nameParts[0]);
							result.addSimpleNameReference(nameParts[length - 1]);
							result.addQualifiedReference(nameParts);
						} else {
							result.addRootReference(nameParts[0]);
							while (result.addQualifiedReference(Arrays.copyOfRange(nameParts, 0, length))) {
								if (length == 2) {
									result.addSimpleNameReference(nameParts[0]);
									result.addSimpleNameReference(nameParts[1]);
									return;
								}

								length--;
								result.addSimpleNameReference(nameParts[length]);
							}
						}
					}

					private void recordTypeHierarchy(ClassSymbol classSymbol) {
						if (hierarchyRecorded.contains(classSymbol)) {
							return;
						}

						hierarchyRecorded.add(classSymbol);
						Type superClass = classSymbol.getSuperclass();
						if (superClass.tsym instanceof ClassSymbol superClassType) {
							recordQualifiedReference(superClassType.className(), true);
							recordTypeHierarchy(superClassType);
						}

						for (Type superInterface : classSymbol.getInterfaces()) {
							if (superInterface.tsym instanceof ClassSymbol superInterfaceType) {
								recordQualifiedReference(superInterfaceType.className(), true);
								recordTypeHierarchy(superInterfaceType);
							}
						}
					}
				};
				unusedTreeScanner.scan(unit, null);
			}

			AccessRestrictionTreeScanner accessRestrictionScanner = null;
			if (getAccessRestrictions) {
				accessRestrictionScanner = new AccessRestrictionTreeScanner(javacCompiler.lookupEnvironment.nameEnvironment, this.problemFactory, this.javacCompiler.options);
				accessRestrictionScanner.scan(unit, null);
			}

			CodeStyleTreeScanner codeStyleScanner = null;
			if (getIndirectStaticAccessProblems || getUnqualifiedFieldAccessProblems) {
				codeStyleScanner = new CodeStyleTreeScanner(this.context, this.problemFactory, this.javacCompiler.options) {
					@Override
					public Void visitClass(ClassTree node, Void p) {
						if (node instanceof JCClassDecl classDecl) {
							/**
							 * If a Java file contains multiple top-level types, it will
							 * trigger multiple ANALYZE taskEvents for the same compilation
							 * unit. Each ANALYZE taskEvent corresponds to the completion
							 * of analysis for a single top-level type. Therefore, in the
							 * ANALYZE task event listener, we only visit the class and nested
							 * classes that belong to the currently analyzed top-level type.
							 */
							if (Objects.equals(currentTopLevelType, classDecl.sym)
									|| !(classDecl.sym.owner instanceof PackageSymbol)) {
								return super.visitClass(node, p);
							} else {
								return null; // Skip if it does not belong to the currently analyzed top-level type.
							}
						}

						return super.visitClass(node, p);
					}
				};
				codeStyleScanner.scan(unit, null);
			}

			DeadCodeTreeScanner deadCodeScanner = null;
			if (getDeadCodeProblems) {
				deadCodeScanner = new DeadCodeTreeScanner(this.problemFactory, this.javacCompiler.options) {
					@Override
					public Void visitClass(ClassTree node, Void p) {
						if (node instanceof JCClassDecl classDecl) {
							/**
							 * If a Java file contains multiple top-level types, it will
							 * trigger multiple ANALYZE taskEvents for the same compilation
							 * unit. Each ANALYZE taskEvent corresponds to the completion
							 * of analysis for a single top-level type. Therefore, in the
							 * ANALYZE task event listener, we only visit the class and nested
							 * classes that belong to the currently analyzed top-level type.
							 */
							if (Objects.equals(currentTopLevelType, classDecl.sym)
									|| !(classDecl.sym.owner instanceof PackageSymbol)) {
								return super.visitClass(node, p);
							} else {
								return null; // Skip if it does not belong to the currently analyzed top-level type.
							}
						}

						return super.visitClass(node, p);
					}
				};
				deadCodeScanner.scan(unit, null);
			}

			if (unusedTreeScanner != null) {
				if (getUnusedPrivateMembers) {
					result.addUnusedMembers(unusedTreeScanner.getUnusedPrivateMembers(this.unusedProblemFactory));
				}
				if (getUnusedLocalVariables) {
					result.addUnusedLocalVariables(unusedTreeScanner.getUnusedLocalVariables(this.unusedProblemFactory));
				}
				if (getUnusedImports) {
					result.addUnusedImports(unusedTreeScanner.getUnusedImports(this.unusedProblemFactory));
				}
				if (getUnnecessaryCasts) {
					result.addUnnecessaryCasts(unusedTreeScanner.getUnnecessaryCasts(this.unusedProblemFactory));
				}
				if (getNoEffectAssignments) {
					result.addNoEffectAssignments(unusedTreeScanner.getNoEffectAssignments(this.unusedProblemFactory));
				}
				if (getUnclosedCloseables) {
					result.addUnclosedCloseables(unusedTreeScanner.getUnclosedCloseables(this.unusedProblemFactory));
				}
				if (getUnusedTypeParameters) {
					result.addUnusedTypeParameters(unusedTreeScanner.getUnusedTypeParameters(this.unusedProblemFactory));
				}
			}
			if (accessRestrictionScanner != null) {
				result.addAccessRestrictionProblems(accessRestrictionScanner.getAccessRestrictionProblems());
			}
			if (codeStyleScanner != null) {
				if (getIndirectStaticAccessProblems) {
					result.addIndirectStaticAccessProblems(codeStyleScanner.getIndirectStaticAccessProblems());
				}
				if (getUnqualifiedFieldAccessProblems) {
					result.addUnqualifiedFieldAccessProblems(codeStyleScanner.getUnqualifiedFieldAccessProblems());
				}
			}
			if (deadCodeScanner != null) {
				result.addDeadCodeProblems(deadCodeScanner.getDeadCodeProblems());
			}
		}
	}

	private JavacClassFile getJavacClassFile(ClassSymbol clazz) {
		if (clazz.sourcefile == null) {
			return null;
		}
		final ICompilationUnit cu = this.fileObjectToCUMap.get(clazz.sourcefile);
		if (cu != null) {
			var result = this.results.get(cu);
			if (result != null) {
				var existing = Arrays.stream(result.getClassFiles())
					.filter(JavacClassFile.class::isInstance)
					.map(JavacClassFile.class::cast)
					.filter(other -> Objects.equals(other.fullName, clazz.flatName().toString()))
					.findAny()
					.orElse(null);
				if (existing != null) {
					return existing;
				}
			}
		}
		return new JavacClassFile(clazz.flatName().toString(),
				clazz.getEnclosingElement() instanceof ClassSymbol enclosing ? getJavacClassFile(enclosing) : null,
				computeOutputDirectory(cu),
				tempDir);
	}

	private boolean isGeneratedSource(JavaFileObject file) {
		List<IContainer> generatedSourcePaths = this.config.originalConfig().generatedSourcePaths();
		if (generatedSourcePaths == null || generatedSourcePaths.isEmpty()) {
			return false;
		}

		URI uri = file.toUri();
		if (uri != null && uri.getPath() != null) {
			File ioFile = new File(uri.getPath());
			Path fileIOPath = ioFile.toPath();
			return generatedSourcePaths.stream().anyMatch(container -> {
				IPath location = container.getRawLocation();
				if (location != null) {
					Path locationIOPath = location.toPath();
					return fileIOPath.startsWith(locationIOPath);
				}
				return false;
			});
		}
		return false;
	}

	private void writeClassFile(ClassSymbol clazz) throws CoreException {
		final ICompilationUnit cu = this.fileObjectToCUMap.get(clazz.sourcefile);
		String qualifiedName = clazz.flatName().toString().replace('.', '/');
		var javaClassFile = new JavacClassFile(qualifiedName, null, computeOutputDirectory(cu), tempDir);
		javaClassFile.flushTempToOutput();
	}

	@Override
	public void started(TaskEvent e) {
		this.javacCompiler.reportProgress(e.toString());
		TaskListener.super.started(e);
	}

	public Map<ICompilationUnit, JavacCompilationResult> getResults() {
		return this.results;
	}

	private IContainer computeOutputDirectory(ICompilationUnit unit) {
		if (unit instanceof SourceFile sf) {
			IContainer sourceDirectory = sf.resource.getParent();
			while (sourceDirectory != null) {
				IContainer mappedOutput = this.config.sourceOutputMapping().get(sourceDirectory);
				if (mappedOutput != null) {
					return mappedOutput;
				}
				sourceDirectory = sourceDirectory.getParent();
			}
		}
		return null;
	}
}
