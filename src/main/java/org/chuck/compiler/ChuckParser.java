package org.chuck.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * A basic recursive descent parser for ChucK.
 */
public class ChuckParser {
    private final List<ChuckLexer.Token> tokens;
    private int pos = 0;

    public ChuckParser(List<ChuckLexer.Token> tokens) {
        this.tokens = tokens;
    }

    public List<ChuckAST.Stmt> parse() {
        List<ChuckAST.Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(parseStatement());
        }
        return statements;
    }

    private ChuckAST.Stmt parseStatement() {
        ChuckLexer.Token token = peek();
        if (token.type() == ChuckLexer.TokenType.IF) return parseIf();
        if (token.type() == ChuckLexer.TokenType.WHILE) return parseWhile();
        if (token.type() == ChuckLexer.TokenType.FOR) return parseFor();
        if (token.type() == ChuckLexer.TokenType.FUN) return parseFuncDef();
        if (token.type() == ChuckLexer.TokenType.LBRACE) return parseBlock();
        
        if (isType(token)) return parseDecl();

        ChuckAST.Exp exp = parseExpression();
        match(ChuckLexer.TokenType.SEMICOLON); 
        return new ChuckAST.ExpStmt(exp, token.line(), token.column());
    }

    private boolean isType(ChuckLexer.Token token) {
        if (token.type() != ChuckLexer.TokenType.ID) return false;
        String val = token.value();
        return val.equals("int") || val.equals("float") || val.equals("dur") || val.equals("time") || val.equals("string") 
            || val.equals("MidiIn") || val.equals("SinOsc") || val.equals("Mandolin") || val.equals("Clarinet")
            || val.equals("ADSR") || val.equals("Gain") || val.equals("SawOsc") || val.equals("TriOsc") || val.equals("SqrOsc") || val.equals("Noise") || val.equals("Impulse");
    }

    private ChuckAST.Stmt parseDecl() {
        ChuckLexer.Token type = advance();
        ChuckLexer.Token nameToken = consume(ChuckLexer.TokenType.ID, "Expected variable name");
        ChuckAST.Exp arraySize = null;
        if (match(ChuckLexer.TokenType.LBRACKET)) {
            if (!check(ChuckLexer.TokenType.RBRACKET)) {
                arraySize = parseExpression();
            } else {
                arraySize = new ChuckAST.IntExp(-1, type.line(), type.column());
            }
            consume(ChuckLexer.TokenType.RBRACKET, "Expected ']'");
        }
        
        ChuckAST.DeclStmt decl = new ChuckAST.DeclStmt(type.value(), nameToken.value(), arraySize, type.line(), type.column());

        // Handle inline connection: SinOsc s => dac;
        if (match(ChuckLexer.TokenType.CHUCK)) {
            ChuckLexer.Token opToken = previous();
            ChuckAST.Exp lhs = new ChuckAST.IdExp(nameToken.value(), nameToken.line(), nameToken.column());
            ChuckAST.Exp rhs = parseExpression();
            ChuckAST.Exp chuckExp = new ChuckAST.BinaryExp(lhs, ChuckAST.Operator.CHUCK, rhs, opToken.line(), opToken.column());
            match(ChuckLexer.TokenType.SEMICOLON);
            
            // Return a block containing both declaration and connection
            List<ChuckAST.Stmt> pair = new ArrayList<>();
            pair.add(decl);
            pair.add(new ChuckAST.ExpStmt(chuckExp, opToken.line(), opToken.column()));
            return new ChuckAST.BlockStmt(pair, type.line(), type.column());
        }

        match(ChuckLexer.TokenType.SEMICOLON);
        return decl;
    }

    private ChuckAST.Exp parseExpression() {
        return parseChuck();
    }

    private ChuckAST.Exp parseChuck() {
        ChuckAST.Exp left = parseComparison();
        while (match(ChuckLexer.TokenType.CHUCK, ChuckLexer.TokenType.AT_CHUCK)) {
            ChuckLexer.Token opToken = previous();
            ChuckAST.Operator op = opToken.type() == ChuckLexer.TokenType.CHUCK ? ChuckAST.Operator.CHUCK : ChuckAST.Operator.AT_CHUCK;
            ChuckAST.Exp right = parseComparison();
            left = new ChuckAST.BinaryExp(left, op, right, opToken.line(), opToken.column());
        }
        return left;
    }

    private ChuckAST.Exp parseComparison() {
        ChuckAST.Exp left = parseBinary();
        while (match(ChuckLexer.TokenType.LT, ChuckLexer.TokenType.GT, ChuckLexer.TokenType.LE, ChuckLexer.TokenType.GE, ChuckLexer.TokenType.EQ_EQ)) {
            ChuckLexer.Token opToken = previous();
            ChuckAST.Operator op = switch (opToken.type()) {
                case LT -> ChuckAST.Operator.LT;
                case GT -> ChuckAST.Operator.GT;
                case LE -> ChuckAST.Operator.LE;
                case GE -> ChuckAST.Operator.GE;
                case EQ_EQ -> ChuckAST.Operator.EQ;
                default -> ChuckAST.Operator.NONE;
            };
            ChuckAST.Exp right = parseBinary();
            left = new ChuckAST.BinaryExp(left, op, right, opToken.line(), opToken.column());
        }
        return left;
    }

    private ChuckAST.Exp parseBinary() {
        ChuckAST.Exp left = parsePrimary();
        while (match(ChuckLexer.TokenType.PLUS, ChuckLexer.TokenType.MINUS, ChuckLexer.TokenType.TIMES, ChuckLexer.TokenType.DIVIDE, ChuckLexer.TokenType.COLON_COLON)) {
            ChuckLexer.Token opToken = previous();
            ChuckAST.Operator op = switch (opToken.type()) {
                case PLUS -> ChuckAST.Operator.PLUS;
                case MINUS -> ChuckAST.Operator.MINUS;
                case TIMES -> ChuckAST.Operator.TIMES;
                case DIVIDE -> ChuckAST.Operator.DIVIDE;
                default -> ChuckAST.Operator.NONE;
            };
            
            ChuckAST.Exp right = parsePrimary();
            left = new ChuckAST.BinaryExp(left, op, right, opToken.line(), opToken.column());
        }
        return left;
    }

    private ChuckAST.Exp parsePrimary() {
        if (match(ChuckLexer.TokenType.SPORK)) {
            ChuckLexer.Token start = previous();
            consume(ChuckLexer.TokenType.TILDE, "Expected '~' after 'spork'");
            ChuckAST.Exp call = parsePrimary();
            if (!(call instanceof ChuckAST.CallExp)) {
                throw new RuntimeException("Spork must be followed by a function call at line " + start.line());
            }
            return new ChuckAST.SporkExp((ChuckAST.CallExp) call, start.line(), start.column());
        }
        if (match(ChuckLexer.TokenType.INT)) {
            return new ChuckAST.IntExp(Long.parseLong(previous().value()), previous().line(), previous().column());
        }
        if (match(ChuckLexer.TokenType.FLOAT)) {
            return new ChuckAST.FloatExp(Double.parseDouble(previous().value()), previous().line(), previous().column());
        }
        if (match(ChuckLexer.TokenType.LBRACKET)) {
            return parseArrayLiteral();
        }
        if (match(ChuckLexer.TokenType.ID)) {
            String name = previous().value();
            if (name.equals("true")) return new ChuckAST.IntExp(1, previous().line(), previous().column());
            if (name.equals("false")) return new ChuckAST.IntExp(0, previous().line(), previous().column());
            
            if (isType(previous()) && check(ChuckLexer.TokenType.ID)) {
                ChuckLexer.Token varName = advance();
                return new ChuckAST.IdExp(varName.value(), varName.line(), varName.column());
            }

            ChuckAST.Exp exp = new ChuckAST.IdExp(name, previous().line(), previous().column());
            
            while (true) {
                if (match(ChuckLexer.TokenType.DOT)) {
                    consume(ChuckLexer.TokenType.ID, "Expected member name after '.'");
                    exp = new ChuckAST.DotExp(exp, previous().value(), previous().line(), previous().column());
                } else if (match(ChuckLexer.TokenType.LBRACKET)) {
                    ChuckAST.Exp index = parseExpression();
                    consume(ChuckLexer.TokenType.RBRACKET, "Expected ']' after array index");
                    exp = new ChuckAST.ArrayAccessExp(exp, index, previous().line(), previous().column());
                } else if (match(ChuckLexer.TokenType.LPAREN)) {
                    List<ChuckAST.Exp> args = new ArrayList<>();
                    if (!check(ChuckLexer.TokenType.RPAREN)) {
                        do {
                            args.add(parseExpression());
                        } while (match(ChuckLexer.TokenType.COMMA));
                    }
                    consume(ChuckLexer.TokenType.RPAREN, "Expected ')' after arguments");
                    exp = new ChuckAST.CallExp(exp, args, previous().line(), previous().column());
                } else {
                    break;
                }
            }
            return exp;
        }
        if (match(ChuckLexer.TokenType.LPAREN)) {
            ChuckAST.Exp exp = parseExpression();
            consume(ChuckLexer.TokenType.RPAREN, "Expected ')' after grouping expression");
            return exp;
        }
        throw new RuntimeException("Unexpected token: " + peek().value() + " at line " + peek().line() + " got " + peek().type());
    }

    private ChuckAST.Exp parseArrayLiteral() {
        ChuckLexer.Token start = previous();
        List<ChuckAST.Exp> elements = new ArrayList<>();
        if (!check(ChuckLexer.TokenType.RBRACKET)) {
            do {
                elements.add(parseExpression());
            } while (match(ChuckLexer.TokenType.COMMA));
        }
        consume(ChuckLexer.TokenType.RBRACKET, "Expected ']'");
        return new ChuckAST.ArrayLitExp(elements, start.line(), start.column());
    }

    private ChuckAST.Stmt parseFuncDef() {
        ChuckLexer.Token start = consume(ChuckLexer.TokenType.FUN, "Expected 'fun'");
        String returnType = advance().value();
        String name = consume(ChuckLexer.TokenType.ID, "Expected function name").value();
        consume(ChuckLexer.TokenType.LPAREN, "Expected '('");
        List<String> argTypes = new ArrayList<>();
        List<String> argNames = new ArrayList<>();
        if (!check(ChuckLexer.TokenType.RPAREN)) {
            do {
                argTypes.add(advance().value());
                argNames.add(consume(ChuckLexer.TokenType.ID, "Expected arg name").value());
            } while (match(ChuckLexer.TokenType.COMMA));
        }
        consume(ChuckLexer.TokenType.RPAREN, "Expected ')'");
        ChuckAST.Stmt body = parseStatement();
        return new ChuckAST.FuncDefStmt(returnType, name, argTypes, argNames, body, start.line(), start.column());
    }

    private ChuckAST.Stmt parseIf() {
        ChuckLexer.Token start = consume(ChuckLexer.TokenType.IF, "Expected 'if'");
        consume(ChuckLexer.TokenType.LPAREN, "Expected '(' after 'if'");
        ChuckAST.Exp condition = parseExpression();
        consume(ChuckLexer.TokenType.RPAREN, "Expected ')' after condition");
        ChuckAST.Stmt thenBranch = parseStatement();
        ChuckAST.Stmt elseBranch = null;
        if (match(ChuckLexer.TokenType.ELSE)) {
            elseBranch = parseStatement();
        }
        return new ChuckAST.IfStmt(condition, thenBranch, elseBranch, start.line(), start.column());
    }

    private ChuckAST.Stmt parseWhile() {
        ChuckLexer.Token start = consume(ChuckLexer.TokenType.WHILE, "Expected 'while'");
        consume(ChuckLexer.TokenType.LPAREN, "Expected '(' after 'while'");
        ChuckAST.Exp condition = parseExpression();
        consume(ChuckLexer.TokenType.RPAREN, "Expected ')' after condition");
        ChuckAST.Stmt body = parseStatement();
        return new ChuckAST.WhileStmt(condition, body, start.line(), start.column());
    }

    private ChuckAST.Stmt parseFor() {
        ChuckLexer.Token start = consume(ChuckLexer.TokenType.FOR, "Expected 'for'");
        consume(ChuckLexer.TokenType.LPAREN, "Expected '(' after 'for'");
        ChuckAST.Stmt init = parseStatement(); 
        ChuckAST.Stmt condition = parseStatement();
        ChuckAST.Exp update = parseExpression();
        consume(ChuckLexer.TokenType.RPAREN, "Expected ')' after for header");
        ChuckAST.Stmt body = parseStatement();
        return new ChuckAST.ForStmt(init, condition, update, body, start.line(), start.column());
    }

    private ChuckAST.BlockStmt parseBlock() {
        ChuckLexer.Token start = consume(ChuckLexer.TokenType.LBRACE, "Expected '{'");
        List<ChuckAST.Stmt> statements = new ArrayList<>();
        while (!check(ChuckLexer.TokenType.RBRACE) && !isAtEnd()) {
            statements.add(parseStatement());
        }
        consume(ChuckLexer.TokenType.RBRACE, "Expected '}' after block");
        return new ChuckAST.BlockStmt(statements, start.line(), start.column());
    }

    private boolean match(ChuckLexer.TokenType... types) {
        for (ChuckLexer.TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(ChuckLexer.TokenType type) {
        if (isAtEnd()) return false;
        return peek().type() == type;
    }

    private ChuckLexer.Token advance() {
        if (!isAtEnd()) pos++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type() == ChuckLexer.TokenType.EOF;
    }

    private ChuckLexer.Token peek() {
        return tokens.get(pos);
    }

    private ChuckLexer.Token previous() {
        return tokens.get(pos - 1);
    }

    private ChuckLexer.Token consume(ChuckLexer.TokenType type, String message) {
        if (check(type)) return advance();
        throw new RuntimeException(message + " at line " + peek().line() + " got " + peek().type());
    }
}
