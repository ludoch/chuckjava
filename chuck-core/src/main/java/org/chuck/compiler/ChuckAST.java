package org.chuck.compiler;

import java.util.List;

/** Base interface for all Abstract Syntax Tree nodes. */
public sealed interface ChuckAST {
  String doc();

  int line();

  int column();

  /** Access modifiers in the ChucK language. */
  enum AccessModifier {
    PUBLIC,
    PRIVATE,
    PROTECTED
  }

  /** Operators in the ChucK language. */
  enum Operator {
    NONE,
    PLUS,
    MINUS,
    TIMES,
    DIVIDE,
    EQ,
    NEQ,
    LT,
    LE,
    GT,
    GE,
    AND,
    OR,
    S_OR,
    S_AND,
    LOGICAL_NOT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    PERCENT,
    DUR_MUL,
    CHUCK,
    PLUS_CHUCK,
    MINUS_CHUCK,
    TIMES_CHUCK,
    DIVIDE_CHUCK,
    PERCENT_CHUCK,
    POSTFIX_PLUS_PLUS,
    POSTFIX_MINUS_MINUS,
    ASSIGN,
    SPORK,
    NEW,
    AT_CHUCK,
    SWAP,
    UNCHUCK,
    WRITE_IO,
    UPCHUCK,
    APPEND
  }

  /** Expressions: AST nodes that evaluate to a value. */
  sealed interface Exp extends ChuckAST {}

  /** Statements: AST nodes that perform actions. */
  sealed interface Stmt extends ChuckAST {}

  // --- Expression Nodes ---

  record IntExp(long value, String doc, int line, int column) implements Exp {}

  record FloatExp(double value, String doc, int line, int column) implements Exp {}

  record StringExp(String value, String doc, int line, int column) implements Exp {}

  record IdExp(String name, String doc, int line, int column) implements Exp {}

  record MeExp(String doc, int line, int column) implements Exp {}

  record BinaryExp(Exp lhs, Operator op, Exp rhs, String doc, int line, int column)
      implements Exp {}

  record LogicalExp(Exp lhs, String op, Exp rhs, String doc, int line, int column) implements Exp {}

  record UnaryExp(Operator op, Exp exp, String doc, int line, int column) implements Exp {}

  record CallExp(Exp base, List<Exp> args, String doc, int line, int column) implements Exp {}

  record DotExp(Exp base, String member, String doc, int line, int column) implements Exp {}

  record ArrayLitExp(List<Exp> elements, String doc, int line, int column) implements Exp {}

  record VectorLitExp(List<Exp> elements, String doc, int line, int column) implements Exp {}

  record ComplexLit(Exp re, Exp im, String doc, int line, int column) implements Exp {}

  record PolarLit(Exp mag, Exp phase, String doc, int line, int column) implements Exp {}

  record ArrayAccessExp(Exp base, List<Exp> indices, String doc, int line, int column)
      implements Exp {}

  record SporkExp(CallExp call, String doc, int line, int column) implements Exp {}

  record DeclExp(
      String type,
      String name,
      List<Exp> arraySizes,
      Exp callArgs,
      boolean isReference,
      boolean isStatic,
      boolean isGlobal,
      boolean isConst,
      AccessModifier access,
      String doc,
      int line,
      int column)
      implements Exp {}

  record TernaryExp(Exp condition, Exp thenExp, Exp elseExp, String doc, int line, int column)
      implements Exp {}

  record CastExp(Exp value, String targetType, String doc, int line, int column) implements Exp {}

  /** typeof(expr) — returns the runtime type name of expr as a string */
  record TypeofExp(Exp expr, String doc, int line, int column) implements Exp {}

  /** instanceof(expr, TypeName) — returns 1 if expr is an instance of TypeName, else 0 */
  record InstanceofExp(Exp expr, String typeName, String doc, int line, int column)
      implements Exp {}

  // --- Statement Nodes ---

  record ImportStmt(String path, String doc, int line, int column) implements Stmt {}

  record ExpStmt(Exp exp, String doc, int line, int column) implements Stmt {}

  record IfStmt(Exp condition, Stmt thenBranch, Stmt elseBranch, String doc, int line, int column)
      implements Stmt {}

  record WhileStmt(Exp condition, Stmt body, String doc, int line, int column) implements Stmt {}

  record UntilStmt(Exp condition, Stmt body, String doc, int line, int column) implements Stmt {}

  record DoStmt(Stmt body, Exp condition, boolean isUntil, String doc, int line, int column)
      implements Stmt {}

  record ForStmt(Stmt init, Stmt condition, Exp update, Stmt body, String doc, int line, int column)
      implements Stmt {}

  record ReturnStmt(Exp exp, String doc, int line, int column) implements Stmt {}

  record BlockStmt(List<Stmt> statements, boolean isScoped, String doc, int line, int column)
      implements Stmt {}

  // Declaration statement: int i; or float f[10];
  record DeclStmt(
      String type,
      String name,
      List<Exp> arraySizes,
      Exp callArgs,
      boolean isReference,
      boolean isStatic,
      boolean isGlobal,
      boolean isConst,
      AccessModifier access,
      String doc,
      int line,
      int column)
      implements Stmt {}

  record FuncDefStmt(
      String returnType,
      String name,
      List<String> argTypes,
      List<String> argNames,
      Stmt body,
      boolean isStatic,
      AccessModifier access,
      String doc,
      int line,
      int column)
      implements Stmt {}

  record ClassDefStmt(
      String name,
      String parentName,
      List<Stmt> body,
      boolean isAbstract,
      boolean isInterface,
      AccessModifier access,
      String doc,
      int line,
      int column)
      implements Stmt {}

  record RepeatStmt(Exp count, Stmt body, String doc, int line, int column) implements Stmt {}

  record LoopStmt(Stmt body, String doc, int line, int column) implements Stmt {}

  record ForEachStmt(
      String iterType, String iterName, Exp collection, Stmt body, String doc, int line, int column)
      implements Stmt {}

  record SwitchStmt(Exp condition, List<CaseStmt> cases, String doc, int line, int column)
      implements Stmt {}

  record CaseStmt(Exp match, boolean isDefault, List<Stmt> body, String doc, int line, int column)
      implements Stmt {}

  record BreakStmt(String doc, int line, int column) implements Stmt {}

  record ContinueStmt(String doc, int line, int column) implements Stmt {}

  record PrintStmt(List<Exp> expressions, String doc, int line, int column) implements Stmt {}
}
