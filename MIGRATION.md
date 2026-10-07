# Migrating from 1.x to 2.0.0

2.0.0 replaces the parsing pipeline. A grammar is now loaded, compiled once into an immutable `Compiled` form, and
parsed by an immutable, thread-safe `Parser`. Expected failures are returned as `Result<T, ErrorDetails>` values
rather than thrown. The grammar language is the same with additions, but several behaviours that were undefined or
inconsistent in 1.x now follow [docs/SEMANTICS.md](docs/SEMANTICS.md), and some grammars select different parses as
a result.

There are no compatibility shims: every removed API below has to be replaced.

## Removed and changed APIs

### Results instead of exceptions

`com.libdbm.ugf.Result<T, E>` is a sealed interface with `Success(value)` and `Failure(error)`, and
`com.libdbm.ugf.ErrorDetails` holds a code, a message and a list of individual problems. Use `orElseThrow()` where a
failure is a programming error, or switch over the two cases.

| 1.x                                                                                                                                                                                                                  | 2.0.0                                                                                                                                                                                                |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `UnificationGrammarParserFactory.parse(Path)`, `parse(String)`, `parseWithImports(...)`, `unvalidated(...)` returning `Grammar` and throwing `IOException`, `GrammarSyntaxException` or `GrammarValidationException` | The same methods returning `Result<Grammar, ErrorDetails>`, with codes `grammar.io`, `grammar.syntax` and `grammar.validation` (`IO`, `SYNTAX`, `VALIDATION`).                                       |
| `GrammarSyntaxException`, `GrammarValidationException`                                                                                                                                                               | Removed; see the row above.                                                                                                                                                                          |
| `com.libdbm.ugf.parser.ModuleResolver`, whose `resolve(Grammar)` threw `IOException`                                                                                                                                 | `com.libdbm.ugf.grammar.loader.ModuleResolver`; `resolve(Grammar)` and `resolve(Grammar, Path)` return `Result`, with codes `import.missing` and `import.unexported`. A resolver is not thread-safe. |

### Parsing

| 1.x                                                                                                             | 2.0.0                                                                                                                                                                          |
|-----------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ParserFactory.create(grammar)`, `create(grammar, observer)` returning `ParserFactory`                          | `ParserFactory.create(grammar)`, `create(grammar, predicates, options)` and `create(path)`, each returning `Result<Parser, ErrorDetails>`.                                     |
| `ParserFactory.parse(text)`                                                                                     | `parser.parse(text)` on the `Parser` from `create`.                                                                                                                            |
| `ParserFactory.tokenize(text)`, `lexer()`                                                                       | `parser.tokenize(text)`, which returns the token graph as `Result<Graph, ErrorDetails>`.                                                                                       |
| `ParserFactory.parser()`, `grammar()`, `enhancer()`                                                             | `Parser.compiled()`; the enhancer is part of `Options`.                                                                                                                        |
| `ChartParser`, `new ChartParser(grammar)`, `new ChartParser(context, grammar)`                                  | `Compiler.compile(grammar, predicates)` then `Parser.of(compiled)` or `Parser.of(compiled, options)`.                                                                          |
| `LexicalAnalyzer`, `LexicalAnalyzer.build(grammar, skip)`, `LexicalRule`, `LexicalContext`, `TerminalExtractor` | `com.libdbm.ugf.lexer.Lexer`, built from a `Compiled` grammar. `Parser.tokenize` uses it.                                                                                      |
| `TokenStream`, `List<List<Token>>` lattices                                                                     | `com.libdbm.ugf.lexer.Graph`, a token graph. `TokenSource.of(tokens)` and `TokenSource.alternatives(cells)` build one from caller tokens, and `parser.parse(graph)` parses it; `parser.parse(graph, input)` also takes the input text, so constraints see the input a constituent spans. |
| `TokenEnhancer.enhance(List<Token>)`                                                                            | `TokenEnhancer.enhance(Graph)`, set through `Options`.                                                                                                                         |
| `TokenTransducer`, `parser.Utilities`                                                                           | Removed; they had no remaining use.                                                                                                                                            |
| `GrammarNormalizer`, `GrammarLinter.normalize(grammar)`                                                         | Removed. The compiler lowers groups, alternation and repetition.                                                                                                               |
| `Item`                                                                                                          | Removed; it was internal.                                                                                                                                                      |
| `Token.string(key)`                                                                                             | `token.string(key, null)`.                                                                                                                                                     |
| `Token(text, features, start, end)` with the category taken from the `cat` feature                              | `Token(text, features, start, end, category)`. The four-argument constructor now gives no category, and `cat` is an ordinary feature.                                          |

### Results and trees

| 1.x                                                                                   | 2.0.0                                                                                                                                                                              |
|---------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ParseResult(tree, int penalty, diagnostics, ambiguous)`, success when `tree != null` | `ParseResult(outcome, tree, long penalty, ambiguous, diagnostics, statistics)`. `outcome()` is `ACCEPTED`, `REJECTED`, `LIMIT` or `CANCELLED`; `success()` is true for `ACCEPTED`. |
| `new ParseResult(tree, penalty)`, `new ParseResult(tree, penalty, diagnostics)`       | Removed. Results come from the parser.                                                                                                                                             |
| `ParseTree.Node(symbol, label, children, features)`                                   | `Node` also records `start` and `end`, so an empty constituent keeps its position. The four-argument constructor remains for trees without positions.                              |
| `ParseDiagnostics.ConstraintFailure.constraint()`                                     | `expression()`, the rendered expression. Predicates return true or false, so failure reasons name the predicate instead of carrying a message.                                     |
| Diagnostics always collected                                                          | Off by default. Enable them with `new Options(limits, observer, true)`.                                                                                                            |

### Observers

`ParseEvents` records carry public data only, and none is created when the observer is `ParseObserver.NOOP`.

| 1.x                                                                | 2.0.0                                                           |
|--------------------------------------------------------------------|-----------------------------------------------------------------|
| `Start(lattice, grammar, start)`                                   | `Start(graph, start)`                                           |
| `Predict(...)` with the rule and item                              | `Predict(position, symbol, production)`                         |
| `Scan(...)` with the token and item                                | `Scan(position, category, text)`                                |
| `Complete(...)` with the item and children                         | `Complete(origin, position, symbol, production, penalty)`       |
| `ConstraintEval(...)` with the rule, constraint result and `Phase` | `ConstraintEval(position, symbol, expression, passed, penalty)` |
| `Position(position, items, complete, incomplete)`                  | `Position(position, states)`                                    |

`Unification`, `End` and `Unexpected` keep their meaning. Pass the observer in `Options`.

### Constraints and predicates

| 1.x                                                                                                                                  | 2.0.0                                                                                                                                                                                                                                                                                                      |
|--------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Constraint` (`And`, `Or`, `Not`), `Predicate`, `Strength`, `priority`                                                               | `Expression` (`And`, `Or`, `Not`, `Call`, `Literal`, `Weighted`). `Plan.of(expressions)` splits a production's constraints into required expressions and `Soft` groups.                                                                                                                                    |
| `constraints.Result(passed, reason, penalty)`                                                                                        | `Verdict`: `Accepted(penalty)`, `Rejected(failed)` or `Overflow`.                                                                                                                                                                                                                                          |
| `Evaluator.eval(context, constraint)`                                                                                                | `Evaluator.evaluate(plan, environment)` and `Evaluator.truth(expression, environment)`.                                                                                                                                                                                                                    |
| `Context`, `withPredicate(...)`, `withBinding(...)`                                                                                  | `Predicates.builder().lexical().builtins().add(name, minimum, maximum, phase, check).build()`, passed to `Compiler.compile` or `ParserFactory.create`. A check is `(environment, args) -> boolean`. `Environment` is immutable; `environment.resolve(value)` resolves variables, labels and feature paths. |
| `Builtins.all()`                                                                                                                     | `Predicates.standard()`. `Builtins` is no longer public.                                                                                                                                                                                                                                                   |
| `GrammarRule(lhs, rhs, List<Constraint>)`                                                                                            | `GrammarRule(lhs, rhs, constraints, kind, cost, transition)` with `List<Expression>` constraints, plus the shorter constructors.                                                                                                                                                                           |
| `GrammarRule.of(lhs, rhs, constraints)`, `GrammarRule.LHS.fromMap(symbol, map)`, `RuleElement.Nonterminal.fromMap(name, label, map)` | `new GrammarRule(lhs, rhs, constraints)`, and `new LHS(symbol, features)` or `new Nonterminal(name, label, features)` with a `Structure` (`Utilities.fromMap(map)` converts a string map).                                                                                                                 |
| `RuleElement.Terminal(text, label, transition)`, `Regex(..., transition)`, `.transition(state)`                                      | `Terminal(text, label)` and `Regex(pattern, label, compiled)`. The transition belongs to the production: `GrammarRule.transition()`.                                                                                                                                                                       |

### Features

| 1.x                                                                                                   | 2.0.0                                                                                                                                                        |
|-------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `new Structure()`, `new Structure(map)`, `set(feature, value)`, `copy()`                              | `Structure` is immutable. Use `Structure.EMPTY`, `Structure.builder()`, `Structure.builder(base)` and `with(feature, value)`, which returns a new structure. |
| `Unifier.unify(...)` returning `Optional`, `unifyWithReason(...)`, `UnifyResult`, `UnificationResult` | `Unifier.unify(left, right, bindings)` returning `Result<Unification<T>, ErrorDetails>`, where `Unification` holds the unified value and the `Bindings`.     |
| `Unifier.substitute(structure, map)`                                                                  | `Unifier.substitute(value, bindings)`.                                                                                                                       |
| `NumericConstant(number, floating)`                                                                   | `NumericConstant` compares by mathematical value, so `1` equals `1.0`.                                                                                       |

### Generation

| 1.x                                                         | 2.0.0                                                                                              |
|-------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| `new GrammarGenerator(grammar, terminals)`                  | `GrammarGenerator.builder(grammar)...build()`, returning `Result<GrammarGenerator, ErrorDetails>`. |
| `generate(start, features, count)` returning `List<String>` | Returns `Result<List<String>, ErrorDetails>`; a failure explains why nothing could be generated.   |
| `generateOne(start, features)` returning `Optional<String>` | Returns `Result<String, ErrorDetails>`.                                                            |
| Fixed depth, attempts and spacing                           | `Policy` (depth, attempts, repetitions, length, steps, `Joiner`), set with `builder.policy(...)`.  |

Every generated sentence is now parsed back before it is returned, so sentences that 1.x produced in violation of
agreement or constraints are no longer produced.

## Grammar behaviour changes

### Longest match keeps every category of that length (S-L2)

```
Keyword --> 'if';
Name --> [a-z]+;
```

For the input `if`, 1.x kept only one token, and if any matching category had constraints it removed the
unconstrained ones. 2.0.0 keeps both `Keyword` and `Name` as alternative edges in the token graph and lets the parser
choose. Grammars that relied on a category being removed should say so in the syntax, for example with a negative
lookahead in the regex:

```
Name --> (?!if\b)[a-z]+;
```

### Weights (S-C4 to S-C6)

```
S --> A:a B:b where (p(a), q(b)):3, r(a):4;
```

A false soft group adds its weight once, however many of its parts are false, and separate groups add up: this
production costs 0, 3, 4 or 7. 1.x could lose the weight of a nested group that passed while carrying a penalty.

A weight may only be attached to a top-level conjunct. In 1.x the weight in `!p(a):5` had no effect; in 2.0.0 it is a
compile error, as is `p(a):3 | q(b)`. Write the weight on the whole expression instead: `(!p(a)):5`. A weight inside
a weighted group, as in `(p(a):3 | q(b)):5`, is ignored and the linter warns about it.

### Unknown predicates are compile errors (S-C8)

In 1.x a predicate that was not registered was evaluated as false at parse time. In 2.0.0 the grammar fails to
compile, and so does a call with the wrong number of arguments. A predicate that was used only to charge a penalty,
such as `bound(x):1` where `bound` is never registered, should become a production cost:

```
statement --> assertion dot @1;
```

### Production costs (S-P7)

A production may end with `@N`, which adds `N` to the penalty every time the production is used.

### Ties are broken by declaration order (S-P3)

When several derivations have the same lowest penalty, 1.x returned whichever it found first. 2.0.0 compares the
productions of the candidate trees in pre-order and prefers the one whose first differing production was declared
earlier. To prefer a reading, declare its production first or give the other production a cost.
`ParseResult.ambiguous()` reports whether another derivation tied.

### Sequences of plain literals are syntactic (S-G1)

```
EndIf --> 'end' 'if';
```

This production matches two tokens, so it accepts both `end if` and `endif`. A production is lexical, and matches one
token, only if its right-hand side is a single literal, or contains a regex, a state annotation or a transition, and
contains no nonterminal. To require `endif` as one token, write `EndIf --> 'endif';`.

### Features are passed to the parent only through variables (S-F5)

```
S --> NP{num: N} VP{num: N};
```

The constituent `S` has the features written on its left-hand side and nothing else. In 1.x, values a child supplied
for features written on its right-hand-side element were merged into the parent. To pass a feature up, share a
variable:

```
S{num: N} --> NP{num: N} VP{num: N};
```

Each use of a production has its own variables, and variables bound on the right-hand side can be used as constraint
arguments, as in `where equals(N, M)`.

### Regular expressions use a portable subset (S-L7)

Grammar regexes, `whitespace` patterns and `matches` patterns must lie in the subset that S-L7 defines, or the grammar
is invalid; a `matches` pattern computed at runtime outside it is false. Backreferences, named groups, possessive
quantifiers, atomic groups, nested classes, `\Q...\E`, Java-only escapes and properties (`\h`, `\R`, `\p{Alpha}`),
and flags other than a leading `(?i)` or `(?s)` are rejected. Two meanings change: `$` is the end of the text only, not
also the position before a final line terminator, and `(?i)` folds Unicode case rather than only ASCII.

### Labels work with every builtin (S-C9)

String builtins such as `starts_with` and feature builtins such as `has_feature` accept labels for terminals and
nonterminals. A label's text is the input it covers. In 1.x they failed for labels. `equals(label, 'x')` and
`not_equals` compare a label with a string by that text; two labels still compare by value.

### Lexical predicates are evaluated in syntactic productions (S-C7)

In 1.x, `in_state`, `state_depth` and `state_contains` always held in a syntactic production, and `at_char_start` and
`at_char_position` were removed from its constraints. They are now evaluated with the state stack of the node where the
constituent starts and the offset where its first token starts, after skipped whitespace. A constituent with no tokens
uses the offset of its node.

### Positional predicates count tokens (S-C7)

`at_start()` and `at_position(n)` hold when the constituent starts after 0 or `n` tokens on some path through the token
graph. They no longer read token graph node ids, which are implementation-defined (S-L1). In a lexical production they
read the character offset where the token starts.

### Numbers compare by value (S-F4)

`1` and `1.0` are equal in unification and in every builtin.

### Penalties are 64-bit (S-C10)

Penalties are `long`. A sum that would overflow ends the parse with outcome `LIMIT`.

### Caller tokens carry an explicit category (S-L6)

A `Token` fills a grammar symbol whose name equals the token's category, and the token's features unify with the
features written on that symbol. In 1.x a caller token's category was visible only through its `cat` feature, as in
`{TOKEN}:w where equals(w.cat, 'NN')`; write `NN` instead.

### Transitions belong to the production

`String --> '"' ==> STRING;` behaves as before. The transition is stored on the production, and `==> ^S`, which
replaces the top of the state stack, is new.

## New grammar features

- `skip A, B;` discards tokens of the named lexical categories between other tokens.
- `whitespace <pattern>;` and `whitespace none;` replace the fixed `\s+`.
- Top-level `|` (`A --> B | C;`), empty alternatives (`A --> B | ;`) and groups holding sequences (`(B C | D)`).
- Regex `{n,m}` quantifiers, lazy and possessive quantifiers, lookbehind, and character classes inside groups.
- `@N` production costs and the `==> ^S` transition.
- The builtins `at_end()` (the constituent ends at the end of the input) and `before(C, ...)` (a token of category `C`
  follows), which express ANTLR's `EOF` and one-token lookahead inside rules.
