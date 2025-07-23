/*******************************************************************************
 * Copyright (c) 2023, 2024 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.internal.core.search.matching;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IModuleBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.search.JavaSearchParticipant;
import org.eclipse.jdt.internal.core.search.LocatorResponse;
import org.eclipse.jdt.internal.core.search.indexing.IIndexConstants;

public class DOMTypeDeclarationLocator extends DOMPatternLocator {

	private TypeDeclarationLocator locator;

	public DOMTypeDeclarationLocator(TypeDeclarationLocator locator) {
		super(locator.pattern);
		this.locator = locator;
	}
	@Override
	public LocatorResponse match(AbstractTypeDeclaration node, NodeSetWrapper nodeSet, MatchLocator locator) {
		if (!matchSearchForTypeSuffix(node, this.locator.pattern.typeSuffix)) {
			return toResponse(IMPOSSIBLE_MATCH);
		}
		if (this.locator.pattern.simpleName == null || this.locator.matchesName(this.locator.pattern.simpleName, node.getName().getIdentifier().toCharArray())) {
			int level = nodeSet.addMatch(node, this.locator.pattern.mustResolve ? POSSIBLE_MATCH : ACCURATE_MATCH);
			return toResponse(level, true);
		}

		return toResponse(IMPOSSIBLE_MATCH);
	}
	@Override
	public LocatorResponse match(LambdaExpression node, NodeSetWrapper nodeSet, MatchLocator locator) {
		return toResponse(POSSIBLE_MATCH);
	}

	static boolean matchSearchForTypeSuffix(AbstractTypeDeclaration type, char typeSuffix) {
		return switch (typeSuffix) {
			case IIndexConstants.CLASS_SUFFIX -> type instanceof TypeDeclaration decl && !decl.isInterface();
			case IIndexConstants.CLASS_AND_INTERFACE_SUFFIX -> type instanceof TypeDeclaration;
			case IIndexConstants.CLASS_AND_ENUM_SUFFIX -> (type instanceof TypeDeclaration decl && !decl.isInterface()) || type instanceof EnumDeclaration;
			case IIndexConstants.INTERFACE_SUFFIX -> type instanceof TypeDeclaration decl && decl.isInterface();
			case IIndexConstants.INTERFACE_AND_ANNOTATION_SUFFIX -> (type instanceof TypeDeclaration decl && !decl.isInterface()) || type instanceof AnnotationTypeDeclaration;
			case IIndexConstants.ENUM_SUFFIX -> type instanceof EnumDeclaration;
			case IIndexConstants.ANNOTATION_TYPE_SUFFIX -> type instanceof AnnotationTypeDeclaration;
			case IIndexConstants.TYPE_SUFFIX -> true;
			default -> false;
		};
	}

	static boolean matchSearchForTypeSuffix(ITypeBinding type, char typeSuffix) {
		return switch (typeSuffix) {
			case IIndexConstants.CLASS_SUFFIX -> type.isClass();
			case IIndexConstants.CLASS_AND_INTERFACE_SUFFIX -> type.isClass() || (type.isInterface() && !type.isAnnotation());
			case IIndexConstants.CLASS_AND_ENUM_SUFFIX -> type.isClass() || type.isEnum();
			case IIndexConstants.INTERFACE_SUFFIX -> type.isInterface() && !type.isAnnotation();
			case IIndexConstants.INTERFACE_AND_ANNOTATION_SUFFIX -> type.isInterface() || type.isAnnotation();
			case IIndexConstants.ENUM_SUFFIX -> type.isEnum();
			case IIndexConstants.ANNOTATION_TYPE_SUFFIX -> type.isAnnotation();
			case IIndexConstants.TYPE_SUFFIX -> true;
			default -> false;
		};
	}

	@Override
	public LocatorResponse resolveLevel(org.eclipse.jdt.core.dom.ASTNode node, IBinding binding, MatchLocator locator) {
		if (binding == null) return toResponse(INACCURATE_MATCH);
		if (!(binding instanceof ITypeBinding type)) return toResponse(IMPOSSIBLE_MATCH);

		if (!matchSearchForTypeSuffix(type, this.locator.pattern.typeSuffix)) {
			return toResponse(IMPOSSIBLE_MATCH);
		}

		if (this.matchModule(this.locator.pattern, type) == IMPOSSIBLE_MATCH) {
			return toResponse(IMPOSSIBLE_MATCH);
		}
		// fully qualified name
		if (this.locator.pattern instanceof QualifiedTypeDeclarationPattern) {
			QualifiedTypeDeclarationPattern qualifiedPattern = (QualifiedTypeDeclarationPattern) this.locator.pattern;
			int level = this.resolveLevelForType(qualifiedPattern.simpleName, qualifiedPattern.qualification, type);
			return toResponse(level);
		} else {
			char[] enclosingTypeName = this.locator.pattern.enclosingTypeNames == null ? null : CharOperation.concatWith(this.locator.pattern.enclosingTypeNames, '.');
			int level = resolveLevelForType(this.locator.pattern.simpleName, this.locator.pattern.pkg, enclosingTypeName, type);
			return toResponse(level);
		}
	}
	protected int resolveLevelForType(char[] simpleNamePattern, char[] qualificationPattern, char[] enclosingNamePattern, ITypeBinding type) {
		if (enclosingNamePattern == null)
			return this.resolveLevelForType(simpleNamePattern, qualificationPattern, type);
		if (qualificationPattern == null)
			return this.resolveLevelForType(simpleNamePattern, enclosingNamePattern, type);

		// pattern was created from a Java element: qualification is the package name.
		char[] fullQualificationPattern = CharOperation.concat(qualificationPattern, enclosingNamePattern, '.');
		if (CharOperation.equals(this.locator.pattern.pkg, type.getPackage().getName().toCharArray()))
			return this.resolveLevelForType(simpleNamePattern, fullQualificationPattern, type);
		return IMPOSSIBLE_MATCH;
	}
	private int matchModule(TypeDeclarationPattern typePattern, ITypeBinding type) {
		IModuleBinding module = type.getModule();
		if (module == null || module.getName() == null || typePattern.moduleNames == null)
			return POSSIBLE_MATCH; //can't determine, say possible to all.
		String bindModName = module.getName();

		if (typePattern.modulePatterns == null) {// use 'normal' matching
			char[][] moduleList = getModuleList(typePattern);
			for (char[] m : moduleList) { // match any in the list
				int ret = this.locator.matchNameValue(m, bindModName.toCharArray());
				if (ret != IMPOSSIBLE_MATCH) return ret;
			}
		} else {// use pattern matching
			for (Pattern p : typePattern.modulePatterns) {
				Matcher matcher = p.matcher(bindModName);
				if (matcher.matches()) return ACCURATE_MATCH;
			}
		}
		return IMPOSSIBLE_MATCH;
	}

	private HashSet<String> getModuleGraph(String mName, TypeDeclarationPattern typePattern, HashSet<String> mGraph) {
		mGraph.add(mName);
		SearchPattern modulePattern = SearchPattern.createPattern(mName,
				IJavaSearchConstants.MODULE, IJavaSearchConstants.DECLARATIONS, typePattern.getMatchRule());
		if (modulePattern == null) return mGraph;
		final HashSet<String> tmpGraph = new HashSet<>();
		final SearchParticipant participant = new JavaSearchParticipant() {
			@Override
			public void locateMatches(SearchDocument[] indexMatches, SearchPattern mPattern,
					IJavaSearchScope scope, SearchRequestor requestor, IProgressMonitor monitor) throws CoreException {
				DOMMatchLocator matchLocator =	new DOMMatchLocator(mPattern,	requestor,	scope,	monitor);
				/* eliminating false matches and locating them */
				if (monitor != null && monitor.isCanceled()) throw new OperationCanceledException();
				matchLocator.locateMatches(indexMatches);
				addRequiredModules(matchLocator);
			}
			private void addRequiredModules(DOMMatchLocator matchLocator) {
				for (IModuleBinding m :matchLocator.moduleBindings) {
					if (m.getName() != null && !m.getName().isEmpty()) {
						tmpGraph.add(new String(m.getName()));
						for (IModuleBinding r : m.getRequiredModules()) {
							char[] name = r.getName().toCharArray();
							if (name == null || CharOperation.equals(name, CharOperation.NO_CHAR)) continue;
							tmpGraph.add(new String(name));
						}
					}
				}
			}
		};
		final SearchRequestor requestor = new SearchRequestor() {
			@Override
			public void acceptSearchMatch(SearchMatch searchMatch) throws CoreException {
				// do nothing
				if (JavaModelManager.VERBOSE) {
					JavaModelManager.trace(searchMatch.toString());
				}
			}
		};
		try {
			new SearchEngine().search(modulePattern, new SearchParticipant[] {participant},
					JavaModelManager.getJavaModelManager().getWorkspaceScope(),
					requestor,	null);
		} catch (CoreException e) {
			// do nothing
		}
		mGraph.addAll(tmpGraph);
		return mGraph;
	}
	protected char[][] getModuleList(TypeDeclarationPattern typePattern) {
		if (!typePattern.moduleGraph)
			return typePattern.moduleNames;
		if (typePattern.moduleGraphElements != null) // already computed
			return typePattern.moduleGraphElements;
		typePattern.moduleGraphElements = CharOperation.NO_CHAR_CHAR; // signal processing done.
		// compute (lazy)
		List<String> moduleList = Arrays.asList(CharOperation.toStrings(typePattern.moduleNames));
		int sz = moduleList.size();
		HashSet<String> mGraph = new HashSet<>();
		for (int i = 0; i < sz; ++i) {
			mGraph = getModuleGraph(moduleList.get(i), typePattern, mGraph);
		}
		sz = mGraph.size();
		if (sz > 0) {
			String[] ar = mGraph.toArray(new String[0]);
			char[][] tmp = new char[sz][];
			for (int i = 0; i < sz; ++i) {
				tmp[i] = ar[i].toCharArray();
			}
			typePattern.moduleGraphElements = tmp;
		}
		return typePattern.moduleGraphElements;
	}
}
