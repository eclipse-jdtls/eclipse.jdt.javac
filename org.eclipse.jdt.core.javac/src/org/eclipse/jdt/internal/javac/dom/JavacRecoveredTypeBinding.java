/*******************************************************************************
 * Copyright (c) 2025, Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.internal.javac.dom;

import java.util.Objects;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.JavacBindingResolver;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ArrayType;

public class JavacRecoveredTypeBinding extends JavacTypeBinding {

	private final org.eclipse.jdt.core.dom.Type domType;

	public JavacRecoveredTypeBinding(com.sun.tools.javac.code.Type type, org.eclipse.jdt.core.dom.Type domType, JavacBindingResolver resolver) {
		super(type, type.tsym, null, null, false, resolver);
		this.domType = domType;
	}

	@Override
	public int hashCode() {
		return super.hashCode() ^ domType.toString().hashCode();
	}
	@Override
	public boolean equals(Object obj) {
		return obj instanceof JavacRecoveredTypeBinding recovered &&
			Objects.equals(recovered.domType.toString(), this.domType.toString()) &&
			Objects.equals(recovered.domType.getAST(), this.domType.getAST()) &&
			super.equals(obj);
	}

	@Override
	public JavacTypeBinding getComponentType() {
		if (this.type instanceof ArrayType javacArrayType && javacArrayType.isErroneous()) {
			if (getDimensions() > 1) {
				return this.resolver.bindings.getRecoveredTypeBinding(javacArrayType.elemtype, this.domType);
			} else if (this.domType instanceof org.eclipse.jdt.core.dom.ArrayType domArrayType) {
				return this.resolver.bindings.getRecoveredTypeBinding(javacArrayType.elemtype, domArrayType.getElementType());
			}
		}
		return super.getComponentType();
	}

	@Override
	public JavacTypeBinding getElementType() {
		var res = getElementType();
		if (res != null) {
			return res;
		}
		if (isArray() && this.domType instanceof org.eclipse.jdt.core.dom.ArrayType domArrayType) {
			Type t = this.types.elemtype(this.type);
			while (t instanceof Type.ArrayType) {
				t = this.types.elemtype(t);
			}
			if (t == null || t.isErroneous()) {
				return this.resolver.bindings.getRecoveredTypeBinding(t, domArrayType);
			}
		}
		return res;
	}

	@Override
	public IPackageBinding getPackage() {
		if (isArray()) {
			return null;
		}
		if (this.type == null || this.type.isErroneous()) {
			SimpleType base = getBaseType();
			if (base.getName() instanceof QualifiedName qname) {
				return this.resolver.bindings.getPackageBinding(qname.getQualifier());
			} else {
				ASTNode current = this.domType;
				while (current != null) {
					if (current instanceof CompilationUnit unit && unit.getPackage() != null) {
						return unit.getPackage().resolveBinding();
					}
					current = current.getParent();
				}
			}
			return null;
		}
		return super.getPackage();
	}

	private SimpleType getBaseType() {
		org.eclipse.jdt.core.dom.Type toConsider = this.domType;
		while (toConsider instanceof ParameterizedType parameterized) {
			toConsider = parameterized.getType();
		}
		return (SimpleType)toConsider;
	}

	@Override
	public String getQualifiedName() {
		if (isArray()) {
			return getComponentType().getQualifiedName() + "[]";
		}
		if (this.type == null || this.type.isErroneous()) {
			StringBuilder res = new StringBuilder(getPackage().getName());
			if (!res.isEmpty()) {
				res.append('.');
			}
			var name = getBaseType().getName();
			String simpleName = name.isSimpleName() ?
						((SimpleName)name).getIdentifier() :
						((QualifiedName)name).getName().getIdentifier();
			res.append(simpleName);
			return res.toString();
		}
		return super.getQualifiedName();
	}
}
