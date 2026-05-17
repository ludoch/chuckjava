package org.chuck.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Token;
import org.chuck.compiler.ChuckANTLRParser.*;

/** Maps ANTLR4 Parse Tree to ChuckAST. Production Grade Visitor. */
@SuppressWarnings("unchecked")
public class ChuckASTVisitor extends ChuckANTLRBaseVisitor<Object> {

  private final BufferedTokenStream tokens;
  private String lastDocComment = null;

  public ChuckASTVisitor() {
    this.tokens = null;
  }

  public ChuckASTVisitor(BufferedTokenStream tokens) {
    this.tokens = tokens;
  }

  private String cleanDoc(String text) {
    if (text == null) return null;
    if (text.startsWith("/**") && text.endsWith("*/")) {
      return text.substring(3, text.length() - 2).trim();
    }
    if (text.startsWith("/*") && text.endsWith("*/")) {
      return text.substring(2, text.length() - 2).trim();
    }
    if (text.startsWith("//")) {
      return text.substring(2).trim();
    }
    return text;
  }

  private void captureDoc(org.antlr.v4.runtime.ParserRuleContext ctx) {
    if (tokens == null) return;
    Token start = ctx.getStart();
    int i = start.getTokenIndex();
    List<Token> hidden = tokens.getHiddenTokensToLeft(i, Token.HIDDEN_CHANNEL);
    if (hidden != null && !hidden.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      for (Token t : hidden) {
        String cleaned = cleanDoc(t.getText());
        if (cleaned != null && !cleaned.isEmpty()) {
          if (sb.length() > 0) sb.append("\n");
          sb.append(cleaned);
        }
      }
      lastDocComment = sb.toString();
    }
  }

  private String consumeDoc() {
    String d = lastDocComment;
    lastDocComment = null;
    return d;
  }

  @Override
  public List<ChuckAST.Stmt> visitProgram(ProgramContext ctx) {
    List<ChuckAST.Stmt> stmts = new ArrayList<>();
    for (org.antlr.v4.runtime.tree.ParseTree c : ctx.children) {
      Object res = visit(c);
      if (res instanceof ChuckAST.Stmt s) stmts.add(s);
      else if (res instanceof List<?> list) {
        for (Object item : list) if (item instanceof ChuckAST.Stmt s) stmts.add(s);
      }
    }
    return stmts;
  }

  @Override
  public ChuckAST.Stmt visitDirective(DirectiveContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    if (ctx.IMPORT() != null) {
      String path = ctx.STRING().getText();
      if (path.startsWith("\"") && path.endsWith("\"")) {
        path = path.substring(1, path.length() - 1);
      }
      return new ChuckAST.ImportStmt(
          path, currentDoc, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }
    if (ctx.ID() != null && !ctx.ID().isEmpty() && ctx.ID(0).getText().equals("doc")) {
      if (ctx.STRING() != null) {
        String text = ctx.STRING().getText();
        if (text.startsWith("\"") && text.endsWith("\"")) {
          text = text.substring(1, text.length() - 1);
        }
        lastDocComment = text;
      }
    }
    return null;
  }

  // --- Statement Adapter Methods (Mapping grammar labels to implementation) ---

  @Override
  public ChuckAST.Stmt visitExampleStmt(ExampleStmtContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    String name = ctx.exampleStatement().ID(1).getText();
    return new ChuckAST.ExampleStmt(
        name, currentDoc, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Stmt visitBlockStmt(BlockStmtContext ctx) {
    return (ChuckAST.Stmt) visit(ctx.blockStatement());
  }

  @Override
  public ChuckAST.Stmt visitLoopStmt(LoopStmtContext ctx) {
    return (ChuckAST.Stmt) visit(ctx.loopStatement());
  }

  @Override
  public ChuckAST.Stmt visitEmptyStmt(EmptyStmtContext ctx) {
    return null;
  }

  @Override
  public ChuckAST.Stmt visitBreakStmt(BreakStmtContext ctx) {
    captureDoc(ctx);
    return new ChuckAST.BreakStmt(
        consumeDoc(), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Stmt visitContinueStmt(ContinueStmtContext ctx) {
    captureDoc(ctx);
    return new ChuckAST.ContinueStmt(
        consumeDoc(), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Stmt visitExpressionStmt(ExpressionStmtContext ctx) {
    captureDoc(ctx);
    // Don't consume yet, let sub-expressions use it if they are DeclExp
    String currentDoc = lastDocComment;

    List<ChuckAST.Exp> flattened = new ArrayList<>();
    for (ExpressionContext ectx : ctx.expression()) {
      Object result = visit(ectx);
      if (result instanceof List<?> list) {
        for (Object item : list) if (item instanceof ChuckAST.Exp exp) flattened.add(exp);
      } else if (result instanceof ChuckAST.Exp exp) {
        flattened.add(exp);
      }
    }

    // Now consume
    consumeDoc();

    List<ChuckAST.Stmt> stmts = new ArrayList<>();
    for (ChuckAST.Exp exp : flattened) {
      if (exp instanceof ChuckAST.DeclExp de) {
        stmts.add(
            new ChuckAST.DeclStmt(
                de.type(),
                de.name(),
                de.arraySizes(),
                de.callArgs(),
                de.isReference(),
                de.isStatic(),
                de.isGlobal(),
                de.isConst(),
                de.access(),
                de.doc(),
                de.line(),
                de.column()));
      } else {
        stmts.add(new ChuckAST.ExpStmt(exp, currentDoc, exp.line(), exp.column()));
      }
    }
    if (stmts.size() == 1) return stmts.get(0);
    return new ChuckAST.BlockStmt(
        stmts, false, currentDoc, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  // --- Statement Implementations ---

  @Override
  public ChuckAST.Stmt visitSwitchStatement(SwitchStatementContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    ChuckAST.Exp cond = (ChuckAST.Exp) visit(ctx.expression());
    List<ChuckAST.CaseStmt> cases = new ArrayList<>();
    for (SwitchCaseContext cctx : ctx.switchCase()) {
      captureDoc(cctx);
      String caseDoc = consumeDoc();
      ChuckAST.Exp match =
          cctx.expression() != null ? (ChuckAST.Exp) visit(cctx.expression()) : null;
      boolean isDefault = cctx.DEFAULT() != null;
      List<ChuckAST.Stmt> body = new ArrayList<>();
      for (org.antlr.v4.runtime.tree.ParseTree child : cctx.children) {
        if (child instanceof StatementContext sctx) {
          ChuckAST.Stmt s = (ChuckAST.Stmt) visit(sctx);
          if (s != null) body.add(s);
        }
      }
      cases.add(
          new ChuckAST.CaseStmt(
              match,
              isDefault,
              body,
              caseDoc,
              cctx.getStart().getLine(),
              cctx.getStart().getCharPositionInLine()));
    }
    return new ChuckAST.SwitchStmt(
        cond, cases, currentDoc, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Stmt visitIfStatement(IfStatementContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    ChuckAST.Exp cond = (ChuckAST.Exp) visit(ctx.expression());
    ChuckAST.Stmt thenBranch = (ChuckAST.Stmt) visit(ctx.statement(0));
    ChuckAST.Stmt elseBranch =
        ctx.statement().size() > 1 ? (ChuckAST.Stmt) visit(ctx.statement(1)) : null;
    return new ChuckAST.IfStmt(
        cond,
        thenBranch,
        elseBranch,
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Stmt visitWhileStatement(WhileStatementContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    return new ChuckAST.WhileStmt(
        (ChuckAST.Exp) visit(ctx.expression()),
        (ChuckAST.Stmt) visit(ctx.statement()),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Stmt visitUntilStatement(UntilStatementContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    return new ChuckAST.UntilStmt(
        (ChuckAST.Exp) visit(ctx.expression()),
        (ChuckAST.Stmt) visit(ctx.statement()),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Stmt visitDoStatement(DoStatementContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    boolean isUntil = ctx.UNTIL() != null;
    return new ChuckAST.DoStmt(
        (ChuckAST.Stmt) visit(ctx.statement()),
        (ChuckAST.Exp) visit(ctx.expression()),
        isUntil,
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Stmt visitForStatement(ForStatementContext ctx) {
    captureDoc(ctx);
    String currentDoc = lastDocComment; // Don't consume, might be ForEach
    if (ctx.COLON() != null) {
      consumeDoc();
      String type =
          ctx.type() != null ? ctx.type().getText() : (ctx.AUTO() != null ? "auto" : "Object");
      ChuckAST.Exp coll = (ChuckAST.Exp) visit(ctx.expression(0));
      return new ChuckAST.ForEachStmt(
          type,
          ctx.ID().getText(),
          coll,
          (ChuckAST.Stmt) visit(ctx.statement()),
          currentDoc,
          ctx.getStart().getLine(),
          ctx.getStart().getCharPositionInLine());
    }
    consumeDoc();
    ChuckAST.Stmt init = null;
    ChuckAST.Stmt cond = null;
    ChuckAST.Exp update = null;
    int childIdx = 2; // skip FOR LPAREN
    if (ctx.getChild(childIdx) instanceof ExpressionContext) {
      Object res = visit(ctx.getChild(childIdx));
      ChuckAST.Exp exp =
          (res instanceof List<?> list && !list.isEmpty())
              ? (ChuckAST.Exp) list.get(0)
              : (res instanceof ChuckAST.Exp e ? e : null);
      if (exp instanceof ChuckAST.DeclExp de) {
        init =
            new ChuckAST.DeclStmt(
                de.type(),
                de.name(),
                de.arraySizes(),
                de.callArgs(),
                de.isReference(),
                de.isStatic(),
                de.isGlobal(),
                de.isConst(),
                de.access(),
                de.doc(),
                de.line(),
                de.column());
      } else if (exp != null) {
        init = new ChuckAST.ExpStmt(exp, "", exp.line(), exp.column());
      }
      childIdx++;
    }
    childIdx++; // skip SEMI
    if (ctx.getChild(childIdx) instanceof ExpressionContext) {
      Object res = visit(ctx.getChild(childIdx));
      ChuckAST.Exp exp =
          (res instanceof List<?> list && !list.isEmpty())
              ? (ChuckAST.Exp) list.get(0)
              : (res instanceof ChuckAST.Exp e ? e : null);
      if (exp != null) cond = new ChuckAST.ExpStmt(exp, "", exp.line(), exp.column());
      childIdx++;
    }
    childIdx++; // skip SEMI
    if (ctx.getChild(childIdx) instanceof ExpressionContext) {
      Object res = visit(ctx.getChild(childIdx));
      update =
          (res instanceof List<?> list && !list.isEmpty())
              ? (ChuckAST.Exp) list.get(0)
              : (res instanceof ChuckAST.Exp e ? e : null);
    }
    return new ChuckAST.ForStmt(
        init,
        cond,
        update,
        (ChuckAST.Stmt) visit(ctx.statement()),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Stmt visitRepeatStatement(RepeatStatementContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    return new ChuckAST.RepeatStmt(
        (ChuckAST.Exp) visit(ctx.expression()),
        (ChuckAST.Stmt) visit(ctx.statement()),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Stmt visitLoopStatement(LoopStatementContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    return new ChuckAST.LoopStmt(
        (ChuckAST.Stmt) visit(ctx.statement()),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Stmt visitReturnStatement(ReturnStatementContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    ChuckAST.Exp exp = ctx.expression() != null ? (ChuckAST.Exp) visit(ctx.expression()) : null;
    return new ChuckAST.ReturnStmt(
        exp, currentDoc, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Stmt visitPrintStatement(PrintStatementContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    List<ChuckAST.Exp> exps =
        ctx.expressionList() != null
            ? (List<ChuckAST.Exp>) visit(ctx.expressionList())
            : new ArrayList<>();
    return new ChuckAST.PrintStmt(
        exps, currentDoc, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Stmt visitBlockStatement(BlockStatementContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    List<ChuckAST.Stmt> stmts = new ArrayList<>();
    for (org.antlr.v4.runtime.tree.ParseTree child : ctx.children) {
      if (child instanceof StatementContext sctx) {
        ChuckAST.Stmt s = (ChuckAST.Stmt) visit(sctx);
        if (s != null) stmts.add(s);
      }
    }
    return new ChuckAST.BlockStmt(
        stmts, true, currentDoc, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  // --- Expressions ---

  @Override
  public ChuckAST.Exp visitDurationOp(DurationOpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    return new ChuckAST.BinaryExp(
        (ChuckAST.Exp) visit(ctx.expression(0)),
        ChuckAST.Operator.DUR_MUL,
        (ChuckAST.Exp) visit(ctx.expression(1)),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitPostfixOp(PostfixOpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    ChuckAST.Operator op =
        ctx.getChild(1).getText().equals("++")
            ? ChuckAST.Operator.POSTFIX_PLUS_PLUS
            : ChuckAST.Operator.POSTFIX_MINUS_MINUS;
    return new ChuckAST.UnaryExp(
        op,
        (ChuckAST.Exp) visit(ctx.expression()),
        true,
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitConditionalOp(ConditionalOpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    return new ChuckAST.TernaryExp(
        (ChuckAST.Exp) visit(ctx.expression(0)),
        (ChuckAST.Exp) visit(ctx.expression(1)),
        (ChuckAST.Exp) visit(ctx.expression(2)),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitChuckOp(ChuckOpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    ChuckAST.Operator op = mapChuckOp(ctx.CHUCK_OP().getText());
    Object left = visit(ctx.expression(0));
    Object right = visit(ctx.expression(1));
    ChuckAST.Exp lExp =
        (left instanceof List<?> list && !list.isEmpty())
            ? (ChuckAST.Exp) list.get(0)
            : (left instanceof ChuckAST.Exp e ? e : null);
    ChuckAST.Exp rExp =
        (right instanceof List<?> list && !list.isEmpty())
            ? (ChuckAST.Exp) list.get(0)
            : (right instanceof ChuckAST.Exp e ? e : null);
    return new ChuckAST.BinaryExp(
        lExp,
        op,
        rExp,
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitCompareOp(CompareOpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    ChuckAST.Operator op = ChuckAST.Operator.NONE;
    for (int i = 0; i < ctx.getChildCount(); i++) {
      String text = ctx.getChild(i).getText();
      op =
          switch (text) {
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
    return new ChuckAST.BinaryExp(
        (ChuckAST.Exp) visit(ctx.expression(0)),
        op,
        (ChuckAST.Exp) visit(ctx.expression(1)),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitLogicalOp(LogicalOpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    String op = ctx.getChild(1).getText();
    return new ChuckAST.LogicalExp(
        (ChuckAST.Exp) visit(ctx.expression(0)),
        op,
        (ChuckAST.Exp) visit(ctx.expression(1)),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitBinaryOp(BinaryOpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    ChuckAST.Operator op = ChuckAST.Operator.NONE;
    for (int i = 0; i < ctx.getChildCount(); i++) {
      String text = ctx.getChild(i).getText();
      op =
          switch (text) {
            case "+" -> ChuckAST.Operator.PLUS;
            case "-" -> ChuckAST.Operator.MINUS;
            case "*" -> ChuckAST.Operator.TIMES;
            case "/" -> ChuckAST.Operator.DIVIDE;
            case "%" -> ChuckAST.Operator.PERCENT;
            case "&&" -> ChuckAST.Operator.AND;
            case "||" -> ChuckAST.Operator.OR;
            case "<<" -> ChuckAST.Operator.SHIFT_LEFT;
            case ">>" -> ChuckAST.Operator.SHIFT_RIGHT;
            case "|" -> ChuckAST.Operator.S_OR;
            case "&" -> ChuckAST.Operator.S_AND;
            case "^" -> ChuckAST.Operator.S_XOR;
            default -> ChuckAST.Operator.NONE;
          };
      if (op != ChuckAST.Operator.NONE) break;
    }
    return new ChuckAST.BinaryExp(
        (ChuckAST.Exp) visit(ctx.expression(0)),
        op,
        (ChuckAST.Exp) visit(ctx.expression(1)),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitUnaryOp(UnaryOpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    String opStr = ctx.prefixOp().getText();
    ChuckAST.Operator op =
        switch (opStr) {
          case "-" -> ChuckAST.Operator.MINUS;
          case "!" -> ChuckAST.Operator.LOGICAL_NOT;
          case "++" -> ChuckAST.Operator.PLUS_PLUS;
          case "--" -> ChuckAST.Operator.MINUS_MINUS;
          default -> ChuckAST.Operator.NONE;
        };
    ChuckAST.Exp subExp = (ChuckAST.Exp) visit(ctx.expression());
    if (opStr.startsWith("spork") && subExp instanceof ChuckAST.CallExp callExp) {
      return new ChuckAST.SporkExp(
          callExp, currentDoc, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }
    return new ChuckAST.UnaryExp(
        op,
        subExp,
        false,
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitIntLit(IntLitContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    String text = ctx.getText();
    long val = 0;
    try {
      if (text.startsWith("0x")) val = Long.parseLong(text.substring(2), 16);
      else if (text.startsWith("'")) val = text.charAt(1);
      else val = Long.parseLong(text);
    } catch (Exception e) {
    }
    return new ChuckAST.IntExp(
        val, currentDoc, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitFloatLit(FloatLitContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    return new ChuckAST.FloatExp(
        Double.parseDouble(ctx.getText()),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitStringLit(StringLitContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    String s = ctx.getText();
    String content = s.length() < 2 ? "" : s.substring(1, s.length() - 1);
    return new ChuckAST.StringExp(
        unescape(content),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
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
      } else sb.append(c);
    }
    return sb.toString();
  }

  @Override
  public ChuckAST.Exp visitTrueLit(TrueLitContext ctx) {
    captureDoc(ctx);
    String d = consumeDoc();
    return new ChuckAST.IntExp(
        1, d, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitFalseLit(FalseLitContext ctx) {
    captureDoc(ctx);
    String d = consumeDoc();
    return new ChuckAST.IntExp(
        0, d, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitNullLit(NullLitContext ctx) {
    captureDoc(ctx);
    String d = consumeDoc();
    return new ChuckAST.IdExp(
        "null", d, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitNowExp(NowExpContext ctx) {
    captureDoc(ctx);
    String d = consumeDoc();
    return new ChuckAST.IdExp(
        "now", d, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitMeExp(MeExpContext ctx) {
    captureDoc(ctx);
    String d = consumeDoc();
    return new ChuckAST.MeExp(d, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitIdExp(IdExpContext ctx) {
    captureDoc(ctx);
    String d = consumeDoc();
    return new ChuckAST.IdExp(
        ctx.getText(), d, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitMemberExp(MemberExpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    return new ChuckAST.DotExp(
        (ChuckAST.Exp) visit(ctx.primary()),
        ctx.memberName().getText(),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitArrayAccessExp(ChuckANTLRParser.ArrayAccessExpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    List<ChuckAST.Exp> indices = (List<ChuckAST.Exp>) visit(ctx.expressionList());
    return new ChuckAST.ArrayAccessExp(
        (ChuckAST.Exp) visit(ctx.primary()),
        indices,
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitCallExp(CallExpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    List<ChuckAST.Exp> args =
        ctx.expressionList() != null
            ? (List<ChuckAST.Exp>) visit(ctx.expressionList())
            : new ArrayList<>();
    return new ChuckAST.CallExp(
        (ChuckAST.Exp) visit(ctx.primary()),
        args,
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitCastExp(ChuckANTLRParser.CastExpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    return new ChuckAST.CastExp(
        (ChuckAST.Exp) visit(ctx.expression()),
        ctx.type().getText(),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitParenExp(ParenExpContext ctx) {
    if (ctx.expressionList() == null)
      return new ChuckAST.IntExp(
          0, "", ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    List<ChuckAST.Exp> exps = (List<ChuckAST.Exp>) visit(ctx.expressionList());
    return exps.isEmpty()
        ? new ChuckAST.IntExp(
            0, "", ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine())
        : exps.get(0);
  }

  @Override
  public ChuckAST.Exp visitArrayLitExp(ArrayLitExpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    List<ChuckAST.Exp> elements =
        ctx.expressionList() != null
            ? (List<ChuckAST.Exp>) visit(ctx.expressionList())
            : new ArrayList<>();
    return new ChuckAST.ArrayLitExp(
        elements, currentDoc, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitVectorLitExp(VectorLitExpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    List<ChuckAST.Exp> elements =
        ctx.expressionList() != null
            ? (List<ChuckAST.Exp>) visit(ctx.expressionList())
            : new ArrayList<>();
    return new ChuckAST.VectorLitExp(
        elements, currentDoc, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
  }

  @Override
  public List<ChuckAST.Exp> visitExpressionList(ExpressionListContext ctx) {
    return ctx.expression().stream()
        .map(
            e -> {
              Object res = visit(e);
              return res instanceof ChuckAST.Exp
                  ? (ChuckAST.Exp) res
                  : new ChuckAST.IntExp(
                      0, "", ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
            })
        .collect(Collectors.toList());
  }

  @Override
  public ChuckAST.Exp visitComplexLit(ComplexLitContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    return new ChuckAST.ComplexLit(
        (ChuckAST.Exp) visit(ctx.expression(0)),
        (ChuckAST.Exp) visit(ctx.expression(1)),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitPolarLit(PolarLitContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    return new ChuckAST.PolarLit(
        (ChuckAST.Exp) visit(ctx.expression(0)),
        (ChuckAST.Exp) visit(ctx.expression(1)),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitTypeofExp(ChuckANTLRParser.TypeofExpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    return new ChuckAST.TypeofExp(
        (ChuckAST.Exp) visit(ctx.expression()),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitInstanceofExp(ChuckANTLRParser.InstanceofExpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    return new ChuckAST.InstanceofExp(
        (ChuckAST.Exp) visit(ctx.expression()),
        ctx.typeName().getText(),
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Exp visitNewExp(NewExpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    String typeStr = ctx.typeName().getText();
    List<ChuckAST.Exp> arraySizes = new ArrayList<>();
    boolean isArray = ctx.arrayDimension() != null && !ctx.arrayDimension().isEmpty();
    for (ChuckANTLRParser.ArrayDimensionContext ad : ctx.arrayDimension()) {
      if (ad.expression() != null) arraySizes.add((ChuckAST.Exp) visit(ad.expression()));
      else
        arraySizes.add(
            new ChuckAST.IntExp(
                -1, "", ad.getStart().getLine(), ad.getStart().getCharPositionInLine()));
    }
    List<ChuckAST.Exp> argList =
        ctx.expressionList() != null ? (List<ChuckAST.Exp>) visit(ctx.expressionList()) : null;
    ChuckAST.Exp callArgs =
        argList != null
            ? new ChuckAST.ArrayLitExp(
                argList, "", ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine())
            : null;
    String namePrefix = isArray ? "@new_array_" : "@new_";
    return new ChuckAST.DeclExp(
        typeStr,
        namePrefix + ctx.getStart().getLine() + "_" + ctx.getStart().getCharPositionInLine(),
        arraySizes,
        callArgs,
        false,
        false,
        false,
        false,
        ChuckAST.AccessModifier.PUBLIC,
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  private ChuckAST.AccessModifier getAccessModifier(AccessModifierContext ctx) {
    if (ctx == null) return ChuckAST.AccessModifier.PUBLIC;
    if (ctx.PRIVATE() != null) return ChuckAST.AccessModifier.PRIVATE;
    if (ctx.PROTECTED() != null) return ChuckAST.AccessModifier.PROTECTED;
    return ChuckAST.AccessModifier.PUBLIC;
  }

  @Override
  public Object visitDeclExp(DeclExpContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    StringBuilder typeBase = new StringBuilder(ctx.type().getText());
    List<ChuckAST.Exp> decls = new ArrayList<>();
    boolean isGlobal = ctx.accessModifier() != null && ctx.accessModifier().GLOBAL() != null;
    boolean isStatic = ctx.STATIC() != null;
    boolean isConst =
        ctx.CONST() != null
            || (ctx.accessModifier() != null && ctx.accessModifier().CONST() != null);
    ChuckAST.AccessModifier access = getAccessModifier(ctx.accessModifier());
    for (VariableDeclContext v : ctx.variableDecl()) {
      List<ChuckAST.Exp> arraySizes = new ArrayList<>();
      for (ChuckANTLRParser.ArrayDimensionContext ad : v.arrayDimension()) {
        if (ad.expression() != null) arraySizes.add((ChuckAST.Exp) visit(ad.expression()));
        else
          arraySizes.add(
              new ChuckAST.IntExp(
                  -1, "", ad.getStart().getLine(), ad.getStart().getCharPositionInLine()));
      }
      String fullType = typeBase.toString();
      if (!arraySizes.isEmpty()) for (int i = 0; i < arraySizes.size(); i++) fullType += "[]";
      boolean isRef = v.REFERENCE_TAG() != null;
      ChuckAST.Exp ctorArgs = null;
      if (v.LPAREN() != null && v.expressionList() != null) {
        List<ChuckAST.Exp> argList = (List<ChuckAST.Exp>) visit(v.expressionList());
        ctorArgs =
            new ChuckAST.CallExp(
                null, argList, "", v.getStart().getLine(), v.getStart().getCharPositionInLine());
      }
      ChuckAST.DeclExp declExp =
          new ChuckAST.DeclExp(
              fullType,
              v.ID().getText(),
              arraySizes,
              ctorArgs,
              isRef,
              isStatic,
              isGlobal,
              isConst,
              access,
              currentDoc,
              v.getStart().getLine(),
              v.getStart().getCharPositionInLine());
      if (v.CHUCK_OP() != null) {
        Object result = visit(v.expression());
        ChuckAST.Exp rhs =
            (result instanceof List<?> list && !list.isEmpty())
                ? (ChuckAST.Exp) list.get(0)
                : (result instanceof ChuckAST.Exp e ? e : null);
        decls.add(
            new ChuckAST.BinaryExp(
                declExp,
                mapChuckOp(v.CHUCK_OP().getText()),
                rhs,
                "",
                v.getStart().getLine(),
                v.getStart().getCharPositionInLine()));
      } else decls.add(declExp);
    }
    return decls.size() == 1 ? decls.get(0) : decls;
  }

  private ChuckAST.Operator mapChuckOp(String opText) {
    return switch (opText) {
      case "=>" -> ChuckAST.Operator.CHUCK;
      case "@=>" -> ChuckAST.Operator.AT_CHUCK;
      case "!=>", "=<" -> ChuckAST.Operator.UNCHUCK;
      case "<=>" -> ChuckAST.Operator.SWAP;
      case "<=" -> ChuckAST.Operator.WRITE_IO;
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
  public ChuckAST.Stmt visitFunctionDef(FunctionDefContext ctx) {
    captureDoc(ctx);
    String currentDoc = consumeDoc();
    String retType = ctx.type() != null ? ctx.type().getText() : "void";
    String name = "";
    if (ctx.functionName() != null) {
      name = ctx.functionName().getText();
    } else if (ctx.OPERATOR() != null) {
      name = (ctx.REFERENCE_TAG() != null ? "@" : "") + "operator";
    }
    if (ctx.postfixOpToken() != null) name += ctx.postfixOpToken().getText();

    boolean isPublic =
        ctx.PUBLIC() != null
            || (ctx.accessModifier() != null && ctx.accessModifier().PUBLIC() != null);
    boolean isStatic = ctx.STATIC() != null;
    ChuckAST.AccessModifier access = getAccessModifier(ctx.accessModifier());

    // Standardize operator names
    if (name.startsWith("@operator") || name.startsWith("operator")) {
      String opSym =
          name.startsWith("@operator")
              ? name.substring("@operator".length()).trim()
              : name.substring("operator".length()).trim();
      if (opSym.startsWith("(") && opSym.endsWith(")")) {
        opSym = opSym.substring(1, opSym.length() - 1).trim();
      }
      String opWord =
          switch (opSym) {
            case "+" -> "plus";
            case "-" -> "minus";
            case "*" -> "times";
            case "/" -> "div";
            case "%" -> "percent";
            case "==" -> "equal";
            case "!=" -> "notequal";
            case ">" -> "greater";
            case ">=" -> "gequal";
            case "<" -> "less";
            case "<=" -> "lequal";
            default -> opSym.replace(" ", "_");
          };
      if (isPublic) name = "__pub_op__" + opWord;
      else name = "__op__" + opWord;
    }

    List<String> argTypes = new ArrayList<>();
    List<String> argNames = new ArrayList<>();
    if (ctx.formalParameters() != null) {
      for (FormalParameterContext p : ctx.formalParameters().formalParameter()) {
        StringBuilder type = new StringBuilder(p.type().getText());
        if (p.arrayDimension() != null)
          for (int i = 0; i < p.arrayDimension().size(); i++) type.append("[]");
        argTypes.add(type.toString());
        argNames.add(p.ID().getText());
      }
    }
    return new ChuckAST.FuncDefStmt(
        retType,
        name,
        argTypes,
        argNames,
        (ChuckAST.Stmt) visit(ctx.statement()),
        isStatic,
        access,
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }

  @Override
  public ChuckAST.Stmt visitClassDefinition(ClassDefinitionContext ctx) {
    captureDoc(ctx);
    String currentDoc = lastDocComment;
    lastDocComment = null;
    String name = ctx.ID().getText();
    String parentName = ctx.EXTENDS() != null ? ctx.typeName().getText() : null;
    boolean isAbstract =
        ctx.ABSTRACT() != null
            || (ctx.accessModifier() != null && ctx.accessModifier().ABSTRACT() != null);
    boolean isInterface = ctx.INTERFACE() != null;
    ChuckAST.AccessModifier access = getAccessModifier(ctx.accessModifier());
    List<ChuckAST.Stmt> body = new ArrayList<>();
    for (org.antlr.v4.runtime.tree.ParseTree child : ctx.children) {
      if (child instanceof StatementContext
          || child instanceof FunctionDefContext
          || child instanceof ClassDefinitionContext
          || child instanceof DirectiveContext) {
        Object res = visit(child);
        if (res instanceof ChuckAST.Stmt s) body.add(s);
        else if (res instanceof List<?> list)
          for (Object item : list) if (item instanceof ChuckAST.Stmt s) body.add(s);
      }
    }
    return new ChuckAST.ClassDefStmt(
        name,
        parentName,
        body,
        isAbstract,
        isInterface,
        access,
        currentDoc,
        ctx.getStart().getLine(),
        ctx.getStart().getCharPositionInLine());
  }
}
