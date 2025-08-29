/*******************************************************************************
 * Copyright (c) 2023, Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.internal.javac.dom;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IModuleBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.JavacBindingResolver;

import com.sun.tools.javac.code.Symbol.PackageSymbol;

public abstract class JavacPackageBinding implements IPackageBinding {

	private PackageSymbol packageSymbol;
	final JavacBindingResolver resolver;
	private String nameString;

	public JavacPackageBinding(PackageSymbol packge, JavacBindingResolver resolver) {
		this.setPackageSymbol(packge);
		this.nameString = packge.getQualifiedName().toString();
		this.resolver = resolver;
	}

	public JavacPackageBinding(String nameString, JavacBindingResolver resolver) {
		this.nameString = nameString;
		this.resolver = resolver;
	}

	@Override
	public IAnnotationBinding[] getAnnotations() {
		return this.getPackageSymbol() == null ?
				new IAnnotationBinding[0] :
				this.getPackageSymbol().getAnnotationMirrors().stream()
				.map(am -> this.resolver.bindings.getAnnotationBinding(am, this))
				.toArray(IAnnotationBinding[]::new);
	}

	@Override
	public int getKind() {
		return PACKAGE;
	}

	@Override
	public int getModifiers() {
		return this.getPackageSymbol() == null ? 0 : JavacMethodBinding.toInt(this.getPackageSymbol().getModifiers());
	}

	@Override
	public boolean isDeprecated() {
		return this.getPackageSymbol() == null ? false : this.getPackageSymbol().isDeprecated();
	}

	@Override
	public boolean isRecovered() {
		var element = getJavaElement();
		return element == null || !element.exists();
	}

	@Override
	public boolean isSynthetic() {
		return false;
	}

	@Override
	public IJavaElement getJavaElement() {
		System.err.println("Hardocded binding->IJavaElement to 1st package");
		if (this.resolver.javaProject == null) {
			return null;
		}
		try {
			IPackageFragmentRoot[] roots = this.resolver.javaProject.getAllPackageFragmentRoots();
			String qName = this.getQualifiedNameInternal();
			IJavaElement ret = Arrays.stream(roots)
				.map(root -> root.getPackageFragment(qName))
				.filter(Objects::nonNull)
				.filter(IPackageFragment::exists)
				.findFirst()
				.orElse(null);

			// TODO need to make sure the package is accessible in the module. :|
			return ret;
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	public IJavaElement findJavaElementForClass(String fqqn) {
		if (this.resolver.javaProject == null) {
			return null;
		}
		String qnInternal = this.getQualifiedNameInternal();
		if( fqqn.length() <= qnInternal.length() ) {
			// Not a type name
			return null;
		}
		try {
			List<IPackageFragment> ret = Arrays.stream(this.resolver.javaProject.getAllPackageFragmentRoots())
				.map(root -> root.getPackageFragment(qnInternal))
				.filter(Objects::nonNull)
				.filter(IPackageFragment::exists)
				.collect(Collectors.toList());
			int s = ret.size();
			if( s == 1 ) {
				return ret.get(0);
			}

			String relative = qnInternal == null || qnInternal.isEmpty() ? fqqn : fqqn.substring(qnInternal.length() + 1);
			String relativePackage = relative != null && relative.contains(".") ? relative.substring(0, relative.lastIndexOf(".")) : null;
			String cuName = relative != null && relative.contains(".") ? relative.substring(relative.lastIndexOf(".") + 1) : relative;
			String soughtFile = cuName + ".java";
			for( int i = 0; i < s; i++ ) {
				IPackageFragment pf = ret.get(i);
				if( relativePackage == null ) {
					ICompilationUnit cu = pf.getCompilationUnit(soughtFile);
					if( cu != null && cu.exists()) {
						return pf;
					}
				}
			}
			// TODO need to make sure the package is accessible in the module. :|
			return s == 0 ? null : ret.get(0);
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
	@Override
	public IModuleBinding getModule() {
		return this.getPackageSymbol() != null ?
				this.resolver.bindings.getModuleBinding(this.getPackageSymbol().modle) :
				null;
	}

	@Override
	public String getKey() {
		if (this.isUnnamed()) {
			return "";
		}
		return getQualifiedNameInternal().replace('.', '/');
	}

	@Override
	public String getName() {
		return isUnnamed() ? "" : this.getQualifiedNameInternal(); //$NON-NLS-1$
	}

	@Override
	public boolean isUnnamed() {
		PackageSymbol ps = this.getPackageSymbol();
		return ps != null ? ps.isUnnamed() : "".equals(this.nameString);
	}

	@Override
	public String[] getNameComponents() {
		return isUnnamed()? new String[0] : getQualifiedNameInternal().split("\\."); //$NON-NLS-1$
	}

	private String getQualifiedNameInternal() {
		return this.getPackageSymbol() != null ? this.getPackageSymbol().getQualifiedName().toString() :
			this.nameString;
	}

	@Override
	public String toString() {
		return "package " + getName();
	}

	public PackageSymbol getPackageSymbol() {
		return packageSymbol;
	}

	public void setPackageSymbol(PackageSymbol packageSymbol) {
		this.packageSymbol = packageSymbol;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.resolver, this.getPackageSymbol(), this.nameString);
	}

	@Override
	public boolean isEqualTo(IBinding binding) {
		return binding instanceof IPackageBinding other && Objects.equals(getKey(), other.getKey());
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof JavacPackageBinding other
				&& Objects.equals(this.resolver, other.resolver)
				&& Objects.equals(this.getPackageSymbol(), other.getPackageSymbol())
				&& Objects.equals(this.nameString, other.nameString);
	}

}
