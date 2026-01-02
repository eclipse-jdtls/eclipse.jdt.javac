/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.internal.core.search;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.ModuleDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

public class DOMASTNodeUtils {

	/**
	 * @param node
	 * @return an existing (in model) Java element enclosing the node
	 */
	public static IJavaElement getEnclosingJavaElement(ASTNode node) {
		if (node == null) {
			return null;
		}
		ASTNode enclosing = getEnclosingJavaElementNode(node);
		if( enclosing != null ) {
			return getDeclaringJavaElement(enclosing);
		}
		return null;
	}

	public static ASTNode getEnclosingJavaElementNode(ASTNode node) {
		if (node == null) {
			return null;
		}
		if (node instanceof AbstractTypeDeclaration
			|| node instanceof MethodDeclaration
			|| node instanceof FieldDeclaration
			|| node instanceof Initializer
			|| node instanceof ImportDeclaration
			|| node instanceof PackageDeclaration
			|| node instanceof CompilationUnit
			|| node instanceof ModuleDeclaration
			|| node instanceof AnnotationTypeMemberDeclaration
			|| node instanceof Initializer
			|| node instanceof LambdaExpression
			|| node.getLocationInParent() == FieldDeclaration.FRAGMENTS_PROPERTY
			|| node.getLocationInParent() == RecordDeclaration.RECORD_COMPONENTS_PROPERTY) {
			return node;
		}
		if (node instanceof ClassInstanceCreation newInst && newInst.getAnonymousClassDeclaration() != null) {
			return (newInst.getAnonymousClassDeclaration());
		}
		return getEnclosingJavaElementNode(node.getParent());
	}



	public static boolean annotationBetweenNodeAndLocalElement(ASTNode node) {
		ASTNode working = node;
		if( node == null )
			return false;
		boolean annotFound = false;
		while(working != null ) {
			annotFound |= working instanceof Annotation;
			if (node instanceof VariableDeclaration )
				return annotFound;
			if (node instanceof VariableDeclarationStatement )
				return annotFound;
			if(node instanceof VariableDeclarationExpression)
				return annotFound;
			if (node instanceof TypeParameter typeParam)
				return annotFound;
			if (node instanceof AbstractTypeDeclaration
					|| node instanceof MethodDeclaration
					|| node instanceof FieldDeclaration
					|| node instanceof Initializer
					|| node instanceof ImportDeclaration
					|| node instanceof PackageDeclaration
					|| node instanceof CompilationUnit
					|| node instanceof AnnotationTypeMemberDeclaration
					|| node instanceof Initializer
					|| node instanceof LambdaExpression
					|| node.getLocationInParent() == FieldDeclaration.FRAGMENTS_PROPERTY)
				return annotFound;
			working = working.getParent();
		}
		return annotFound;
	}

	/**
	 * @param node
	 * @return an existing (in model) Java element enclosing the node
	 */
	public static IJavaElement getLocalJavaElement(ASTNode node) {
		IJavaElement[] r = getLocalOrOtherJavaElements(node, true);
		return r != null && r.length > 0 && r[0] != null ? r[0] : null;
	}

	public static IJavaElement[] getLocalOrOtherJavaElements(ASTNode node, boolean local) {
		if (node == null) {
			return null;
		}
		if (node instanceof VariableDeclaration variable) {
			IVariableBinding vb = variable.resolveBinding();
			if( vb != null )
				return new IJavaElement[] {vb.getJavaElement()};
		}
		if (node instanceof VariableDeclarationStatement variable && !variable.fragments().isEmpty()) {
			if( local ) {
				IVariableBinding vb =  ((List<VariableDeclarationFragment>)variable.fragments()).iterator().next().resolveBinding();
				if( vb != null )
					return new IJavaElement[] {vb.getJavaElement()};
			} else {
				ArrayList<IJavaElement> ret = new ArrayList<>();
				List<VariableDeclarationFragment> l1 = variable.fragments();
				boolean first = true;
				for( VariableDeclarationFragment v1 : l1 ) {
					if( first ) {
						first = false;
						continue;
					}
					IVariableBinding vb = v1.resolveBinding();
					if( vb != null ) {
						IJavaElement e = vb.getJavaElement();
						if( e != null ) {
							ret.add(e);
						}
					}
				}
				return ret.toArray(new IJavaElement[ret.size()]);
			}
		}
		if (node instanceof VariableDeclarationExpression variable && !variable.fragments().isEmpty()) {
			if( local ) {
				IVariableBinding vb =  ((List<VariableDeclarationFragment>)variable.fragments()).iterator().next().resolveBinding();
				if( vb != null )
					return new IJavaElement[] {vb.getJavaElement()};
			} else {
				ArrayList<IJavaElement> ret = new ArrayList<>();
				List<VariableDeclarationFragment> l1 = variable.fragments();
				boolean first = true;
				for( VariableDeclarationFragment v1 : l1 ) {
					if( first ) {
						first = false;
						continue;
					}
					IVariableBinding vb = v1.resolveBinding();
					if( vb != null ) {
						IJavaElement e = vb.getJavaElement();
						if( e != null ) {
							ret.add(e);
						}
					}
				}
				return ret.toArray(new IJavaElement[ret.size()]);
			}
		}

        if (!local
        		&& (node.getLocationInParent() == FieldDeclaration.TYPE_PROPERTY
        			|| node.getLocationInParent() == FieldDeclaration.MODIFIERS_PROPERTY
        			|| node.getLocationInParent() == FieldDeclaration.MODIFIERS2_PROPERTY)
        		&& node.getParent() instanceof FieldDeclaration stmt && stmt.fragments().size() > 1) {
            return ((List<VariableDeclarationFragment>)stmt.fragments())
                            .stream()
                            .map(VariableDeclarationFragment::resolveBinding)
                            .filter(x -> x != null)
                            .map(IVariableBinding::getJavaElement)
                            .filter(x -> x != null)
                            .toArray(IJavaElement[]::new);
        }

		if (node instanceof TypeParameter typeParam) {
			return new IJavaElement[] { typeParam.resolveBinding().getJavaElement()};
		}
		if (node instanceof AbstractTypeDeclaration
			|| node instanceof MethodDeclaration
			|| node instanceof FieldDeclaration
			|| node instanceof Initializer
			|| node instanceof ImportDeclaration
			|| node instanceof PackageDeclaration
			|| node instanceof CompilationUnit
			|| node instanceof AnnotationTypeMemberDeclaration
			|| node instanceof Initializer
			|| node instanceof LambdaExpression
			|| node.getLocationInParent() == FieldDeclaration.FRAGMENTS_PROPERTY) {
			return new IJavaElement[] { getEnclosingJavaElement(node) };
		}
		return getLocalOrOtherJavaElements(node.getParent(), local);
	}

	public static IJavaElement getDeclaringJavaElement(ASTNode key) {
		if (key instanceof CompilationUnit unit) {
			return unit.getJavaElement();
		}
		IJavaElement je = findElementForNodeViaDirectBinding(key);
		if( je != null ) {
			return je;
		}
		IJavaElement je2 = findElementForNodeCustom(key);
		return je2;
	}

	private static IJavaElement findElementForNodeCustom(ASTNode key) {
		if( key instanceof FieldDeclaration fd ) {
			List<?> fragments = fd.fragments();
			if( fragments.size() > 0 ) {
				VariableDeclarationFragment vdf = (VariableDeclarationFragment)fragments.get(0);
				if( vdf != null ) {
					IJavaElement ret = findElementForNodeViaDirectBinding(vdf);
					return ret;
				}
			}
		}
		if( key instanceof Initializer i) {
			ASTNode parentNode = i.getParent();
			int domOccurance = -1;
			if( parentNode instanceof AbstractTypeDeclaration typeDecl) {
				domOccurance = typeDecl.bodyDeclarations()
						.stream()
						.filter(Initializer.class::isInstance)
						.toList()
						.indexOf(key) + 1;
			}
			IJavaElement parentEl = findElementForNodeViaDirectBinding(parentNode);
			if( parentEl instanceof IParent parentElement) {
				try {
					for (IJavaElement child : parentElement.getChildren()) {
						if (child instanceof IInitializer init) {
							int count = init.getOccurrenceCount();
							if( count == domOccurance ) {
								return init;
							}
						}
					}
				} catch( JavaModelException jme) {
					// ignore
				}
			}
		}
		if( key instanceof ImportDeclaration id) {
			ASTNode parentNode = id.getParent();
			if( parentNode instanceof CompilationUnit unit) {
				IJavaElement parentEl = unit.getJavaElement();
				return ((org.eclipse.jdt.internal.core.CompilationUnit) parentEl).getImport(id.getName().toString());
			}
		}
		if (key instanceof PackageDeclaration pack && pack.getParent() instanceof CompilationUnit unit) {
			IJavaElement parentEl = unit.getJavaElement();
			return ((org.eclipse.jdt.internal.core.CompilationUnit) parentEl).getPackageDeclaration(pack.getName().getFullyQualifiedName());
		}
		return null;
	}

	private static IJavaElement findElementForNodeViaDirectBinding(ASTNode key) {
		if( key != null ) {
			IBinding b = DOMASTNodeUtils.getBinding(key);
			if( b != null ) {
				IJavaElement el = b.getJavaElement();
				return el;
			}
		}
		return null;
	}

	public static IBinding getBinding(ASTNode astNode) {
		if (astNode instanceof Name name) {
			return name.resolveBinding();
		}
		if (astNode instanceof VariableDeclaration variable) {
			return variable.resolveBinding();
		}
		if (astNode instanceof EnumConstantDeclaration enumConstantDeclaration) {
			return enumConstantDeclaration.resolveVariable();
		}
		if (astNode instanceof FieldAccess fieldAcces) {
			return fieldAcces.resolveFieldBinding();
		}
		if (astNode instanceof MethodInvocation method) {
			return method.resolveMethodBinding();
		}
		if (astNode instanceof Type type) {
			return type.resolveBinding();
		}
		if (astNode instanceof AbstractTypeDeclaration type) {
			return type.resolveBinding();
		}
		if (astNode instanceof MethodDeclaration method) {
			return method.resolveBinding();
		}
		if (astNode instanceof FieldDeclaration field && !field.fragments().isEmpty()) {
			return ((List<VariableDeclarationFragment>)field.fragments()).get(0).resolveBinding();
		}
		if (astNode instanceof SuperFieldAccess superField) {
			return superField.resolveFieldBinding();
		}
		if (astNode instanceof SuperMethodInvocation superMethod) {
			return superMethod.resolveMethodBinding();
		}
		if (astNode instanceof SuperMethodReference superRef) {
			return superRef.resolveMethodBinding();
		}
		if (astNode instanceof ConstructorInvocation superRef) {
			return superRef.resolveConstructorBinding();
		}
		if (astNode instanceof SuperConstructorInvocation superRef) {
			return superRef.resolveConstructorBinding();
		}
		if (astNode instanceof MethodRef methodRef) {
			return methodRef.resolveBinding();
		}
		if (astNode instanceof MethodReference methodRef) {
			return methodRef.resolveMethodBinding();
		}
		if (astNode instanceof AnnotationTypeMemberDeclaration methodRef) {
			return methodRef.resolveBinding();
		}
		if (astNode instanceof ClassInstanceCreation ref) {
			return ref.resolveConstructorBinding();
		}
		if (astNode instanceof TypeParameter ref) {
			return ref.resolveBinding();
		}
		if (astNode instanceof MemberValuePair ref) {
			return ref.resolveMemberValuePairBinding();
		}
		if (astNode instanceof ModuleDeclaration ref) {
			return ref.resolveBinding();
		}
		if (astNode instanceof LambdaExpression lambda) {
			return lambda.resolveMethodBinding();
		}
		if (astNode instanceof AnonymousClassDeclaration anon) {
			return anon.resolveBinding();
		}
		// TODO more...
		return null;
	}

	public static boolean insideDocComment(org.eclipse.jdt.core.dom.ASTNode node) {
		return node.getRoot() instanceof org.eclipse.jdt.core.dom.CompilationUnit unit &&
			((List<Comment>)unit.getCommentList()).stream().anyMatch(comment -> comment.getStartPosition() <= node.getStartPosition() && comment.getStartPosition() + comment.getLength() >= node.getStartPosition() + node.getLength());
	}

	public static boolean isWithinRange(org.eclipse.jdt.core.dom.ASTNode node, IJavaElement el) {
		if( el instanceof ISourceReference isr) {
			try {
				ISourceRange r = isr.getSourceRange();
				if( r != null ) {
					int astStart = node.getStartPosition();
					int astLen = node.getLength();
					int rangeStart = r.getOffset();
					int rangeLen = r.getLength();
					return astStart >= rangeStart && (astStart + astLen) <= (rangeStart + rangeLen);
				}
			} catch(JavaModelException jme) {
				// Ignore
			}
		}
		return false;
	}
}
