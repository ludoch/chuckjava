grammar ChuckANTLR;

/*
 * ChucK ANTLR4 Grammar - Ultra Hardened Production Version
 */

program : (directive | statement | functionDef | classDef)* EOF ;

directive
    : REFERENCE_TAG 'import' STRING                        // @import "file.ck"
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
    | expression ';'                                       # expressionStmt
    | 'break' ';'                                          # breakStmt
    | 'continue' ';'                                       # continueStmt
    | ';'                                                  # emptyStmt
    ;

ifStatement: 'if' '(' expression ')' statement ('else' statement)? ;
whileStatement: 'while' '(' expression ')' statement ;
untilStatement: 'until' '(' expression ')' statement ;
forStatement
    : 'for' '(' (expression? ';') (expression? ';') expression? ')' statement
    | 'for' '(' (type|'auto') REFERENCE_TAG? ID (arrayDimension)* ':' expression ')' statement
    ;
repeatStatement: 'repeat' '(' expression ')' statement ;
doStatement: 'do' statement ('while'|'until') '(' expression ')' ';' ;
returnStatement: 'return' expression? ';' ;
printStatement: '<<<' expressionList? '>>>' ';' ;
blockStatement: '{' (statement)* '}' ;

variableDecl
    : REFERENCE_TAG? ID ('(' expressionList? ')')? (arrayDimension)* (CHUCK_OP expression)?
    ;

accessModifier
    : 'public' | 'private' | 'protected' | 'global'
    ;

arrayDimension
    : '[' expression? ']'
    ;

expressionList
    : expression (',' expression)*
    ;

// --- Definitions ---
functionDef
    : (accessModifier? ('static'? 'fun' | 'fun' 'static'?) | 'public') type? functionName '(' formalParameters? ')' postfixOpToken? statement
    ;

postfixOpToken : PLUS_PLUS | MINUS_MINUS ;

functionName
    : ID
    | REFERENCE_TAG ID
    | REFERENCE_TAG 'operator' operatorToken?
    ;

operatorToken
    : PLUS | MINUS | TIMES | DIVIDE | MOD | PLUS_PLUS | MINUS_MINUS | BANG | AND | OR
    | AMP | PIPE | CARET | LT | GT | GE | EQ | NEQ | TILDE | LSHIFT | RSHIFT | HASH
    | CHUCK_OP | QUESTION | COLON
    ;

formalParameters
    : formalParameter (',' formalParameter)*
    ;

formalParameter
    : type REFERENCE_TAG? ID (arrayDimension)*
    ;

classDef
    : accessModifier? 'class' ID ('extends' typeName)? '{' (directive | statement | functionDef)* '}'
    ;

type
    : typeName (arrayDimension)*
    ;

typeName
    : 'int' | 'float' | 'time' | 'dur' | 'void' | 'complex' | 'polar' | 'string' | 'Event' | 'auto' | ID
    ;

// Any keyword or ID usable as a member name after '.'
memberName
    : ID
    | 'int' | 'float' | 'time' | 'dur' | 'void' | 'complex' | 'polar' | 'string' | 'Event' | 'auto'
    | 'if' | 'else' | 'while' | 'until' | 'for' | 'repeat' | 'do' | 'return' | 'break' | 'continue'
    | 'fun' | 'class' | 'extends' | 'public' | 'private' | 'static' | 'protected' | 'global'
    | 'spork' | 'now' | 'me' | 'new'
    ;

// --- Expressions ---
expression
    : primary                                              # primaryExp
    | accessModifier? 'static'? type variableDecl (',' variableDecl)* # declExp
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
    | 'true'                                               # trueLit
    | 'false'                                              # falseLit
    | 'null'                                               # nullLit
    | 'now'                                                # nowExp
    | 'me'                                                 # meExp
    | ID                                                   # idExp
    | REFERENCE_TAG ID                                     # idExp
    | primary '.' memberName                               # memberExp
    | primary '[' expression ']'                           # arrayAccessExp
    | primary '(' expressionList? ')'                      # callExp
    | '(' expressionList? ')'                              # parenExp
    | '[' expressionList? ']'                              # arrayLitExp
    | REFERENCE_TAG '(' expressionList? ')'                # vectorLitExp
    | 'new' type ('(' expressionList? ')')? (arrayDimension)* # newExp
    | HASH '(' expression ',' expression ')'               # complexLit
    | MOD '(' expression ',' expression ')'                # polarLit
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

CHUCK_OP     : '=>' | '@=>' | '!=>' | '=^' | '<=>' | '<=' | '=<' | '+=>' | '-=>' | '*=>' | '/=>' | '%=>' | '&=>' | '|=>' | '^=>' | '<<=>' | '>>=>' | '-->' ;

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

ID : [a-zA-Z_] [a-zA-Z0-9_]* ;
INT : '0x' [0-9a-fA-F]+ | [0-9]+ ;
FLOAT : [0-9]* '.' [0-9]+ | [0-9]+ '.' [0-9]* ;
STRING : '"' ( ESC | ~["\\] )* '"' ;
CHAR_LIT : '\'' ( ESC | ~['\\] ) '\'' ;

fragment ESC : '\\' . ;

WS : [ \t\r\n]+ -> skip ;
COMMENT : '//' ~[\r\n]* -> skip ;
MULTILINE_COMMENT : '/*' .*? '*/' -> skip ;

LE : '<-never->' ;
