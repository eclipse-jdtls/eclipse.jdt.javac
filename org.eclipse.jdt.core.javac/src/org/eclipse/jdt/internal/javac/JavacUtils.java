/*******************************************************************************
 * Copyright (c) 2023, 2024 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.internal.javac;

import java.io.File;
import java.io.IOException;
import java.lang.Runtime.Version;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.internal.core.JavaProject;

import com.sun.tools.javac.comp.CompileStates.CompileState;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Options;

public class JavacUtils {

	public static void configureJavacContext(Context context, Map<String, String> compilerOptions, IJavaProject javaProject, boolean isTest, boolean skipModules) {
		configureJavacContext(context, compilerOptions, javaProject, null, null, isTest, skipModules);
	}

	public static void configureJavacContext(Context context, JavacConfig compilerConfig,
	        IJavaProject javaProject, File output, boolean isTest) {
		configureJavacContext(context, compilerConfig.compilerOptions().getMap(), javaProject, compilerConfig, output, isTest, false);
	}

	private static void configureJavacContext(Context context, Map<String, String> compilerOptions,
	        IJavaProject javaProject, JavacConfig compilerConfig, File output, boolean isTest, boolean skipModules) {
		IClasspathEntry[] classpath = new IClasspathEntry[0];
		if (javaProject != null && javaProject.getProject() != null) {
			try {
				classpath = javaProject.getRawClasspath();
			} catch (JavaModelException ex) {
				ILog.get().error(ex.getMessage(), ex);
			}
		}

		var addExports = Arrays.stream(classpath) //
				.filter(entry -> entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) //
				.map(IClasspathEntry::getExtraAttributes)
				.flatMap(Arrays::stream)
				.filter(attribute -> IClasspathAttribute.ADD_EXPORTS.equals(attribute.getName()))
				.map(IClasspathAttribute::getValue)
				.map(value -> value.split(":"))
				.flatMap(Arrays::stream)
				.collect(Collectors.joining("\0")); //$NON-NLS-1$ // \0 as expected by javac
		var limitModules = Arrays.stream(classpath) //
				.filter(entry -> entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) //
				.map(IClasspathEntry::getExtraAttributes)
				.flatMap(Arrays::stream)
				.filter(attribute -> IClasspathAttribute.LIMIT_MODULES.equals(attribute.getName()))
				.map(IClasspathAttribute::getValue)
				.map(value -> value.split(":"))
				.flatMap(Arrays::stream)
				.collect(Collectors.joining("\0"));
		configureOptions(javaProject, context, compilerOptions, addExports, limitModules);
		// TODO populate more from compilerOptions and/or project settings
		if (context.get(JavaFileManager.class) == null) {
			JavacFileManager.preRegister(context);
		}
		if (javaProject instanceof JavaProject internal) {
			configurePaths(internal, context, compilerConfig, output, isTest, skipModules);
		}
	}

	private static void configureOptions(IJavaProject javaProject, Context context, Map<String, String> compilerOptions, String addExports, String limitModules) {
		boolean nineOrLater = false;
		Options options = Options.instance(context);
		options.put("should-stop.ifError", CompileState.GENERATE.toString());
		options.put(Option.IMPLICIT, "none");
		final Version complianceVersion;
		String compliance = compilerOptions.get(CompilerOptions.OPTION_Compliance);
		if (CompilerOptions.VERSION_1_8.equals(compliance)) {
			compliance = "8";
			nineOrLater = false;
		}
		if (CompilerOptions.ENABLED.equals(compilerOptions.get(CompilerOptions.OPTION_Release))
			&& compliance != null && !compliance.isEmpty()) {
			complianceVersion = Version.parse(compliance);
			options.put(Option.RELEASE, compliance);
			nineOrLater = complianceVersion.compareTo(Version.parse("9")) >= 0;
		} else {
			String source = compilerOptions.get(CompilerOptions.OPTION_Source);
			if (CompilerOptions.VERSION_1_8.equals(source)) {
				source = "8";
				nineOrLater = false;
			}
			if (source != null && !source.isBlank()) {
				complianceVersion = Version.parse(source);
				if (complianceVersion.compareToIgnoreOptional(Version.parse("8")) < 0) {
					ILog.get().warn("Unsupported source level: " + source + ", using 8 instead");
					options.put(Option.SOURCE, "8");
					nineOrLater = false;
				} else {
					options.put(Option.SOURCE, source);
					nineOrLater = complianceVersion.compareTo(Version.parse("9")) >= 0;
				}
			} else {
				complianceVersion = Runtime.version();
				nineOrLater = true;
			}
			String target = compilerOptions.get(CompilerOptions.OPTION_TargetPlatform);
			if (CompilerOptions.VERSION_1_8.equals(target)) {
				target = "8";
				nineOrLater = false;
			}
			if (target != null && !target.isEmpty()) {
				Version version = Version.parse(target);
				if (version.compareToIgnoreOptional(Version.parse("8")) < 0) {
					ILog.get().warn("Unsupported target level: " + target + ", using 8 instead");
					options.put(Option.TARGET, "8");
					nineOrLater = false;
				} else {
					if (Integer.parseInt(target) < Integer.parseInt(source)) {
						ILog.get().warn("javac requires the source version to be less than or equal to the target version. Targetting " + source + " instead");
						target = source;
					}
					options.put(Option.TARGET, target);
				}
			}
		}
		if (CompilerOptions.ENABLED.equals(compilerOptions.get(CompilerOptions.OPTION_EnablePreviews)) &&
			Runtime.version().feature() == complianceVersion.feature()) {
			options.put(Option.PREVIEW, Boolean.toString(true));
		}
		options.put(Option.XLINT, Boolean.toString(true));
		options.put(Option.XLINT_CUSTOM, "all");
		if (JavaCore.ENABLED.equals(compilerOptions.get(JavaCore.COMPILER_DOC_COMMENT_SUPPORT))) {
			options.put(Option.XDOCLINT, Boolean.toString(true));
			options.put(Option.XDOCLINT_CUSTOM, "all");
		}
		if (addExports != null && !addExports.isBlank()) {
			options.put(Option.ADD_EXPORTS, addExports);
		}
		if (limitModules != null && !limitModules.isBlank()) {
			options.put(Option.LIMIT_MODULES, limitModules);
		}
		if (!options.isSet(Option.RELEASE) && javaProject instanceof JavaProject jp ) {
			if( jp.getProject() != null && jp.exists() ) {
				try {
					IType systemType = javaProject.findType(Object.class.getName());
					if (systemType != null) {
						IJavaElement element = systemType.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
						IPath path = element.getPath();
						if (path != null && path.toFile().exists()) {
							if(!nineOrLater ) {
								options.put( Option.BOOT_CLASS_PATH, path.toFile().getAbsolutePath());
							}
						}
					}
				} catch (JavaModelException ex) {
					ILog.get().error(ex.getMessage(), ex);
				}
			}
		}

		Map<String, String> processorOptions = ProcessorConfig.getProcessorOptions(javaProject);
		for (Entry<String, String> processorOption : processorOptions.entrySet()) {
			options.put("-A" + processorOption.getKey() + "=" + processorOption.getValue(), Boolean.toString(true));
		}
		if (!options.isSet(Option.A) && ProcessorConfig.isAnnotationProcessingEnabled(javaProject)) {
			options.put(Option.A, "");
		}

		addDebugInfos(compilerOptions, options);
	}

	private static void addDebugInfos(Map<String, String> compilerOptions, Options options) {
		boolean generateVars = CompilerOptions.GENERATE.equals(compilerOptions.get(CompilerOptions.OPTION_LocalVariableAttribute));
		boolean generateLines = CompilerOptions.GENERATE.equals(compilerOptions.get(CompilerOptions.OPTION_LineNumberAttribute));
		boolean generateSource = CompilerOptions.GENERATE.equals(compilerOptions.get(CompilerOptions.OPTION_SourceFileAttribute));
		if (generateVars && generateLines && generateSource) {
			options.put(Option.G, Boolean.toString(true));
		} else if (!generateVars && !generateLines && !generateSource) {
			options.put(Option.G_CUSTOM, Boolean.toString(true));
			options.put(Option.G_NONE, Boolean.toString(true));
		} else {
			options.put(Option.G_CUSTOM, Boolean.toString(true));
			if (generateVars) {
				options.put("-g:vars", Boolean.toString(true));
			}
			if (generateLines) {
				options.put("-g:lines", Boolean.toString(true));
			}
			if (generateSource) {
				options.put("-g:source", Boolean.toString(true));
			}
		}
	}

	private static void configurePaths(JavaProject javaProject, Context context, JavacConfig compilerConfig,
	        File output, boolean isTest, boolean skipModules) {
		var fileManager = (StandardJavaFileManager)context.get(JavaFileManager.class);
		try {
			if (compilerConfig != null && !isEmpty(compilerConfig.annotationProcessorPaths())) {
				fileManager.setLocation(StandardLocation.ANNOTATION_PROCESSOR_PATH,
					compilerConfig.annotationProcessorPaths()
						.stream()
						.map(File::new)
						.toList());
			}
			if (compilerConfig != null && !isEmpty(compilerConfig.generatedSourcePaths())) {
				fileManager.setLocation(StandardLocation.SOURCE_OUTPUT,
					compilerConfig.generatedSourcePaths()
						.stream()
						.map(File::new)
						.map(file -> {
							if (!file.exists() && !file.mkdirs()) {
								ILog.get().warn("Failed to create generated source file directory: " + file);
							}
							return file;
						})
						.toList());
			}

			if (output != null) {
				fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(ensureDirExists(output)));
			} else if (javaProject.getProject() != null && javaProject.getProject().exists()) {
				IResource member = javaProject.getProject().getParent().findMember(javaProject.getOutputLocation());
				if( member != null ) {
					File f = member.getLocation().toFile();
					fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(ensureDirExists(f)));
				}
			}

			Iterable<? extends File> srcPathLocationList = null;
			if (compilerConfig != null && !isEmpty(compilerConfig.sourcepaths())) {
				srcPathLocationList = compilerConfig.sourcepaths().stream().map(x -> new File(x)).toList();
			} else {
				srcPathLocationList = classpathEntriesToFiles(javaProject, true, entry -> (isTest || !entry.isTest()));
			}
			fileManager.setLocation(StandardLocation.SOURCE_PATH, srcPathLocationList);

			boolean moduleSourcePathEnabled = false;
			if (compilerConfig != null && !isEmpty(compilerConfig.moduleSourcepaths())) {
				Iterable<? extends File> msp = compilerConfig.moduleSourcepaths()
						.stream()
						.map(File::new)
						.toList();
				fileManager.setLocation(StandardLocation.MODULE_SOURCE_PATH,msp);
				moduleSourcePathEnabled = true;
			}

			boolean classpathEnabled = false;
			if (compilerConfig != null && !isEmpty(compilerConfig.classpaths())) {
				fileManager.setLocation(StandardLocation.CLASS_PATH,
					compilerConfig.classpaths()
						.stream()
						.map(File::new)
						.toList());
				classpathEnabled = true;
			}

			if (compilerConfig != null && !isEmpty(compilerConfig.modulepaths())) {
				fileManager.setLocation(StandardLocation.MODULE_PATH,
					compilerConfig.modulepaths()
						.stream()
						.map(File::new)
						.toList());
				classpathEnabled = true;
			}
			if (!classpathEnabled && javaProject.getProject() != null && javaProject.exists()) {
//				Set<JavaProject> moduleProjects = Set.of();
				IClasspathEntry[] expandedClasspath = javaProject.getExpandedClasspath();
				Set<JavaProject> moduleProjects = Stream.of(expandedClasspath)
						.filter(classpath -> classpath.getEntryKind() == IClasspathEntry.CPE_PROJECT)
						.map(classpath -> javaProject.getJavaModel().getJavaProject(classpath.getPath().lastSegment()))
						.filter(Objects::nonNull)
						.filter(classpathJavaProject -> {
							try {
								return classpathJavaProject.getModuleDescription() != null;
							} catch (JavaModelException e) {
								return false;
							}
						})
						.collect(Collectors.toSet());

				Collection<File> modulePathFiles = classpathEntriesToFiles(javaProject, false, entry -> (isTest || !entry.isTest()) && ClasspathEntry.isModular(entry));
				modulePathFiles.removeIf(JavacUtils::isCorrupt);
				if (!moduleProjects.isEmpty()) {
					modulePathFiles = new LinkedHashSet<>(modulePathFiles);
					moduleProjects.stream()
						.map(project -> {
							try {
								IPath relativeOutputPath = project.getOutputLocation();
								IPath absPath = javaProject.getProject().getParent()
										.findMember(relativeOutputPath).getLocation();
								return absPath.toOSString();
							} catch (JavaModelException e) {
								return null;
							}
						}).filter(Objects::nonNull)
						.map(File::new)
						.forEach(modulePathFiles::add);
				}
				fileManager.setLocation(StandardLocation.MODULE_PATH, modulePathFiles);
				Collection<File> classpathFiles = classpathEntriesToFiles(javaProject, false, entry -> (isTest || !entry.isTest()) && !ClasspathEntry.isModular(entry));
				classpathFiles.addAll(outDirectories(javaProject, entry -> isTest || !entry.isTest()));
				classpathFiles.removeIf(JavacUtils::isCorrupt);
				fileManager.setLocation(StandardLocation.CLASS_PATH, classpathFiles);

				if (!skipModules && !moduleSourcePathEnabled && javaProject.getModuleDescription() != null) {
					moduleProjects = new LinkedHashSet<>(moduleProjects);
					moduleProjects.add(javaProject);
					for (IJavaProject requiredModuleProject : moduleProjects) {
						IPath moduleFileLocation = requiredModuleProject.getModuleDescription().getResource().getLocation();
						if (moduleFileLocation.toFile().isFile()) {
							String elName = requiredModuleProject.getModuleDescription().getElementName();
							List<java.nio.file.Path> p1 = List.of(moduleFileLocation.removeLastSegments(1).toPath());
							if( isModuleSourcePath(moduleFileLocation.toPath(), elName)) {
								fileManager.setLocationForModule(StandardLocation.MODULE_SOURCE_PATH,
									elName, p1);
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			ILog.get().error(ex.getMessage(), ex);
		}
	}

	  // Named constants
    private static final boolean MODULE_SOURCE_PATH = true;
    private static final boolean NORMAL_SOURCE_PATH = false;

    /**
     * Determines whether this module-info.java should be treated
     * as a module-source-path layout or normal source path.
     *
     * @param moduleInfoPath path to module-info.java
     * @param moduleName the name declared inside module-info.java
     * @return MODULE_SOURCE_PATH or NORMAL_SOURCE_PATH
     */
    public static boolean isModuleSourcePath(java.nio.file.Path moduleInfoPath, String moduleName) {
    	java.nio.file.Path parent = moduleInfoPath.getParent();
        if (parent == null) return NORMAL_SOURCE_PATH; // defensive
        return parent.getFileName().toString().equals(moduleName)
                ? MODULE_SOURCE_PATH
                : NORMAL_SOURCE_PATH;
    }

	private static Collection<? extends File> outDirectories(JavaProject javaProject, Predicate<IClasspathEntry> select) {
		LinkedHashSet<File> res = new LinkedHashSet<>();
		try {
			addPath(javaProject, javaProject.getOutputLocation(), res);
		} catch (JavaModelException ex) {
			ILog.get().error(ex.getMessage(), ex);
		}
		try {
			for (IClasspathEntry entry : javaProject.resolveClasspath(javaProject.getExpandedClasspath())) {
				if (select == null || select.test(entry)) {
					var output = entry.getOutputLocation();
					if (output != null) {
						addPath(javaProject, output, res);
					}
				}
			}
		} catch (JavaModelException ex) {
			ILog.get().error(ex.getMessage(), ex);
		}
		return res;
	}

	public static <T> boolean isEmpty(List<T> list) {
		return list == null || list.isEmpty();
	}

	private static Collection<File> classpathEntriesToFiles(JavaProject project, boolean source, Predicate<IClasspathEntry> select) {
		try {
			LinkedHashSet<File> res = new LinkedHashSet<>();
//			if( !project.exists())
//				return res;

			ArrayList<IClasspathEntry> seen = new ArrayList<>();
			IModuleDescription modDesc = null;
			try {
				modDesc = project.getModuleDescription();
			} catch(CoreException ce) {
				// ignore
			}
			if (modDesc == null) {
				IPath outputLocation = project.getOutputLocation();
				if (outputLocation != null) {
					addPath(project, outputLocation, res);
				}
			}
			Queue<IClasspathEntry> toProcess = new LinkedList<>();
			if( project.getProject() != null ) {
				toProcess.addAll(Arrays.asList(project.resolveClasspath(project.getExpandedClasspath())));
			}
			while (!toProcess.isEmpty()) {
				IClasspathEntry current = toProcess.poll();
				if (current.getEntryKind() == IClasspathEntry.CPE_PROJECT && select.test(current)) {
					IResource referencedResource = project.getProject().getParent().findMember(current.getPath());
					if (referencedResource instanceof IProject referencedProject) {
						JavaProject referencedJavaProject = (JavaProject) JavaCore.create(referencedProject);
						if (referencedJavaProject.exists()) {
//							IModuleDescription moduleDescription = null;
//							try {
//								moduleDescription = referencedJavaProject.getModuleDescription();
//							} catch (JavaModelException e) {
//								// do nothing
//							}
//							if (moduleDescription == null) {
								IPath path = referencedJavaProject.getOutputLocation();
								if (!source) {
									addPath(referencedJavaProject, path, res);
								}
								IClasspathEntry[] resolved = referencedJavaProject.resolveClasspath(referencedJavaProject.getExpandedClasspath());
								for (IClasspathEntry transitiveEntry : resolved ) {
									if (transitiveEntry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
										if (select.test(transitiveEntry)) {
											if (!source) {
												IPath outputLocation = transitiveEntry.getOutputLocation();
												if (outputLocation != null) {
													addPath(referencedJavaProject, outputLocation, res);
												}
											}
											if (source) {
												IPath sourceLocation = transitiveEntry.getPath();
												if (sourceLocation != null) {
													addPath(referencedJavaProject, sourceLocation, res);
												}
											}
										}
									} else if (transitiveEntry.isExported() && !seen.contains(transitiveEntry)) {
										toProcess.add(transitiveEntry);
										seen.add(transitiveEntry);
									}
								}
//							}
						}
					}
				} else if (select.test(current) &&
						((source && current.getEntryKind() == IClasspathEntry.CPE_SOURCE) || (!source && current.getEntryKind() != IClasspathEntry.CPE_SOURCE))) {
					IPath path = current.getPath();
					addPath(project, path, res);
				}
			}

			return res;
		} catch (JavaModelException ex) {
			ILog.get().error(ex.getMessage(), ex);
			return List.of();
		}
	}

	private static void addPath(JavaProject project, IPath path, LinkedHashSet<File> res) {
		if (path.isEmpty()) {
			return;
		}
		File asFile = path.toFile();
		if (asFile.exists()) {
			res.add(asFile);
		} else {
			IResource asResource = project.getProject().getParent().findMember(path);
			if (asResource != null && asResource.exists()) {
				res.add(asResource.getLocation().toFile());
			}
		}
	}

	private static File ensureDirExists(File file) {
		if (!file.exists()) {
			file.mkdirs();
		}
		return file;
	}

	public static boolean isTest(IJavaProject project, org.eclipse.jdt.internal.compiler.env.ICompilationUnit[] units) {
		if (units == null || project == null || project.getResource() == null || !project.exists()) {
			return false;
		}
		Set<IPath> testFolders = new HashSet<>();
		try {
			for (IClasspathEntry entry : project.getResolvedClasspath(false)) {
				if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE && entry.isTest()) {
					testFolders.add(entry.getPath());
				}
			}
			return Arrays.stream(units)
					.map(org.eclipse.jdt.internal.compiler.env.ICompilationUnit::getFileName)
					.map(String::new)
					.map(Path::new)
					.anyMatch(file -> testFolders.stream().anyMatch(folder -> folder.isPrefixOf(file)));
		}  catch (JavaModelException ex) {
			ILog.get().error(ex.getMessage(), ex);
			return true;
		}
	}

	private static boolean isCorrupt(File f) {
		if (f == null || !f.canRead()) {
			return true;
		}
		if (f.isDirectory()) {
			return false;
		}
		if (f.isFile() && f.getName().endsWith(".jar")) {
			try (JarFile jarFile = new JarFile(f)) {
				jarFile.entries().asIterator().forEachRemaining(e -> {});
			} catch (IOException ex) {
				return true;
			}
		}
		return false;

	}
}
