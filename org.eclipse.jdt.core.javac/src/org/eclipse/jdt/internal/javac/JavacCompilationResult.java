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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;

public class JavacCompilationResult extends CompilationResult {
	private Set<String[]> javacQualifiedReferences = new TreeSet<>((a, b) -> Arrays.compare(a, b));
	private Set<String> javacSimpleNameReferences = new TreeSet<>();
	private Set<String> javacRootReferences = new TreeSet<>();
	private boolean isMigrated = false;
	private List<CategorizedProblem> unusedMembers = null;
	private List<CategorizedProblem> unusedLocalVariables = null;
	private List<CategorizedProblem> unusedImports = null;
	private List<CategorizedProblem> unnecessaryCasts = null;
	private List<CategorizedProblem> noEffectAssignments = null;
	private List<CategorizedProblem> unclosedCloseables = null;
	private List<CategorizedProblem> unusedTypeParameters = null;
	private List<CategorizedProblem> unnecessaryElse = null;
	private List<CategorizedProblem> accessRestrictionProblems = null;
	private List<CategorizedProblem> indirectStaticAccessProblems = null;
	private List<CategorizedProblem> unqualifiedFieldAccessProblems = null;
	private List<CategorizedProblem> deadCodeProblems = null;
	private List<CategorizedProblem> redundantNullAnnotationProblems = null;
	private List<CategorizedProblem> potentialNullReferenceProblems = null;

	public JavacCompilationResult(ICompilationUnit compilationUnit) {
		this(compilationUnit, 0, 0, Integer.MAX_VALUE);
	}

	public JavacCompilationResult(ICompilationUnit compilationUnit, int unitIndex, int totalUnitsKnown,
			int maxProblemPerUnit) {
		super(compilationUnit, unitIndex, totalUnitsKnown, maxProblemPerUnit);
	}

	public boolean addQualifiedReference(String[] qualifiedReference) {
		return this.javacQualifiedReferences.add(qualifiedReference);
	}

	public boolean addSimpleNameReference(String simpleNameReference) {
		return this.javacSimpleNameReferences.add(simpleNameReference);
	}

	public boolean addRootReference(String rootReference) {
		return this.javacRootReferences.add(rootReference);
	}

	public void migrateReferenceInfo() {
		if (isMigrated) {
			return;
		}

		this.simpleNameReferences = this.javacSimpleNameReferences.stream().map(String::toCharArray).toArray(char[][]::new);
		this.rootReferences = this.javacRootReferences.stream().map(String::toCharArray).toArray(char[][]::new);
		this.qualifiedReferences = this.javacQualifiedReferences.stream().map(qualifiedNames -> {
			// convert String[] to char[][]
			return Stream.of(qualifiedNames).map(String::toCharArray).toArray(char[][]::new);
		}).toArray(char[][][]::new);

		this.javacSimpleNameReferences.clear();
		this.javacRootReferences.clear();
		this.javacQualifiedReferences.clear();
		this.isMigrated = true;
	}

	public void addUnusedImports(List<CategorizedProblem> problems) {
		if (this.unusedImports == null) {
			this.unusedImports = new ArrayList<>();
		}
		this.unusedImports.addAll(problems);
	}

	public void addUnusedMembers(List<CategorizedProblem> problems) {
		if (this.unusedMembers == null) {
			this.unusedMembers = new ArrayList<>();
		}
		this.unusedMembers.addAll(problems);
	}

	public void addUnusedLocalVariables(List<CategorizedProblem> problems) {
		if (this.unusedLocalVariables == null) {
			this.unusedLocalVariables = new ArrayList<>();
		}
		this.unusedLocalVariables.addAll(problems);
	}

	public void addUnnecessaryCasts(List<CategorizedProblem> problems) {
		if (this.unnecessaryCasts == null) {
			this.unnecessaryCasts = new ArrayList<>();
		}
		this.unnecessaryCasts.addAll(problems);
	}

	public void addNoEffectAssignments(List<CategorizedProblem> problems) {
		if (this.noEffectAssignments == null) {
			this.noEffectAssignments = new ArrayList<>();
		}
		this.noEffectAssignments.addAll(problems);
	}

	public void addUnclosedCloseables(List<CategorizedProblem> problems) {
		if (this.unclosedCloseables == null) {
			this.unclosedCloseables = new ArrayList<>();
		}
		this.unclosedCloseables.addAll(problems);
	}

	public void addUnusedTypeParameters(List<CategorizedProblem> problems) {
		if (this.unusedTypeParameters == null) {
			this.unusedTypeParameters = new ArrayList<>();
		}
		this.unusedTypeParameters.addAll(problems);
	}

	public void addUnnecessaryElse(List<CategorizedProblem> problems) {
		if (this.unnecessaryElse == null) {
			this.unnecessaryElse = new ArrayList<>();
		}
		this.unnecessaryElse.addAll(problems);
	}

	public void addAccessRestrictionProblems(List<CategorizedProblem> problems) {
		if (this.accessRestrictionProblems == null) {
			this.accessRestrictionProblems = new ArrayList<>();
		}
		this.accessRestrictionProblems.addAll(problems);
	}

	public void addIndirectStaticAccessProblems(List<CategorizedProblem> problems) {
		if (this.indirectStaticAccessProblems == null) {
			this.indirectStaticAccessProblems = new ArrayList<>();
		}
		this.indirectStaticAccessProblems.addAll(problems);
	}

	public void addUnqualifiedFieldAccessProblems(List<CategorizedProblem> problems) {
		if (this.unqualifiedFieldAccessProblems == null) {
			this.unqualifiedFieldAccessProblems = new ArrayList<>();
		}
		this.unqualifiedFieldAccessProblems.addAll(problems);
	}

	public void addDeadCodeProblems(List<CategorizedProblem> problems) {
		if (this.deadCodeProblems == null) {
			this.deadCodeProblems = new ArrayList<>();
		}
		this.deadCodeProblems.addAll(problems);
	}

	public void addRedundantNullAnnotationProblems(List<CategorizedProblem> problems) {
		if (this.redundantNullAnnotationProblems == null) {
			this.redundantNullAnnotationProblems = new ArrayList<>();
		}
		this.redundantNullAnnotationProblems.addAll(problems);
	}

	public void addPotentialNullReferenceProblems(List<CategorizedProblem> problems) {
		if (this.potentialNullReferenceProblems == null) {
			this.potentialNullReferenceProblems = new ArrayList<>();
		}
		this.potentialNullReferenceProblems.addAll(problems);
	}

	public List<CategorizedProblem> getAdditionalProblems() {
		if (this.unusedMembers == null
				&& this.unusedLocalVariables == null
				&& this.unusedImports == null
				&& this.unnecessaryCasts == null
				&& this.noEffectAssignments == null
				&& this.unclosedCloseables == null
				&& this.unusedTypeParameters == null
				&& this.unnecessaryElse == null
				&& this.accessRestrictionProblems == null
				&& this.indirectStaticAccessProblems == null
				&& this.unqualifiedFieldAccessProblems == null
				&& this.deadCodeProblems == null
				&& this.redundantNullAnnotationProblems == null
				&& this.potentialNullReferenceProblems == null) {
			return null;
		}

		List<CategorizedProblem> problems = new ArrayList<>();
		if (this.unusedImports != null) {
			problems.addAll(this.unusedImports);
		}
		if (this.unusedMembers != null) {
			problems.addAll(this.unusedMembers);
		}
		if (this.unusedLocalVariables != null) {
			problems.addAll(this.unusedLocalVariables);
		}
		if (this.unnecessaryCasts != null) {
			problems.addAll(this.unnecessaryCasts);
		}
		if (this.noEffectAssignments != null) {
			problems.addAll(this.noEffectAssignments);
		}
		if (this.unclosedCloseables != null) {
			problems.addAll(this.unclosedCloseables);
		}
		if (this.unusedTypeParameters != null) {
			problems.addAll(this.unusedTypeParameters);
		}
		if (this.unnecessaryElse != null) {
			problems.addAll(this.unnecessaryElse);
		}
		if (this.accessRestrictionProblems != null) {
			problems.addAll(this.accessRestrictionProblems);
		}
		if (this.indirectStaticAccessProblems != null) {
			problems.addAll(this.indirectStaticAccessProblems);
		}
		if (this.unqualifiedFieldAccessProblems != null) {
			problems.addAll(this.unqualifiedFieldAccessProblems);
		}
		if (this.deadCodeProblems != null) {
			problems.addAll(this.deadCodeProblems);
		}
		if (this.redundantNullAnnotationProblems != null) {
			problems.addAll(this.redundantNullAnnotationProblems);
		}
		if (this.potentialNullReferenceProblems != null) {
			problems.addAll(this.potentialNullReferenceProblems);
		}
		return problems;
	}
}
