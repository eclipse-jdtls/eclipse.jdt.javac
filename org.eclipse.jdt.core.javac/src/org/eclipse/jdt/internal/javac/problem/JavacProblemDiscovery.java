package org.eclipse.jdt.internal.javac.problem;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.JavacBindingResolver;
import org.eclipse.jdt.core.dom.JdtCoreDomPackagePrivateUtility;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.impl.ReferenceContext;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.javac.dom.JavacTypeBinding;

public class JavacProblemDiscovery extends ASTVisitor {
	private JavacProblemReporter reporter = null;
	public JavacProblemDiscovery(Map<String, String> compilerOptions, ReferenceContext referenceContext) {
		reporter = new JavacProblemReporter(DefaultErrorHandlingPolicies.proceedWithAllProblems(),
				new CompilerOptions(compilerOptions),
				new DefaultProblemFactory(Locale.getDefault()), referenceContext);
	}
	@Override
	public boolean visit(MethodDeclaration node) {
		if( reporter.options.reportMissingOverrideAnnotationForInterfaceMethodImplementation ) {
			return visitMethodDeclarationForOverrides(node);
		}
		return true;
	}

	private boolean visitMethodDeclarationForOverrides(MethodDeclaration node) {
		IMethodBinding b = node.resolveBinding();
		if( b == null )
			return true;

		boolean hasOverridesAnnotation = false;
		List mods = node.modifiers();
		for( int i = 0; i < mods.size() && !hasOverridesAnnotation; i++) {
			ASTNode o = (ASTNode)mods.get(i);
			if( o instanceof MarkerAnnotation ma) {
				String s = ma.getTypeName().toString();
				if( "Override".equals(s)) {
					hasOverridesAnnotation = true;
				}
			}
		}

		ITypeBinding tb = b.getDeclaringClass();
		boolean shouldHaveOverride = typeHasMethodRecurse(b, tb, false);
		boolean isInterface = tb.isInterface();
		if( isInterface ) {
			JavacBindingResolver jcbr =JdtCoreDomPackagePrivateUtility.getJavacBindingResolverOrNull(node.getAST());
			ITypeBinding objBinding = jcbr.resolveWellKnownType("java.lang.Object");
			shouldHaveOverride |= typeHasMethod(b, objBinding);
		}
		if( shouldHaveOverride && !hasOverridesAnnotation) {
			reporter.missingOverrideAnnotation(node);
		}
		return true;
	}

	private boolean typeHasMethodRecurse(IMethodBinding mb, ITypeBinding tb, boolean checkType) {
		if( mb == null || tb == null )
			return false;

		if( checkType ) {
			if( typeHasMethod(mb, tb)) {
				return true;
			}
		}
		ITypeBinding[] interfaces = tb.getInterfaces();
		if( interfaces != null ) {
			for( int i = 0; i < interfaces.length; i++ ) {
				if( typeHasMethodRecurse(mb, interfaces[i], true)) {
					return true;
				}
			}
		}

		ITypeBinding sup = tb.getSuperclass();
		if( typeHasMethodRecurse(mb, sup, true)) {
			return true;
		}
		return false;
	}


	private boolean typeHasMethod(IMethodBinding mb, ITypeBinding tb) {
		if( mb == null || tb == null)
			return false;
		IMethodBinding[] all = tb instanceof JavacTypeBinding jct ? jct.getDeclaredMethods(false) : tb.getDeclaredMethods();
		if( all == null )
			return false;
		for( int i = 0; i < all.length; i++ ) {
			if( mb.overrides(all[i])) {
				return true;
			}
		}
		return false;
	}
}