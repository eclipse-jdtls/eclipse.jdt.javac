/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.internal.javac.dom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.ILog;
import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IModuleBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.JavacBindingResolver;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ModuleDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import shaded.com.sun.tools.javac.code.Attribute.Compound;

public abstract class JavacAnnotationBinding implements IAnnotationBinding {

	private final JavacBindingResolver resolver;
	private final Compound annotation;

	private final IBinding recipient;

	public JavacAnnotationBinding(Compound ann, JavacBindingResolver resolver, IBinding recipient) {
		this.resolver = resolver;
		this.annotation = ann;
		this.recipient = recipient;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof JavacAnnotationBinding other
				&& Objects.equals(this.resolver, other.resolver)
				&& Objects.equals(this.annotation, other.annotation)
				&& Objects.equals(this.getRecipient(), other.getRecipient());
	}
	@Override
	public int hashCode() {
		return Objects.hash(this.resolver, this.annotation, this.getRecipient());
	}

	@Override
	public IAnnotationBinding[] getAnnotations() {
		return new IAnnotationBinding[0];
	}

	@Override
	public int getKind() {
		return ANNOTATION;
	}

	@Override
	public int getModifiers() {
		return getAnnotationTypeOptional().map(ITypeBinding::getModifiers).orElse(0);
	}

	@Override
	public boolean isDeprecated() {
		return getAnnotationTypeOptional().map(ITypeBinding::isDeprecated).orElse(false);
	}

	@Override
	public boolean isRecovered() {
		return getAnnotationTypeOptional().map(ITypeBinding::isRecovered).orElse(false);
	}

	@Override
	public boolean isSynthetic() {
		return getAnnotationTypeOptional().map(ITypeBinding::isSynthetic).orElse(false);
	}

	@Override
	public IJavaElement getJavaElement() {
		ASTNode node = this.resolver.findAstNodeForBinding(this);
		if (!(node instanceof Annotation)) return null;
		ASTNode parent = node.getParent();
		IJavaElement parentElement = null;
		switch (parent.getNodeType()) {
		case ASTNode.PACKAGE_DECLARATION:
			IJavaElement cu = ((CompilationUnit) parent.getParent()).getJavaElement();
			if (cu instanceof ICompilationUnit) {
				String pkgName = ((PackageDeclaration) parent).getName().getFullyQualifiedName();
				parentElement =  ((ICompilationUnit) cu).getPackageDeclaration(pkgName);
			}
			break;
		case ASTNode.ENUM_DECLARATION:
		case ASTNode.TYPE_DECLARATION:
		case ASTNode.ANNOTATION_TYPE_DECLARATION:
			parentElement = ((AbstractTypeDeclaration) parent).resolveBinding().getJavaElement();
			break;
		case ASTNode.FIELD_DECLARATION:
			VariableDeclarationFragment fragment = (VariableDeclarationFragment) ((FieldDeclaration) parent).fragments().get(0);
			IVariableBinding variableBinding = fragment.resolveBinding();
			if (variableBinding == null) {
				return null;
			}
			parentElement = variableBinding.getJavaElement();
			break;
		case ASTNode.METHOD_DECLARATION:
				IMethodBinding methodBinding = ((MethodDeclaration) parent).resolveBinding();
				if (methodBinding == null) return null;
				parentElement = methodBinding.getJavaElement();
			break;
		case ASTNode.MODULE_DECLARATION:
			IModuleBinding moduleBinding = ((ModuleDeclaration) parent).resolveBinding();
			if (moduleBinding == null) return null;
			parentElement = moduleBinding.getJavaElement();
		break;
		case ASTNode.VARIABLE_DECLARATION_STATEMENT:
			fragment = (VariableDeclarationFragment) ((VariableDeclarationStatement) parent).fragments().get(0);
			variableBinding = fragment.resolveBinding();
			if (variableBinding == null) {
				return null;
			}
			parentElement = variableBinding.getJavaElement();
			break;
		default:
			return null;
		}
		if (! (parentElement instanceof IAnnotatable)) return null;
		if ((parentElement instanceof IMember) && ((IMember) parentElement).isBinary()) {
			return ((IAnnotatable) parentElement).getAnnotation(getAnnotationType().getQualifiedName());
		}
		return ((IAnnotatable) parentElement).getAnnotation(getName());


//		IBinding recipient = getRecipient();
//		IJavaElement recipientElement = recipient == null ? null : recipient.getJavaElement();
//		if( recipientElement != null && recipientElement instanceof IAnnotatable annot) {
//			IAnnotation found = annot.getAnnotation(getName());
//			if( found != null )
//				return found;
//		}
//		return getAnnotationTypeOptional().map(ITypeBinding::getJavaElement).orElse(null);
	}

	@Override
	public String getKey() {
		StringBuilder builder = new StringBuilder();
		if (this.getRecipient() != null) {
			builder.append(this.getRecipient().getKey());
		}
		builder.append('@');
		ITypeBinding annotationType = this.getAnnotationType();
		if (annotationType != null) {
			builder.append(this.getAnnotationType().getKey());
		} else {
			ILog.get().error("missing annotation type");
		}
		return builder.toString();
	}

	@Override
	public boolean isEqualTo(IBinding binding) {
		return binding instanceof IAnnotationBinding other && Objects.equals(getKey(), other.getKey());
	}

	@Override
	public IMemberValuePairBinding[] getAllMemberValuePairs() {
		IMemberValuePairBinding[] declared = getDeclaredMemberValuePairs();
		List<IMethodBinding> implicitDefaultMethods = new ArrayList<>(Arrays.asList(getAnnotationType().getDeclaredMethods()));
		Collections.sort(implicitDefaultMethods, new Comparator<IMethodBinding>() {
			@Override
			public int compare(IMethodBinding o1, IMethodBinding o2) {
				String s1 = o1.getName();
				String s2 = o2.getName();
				return Comparator.nullsFirst(String::compareTo).compare(s1, s2);
			}
		});
		var explicitedMethods = Arrays.stream(declared).map(IMemberValuePairBinding::getMethodBinding).toList();
		implicitDefaultMethods.removeAll(explicitedMethods);
		if (implicitDefaultMethods.isEmpty()) {
			return declared;
		}
		var defaults = implicitDefaultMethods.stream().map(this.resolver.bindings::getDefaultMemberValuePairBinding).filter(Objects::nonNull).toArray();
		var res = Arrays.copyOf(declared, declared.length + defaults.length);
		System.arraycopy(defaults, 0, res, declared.length, defaults.length);
		return res;
	}

	@Override
	public ITypeBinding getAnnotationType() {
		return this.resolver.bindings.getTypeBinding(this.annotation.type);
	}

	@Override
	public IMemberValuePairBinding[] getDeclaredMemberValuePairs() {
		return this.annotation.getElementValues().entrySet().stream()
				.map(entry -> this.resolver.bindings.getMemberValuePairBinding(entry.getKey(), entry.getValue()))
				.filter(Objects::nonNull)
				.toArray(IMemberValuePairBinding[]::new);
	}

	@Override
	public String getName() {
		return getAnnotationTypeOptional().map(ITypeBinding::getName).orElse(null);
	}

	@Override
	public String toString() {
		return '@' + getName() + '(' +
				Arrays.stream(getAllMemberValuePairs()).map(IMemberValuePairBinding::toString).collect(Collectors.joining(","))
			+ ')';
	}

	public IBinding getRecipient() {
		return recipient;
	}

	private Optional<ITypeBinding> getAnnotationTypeOptional() {
		return Optional.ofNullable(getAnnotationType());
	}
}
