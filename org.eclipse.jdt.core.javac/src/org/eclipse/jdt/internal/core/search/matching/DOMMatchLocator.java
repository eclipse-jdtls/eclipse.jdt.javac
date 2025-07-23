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
package org.eclipse.jdt.internal.core.search.matching;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.IModuleBinding;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

public class DOMMatchLocator extends MatchLocator {

	final Set<IModuleBinding> moduleBindings = new HashSet<>();

	DOMMatchLocator(SearchPattern pattern, SearchRequestor requestor, IJavaSearchScope scope,
			IProgressMonitor progressMonitor) {
		super(pattern, requestor, scope, progressMonitor);
	}

	public void registerModuleBinding(IModuleBinding binding) {
		if (binding != null) {
			this.moduleBindings.add(binding);
		}
	}

}
