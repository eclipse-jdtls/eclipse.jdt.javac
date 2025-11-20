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
package org.eclipse.jdt.javac.ui;

import java.util.stream.Collectors;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.javac.configurator.JavacConfigurationActivator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

public class PreferencePage extends org.eclipse.jface.preference.PreferencePage implements IWorkbenchPreferencePage {

	private boolean selected = JavacConfigurationActivator.getInstance().isJavacEnabled();
	
	public PreferencePage() { }

	public PreferencePage(String title) {
		super(title);
	}

	public PreferencePage(String title, ImageDescriptor image) {
		super(title, image);
	}

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE,
				JavacConfigurationActivator.symbolicName()));
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite res = new Composite(parent, SWT.NONE);
		res.setLayout(new RowLayout(SWT.VERTICAL));
		Button checkbox = new Button(res, SWT.CHECK);
		checkbox.setText("Use Javac as backend for JDT instead of ECJ");
		checkbox.setSelection(selected);
		checkbox.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {}
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				PreferencePage.this.selected = checkbox.getSelection();
			}
		});
		new Label(res, SWT.NONE).setText("Enabling sets the following system properties");
		new Text(res, SWT.MULTI | SWT.BORDER | SWT.READ_ONLY).setText(JavacConfigurationActivator.SYSTEM_PROPERTIES.entrySet().stream()
				.map(e -> e.getKey() + "=" + e.getValue())
				.collect(Collectors.joining("\n")));
		new Label(res, SWT.NONE).setText("A restart and a full re-indexing are required for the change to be fully effective.");
		return res;
	}

	@Override
	public boolean performOk() {
		if (selected != JavacConfigurationActivator.getInstance().isJavacEnabled()) {
			getPreferenceStore().setValue(JavacConfigurationActivator.PREF_JAVAC_ENABLED, selected ? JavacConfigurationActivator.ENABLED : JavacConfigurationActivator.DISABLED);
		}
		return true;
	}

}
