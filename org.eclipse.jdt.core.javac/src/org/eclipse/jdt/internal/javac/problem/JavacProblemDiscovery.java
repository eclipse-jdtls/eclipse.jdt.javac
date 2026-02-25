package org.eclipse.jdt.internal.javac.problem;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.JavacBindingResolver;
import org.eclipse.jdt.core.dom.JdtCoreDomPackagePrivateUtility;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
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
	public boolean visit(ParameterizedType node) {
		ASTNode parent = node.getParent();
		if( parent instanceof ClassInstanceCreation cic ) {
			Type rightType = cic.getType();
			if( cic.getParent() instanceof VariableDeclarationFragment vdf && vdf.getParent() instanceof VariableDeclarationStatement vds) {
				Type leftType = vds.getType();
				if( leftType.isParameterizedType() && rightType.isParameterizedType()) {
					if( leftType instanceof ParameterizedType ltt && rightType instanceof ParameterizedType rtt) {
						List leftList = ltt.typeArguments();
						List rightList = rtt.typeArguments();
						if( leftList.size() > 0 && rightList.size() == leftList.size()) {
							ITypeBinding[] rightListArr =
									 ((List<?>) rightList).stream()
								        .map(o -> (Type) o)                 // explicit cast
								        .map(x -> x.resolveBinding())
								        .filter(Objects::nonNull)
								        .toArray(ITypeBinding[]::new);
							if( rightListArr.length == leftList.size()) {
								reporter.redundantSpecificationOfTypeArguments(rtt, rightListArr);
							}
						}
					}
				}
			}

		}
		return true;
	}

	@Override
	public boolean visit(Assignment node) {
		// TODO
//		if( node.getOperator() == Assignment.Operator.ASSIGN ) {
//			return true;
//		}
		return true;
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
		int shouldHaveOverride = typeHasMethodRecurse(b, tb, false);
		boolean isInterface = tb.isInterface();
		if( isInterface ) {
			JavacBindingResolver jcbr =JdtCoreDomPackagePrivateUtility.getJavacBindingResolverOrNull(node.getAST());
			ITypeBinding objBinding = jcbr.resolveWellKnownType("java.lang.Object");
			if( typeHasMethod(b, objBinding) ) {
				shouldHaveOverride |= INTERFACE_HAS_METHOD;
			}
		}
		if( !hasOverridesAnnotation ) {
			if( (shouldHaveOverride & TYPE_HAS_METHOD) == TYPE_HAS_METHOD ) {
				reporter.missingOverrideAnnotation(node);
			} else if( shouldHaveOverride == INTERFACE_HAS_METHOD ) {
				reporter.missingOverrideAnnotationForInterfaceMethodImplementation(node);
			}
		}
		return true;
	}

	private static final int TYPE_HAS_METHOD = 0b1;
	private static final int INTERFACE_HAS_METHOD = 0b10;
	private int typeHasMethodRecurse(IMethodBinding mb, ITypeBinding tb, boolean checkType) {
		if( mb == null || tb == null )
			return 0;

		if( checkType ) {
			if( typeHasMethod(mb, tb)) {
				if( tb.isInterface() )
					return INTERFACE_HAS_METHOD;
				return TYPE_HAS_METHOD;
			}
		}

		ITypeBinding[] interfaces = tb.getInterfaces();
		if( interfaces != null ) {
			for( int i = 0; i < interfaces.length; i++ ) {
				int recurse = typeHasMethodRecurse(mb, interfaces[i], true);
				if( recurse != 0) {
					return recurse;
				}
			}
		}

		ITypeBinding sup = tb.getSuperclass();
		int supRet = typeHasMethodRecurse(mb, sup, true);
		return supRet;
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