/*******************************************************************************
* Copyright (c) 2026 IBM Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License 2.0
* which accompanies this distribution, and is available at
* https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     IBM Corporation - initial API and implementation
*******************************************************************************/
package org.eclipse.jdt.internal.javac;

import java.util.Objects;

import javax.lang.model.element.TypeElement;

import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;

public abstract class TopLevelTreeScanner<R, P> extends TreeScanner<R, P> {

	private final TypeElement currentTopLevelType;

	protected TopLevelTreeScanner(TypeElement currentTopLevelType) {
		this.currentTopLevelType = currentTopLevelType;
	}

	@Override
	public R visitClass(ClassTree node, P p) {
		if (node instanceof JCClassDecl classDecl) {
			/**
             * If a Java file contains multiple top-level types, it will trigger multiple
             * ANALYZE taskEvents for the same compilation unit. Each ANALYZE taskEvent
             * corresponds to the completion of analysis for a single top-level type.
             * Therefore, in the ANALYZE task event listener, we only visit the class and
             * nested classes that belong to the currently analyzed top-level type.
             */
			if (Objects.equals(this.currentTopLevelType, classDecl.sym)
					|| !(classDecl.sym.owner instanceof PackageSymbol)) {
				return super.visitClass(node, p);
			} else {
				return null; // Skip if it does not belong to the currently analyzed top-level type.
			}
		}

		return super.visitClass(node, p);
	}
}
