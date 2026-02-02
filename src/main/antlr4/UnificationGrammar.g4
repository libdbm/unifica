grammar UnificationGrammar;

// Parser rules

grammarFile
    : statement* EOF
    ;

statement
    : moduleStmt
    | importStmt
    | exportStmt
    | startStmt
    | rule
    ;

moduleStmt
    : 'module' moduleName ';'
    ;

moduleName
    : IDENTIFIER ('.' IDENTIFIER)*
    ;

importStmt
    : 'import' modulePath importSpec? ';'
    ;

modulePath
    : moduleName
    | STRING
    ;

importSpec
    : '{' IDENTIFIER (',' IDENTIFIER)* '}'
    | '.*'
    ;

exportStmt
    : 'export' '*' ';'                              #exportAll
    | 'export' IDENTIFIER (',' IDENTIFIER)* ';'    #exportList
    ;

startStmt
    : 'start' IDENTIFIER ';'
    ;

rule
    : lexicalRule
    | grammarRule
    ;

// Lexical rules: RHS is terminals/regexes only, allows state constructs
lexicalRule
    : lhs '-->' lexicalRhs whereClause? stateTransition? ';'
    ;

// Grammar rules: RHS can have nonterminals, NO state constructs
grammarRule
    : lhs '-->' rhs whereClause? ';'
    ;

stateTransition
    : '==>' IDENTIFIER         #pushState
    | '==>' '_'                #popState
    | '==>' '!' IDENTIFIER     #resetState
    ;

lhs
    : IDENTIFIER featureStruct?
    ;

rhs
    : element+
    ;

// Lexical RHS: only terminals, regexes, state annotations
lexicalRhs
    : lexicalElement+
    ;

lexicalElement
    : labeledLexicalElement quantifier?
    | lexicalAlternation quantifier?
    ;

labeledLexicalElement
    : baseLexicalElement (':' IDENTIFIER)?
    ;

baseLexicalElement
    : terminal
    | regex
    | stateAnnotation
    | tokenMatch
    | '(' lexicalElement ')'
    ;

lexicalAlternation
    : '(' lexicalAltOption ('|' lexicalAltOption)+ ')'
    ;

lexicalAltOption
    : lexicalElement
    ;

element
    : labeledElement quantifier?
    | alternation quantifier?
    ;

labeledElement
    : baseElement (':' IDENTIFIER)?
    ;

baseElement
    : nonterminal
    | terminal
    | regex
    | tokenMatch
    | '(' element ')'
    ;

// Special case used for grammars with no lexical rules
tokenMatch
    : '{TOKEN}'
    ;

stateAnnotation
    : '{' IDENTIFIER '}'
    ;

nonterminal
    : IDENTIFIER featureStruct?
    ;

terminal
    : STRING
    ;

regex
    : REGEX+
    ;

featureStruct
    : '{' featurePair (',' featurePair)* '}'
    ;

featurePair
    : IDENTIFIER ':' featureValue
    ;

featureValue
    : IDENTIFIER       // atom or variable
    | STRING           // quoted atom
    | featureStruct    // nested structure
    ;

alternation
    : '(' altOption ('|' altOption)+ ')'
    ;

altOption
    : element
    ;

quantifier
    : '*'   // zero or more
    | '+'   // one or more
    | '?'   // zero or one
    ;

whereClause
    : 'where' constraintExpr
    ;

// Constraint expression with disjunction (|) and negation (!)
// Precedence (highest to lowest): ! -> , -> |
constraintExpr
    : constraintTerm ('|' constraintTerm)*      // OR: lowest precedence
    ;

constraintTerm
    : constraintFactor (',' constraintFactor)*  // AND: comma, medium precedence
    ;

constraintFactor
    : '!' constraintFactor                      // NOT: prefix, highest precedence
    | '(' constraintExpr ')' penalty?           // grouping with optional penalty
    | predicate penalty?                        // predicate with optional penalty
    ;

// Defeasible penalty annotation - makes constraint soft with given penalty value
penalty
    : ':' NUMBER
    ;

predicate
    : IDENTIFIER '(' args? ')'
    ;

args
    : arg (',' arg)*
    ;

arg
    : featurePath
    | STRING
    | featureStruct
    ;

featurePath
    : IDENTIFIER ('.' IDENTIFIER)*
    ;

// Lexer rules

IDENTIFIER
    : [a-zA-Z_][a-zA-Z0-9_]*
    ;

STRING
    : '\'' (ESC_SEQ | ~['\\])* '\''
    | '"' (ESC_SEQ | ~["\\])* '"'
    ;

fragment ESC_SEQ
    : '\\' .
    ;

REGEX
    : '[' (~[\]\\] | '\\' .)+ ']' [+*?]?          // character class
    | '(?:' RegexContent ')' [+*?]?               // non-capturing group
    | '(?!' RegexContent ')' [+*?]?               // negative lookahead
    | '(?=' RegexContent ')' [+*?]?               // positive lookahead
    | '.' [+*?]?                                  // dot metacharacter
    ;

fragment RegexContent
    : (~[)\\] | '\\' . | '(' RegexContent ')')*
    ;

NUMBER
    : [0-9]+
    ;

COMMENT
    : '#' ~[\r\n]* -> skip
    ;

WS
    : [ \t\r\n]+ -> skip
    ;
