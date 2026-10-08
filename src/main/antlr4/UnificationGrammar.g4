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
    | skipStmt
    | whitespaceStmt
    | production
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

// Lexical categories matched and discarded between tokens, like whitespace (S-L4)
skipStmt
    : 'skip' IDENTIFIER (',' IDENTIFIER)* ';'
    ;

// Replaces the default whitespace (\s+) skipped between tokens; 'none' disables it (S-L4)
whitespaceStmt
    : 'whitespace' (regex | IDENTIFIER) ';'      // the identifier must be 'none'
    ;

production
    : lexicalRule
    | grammarRule
    ;

// Lexical rules: RHS is terminals/regexes only, allows state constructs
lexicalRule
    : lhs '-->' lexicalRhs ('|' lexicalRhs)* whereClause? stateTransition? costClause? ';'
    ;

// Grammar rules: RHS can have nonterminals, NO state constructs
// Top-level '|' separates alternatives; each becomes its own production. An empty alternative
// is an empty production.
grammarRule
    : lhs '-->' rhs ('|' rhs)* whereClause? costClause? ';'
    ;

// Production cost (S-P7): added to the penalty whenever the production is used
costClause
    : '@' NUMBER
    ;

stateTransition
    : '==>' IDENTIFIER         #pushState
    | '==>' '_'                #popState
    | '==>' '!' IDENTIFIER     #resetState
    | '==>' '^' IDENTIFIER     #replaceState
    ;

lhs
    : IDENTIFIER featureStruct?
    ;

rhs
    : element*
    ;

// Lexical RHS: only terminals, regexes, state annotations
lexicalRhs
    : lexicalElement+
    ;

lexicalElement
    : labeledLexicalElement quantifier?
    | lexicalGroup quantifier?
    ;

labeledLexicalElement
    : baseLexicalElement (':' IDENTIFIER)?
    ;

baseLexicalElement
    : terminal
    | regex
    | stateAnnotation
    | tokenMatch
    ;

// A parenthesised group: a sequence, or alternatives that are each a sequence (S-G4)
lexicalGroup
    : '(' lexicalSequence ('|' lexicalSequence)* ')'
    ;

lexicalSequence
    : lexicalElement*
    ;

element
    : labeledElement quantifier?
    | group quantifier?
    ;

labeledElement
    : baseElement (':' IDENTIFIER)?
    ;

baseElement
    : nonterminal
    | terminal
    | regex
    | tokenMatch
    ;

// Special case used for grammars with no lexical rules
tokenMatch
    : '{TOKEN}' featureStruct?
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
    : VARIABLE         // variable
    | IDENTIFIER       // atom
    | STRING           // quoted atom
    | featureStruct    // nested structure
    ;

// A parenthesised group: a sequence, or alternatives that are each a sequence (S-G4)
group
    : '(' sequence ('|' sequence)* ')'
    ;

sequence
    : element*
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
    : (VARIABLE | IDENTIFIER) ('.' IDENTIFIER)*
    ;

// Lexer rules

VARIABLE
    : '$' [a-zA-Z_][a-zA-Z0-9_]*
    ;

IDENTIFIER
    : [a-zA-Z_][a-zA-Z0-9_]*
    ;

STRING
    : '\'' (ESC_SEQ | ~['\\])* '\''
    | '"' (ESC_SEQ | ~["\\])* '"'
    ;

fragment ESC_SEQ
    : '\\' ('u' HEX HEX HEX HEX | ~'u')
    ;

fragment HEX
    : [0-9a-fA-F]
    ;

REGEX
    : RegexClass Quantity?                        // character class
    | '(?:' RegexContent ')' Quantity?            // non-capturing group
    | '(?!' RegexContent ')' Quantity?            // negative lookahead
    | '(?=' RegexContent ')' Quantity?            // positive lookahead
    | '(?<!' RegexContent ')'                     // negative lookbehind
    | '(?<=' RegexContent ')'                     // positive lookbehind
    | '.' Quantity?                               // dot metacharacter
    ;

fragment RegexClass
    : '[' (~[\]\\] | '\\' .)+ ']'
    ;

// Greedy, lazy (?) or possessive (+) quantifiers, including bounded {n}, {n,} and {n,m}
fragment Quantity
    : ([+*?] | '{' [0-9]+ (',' [0-9]*)? '}') [?+]?
    ;

// Group contents: parentheses nest, and a character class may contain parentheses
fragment RegexContent
    : (~[)(\\[] | '\\' . | '(' RegexContent ')' | RegexClass)*
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
