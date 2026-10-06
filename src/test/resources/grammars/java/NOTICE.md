# Java grammar port

## Source

- `java/java/JavaLexer.g4` and `java/java/JavaParser.g4` from [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- Licence: BSD licence (3-clause), as stated in the header of both files.
- Authors credited: Terence Parr and Sam Harwell (2013), Ivan Kochurkin (2017, upgrade to Java 8), Michał Lorek (2021 and 2022, upgrades to Java 11 and Java 17).

## Samples

`valid/` holds every `*.java` file of `java/java/examples/` except `Foo4391.java`, which grammars-v4 keeps as an expected parse error (it has a `.errors` file). `examples/performance/x/RecordExample.java` is a copy of `RecordExample.java` and is not repeated; `performance/test.sh` downloads the JDK sources and was not used.

The `examples/` directories of the sibling `java/java8`, `java/java9` and `java/java20` grammars (22 files) were also run against the port during development; all parse except their own copy of `Foo4391.java`. They are not copied here.

`invalid/` holds three hand-written inputs: a missing semicolon, an unterminated string literal, and an unclosed class body.

## Deviations

1. **Expressions.** ANTLR's single left-recursive `expression` rule (precedence by alternative order) is rewritten as precedence levels in the same order: `assignmentExpression`, `conditionalExpression`, `conditionalOrExpression`, `conditionalAndExpression`, `inclusiveOrExpression`, `exclusiveOrExpression`, `andExpression`, `equalityExpression`, `instanceofExpression`, `relationalExpression`, `shiftExpression`, `additiveExpression`, `multiplicativeExpression`, `unaryExpression`, `castExpression`, `postfixExpression` (member access, indexing, method references, `++`/`--`) and `primary`. Assignment and `?:` are right-associative, the binary operators left-associative. ANTLR also lets prefix alternatives (lambda, cast, `new`, `switch`) appear as operands at any level; here a lambda is allowed as a whole expression, as the third operand of `?:`, and as a cast operand (JLS 15.27), and `switch` expressions and `new` are primaries.
2. **Relational operators do not chain.** `relationalExpression --> shiftExpression op shiftExpression`. With chaining, `List<String> x = y;` would also be the expression statement `((List < String) > x) = y`. `a < b < c` is a type error in Java anyway.
3. **Array creation.** `new T[n]...` (ANTLR's `arrayCreatorRest` with dimension expressions) is a unary expression (`'new' arrayCreator`), so `new int[2][3]` cannot also be read as `(new int[2])[3]`. Consequently `new int[5].length` is rejected. Array creation with an initialiser stays a primary (`new String[] {"a"}[0]` parses, as in `AllInOne7.java`).
4. **Costs instead of alternative order and greedy loops** (penalties of accepted samples count these readings):
   - a cast costs 1, so `(a) - b` is a subtraction, as ANTLR picks the parenthesised primary;
   - annotations cost 0 as modifiers, 1 as interface method modifiers, 2 in front of an interface method's return type, and 3 as leading type annotations of `typeType`, so each one goes to the earliest loop that takes it, as with ANTLR's greedy loops. Interface method modifiers that `modifier` also accepts (`public`, `abstract`, `static`, `strictfp`) cost 1 for the same reason. `typeType` with leading annotations is a separate production;
   - `typeType '::' identifier` costs 1, so `Foo::bar` is an expression method reference;
   - a switch expression costs 2 and the arrow-form switch statement costs 1, so a classic `switch` statement is never also a switch expression or an arrow-form statement;
   - a switch rule outcome that is a list of block statements costs 1, so `case 1 -> { ... }` has a `block` outcome;
   - `yield` as an `identifier` costs 1, so `yield -1;` is a yield statement.
5. **Dangling else.** Statements carry a feature `open` (`yes` for an `if` without `else`, or a statement ending in one). The then-branch of an `if ... else` is `statement{open: no}`, so `else` binds to the nearest `if` as in ANTLR.
6. **`compilationunit`.** `packageDeclaration? (importDeclaration | ';')* (typeDeclaration | ';')*` lets a `;` between the imports and the first type declaration belong to either list. Here the second list starts with a `typeDeclaration`, so every such `;` belongs to the first.
7. **`classType`.** ANTLR's `((packageName '.' annotation*)? typeIdentifier typeArguments?)+ (...)*` is ambiguous for `a.b.C`; it is rewritten as `classTypePrefix '.' annotation* typeIdentifier typeArguments?`, where `classTypePrefix` is a left-recursive list of `identifier typeArguments?` segments. `packageName` is not used.
8. **`formalParameters`.** ANTLR's `(',' formalParameterList)*` (ambiguous, since `formalParameterList` is itself a list) is `(',' formalParameterList)?`. `receiverParameter`'s `(identifier '.')* THIS` is `typeType qualifiedName '.' 'this'` or `typeType 'this'`.
9. **`lambdaParameters`.** The three alternatives that accept `()` are replaced by one `'(' ')'` alternative plus the non-empty forms.
10. **`switchLabel`** includes its `:`, so the trailing `switchLabel*` of a classic switch accepts `case 1: }` (ANTLR's trailing `switchLabel*` has no `:`). Its `IDENTIFIER` alternative, covered by `expression`, is dropped. In **`switchLabeledRule`**, `'case' null` is covered by `expressionList`, so only `case null, default` keeps its own production. The statement form `switchExpression ';'?` has no optional `';'`; a following `;` is an empty statement.
11. **`pattern`** binds one identifier (`variableModifier* typeType annotation* identifier`) where ANTLR has `variableDeclarators`, which made `f(x instanceof A a, b)` ambiguous.
12. **`annotationFieldValue`.** The predicate `IsNotIdentifierAssign` is dropped; `annotationValue` uses `conditionalExpression` (the JLS element value, and the comment in the ANTLR rule) instead of `expression`, so `x = 1` is only a named element.
13. **`annotationTypeElementRest`.** The optional `';'` after nested type declarations is dropped; a following `;` is an empty element.
14. **`recordComponentList`.** The predicate `DoLastRecordComponent` (varargs only last) is dropped.
15. **Lexer.** `TEXT_BLOCK`'s lazy `(. | EscapeSequence)*?` is the equivalent greedy pattern `(?:[^"\\]|[\\][\s\S]|"(?!""))*` before the closing `"""`. `IDENTIFIER`'s letter classes are `[a-zA-Z$_]` and any non-ASCII character. `IDENTIFIER` excludes every keyword of `JavaLexer.g4` (including contextual ones and `true`, `false`, `null`) with a negative lookahead; contextual keywords are inline literals accepted by `identifier` and `typeIdentifier` as in ANTLR. `WS` is the `whitespace` statement.
16. **Unreferenced rules** (`altAnnotationQualifiedName`, `packageName`) are omitted.
