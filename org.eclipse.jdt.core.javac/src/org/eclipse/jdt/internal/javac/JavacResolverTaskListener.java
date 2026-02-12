package org.eclipse.jdt.internal.javac;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import org.eclipse.core.runtime.ILog;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.JdtCoreDomPackagePrivateUtility;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.SearchableEnvironment;
import org.eclipse.jdt.internal.javac.problem.JavacDiagnosticProblemConverter;
import org.eclipse.jdt.internal.javac.problem.UnusedProblemFactory;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.main.Arguments;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.parser.Tokens.Comment.CommentStyle;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.Options;

import jdk.javadoc.internal.doclint.DocLint;

public class JavacResolverTaskListener implements TaskListener {
	private final Context context;
	private final JavacDiagnosticProblemConverter problemConverter;
	private final Map<String, String> compilerOptions;
	private final IJavaProject javaProject;
	private final UnusedProblemFactory unusedProblemFactory;
	private final JavacTask task;
	private final int focalPoint;
	private final Map<JavaFileObject, CompilationUnit> filesToUnits;
	private final int flags;

	public JavacResolverTaskListener(Context context, JavacDiagnosticProblemConverter problemConverter,
			Map<String, String> compilerOptions, IJavaProject javaProject, UnusedProblemFactory unusedProblemFactory,
			JavacTask task, int focalPoint, Map<JavaFileObject, CompilationUnit> filesToUnits, int flags) {
		this.context = context;
		this.problemConverter = problemConverter;
		this.compilerOptions = compilerOptions;
		this.javaProject = javaProject;
		this.unusedProblemFactory = unusedProblemFactory;
		this.task = task;
		this.focalPoint = focalPoint;
		this.filesToUnits = filesToUnits;
		this.flags = flags;
	}

	@Override
	public void finished(TaskEvent e) {
		if (e.getCompilationUnit() instanceof JCCompilationUnit u) {
			problemConverter.registerUnit(e.getSourceFile(), u);
		}

		if (e.getKind() == TaskEvent.Kind.PARSE && e.getCompilationUnit() instanceof JCCompilationUnit u) {
			finishedParse(u);
			return;
		}

		if( e.getKind() == TaskEvent.Kind.ANALYZE && e.getCompilationUnit() instanceof JCCompilationUnit u) {
			finishedAnalyze(e, u);
			return;
		}
	}


	private void finishedParse(JCCompilationUnit u) {
		List<TreeScanner> list = new ArrayList<>();
		if ((flags & ICompilationUnit.IGNORE_METHOD_BODIES) != 0) {
			list.add(getIgnoreMethodBodiesScanner());
		}
		if (focalPoint >= 0) {
			list.add(new TrimNonFocussedContentTreeScanner(u, focalPoint));
		}
		DelegatingTreeScanner scanner = new DelegatingTreeScanner(list);
		u.accept(scanner);
	}


	private void finishedAnalyze(TaskEvent e, JCCompilationUnit u) {
		var doclintOpts = Arguments.instance(context).getDocLintOpts();
		if (doclintOpts == null && isInJavadoc(u, focalPoint)) {
			// resolve doc comment bindings
			DocLint doclint = (DocLint) DocLint.newDocLint();
			doclint.init(task, doclintOpts.toArray(new String[doclintOpts.size()]));
			doclint.scan(TreePath.getPath(u, u));
		}

		final JavaFileObject file = e.getSourceFile();
		final CompilationUnit dom = filesToUnits.get(file);
		if (dom == null) {
			return;
		}
		if (Stream.of(dom.getProblems()).anyMatch(problem -> problem.isError())) {
			// don't bother; a severe error has already been reported
			return;
		}

		// check if the diagnostics are actually enabled before trying to collect them
		var objectCompilerOptions = new CompilerOptions(compilerOptions);
		boolean unusedImportIgnored = objectCompilerOptions
				.getSeverityString(CompilerOptions.UnusedImport).equals(CompilerOptions.IGNORE);
		boolean unusedPrivateMemberIgnored = objectCompilerOptions
				.getSeverityString(CompilerOptions.UnusedPrivateMember).equals(CompilerOptions.IGNORE);
		boolean unusedLocalVariableIgnored = objectCompilerOptions
				.getSeverityString(CompilerOptions.UnusedLocalVariable).equals(CompilerOptions.IGNORE);
		if (!Options.instance(context).get(Option.XLINT_CUSTOM).contains("all")
			    && unusedImportIgnored && unusedPrivateMemberIgnored && unusedLocalVariableIgnored) {
			return;
		}

		final TypeElement currentTopLevelType = e.getTypeElement();
		UnusedTreeScanner<Void, Void> scanner = new UnusedTreeScanner<>() {
			@Override
			public Void visitClass(ClassTree node, Void p) {
				if (node instanceof JCClassDecl classDecl) {
					/**
					 * If a Java file contains multiple top-level types, it will trigger multiple
					 * ANALYZE taskEvents for the same compilation unit. Each ANALYZE taskEvent
					 * corresponds to the completion of analysis for a single top-level type.
					 * Therefore, in the ANALYZE task event listener, we only visit the class and
					 * nested classes that belong to the currently analyzed top-level type.
					 */
					if (Objects.equals(currentTopLevelType, classDecl.sym)
							|| !(classDecl.sym.owner instanceof PackageSymbol)) {
						return super.visitClass(node, p);
					} else {
						return null; // Skip if it does not belong to the currently analyzed top-level type.
					}
				}

				return super.visitClass(node, p);
			}
		};
		final CompilationUnitTree unit = e.getCompilationUnit();
		try {
			scanner.scan(unit, null);
		} catch (Exception ex) {
			ILog.get().error("Internal error when visiting the AST Tree. " + ex.getMessage(), ex);
		}
		List<CategorizedProblem> unusedProblems = scanner.getUnusedPrivateMembers(unusedProblemFactory);
		if (!unusedProblems.isEmpty()) {
			addProblemsToDOM(dom, unusedProblems);
		}

		List<CategorizedProblem> unusedImports = scanner.getUnusedImports(unusedProblemFactory);
		List<? extends Tree> topTypes = unit.getTypeDecls();
		int typeCount = topTypes.size();
		// Once all top level types of this Java file have been resolved,
		// we can report the unused import to the DOM.
		if (typeCount <= 1) {
			addProblemsToDOM(dom, unusedImports);
		} else if (typeCount > 1 && topTypes.get(typeCount - 1) instanceof JCClassDecl lastType) {
			if (Objects.equals(currentTopLevelType, lastType.sym)) {
				addProblemsToDOM(dom, unusedImports);
			}
		}

		if (Options.instance(context).get(Option.XLINT_CUSTOM).contains("all")) {
			AccessRestrictionTreeScanner accessScanner = null;
			if (javaProject instanceof JavaProject internalJavaProject) {
				try {
					INameEnvironment environment = new SearchableEnvironment(internalJavaProject,
							(WorkingCopyOwner) null, false, JavaProject.NO_RELEASE);
					accessScanner = new AccessRestrictionTreeScanner(environment, new DefaultProblemFactory(),
							new CompilerOptions(compilerOptions));
					accessScanner.scan(unit, null);
				} catch (JavaModelException javaModelException) {
					// do nothing
				}
			}
			addProblemsToDOM(dom, accessScanner.getAccessRestrictionProblems());
		}
	}

	private static void addProblemsToDOM(CompilationUnit dom, List<CategorizedProblem> accessRestrictionProblems) {
		JdtCoreDomPackagePrivateUtility.addProblemsToDOM(dom, accessRestrictionProblems);
	}


	private TreeScanner getIgnoreMethodBodiesScanner() {
		return new TreeScanner() {
			@Override
			public void visitMethodDef(JCMethodDecl method) {
				if (method.body != null) {
					method.body.stats = com.sun.tools.javac.util.List.nil();
				}
			}
		};
	}

	private static class TrimNonFocussedContentTreeScanner extends TreeScanner {
		private JCCompilationUnit compilationUnit;
		private int focalPoint;
		public TrimNonFocussedContentTreeScanner(JCCompilationUnit compilationUnit, int focalPoint) {
			this.compilationUnit = compilationUnit;
			this.focalPoint = focalPoint;

		}
		@Override
		public void visitMethodDef(JCMethodDecl method) {
			if (method.body != null &&
				(focalPoint < method.getStartPosition()
				|| method.getEndPosition(compilationUnit.endPositions) < focalPoint)) {
				method.body.stats = com.sun.tools.javac.util.List.nil();
				// add a `throw new RuntimeException();` ?
			}
		}
		@Override
		public void scan(JCTree tree) {
			var comment = compilationUnit.docComments.getComment(tree);
			if (comment != null &&
				(focalPoint < comment.getPos().getStartPosition() || comment.getPos().getEndPosition(compilationUnit.endPositions) < focalPoint)) {
				compilationUnit.docComments.putComment(tree, new com.sun.tools.javac.parser.Tokens.Comment() {
					@Override public boolean isDeprecated() { return comment.isDeprecated(); }
					@Override public CommentStyle getStyle() { return comment.getStyle(); }
					@Override public int getSourcePos(int index) { return comment.getSourcePos(index); }
					@Override public DiagnosticPosition getPos() { return comment.getPos(); }
					@Override public com.sun.tools.javac.parser.Tokens.Comment stripIndent() { return comment.stripIndent(); }
					@Override public String getText() { return ""; }
				});
			}
			super.scan(tree);
		}
	}

	private static boolean isInJavadoc(JCCompilationUnit u, int focalPoint) {
		boolean[] res = new boolean[] { false };
		u.accept(new TreeScanner() {
			@Override
			public void scan(JCTree tree) {
				if (res[0]) {
					return;
				}
				var comment = u.docComments.getComment(tree);
				if (comment != null &&
					comment.getPos().getStartPosition() < focalPoint &&
					focalPoint < comment.getPos().getEndPosition(u.endPositions) &&
					(comment.getStyle() == CommentStyle.JAVADOC_BLOCK ||
					comment.getStyle() == CommentStyle.JAVADOC_LINE)) {
					res[0] = true;
					return;
				}
				super.scan(tree);
			}
		});
		return res[0];
	}

}