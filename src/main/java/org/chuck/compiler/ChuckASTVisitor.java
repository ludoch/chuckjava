package org.chuck.compiler;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.chuck.compiler.ChuckANTLRParser.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps ANTLR4 Parse Tree to ChuckAST.
 * Production Grade Visitor.
 */
public class ChuckASTVisitor extends ChuckANTLRBaseVisitor<Object> {

    @Override
    public List<ChuckAST.Stmt> visitProgram(ProgramContext ctx) {
        return ctx.children.stream()
                .filter(c -> c instanceof StatementContext || c instanceof FunctionDefContext || c instanceof ClassDefContext)
                .map(c -> (ChuckAST.Stmt) visit(c))
                .filter(s -> s != null)
                .collect(Collectors.toList());
    }

    // --- Statements ---

    @Override public ChuckAST.Stmt visitIfStmt(IfStmtContext ctx) { return (ChuckAST.Stmt) visit(ctx.ifStatement()); }
    @Override public ChuckAST.Stmt visitWhileStmt(WhileStmtContext ctx) { return (ChuckAST.Stmt) visit(ctx.whileStatement()); }
    @Override public ChuckAST.Stmt visitUntilStmt(UntilStmtContext ctx) { return (ChuckAST.Stmt) visit(ctx.untilStatement()); }
    @Override public ChuckAST.Stmt visitForStmt(ForStmtContext ctx) { return (ChuckAST.Stmt) visit(ctx.forStatement()); }
    @Override public ChuckAST.Stmt visitRepeatStmt(RepeatStmtContext ctx) { return (ChuckAST.Stmt) visit(ctx.repeatStatement()); }
    @Override public ChuckAST.Stmt visitDoStmt(DoStmtContext ctx) { return (ChuckAST.Stmt) visit(ctx.doStatement()); }
    @Override public ChuckAST.Stmt visitReturnStmt(ReturnStmtContext ctx) { return (ChuckAST.Stmt) visit(ctx.returnStatement()); }
    @Override public ChuckAST.Stmt visitPrintStmt(PrintStmtContext ctx) { return (ChuckAST.Stmt) visit(ctx.printStatement()); }
    @Override public ChuckAST.Stmt visitBlockStmt(BlockStmtContext ctx) { return (ChuckAST.Stmt) visit(ctx.blockStatement()); }
    @Override public ChuckAST.Stmt visitEmptyStmt(EmptyStmtContext ctx) { return null; }
    @Override public ChuckAST.Stmt visitBreakStmt(BreakStmtContext ctx) { return new ChuckAST.BreakStmt(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); }

    @Override 
    public ChuckAST.Stmt visitExpressionStmt(ExpressionStmtContext ctx) { 
        Object result = visit(ctx.expression());
        if (result instanceof ChuckAST.Stmt) {
            return (ChuckAST.Stmt) result;
        } else if (result instanceof ChuckAST.Exp) {
            return new ChuckAST.ExpStmt((ChuckAST.Exp) result, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }
        return null;
    }

    // --- Statement Implementations ---

    @Override
    public ChuckAST.Stmt visitIfStatement(IfStatementContext ctx) {
        ChuckAST.Exp cond = (ChuckAST.Exp) visit(ctx.expression());
        ChuckAST.Stmt thenBranch = (ChuckAST.Stmt) visit(ctx.statement(0));
        ChuckAST.Stmt elseBranch = ctx.statement().size() > 1 ? (ChuckAST.Stmt) visit(ctx.statement(1)) : null;
        return new ChuckAST.IfStmt(cond, thenBranch, elseBranch, 
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Stmt visitWhileStatement(WhileStatementContext ctx) {
        return new ChuckAST.WhileStmt((ChuckAST.Exp) visit(ctx.expression()), (ChuckAST.Stmt) visit(ctx.statement()),
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Stmt visitUntilStatement(UntilStatementContext ctx) {
        return new ChuckAST.UntilStmt((ChuckAST.Exp) visit(ctx.expression()), (ChuckAST.Stmt) visit(ctx.statement()),
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Stmt visitDoStatement(DoStatementContext ctx) {
        boolean isUntil = ctx.UNTIL() != null;
        return new ChuckAST.DoStmt((ChuckAST.Stmt) visit(ctx.statement()), (ChuckAST.Exp) visit(ctx.expression()),
            isUntil, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override public ChuckAST.Stmt visitContinueStmt(ContinueStmtContext ctx) {
        return new ChuckAST.ContinueStmt(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Stmt visitForStatement(ForStatementContext ctx) {
        if (ctx.COLON() != null) {
            String type = ctx.getChild(2).getText();
            String name = ctx.ID().getText();
            ChuckAST.Exp coll = (ChuckAST.Exp) visit(ctx.expression(0));
            return new ChuckAST.ForEachStmt(type, name, coll, (ChuckAST.Stmt) visit(ctx.statement()), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }
        return (ChuckAST.Stmt) visit(ctx.statement());
    }

    @Override
    public ChuckAST.Stmt visitRepeatStatement(RepeatStatementContext ctx) {
        return new ChuckAST.RepeatStmt((ChuckAST.Exp) visit(ctx.expression()), (ChuckAST.Stmt) visit(ctx.statement()),
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Stmt visitReturnStatement(ReturnStatementContext ctx) {
        ChuckAST.Exp exp = ctx.expression() != null ? (ChuckAST.Exp) visit(ctx.expression()) : null;
        return new ChuckAST.ReturnStmt(exp, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Stmt visitPrintStatement(PrintStatementContext ctx) {
        List<ChuckAST.Exp> exps = ctx.expressionList() != null ? (List<ChuckAST.Exp>) visit(ctx.expressionList()) : new ArrayList<>();
        return new ChuckAST.PrintStmt(exps, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Stmt visitBlockStatement(BlockStatementContext ctx) {
        List<ChuckAST.Stmt> stmts = ctx.statement().stream()
                .map(s -> (ChuckAST.Stmt) visit(s))
                .filter(s -> s != null)
                .collect(Collectors.toList());
        return new ChuckAST.BlockStmt(stmts, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    // --- Expressions ---

    @Override
    public Object visitDeclExp(DeclExpContext ctx) {
        String typeStr = ctx.type().getText();
        VariableDeclContext v = ctx.variableDecl(0);
        
        List<ChuckAST.Exp> arraySizes = new ArrayList<>();
        for (ChuckANTLRParser.ArrayDimensionContext ad : v.arrayDimension()) {
            if (ad.expression() != null) {
                arraySizes.add((ChuckAST.Exp) visit(ad.expression()));
            } else {
                arraySizes.add(new ChuckAST.IntExp(-1, ad.getStart().getLine(), ad.getStart().getCharPositionInLine()));
            }
        }
        
        boolean isRef = v.REFERENCE_TAG() != null;
        if (typeStr.equals("vec3") || typeStr.equals("vec4") || typeStr.equals("complex") || typeStr.equals("polar")) {
            isRef = true;
        }
        
        ChuckAST.DeclExp declExp = new ChuckAST.DeclExp(typeStr, v.ID().getText(), arraySizes, null, isRef, ctx.STATIC() != null, false,
                v.getStart().getLine(), v.getStart().getCharPositionInLine());            
        if (v.CHUCK_OP() != null) {
            ChuckAST.Exp rhs = (ChuckAST.Exp) visit(v.expression());
            ChuckAST.Operator op = mapChuckOp(v.CHUCK_OP().getText());
            return new ChuckAST.BinaryExp(declExp, op, rhs, v.getStart().getLine(), v.getStart().getCharPositionInLine());
        }
        
        return declExp;
    }

    private ChuckAST.Operator mapChuckOp(String opText) {
        return switch (opText) {
            case "=>" -> ChuckAST.Operator.CHUCK;
            case "@=>" -> ChuckAST.Operator.AT_CHUCK;
            case "!=>" -> ChuckAST.Operator.UNCHUCK;
            case "<=>" -> ChuckAST.Operator.SWAP;
            case "<=" -> ChuckAST.Operator.WRITE_IO;
            case "=<" -> ChuckAST.Operator.UNCHUCK;
            case "=^" -> ChuckAST.Operator.UPCHUCK;
            case "+=>" -> ChuckAST.Operator.PLUS_CHUCK;
            case "-=>" -> ChuckAST.Operator.MINUS_CHUCK;
            case "*=>" -> ChuckAST.Operator.TIMES_CHUCK;
            case "/=>" -> ChuckAST.Operator.DIVIDE_CHUCK;
            case "%=>" -> ChuckAST.Operator.PERCENT_CHUCK;
            default -> ChuckAST.Operator.CHUCK;
        };
    }

    @Override
    public ChuckAST.Exp visitDurationOp(DurationOpContext ctx) {
        return new ChuckAST.BinaryExp((ChuckAST.Exp) visit(ctx.expression(0)), ChuckAST.Operator.NONE, (ChuckAST.Exp) visit(ctx.expression(1)),
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Exp visitPostfixOp(PostfixOpContext ctx) {
        ChuckAST.Operator op = ctx.getChild(1).getText().equals("++") ? ChuckAST.Operator.PLUS : ChuckAST.Operator.MINUS;
        return new ChuckAST.UnaryExp(op, (ChuckAST.Exp) visit(ctx.expression()), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Exp visitChuckOp(ChuckOpContext ctx) {
        ChuckAST.Operator op = mapChuckOp(ctx.CHUCK_OP().getText());
        Object left = visit(ctx.expression(0));
        Object right = visit(ctx.expression(1));
        
        ChuckAST.Exp lExp = left instanceof ChuckAST.Exp ? (ChuckAST.Exp) left : new ChuckAST.IntExp(0, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        ChuckAST.Exp rExp = right instanceof ChuckAST.Exp ? (ChuckAST.Exp) right : new ChuckAST.IntExp(0, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        
        return new ChuckAST.BinaryExp(lExp, op, rExp, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Exp visitBinaryOp(BinaryOpContext ctx) {
        ChuckAST.Operator op = switch (ctx.getChild(1).getText()) {
            case "+" -> ChuckAST.Operator.PLUS;
            case "-" -> ChuckAST.Operator.MINUS;
            case "*" -> ChuckAST.Operator.TIMES;
            case "/" -> ChuckAST.Operator.DIVIDE;
            case "%" -> ChuckAST.Operator.PERCENT;
            case "&&" -> ChuckAST.Operator.AND;
            case "||" -> ChuckAST.Operator.OR;
            case "<<" -> ChuckAST.Operator.SHIFT_LEFT;
            case ">>" -> ChuckAST.Operator.SHIFT_RIGHT;
            default -> ChuckAST.Operator.NONE;
        };
        return new ChuckAST.BinaryExp((ChuckAST.Exp) visit(ctx.expression(0)), op, (ChuckAST.Exp) visit(ctx.expression(1)),
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Exp visitUnaryOp(UnaryOpContext ctx) {
        String opStr = ctx.prefixOp().getText();
        ChuckAST.Operator op = switch (opStr) {
            case "-" -> ChuckAST.Operator.MINUS;
            case "!" -> ChuckAST.Operator.S_OR;
            case "++" -> ChuckAST.Operator.PLUS;
            case "--" -> ChuckAST.Operator.MINUS;
            case "~" -> ChuckAST.Operator.NONE;
            default -> ChuckAST.Operator.NONE;
        };
        
        ChuckAST.Exp subExp = (ChuckAST.Exp) visit(ctx.expression());
        if (opStr.startsWith("spork")) {
            if (subExp instanceof ChuckAST.CallExp) {
                return new ChuckAST.SporkExp((ChuckAST.CallExp) subExp, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
            }
        }
        return new ChuckAST.UnaryExp(op, subExp, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override public ChuckAST.Exp visitIntLit(IntLitContext ctx) { 
        String text = ctx.getText();
        long val;
        try {
            if (text.startsWith("0x")) val = Long.parseLong(text.substring(2), 16);
            else if (text.startsWith("'")) val = text.charAt(1);
            else val = Long.parseLong(text);
        } catch (Exception e) { val = 0; }
        return new ChuckAST.IntExp(val, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); 
    }
    @Override public ChuckAST.Exp visitFloatLit(FloatLitContext ctx) { return new ChuckAST.FloatExp(Double.parseDouble(ctx.getText()), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); }
    @Override public ChuckAST.Exp visitStringLit(StringLitContext ctx) { 
        String s = ctx.getText();
        if (s.length() < 2) return new ChuckAST.StringExp("", ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        return new ChuckAST.StringExp(s.substring(1, s.length() - 1), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); 
    }
    @Override public ChuckAST.Exp visitTrueLit(TrueLitContext ctx) { return new ChuckAST.IntExp(1, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); }
    @Override public ChuckAST.Exp visitFalseLit(FalseLitContext ctx) { return new ChuckAST.IntExp(0, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); }
    @Override public ChuckAST.Exp visitNullLit(NullLitContext ctx) { return new ChuckAST.IntExp(0, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); }
    @Override public ChuckAST.Exp visitNowExp(NowExpContext ctx) { return new ChuckAST.IdExp("now", ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); }
    @Override public ChuckAST.Exp visitMeExp(MeExpContext ctx) { return new ChuckAST.IdExp("me", ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); }
    @Override public ChuckAST.Exp visitIdExp(IdExpContext ctx) { return new ChuckAST.IdExp(ctx.getText(), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); }

    @Override
    public ChuckAST.Exp visitMemberExp(MemberExpContext ctx) {
        return new ChuckAST.DotExp((ChuckAST.Exp) visit(ctx.primary()), ctx.memberName().getText(), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }
    @Override
    public ChuckAST.Exp visitArrayAccessExp(ChuckANTLRParser.ArrayAccessExpContext ctx) {
        ChuckAST.Exp base = (ChuckAST.Exp) visit(ctx.primary());
        List<ChuckAST.Exp> indices = (List<ChuckAST.Exp>) visit(ctx.expressionList());
        return new ChuckAST.ArrayAccessExp(base, indices, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Exp visitCallExp(CallExpContext ctx) {
        List<ChuckAST.Exp> args = ctx.expressionList() != null ? (List<ChuckAST.Exp>) visit(ctx.expressionList()) : new ArrayList<>();
        return new ChuckAST.CallExp((ChuckAST.Exp) visit(ctx.primary()), args, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Exp visitParenExp(ParenExpContext ctx) {
        if (ctx.expressionList() == null) {
            // empty () — void expression
            return new ChuckAST.IntExp(0, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }
        List<ChuckAST.Exp> exps = (List<ChuckAST.Exp>) visit(ctx.expressionList());
        // single expression: unwrap; multi-value: return first (caller uses chuck semantics)
        return exps.isEmpty() ? new ChuckAST.IntExp(0, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine())
                              : exps.get(0);
    }

    @Override
    public ChuckAST.Exp visitArrayLitExp(ArrayLitExpContext ctx) {
        List<ChuckAST.Exp> elements = ctx.expressionList() != null ? (List<ChuckAST.Exp>) visit(ctx.expressionList()) : new ArrayList<>();
        return new ChuckAST.ArrayLitExp(elements, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public List<ChuckAST.Exp> visitExpressionList(ExpressionListContext ctx) {
        return ctx.expression().stream().map(e -> {
            Object res = visit(e);
            return res instanceof ChuckAST.Exp ? (ChuckAST.Exp) res : new ChuckAST.IntExp(0, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }).collect(Collectors.toList());
    }

    @Override public ChuckAST.Exp visitComplexLit(ComplexLitContext ctx) { 
        return new ChuckAST.ComplexLit((ChuckAST.Exp) visit(ctx.expression(0)), (ChuckAST.Exp) visit(ctx.expression(1)),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }
    @Override public ChuckAST.Exp visitPolarLit(PolarLitContext ctx) { 
        return new ChuckAST.PolarLit((ChuckAST.Exp) visit(ctx.expression(0)), (ChuckAST.Exp) visit(ctx.expression(1)),
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    // --- Definitions ---

    @Override
    public ChuckAST.Stmt visitFunctionDef(FunctionDefContext ctx) {
        String retType = ctx.type() != null ? ctx.type().getText() : "void";
        String name = ctx.functionName().getText();
        boolean isStatic = ctx.STATIC() != null;
        List<String> argTypes = new ArrayList<>();
        List<String> argNames = new ArrayList<>();
        if (ctx.formalParameters() != null) {
            for (FormalParameterContext p : ctx.formalParameters().formalParameter()) {
                argTypes.add(p.type().getText());
                argNames.add(p.ID().getText());
            }
        }
        return new ChuckAST.FuncDefStmt(retType, name, argTypes, argNames, (ChuckAST.Stmt) visit(ctx.statement()), isStatic, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Stmt visitClassDef(ClassDefContext ctx) {
        String name = ctx.ID().getText();
        String parentName = ctx.EXTENDS() != null ? ctx.typeName().getText() : null;
        List<ChuckAST.Stmt> body = ctx.children.stream()
            .filter(c -> c instanceof StatementContext || c instanceof FunctionDefContext)
            .map(c -> (ChuckAST.Stmt) visit(c))
            .filter(s -> s != null)
            .collect(Collectors.toList());
        return new ChuckAST.ClassDefStmt(name, parentName, body, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Exp visitVectorLitExp(ChuckANTLRParser.VectorLitExpContext ctx) {
        List<ChuckAST.Exp> elements = ctx.expressionList() != null ? (List<ChuckAST.Exp>) visit(ctx.expressionList()) : new ArrayList<>();
        return new ChuckAST.VectorLitExp(elements, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }
}
