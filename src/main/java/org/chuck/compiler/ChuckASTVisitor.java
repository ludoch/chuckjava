package org.chuck.compiler;

import org.chuck.compiler.ChuckANTLRParser.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps ANTLR4 Parse Tree to ChuckAST.
 * Production Grade Visitor.
 */
@SuppressWarnings("unchecked")
public class ChuckASTVisitor extends ChuckANTLRBaseVisitor<Object> {

    @Override
    public List<ChuckAST.Stmt> visitProgram(ProgramContext ctx) {
        return ctx.children.stream()
                .filter(c -> c instanceof DirectiveContext || c instanceof StatementContext || c instanceof FunctionDefContext || c instanceof ClassDefinitionContext)
                .map(c -> (ChuckAST.Stmt) visit(c))
                .filter(s -> s != null)
                .collect(Collectors.toList());
    }

    @Override
    public ChuckAST.Stmt visitDirective(DirectiveContext ctx) {
        if (ctx.IMPORT() != null) {
            String path = ctx.STRING().getText();
            // Remove quotes
            if (path.startsWith("\"") && path.endsWith("\"")) {
                path = path.substring(1, path.length() - 1);
            }
            return new ChuckAST.ImportStmt(path, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }
        return null;
    }

    // --- Statements ---

    @Override public ChuckAST.Stmt visitIfStmt(IfStmtContext ctx) { return (ChuckAST.Stmt) visit(ctx.ifStatement()); }
    @Override public ChuckAST.Stmt visitSwitchStmt(SwitchStmtContext ctx) { return (ChuckAST.Stmt) visit(ctx.switchStatement()); }
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
        List<ChuckAST.Exp> flattened = new ArrayList<>();
        for (ExpressionContext ectx : ctx.expression()) {
            Object result = visit(ectx);
            if (result instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof ChuckAST.Exp exp) flattened.add(exp);
                }
            } else if (result instanceof ChuckAST.Exp exp) {
                flattened.add(exp);
            }
        }
        
        if (flattened.size() == 1) {
            return new ChuckAST.ExpStmt(flattened.get(0), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        } else {
            List<ChuckAST.Stmt> stmts = flattened.stream()
                    .map(e -> new ChuckAST.ExpStmt(e, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()))
                    .collect(Collectors.toList());
            return new ChuckAST.BlockStmt(stmts, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }
    }

    // --- Statement Implementations ---

    @Override
    public ChuckAST.Stmt visitSwitchStatement(SwitchStatementContext ctx) {
        ChuckAST.Exp cond = (ChuckAST.Exp) visit(ctx.expression());
        List<ChuckAST.CaseStmt> cases = new ArrayList<>();
        for (SwitchCaseContext cctx : ctx.switchCase()) {
            ChuckAST.Exp match = cctx.expression() != null ? (ChuckAST.Exp) visit(cctx.expression()) : null;
            boolean isDefault = cctx.DEFAULT() != null;
            List<ChuckAST.Stmt> body = new ArrayList<>();
            for (StatementContext sctx : cctx.statement()) {
                ChuckAST.Stmt s = (ChuckAST.Stmt) visit(sctx);
                if (s != null) body.add(s);
            }
            cases.add(new ChuckAST.CaseStmt(match, isDefault, body, cctx.getStart().getLine(), cctx.getStart().getCharPositionInLine()));
        }
        return new ChuckAST.SwitchStmt(cond, cases, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

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
            String type = ctx.type() != null ? ctx.type().getText() : (ctx.AUTO() != null ? "auto" : "Object");
            String name = ctx.ID().getText();
            ChuckAST.Exp coll = (ChuckAST.Exp) visit(ctx.expression(0));
            return new ChuckAST.ForEachStmt(type, name, coll, (ChuckAST.Stmt) visit(ctx.statement()), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }

        ChuckAST.Stmt init = null;
        ChuckAST.Stmt cond = null;
        ChuckAST.Exp update = null;

        int childIdx = 2; // skip FOR LPAREN
        if (ctx.getChild(childIdx) instanceof ExpressionContext) {
            init = new ChuckAST.ExpStmt((ChuckAST.Exp) visit(ctx.getChild(childIdx)), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
            childIdx++;
        }
        childIdx++; // skip SEMI

        if (ctx.getChild(childIdx) instanceof ExpressionContext) {
            cond = new ChuckAST.ExpStmt((ChuckAST.Exp) visit(ctx.getChild(childIdx)), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
            childIdx++;
        }
        childIdx++; // skip SEMI

        if (ctx.getChild(childIdx) instanceof ExpressionContext) {
            update = (ChuckAST.Exp) visit(ctx.getChild(childIdx));
        }

        return new ChuckAST.ForStmt(init, cond, update, (ChuckAST.Stmt) visit(ctx.statement()), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
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
        StringBuilder typeBase = new StringBuilder(ctx.type().getText());
        List<ChuckAST.Exp> decls = new ArrayList<>();
        
        boolean isGlobal = ctx.accessModifier() != null && ctx.accessModifier().GLOBAL() != null;
        boolean isStatic = ctx.STATIC() != null;
        boolean isConst = ctx.CONST() != null || (ctx.accessModifier() != null && ctx.accessModifier().CONST() != null);

        if (ctx.variableDecl().size() > 1) {
            for (VariableDeclContext v : ctx.variableDecl()) {
                if (v.CHUCK_OP() != null) {
                    throw new RuntimeException("assignment not allowed in multi-variable declaration");
                }
            }
        }

        for (VariableDeclContext v : ctx.variableDecl()) {
            StringBuilder fullType = new StringBuilder(typeBase);
            List<ChuckAST.Exp> arraySizes = new ArrayList<>();
            for (ChuckANTLRParser.ArrayDimensionContext ad : v.arrayDimension()) {
                fullType.append("[]");
                if (ad.expression() != null) {
                    arraySizes.add((ChuckAST.Exp) visit(ad.expression()));
                } else {
                    arraySizes.add(new ChuckAST.IntExp(-1, ad.getStart().getLine(), ad.getStart().getCharPositionInLine()));
                }
            }
            
            boolean isRef = v.REFERENCE_TAG() != null;
            // A declaration like 'GainDB g(-18)' is NOT a reference declaration,
            // it's an instantiation.
            if (v.LPAREN() != null) isRef = false;
            
            // Also inherit global reference tag if present on the first one or type?
            if (ctx.variableDecl(0).REFERENCE_TAG() != null) isRef = true;
            if (v.LPAREN() != null) isRef = false; // Constructor call always overrides ref

            // vec/complex/polar are value types stored as ChuckArrays — instantiate, don't treat as null reference
            
            ChuckAST.DeclExp declExp = new ChuckAST.DeclExp(fullType.toString(), v.ID().getText(), arraySizes, null, isRef, isStatic, isGlobal, isConst,
                    v.getStart().getLine(), v.getStart().getCharPositionInLine());            
            
            if (v.CHUCK_OP() != null) {
                Object result = visit(v.expression());
                ChuckAST.Exp rhs;
                if (result instanceof List<?> list) {
                    if (list.size() > 1) {
                        throw new RuntimeException("cannot '=>' from/to a multi-variable declaration");
                    }
                    rhs = (ChuckAST.Exp) list.get(0);
                } else {
                    rhs = (ChuckAST.Exp) result;
                }
                ChuckAST.Operator op = mapChuckOp(v.CHUCK_OP().getText());
                decls.add(new ChuckAST.BinaryExp(declExp, op, rhs, v.getStart().getLine(), v.getStart().getCharPositionInLine()));
            } else {
                decls.add(declExp);
            }
        }
        
        if (decls.size() == 1) return decls.get(0);
        return decls;
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
        return new ChuckAST.BinaryExp((ChuckAST.Exp) visit(ctx.expression(0)), ChuckAST.Operator.DUR_MUL, (ChuckAST.Exp) visit(ctx.expression(1)),
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Exp visitPostfixOp(PostfixOpContext ctx) {
        ChuckAST.Operator op = ctx.getChild(1).getText().equals("++") ?
            ChuckAST.Operator.POSTFIX_PLUS_PLUS : ChuckAST.Operator.POSTFIX_MINUS_MINUS;
        ChuckAST.Exp exp = (ChuckAST.Exp) visit(ctx.expression());
        int line = ctx.getStart().getLine();
        int col = ctx.getStart().getCharPositionInLine();
        // Emitter expects BinaryExp(lhs=IntExp(1), POSTFIX_PLUS_PLUS, rhs=variable)
        return new ChuckAST.BinaryExp(new ChuckAST.IntExp(1, line, col), op, exp, line, col);
    }

    @Override
    public ChuckAST.Exp visitConditionalOp(ConditionalOpContext ctx) {
        ChuckAST.Exp condition = (ChuckAST.Exp) visit(ctx.expression(0));
        ChuckAST.Exp thenExp   = (ChuckAST.Exp) visit(ctx.expression(1));
        ChuckAST.Exp elseExp   = (ChuckAST.Exp) visit(ctx.expression(2));
        return new ChuckAST.TernaryExp(condition, thenExp, elseExp,
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Exp visitChuckOp(ChuckOpContext ctx) {
        ChuckAST.Operator op = mapChuckOp(ctx.CHUCK_OP().getText());
        Object left = visit(ctx.expression(0));
        Object right = visit(ctx.expression(1));
        
        ChuckAST.Exp lExp;
        if (left instanceof List<?> list) {
            if (list.size() > 1) throw new RuntimeException("cannot '=>' from/to a multi-variable declaration");
            lExp = (ChuckAST.Exp) list.get(0);
        } else {
            lExp = (ChuckAST.Exp) left;
        }

        ChuckAST.Exp rExp;
        if (right instanceof List<?> list) {
            if (list.size() > 1) throw new RuntimeException("cannot '=>' from/to a multi-variable declaration");
            rExp = (ChuckAST.Exp) list.get(0);
        } else {
            rExp = (ChuckAST.Exp) right;
        }
        
        return new ChuckAST.BinaryExp(lExp, op, rExp, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Exp visitCompareOp(CompareOpContext ctx) {
        ChuckAST.Operator op = ChuckAST.Operator.NONE;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            String text = ctx.getChild(i).getText();
            op = switch (text) {
                case "<" -> ChuckAST.Operator.LT;
                case ">" -> ChuckAST.Operator.GT;
                case "<=" -> ChuckAST.Operator.LE;
                case ">=" -> ChuckAST.Operator.GE;
                case "==" -> ChuckAST.Operator.EQ;
                case "!=" -> ChuckAST.Operator.NEQ;
                default -> ChuckAST.Operator.NONE;
            };
            if (op != ChuckAST.Operator.NONE) break;
        }
        return new ChuckAST.BinaryExp((ChuckAST.Exp) visit(ctx.expression(0)), op, (ChuckAST.Exp) visit(ctx.expression(1)),
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Exp visitBinaryOp(BinaryOpContext ctx) {
        ChuckAST.Operator op = ChuckAST.Operator.NONE;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            String text = ctx.getChild(i).getText();
            op = switch (text) {
                case "+" -> ChuckAST.Operator.PLUS;
                case "-" -> ChuckAST.Operator.MINUS;
                case "*" -> ChuckAST.Operator.TIMES;
                case "/" -> ChuckAST.Operator.DIVIDE;
                case "%" -> ChuckAST.Operator.PERCENT;
                case "&&" -> ChuckAST.Operator.AND;
                case "||" -> ChuckAST.Operator.OR;
                case "<<" -> ChuckAST.Operator.SHIFT_LEFT;
                case ">>" -> ChuckAST.Operator.SHIFT_RIGHT;
                case "&" -> ChuckAST.Operator.S_AND;
                case "|" -> ChuckAST.Operator.S_OR;
                case "^" -> ChuckAST.Operator.NONE;
                default -> ChuckAST.Operator.NONE;
            };
            if (op != ChuckAST.Operator.NONE) break;
        }
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
            if (subExp instanceof ChuckAST.CallExp callExp) {
                return new ChuckAST.SporkExp(callExp, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
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
        } catch (NumberFormatException e) { val = 0; }
        return new ChuckAST.IntExp(val, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); 
    }
    @Override public ChuckAST.Exp visitFloatLit(FloatLitContext ctx) { 
        return new ChuckAST.FloatExp(Double.parseDouble(ctx.getText()), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); 
    }
    @Override public ChuckAST.Exp visitStringLit(StringLitContext ctx) { 
        String s = ctx.getText();
        if (s.length() < 2) return new ChuckAST.StringExp("", ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        String content = s.substring(1, s.length() - 1);
        return new ChuckAST.StringExp(unescape(content), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); 
    }

    private String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '\"' -> sb.append('\"');
                    case '\'' -> sb.append('\'');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    @Override public ChuckAST.Exp visitTrueLit(TrueLitContext ctx) { return new ChuckAST.IntExp(1, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); }
    @Override public ChuckAST.Exp visitFalseLit(FalseLitContext ctx) { return new ChuckAST.IntExp(0, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); }
    @Override public ChuckAST.Exp visitNullLit(NullLitContext ctx) { return new ChuckAST.IdExp("null", ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); }
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
    public ChuckAST.Exp visitCastExp(ChuckANTLRParser.CastExpContext ctx) {
        ChuckAST.Exp value = (ChuckAST.Exp) visit(ctx.expression());
        String targetType = ctx.type().getText();
        return new ChuckAST.CastExp(value, targetType, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
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
        String name = "";
        if (ctx.functionName() != null) {
            name = ctx.functionName().getText();
        } else if (ctx.OPERATOR() != null) {
            name = (ctx.REFERENCE_TAG() != null ? "@" : "") + "operator";
        }
        
        if (ctx.postfixOpToken() != null) {
            name += ctx.postfixOpToken().getText();
        }

        boolean isPublic = ctx.PUBLIC() != null || (ctx.accessModifier() != null && ctx.accessModifier().PUBLIC() != null);
        
        // Find STATIC keyword in any of the possible locations in the prefix
        boolean isStatic = ctx.STATIC() != null;
        if (!isStatic && ctx.getText().startsWith("static")) isStatic = true;
        if (!isStatic && ctx.accessModifier() != null && ctx.accessModifier().getText().equals("static")) isStatic = true; // should not happen per grammar but safe
        
        // Check for static after access modifier: e.g. "public static fun"
        if (!isStatic) {
            String prefix = ctx.getChild(0).getText();
            if (prefix.contains("static")) isStatic = true;
        }

        if (name.startsWith("@operator") || name.startsWith("operator")) {
            String opSym = name.startsWith("@operator") ? name.substring("@operator".length()).trim() : name.substring("operator".length()).trim();
            // Handle formal parenthesis notation like @operator(%)
            if (opSym.startsWith("(") && opSym.endsWith(")")) {
                opSym = opSym.substring(1, opSym.length() - 1).trim();
            }
            if (isPublic) name = "__pub_op__" + opSym;
            else name = "__op__" + opSym;
        }

        List<String> argTypes = new ArrayList<>();
        List<String> argNames = new ArrayList<>();
        if (ctx.formalParameters() != null) {
            for (FormalParameterContext p : ctx.formalParameters().formalParameter()) {
                StringBuilder type = new StringBuilder(p.type().getText());
                if (p.arrayDimension() != null) {
                    for (int i = 0; i < p.arrayDimension().size(); i++) {
                        type.append("[]");
                    }
                }
                argTypes.add(type.toString());
                argNames.add(p.ID().getText());
            }
        }
        return new ChuckAST.FuncDefStmt(retType, name, argTypes, argNames, (ChuckAST.Stmt) visit(ctx.statement()), isStatic, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    @Override
    public ChuckAST.Stmt visitClassDefinition(ClassDefinitionContext ctx) {
        String name = ctx.ID().getText();
        String parentName = ctx.EXTENDS() != null ? ctx.typeName().getText() : null;
        List<ChuckAST.Stmt> body = ctx.children.stream()
            .filter(c -> c instanceof StatementContext || c instanceof FunctionDefContext || c instanceof ClassDefinitionContext)
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

    @Override
    public ChuckAST.Exp visitNewExp(NewExpContext ctx) {
        String typeStr = ctx.typeName().getText();
        List<ChuckAST.Exp> arraySizes = new ArrayList<>();
        boolean isArray = ctx.arrayDimension() != null && !ctx.arrayDimension().isEmpty();
        
        for (ChuckANTLRParser.ArrayDimensionContext ad : ctx.arrayDimension()) {
            if (ad.expression() != null) {
                arraySizes.add((ChuckAST.Exp) visit(ad.expression()));
            } else {
                arraySizes.add(new ChuckAST.IntExp(-1, ad.getStart().getLine(), ad.getStart().getCharPositionInLine()));
            }
        }
        List<ChuckAST.Exp> argList = ctx.expressionList() != null ? (List<ChuckAST.Exp>) visit(ctx.expressionList()) : null;
        ChuckAST.Exp callArgs = null;
        if (argList != null) {
            callArgs = new ChuckAST.ArrayLitExp(argList, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }
        
        // Special prefix to signal emitter this is a 'new' expression
        String namePrefix = isArray ? "@new_array_" : "@new_";
        return new ChuckAST.DeclExp(typeStr, namePrefix + ctx.getStart().getLine() + "_" + ctx.getStart().getCharPositionInLine(), 
                arraySizes, callArgs, false, false, false, false, 
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }
}
