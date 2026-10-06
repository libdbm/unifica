# Erlang grammar: provenance

- Source: `erlang/Erlang.g4` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4), commit `7df52be9`.
- Samples: `valid/fac.P`, `valid/getty.P`, `valid/helloword.P`, `valid/map.P` are copied unchanged from `erlang/examples/` at the same commit. `valid/features.erl` and everything under `invalid/` are hand-written for this port.
- License: BSD licence, as stated in the `.g4` header ("Copyright (c) 2013 Terence Parr. All rights reserved."), with its three conditions and warranty disclaimer.
- Authors: Terence Parr (copyright holder); the grammar is credited to Pierre Fenoll ("An ANTLR4 Grammar of Erlang R16B01 made by Pierre Fenoll", derived from OTP's `erl_parse.yrl`, updated to Erlang/OTP 23.3).

## Deviations

The parser rules are those of `Erlang.g4`, including its flat operator chains (`expr150 ('orelse' expr160)*`) and nullable rules (`clauseGuard`, `optBitSizeExpr`, `optBitTypeList`, `tryOptStackTrace`), except:

- **`attrVal`.** The ANTLR alternative `'(' expr ')'` is dropped. `expr` already derives a parenthesised expression, so `-module(m).` had two derivations. `erl_parse.yrl` does not have this alternative either.
- **`tryClause`.** This follows `erl_parse.yrl`: `patExpr clauseGuard clauseBody | atomOrVar ':' patExpr tryOptStackTrace clauseGuard clauseBody`. The ANTLR alternative `expr clauseGuard clauseBody` is dropped because every pattern is also an expression. A stack-trace variable now requires an exception class, because `throw:Reason` could be read either as class `throw` with pattern `Reason` or as pattern `throw` with stack trace `Reason`. With this change, an expression that is not a pattern can no longer head a `catch` clause.
- **`EOF`** is dropped from `forms`. The parse must cover the whole input anyway (S-P1).

Lexer:

- **Fragments** (`DIGIT`, `LOWERCASE`, `UPPERCASE`) are inlined into the token regexes, including the Latin-1 ranges `À`-`Þ` and `ß`-`ÿ`.
- **Keywords.** ANTLR gives the implicit literal tokens (`end`, `fun`, `when`, `and`, ...) priority over `TokAtom`. Under maximal munch every category matching the same length is an alternative (S-L2). So the unquoted `TokAtom` regex excludes the 25 reserved words with a negative lookahead. Quoted atoms such as `'end'` are unaffected.
- **`TokAtom`** is two productions (unquoted and quoted) with the same category, compared with one rule with two alternatives in ANTLR.
- **`TokChar`.** The three-digit octal escape is tried before the single-character form, because Java regex alternation is leftmost-first and ANTLR's is longest-match.
- **Strings and quoted atoms.** ANTLR `~'\\'` after a backslash (any character, including a newline) is written `[\s\S]`.
- **`WS`** is the `whitespace` statement, with the ANTLR character set.
- **`Comment`** keeps the ANTLR definition, which requires a terminating newline. A `%` comment on the last line without a final newline is therefore an error, as in the ANTLR grammar.

## Samples

All four grammars-v4 examples are included and parse unambiguously with penalty 0. `valid/features.erl` adds records, type specs, binaries with bit syntax, list and binary comprehensions, `try ... of ... catch ... after`, `receive ... after`, `if`, `case`, named and external funs, and `catch` in parentheses.
