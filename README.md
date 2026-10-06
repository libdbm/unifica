# Unifica

An Earley chart parser for unification grammars with weighted constraints, for Java 21+.

## Overview

Unifica parses text against context-free grammars whose symbols carry feature structures. Productions can state
required constraints, which reject a derivation, and soft constraints, which add a penalty. When the input is
ambiguous, the parser returns the derivation with the lowest total penalty and reports whether others tied with it.

- **Earley parsing** over a token graph. Left recursion, empty productions and arbitrary context-free grammars work
  unchanged, and ambiguity is packed, so even highly ambiguous grammars parse in polynomial time.
- **Feature structures and variables.** Features on symbols unify, and variables shared within a production express
  agreement. Unification includes the occurs check.
- **Required and soft constraints.** Constraints are Boolean expressions over labelled constituents and variables. A
  soft constraint carries an integer weight, and each production can declare a cost.
- **Grammar-driven lexer.** Lexical productions in the same grammar define the tokens. The lexer takes the longest
  match, keeps every category of that length as an alternative, and supports lexical states. Tokens can also come
  from an external tagger.
- **Generation.** The generator produces sentences for a symbol and requested features, and every sentence it returns
  parses back to that symbol with those features.

The rules every implementation follows are in [docs/SEMANTICS.md](docs/SEMANTICS.md). Upgrading from 1.x is covered
in [MIGRATION.md](MIGRATION.md).

## Installation

### Maven

```xml
<dependency>
    <groupId>com.libdbm</groupId>
    <artifactId>unifica</artifactId>
    <version>2.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.libdbm:unifica:2.0.0'
```

## Quick start

A grammar file, `arithmetic.ug`:

<!-- example: arithmetic.ug -->
```
# Arithmetic with the usual precedence. Left recursion makes + - * / left associative.
start Expr;

Expr --> Expr ('+' | '-') Term | Term;
Term --> Term ('*' | '/') Factor | Factor;
Factor --> Number | '(' Expr ')';

Number --> [0-9]+ (?:[.][0-9]+)?;
```

Load it, compile it and parse, given `Path grammar = Path.of("arithmetic.ug")`:

<!-- example: quickstart -->
```java
final var parser = ParserFactory.create(grammar).orElseThrow();
final var result = parser.parse("2 * (3 + 4)");

if (result.success()) {
  render(result.tree());
}
```

`ParserFactory.create` returns a `Result`, which is either a `Success` holding the parser or a `Failure` holding
`ErrorDetails` (a code, a message and the individual problems). Grammars with syntax errors, unknown predicates or
undefined symbols fail here, before any input is parsed.

## Grammar syntax

### Statements

| Statement | Meaning |
|---|---|
| `start S;` | The start symbol. |
| `A --> ...;` | A production. |
| `skip A, B;` | Discard tokens of lexical categories `A` and `B` between other tokens (comments, for example). |
| `whitespace [ \t]+;` | What counts as whitespace between tokens. The default is `\s+`. `whitespace none;` disables it. |
| `module a.b;` | Name this grammar as a module. |
| `import a.b;`, `import a.b {X, Y};`, `import a.b.*;`, `import "file.ug";` | Import productions from another grammar. |
| `export X, Y;`, `export *;` | Symbols other grammars may import. |

Comments start with `#` and run to the end of the line.

### Productions

```
A --> B C;                 # a sequence
A --> B | C D | ;          # three productions: B, C D, and the empty production
A --> B (C | D E)? F*;     # groups, alternation inside a group, and ? * + repetition
A --> 'if' Expr 'then';    # quoted literals
Id --> [a-z_][a-z0-9_]*;   # a regular expression, written without delimiters
```

Literals use single or double quotes, with backslash escapes. Regular expressions are written inline using Java regex
syntax: character classes, `(?:...)` groups, lookahead and lookbehind, `.`, and quantifiers including `{n,m}`. An
element can be labelled with `:name` so that constraints can refer to it. `{TOKEN}` matches any single token.

### Lexical and syntactic productions

Every production is classified once, when the grammar is loaded:

- A production is **lexical** if its right-hand side is a single literal, or contains a regex, a state annotation or
  a state transition, and contains no nonterminal. A lexical production matches one token, whose category is the
  production's left-hand side, and its parts are never visible separately.
- Every other production is **syntactic**, including a sequence of plain literals such as `'end' 'if'`, which matches
  two tokens.
- A literal or regex written inside a syntactic production defines an anonymous token category for exactly that text
  or pattern.

At each position the lexer finds the longest match among the lexical productions that apply. Every category that
matches with that length becomes an alternative edge in the token graph, so `if` can be both a keyword and an
identifier, and the parser decides. Whitespace and `skip` categories are discarded between tokens. A character that
no production matches becomes a one-character `error` token, which only a production naming `error` can accept.

### Features and variables

<!-- example: agreement.ug -->
```
# Subject and verb agree in number through the shared variable N.
start S;

S{num: N} --> NP{num: N} VP{num: N};
NP{num: N} --> Det{num: N} Noun{num: N};
VP{num: N} --> Verb{num: N};

Det{num: sg} --> 'a';
Det --> 'the';
Noun{num: sg} --> 'dog';
Noun{num: pl} --> 'dogs';
Verb{num: sg} --> 'barks';
Verb{num: pl} --> 'bark';
```

Features are written in braces after a symbol. A value that starts with an uppercase letter is a variable. Each use
of a production has its own variables: the features written on a right-hand side element must unify with the
features of the constituent that fills it, and the resulting bindings hold for the rest of that production, including
its constraints. Features are never copied implicitly from a child to its parent; a parent sees a child's features
only through shared variables, as `N` above.

### Constraints and costs

<!-- example: greeting.ug -->
```
# The name must be capitalized (required). A name that does not end in a
# vowel costs 2 (a soft group). Leaving out the comma costs 1 (a production cost).
start Greeting;

Greeting --> 'hello' ',' Name:n where is_capitalized(n), matches(n, '.*[aeiou]'):2;
Greeting --> 'hello' Name:n where is_capitalized(n) @1;

Name --> [A-Za-z]+;
```

A `where` clause holds a Boolean expression. `,` is and, `|` is or, `!` is not, and parentheses group. Arguments are
quoted strings, variables, labels and feature paths such as `subject.num`. A label stands for its constituent: string
predicates use the constituent's text, and feature predicates use its features.

An unweighted top-level conjunct is **required**: if it is false, the derivation is rejected. A weighted one, written
`pred(...):w` or `(...):w`, is a **soft group**: if it is false, its weight is added to the derivation's penalty.

| Constraint | Meaning |
|---|---|
| `p(a)` | Required. |
| `p(a):3` | A soft group: adds 3 when false. |
| `(p(a), q(b)):3` | One soft group: adds 3 once, however many of its parts are false. |
| `p(a):3, q(b):4` | Two soft groups: adds 3, 4 or 7. |
| `r(c), (p(a):3, q(b):4)` | An unweighted group is flattened: one required expression and two soft groups. |
| `(p(a):3 \| q(b)):5` | A weight inside a soft group is ignored (the linter warns): adds 5 when false. |
| `p(a):3 \| q(b)`, `!p(a):3` | A compile error: a soft group must be a top-level conjunct. |

A production can also declare a cost, written `@N` at its end, which is added every time the production is used. A
cost says "prefer the other readings" without inventing a predicate that is always false.

The total penalty of a derivation is the sum of its edge costs, production costs and false soft groups, as a 64-bit
integer. The parser returns the derivation with the lowest total. Ties are broken by declaration order: the derivation
whose first differing production was declared earlier wins, so reordering productions changes only which of the
equally good readings is chosen. `ParseResult.ambiguous()` is true when more than one derivation reached the lowest
penalty.

### Lexical states

<!-- example: states.ug -->
```
# Words, and code spans in backquotes. Inside a code span the lexer is in
# state CODE, where any run of non-space characters is one Token.
# Comments run from '#' to the end of the line and are skipped.
start Text;
skip Comment;

Text --> (Word | Code)*;
Code --> Open Token* Close;

Word --> [a-z]+;
Open --> '`' ==> CODE;
Token --> {CODE} [^`\s]+;
Close --> {CODE} '`' ==> _;
Comment --> '#' [^\n]*;
```

A lexical production can require a state with `{STATE}` and change the state stack after it matches: `==> S` pushes
`S`, `==> _` pops, `==> !S` resets the stack to `S`, and `==> ^S` replaces the top with `S`. A lexical production with
no state annotation applies in every state. Lexical predicates such as `in_state(S)` can also test the state stack.

## Parsing in steps

`ParserFactory` combines three steps that can also be called separately: load the grammar, compile it against a
predicate registry, and create a parser with options.

<!-- example: pipeline -->
```java
final var loaded = UnificationGrammarParserFactory.parseWithImports(grammar);
final var compiled = loaded.flatMap(source -> Compiler.compile(source, Predicates.standard()));

switch (compiled) {
  case Result.Success<Compiled, ErrorDetails>(var value) -> {
    final var options = new Options(Limits.DEFAULT.states(100_000), ParseObserver.NOOP, true);
    final var result = Parser.of(value, options).parse("the dogs bark");
    switch (result.outcome()) {
      case ACCEPTED -> render(result.tree());
      case REJECTED -> report(result.diagnostics());
      case LIMIT, CANCELLED -> retry();
    }
  }
  case Result.Failure<Compiled, ErrorDetails>(var error) -> report(error);
}
```

A parse ends with one of four outcomes:

| Outcome | Meaning |
|---|---|
| `ACCEPTED` | A derivation covers the input. `tree()`, `penalty()` and `ambiguous()` describe the best one. |
| `REJECTED` | No derivation covers the input. With diagnostics enabled, `diagnostics()` lists the furthest position reached, what was expected there, and the constraints that failed. |
| `LIMIT` | A limit in `Limits` was reached, a penalty overflowed 64 bits, or the input could not be lexed. `stop()` names the resource, the amount used and the limit. |
| `CANCELLED` | The parsing thread was interrupted; `stop()` says so. |

In the tree, a syntactic constituent is a `ParseTree.Node` with its symbol, label, features and children. A token of
a named category is a node with one `ParseTree.Leaf` child holding the text and offsets, and a token of an anonymous
category is a leaf. Productions introduced for groups and repetition do not appear; their children are spliced into
the parent.

## Trust and limits

A grammar is trusted code. Its regexes run on Java's regex engine, its imports read files from the grammar's directory
and the resolver's search paths, and its predicates run with the caller's privileges; none of them is sandboxed. Load
grammars only from sources you would accept code from.

Input text and caller token graphs are untrusted. A caller's `Graph` is validated when it is built (node ids, forward
edges, non-negative costs), `TokenSource.linear` refuses overlapping or misordered tokens, and every parse runs within
its `Limits`.

`Limits` bounds every stage of a parse: token graph nodes and lexical state depth while lexing, chart states
(including replacements), agenda entries and packed derivations while parsing, the size of the selected tree, the
diagnostic records kept, and the total time. Default options use `Limits.DEFAULT`, which leaves wide headroom over every
sample grammar in this repository (ten million chart states, one million graph nodes, one minute); a parse that reaches
a limit ends with `LIMIT` and a `stop()` that names it. Adjust one limit with, for example,
`Limits.DEFAULT.states(100_000)`, or opt out with `Limits.NONE`, where zero means unlimited. `result.statistics()`
reports the graph size, chart states, unifications and predicate calls of the parse.

A `Compiled` grammar and a `Parser` are immutable and can be shared between threads. Each `parse` call works on its
own state, but the callbacks in its `Options` and `Predicates` are shared: custom predicates, the `TokenEnhancer` and
the `ParseObserver` must be safe to call from several threads at once if the parser is. The built-in predicates,
`ParseObserver.NOOP` and the default enhancer are; `LoggingObserver` keeps its events in an unsynchronized list, so use
one per thread. A `GrammarGenerator` draws from a single `Random` and is not thread-safe.

The parser does not catch exceptions from callbacks: an exception thrown by a predicate, the enhancer or the observer
ends the parse and propagates out of `parse`. A callback that blocks holds the parse until it returns, because the
deadline and interruption are checked only between steps of the parser.

## Custom predicates

Predicates live in a `Predicates` registry. A predicate has a name, an arity range, a phase and a check. The check
receives the environment, which resolves variables, labels and feature paths, and returns true or false.

<!-- example: predicates -->
```java
final var predicates =
    Predicates.builder()
        .lexical()
        .builtins()
        .add(
            "known",
            1,
            1,
            Predicates.Phase.SYNTACTIC,
            (environment, args) -> {
              final var value = environment.resolve(args.getFirst());
              return value != null && names.contains(value.text());
            })
        .build();
final var grammar =
    UnificationGrammarParserFactory.parse(
        """
    start S;
    S --> 'hello' Name:n where known(n);
    Name --> [A-Z][a-z]+;
    """);
final var parser =
    grammar.flatMap(source -> ParserFactory.create(source, predicates, Options.DEFAULT));
```

Syntactic predicates are evaluated when a constituent completes, and must be pure functions of their arguments,
because the parser caches their results within a parse. Positional predicates also read where the constituent starts
and ends and what follows it (`Environment.POSITION`, `Environment.END`, `Environment.NEXT`), and lexical predicates read the lexer's state stack and character
position; neither is cached. A grammar that uses a predicate the registry does not contain, or calls it with the wrong number of arguments,
fails to compile.

## Tokens from outside

The lexer produces a token graph, and a parser can also parse a graph supplied by the caller, such as the output of
a part-of-speech tagger. Given a parser for this grammar:

<!-- example: tagged.ug -->
```
# Noun and Verb are filled by an external tagger. Their patterns are used
# only when the parser lexes text itself.
start S;

S --> Noun{num: N} Verb{num: N};

Noun --> [a-z]+;
Verb --> [a-z]+;
```

each position can carry several tagged alternatives, and the token's features unify with the features in the grammar:

<!-- example: tokens -->
```java
final var plural = Structure.builder().with("num", "pl").build();
final var tokens =
    List.of(
        List.of(new Token("dogs", plural, 0, 4, "Noun")),
        List.of(
            new Token("bark", plural, 5, 9, "Verb"), new Token("bark", plural, 5, 9, "Noun")));

final var graph = TokenSource.alternatives(tokens).tokenize("dogs bark").orElseThrow();
final var result = parser.parse(graph);
```

`TokenSource.of(tokens)` builds a graph from a plain list. Tokens that overlap or are out of order, and alternatives with different spans, make `tokenize` return a `Failure`; a hand-built `Graph` or `Edge` that breaks the graph invariants (negative cost, an edge that does not lead to a later node) is refused by its constructor. `Parser.tokenize(input)` returns the graph the parser's
own lexer produces.

## Generation

<!-- example: generation -->
```java
final var generator =
    GrammarGenerator.builder(grammar)
        .random(new Random(42))
        .policy(Policy.DEFAULT.depth(10))
        .build()
        .orElseThrow();

final var plural = Structure.builder().with("num", "pl").build();
final var sentences = generator.generate("S", plural, 5);
```

`generate(symbol, features, count)` returns up to `count` sentences, or a `Failure` explaining why none could be
produced. Before a sentence is returned it is parsed back with the same grammar, and it is kept only if the parse is
accepted and the root features unify with the requested ones, so required constraints, agreement and lexing all hold.
`batch(symbol, features, count)` returns a `Generation` with the sentences produced, the number requested, and how
many times each failure reason occurred. The verifying parse uses the builder's `limits` (`Limits.DEFAULT` unless
set); a sentence whose parse reaches a limit is not returned, and the failure names the limit.

`Policy` sets the expansion depth, the attempts per sentence, how many times a repetition repeats, the output length
and step limits, and the `Joiner` that puts tokens together (`Joiner.SPACED` follows normal punctuation spacing).
Literal terminals generate themselves. Regex terminals use a registered `TerminalGenerator` or a `Vocabulary`, chosen
by the builder's `terminal` and `vocabulary` methods.

## Built-in predicates

<!-- builtins -->
| Predicate | True when |
|---|---|
| `equals(a, b)` | `a` and `b` are equal values. |
| `not_equals(a, b)` | `a` and `b` are not equal, or `a` is unbound. |
| `lt(a, b)` | `a` < `b` as numbers. |
| `le(a, b)` | `a` <= `b` as numbers. |
| `gt(a, b)` | `a` > `b` as numbers. |
| `ge(a, b)` | `a` >= `b` as numbers. |
| `starts_with(s, prefix)` | The text of `s` starts with `prefix`. |
| `ends_with(s, suffix)` | The text of `s` ends with `suffix`. |
| `contains(s, part)` | The text of `s` contains `part`. |
| `matches(s, regex)` | The whole text of `s` matches `regex`. |
| `not_empty(s)` | The text of `s` is not empty. |
| `is_upper(s)` | The text of `s` is not empty and all upper case. |
| `is_lower(s)` | The text of `s` is not empty and all lower case. |
| `is_capitalized(s)` | The text of `s` starts with an upper case letter. |
| `is_string(v)` | `v` is a string. |
| `is_number(v)` | `v` is a number. |
| `is_structure(v)` | `v` is a feature structure. |
| `is_bound(v)` | `v` is a constant, or a variable with a binding. |
| `agree(a, b)` | The features of `a` and `b` unify. |
| `unify(a, b)` | The same as `agree`. |
| `has_feature(s, name)` | The features of `s` include `name`. |
| `get_feature(s, name, value)` | Feature `name` of `s` equals `value`. |
| `feature_eq(s, name, t, other)` | Feature `name` of `s` equals feature `other` of `t`. |
| `at_start()` | The constituent starts at the first node of the token graph. |
| `at_position(n)` | The constituent starts at token graph node `n`. |
| `at_end()` | The constituent ends at the end of the input. |
| `before(C, ...)` | A token of one of the categories `C` follows the constituent. |
| `in_state(S, ...)` | The current lexical state is one of the arguments. Lexical. |
| `state_depth(n)` | The lexical state stack holds at most `n` states. Lexical. |
| `state_contains(S, ...)` | The lexical state stack contains one of the arguments. Lexical. |
| `at_char_start()` | Matching starts at character 0. Lexical. |
| `at_char_position(n)` | Matching starts at character `n`. Lexical. |
<!-- /builtins -->

## Debugging

`Options` takes a `ParseObserver`, which receives an event for each prediction, scan, completion, unification and
constraint evaluation. `LoggingObserver` collects them and logs them through SLF4J. `GrammarLinter` reports
unreachable and undefined symbols, unbound labels, misplaced state transitions and ignored weights, and suggests a
production cost where a weight is attached to a predicate that is not registered.

## Building from source

```bash
git clone https://github.com/libdbm/unifica.git
cd unifica
mvn clean verify
```

`mvn clean test` also writes a coverage report to `target/site/jacoco/index.html`. Building needs Java 21 or later
and Maven 3.8 or later.

## License

BSD 3-Clause License. See [LICENSE](LICENSE) for details.
