/*******************************************************************************
* Copyright (c) 2025 Red Hat, Inc. and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License 2.0
* which accompanies this distribution, and is available at
* https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*******************************************************************************/
package org.eclipse.jdt.internal.javac;

import java.util.Set;

import shaded.javax.lang.model.element.Element;

import shaded.com.sun.tools.javac.code.Symbol.MethodSymbol;
import shaded.com.sun.tools.javac.model.JavacTypes;
import shaded.com.sun.tools.javac.util.Context;

public class AvoidNPEJavacTypes extends JavacTypes {

	public static void preRegister(Context context) {
		context.put(JavacTypes.class, (Context.Factory<JavacTypes>)c -> new AvoidNPEJavacTypes(c));
	}

	private AvoidNPEJavacTypes(Context c) {
		super(c);
	}

	@Override
	public Set<MethodSymbol> getOverriddenMethods(Element elem) {
		if (elem == null) {
			return Set.of();
		}
		return super.getOverriddenMethods(elem);
	}
}
