/*******************************************************************************
 * Copyright (c) 2026, IBM Corp. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.core.tests.javac.ui;

import org.eclipse.jdt.ui.tests.quickfix.AdvancedQuickAssistTest;
import org.eclipse.jdt.ui.tests.quickfix.AdvancedQuickAssistTest10;
import org.eclipse.jdt.ui.tests.quickfix.AdvancedQuickAssistTest1d7;
import org.eclipse.jdt.ui.tests.quickfix.AdvancedQuickAssistTest1d8;
import org.eclipse.jdt.ui.tests.quickfix.AnnotateAssistTest1d5;
import org.eclipse.jdt.ui.tests.quickfix.AnnotateAssistTest1d8;
import org.eclipse.jdt.ui.tests.quickfix.AssistQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.AssistQuickFixTest10;
import org.eclipse.jdt.ui.tests.quickfix.AssistQuickFixTest12;
import org.eclipse.jdt.ui.tests.quickfix.AssistQuickFixTest14;
import org.eclipse.jdt.ui.tests.quickfix.AssistQuickFixTest15;
import org.eclipse.jdt.ui.tests.quickfix.AssistQuickFixTest16;
import org.eclipse.jdt.ui.tests.quickfix.AssistQuickFixTest1d7;
import org.eclipse.jdt.ui.tests.quickfix.AssistQuickFixTest1d8;
import org.eclipse.jdt.ui.tests.quickfix.AssistQuickFixTest21;
import org.eclipse.jdt.ui.tests.quickfix.ChangeNonStaticToStaticTest;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpActionTest;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpAnnotationTest;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpStressTest;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest10;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest11;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest12;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest14;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest15;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest16;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest1d4;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest1d5;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest1d6;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest1d7;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest1d8;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest21;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest22;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest25;
import org.eclipse.jdt.ui.tests.quickfix.CleanUpTest9;
import org.eclipse.jdt.ui.tests.quickfix.ConvertForLoopQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.ConvertIterableLoopQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.ConvertIterableLoopQuickFixTest1d7;
import org.eclipse.jdt.ui.tests.quickfix.GetterSetterQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.JavadocQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.JavadocQuickFixTest16;
import org.eclipse.jdt.ui.tests.quickfix.JavadocQuickFixTest25;
import org.eclipse.jdt.ui.tests.quickfix.JavadocQuickFixTest9;
import org.eclipse.jdt.ui.tests.quickfix.LocalCorrectionsQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.LocalCorrectionsQuickFixTest10;
import org.eclipse.jdt.ui.tests.quickfix.LocalCorrectionsQuickFixTest15;
import org.eclipse.jdt.ui.tests.quickfix.LocalCorrectionsQuickFixTest1d7;
import org.eclipse.jdt.ui.tests.quickfix.LocalCorrectionsQuickFixTest1d8;
import org.eclipse.jdt.ui.tests.quickfix.MarkerResolutionTest;
import org.eclipse.jdt.ui.tests.quickfix.ModifierCorrectionsQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.ModifierCorrectionsQuickFixTest1d7;
import org.eclipse.jdt.ui.tests.quickfix.ModifierCorrectionsQuickFixTest9;
import org.eclipse.jdt.ui.tests.quickfix.NullAnnotationsCleanUpTest1d8;
import org.eclipse.jdt.ui.tests.quickfix.NullAnnotationsQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.NullAnnotationsQuickFixTest1d8;
import org.eclipse.jdt.ui.tests.quickfix.NullAnnotationsQuickFixTest1d8Mix;
import org.eclipse.jdt.ui.tests.quickfix.NullAnnotationsQuickFixTest9;
import org.eclipse.jdt.ui.tests.quickfix.PropertiesFileQuickAssistTest;
import org.eclipse.jdt.ui.tests.quickfix.QuickFixEnablementTest;
import org.eclipse.jdt.ui.tests.quickfix.QuickFixTest14;
import org.eclipse.jdt.ui.tests.quickfix.QuickFixTest15;
import org.eclipse.jdt.ui.tests.quickfix.QuickFixTest17;
import org.eclipse.jdt.ui.tests.quickfix.QuickFixTest1d8;
import org.eclipse.jdt.ui.tests.quickfix.QuickFixTest22;
import org.eclipse.jdt.ui.tests.quickfix.QuickFixTest9;
import org.eclipse.jdt.ui.tests.quickfix.ReorgQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.ReturnTypeQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.SaveParticipantTest;
import org.eclipse.jdt.ui.tests.quickfix.SerialVersionQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.SurroundWithTemplateTest;
import org.eclipse.jdt.ui.tests.quickfix.TypeAnnotationQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.TypeMismatchQuickFixTests;
import org.eclipse.jdt.ui.tests.quickfix.TypeParameterMismatchTest;
import org.eclipse.jdt.ui.tests.quickfix.UnresolvedMethodsQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.UnresolvedMethodsQuickFixTest16;
import org.eclipse.jdt.ui.tests.quickfix.UnresolvedMethodsQuickFixTest1d8;
import org.eclipse.jdt.ui.tests.quickfix.UnresolvedTypesQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.UnresolvedVariablesQuickFixTest;
import org.eclipse.jdt.ui.tests.quickfix.UtilitiesTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
		QuickFixTest9.class,
		QuickFixTest1d8.class,
		QuickFixTest14.class,
		QuickFixTest15.class,
		QuickFixTest17.class,
		QuickFixTest22.class,
		SerialVersionQuickFixTest.class,
		UtilitiesTest.class,
		UnresolvedTypesQuickFixTest.class,
		UnresolvedVariablesQuickFixTest.class,
		UnresolvedMethodsQuickFixTest.class,
		UnresolvedMethodsQuickFixTest1d8.class,
		UnresolvedMethodsQuickFixTest16.class,
		ReturnTypeQuickFixTest.class,
		LocalCorrectionsQuickFixTest.class,
		LocalCorrectionsQuickFixTest1d7.class,
		LocalCorrectionsQuickFixTest1d8.class,
		LocalCorrectionsQuickFixTest10.class,
		LocalCorrectionsQuickFixTest15.class,
		TypeMismatchQuickFixTests.class,
		ReorgQuickFixTest.class,
		ModifierCorrectionsQuickFixTest.class,
		ModifierCorrectionsQuickFixTest1d7.class,
		ModifierCorrectionsQuickFixTest9.class,
		GetterSetterQuickFixTest.class,
		AssistQuickFixTest.class,
		AssistQuickFixTest1d7.class,
		AssistQuickFixTest1d8.class,
		AssistQuickFixTest10.class,
		AssistQuickFixTest12.class,
		AssistQuickFixTest14.class,
		AssistQuickFixTest15.class,
		AssistQuickFixTest16.class,
		AssistQuickFixTest21.class,
		ChangeNonStaticToStaticTest.class,
		MarkerResolutionTest.class,
		JavadocQuickFixTest.class,
		JavadocQuickFixTest9.class,
		JavadocQuickFixTest16.class,
		JavadocQuickFixTest25.class,
		ConvertForLoopQuickFixTest.class,
		ConvertIterableLoopQuickFixTest.class,
		ConvertIterableLoopQuickFixTest1d7.class,
		AdvancedQuickAssistTest.class,
		AdvancedQuickAssistTest1d7.class,
		AdvancedQuickAssistTest1d8.class,
		AdvancedQuickAssistTest10.class,

		/* CleanUpTestCaseSuite.class, */
		CleanUpStressTest.class,
		CleanUpTest.class,
		CleanUpTest1d4.class,
		CleanUpTest1d5.class,
		CleanUpTest1d6.class,
		CleanUpTest1d7.class,
		CleanUpTest1d8.class,
		CleanUpTest9.class,
		CleanUpTest10.class,
		CleanUpTest11.class,
		CleanUpTest12.class,
		CleanUpTest14.class,
		CleanUpTest15.class,
		CleanUpTest16.class,
		CleanUpTest21.class,
		CleanUpTest22.class,
		CleanUpTest25.class,
		CleanUpAnnotationTest.class,
		SaveParticipantTest.class,
		CleanUpActionTest.class,
		NullAnnotationsCleanUpTest1d8.class,

		QuickFixEnablementTest.class,
		SurroundWithTemplateTest.class,
		TypeParameterMismatchTest.class,
		PropertiesFileQuickAssistTest.class,
		NullAnnotationsQuickFixTest.class,
		NullAnnotationsQuickFixTest1d8.class,
		NullAnnotationsQuickFixTest1d8Mix.class,
		NullAnnotationsQuickFixTest9.class,
		AnnotateAssistTest1d5.class,
		AnnotateAssistTest1d8.class,
		TypeAnnotationQuickFixTest.class})
public class RunQuickFixJavacTests {

}
