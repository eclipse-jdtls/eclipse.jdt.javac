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
package org.eclipse.jdt.core.tests.javac;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.CharArrayWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.JavacCompilationUnitResolver;
import org.junit.Ignore;
import org.junit.Test;

public class CachingClassReaderTests {

	@Ignore // currently ignored https://github.com/eclipse-jdtls/eclipse-jdt-core-incubator/issues/1827
	@Test
	public void testInnerAnonymousWithTypeParams() throws Exception {
		Path dir = Files.createTempDirectory(getClass().getName());
		Path sourceFile = dir.resolve("A.java");
		Files.write(sourceFile, """
				class A<K, V> {
					java.util.function.Function<K, V> m() {
						return new java.util.function.Function<>() {
							@Override
							public V apply(K k) {
								return null;
							}
						};
					}
				}
				""".getBytes());
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
		Iterable<? extends JavaFileObject> compilationUnits1 = fileManager.getJavaFileObjectsFromPaths(List.of(sourceFile));
		CharArrayWriter writer = new CharArrayWriter(2000);
		assertTrue(new String(writer.toCharArray()), compiler.getTask(writer, fileManager, null, List.of("--release", "17"), null, compilationUnits1).call());
		Path Bclass = dir.resolve("A.class");
		assertTrue(Files.exists(Bclass));

		String source = """
				class I {
					A<String, String> a = new A<>();
				}
				""";
		// What we really want to test is calling CachingClassSymbolClassReader multiple times for A$B
		// but as we prefer avoiding references to internal Javac types in tests, we just load the AST multiple times.
		for (int i = 0; i < 2; i++) {
			ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
			parser.setSource(source.toCharArray());
			parser.setUnitName("I.java");
			parser.setEnvironment(new String[] { dir.toString() }, null, null, true);
			parser.setResolveBindings(true);
			try (var _ = withoutLoggedError()) {
				var node = (CompilationUnit)parser.createAST(new NullProgressMonitor());
				assertArrayEquals(new IProblem[0], node.getProblems());
				node.getAST().resolveWellKnownType("A$1");
			}
		}
	}

	@Test
	public void testIntersectionType() throws Exception {
		Path dir = Files.createTempDirectory(getClass().getName());
		Path sourceFile = dir.resolve("A.java");
		Files.write(sourceFile, """
				class A<T extends java.util.function.IntSupplier & java.util.function.LongSupplier> {
					T f;
					A(T f) {
						this.f = f;
					}
				}
				""".getBytes());

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
		Iterable<? extends JavaFileObject> compilationUnits1 = fileManager.getJavaFileObjectsFromPaths(List.of(sourceFile));
		CharArrayWriter writer = new CharArrayWriter(2000);
		assertTrue(compiler.getTask(writer, fileManager, null, List.of("--release", "17"), null, compilationUnits1).call());
		Path Bclass = dir.resolve("A.class");
		assertTrue(Files.exists(Bclass));

		String source = """
				class I {
					A<Sup> a = new A<>(new Sup());
				}
				class Sup implements java.util.function.IntSupplier, java.util.function.LongSupplier {
					public int getAsInt() { return 0; }
					public long getAsLong() { return 0; }
				}
			""";
		// What we really want to test is calling CachingClassSymbolClassReader multiple times for A$B
		// but as we prefer avoiding references to internal Javac types in tests, we just load the AST multiple times.
		for (int i = 0; i < 2; i++) {
			ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
			parser.setSource(source.toCharArray());
			parser.setUnitName("I.java");
			parser.setEnvironment(new String[] { dir.toString() }, null, null, true);
			parser.setResolveBindings(true);
			try (var _ = withoutLoggedError()) {
				var node = (CompilationUnit)parser.createAST(new NullProgressMonitor());
				assertArrayEquals(new IProblem[0], node.getProblems());
			}
		}
	}

	private AutoCloseable withoutLoggedError() {
		ILog log = Platform.getLog(JavacCompilationUnitResolver.class);
		List<IStatus> errors = new ArrayList<>();
		ILogListener listener = (status, bundle) -> {
			if (status.getSeverity() == IStatus.ERROR) {
				errors.add(status);
			}
		};
		log.addLogListener(listener);
		return () -> {
			log.removeLogListener(listener);
			assertEquals(List.of(), errors);
		};
	}
}
