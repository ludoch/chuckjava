grammar ChuckANTLR;

/*
 * ChucK ANTLR4 Grammar - Cleaned up for non-combined usage
 */

program : (directive | statement | functionDef | classDef)* EOF ;

directive
    : REFERENCE_TAG IMPORT STRING                        // @import "file.ck"
    | REFERENCE_TAG ID STRING?                             // @doc "...", @global, etc.
    ;

// --- Statements ---
statement
    : ifStatement                                          # ifStmt
    | whileStatement                                       # whileStmt
    | untilStatement                                       # untilStmt
    | forStatement                                         # forStmt
    | repeatStatement                                      # repeatStmt
    | doStatement                                          # doStmt
    | returnStatement                                      # returnStmt
    | printStatement                                       # printStmt
    | blockStatement                                       # blockStmt
    | expression SEMI                                      # expressionStmt
    | BREAK SEMI                                           # breakStmt
    | CONTINUE SEMI                                        # continueStmt
    | SEMI                                                 # emptyStmt
    ;

ifStatement: IF LPAREN expression RPAREN statement (ELSE statement)? ;
whileStatement: WHILE LPAREN expression RPAREN statement ;
untilStatement: UNTIL LPAREN expression RPAREN statement ;
forStatement
    : FOR LPAREN (expression? SEMI) (expression? SEMI) expression? RPAREN statement
    | FOR LPAREN (type|AUTO) REFERENCE_TAG? ID (arrayDimension)* COLON expression RPAREN statement
    ;
repeatStatement: REPEAT LPAREN expression RPAREN statement ;
doStatement: DO statement (WHILE|UNTIL) LPAREN expression RPAREN SEMI ;
returnStatement: RETURN expression? SEMI ;
printStatement: LTRIPLE expressionList? RTRIPLE SEMI ;
blockStatement: LBRACE (statement)* RBRACE ;

variableDecl
    : REFERENCE_TAG? ID (LPAREN expressionList? RPAREN)? (arrayDimension)* (CHUCK_OP expression)?
    ;

accessModifier
    : PUBLIC | PRIVATE | PROTECTED | GLOBAL
    ;

arrayDimension
    : LBRACK expression? RBRACK
    ;

expressionList
    : expression (COMMA expression)*
    ;

// --- Definitions ---
functionDef
    : (accessModifier? (STATIC? FUN | FUN STATIC?) | PUBLIC) type? functionName LPAREN formalParameters? RPAREN postfixOpToken? statement
    ;

postfixOpToken : PLUS_PLUS | MINUS_MINUS ;

functionName
    : ID
    | REFERENCE_TAG ID
    | REFERENCE_TAG OPERATOR operatorToken?
    ;

operatorToken
    : PLUS | MINUS | TIMES | DIVIDE | MOD | PLUS_PLUS | MINUS_MINUS | BANG | AND | OR
    | AMP | PIPE | CARET | LT | GT | GE | EQ | NEQ | TILDE | LSHIFT | RSHIFT | HASH
    | CHUCK_OP | QUESTION | COLON
    ;

formalParameters
    : formalParameter (COMMA formalParameter)*
    ;

formalParameter
    : type REFERENCE_TAG? ID (arrayDimension)*
    ;

classDef
    : accessModifier? CLASS ID (EXTENDS typeName)? LBRACE (directive | statement | functionDef)* RBRACE
    ;

type
    : typeName (arrayDimension)*
    ;

typeName
    : INT_TYPE | FLOAT_TYPE | TIME_TYPE | DUR_TYPE | VOID_TYPE | COMPLEX_TYPE | POLAR_TYPE | STRING_TYPE | EVENT_TYPE | AUTO | ID
    ;

// Any keyword or ID usable as a member name after '.'
memberName
    : ID
    | INT_TYPE | FLOAT_TYPE | TIME_TYPE | DUR_TYPE | VOID_TYPE | COMPLEX_TYPE | POLAR_TYPE | STRING_TYPE | EVENT_TYPE | AUTO
    | IF | ELSE | WHILE | UNTIL | FOR | REPEAT | DO | RETURN | BREAK | CONTINUE
    | FUN | CLASS | EXTENDS | PUBLIC | PRIVATE | STATIC | PROTECTED | GLOBAL
    | SPORK | NOW | ME | NEW
    ;

// --- Expressions ---
expression
    : primary                                              # primaryExp
    | accessModifier? STATIC? type variableDecl (COMMA variableDecl)* # declExp
    | prefixOp expression                                   # unaryOp
    | expression CAST type                                 # castExp
    | expression COLON_COLON expression                   # durationOp
    | expression (TIMES | DIVIDE | MOD) expression         # binaryOp
    | expression (PLUS | MINUS) expression                 # binaryOp
    | expression (LSHIFT | RSHIFT) expression              # binaryOp
    | expression (LT | GT | LE | GE) expression            # compareOp
    | expression (EQ | NEQ) expression                     # compareOp
    | expression (AND | OR) expression                     # logicalOp
    | expression (AMP | PIPE | CARET) expression           # binaryOp
    | expression CHUCK_OP expression                       # chuckOp
    | expression QUESTION expression COLON expression      # conditionalOp
    | expression (PLUS_PLUS | MINUS_MINUS)                 # postfixOp
    ;

prefixOp : MINUS | BANG | PLUS_PLUS | MINUS_MINUS | SPORK TILDE? | TILDE ;

primary
    : INT                                                  # intLit
    | FLOAT                                                # floatLit
    | STRING                                               # stringLit
    | CHAR_LIT                                             # intLit
    | TRUE                                                 # trueLit
    | FALSE                                                # falseLit
    | NULL                                                 # nullLit
    | NOW                                                  # nowExp
    | ME                                                   # meExp
    | ID                                                   # idExp
    | REFERENCE_TAG ID                                     # idExp
    | primary DOT memberName                               # memberExp
    | primary LBRACK expressionList RBRACK                 # arrayAccessExp
    | primary LPAREN expressionList? RPAREN                # callExp
    | LPAREN expressionList? RPAREN                        # parenExp
    | LBRACK expressionList? RBRACK                        # arrayLitExp
    | REFERENCE_TAG LPAREN expressionList? RPAREN          # vectorLitExp
    | NEW typeName (LPAREN expressionList? RPAREN)? (arrayDimension)* # newExp
    | HASH LPAREN expression COMMA expression RPAREN       # complexLit
    | MOD LPAREN expression COMMA expression RPAREN        # polarLit
    ;

// --- Lexer Rules ---

IF      : 'if';
ELSE    : 'else';
WHILE   : 'while';
UNTIL   : 'until';
FOR     : 'for';
REPEAT  : 'repeat';
DO      : 'do';
RETURN  : 'return';
BREAK   : 'break';
CONTINUE: 'continue';
FUN     : 'fun';
CLASS   : 'class';
EXTENDS : 'extends';
PUBLIC  : 'public';
PRIVATE : 'private';
STATIC  : 'static';
PROTECTED: 'protected';
GLOBAL  : 'global';
SPORK   : 'spork';
NOW     : 'now';
ME      : 'me';
NEW     : 'new';
AUTO    : 'auto';

INT_TYPE     : 'int';
FLOAT_TYPE   : 'float';
TIME_TYPE    : 'time';
DUR_TYPE     : 'dur';
VOID_TYPE    : 'void';
COMPLEX_TYPE : 'complex';
POLAR_TYPE   : 'polar';
STRING_TYPE  : 'string';
EVENT_TYPE   : 'Event';

IMPORT       : 'import';
OPERATOR     : 'operator';
TRUE         : 'true';
FALSE        : 'false';
NULL         : 'null';

CHUCK_OP     : '=>' | '@=>' | '!=>' | '=^' | '<=>' | '=<' | '+=>' | '-=>' | '*=>' | '/=>' | '%=>' | '&=>' | '|=>' | '^=>' | '<<=>' | '>>=>' | '-->' ;

PLUS         : '+';
MINUS        : '-';
TIMES        : '*';
DIVIDE       : '/';
MOD          : '%';
PLUS_PLUS    : '++';
MINUS_MINUS  : '--';
BANG         : '!';
AND          : '&&';
OR           : '||';
AMP          : '&';
PIPE         : '|';
CARET        : '^';
LT           : '<';
GT           : '>';
LE           : '<=';
GE           : '>=';
EQ           : '==';
NEQ          : '!=';
TILDE        : '~';
QUESTION     : '?';
COLON_COLON  : '::';
COLON        : ':';
CAST         : '$';
LSHIFT       : '<<';
RSHIFT       : '>>';
HASH         : '#';
REFERENCE_TAG : '@';

SEMI         : ';';
COMMA        : ',';
LPAREN       : '(';
RPAREN       : ')';
LBRACE       : '{';
RBRACE       : '}';
LBRACK       : '[';
RBRACK       : ']';
DOT          : '.';
LTRIPLE      : '<<<';
RTRIPLE      : '>>>';

ID : [a-zA-Z_] [a-zA-Z0-9_]* ;
INT : '0x' [0-9a-fA-F]+ | [0-9]+ ;
FLOAT : [0-9]* '.' [0-9]+ | [0-9]+ '.' [0-9]* ;
STRING : '"' ( ESC | ~["\\] )* '"' ;
CHAR_LIT : '\'' ( ESC | ~['\\] ) '\'' ;

fragment ESC : '\\' . ;

WS : [ \t\r\n]+ -> skip ;
COMMENT : '//' ~[\r\n]* -> skip ;
MULTILINE_COMMENT : '/*' .*? '*/' -> skip ;
