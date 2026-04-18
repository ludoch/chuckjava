grammar ChuckANTLR;

/*
 * ChucK ANTLR4 Grammar - Cleaned up for non-combined usage
 */

program : (directive | statement | functionDef | classDefinition | DOC_COMMENT)* EOF ;

directive
    : REFERENCE_TAG IMPORT STRING                        // @import "file.ck"
    | REFERENCE_TAG ID STRING?                             // @doc "...", @global, etc.
    | DOC_COMMENT
    ;

// --- Statements ---
statement
    : ifStatement                                          # ifStmt
    | whileStatement                                       # whileStmt
    | untilStatement                                       # untilStmt
    | forStatement                                         # forStmt
    | repeatStatement                                      # repeatStmt
    | doStatement                                          # doStmt
    | loopStatement                                        # loopStmt
    | returnStatement                                      # returnStmt
    | printStatement                                       # printStmt
    | blockStatement                                       # blockStmt
    | DOC_COMMENT? expression (COMMA expression)* SEMI     # expressionStmt
    | switchStatement                                      # switchStmt
    | BREAK SEMI                                           # breakStmt
    | CONTINUE SEMI                                        # continueStmt
    | SEMI                                                 # emptyStmt
    ;

ifStatement: DOC_COMMENT? IF LPAREN expression RPAREN statement (ELSE statement)? ;
whileStatement: DOC_COMMENT? WHILE LPAREN expression RPAREN statement ;
untilStatement: DOC_COMMENT? UNTIL LPAREN expression RPAREN statement ;
forStatement
    : DOC_COMMENT? FOR LPAREN (expression? SEMI) (expression? SEMI) expression? RPAREN statement
    | DOC_COMMENT? FOR LPAREN (type|AUTO) REFERENCE_TAG? ID (arrayDimension)* COLON expression RPAREN statement
    ;
repeatStatement: DOC_COMMENT? REPEAT LPAREN expression RPAREN statement ;
loopStatement: DOC_COMMENT? LOOP statement ;
doStatement: DOC_COMMENT? DO statement (WHILE|UNTIL) LPAREN expression RPAREN SEMI ;
returnStatement: RETURN expression? SEMI ;
printStatement: LTRIPLE expressionList? RTRIPLE SEMI ;
blockStatement: LBRACE (statement | DOC_COMMENT)* RBRACE ;
switchStatement: SWITCH LPAREN expression RPAREN LBRACE switchCase* RBRACE ;
switchCase: (CASE expression COLON | DEFAULT COLON) (statement | DOC_COMMENT)* ;

variableDecl
    : REFERENCE_TAG? ID (LPAREN expressionList? RPAREN)? (arrayDimension)* (CHUCK_OP expression)?
    ;

accessModifier
    : PUBLIC | PRIVATE | PROTECTED | GLOBAL | CONST | ABSTRACT
    ;

arrayDimension
    : LBRACK expression? RBRACK
    ;

expressionList
    : expression (COMMA expression)*
    ;

// --- Definitions ---
functionDef
    : DOC_COMMENT? (accessModifier? (STATIC? FUN | FUN STATIC?) | PUBLIC) type? (functionName LPAREN formalParameters? RPAREN | REFERENCE_TAG? OPERATOR LPAREN formalParameters? RPAREN postfixOpToken) statement
    ;

postfixOpToken : PLUS_PLUS | MINUS_MINUS ;

functionName
    : ID
    | REFERENCE_TAG ID
    | REFERENCE_TAG? OPERATOR (operatorToken | LPAREN operatorToken RPAREN)?
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

classDefinition
    : DOC_COMMENT? accessModifier? ABSTRACT? CLASS ID (EXTENDS typeName)? LBRACE (directive | statement | functionDef | classDefinition | DOC_COMMENT)* RBRACE
    | DOC_COMMENT? accessModifier? INTERFACE ID (EXTENDS typeName)? LBRACE (functionDef | classDefinition | DOC_COMMENT)* RBRACE
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
    | IF | ELSE | WHILE | UNTIL | FOR | REPEAT | DO | RETURN | BREAK | CONTINUE | SWITCH | CASE | DEFAULT
    | FUN | CLASS | EXTENDS | PUBLIC | PRIVATE | STATIC | PROTECTED | GLOBAL | ABSTRACT | INTERFACE | LOOP
    | SPORK | NOW | ME | NEW | TYPEOF | INSTANCEOF
    ;

// --- Expressions ---
expression
    : primary                                              # primaryExp
    | DOC_COMMENT? accessModifier? STATIC? CONST? type variableDecl (COMMA variableDecl)* # declExp
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
    | expression QUESTION expression COLON expression      # conditionalOp
    | expression CHUCK_OP expression                       # chuckOp
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
    | LBRACK (expressionList COMMA?)? RBRACK               # arrayLitExp
    | REFERENCE_TAG LPAREN expressionList? RPAREN          # vectorLitExp
    | NEW typeName (LPAREN expressionList? RPAREN)? (arrayDimension)* # newExp
    | HASH LPAREN expression COMMA expression RPAREN       # complexLit
    | MOD LPAREN expression COMMA expression RPAREN        # polarLit
    | TYPEOF LPAREN expression RPAREN                      # typeofExp
    | INSTANCEOF LPAREN expression COMMA typeName RPAREN   # instanceofExp
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
SWITCH  : 'switch';
CASE    : 'case';
DEFAULT : 'default';
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
NEW        : 'new';
TYPEOF     : 'typeof';
INSTANCEOF : 'instanceof';
AUTO     : 'auto';
LOOP     : 'loop';
ABSTRACT : 'abstract';
INTERFACE: 'interface';

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
CONST        : 'const';

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
FLOAT : [0-9]+ '.' [0-9]+ | [0-9]+ '.' | '.' [0-9]+ ;
INT : '0x' [0-9a-fA-F]+ | [0-9]+ ;
STRING : '"' ( ESC | ~["\\] )* '"' ;
CHAR_LIT : '\'' ( ESC | ~['\\] ) '\'' ;
ID : [a-zA-Z_] [a-zA-Z0-9_]* ;

DOT          : '.';
LTRIPLE      : '<<<';
RTRIPLE      : '>>>';

fragment ESC : '\\' . ;

WS : [ \t\r\n]+ -> skip ;
COMMENT : '//' ~[\r\n]* -> skip ;
DOC_COMMENT : '/**' .*? '*/' ;
MULTILINE_COMMENT : '/*' ~[*] .*? '*/' -> skip ;

