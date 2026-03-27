package org.eclipse.jdt.internal.javac.problem;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.JavacBindingResolver;
import org.eclipse.jdt.core.dom.JdtCoreDomPackagePrivateUtility;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
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
	private CompilationUnit currentCU = null;
	public JavacProblemDiscovery(Map<String, String> compilerOptions, ReferenceContext referenceContext) {
		reporter = new JavacProblemReporter(DefaultErrorHandlingPolicies.proceedWithAllProblems(),
				new CompilerOptions(compilerOptions),
				new DefaultProblemFactory(Locale.getDefault()), referenceContext);
	}



	@Override
	public boolean visit(CompilationUnit cu) {
		currentCU = cu;
		return true;
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
	public boolean visit(VariableDeclarationFragment frag) {
		int fragStart = frag.getStartPosition();
		IProblem match = Arrays.asList(currentCU.getProblems()).stream()
				.filter(x -> x.getID() == IProblem.UninitializedLocalVariable && x.getSourceStart() == fragStart).findFirst().orElse(null);
		if( match != null ) {
			if( frag.getInitializer() != null && frag.getInitializer() instanceof SimpleName sn) {
				SimpleName lhs = frag.getName();
				IBinding lhsBinding = lhs.resolveBinding();
				IBinding rhsBinding = sn.resolveBinding();
				if( lhsBinding == rhsBinding) {
					reporter.assignmentHasNoEffect(frag, lhs.getIdentifier().toCharArray());
				}
			}
		}
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

	@Override
	public boolean visit(SwitchStatement node) {
	    if (node.getExpression() == null) {
	        return true;
	    }

	    ITypeBinding typeBinding = node.getExpression().resolveTypeBinding();
	    if (typeBinding == null || !typeBinding.isEnum()) {
	        return true;
	    }

	    Set<String> enumConstants = new LinkedHashSet<>();
	    for (IVariableBinding field : typeBinding.getDeclaredFields()) {
	        if (field.isEnumConstant()) {
	            enumConstants.add(field.getName());
	        }
	    }

	    Set<String> handledConstants = new LinkedHashSet<>();
	    boolean hasDefault = false;
	    boolean hasInvalidCaseLabel = false;

	    for (Object stmtObj : node.statements()) {
	        if (stmtObj instanceof SwitchCase sc) {
	            if (sc.isDefault()) {
	                hasDefault = true;
	                continue;
	            }

	            @SuppressWarnings("unchecked")
	            List<Expression> expressions = sc.expressions();

	            for (Expression caseExpr : expressions) {
	                if (!(caseExpr instanceof SimpleName simpleName)) {
	                    hasInvalidCaseLabel = true;
	                    continue;
	                }

	                IBinding b = simpleName.resolveBinding();
	                if (!(b instanceof IVariableBinding vb) || !vb.isEnumConstant()) {
	                    hasInvalidCaseLabel = true;
	                    continue;
	                }

	                ITypeBinding declaringType = vb.getDeclaringClass();
	                if (declaringType == null || !declaringType.isEqualTo(typeBinding)) {
	                    hasInvalidCaseLabel = true;
	                    continue;
	                }

	                handledConstants.add(vb.getName());
	            }
	        }
	    }

	    Set<String> missing = new LinkedHashSet<>(enumConstants);
	    missing.removeAll(handledConstants);

	    if (!missing.isEmpty() && !hasDefault && !hasInvalidCaseLabel) {
	        String enumTypeName = typeBinding.getName();
	        for (String missingConstant : missing) {
	            reporter.missingEnumConstantInSwitch(node, enumTypeName, missingConstant);
	        }
	    }

	    return true;
	}
}