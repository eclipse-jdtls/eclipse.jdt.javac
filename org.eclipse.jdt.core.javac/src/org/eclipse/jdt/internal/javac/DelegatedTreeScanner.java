package org.eclipse.jdt.internal.javac;

import shaded.com.sun.tools.javac.tree.JCTree.JCAnnotatedType;
import shaded.com.sun.tools.javac.tree.JCTree.JCAnnotation;
import shaded.com.sun.tools.javac.tree.JCTree.JCAnyPattern;
import shaded.com.sun.tools.javac.tree.JCTree.JCArrayAccess;
import shaded.com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import shaded.com.sun.tools.javac.tree.JCTree.JCAssert;
import shaded.com.sun.tools.javac.tree.JCTree.JCAssign;
import shaded.com.sun.tools.javac.tree.JCTree.JCAssignOp;
import shaded.com.sun.tools.javac.tree.JCTree.JCBinary;
import shaded.com.sun.tools.javac.tree.JCTree.JCBindingPattern;
import shaded.com.sun.tools.javac.tree.JCTree.JCBlock;
import shaded.com.sun.tools.javac.tree.JCTree.JCBreak;
import shaded.com.sun.tools.javac.tree.JCTree.JCCase;
import shaded.com.sun.tools.javac.tree.JCTree.JCCatch;
import shaded.com.sun.tools.javac.tree.JCTree.JCClassDecl;
import shaded.com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import shaded.com.sun.tools.javac.tree.JCTree.JCConditional;
import shaded.com.sun.tools.javac.tree.JCTree.JCConstantCaseLabel;
import shaded.com.sun.tools.javac.tree.JCTree.JCContinue;
import shaded.com.sun.tools.javac.tree.JCTree.JCDefaultCaseLabel;
import shaded.com.sun.tools.javac.tree.JCTree.JCDoWhileLoop;
import shaded.com.sun.tools.javac.tree.JCTree.JCEnhancedForLoop;
import shaded.com.sun.tools.javac.tree.JCTree.JCErroneous;
import shaded.com.sun.tools.javac.tree.JCTree.JCExports;
import shaded.com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import shaded.com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import shaded.com.sun.tools.javac.tree.JCTree.JCForLoop;
import shaded.com.sun.tools.javac.tree.JCTree.JCIdent;
import shaded.com.sun.tools.javac.tree.JCTree.JCIf;
import shaded.com.sun.tools.javac.tree.JCTree.JCImport;
import shaded.com.sun.tools.javac.tree.JCTree.JCInstanceOf;
import shaded.com.sun.tools.javac.tree.JCTree.JCLabeledStatement;
import shaded.com.sun.tools.javac.tree.JCTree.JCLambda;
import shaded.com.sun.tools.javac.tree.JCTree.JCLiteral;
import shaded.com.sun.tools.javac.tree.JCTree.JCMemberReference;
import shaded.com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import shaded.com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import shaded.com.sun.tools.javac.tree.JCTree.JCModifiers;
import shaded.com.sun.tools.javac.tree.JCTree.JCModuleDecl;
import shaded.com.sun.tools.javac.tree.JCTree.JCModuleImport;
import shaded.com.sun.tools.javac.tree.JCTree.JCNewArray;
import shaded.com.sun.tools.javac.tree.JCTree.JCNewClass;
import shaded.com.sun.tools.javac.tree.JCTree.JCOpens;
import shaded.com.sun.tools.javac.tree.JCTree.JCPackageDecl;
import shaded.com.sun.tools.javac.tree.JCTree.JCParens;
import shaded.com.sun.tools.javac.tree.JCTree.JCPatternCaseLabel;
import shaded.com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import shaded.com.sun.tools.javac.tree.JCTree.JCProvides;
import shaded.com.sun.tools.javac.tree.JCTree.JCRecordPattern;
import shaded.com.sun.tools.javac.tree.JCTree.JCRequires;
import shaded.com.sun.tools.javac.tree.JCTree.JCReturn;
import shaded.com.sun.tools.javac.tree.JCTree.JCSkip;
import shaded.com.sun.tools.javac.tree.JCTree.JCSwitch;
import shaded.com.sun.tools.javac.tree.JCTree.JCSwitchExpression;
import shaded.com.sun.tools.javac.tree.JCTree.JCSynchronized;
import shaded.com.sun.tools.javac.tree.JCTree.JCThrow;
import shaded.com.sun.tools.javac.tree.JCTree.JCTry;
import shaded.com.sun.tools.javac.tree.JCTree.JCTypeApply;
import shaded.com.sun.tools.javac.tree.JCTree.JCTypeCast;
import shaded.com.sun.tools.javac.tree.JCTree.JCTypeIntersection;
import shaded.com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import shaded.com.sun.tools.javac.tree.JCTree.JCTypeUnion;
import shaded.com.sun.tools.javac.tree.JCTree.JCUnary;
import shaded.com.sun.tools.javac.tree.JCTree.JCUses;
import shaded.com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import shaded.com.sun.tools.javac.tree.JCTree.JCWhileLoop;
import shaded.com.sun.tools.javac.tree.JCTree.JCWildcard;
import shaded.com.sun.tools.javac.tree.JCTree.JCYield;
import shaded.com.sun.tools.javac.tree.JCTree.LetExpr;
import shaded.com.sun.tools.javac.tree.JCTree.TypeBoundKind;
import shaded.com.sun.tools.javac.tree.TreeScanner;

public class DelegatedTreeScanner extends TreeScanner {
    @Override
	public void visitTopLevel(JCCompilationUnit tree) {
    }

    @Override
	public void visitPackageDef(JCPackageDecl tree) {
    }

    @Override
    public void visitModuleDef(JCModuleDecl tree) {
    }

    @Override
    public void visitExports(JCExports tree) {
    }

    @Override
    public void visitModuleImport(JCModuleImport tree) {
    }

    @Override
    public void visitOpens(JCOpens tree) {
    }

    @Override
    public void visitProvides(JCProvides tree) {
    }

    @Override
    public void visitRequires(JCRequires tree) {
    }

    @Override
    public void visitUses(JCUses tree) {
    }

    @Override
	public void visitImport(JCImport tree) {
    }

    @Override
	public void visitClassDef(JCClassDecl tree) {
    }

    @Override
	public void visitMethodDef(JCMethodDecl tree) {
    }

    @Override
	public void visitVarDef(JCVariableDecl tree) {
    }

    @Override
	public void visitSkip(JCSkip tree) {
    }

    @Override
	public void visitBlock(JCBlock tree) {
    }

    @Override
	public void visitDoLoop(JCDoWhileLoop tree) {
    }

    @Override
	public void visitWhileLoop(JCWhileLoop tree) {
    }

    @Override
	public void visitForLoop(JCForLoop tree) {
    }

    @Override
	public void visitForeachLoop(JCEnhancedForLoop tree) {
    }

    @Override
	public void visitLabelled(JCLabeledStatement tree) {
    }

    @Override
	public void visitSwitch(JCSwitch tree) {
    }

    @Override
	public void visitCase(JCCase tree) {
    }

    @Override
	public void visitSwitchExpression(JCSwitchExpression tree) {
    }

    @Override
	public void visitSynchronized(JCSynchronized tree) {
    }

    @Override
	public void visitTry(JCTry tree) {
    }

    @Override
	public void visitCatch(JCCatch tree) {
    }

    @Override
	public void visitConditional(JCConditional tree) {
    }

    @Override
	public void visitIf(JCIf tree) {
    }

    @Override
	public void visitExec(JCExpressionStatement tree) {
    }

    @Override
	public void visitBreak(JCBreak tree) {
    }

    @Override
	public void visitYield(JCYield tree) {
    }

    @Override
	public void visitContinue(JCContinue tree) {
    }

    @Override
	public void visitReturn(JCReturn tree) {
    }

    @Override
	public void visitThrow(JCThrow tree) {
    }

    @Override
	public void visitAssert(JCAssert tree) {
    }

    @Override
	public void visitApply(JCMethodInvocation tree) {
    }

    @Override
	public void visitNewClass(JCNewClass tree) {
    }

    @Override
	public void visitNewArray(JCNewArray tree) {
    }

    @Override
	public void visitLambda(JCLambda tree) {
    }

    @Override
	public void visitParens(JCParens tree) {
    }

    @Override
	public void visitAssign(JCAssign tree) {
    }

    @Override
	public void visitAssignop(JCAssignOp tree) {
    }

    @Override
	public void visitUnary(JCUnary tree) {
    }

    @Override
	public void visitBinary(JCBinary tree) {
    }

    @Override
	public void visitTypeCast(JCTypeCast tree) {
    }

    @Override
	public void visitTypeTest(JCInstanceOf tree) {
    }

    @Override
	public void visitBindingPattern(JCBindingPattern tree) {
    }

    @Override
    public void visitDefaultCaseLabel(JCDefaultCaseLabel tree) {
    }

    @Override
    public void visitConstantCaseLabel(JCConstantCaseLabel tree) {
    }

    @Override
    public void visitPatternCaseLabel(JCPatternCaseLabel tree) {
    }

    @Override
    public void visitAnyPattern(JCAnyPattern that) {
    }

    @Override
    public void visitRecordPattern(JCRecordPattern that) {
    }

    @Override
	public void visitIndexed(JCArrayAccess tree) {
    }

    @Override
	public void visitSelect(JCFieldAccess tree) {
    }

    @Override
	public void visitReference(JCMemberReference tree) {
    }

    @Override
	public void visitIdent(JCIdent tree) {
    }

    @Override
	public void visitLiteral(JCLiteral tree) {
    }

    @Override
	public void visitTypeIdent(JCPrimitiveTypeTree tree) {
    }

    @Override
	public void visitTypeArray(JCArrayTypeTree tree) {
    }

    @Override
	public void visitTypeApply(JCTypeApply tree) {
    }

    @Override
	public void visitTypeUnion(JCTypeUnion tree) {
    }

    @Override
	public void visitTypeIntersection(JCTypeIntersection tree) {
    }

    @Override
	public void visitTypeParameter(JCTypeParameter tree) {
    }

    @Override
    public void visitWildcard(JCWildcard tree) {
    }

    @Override
    public void visitTypeBoundKind(TypeBoundKind that) {
    }

    @Override
	public void visitModifiers(JCModifiers tree) {
    }

    @Override
	public void visitAnnotation(JCAnnotation tree) {
    }

    @Override
	public void visitAnnotatedType(JCAnnotatedType tree) {
    }

    @Override
	public void visitErroneous(JCErroneous tree) {
    }

    @Override
	public void visitLetExpr(LetExpr tree) {
    }
}