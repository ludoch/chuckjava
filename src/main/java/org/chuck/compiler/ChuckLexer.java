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
        PLUS, MINUS, TIMES, DIVIDE, PERCENT,
        PLUS_PLUS, MINUS_MINUS,           // ++ --
        BANG,                              // !
        AND_AND, OR_OR,                    // && ||
        AMP, PIPE,                         // & | (bitwise)
        SHIFT_RIGHT,                       // >>
        LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
        COMMA, SEMICOLON, DOT, COLON,
        COLON_COLON,                       // ::
        CHUCK,                             // =>
        AT_CHUCK,                          // @=>
        UNCHUCK,                           // !=>
        SWAP,                              // <=>
        ASSIGN,                            // = (plain assignment)
        UPCHUCK,                           // =^
        PLUS_CHUCK, MINUS_CHUCK, TIMES_CHUCK, DIVIDE_CHUCK, PERCENT_CHUCK, // +=> -= *= /=> %=>
        PIPE_CHUCK, AMP_CHUCK,             // |=> &=>
        APPEND,                            // <<
        DOLLAR,                            // $ (cast operator)
        HASH,                              // # (complex literal)
        QUESTION,                          // ? (ternary)
        IF, ELSE, WHILE, FOR, REPEAT, RETURN, BREAK, CONTINUE,
        NEW, SPORK, FUN, CLASS, EXTENDS, PUBLIC, STATIC,
        LT, GT, LE, GE, EQ_EQ, NEQ,
        WRITE_IO,                          // <=
        TILDE,
        PRINT_START, PRINT_END, // <<< and >>>
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
                    if (pos < source.length() - 1) {
                        pos += 2; column += 2;
                    }
                    continue;
                }
            }

            if (c == '\'') {
                // Character literal: 'x' or '\n' etc.
                int p = pos + 1;
                char ch;
                if (p < source.length() && source.charAt(p) == '\\' && p + 1 < source.length()) {
                    char esc = source.charAt(p + 1);
                    ch = switch (esc) {
                        case 'n' -> '\n'; case 't' -> '\t'; case 'r' -> '\r';
                        case '\\' -> '\\'; case '\'' -> '\''; case '0' -> '\0';
                        default -> esc;
                    };
                    p += 2;
                } else if (p < source.length()) {
                    ch = source.charAt(p);
                    p++;
                } else { ch = 0; }
                if (p < source.length() && source.charAt(p) == '\'') {
                    tokens.add(new Token(TokenType.INT, String.valueOf((long) ch), line, column));
                    int consumed = p + 1 - pos; pos = p + 1; column += consumed;
                    continue; // character literal handled
                }
                // Not a valid char literal — fall through to lexPunctuation for the single quote
            }
            if (Character.isDigit(c)) {
                tokens.add(lexNumber());
            } else if (Character.isLetter(c) || c == '_') {
                tokens.add(lexIdentifier());
            } else if (c == '"') {
                tokens.add(lexString());
            } else if (c == '=') {
                if (peek() == '>') {
                    tokens.add(new Token(TokenType.CHUCK, "=>", line, column));
                    pos += 2; column += 2;
                } else if (peek() == '=') {
                    tokens.add(new Token(TokenType.EQ_EQ, "==", line, column));
                    pos += 2; column += 2;
                } else if (peek() == '^') {
                    tokens.add(new Token(TokenType.UPCHUCK, "=^", line, column));
                    pos += 2; column += 2;
                } else if (peek() == '<') {
                    tokens.add(new Token(TokenType.UNCHUCK, "=<", line, column));
                    pos += 2; column += 2;
                } else {
                    tokens.add(new Token(TokenType.ASSIGN, "=", line, column));
                    pos++; column++;
                }
            } else if (c == '!') {
                if (peek() == '=' && peek2() == '>') {
                    tokens.add(new Token(TokenType.UNCHUCK, "!=>", line, column));
                    pos += 3; column += 3;
                } else if (peek() == '=') {
                    tokens.add(new Token(TokenType.NEQ, "!=", line, column));
                    pos += 2; column += 2;
                } else {
                    tokens.add(new Token(TokenType.BANG, "!", line, column));
                    pos++; column++;
                }
            } else if (c == '&') {
                if (peek() == '=' && peek2() == '>') {
                    tokens.add(new Token(TokenType.AMP_CHUCK, "&=>", line, column));
                    pos += 3; column += 3;
                } else if (peek() == '&') {
                    tokens.add(new Token(TokenType.AND_AND, "&&", line, column));
                    pos += 2; column += 2;
                } else {
                    tokens.add(new Token(TokenType.AMP, "&", line, column));
                    pos++; column++;
                }
            } else if (c == '|') {
                if (peek() == '=' && peek2() == '>') {
                    tokens.add(new Token(TokenType.PIPE_CHUCK, "|=>", line, column));
                    pos += 3; column += 3;
                } else if (peek() == '|') {
                    tokens.add(new Token(TokenType.OR_OR, "||", line, column));
                    pos += 2; column += 2;
                } else {
                    tokens.add(new Token(TokenType.PIPE, "|", line, column));
                    pos++; column++;
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
                    tokens.add(new Token(TokenType.COLON, ":", line, column));
                    pos++;
                    column++;
                }
            } else if (c == '<') {
                if (peek() == '=' && peek2() == '>') {
                    tokens.add(new Token(TokenType.SWAP, "<=>", line, column));
                    pos += 3; column += 3;
                } else if (peek() == '<' && peek2() == '<') {
                    tokens.add(new Token(TokenType.PRINT_START, "<<<", line, column));
                    pos += 3; column += 3;
                } else if (peek() == '<') {
                    tokens.add(new Token(TokenType.APPEND, "<<", line, column));
                    pos += 2; column += 2;
                } else if (peek() == '=') {
                    tokens.add(new Token(TokenType.WRITE_IO, "<=", line, column));
                    pos += 2; column += 2;
                } else {
                    tokens.add(new Token(TokenType.LT, "<", line, column));
                    pos++; column++;
                }
            } else if (c == '>') {
                if (peek() == '>' && peek2() == '>') {
                    tokens.add(new Token(TokenType.PRINT_END, ">>>", line, column));
                    pos += 3; column += 3;
                } else if (peek() == '>') {
                    tokens.add(new Token(TokenType.SHIFT_RIGHT, ">>", line, column));
                    pos += 2; column += 2;
                } else if (peek() == '=') {
                    tokens.add(new Token(TokenType.GE, ">=", line, column));
                    pos += 2; column += 2;
                } else {
                    tokens.add(new Token(TokenType.GT, ">", line, column));
                    pos++; column++;
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
        int startCol = column;
        // Hex literal: 0x or 0X
        if (source.charAt(pos) == '0' && pos + 1 < source.length()
                && (source.charAt(pos + 1) == 'x' || source.charAt(pos + 1) == 'X')) {
            pos += 2; column += 2;
            StringBuilder hex = new StringBuilder();
            while (pos < source.length() && isHexDigit(source.charAt(pos))) {
                hex.append(source.charAt(pos++)); column++;
            }
            long val = hex.length() > 0 ? Long.parseUnsignedLong(hex.toString(), 16) : 0;
            return new Token(TokenType.INT, String.valueOf(val), line, startCol);
        }
        StringBuilder sb = new StringBuilder();
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

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
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
            case "break" -> TokenType.BREAK;
            case "continue" -> TokenType.CONTINUE;
            case "new" -> TokenType.NEW;
            case "spork" -> TokenType.SPORK;
            case "fun" -> TokenType.FUN;
            case "class" -> TokenType.CLASS;
            case "extends" -> TokenType.EXTENDS;
            case "public" -> TokenType.PUBLIC;
            case "static" -> TokenType.STATIC;
            default -> TokenType.ID;
        };
        return new Token(type, value, line, startCol);
    }

    private Token lexPunctuation() {
        char c = source.charAt(pos);
        int startCol = column;
        // Handle dot-leading float literals like .5
        if (c == '.' && Character.isDigit(peek())) {
            return lexNumber();
        }
        // Compound chuck operators: +=> -= *= /=> %=>
        if (peek() == '=' && peek2() == '>') {
            TokenType ct = switch (c) {
                case '+' -> TokenType.PLUS_CHUCK;
                case '-' -> TokenType.MINUS_CHUCK;
                case '*' -> TokenType.TIMES_CHUCK;
                case '/' -> TokenType.DIVIDE_CHUCK;
                case '%' -> TokenType.PERCENT_CHUCK;
                default  -> null;
            };
            if (ct != null) { pos += 3; column += 3; return new Token(ct, c + "=>", line, startCol); }
        }
        // Handle two-character operators before consuming
        if (c == '+' && peek() == '+') {
            pos += 2; column += 2;
            return new Token(TokenType.PLUS_PLUS, "++", line, startCol);
        }
        if (c == '-' && peek() == '-') {
            pos += 2; column += 2;
            return new Token(TokenType.MINUS_MINUS, "--", line, startCol);
        }
        pos++;
        column++;
        return switch (c) {
            case '+' -> new Token(TokenType.PLUS, "+", line, startCol);
            case '-' -> new Token(TokenType.MINUS, "-", line, startCol);
            case '*' -> new Token(TokenType.TIMES, "*", line, startCol);
            case '/' -> new Token(TokenType.DIVIDE, "/", line, startCol);
            case '%' -> new Token(TokenType.PERCENT, "%", line, startCol);
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
            case '$' -> new Token(TokenType.DOLLAR, "$", line, startCol);
            case '#' -> new Token(TokenType.HASH, "#", line, startCol);
            case '?' -> new Token(TokenType.QUESTION, "?", line, startCol);
            default -> new Token(TokenType.ID, String.valueOf(c), line, startCol);
        };
    }

    private Token lexString() {
        int startCol = column;
        pos++; column++; // skip opening "
        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && source.charAt(pos) != '"') {
            char c = source.charAt(pos);
            if (c == '\\' && pos + 1 < source.length()) {
                pos++; column++;
                char esc = source.charAt(pos);
                sb.append(switch (esc) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> esc;
                });
            } else {
                if (c == '\n') { line++; column = 1; } else { column++; }
                sb.append(c);
            }
            pos++;
        }
        if (pos < source.length()) { pos++; column++; } // skip closing "
        return new Token(TokenType.STRING, sb.toString(), line, startCol);
    }
}
