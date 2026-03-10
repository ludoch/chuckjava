package org.chuck.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple lexer for the ChucK language.
 * Supports single-line (//) and multi-line (slash-star) comments.
 */
public class ChuckLexer {
    public enum TokenType {
        ID, INT, FLOAT, STRING, 
        PLUS, MINUS, TIMES, DIVIDE,
        LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
        COMMA, SEMICOLON, DOT,
        COLON_COLON, // :: operator
        CHUCK, // => operator
        AT_CHUCK, // @=> operator
        IF, ELSE, WHILE, FOR, REPEAT, RETURN,
        NEW, SPORK, FUN,
        LT, GT, LE, GE, EQ_EQ, NEQ,
        TILDE,
        EOF
    }

    public record Token(TokenType type, String value, int line, int column) {}

    private final String source;
    private int pos = 0;
    private int line = 1;
    private int column = 1;

    public ChuckLexer(String source) {
        this.source = source;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < source.length()) {
            char c = source.charAt(pos);
            
            // Handle Whitespace
            if (Character.isWhitespace(c)) {
                if (c == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
                pos++;
                continue;
            }

            // Handle Comments
            if (c == '/') {
                if (peek() == '/') {
                    // Single-line comment
                    while (pos < source.length() && source.charAt(pos) != '\n') {
                        pos++;
                    }
                    // Don't skip the newline so it can be handled by the whitespace logic
                    continue;
                } else if (peek() == '*') {
                    // Multi-line comment
                    pos += 2;
                    column += 2;
                    while (pos < source.length() - 1 && !(source.charAt(pos) == '*' && source.charAt(pos + 1) == '/')) {
                        if (source.charAt(pos) == '\n') {
                            line++;
                            column = 1;
                        } else {
                            column++;
                        }
                        pos++;
                    }
                    pos += 2; // skip */
                    column += 2;
                    continue;
                }
            }

            if (Character.isDigit(c)) {
                tokens.add(lexNumber());
            } else if (Character.isLetter(c) || c == '_') {
                tokens.add(lexIdentifier());
            } else if (c == '=') {
                if (peek() == '>') {
                    tokens.add(new Token(TokenType.CHUCK, "=>", line, column));
                    pos += 2;
                    column += 2;
                } else if (peek() == '=') {
                    tokens.add(new Token(TokenType.EQ_EQ, "==", line, column));
                    pos += 2;
                    column += 2;
                } else {
                    tokens.add(new Token(TokenType.ID, "=", line, column));
                    pos++;
                    column++;
                }
            } else if (c == '@') {
                if (peek() == '=') {
                    if (peek2() == '>') {
                        tokens.add(new Token(TokenType.AT_CHUCK, "@=>", line, column));
                        pos += 3;
                        column += 3;
                    } else {
                        tokens.add(new Token(TokenType.ID, "@=", line, column));
                        pos += 2;
                        column += 2;
                    }
                } else {
                    tokens.add(new Token(TokenType.ID, "@", line, column));
                    pos++;
                    column++;
                }
            } else if (c == ':') {
                if (peek() == ':') {
                    tokens.add(new Token(TokenType.COLON_COLON, "::", line, column));
                    pos += 2;
                    column += 2;
                } else {
                    tokens.add(new Token(TokenType.ID, ":", line, column));
                    pos++;
                    column++;
                }
            } else if (c == '<') {
                if (peek() == '=') {
                    tokens.add(new Token(TokenType.LE, "<=", line, column));
                    pos += 2;
                    column += 2;
                } else {
                    tokens.add(new Token(TokenType.LT, "<", line, column));
                    pos++;
                    column++;
                }
            } else if (c == '>') {
                if (peek() == '=') {
                    tokens.add(new Token(TokenType.GE, ">=", line, column));
                    pos += 2;
                    column += 2;
                } else {
                    tokens.add(new Token(TokenType.GT, ">", line, column));
                    pos++;
                    column++;
                }
            } else {
                tokens.add(lexPunctuation());
            }
        }
        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }

    private char peek() {
        if (pos + 1 < source.length()) return source.charAt(pos + 1);
        return '\0';
    }

    private char peek2() {
        if (pos + 2 < source.length()) return source.charAt(pos + 2);
        return '\0';
    }

    private Token lexNumber() {
        StringBuilder sb = new StringBuilder();
        int startCol = column;
        boolean isFloat = false;
        while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) {
            char c = source.charAt(pos);
            if (c == '.') isFloat = true;
            sb.append(c);
            pos++;
            column++;
        }
        return new Token(isFloat ? TokenType.FLOAT : TokenType.INT, sb.toString(), line, startCol);
    }

    private Token lexIdentifier() {
        StringBuilder sb = new StringBuilder();
        int startCol = column;
        while (pos < source.length() && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
            sb.append(source.charAt(pos));
            pos++;
            column++;
        }
        String value = sb.toString();
        TokenType type = switch (value) {
            case "if" -> TokenType.IF;
            case "else" -> TokenType.ELSE;
            case "while" -> TokenType.WHILE;
            case "for" -> TokenType.FOR;
            case "repeat" -> TokenType.REPEAT;
            case "return" -> TokenType.RETURN;
            case "new" -> TokenType.NEW;
            case "spork" -> TokenType.SPORK;
            case "fun" -> TokenType.FUN;
            default -> TokenType.ID;
        };
        return new Token(type, value, line, startCol);
    }

    private Token lexPunctuation() {
        char c = source.charAt(pos);
        int startCol = column;
        pos++;
        column++;
        return switch (c) {
            case '+' -> new Token(TokenType.PLUS, "+", line, startCol);
            case '-' -> new Token(TokenType.MINUS, "-", line, startCol);
            case '*' -> new Token(TokenType.TIMES, "*", line, startCol);
            case '/' -> new Token(TokenType.DIVIDE, "/", line, startCol);
            case '(' -> new Token(TokenType.LPAREN, "(", line, startCol);
            case ')' -> new Token(TokenType.RPAREN, ")", line, startCol);
            case '{' -> new Token(TokenType.LBRACE, "{", line, startCol);
            case '}' -> new Token(TokenType.RBRACE, "}", line, startCol);
            case '[' -> new Token(TokenType.LBRACKET, "[", line, startCol);
            case ']' -> new Token(TokenType.RBRACKET, "]", line, startCol);
            case ',' -> new Token(TokenType.COMMA, ",", line, startCol);
            case ';' -> new Token(TokenType.SEMICOLON, ";", line, startCol);
            case '.' -> new Token(TokenType.DOT, ".", line, startCol);
            case '~' -> new Token(TokenType.TILDE, "~", line, startCol);
            default -> new Token(TokenType.ID, String.valueOf(c), line, startCol);
        };
    }
}
