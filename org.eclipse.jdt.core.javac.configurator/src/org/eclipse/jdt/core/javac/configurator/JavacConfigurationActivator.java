/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.core.javac.configurator;

import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/// Activates the bundle, configure system properties according to preferences
/// and set up preference listener.
/// 
/// ⚠️ This bundle must *NOT* depend on JDT Core, as it has to start before.
/// It also deals with startLevels and autostart (in p2.inf), keep it minimal
public class JavacConfigurationActivator implements BundleActivator {

	/// Value can be {@link #ENABLED} or {@link #DISABLED} as we want to distinguish
	/// unset vs false and boolean preferences don't allow that
	/// TODO consider drill down to individual properties?
	public static final String PREF_JAVAC_ENABLED = "javacEnabled";
	public static String ENABLED = "enabled";
	public static String DISABLED = "disabled";

	/// Those are the value of system properties to fully enable Javac backend
	public static Map<String, String> SYSTEM_PROPERTIES = Map.of(
		"ICompilationUnitResolver", "org.eclipse.jdt.core.dom.JavacCompilationUnitResolver",
		"AbstractImageBuilder.compilerFactory", "org.eclipse.jdt.internal.javac.JavacCompilerFactory",
		"CompilationUnit.DOM_BASED_OPERATIONS", "true",
		"ICompletionEngineProvider", "org.eclipse.jdt.core.dom.DOMCompletionEngineProvider",
		"SourceIndexer.DOM_BASED_INDEXER", "true",
		"MatchLocator.DOM_BASED_MATCH", "true",
		"IJavaSearchDelegate", "org.eclipse.jdt.internal.core.search.DOMJavaSearchDelegate");
	
	private static BundleContext context;
	private static JavacConfigurationActivator instance;
	private IEclipsePreferences instancePreferences;

	public JavacConfigurationActivator() {
		instance = this;
	}
	public static JavacConfigurationActivator getInstance() {
		return instance;
	}

	static BundleContext getContext() {
		return context;
	}

	public void start(BundleContext bundleContext) throws Exception {
		JavacConfigurationActivator.context = bundleContext;
		instancePreferences = InstanceScope.INSTANCE.getNode(symbolicName());
		// ensure the initial value is available from preference store
		instancePreferences.put(PREF_JAVAC_ENABLED, isJavacEnabled() ? ENABLED : DISABLED);
		instancePreferences.flush();
		instancePreferences.addPreferenceChangeListener(event -> {
			refreshSystemProperties();
		});
		refreshSystemProperties();
	}
	public static String symbolicName() {
		return JavacConfigurationActivator.context.getBundle().getSymbolicName();
	}

	private void refreshSystemProperties() {
		// Those need to be in sync with system properties usually
		// added to eclipse.ini thanks to p2.inf instructions
		if (isJavacEnabled()) {
			SYSTEM_PROPERTIES.entrySet().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
		} else {
			SYSTEM_PROPERTIES.keySet().forEach(System::clearProperty);
		}
	}

	public boolean isJavacEnabled() {
		boolean enabled = System.getProperty("ICompilationUnitResolver", "").toLowerCase().contains("javac");
		String pref = instancePreferences.get(PREF_JAVAC_ENABLED, null);
		if (pref != null) {
			enabled = Objects.equals(pref, ENABLED);
		}
		return enabled;
	}

	public void stop(BundleContext bundleContext) throws Exception {
		JavacConfigurationActivator.context = null;
	}

}
