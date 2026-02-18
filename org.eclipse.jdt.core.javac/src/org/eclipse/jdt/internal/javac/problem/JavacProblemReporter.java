package org.eclipse.jdt.internal.javac.problem;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.IErrorHandlingPolicy;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.impl.ReferenceContext;
import org.eclipse.jdt.internal.compiler.problem.ProblemHandler;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities;

public class JavacProblemReporter extends ProblemHandler {
	public ReferenceContext referenceContext;
	private ProblemReporter severityUtility; // very wasteful but dont want to copy an entire class
	public JavacProblemReporter(IErrorHandlingPolicy policy, CompilerOptions options, IProblemFactory problemFactory, ReferenceContext referenceContext) {
		super(policy, options, problemFactory);
		this.referenceContext = referenceContext;
		this.severityUtility = new ProblemReporter(policy, options, problemFactory);
	}

	public void missingOverrideAnnotation(MethodDeclaration method) {
		int severity = this.severityUtility.computeSeverity(IProblem.MissingOverrideAnnotation);
		if (severity == ProblemSeverities.Ignore) return;
		IMethodBinding binding = method.resolveBinding();
		this.handle(
			IProblem.MissingOverrideAnnotation,
			new String[] { binding.getName(), typesAsString(binding, false),
					binding.getDeclaringClass().getName(), },
			0,
			new String[] { binding.getName(), typesAsString(binding, true),
					binding.getDeclaringClass().getName(), },
			severity, method.getName().getStartPosition(), method.getStartPosition() + method.getLength() - 1);
	}

	public void missingOverrideAnnotationForInterfaceMethodImplementation(MethodDeclaration method) {
		int severity = this.severityUtility.computeSeverity(IProblem.MissingOverrideAnnotationForInterfaceMethodImplementation);
		if (severity == ProblemSeverities.Ignore)
			return;
		IMethodBinding binding = method.resolveBinding();
		this.handle(IProblem.MissingOverrideAnnotationForInterfaceMethodImplementation,
				new String[] { binding.getName(), typesAsString(binding, false),
						binding.getDeclaringClass().getName(), },
				new String[] { binding.getName(), typesAsString(binding, true),
						binding.getDeclaringClass().getName(), },
				severity, method.getName().getStartPosition(), method.getStartPosition() + method.getLength());
	}

	private String typesAsString(IMethodBinding imb, boolean makeShort) {
		return typesAsString(imb, imb.getParameterTypes(), makeShort);
	}

	private String typesAsString(IMethodBinding imb, ITypeBinding[] parameters, boolean makeShort) {
		return typesAsString(imb, parameters, makeShort, false);
	}

	private String typesAsString(IMethodBinding imb, boolean makeShort, boolean showNullAnnotations) {
		return typesAsString(imb, imb.getParameterTypes(), makeShort, showNullAnnotations);
	}

	private String typesAsString(IMethodBinding imb, ITypeBinding[] parameters, boolean makeShort,
			boolean showNullAnnotations) {
//		if (imb.isPolymorphic()) {
//			// get the original polymorphicMethod method
//			ITypeBinding[] types = imb.original().parameters;
//			StringBuilder buffer = new StringBuilder(10);
//			for (int i = 0, length = types.length; i < length; i++) {
//				if (i != 0) {
//					buffer.append(", "); //$NON-NLS-1$
//				}
//				ITypeBinding type = types[i];
//				boolean isVarargType = i == length - 1;
//				if (isVarargType) {
//					type = ((ArrayBinding) type).elementsType();
//				}
//				if (showNullAnnotations)
//					buffer.append(new String(type.nullAnnotatedReadableName(this.options, makeShort)));
//				else
//					buffer.append(new String(makeShort ? type.shortReadableName() : type.readableName()));
//				if (isVarargType) {
//					buffer.append("..."); //$NON-NLS-1$
//				}
//			}
//			return buffer.toString();
//		}
		StringBuilder buffer = new StringBuilder(10);
		for (int i = 0, length = parameters.length; i < length; i++) {
			if (i != 0) {
				buffer.append(", "); //$NON-NLS-1$
			}
			ITypeBinding type = parameters[i];
			boolean isVarargType = imb.isVarargs() && i == length - 1;
			if (isVarargType && type.isArray()) {
				type = type.getElementType();
			}
//			if (showNullAnnotations)
//				buffer.append(new String(type.nullAnnotatedReadableName(this.options, makeShort)));
//			else
				buffer.append(new String(makeShort ? type.getName() : type.getQualifiedName()));
			if (isVarargType) {
				buffer.append("..."); //$NON-NLS-1$
			}
		}
		return buffer.toString();
	}

	private String typesAsString(ITypeBinding[] types, boolean makeShort) {
		return typesAsString(types, makeShort, false);
	}

	private String typesAsString(ITypeBinding[] types, boolean makeShort, boolean showNullAnnotations) {
		StringBuilder buffer = new StringBuilder(10);
		for (int i = 0, length = types.length; i < length; i++) {
			if (i != 0) {
				buffer.append(", "); //$NON-NLS-1$
			}
			ITypeBinding type = types[i];
//			if (showNullAnnotations)
//				buffer.append(new String(type.nullAnnotatedReadableName(this.options, makeShort)));
//			else
				buffer.append(new String(makeShort ? type.getName() : type.getQualifiedName()));
		}
		return buffer.toString();
	}



	// use this private API when the compilation unit result can be found through the
	// reference context. Otherwise, use the other API taking a problem and a compilation result
	// as arguments
	private void handle(
			int problemId,
			String[] problemArguments,
			int elaborationId,
			String[] messageArguments,
			int severity,
			int problemStartPosition,
			int problemEndPosition){
		this.handle(
				problemId,
				problemArguments,
				elaborationId,
				messageArguments,
				severity,
				problemStartPosition,
				problemEndPosition,
				this.referenceContext,
				this.referenceContext == null ? null : this.referenceContext.compilationResult());
	}
	// use this private API when the compilation unit result can be found through the
	// reference context. Otherwise, use the other API taking a problem and a compilation result
	// as arguments
	private void handle(
		int problemId,
		String[] problemArguments,
		String[] messageArguments,
		int problemStartPosition,
		int problemEndPosition){

		this.handle(
				problemId,
				problemArguments,
				messageArguments,
				problemStartPosition,
				problemEndPosition,
				this.referenceContext,
				this.referenceContext == null ? null : this.referenceContext.compilationResult());
	}

	// use this private API when the compilation unit result can be found through the
	// reference context. Otherwise, use the other API taking a problem and a compilation result
	// as arguments
	private void handle(
		int problemId,
		String[] problemArguments,
		String[] messageArguments,
		int severity,
		int problemStartPosition,
		int problemEndPosition){

		this.handle(
				problemId,
				problemArguments,
				0, // no elaboration
				messageArguments,
				severity,
				problemStartPosition,
				problemEndPosition);
	}

}
