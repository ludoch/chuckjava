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

        // Empty statement
        if (token.type() == ChuckLexer.TokenType.SEMICOLON) {
            advance();
            return new ChuckAST.BlockStmt(List.of(), token.line(), token.column());
        }
        if (token.type() == ChuckLexer.TokenType.IF) return parseIf();
        if (token.type() == ChuckLexer.TokenType.WHILE) return parseWhile();
        if (token.type() == ChuckLexer.TokenType.UNTIL) return parseUntil();
        if (token.type() == ChuckLexer.TokenType.DO) return parseDo();
        if (token.type() == ChuckLexer.TokenType.FOR) return parseFor();
        if (token.type() == ChuckLexer.TokenType.REPEAT) return parseRepeat();
        if (token.type() == ChuckLexer.TokenType.BREAK || token.type() == ChuckLexer.TokenType.CONTINUE) {
            advance();
            match(ChuckLexer.TokenType.SEMICOLON);
            if (token.type() == ChuckLexer.TokenType.BREAK)
                return new ChuckAST.BreakStmt(token.line(), token.column());
            else
                return new ChuckAST.ContinueStmt(token.line(), token.column());
        }
        // Check for modifiers
        boolean isPublic = false;
        boolean isStatic = false;
        while (!isAtEnd() && (peek().type() == ChuckLexer.TokenType.PUBLIC || peek().type() == ChuckLexer.TokenType.STATIC)) {
            if (match(ChuckLexer.TokenType.PUBLIC)) isPublic = true;
            if (match(ChuckLexer.TokenType.STATIC)) isStatic = true;
        }
        token = peek(); // refresh token after skipping modifiers

        // @-directives...
        if (token.type() == ChuckLexer.TokenType.AT
                && pos + 1 < tokens.size() && tokens.get(pos + 1).type() == ChuckLexer.TokenType.ID) {
            advance(); // consume "@"
            ChuckLexer.Token directiveName = null;
            if (!isAtEnd() && peek().type() == ChuckLexer.TokenType.ID) directiveName = advance();
            // @import "path" — produce an ImportStmt
            if (directiveName != null && directiveName.value().equals("import")
                    && !isAtEnd() && peek().type() == ChuckLexer.TokenType.STRING) {
                String path = advance().value();
                match(ChuckLexer.TokenType.SEMICOLON);
                return new ChuckAST.ImportStmt(path, token.line(), token.column());
            }
            // Other directives — skip
            while (!isAtEnd() && !check(ChuckLexer.TokenType.SEMICOLON)
                    && !check(ChuckLexer.TokenType.FUN) && !check(ChuckLexer.TokenType.CLASS)
                    && !check(ChuckLexer.TokenType.RBRACE) && !check(ChuckLexer.TokenType.IF)
                    && !check(ChuckLexer.TokenType.WHILE) && !check(ChuckLexer.TokenType.FOR)) {
                boolean wasString = check(ChuckLexer.TokenType.STRING);
                advance();
                if (wasString) break;
            }
            match(ChuckLexer.TokenType.SEMICOLON);
            return new ChuckAST.BlockStmt(List.of(), token.line(), token.column());
        }
        // Check for operator overload without 'fun': e.g. public Foo @operator +(...) {...}
        if (isType(token) && pos + 1 < tokens.size()
                && tokens.get(pos + 1).type() == ChuckLexer.TokenType.AT
                && pos + 2 < tokens.size()
                && tokens.get(pos + 2).type() == ChuckLexer.TokenType.ID
                && tokens.get(pos + 2).value().equals("operator")) {
            return parseOperatorDef(isPublic, isStatic);
        }
        if (token.type() == ChuckLexer.TokenType.FUN) return parseFuncDef(isPublic, isStatic);
        if (token.type() == ChuckLexer.TokenType.CLASS) return parseClassDef();
        if (token.type() == ChuckLexer.TokenType.LBRACE) return parseBlock();
        if (token.type() == ChuckLexer.TokenType.PRINT_START) return parsePrint();
        if (token.type() == ChuckLexer.TokenType.RETURN) {
            advance();
            ChuckAST.Exp exp = null;
            if (!check(ChuckLexer.TokenType.SEMICOLON)) {
                exp = parseExpression();
            }
            match(ChuckLexer.TokenType.SEMICOLON);
            return new ChuckAST.ReturnStmt(exp, token.line(), token.column());
        }

        if (isType(token) && isDeclStart()) return parseDecl(isPublic, isStatic, true);

        ChuckAST.Exp exp = parseExpression();
        match(ChuckLexer.TokenType.SEMICOLON);
        return new ChuckAST.ExpStmt(exp, token.line(), token.column());
    }

    private ChuckAST.Stmt parsePrint() {
        ChuckLexer.Token start = consume(ChuckLexer.TokenType.PRINT_START, "Expected '<<<'");
        List<ChuckAST.Exp> expressions = new ArrayList<>();
        while (!check(ChuckLexer.TokenType.PRINT_END) && !isAtEnd()) {
            expressions.add(parseExpression());
            if (check(ChuckLexer.TokenType.COMMA)) advance();
        }
        consume(ChuckLexer.TokenType.PRINT_END, "Expected '>>>' after print expressions");
        match(ChuckLexer.TokenType.SEMICOLON);
        return new ChuckAST.PrintStmt(expressions, start.line(), start.column());
    }

    private final java.util.Set<String> knownTypes = new java.util.HashSet<>(java.util.Set.of(
        "int", "float", "dur", "time", "string", "void", "auto",
        // UGens - oscillators
        "SinOsc", "SawOsc", "TriOsc", "SqrOsc", "PulseOsc", "Phasor", "Noise", "Impulse", "Step",
        // UGens - instruments
        "Mandolin", "Clarinet", "Plucked", "Rhodey", "Bowed", "StifKarp", "Moog", "Flute", "Sitar",
        "Brass", "Saxofony", "Shakers",
        // UGens - envelopes
        "ADSR", "Adsr", "Envelope",
        // UGens - utilities
        "Gain", "Pan2", "FFT", "IFFT", "LiSa", "Gen5", "Gen7", "Gen10", "RMS", "Centroid",
        // UGens - effects
        "Echo", "Delay", "DelayL", "JCRev", "Chorus", "ResonZ", "Lpf", "OnePole", "OneZero",
        // IO
        "MidiIn", "SndBuf", "WvOut", "IO",
        // Networking
        "OscIn", "OscOut", "OscMsg",
        // HID
        "Hid", "HidMsg",
        // MIDI
        "MidiMsg",
        // Builtins
        "Machine", "me",
        // Primitive/built-in types
        "complex", "polar", "vec3", "vec4",
        // Other built-in types seen in examples
        "Event", "Object", "UGen", "Chugraph", "Chugen",
        "DCT", "Flip", "PitchTrack", "AutoCorr", "XCorr", "ZeroX", "Flux", "MFCC", "Kurtosis",
        "Rolloff", "SFM", "Chroma", "Sigmund", "Dyno", "LPF", "HPF", "BPF", "BRF", "BiQuad",
        "PoleZero", "AllPass", "Blit", "BlitSaw", "BlitSquare", "FilterBasic", "Teabox",
        "KNN", "KNN2", "SVM", "MLP", "HMM", "PCA", "Wekinator", "Word2Vec",
        "Scope", "WvOut2", "UAna", "UAnaBlob", "Complex", "Polar",
        "Serial", "OscArg",
        // Base/abstract types
        "Osc", "Type", "FileIO", "StringTokenizer", "RegEx", "Math", "Std", "cherr", "chout"
    ));

    /** A declaration starts only when a type token is followed by an identifier (not . or (). */
    private boolean isDeclStart() {
        if (pos + 1 >= tokens.size()) return false;
        ChuckLexer.TokenType next = tokens.get(pos + 1).type();
        if (next == ChuckLexer.TokenType.ID) return true;
        // Support @ reference syntax: Gain @ g;
        if (next == ChuckLexer.TokenType.AT && pos + 2 < tokens.size()) {
            return tokens.get(pos + 2).type() == ChuckLexer.TokenType.ID;
        }
        return false;
    }

    private boolean isDeclNext() {
        if (check(ChuckLexer.TokenType.ID) || check(ChuckLexer.TokenType.AT)) {
            ChuckLexer.Token t = peek();
            if (t.type() == ChuckLexer.TokenType.AT) {
                // If it's @ followed by ID, might be a decl like Gain @ g;
                if (pos + 1 < tokens.size() && tokens.get(pos+1).type() == ChuckLexer.TokenType.ID) {
                    // Check if the ID is a type
                    if (isType(tokens.get(pos+1))) {
                         // peek further for name
                         if (pos + 2 < tokens.size()) {
                             ChuckLexer.Token next2 = tokens.get(pos + 2);
                             return next2.type() == ChuckLexer.TokenType.ID || next2.type() == ChuckLexer.TokenType.AT;
                         }
                    }
                }
                return false;
            }
            // A declaration starts with a type name.
            // In ChucK, if it's an ID followed by another ID, it's a declaration.
            if (isType(t)) {
                // Peek ahead to see if there is another ID (the variable name)
                if (pos + 1 < tokens.size()) {
                    ChuckLexer.Token next = tokens.get(pos + 1);
                    return next.type() == ChuckLexer.TokenType.ID || next.type() == ChuckLexer.TokenType.AT;
                }
            }
        }
        return false;
    }

    private boolean isType(ChuckLexer.Token token) {
        if (token.type() != ChuckLexer.TokenType.ID) return false;
        String val = token.value();
        if (val.equals("me") || val.equals("Machine")) return false;
        // Known types, or any capitalized identifier (user-defined classes are always capitalized in ChucK)
        return knownTypes.contains(val) || (!val.isEmpty() && Character.isUpperCase(val.charAt(0)));
    }

    private ChuckAST.Stmt parseDecl(boolean isPublic, boolean isStatic, boolean allowInlineChuck) {
        ChuckLexer.Token type = advance();
        List<ChuckAST.Stmt> results = new ArrayList<>();
        // Support @ reference for the whole declaration: Gain @ h, g;
        boolean isRef = match(ChuckLexer.TokenType.AT);
        
        int variableCount = 0;
        boolean hasChuck = false;

        // Support multiple names: float x[], y[];
        do {
            variableCount++;
            // Also support @ per-variable if needed (though unconventional)
            boolean thisRef = isRef || match(ChuckLexer.TokenType.AT);
            
            ChuckLexer.Token nameToken = consume(ChuckLexer.TokenType.ID, "Expected variable name");
            // Collect array dimensions: int a[0][2] or int a[]
            List<ChuckAST.Exp> arraySizes = new ArrayList<>();
            while (check(ChuckLexer.TokenType.LBRACKET)) {
                advance(); // [
                ChuckAST.Exp dim = check(ChuckLexer.TokenType.RBRACKET)
                        ? new ChuckAST.IntExp(-1, type.line(), type.column())
                        : parseExpression();
                consume(ChuckLexer.TokenType.RBRACKET, "Expected ']'");
                arraySizes.add(dim);
            }
            // Handle constructor args: Foo a(1, 2)
            ChuckAST.Exp callArgs = null;
            if (check(ChuckLexer.TokenType.LPAREN)) {
                ChuckLexer.Token start = advance(); // (
                List<ChuckAST.Exp> args = new ArrayList<>();
                if (!check(ChuckLexer.TokenType.RPAREN)) {
                    do { args.add(parseExpression()); } while (match(ChuckLexer.TokenType.COMMA));
                }
                consume(ChuckLexer.TokenType.RPAREN, "Expected ')'");
                callArgs = new ChuckAST.CallExp(new ChuckAST.IdExp(type.value(), start.line(), start.column()),
                        args, start.line(), start.column());
            }

            ChuckAST.DeclStmt decl = new ChuckAST.DeclStmt(type.value(), nameToken.value(), arraySizes, callArgs, thisRef, isStatic,
                    type.line(), type.column());

            // Handle inline chuck after the name: SinOsc s => dac; or fc =^ unflip
            if (allowInlineChuck && match(ChuckLexer.TokenType.CHUCK, ChuckLexer.TokenType.AT_CHUCK,
                      ChuckLexer.TokenType.UPCHUCK)) {
                hasChuck = true;
                ChuckLexer.Token opToken = previous();
                ChuckAST.Operator op = switch (opToken.type()) {
                    case AT_CHUCK -> ChuckAST.Operator.AT_CHUCK;
                    case UPCHUCK  -> ChuckAST.Operator.UPCHUCK;
                    default       -> ChuckAST.Operator.CHUCK;
                };
                ChuckAST.Exp lhs = new ChuckAST.IdExp(nameToken.value(), nameToken.line(), nameToken.column());
                ChuckAST.Exp rhs = parseExpression();
                results.add(decl);
                results.add(new ChuckAST.ExpStmt(
                        new ChuckAST.BinaryExp(lhs, op, rhs, opToken.line(), opToken.column()),
                        opToken.line(), opToken.column()));
            } else {
                results.add(decl);
            }
        } while (match(ChuckLexer.TokenType.COMMA)); // float x, y;  or  float x[], y[];

        if (variableCount > 1 && hasChuck) {
            throw new RuntimeException("cannot '=>' from/to a multi-variable declaration (from parseDecl)");
        }

        match(ChuckLexer.TokenType.SEMICOLON);
        return results.size() == 1 ? results.get(0)
                : new ChuckAST.BlockStmt(results, type.line(), type.column());
    }

    private ChuckAST.Exp parseExpression() {
        return parseChuck();
    }

    private ChuckAST.Exp parseChuck() {
        ChuckAST.Exp left = parseTernary();
        while (match(ChuckLexer.TokenType.CHUCK, ChuckLexer.TokenType.AT_CHUCK,
                     ChuckLexer.TokenType.UNCHUCK, ChuckLexer.TokenType.UPCHUCK,
                     ChuckLexer.TokenType.SWAP, ChuckLexer.TokenType.WRITE_IO,
                     ChuckLexer.TokenType.ASSIGN,
                     ChuckLexer.TokenType.PLUS_CHUCK, ChuckLexer.TokenType.MINUS_CHUCK,
                     ChuckLexer.TokenType.TIMES_CHUCK, ChuckLexer.TokenType.DIVIDE_CHUCK,
                     ChuckLexer.TokenType.PERCENT_CHUCK, ChuckLexer.TokenType.APPEND,
                     ChuckLexer.TokenType.PIPE_CHUCK, ChuckLexer.TokenType.AMP_CHUCK)) {
            ChuckLexer.Token opToken = previous();
            ChuckAST.Operator op = switch (opToken.type()) {
                case CHUCK        -> ChuckAST.Operator.CHUCK;
                case AT_CHUCK     -> ChuckAST.Operator.AT_CHUCK;
                case UNCHUCK      -> ChuckAST.Operator.UNCHUCK;
                case UPCHUCK      -> ChuckAST.Operator.UPCHUCK;
                case SWAP         -> ChuckAST.Operator.SWAP;
                case WRITE_IO     -> ChuckAST.Operator.WRITE_IO;
                case ASSIGN       -> ChuckAST.Operator.ASSIGN;
                case PLUS_CHUCK   -> ChuckAST.Operator.PLUS_CHUCK;
                case MINUS_CHUCK  -> ChuckAST.Operator.MINUS_CHUCK;
                case TIMES_CHUCK  -> ChuckAST.Operator.TIMES_CHUCK;
                case DIVIDE_CHUCK -> ChuckAST.Operator.DIVIDE_CHUCK;
                case PERCENT_CHUCK-> ChuckAST.Operator.PERCENT_CHUCK;
                case APPEND       -> ChuckAST.Operator.APPEND;
                case PIPE_CHUCK   -> ChuckAST.Operator.S_OR;
                case AMP_CHUCK    -> ChuckAST.Operator.S_AND;
                default           -> ChuckAST.Operator.NONE;
            };
            ChuckAST.Exp right;
            if (isDeclNext()) {
                ChuckAST.Stmt decl = parseDecl(false, false, false);
                if (decl instanceof ChuckAST.BlockStmt) {
                    throw new RuntimeException("cannot '=>' from/to a multi-variable declaration (from parseChuck)");
                }
                // Wrap DeclStmt back into DeclExp for the BinaryExp
                ChuckAST.DeclStmt ds = (ChuckAST.DeclStmt) decl;
                right = new ChuckAST.DeclExp(ds.type(), ds.name(), ds.arraySizes(), ds.callArgs(), ds.isReference(), ds.isStatic(), ds.line(), ds.column());
            } else {
                right = parseTernary();
            }
            left = new ChuckAST.BinaryExp(left, op, right, opToken.line(), opToken.column());
        }
        return left;
    }

    private ChuckAST.Exp parseTernary() {
        ChuckAST.Exp exp = parseLogical();
        if (match(ChuckLexer.TokenType.QUESTION)) {
            ChuckAST.Exp thenExp = parseTernary(); // right-associative
            if (match(ChuckLexer.TokenType.COLON)) parseTernary(); // consume else branch
            return thenExp; // simplified: return then branch
        }
        return exp;
    }

    private ChuckAST.Exp parseLogical() {
        ChuckAST.Exp left = parseComparison();
        while (match(ChuckLexer.TokenType.AND_AND, ChuckLexer.TokenType.OR_OR)) {
            ChuckLexer.Token opToken = previous();
            ChuckAST.Operator op = opToken.type() == ChuckLexer.TokenType.AND_AND
                    ? ChuckAST.Operator.AND : ChuckAST.Operator.OR;
            ChuckAST.Exp right = parseComparison();
            left = new ChuckAST.BinaryExp(left, op, right, opToken.line(), opToken.column());
        }
        return left;
    }

    private ChuckAST.Exp parseComparison() {
        ChuckAST.Exp left = parseCast();
        while (match(ChuckLexer.TokenType.LT, ChuckLexer.TokenType.GT, ChuckLexer.TokenType.LE,
                     ChuckLexer.TokenType.GE, ChuckLexer.TokenType.EQ_EQ, ChuckLexer.TokenType.NEQ)) {
            ChuckLexer.Token opToken = previous();
            ChuckAST.Operator op = switch (opToken.type()) {
                case LT  -> ChuckAST.Operator.LT;
                case GT  -> ChuckAST.Operator.GT;
                case LE  -> ChuckAST.Operator.LE;
                case GE  -> ChuckAST.Operator.GE;
                case EQ_EQ -> ChuckAST.Operator.EQ;
                case NEQ -> ChuckAST.Operator.NEQ;
                default  -> ChuckAST.Operator.NONE;
            };
            ChuckAST.Exp right = parseCast();
            left = new ChuckAST.BinaryExp(left, op, right, opToken.line(), opToken.column());
        }
        return left;
    }

    private ChuckAST.Exp parseCast() {
        ChuckAST.Exp exp = parseAdditive();
        while (match(ChuckLexer.TokenType.DOLLAR)) {
            advance(); // consume type name
            // optional [] suffix
            while (check(ChuckLexer.TokenType.LBRACKET) && peekAt(1).type() == ChuckLexer.TokenType.RBRACKET) {
                advance(); advance();
            }
        }
        return exp;
    }

    private ChuckAST.Exp parseAdditive() {
        ChuckAST.Exp left = parseMultiplicative();
        while (match(ChuckLexer.TokenType.PLUS, ChuckLexer.TokenType.MINUS,
                     ChuckLexer.TokenType.SHIFT_RIGHT,
                     ChuckLexer.TokenType.PIPE)) {
            ChuckLexer.Token opToken = previous();
            ChuckAST.Operator op = switch (opToken.type()) {
                case PLUS         -> ChuckAST.Operator.PLUS;
                case MINUS        -> ChuckAST.Operator.MINUS;
                case SHIFT_RIGHT  -> ChuckAST.Operator.SHIFT_RIGHT;
                case PIPE         -> ChuckAST.Operator.S_OR;
                default           -> ChuckAST.Operator.NONE;
            };
            ChuckAST.Exp right = parseMultiplicative();
            left = new ChuckAST.BinaryExp(left, op, right, opToken.line(), opToken.column());
        }
        return left;
    }

    private ChuckAST.Exp parseMultiplicative() {
        ChuckAST.Exp left = parseUnary();
        while (match(ChuckLexer.TokenType.TIMES, ChuckLexer.TokenType.DIVIDE,
                     ChuckLexer.TokenType.PERCENT, ChuckLexer.TokenType.AMP,
                     ChuckLexer.TokenType.COLON_COLON)) {
            ChuckLexer.Token opToken = previous();
            ChuckAST.Operator op = switch (opToken.type()) {
                case TIMES        -> ChuckAST.Operator.TIMES;
                case DIVIDE       -> ChuckAST.Operator.DIVIDE;
                case PERCENT      -> ChuckAST.Operator.PERCENT;
                case AMP          -> ChuckAST.Operator.S_AND;
                case COLON_COLON  -> ChuckAST.Operator.DUR_MUL;
                default           -> ChuckAST.Operator.NONE;
            };
            ChuckAST.Exp right = parseUnary();
            left = new ChuckAST.BinaryExp(left, op, right, opToken.line(), opToken.column());
        }
        return left;
    }

    private ChuckAST.Exp parseUnary() {
        if (match(ChuckLexer.TokenType.MINUS)) {
            ChuckLexer.Token t = previous();
            return new ChuckAST.UnaryExp(ChuckAST.Operator.MINUS, parseUnary(), t.line(), t.column());
        }
        if (match(ChuckLexer.TokenType.BANG)) {
            ChuckLexer.Token t = previous();
            return new ChuckAST.UnaryExp(ChuckAST.Operator.S_OR, parseUnary(), t.line(), t.column());
        }
        if (match(ChuckLexer.TokenType.PLUS_PLUS)) {
            ChuckLexer.Token t = previous();
            return new ChuckAST.BinaryExp(
                    new ChuckAST.IntExp(1, t.line(), t.column()),
                    ChuckAST.Operator.PLUS_CHUCK, parseUnary(), t.line(), t.column());
        }
        if (match(ChuckLexer.TokenType.MINUS_MINUS)) {
            ChuckLexer.Token t = previous();
            return new ChuckAST.BinaryExp(
                    new ChuckAST.IntExp(1, t.line(), t.column()),
                    ChuckAST.Operator.MINUS_CHUCK, parseUnary(), t.line(), t.column());
        }
        return parsePostfix();
    }

    private ChuckAST.Exp parsePostfix() {
        ChuckAST.Exp exp = parsePrimary();
        while (true) {
            if (match(ChuckLexer.TokenType.PLUS_PLUS)) {
                ChuckLexer.Token t = previous();
                // exp++ -> (1 +=> exp) — compound assignment performs the increment
                exp = new ChuckAST.BinaryExp(
                        new ChuckAST.IntExp(1, t.line(), t.column()),
                        ChuckAST.Operator.PLUS_CHUCK, exp, t.line(), t.column());
            } else if (match(ChuckLexer.TokenType.MINUS_MINUS)) {
                ChuckLexer.Token t = previous();
                // exp-- -> (1 -=> exp)
                exp = new ChuckAST.BinaryExp(
                        new ChuckAST.IntExp(1, t.line(), t.column()),
                        ChuckAST.Operator.MINUS_CHUCK, exp, t.line(), t.column());
            } else if (match(ChuckLexer.TokenType.DOT)) {
                consume(ChuckLexer.TokenType.ID, "Expected member name after '.'");
                ChuckLexer.Token member = previous();
                exp = new ChuckAST.DotExp(exp, member.value(), member.line(), member.column());
            } else if (match(ChuckLexer.TokenType.LBRACKET)) {
                if (check(ChuckLexer.TokenType.RBRACKET)) {
                    // empty [] — array type annotation suffix, not an access; skip
                    advance();
                } else {
                    List<ChuckAST.Exp> indices = new ArrayList<>();
                    do {
                        indices.add(parseExpression());
                    } while (match(ChuckLexer.TokenType.COMMA));
                    consume(ChuckLexer.TokenType.RBRACKET, "Expected ']' after array index");
                    exp = new ChuckAST.ArrayAccessExp(exp, indices, previous().line(), previous().column());
                }
            } else if (match(ChuckLexer.TokenType.LPAREN)) {
                List<ChuckAST.Exp> args = new ArrayList<>();
                if (!check(ChuckLexer.TokenType.RPAREN)) {
                    do { args.add(parseExpression()); } while (match(ChuckLexer.TokenType.COMMA));
                }
                consume(ChuckLexer.TokenType.RPAREN, "Expected ')' after arguments");
                exp = new ChuckAST.CallExp(exp, args, previous().line(), previous().column());
            } else {
                break;
            }
        }
        return exp;
    }

    private ChuckAST.Exp parsePrimary() {
        // spork ~ call()
        if (match(ChuckLexer.TokenType.SPORK)) {
            ChuckLexer.Token start = previous();
            consume(ChuckLexer.TokenType.TILDE, "Expected '~' after 'spork'");
            ChuckAST.Exp call = parsePostfix();
            if (!(call instanceof ChuckAST.CallExp)) {
                throw new RuntimeException("Spork must be followed by a function call at line " + start.line());
            }
            return new ChuckAST.SporkExp((ChuckAST.CallExp) call, start.line(), start.column());
        }
        // new Type[n] or new Type
        if (match(ChuckLexer.TokenType.NEW)) {
            ChuckLexer.Token start = previous();
            ChuckLexer.Token typeTok = advance(); // type name
            
            // new Type[size]
            if (match(ChuckLexer.TokenType.LBRACKET)) {
                ChuckAST.Exp size = check(ChuckLexer.TokenType.RBRACKET)
                        ? new ChuckAST.IntExp(0, start.line(), start.column())
                        : parseExpression();
                consume(ChuckLexer.TokenType.RBRACKET, "Expected ']'");
                // additional dims like [2]
                while (match(ChuckLexer.TokenType.LBRACKET)) {
                    if (!check(ChuckLexer.TokenType.RBRACKET)) parseExpression(); // discard extra dim
                    consume(ChuckLexer.TokenType.RBRACKET, "Expected ']'");
                }
                return new ChuckAST.BinaryExp(
                        new ChuckAST.IdExp(typeTok.value(), typeTok.line(), typeTok.column()),
                        ChuckAST.Operator.NEW, size, start.line(), start.column());
            }
            
            // new Type(args)
            ChuckAST.Exp callArgs = null;
            if (match(ChuckLexer.TokenType.LPAREN)) {
                List<ChuckAST.Exp> args = new ArrayList<>();
                if (!check(ChuckLexer.TokenType.RPAREN)) {
                    do { args.add(parseExpression()); } while (match(ChuckLexer.TokenType.COMMA));
                }
                consume(ChuckLexer.TokenType.RPAREN, "Expected ')' after new args");
                callArgs = new ChuckAST.CallExp(new ChuckAST.IdExp(typeTok.value(), typeTok.line(), typeTok.column()),
                        args, typeTok.line(), typeTok.column());
            }
            
            return new ChuckAST.DeclExp(typeTok.value(), "@new_" + start.line() + "_" + start.column(), 
                    new ArrayList<>(), callArgs, false, false, start.line(), start.column());
        }
        if (match(ChuckLexer.TokenType.INT)) {
            return new ChuckAST.IntExp(Long.parseLong(previous().value()), previous().line(), previous().column());
        }
        if (match(ChuckLexer.TokenType.FLOAT)) {
            return new ChuckAST.FloatExp(Double.parseDouble(previous().value()), previous().line(), previous().column());
        }
        if (match(ChuckLexer.TokenType.STRING)) {
            return new ChuckAST.StringExp(previous().value(), previous().line(), previous().column());
        }
        // skip static/public in expression context (e.g. 1 => static int x)
        if (match(ChuckLexer.TokenType.STATIC, ChuckLexer.TokenType.PUBLIC)) {
            return parsePrimary(); // recurse with modifier consumed
        }
        // #(re, im) or %(mag, angle) — complex/polar literals; treat as FloatExp(0)
        if (match(ChuckLexer.TokenType.HASH, ChuckLexer.TokenType.PERCENT)) {
            if (check(ChuckLexer.TokenType.LPAREN)) {
                advance(); // (
                parseExpression(); // re or mag (discard)
                if (match(ChuckLexer.TokenType.COMMA)) parseExpression(); // im or angle
                consume(ChuckLexer.TokenType.RPAREN, "Expected ')'");
            }
            return new ChuckAST.FloatExp(0.0, previous().line(), previous().column());
        }
        if (match(ChuckLexer.TokenType.LBRACKET)) {
            return parseArrayLiteral();
        }
        if (match(ChuckLexer.TokenType.ME)) {
            return new ChuckAST.MeExp(previous().line(), previous().column());
        }
        if (match(ChuckLexer.TokenType.ID)) {
            ChuckLexer.Token idTok = previous();
            String name = idTok.value();
            if (name.equals("true"))  return new ChuckAST.IntExp(1, idTok.line(), idTok.column());
            if (name.equals("false")) return new ChuckAST.IntExp(0, idTok.line(), idTok.column());

            // type name  →  DeclExp
            if (isType(idTok) && (check(ChuckLexer.TokenType.ID) || check(ChuckLexer.TokenType.AT))) {
                boolean isRef = match(ChuckLexer.TokenType.AT);
                ChuckLexer.Token varName = consume(ChuckLexer.TokenType.ID, "Expected variable name");
                // optional [] array suffix
                List<ChuckAST.Exp> arraySizes = new ArrayList<>();
                while (check(ChuckLexer.TokenType.LBRACKET)) {
                    advance(); // [
                    ChuckAST.Exp dim = check(ChuckLexer.TokenType.RBRACKET)
                            ? new ChuckAST.IntExp(-1, idTok.line(), idTok.column())
                            : parseExpression();
                    consume(ChuckLexer.TokenType.RBRACKET, "Expected ']'");
                    arraySizes.add(dim);
                }
                // Handle constructor args: Foo a(1, 2)
                ChuckAST.Exp callArgs = null;
                if (check(ChuckLexer.TokenType.LPAREN)) {
                    ChuckLexer.Token start = advance(); // (
                    List<ChuckAST.Exp> args = new ArrayList<>();
                    if (!check(ChuckLexer.TokenType.RPAREN)) {
                        do { args.add(parseExpression()); } while (match(ChuckLexer.TokenType.COMMA));
                    }
                    consume(ChuckLexer.TokenType.RPAREN, "Expected ')'");
                    callArgs = new ChuckAST.CallExp(new ChuckAST.IdExp(idTok.value(), start.line(), start.column()),
                            args, start.line(), start.column());
                }
                return new ChuckAST.DeclExp(idTok.value(), varName.value(), arraySizes, callArgs, isRef, false, idTok.line(), idTok.column());
            }
            return new ChuckAST.IdExp(name, idTok.line(), idTok.column());
        }
        if (match(ChuckLexer.TokenType.AT)) {
            ChuckLexer.Token start = previous();
            consume(ChuckLexer.TokenType.LPAREN, "Expected '(' after '@'");
            List<ChuckAST.Exp> elements = new ArrayList<>();
            if (!check(ChuckLexer.TokenType.RPAREN)) {
                do {
                    elements.add(parseExpression());
                } while (match(ChuckLexer.TokenType.COMMA));
            }
            consume(ChuckLexer.TokenType.RPAREN, "Expected ')' after vector literal elements");
            return new ChuckAST.ArrayLitExp(elements, start.line(), start.column());
        }
        if (match(ChuckLexer.TokenType.LPAREN)) {
            // () => foo  (empty-arg call syntax)
            if (check(ChuckLexer.TokenType.RPAREN)) {
                advance();
                return new ChuckAST.ArrayLitExp(List.of(), previous().line(), previous().column());
            }
            ChuckAST.Exp exp = parseExpression();
            // Tuple args: (a, b, c) => obj.method  — return first, consume rest
            while (match(ChuckLexer.TokenType.COMMA)) {
                parseExpression(); // consume but discard extra args
            }
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
                // nested array literal: [ [1,2], [3,4] ]
                if (check(ChuckLexer.TokenType.LBRACKET)) {
                    advance();
                    elements.add(parseArrayLiteral());
                } else {
                    elements.add(parseExpression());
                }
            } while (match(ChuckLexer.TokenType.COMMA));
        }
        consume(ChuckLexer.TokenType.RBRACKET, "Expected ']'");
        return new ChuckAST.ArrayLitExp(elements, start.line(), start.column());
    }

    private ChuckAST.Stmt parseRepeat() {
        ChuckLexer.Token start = consume(ChuckLexer.TokenType.REPEAT, "Expected 'repeat'");
        consume(ChuckLexer.TokenType.LPAREN, "Expected '(' after 'repeat'");
        ChuckAST.Exp count = parseExpression();
        consume(ChuckLexer.TokenType.RPAREN, "Expected ')' after repeat count");
        ChuckAST.Stmt body = parseStatement();
        return new ChuckAST.RepeatStmt(count, body, start.line(), start.column());
    }

    private ChuckAST.ClassDefStmt parseClassDef() {
        ChuckLexer.Token start = consume(ChuckLexer.TokenType.CLASS, "Expected 'class'");
        String name = consume(ChuckLexer.TokenType.ID, "Expected class name").value();
        knownTypes.add(name);
        // optional: extends SuperType
        String parentName = null;
        if (match(ChuckLexer.TokenType.EXTENDS)) {
            parentName = consume(ChuckLexer.TokenType.ID, "Expected parent class name").value();
        }
        consume(ChuckLexer.TokenType.LBRACE, "Expected '{' after class name");

        List<ChuckAST.Stmt> body = new ArrayList<>();
        while (!check(ChuckLexer.TokenType.RBRACE) && !isAtEnd()) {
            ChuckAST.Stmt s = parseStatement();
            if (s != null) body.add(s);
        }
        consume(ChuckLexer.TokenType.RBRACE, "Expected '}' after class body");
        return new ChuckAST.ClassDefStmt(name, parentName, body, start.line(), start.column());
    }

    /** Parses: ReturnType @operator Symbol ( params ) body — no 'fun' keyword */
    private ChuckAST.Stmt parseOperatorDef(boolean isPublic, boolean isStatic) {
        int line = peek().line(); int col = peek().column();
        String returnType = advance().value(); // return type (e.g. "Foo")
        advance(); // "@"
        advance(); // "operator"
        String prefix = isPublic ? "__pub_op__" : "__op__";
        StringBuilder opName = new StringBuilder(prefix);
        while (!check(ChuckLexer.TokenType.LPAREN) && !isAtEnd()) {
            opName.append(advance().value());
        }
        consume(ChuckLexer.TokenType.LPAREN, "Expected '('");
        List<String> argTypes = new ArrayList<>();
        List<String> argNames = new ArrayList<>();
        if (!check(ChuckLexer.TokenType.RPAREN)) {
            do {
                argTypes.add(advance().value()); // type
                if (check(ChuckLexer.TokenType.AT)) advance(); // optional @ ref
                argNames.add(check(ChuckLexer.TokenType.ID) ? advance().value() : "_");
            } while (match(ChuckLexer.TokenType.COMMA));
        }
        consume(ChuckLexer.TokenType.RPAREN, "Expected ')'");
        ChuckAST.Stmt body = parseStatement();
        return new ChuckAST.FuncDefStmt(returnType, opName.toString(), argTypes, argNames, body, isStatic, line, col);
    }

    private ChuckAST.Stmt parseFuncDef(boolean isPublic, boolean isStatic) {
        consume(ChuckLexer.TokenType.FUN, "Expected 'fun'");
        ChuckLexer.Token start = previous();

        // Also handle modifiers after 'fun': fun static void foo()
        while (!isAtEnd() && (peek().type() == ChuckLexer.TokenType.PUBLIC || peek().type() == ChuckLexer.TokenType.STATIC)) {
            if (match(ChuckLexer.TokenType.PUBLIC)) isPublic = true;
            if (match(ChuckLexer.TokenType.STATIC)) isStatic = true;
        }

        // fun @construct(...) — constructor; no return type token
        if (peek().type() == ChuckLexer.TokenType.AT) {
            advance(); // consume "@"
            String constructName = check(ChuckLexer.TokenType.ID) ? advance().value() : "construct";
            // directly to parameter list
            consume(ChuckLexer.TokenType.LPAREN, "Expected '('");
            List<String> argTypes2 = new ArrayList<>();
            List<String> argNames2 = new ArrayList<>();
            if (!check(ChuckLexer.TokenType.RPAREN)) {
                do {
                    argTypes2.add(advance().value());
                    if (check(ChuckLexer.TokenType.LBRACKET)) { advance(); if (!check(ChuckLexer.TokenType.RBRACKET)) advance(); consume(ChuckLexer.TokenType.RBRACKET, ""); }
                    if (peek().type() == ChuckLexer.TokenType.AT) advance(); // skip @ ref
                    argNames2.add(check(ChuckLexer.TokenType.ID) ? advance().value() : "_");
                    if (check(ChuckLexer.TokenType.LBRACKET)) { advance(); if (!check(ChuckLexer.TokenType.RBRACKET)) advance(); consume(ChuckLexer.TokenType.RBRACKET, ""); }
                } while (match(ChuckLexer.TokenType.COMMA));
            }
            consume(ChuckLexer.TokenType.RPAREN, "Expected ')'");
            ChuckAST.Stmt body2 = parseStatement();
            return new ChuckAST.FuncDefStmt("void", "@" + constructName, argTypes2, argNames2, body2, isStatic, start.line(), start.column());
        }

        // Handle @operator return type prefix: fun @operator +
        boolean hasAtOperator = false;
        if (peek().type() == ChuckLexer.TokenType.ID && peek().value().startsWith("@")) {
            hasAtOperator = true;
            advance(); // @operator token
        }

        String returnType = advance().value(); // return type
        // array return type like int[][]
        while (match(ChuckLexer.TokenType.LBRACKET)) consume(ChuckLexer.TokenType.RBRACKET, "Expected ']'");

        // function name — could be ID or operator symbol
        // Special case: fun ClassName(...) — constructor, no separate name token
        String funcName;
        if (check(ChuckLexer.TokenType.LPAREN)) {
            funcName = returnType; returnType = "void";
        } else if (check(ChuckLexer.TokenType.AT)) {
            // fun ReturnType @operator symbol (...) — operator overload with @operator keyword
            advance(); // skip "@"
            if (check(ChuckLexer.TokenType.ID)) advance(); // skip "operator" word
            // skip operator symbol(s) until LPAREN
            String opPrefix = isPublic ? "__pub_op__" : "__op__";
            StringBuilder opName = new StringBuilder(opPrefix);
            while (!check(ChuckLexer.TokenType.LPAREN) && !isAtEnd()) {
                opName.append(advance().value());
            }
            funcName = opName.toString();
        } else if (check(ChuckLexer.TokenType.ID)) {
            funcName = advance().value();
        } else {
            // operator overloading: skip operator symbol tokens
            funcName = "__op__" + advance().value();
        }

        consume(ChuckLexer.TokenType.LPAREN, "Expected '('");
        List<String> argTypes = new ArrayList<>();
        List<String> argNames = new ArrayList<>();
        if (!check(ChuckLexer.TokenType.RPAREN)) {
            do {
                argTypes.add(advance().value()); // type
                // array suffix on param type: int arr[]
                if (check(ChuckLexer.TokenType.LBRACKET)) {
                    advance(); // [
                    if (!check(ChuckLexer.TokenType.RBRACKET)) advance(); // size if present
                    consume(ChuckLexer.TokenType.RBRACKET, "Expected ']'");
                }
                if (peek().type() == ChuckLexer.TokenType.AT) advance(); // skip @ ref
                if (check(ChuckLexer.TokenType.ID)) {
                    argNames.add(advance().value());
                } else {
                    argNames.add("_");
                }
                // handle optional array suffix on param name: int arr[]
                if (check(ChuckLexer.TokenType.LBRACKET)) {
                    advance(); // [
                    if (!check(ChuckLexer.TokenType.RBRACKET)) advance();
                    consume(ChuckLexer.TokenType.RBRACKET, "Expected ']'");
                }
            } while (match(ChuckLexer.TokenType.COMMA));
        }
        consume(ChuckLexer.TokenType.RPAREN, "Expected ')'");

        // Handle optional postfix operator token (e.g. `fun Number @operator (Number n) ++`)
        if (!check(ChuckLexer.TokenType.LBRACE)) {
            while (!check(ChuckLexer.TokenType.LBRACE) && !isAtEnd()) advance(); // skip
        }

        ChuckAST.Stmt body = parseStatement();
        return new ChuckAST.FuncDefStmt(returnType, funcName, argTypes, argNames, body, isStatic, start.line(), start.column());
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

    private ChuckAST.Stmt parseUntil() {
        ChuckLexer.Token start = consume(ChuckLexer.TokenType.UNTIL, "Expected 'until'");
        consume(ChuckLexer.TokenType.LPAREN, "Expected '(' after 'until'");
        ChuckAST.Exp condition = parseExpression();
        consume(ChuckLexer.TokenType.RPAREN, "Expected ')' after condition");
        ChuckAST.Stmt body = parseStatement();
        return new ChuckAST.UntilStmt(condition, body, start.line(), start.column());
    }

    private ChuckAST.Stmt parseDo() {
        ChuckLexer.Token start = consume(ChuckLexer.TokenType.DO, "Expected 'do'");
        ChuckAST.Stmt body = parseStatement();
        boolean isUntil = false;
        if (match(ChuckLexer.TokenType.WHILE)) {
            isUntil = false;
        } else if (match(ChuckLexer.TokenType.UNTIL)) {
            isUntil = true;
        } else {
            throw new RuntimeException("Expected 'while' or 'until' after 'do' body at line " + start.line());
        }
        consume(ChuckLexer.TokenType.LPAREN, "Expected '('");
        ChuckAST.Exp condition = parseExpression();
        consume(ChuckLexer.TokenType.RPAREN, "Expected ')'");
        match(ChuckLexer.TokenType.SEMICOLON);
        return new ChuckAST.DoStmt(body, condition, isUntil, start.line(), start.column());
    }

    private ChuckAST.Stmt parseFor() {
        ChuckLexer.Token start = consume(ChuckLexer.TokenType.FOR, "Expected 'for'");
        consume(ChuckLexer.TokenType.LPAREN, "Expected '(' after 'for'");

        // Detect range-for: for( type name : collection )
        // Look ahead: if we see  ID ID :  or  type name :
        if (isRangeFor()) {
            ChuckLexer.Token iterType = advance(); // type
            String iterName = consume(ChuckLexer.TokenType.ID, "Expected iterator name").value();
            // consume optional [] array suffixes on iterator type
            while (check(ChuckLexer.TokenType.LBRACKET) && peekAt(1).type() == ChuckLexer.TokenType.RBRACKET) {
                advance(); advance();
            }
            consume(ChuckLexer.TokenType.COLON, "Expected ':' in range-for");
            ChuckAST.Exp collection = parseExpression();
            consume(ChuckLexer.TokenType.RPAREN, "Expected ')' after range-for header");
            ChuckAST.Stmt body = parseStatement();
            return new ChuckAST.ForEachStmt(iterType.value(), iterName, collection, body, start.line(), start.column());
        }

        // Classic for: for( init; condition; update )
        ChuckAST.Stmt init = parseStatement();
        ChuckAST.Stmt condition = parseStatement();
        ChuckAST.Exp update = parseExpression();
        consume(ChuckLexer.TokenType.RPAREN, "Expected ')' after for header");
        ChuckAST.Stmt body = parseStatement();
        return new ChuckAST.ForStmt(init, condition, update, body, start.line(), start.column());
    }

    /** True if next tokens look like a range-for header: type name [[] ...] : */
    private boolean isRangeFor() {
        if (pos + 2 >= tokens.size()) return false;
        ChuckLexer.Token t0 = tokens.get(pos);
        ChuckLexer.Token t1 = tokens.get(pos + 1);
        if (t0.type() != ChuckLexer.TokenType.ID) return false;
        if (t1.type() != ChuckLexer.TokenType.ID) return false;
        // skip optional [] [] suffixes after name
        int look = pos + 2;
        while (look + 1 < tokens.size()
                && tokens.get(look).type() == ChuckLexer.TokenType.LBRACKET
                && tokens.get(look + 1).type() == ChuckLexer.TokenType.RBRACKET) {
            look += 2;
        }
        return look < tokens.size() && tokens.get(look).type() == ChuckLexer.TokenType.COLON;
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

    // ── helpers ────────────────────────────────────────────────────────────────

    private ChuckLexer.Token peekAt(int offset) {
        int idx = pos + offset;
        if (idx >= tokens.size()) return tokens.get(tokens.size() - 1); // EOF
        return tokens.get(idx);
    }

    private boolean match(ChuckLexer.TokenType... types) {
        for (ChuckLexer.TokenType type : types) {
            if (check(type)) { advance(); return true; }
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
