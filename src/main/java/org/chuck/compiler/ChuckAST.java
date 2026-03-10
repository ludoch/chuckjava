package org.chuck.compiler;

import org.chuck.core.ChuckType;

/**
 * Base interface for all Abstract Syntax Tree nodes.
 */
public sealed interface ChuckAST {
    int line();
    int column();

    /**
     * Operators in the ChucK language.
     */
    enum Operator {
        NONE, PLUS, MINUS, TIMES, DIVIDE,
        EQ, NEQ, LT, LE, GT, GE,
        AND, OR, S_OR, S_AND,
        SHIFT_LEFT, SHIFT_RIGHT, PERCENT,
        CHUCK, PLUS_CHUCK, MINUS_CHUCK, TIMES_CHUCK, DIVIDE_CHUCK,
        ASSIGN, SPORK, NEW, AT_CHUCK
    }

    /**
     * Expressions: AST nodes that evaluate to a value.
     */
    sealed interface Exp extends ChuckAST {}

    /**
     * Statements: AST nodes that perform actions.
     */
    sealed interface Stmt extends ChuckAST {}

    // --- Expression Nodes ---

    record IntExp(long value, int line, int column) implements Exp {}
    record FloatExp(double value, int line, int column) implements Exp {}
    record StringExp(String value, int line, int column) implements Exp {}
    record IdExp(String name, int line, int column) implements Exp {}
    
    record BinaryExp(Exp lhs, Operator op, Exp rhs, int line, int column) implements Exp {}
    record UnaryExp(Operator op, Exp exp, int line, int column) implements Exp {}
    
    record CallExp(Exp base, java.util.List<Exp> args, int line, int column) implements Exp {}
    record DotExp(Exp base, String member, int line, int column) implements Exp {}
    record ArrayLitExp(java.util.List<Exp> elements, int line, int column) implements Exp {}
    record ArrayAccessExp(Exp base, Exp index, int line, int column) implements Exp {}
    record SporkExp(CallExp call, int line, int column) implements Exp {}

    // --- Statement Nodes ---

    record ExpStmt(Exp exp, int line, int column) implements Stmt {}
    
    record IfStmt(Exp condition, Stmt thenBranch, Stmt elseBranch, int line, int column) implements Stmt {}
    
    record WhileStmt(Exp condition, Stmt body, int line, int column) implements Stmt {}
    
    record ForStmt(Stmt init, Stmt condition, Exp update, Stmt body, int line, int column) implements Stmt {}
    
    record ReturnStmt(Exp exp, int line, int column) implements Stmt {}
    
    record BlockStmt(java.util.List<Stmt> statements, int line, int column) implements Stmt {}
    
    // Declaration statement: int i; or float f[10];
    record DeclStmt(String type, String name, Exp arraySize, int line, int column) implements Stmt {}
    
    record FuncDefStmt(String returnType, String name, java.util.List<String> argTypes, java.util.List<String> argNames, Stmt body, int line, int column) implements Stmt {}
}
