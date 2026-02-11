package org.eclipse.jdt.internal.javac;

import java.util.List;

import com.sun.tools.javac.tree.JCTree.JCAnnotatedType;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCAnyPattern;
import com.sun.tools.javac.tree.JCTree.JCArrayAccess;
import com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import com.sun.tools.javac.tree.JCTree.JCAssert;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCAssignOp;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCBindingPattern;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCBreak;
import com.sun.tools.javac.tree.JCTree.JCCase;
import com.sun.tools.javac.tree.JCTree.JCCatch;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCConstantCaseLabel;
import com.sun.tools.javac.tree.JCTree.JCContinue;
import com.sun.tools.javac.tree.JCTree.JCDefaultCaseLabel;
import com.sun.tools.javac.tree.JCTree.JCDoWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCEnhancedForLoop;
import com.sun.tools.javac.tree.JCTree.JCErroneous;
import com.sun.tools.javac.tree.JCTree.JCExports;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCForLoop;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCImport;
import com.sun.tools.javac.tree.JCTree.JCInstanceOf;
import com.sun.tools.javac.tree.JCTree.JCLabeledStatement;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCMemberReference;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCModuleDecl;
import com.sun.tools.javac.tree.JCTree.JCModuleImport;
import com.sun.tools.javac.tree.JCTree.JCNewArray;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCOpens;
import com.sun.tools.javac.tree.JCTree.JCPackageDecl;
import com.sun.tools.javac.tree.JCTree.JCParens;
import com.sun.tools.javac.tree.JCTree.JCPatternCaseLabel;
import com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import com.sun.tools.javac.tree.JCTree.JCProvides;
import com.sun.tools.javac.tree.JCTree.JCRecordPattern;
import com.sun.tools.javac.tree.JCTree.JCRequires;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCSkip;
import com.sun.tools.javac.tree.JCTree.JCSwitch;
import com.sun.tools.javac.tree.JCTree.JCSwitchExpression;
import com.sun.tools.javac.tree.JCTree.JCSynchronized;
import com.sun.tools.javac.tree.JCTree.JCThrow;
import com.sun.tools.javac.tree.JCTree.JCTry;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.sun.tools.javac.tree.JCTree.JCTypeIntersection;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCTypeUnion;
import com.sun.tools.javac.tree.JCTree.JCUnary;
import com.sun.tools.javac.tree.JCTree.JCUses;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.JCWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCWildcard;
import com.sun.tools.javac.tree.JCTree.JCYield;
import com.sun.tools.javac.tree.JCTree.LetExpr;
import com.sun.tools.javac.tree.JCTree.TypeBoundKind;
import com.sun.tools.javac.tree.TreeScanner;

public class DelegatingTreeScanner extends TreeScanner {
	private List<TreeScanner> delegates;

	public DelegatingTreeScanner(List<TreeScanner> delegates) {
		this.delegates = delegates;
	}

    @Override
	public void visitTopLevel(JCCompilationUnit tree) {
    	delegates.forEach(x -> x.visitTopLevel(tree));
    	super.visitTopLevel(tree);
    }

    @Override
	public void visitPackageDef(JCPackageDecl tree) {
    	delegates.forEach(x -> x.visitPackageDef(tree));
    	super.visitPackageDef(tree);
    }

    @Override
    public void visitModuleDef(JCModuleDecl tree) {
    	delegates.forEach(x -> x.visitModuleDef(tree));
    	super.visitModuleDef(tree);
    }

    @Override
    public void visitExports(JCExports tree) {
    	delegates.forEach(x -> x.visitExports(tree));
    	super.visitExports(tree);
    }

    @Override
    public void visitModuleImport(JCModuleImport tree) {
    	delegates.forEach(x -> x.visitModuleImport(tree));
    	super.visitModuleImport(tree);
    }

    @Override
    public void visitOpens(JCOpens tree) {
    	delegates.forEach(x -> x.visitOpens(tree));
    	super.visitOpens(tree);
    }

    @Override
    public void visitProvides(JCProvides tree) {
    	delegates.forEach(x -> x.visitProvides(tree));
    	super.visitProvides(tree);
    }

    @Override
    public void visitRequires(JCRequires tree) {
    	delegates.forEach(x -> x.visitRequires(tree));
    	super.visitRequires(tree);
    }

    @Override
    public void visitUses(JCUses tree) {
    	delegates.forEach(x -> x.visitUses(tree));
    	super.visitUses(tree);
    }

    @Override
	public void visitImport(JCImport tree) {
    	delegates.forEach(x -> x.visitImport(tree));
    	super.visitImport(tree);
    }

    @Override
	public void visitClassDef(JCClassDecl tree) {
    	delegates.forEach(x -> x.visitClassDef(tree));
    	super.visitClassDef(tree);
    }

    @Override
	public void visitMethodDef(JCMethodDecl tree) {
    	delegates.forEach(x -> x.visitMethodDef(tree));
    	super.visitMethodDef(tree);
    }

    @Override
	public void visitVarDef(JCVariableDecl tree) {
    	delegates.forEach(x -> x.visitVarDef(tree));
    	super.visitVarDef(tree);
    }

    @Override
	public void visitSkip(JCSkip tree) {
    	delegates.forEach(x -> x.visitSkip(tree));
    	super.visitSkip(tree);
    }

    @Override
	public void visitBlock(JCBlock tree) {
    	delegates.forEach(x -> x.visitBlock(tree));
    	super.visitBlock(tree);
    }

    @Override
	public void visitDoLoop(JCDoWhileLoop tree) {
    	delegates.forEach(x -> x.visitDoLoop(tree));
    	super.visitDoLoop(tree);
    }

    @Override
	public void visitWhileLoop(JCWhileLoop tree) {
    	delegates.forEach(x -> x.visitWhileLoop(tree));
    	super.visitWhileLoop(tree);
    }

    @Override
	public void visitForLoop(JCForLoop tree) {
    	delegates.forEach(x -> x.visitForLoop(tree));
    	super.visitForLoop(tree);
    }

    @Override
	public void visitForeachLoop(JCEnhancedForLoop tree) {
    	delegates.forEach(x -> x.visitForeachLoop(tree));
    	super.visitForeachLoop(tree);
    }

    @Override
	public void visitLabelled(JCLabeledStatement tree) {
    	delegates.forEach(x -> x.visitLabelled(tree));
    	super.visitLabelled(tree);
    }

    @Override
	public void visitSwitch(JCSwitch tree) {
    	delegates.forEach(x -> x.visitSwitch(tree));
    	super.visitSwitch(tree);
    }

    @Override
	public void visitCase(JCCase tree) {
    	delegates.forEach(x -> x.visitCase(tree));
    	super.visitCase(tree);
    }

    @Override
	public void visitSwitchExpression(JCSwitchExpression tree) {
    	delegates.forEach(x -> x.visitSwitchExpression(tree));
    	super.visitSwitchExpression(tree);
    }

    @Override
	public void visitSynchronized(JCSynchronized tree) {
    	delegates.forEach(x -> x.visitSynchronized(tree));
    	super.visitSynchronized(tree);
    }

    @Override
	public void visitTry(JCTry tree) {
    	delegates.forEach(x -> x.visitTry(tree));
    	super.visitTry(tree);
    }

    @Override
	public void visitCatch(JCCatch tree) {
    	delegates.forEach(x -> x.visitCatch(tree));
    	super.visitCatch(tree);
    }

    @Override
	public void visitConditional(JCConditional tree) {
    	delegates.forEach(x -> x.visitConditional(tree));
    	super.visitConditional(tree);
    }

    @Override
	public void visitIf(JCIf tree) {
    	delegates.forEach(x -> x.visitIf(tree));
    	super.visitIf(tree);
    }

    @Override
	public void visitExec(JCExpressionStatement tree) {
    	delegates.forEach(x -> x.visitExec(tree));
    	super.visitExec(tree);
    }

    @Override
	public void visitBreak(JCBreak tree) {
    	delegates.forEach(x -> x.visitBreak(tree));
    	super.visitBreak(tree);
    }

    @Override
	public void visitYield(JCYield tree) {
    	delegates.forEach(x -> x.visitYield(tree));
    	super.visitYield(tree);
    }

    @Override
	public void visitContinue(JCContinue tree) {
    	delegates.forEach(x -> x.visitContinue(tree));
    	super.visitContinue(tree);
    }

    @Override
	public void visitReturn(JCReturn tree) {
    	delegates.forEach(x -> x.visitReturn(tree));
    	super.visitReturn(tree);
    }

    @Override
	public void visitThrow(JCThrow tree) {
    	delegates.forEach(x -> x.visitThrow(tree));
    	super.visitThrow(tree);
    }

    @Override
	public void visitAssert(JCAssert tree) {
    	delegates.forEach(x -> x.visitAssert(tree));
    	super.visitAssert(tree);
    }

    @Override
	public void visitApply(JCMethodInvocation tree) {
    	delegates.forEach(x -> x.visitApply(tree));
    	super.visitApply(tree);
    }

    @Override
	public void visitNewClass(JCNewClass tree) {
    	delegates.forEach(x -> x.visitNewClass(tree));
    	super.visitNewClass(tree);
    }

    @Override
	public void visitNewArray(JCNewArray tree) {
    	delegates.forEach(x -> x.visitNewArray(tree));
    	super.visitNewArray(tree);
    }

    @Override
	public void visitLambda(JCLambda tree) {
    	delegates.forEach(x -> x.visitLambda(tree));
    	super.visitLambda(tree);
    }

    @Override
	public void visitParens(JCParens tree) {
    	delegates.forEach(x -> x.visitParens(tree));
    	super.visitParens(tree);
    }

    @Override
	public void visitAssign(JCAssign tree) {
    	delegates.forEach(x -> x.visitAssign(tree));
    	super.visitAssign(tree);
    }

    @Override
	public void visitAssignop(JCAssignOp tree) {
    	delegates.forEach(x -> x.visitAssignop(tree));
    	super.visitAssignop(tree);
    }

    @Override
	public void visitUnary(JCUnary tree) {
    	delegates.forEach(x -> x.visitUnary(tree));
    	super.visitUnary(tree);
    }

    @Override
	public void visitBinary(JCBinary tree) {
    	delegates.forEach(x -> x.visitBinary(tree));
    	super.visitBinary(tree);
    }

    @Override
	public void visitTypeCast(JCTypeCast tree) {
    	delegates.forEach(x -> x.visitTypeCast(tree));
    	super.visitTypeCast(tree);
    }

    @Override
	public void visitTypeTest(JCInstanceOf tree) {
    	delegates.forEach(x -> x.visitTypeTest(tree));
    	super.visitTypeTest(tree);
    }

    @Override
	public void visitBindingPattern(JCBindingPattern tree) {
    	delegates.forEach(x -> x.visitBindingPattern(tree));
    	super.visitBindingPattern(tree);
    }

    @Override
    public void visitDefaultCaseLabel(JCDefaultCaseLabel tree) {
    	delegates.forEach(x -> x.visitDefaultCaseLabel(tree));
    	super.visitDefaultCaseLabel(tree);
    }

    @Override
    public void visitConstantCaseLabel(JCConstantCaseLabel tree) {
    	delegates.forEach(x -> x.visitConstantCaseLabel(tree));
    	super.visitConstantCaseLabel(tree);
    }

    @Override
    public void visitPatternCaseLabel(JCPatternCaseLabel tree) {
    	delegates.forEach(x -> x.visitPatternCaseLabel(tree));
    	super.visitPatternCaseLabel(tree);
    }

    @Override
    public void visitAnyPattern(JCAnyPattern tree) {
    	delegates.forEach(x -> x.visitAnyPattern(tree));
    	super.visitAnyPattern(tree);
    }

    @Override
    public void visitRecordPattern(JCRecordPattern tree) {
    	delegates.forEach(x -> x.visitRecordPattern(tree));
    	super.visitRecordPattern(tree);
    }

    @Override
	public void visitIndexed(JCArrayAccess tree) {
    	delegates.forEach(x -> x.visitIndexed(tree));
    	super.visitIndexed(tree);
    }

    @Override
	public void visitSelect(JCFieldAccess tree) {
    	delegates.forEach(x -> x.visitSelect(tree));
    	super.visitSelect(tree);
    }

    @Override
	public void visitReference(JCMemberReference tree) {
    	delegates.forEach(x -> x.visitReference(tree));
    	super.visitReference(tree);
    }

    @Override
	public void visitIdent(JCIdent tree) {
    	delegates.forEach(x -> x.visitIdent(tree));
    	super.visitIdent(tree);
    }

    @Override
	public void visitLiteral(JCLiteral tree) {
    	delegates.forEach(x -> x.visitLiteral(tree));
    	super.visitLiteral(tree);
    }

    @Override
	public void visitTypeIdent(JCPrimitiveTypeTree tree) {
    	delegates.forEach(x -> x.visitTypeIdent(tree));
    	super.visitTypeIdent(tree);
    }

    @Override
	public void visitTypeArray(JCArrayTypeTree tree) {
    	delegates.forEach(x -> x.visitTypeArray(tree));
    	super.visitTypeArray(tree);
    }

    @Override
	public void visitTypeApply(JCTypeApply tree) {
    	delegates.forEach(x -> x.visitTypeApply(tree));
    	super.visitTypeApply(tree);
    }

    @Override
	public void visitTypeUnion(JCTypeUnion tree) {
    	delegates.forEach(x -> x.visitTypeUnion(tree));
    	super.visitTypeUnion(tree);
    }

    @Override
	public void visitTypeIntersection(JCTypeIntersection tree) {
    	delegates.forEach(x -> x.visitTypeIntersection(tree));
    	super.visitTypeIntersection(tree);
    }

    @Override
	public void visitTypeParameter(JCTypeParameter tree) {
    	delegates.forEach(x -> x.visitTypeParameter(tree));
    	super.visitTypeParameter(tree);
    }

    @Override
    public void visitWildcard(JCWildcard tree) {
    	delegates.forEach(x -> x.visitWildcard(tree));
    	super.visitWildcard(tree);
    }

    @Override
    public void visitTypeBoundKind(TypeBoundKind tree) {
    	delegates.forEach(x -> x.visitTypeBoundKind(tree));
    	super.visitTypeBoundKind(tree);
    }

    @Override
	public void visitModifiers(JCModifiers tree) {
    	delegates.forEach(x -> x.visitModifiers(tree));
    	super.visitModifiers(tree);
    }

    @Override
	public void visitAnnotation(JCAnnotation tree) {
    	delegates.forEach(x -> x.visitAnnotation(tree));
    	super.visitAnnotation(tree);
    }

    @Override
	public void visitAnnotatedType(JCAnnotatedType tree) {
    	delegates.forEach(x -> x.visitAnnotatedType(tree));
    	super.visitAnnotatedType(tree);
    }

    @Override
	public void visitErroneous(JCErroneous tree) {
    	delegates.forEach(x -> x.visitErroneous(tree));
    	super.visitErroneous(tree);
    }

    @Override
	public void visitLetExpr(LetExpr tree) {
    	delegates.forEach(x -> x.visitLetExpr(tree));
    	super.visitLetExpr(tree);
    }
}