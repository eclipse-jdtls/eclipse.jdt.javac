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

import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.search.FieldDeclarationMatch;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.compiler.lookup.ArrayBinding;
import org.eclipse.jdt.internal.core.search.DOMASTNodeUtils;
import org.eclipse.jdt.internal.core.search.LocatorResponse;

public class DOMFieldLocator extends DOMPatternLocator {

	private FieldLocator fieldLocator;

	public DOMFieldLocator(FieldLocator locator) {
		super(locator.pattern);
		this.fieldLocator = locator;
	}

	@Override
	public LocatorResponse match(org.eclipse.jdt.core.dom.ASTNode node, NodeSetWrapper nodeSet, MatchLocator locator) {
		int declarationsLevel = PatternLocator.IMPOSSIBLE_MATCH;
		if (node instanceof EnumConstantDeclaration enumConstant) {
			return match(enumConstant, nodeSet);
		}
		if (this.fieldLocator.pattern.findReferences) {
			if (node instanceof ImportDeclaration importRef) {
				// With static import, we can have static field reference in import reference
				if (importRef.isStatic() && !importRef.isOnDemand()
						&& this.fieldLocator.matchesName(this.fieldLocator.pattern.name,
								importRef.getName().toString().toCharArray())
						&& this.fieldLocator.pattern instanceof FieldPattern fieldPattern) {
					char[] declaringType = CharOperation.concat(fieldPattern.declaringQualification,
							fieldPattern.declaringSimpleName, '.');
					if (this.fieldLocator.matchesName(declaringType, importRef.getName().toString().toCharArray())) {
						declarationsLevel = getPossibleOrAccurateViaMustResolve();
					}
				}
			}
		}
		int level = nodeSet.addMatch(node, declarationsLevel);
		return toResponse(level, true);
	}

	@Override
	public LocatorResponse match(Name name, NodeSetWrapper nodeSet, MatchLocator locator) {
		if (name.getLocationInParent() == SingleVariableDeclaration.NAME_PROPERTY || name.getLocationInParent() == VariableDeclarationFragment.NAME_PROPERTY) {
			return toResponse(PatternLocator.IMPOSSIBLE_MATCH); // already caught by match(VariableDeclaration)
		}
		if (name.getLocationInParent() == MethodInvocation.NAME_PROPERTY) {
			return toResponse(IMPOSSIBLE_MATCH);
		}

		if (!this.fieldLocator.matchesName(this.fieldLocator.pattern.name, name.toString().toCharArray())) {
			return toResponse(PatternLocator.IMPOSSIBLE_MATCH);
		}
		if (this.fieldLocator.isDeclarationOfAccessedFieldsPattern
				&& this.fieldLocator.pattern instanceof DeclarationOfAccessedFieldsPattern doafp) {
			if (doafp.enclosingElement != null) {
				// we have an enclosing element to check
				return reportDeclarationOfAccessedFieldsPatternResult(name, doafp, locator);
			}
		}

		if (this.fieldLocator.pattern.fineGrain != 0 ? matchesFineGrain(name) :
			((this.fieldLocator.pattern.readAccess && DOMLocalVariableLocator.isRead(name))
			|| (this.fieldLocator.pattern.writeAccess && DOMLocalVariableLocator.isWrite(name)))) {
			int level = nodeSet.addMatch(name, getPossibleOrAccurateViaMustResolve());
			if( level != IMPOSSIBLE_MATCH ) {
				IBinding b = name.resolveBinding();
				if( b == null ) {
					if( name instanceof SimpleName sn && name.getParent() instanceof QualifiedName qn && sn == qn.getQualifier()) {
						boolean shadowed = methodHasShadowedVariable(name);
						if( shadowed )
							return toResponse(IMPOSSIBLE_MATCH);
					}
				}
			}
			return toResponse(level, true);
		}
		return toResponse(IMPOSSIBLE_MATCH);
	}

	private LocatorResponse reportDeclarationOfAccessedFieldsPatternResult(Name name,
			DeclarationOfAccessedFieldsPattern doafp, MatchLocator locator) {
		if (!DOMASTNodeUtils.isWithinRange(name, doafp.enclosingElement)) {
			return toResponse(PatternLocator.IMPOSSIBLE_MATCH);
		}
		// We need to report the declaration, not the usage
		// TODO testDeclarationOfAccessedFields2
		IBinding b = name.resolveBinding();
		IJavaElement je = b == null ? null : b.getJavaElement();
		if (je != null && doafp.knownFields.includes(je)) {
			doafp.knownFields.remove(je);
			ISourceReference sr = je instanceof ISourceReference ? (ISourceReference) je : null;
			IResource r = null;
			ISourceRange srg = null;
			String elName = je.getElementName();
			try {
				srg = sr.getSourceRange();
				IJavaElement ancestor = je.getAncestor(IJavaElement.COMPILATION_UNIT);
				r = ancestor == null ? null : ancestor.getCorrespondingResource();
			} catch (JavaModelException jme) {
				// ignore
			}
			if (srg != null) {
				int accuracy = getPossibleOrAccurateViaMustResolve();
				FieldDeclarationMatch fdMatch = new FieldDeclarationMatch(je, accuracy,
						srg.getOffset() + srg.getLength() - elName.length() - 1, elName.length(),
						locator.getParticipant(), r);
				try {
					locator.report(fdMatch);
				} catch (CoreException ce) {
					// ignore
				}
			}
		}
		return toResponse(PatternLocator.IMPOSSIBLE_MATCH);
	}

	private int getPossibleOrAccurateViaMustResolve() {
		return this.fieldLocator.pattern.mustResolve ? PatternLocator.POSSIBLE_MATCH : PatternLocator.ACCURATE_MATCH;
	}

	private boolean methodHasShadowedVariable(Name name) {
		int startPos = name.getStartPosition();
		String nameString = name.toString();
		Block b = findParentBlockUpToMethod(name);
		while( b != null ) {
			List<Statement> l = b.statements();
			for( Statement s : l ) {
				if( s.getStartPosition() > startPos )
					return false;
				if( s instanceof VariableDeclarationStatement vds ) {
					List<VariableDeclarationFragment> frags = vds.fragments();
					for( VariableDeclarationFragment frag : frags ) {
						Name varName = frag.getName();
						String varNameStr = varName == null ? null : varName.toString();
						if( nameString.equals(varNameStr)) {
							return true;
						}
					}
				}
			}
			b = findParentBlockUpToMethod(b);
		}
		return false;
	}

	private Block findParentBlockUpToMethod(ASTNode start) {
		ASTNode n = start;
		while(n != null ) {
			n = n.getParent();
			if( n instanceof Block b) {
				return b;
			}
			if( n instanceof MethodDeclaration) {
				return null;
			}
		}
		return null;
	}

	private boolean matchesFineGrain(Name name) {
		int fineGrain = this.fieldLocator.pattern.fineGrain;
		if (fineGrain == 0) {
			return true;
		}
		if ((fineGrain & IJavaSearchConstants.SUPER_REFERENCE) != 0 && name.getLocationInParent() == SuperFieldAccess.NAME_PROPERTY) {
			return true;
		}
		if ((fineGrain & IJavaSearchConstants.QUALIFIED_REFERENCE) != 0 && name.getLocationInParent() == QualifiedName.NAME_PROPERTY) {
			return true;
		}
		if (name.getLocationInParent() == FieldAccess.NAME_PROPERTY) {
			Expression expr = ((FieldAccess)name.getParent()).getExpression();
			if ((fineGrain & IJavaSearchConstants.THIS_REFERENCE) != 0 && expr instanceof ThisExpression) {
				return true;
			}
			if ((fineGrain & IJavaSearchConstants.QUALIFIED_REFERENCE) != 0 && expr != null && !(expr instanceof ThisExpression)) {
				return true;
			}
		}
		if ((fineGrain & IJavaSearchConstants.IMPLICIT_THIS_REFERENCE) != 0 &&
			!Set.of(SuperFieldAccess.NAME_PROPERTY, FieldAccess.NAME_PROPERTY, QualifiedName.NAME_PROPERTY).contains(name.getLocationInParent())) {
			return true;
		}
		return false;
	}

	@Override
	public LocatorResponse resolveLevel(org.eclipse.jdt.core.dom.ASTNode node, IBinding binding, MatchLocator locator) {
		if (binding == null)
			return toResponse(PatternLocator.ACCURATE_MATCH);
		if (binding instanceof IVariableBinding variableBinding) {
			if (variableBinding.isRecordComponent()) {
				// for matching the component in constructor of a record
				if (!this.fieldLocator.matchesName(this.fieldLocator.pattern.name,
						variableBinding.getName().toCharArray()))
					return toResponse(PatternLocator.IMPOSSIBLE_MATCH);
				FieldPattern fieldPattern = (FieldPattern) this.fieldLocator.pattern;
				// sometimes the binding is assumed as being a parameter (with a declaring class)
				// and sometimes it has a declaring class... This is a bad smell, more likely to
				// be fixed in the binding resolver, but in the meantime, let's try both
				IMethodBinding declaring = variableBinding == null ? null : variableBinding.getDeclaringMethod();
				ITypeBinding tb = declaring == null ? variableBinding.getDeclaringClass() : declaring.getDeclaringClass();
				int level = this.resolveLevelForType(fieldPattern.declaringSimpleName,
						fieldPattern.declaringQualification, tb);
				return toResponse(level);
			}
			if (variableBinding.isField()) {
				boolean mightMatch = false;
				if( this.fieldLocator.pattern.findDeclarations && node instanceof VariableDeclarationFragment vdf && vdf.getParent() instanceof FieldDeclaration) {
					mightMatch = true;
				}
				if(this.fieldLocator.pattern.readAccess && this.fieldLocator.pattern.writeAccess) {
					mightMatch = true;
				} else {
					ASTNode working = node instanceof Name n && n.getParent() instanceof QualifiedName qn ? qn : node;
					boolean isQualified = working == node ? false : true;
					boolean qualifierRead = working instanceof QualifiedName qn ? qn.getQualifier() == node : false;
					boolean isAssignNonQualified = working instanceof Name n
							&& n.getParent() instanceof Assignment assign
							&& assign.getLeftHandSide() == n;
					boolean isAssignQualified = working instanceof Name n && n.getParent() instanceof FieldAccess fa
							&& fa.getName() == n
							&& fa.getParent() instanceof Assignment assign
							&& assign.getLeftHandSide() == fa;
					boolean isAssign = isAssignNonQualified || isAssignQualified;
					boolean isPrefix = working instanceof Name n &&
							(n.getParent() instanceof PrefixExpression pe
								&& pe.getOperand() == n);
					boolean isPostfix = working instanceof Name n &&
							(n.getParent() instanceof PostfixExpression pe
									&& pe.getOperand() == n);

					boolean isWrite = (isAssign && !qualifierRead) || isPrefix || isPostfix;
					boolean isRead = !isAssign || qualifierRead || isPrefix || isPostfix;

					if( this.fieldLocator.pattern.writeAccess && isWrite) {
						mightMatch = true;
					}
					// Otherwise it's likely a read
					if( this.fieldLocator.pattern.readAccess && isRead ) {
						mightMatch = true;
					}

					if( (this.fieldLocator.pattern.fineGrain & IJavaSearchConstants.QUALIFIED_REFERENCE) != 0 ) {
						mightMatch |= isQualified;
					}
					if( (this.fieldLocator.pattern.fineGrain & IJavaSearchConstants.IMPLICIT_THIS_REFERENCE) != 0 ) {
						mightMatch |= !isQualified;
					}
					if( (this.fieldLocator.pattern.fineGrain & IJavaSearchConstants.SUPER_REFERENCE) != 0 ) {
						mightMatch |= working.getParent() instanceof SuperFieldAccess;
					}
					if( (this.fieldLocator.pattern.fineGrain & IJavaSearchConstants.THIS_REFERENCE) != 0 ) {
						mightMatch |= working.getParent() instanceof FieldAccess;
					}
				}
				if( mightMatch ) {
					return toResponse(this.matchField(variableBinding, true));
				}
			}
		}
		return toResponse(PatternLocator.IMPOSSIBLE_MATCH);
	}

	@Override
	public LocatorResponse match(VariableDeclaration node, NodeSetWrapper nodeSet, MatchLocator locator) {
		if (!this.fieldLocator.pattern.findDeclarations && !this.fieldLocator.isDeclarationOfAccessedFieldsPattern) {
			return toResponse(PatternLocator.IMPOSSIBLE_MATCH);
		}
		if (node.getLocationInParent() != org.eclipse.jdt.core.dom.FieldDeclaration.FRAGMENTS_PROPERTY) {
			return toResponse(PatternLocator.IMPOSSIBLE_MATCH);
		}
		int referencesLevel = PatternLocator.IMPOSSIBLE_MATCH;
		if (this.fieldLocator.pattern.findReferences)
			// must be a write only access with an initializer
			if (this.fieldLocator.pattern.writeAccess && !this.fieldLocator.pattern.readAccess
					&& node.getInitializer() != null)
				if (this.fieldLocator.matchesName(this.fieldLocator.pattern.name,
						node.getName().getIdentifier().toCharArray()))
					referencesLevel = getPossibleOrAccurateViaMustResolve();

		int declarationsLevel = PatternLocator.IMPOSSIBLE_MATCH;
		if ((this.fieldLocator.pattern.findDeclarations || this.fieldLocator.isDeclarationOfAccessedFieldsPattern)
				&& this.fieldLocator.matchesName(this.fieldLocator.pattern.name,
						node.getName().getIdentifier().toCharArray())
				&& this.fieldLocator.pattern instanceof FieldPattern fieldPattern
				&& this.matchesTypeReference(fieldPattern.typeSimpleName,
						((org.eclipse.jdt.core.dom.FieldDeclaration) node.getParent()).getType())) {
			declarationsLevel = getPossibleOrAccurateViaMustResolve();
		}
		// use the stronger match
		int level = nodeSet.addMatch(node, referencesLevel >= declarationsLevel ? referencesLevel : declarationsLevel);
		return toResponse(level, true);
	}

	private LocatorResponse match(EnumConstantDeclaration node, NodeSetWrapper nodeSet) {
		int referencesLevel = PatternLocator.IMPOSSIBLE_MATCH;
		if (this.fieldLocator.pattern.findReferences)
			// must be a write only access with an initializer
			if (this.fieldLocator.pattern.writeAccess && !this.fieldLocator.pattern.readAccess)
				if (this.fieldLocator.matchesName(this.fieldLocator.pattern.name,
						node.getName().getIdentifier().toCharArray()))
					referencesLevel = getPossibleOrAccurateViaMustResolve();

		int declarationsLevel = PatternLocator.IMPOSSIBLE_MATCH;
		if (this.fieldLocator.pattern.findDeclarations
				&& this.fieldLocator.matchesName(this.fieldLocator.pattern.name,
						node.getName().getIdentifier().toCharArray())
				&& this.fieldLocator.pattern instanceof FieldPattern fieldPattern
				&& this.fieldLocator.matchesName(fieldPattern.typeSimpleName,
						((EnumDeclaration) node.getParent()).getName().getIdentifier().toCharArray())) {
			declarationsLevel = getPossibleOrAccurateViaMustResolve();
		}
		// use the stronger match
		int level = nodeSet.addMatch(node, referencesLevel >= declarationsLevel ? referencesLevel : declarationsLevel);
		return toResponse(level, true);
	}

	protected int matchField(IVariableBinding field, boolean matchName) {
		if (field == null)
			return INACCURATE_MATCH;
		if (!field.isField())
			return IMPOSSIBLE_MATCH;

		if (matchName && !this.fieldLocator.matchesName(this.fieldLocator.pattern.name, field.getName().toCharArray()))
			return IMPOSSIBLE_MATCH;

		FieldPattern fieldPattern = (FieldPattern) this.fieldLocator.pattern;
		ITypeBinding receiverBinding = field.getDeclaringClass();
		if (receiverBinding == null) {
			if (field == ArrayBinding.ArrayLength)
				// optimized case for length field of an array
				return fieldPattern.declaringQualification == null && fieldPattern.declaringSimpleName == null
						? ACCURATE_MATCH
						: IMPOSSIBLE_MATCH;
			int mode = fieldPattern.getMatchMode();
			if (mode == SearchPattern.R_EXACT_MATCH) {
				return IMPOSSIBLE_MATCH;
			}
			return INACCURATE_MATCH;
		}

		// Note there is no dynamic lookup for field access
		int declaringLevel = this.resolveLevelForType(fieldPattern.declaringSimpleName,
				fieldPattern.declaringQualification, receiverBinding);
		if (declaringLevel == IMPOSSIBLE_MATCH)
			return IMPOSSIBLE_MATCH;

		// look at field type only if declaring type is not specified
		if (fieldPattern.declaringSimpleName == null) {
			if (this.fieldLocator.isDeclarationOfAccessedFieldsPattern
					&& this.fieldLocator.pattern instanceof DeclarationOfAccessedFieldsPattern doafp) {
				IJavaElement je = field.getJavaElement();
				if (je != null) {
					doafp.knownFields.add(je);
				}
			} else {
				return declaringLevel;
			}
			return IMPOSSIBLE_MATCH;
		}

		int typeLevel = resolveLevelForType(field.getType());
		int ret = declaringLevel > typeLevel ? typeLevel : declaringLevel; // return the weaker match
		if (this.fieldLocator.isDeclarationOfAccessedFieldsPattern
				&& this.fieldLocator.pattern instanceof DeclarationOfAccessedFieldsPattern doafp) {
			IJavaElement je = field.getJavaElement();
			if (je != null) {
				doafp.knownFields.add(je);
			}
		} else {
			return ret;
		}
		return IMPOSSIBLE_MATCH;
	}

	protected int resolveLevelForType(ITypeBinding typeBinding) {
		FieldPattern fieldPattern = (FieldPattern) this.fieldLocator.pattern;
		ITypeBinding fieldTypeBinding = typeBinding;
		if (fieldTypeBinding != null && fieldTypeBinding.isParameterizedType()) {
			fieldTypeBinding = typeBinding.getErasure();
		}
		int fieldNameMatch = this.resolveLevelForType(fieldPattern.typeSimpleName,
				fieldPattern.typeQualification, fieldTypeBinding);
		return fieldNameMatch;
	}
}
