# Turtle grammar port

- Source: `turtle/TURTLE.g4` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- Samples: `turtle/examples/iriref_test.ttl` and `turtle/examples/Vehicle.ttl` from the same commit. `valid/handwritten_literals.ttl` is hand-written.
- License: BSD licence (3-clause), as stated in the `.g4` header. Copyright (c) 2014, Alejandro Medrano (Universidad Politecnica de Madrid). All rights reserved.
- Authors: Alejandro Medrano. Derived from http://www.w3.org/TR/turtle/#sec-grammar-grammar.

## Deviations

- `EOF` is dropped: a parse must cover the whole input anyway (S-P1).
- Every helper lexer rule (`PN_PREFIX`, `PN_CHARS_BASE`, `PN_CHARS_U`, `PN_CHARS`, `PN_LOCAL`, `PLX`, `PERCENT`, `HEX`, `PN_LOCAL_ESC`, `INTEGER`, `DECIMAL`, `DOUBLE`, `EXPONENT`, `STRING_LITERAL_*`, `UCHAR`, `ECHAR`, `ANON_WS`, `ANON`, `BLANK_NODE_LABEL`) is inlined into the regex of the token category that uses it. In ANTLR these are not fragments, so they are token types of their own that the parser never accepts.
- `NumericLiteral` is one regex with the `DOUBLE`, `DECIMAL` and `INTEGER` alternatives ordered longest first, so `1.` before a statement dot is the integer `1` followed by `.`.
- `PrefixedName` is a syntactic production over the tokens `PNAME_LN` and `PNAME_NS`. In ANTLR, `PNAME_NS` is declared before `PrefixedName` and wins the tie on a bare prefix such as `ex:`, so a bare prefixed name used as an IRI is a syntax error there. Here it is accepted, as the W3C grammar allows.
- String literals follow the W3C definitions: all four forms accept `ECHAR` and `UCHAR` escapes. ANTLR's `STRING_LITERAL_QUOTE` accepts only `\"`, and its `STRING_LITERAL_LONG_SINGLE_QUOTE` uses `[^'\\]`, which in ANTLR is the set {`^`, `'`, `\`}, not a negation.
- ANTLR's `'@prefix'` and `'@base'` literals win their ties against `LANGTAG` by rule order. Here `LANGTAG` excludes them with a negative lookahead.
- `LC` (`-> channel(HIDDEN)`) is a `skip` category, and `WS` is the `whitespace` statement.
- Examples excluded: `schema.org.ttl` (367 KB, over the size limit). It does parse with this grammar (accepted, unambiguous, about 2 s including JVM start). The examples do not use long strings, collections, blank nodes, booleans, numbers or SPARQL-style directives, so `valid/handwritten_literals.ttl` covers them.
