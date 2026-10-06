# Changelog

## [2.0.0]

2.0.0 replaces the parsing pipeline and is not source compatible with 1.x. See [MIGRATION.md](MIGRATION.md) for
every removed API and its replacement, and [docs/SEMANTICS.md](docs/SEMANTICS.md) for the normative semantics.

### Changed

- Grammars are compiled once into an immutable `Compiled` form and parsed by an immutable, thread-safe `Parser`, with
  one `Session` per parse.
- Expected failures are returned as `Result<T, ErrorDetails>` values instead of thrown exceptions, across loading,
  compiling, parsing, unification and generation.
- The lexer produces a token graph that keeps every category of the longest match (S-L2), and caller tokens carry an
  explicit category (S-L6).
- `ParseResult` reports an `Outcome` (`ACCEPTED`, `REJECTED`, `LIMIT`, `CANCELLED`), a 64-bit penalty, whether the
  parse was ambiguous, and statistics. Diagnostics are off by default.
- Constraints are `Expression`s evaluated through a `Plan`, and predicates are registered in a `Predicates` registry.
  Unknown predicates and wrong arities are compile errors (S-C8).
- Soft-group weights apply once per group and only on top-level conjuncts (S-C4 to S-C6).
- Ties between equally cheap derivations are broken by production declaration order (S-P3).
- Features reach the parent only through shared variables (S-F5), and numbers compare by value (S-F4).
- `Structure` is immutable.
- `GrammarGenerator` is built with a `Policy`, and every generated sentence is parsed back before it is returned.
- Pattern caches in the `matches` builtin and `Vocabulary.byPattern` compile and filter outside the cache lock, so
  concurrent callers wait only for map operations.

### Added

- `skip` and `whitespace` declarations, top-level `|`, empty alternatives and grouped sequences.
- `@N` production costs (S-P7) and the `==> ^S` transition.
- The `at_end()` and `before(C, ...)` builtins.
- Regex `{n,m}`, lazy and possessive quantifiers, lookbehind, and character classes inside groups.
- `Limits` on states, agenda, packed derivations, graph nodes, depth, tree size, diagnostics and a deadline.
- A conformance corpus (`src/test/resources/conformance`) that checks `docs/SEMANTICS.md`.

### Documentation

- `Predicates.Check` states that a predicate exception propagates out of the parse (S-T1).
- `LoggingObserver` states that it is not thread-safe.
