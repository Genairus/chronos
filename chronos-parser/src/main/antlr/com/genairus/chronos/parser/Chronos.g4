grammar Chronos;

// Entry point
model
    : namespaceDecl journey* EOF
    ;

namespaceDecl
    : 'namespace' QUALIFIED_ID NEWLINE
    ;

journey
    : 'journey' ID '{' journeyBody '}' 
    ;

journeyBody
    : actorDecl stepsDecl
    ;

actorDecl
    : 'actor' ':' ID NEWLINE
    ;

stepsDecl
    : 'steps' ':' '[' step (',' step)* ']'
    ;

step
    : 'step' ID '{' stepBody '}'
    ;

stepBody
    : actionDecl expectationDecl
    ;

actionDecl
    : 'action' ':' STRING NEWLINE
    ;

expectationDecl
    : 'expectation' ':' STRING NEWLINE
    ;

// Tokens
ID          : [a-zA-Z][a-zA-Z0-9_]* ;
QUALIFIED_ID: ID ('.' ID)+ ;
STRING      : '"' (~["\r\n])* '"' ;
NEWLINE     : [\r\n]+ -> skip ;
WS          : [ \t]+ -> skip ;
COMMENT     : '//' ~[\r\n]* -> skip ;