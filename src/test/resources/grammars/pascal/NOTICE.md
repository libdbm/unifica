# Pascal grammar port

## Source

- grammars-v4, commit `7df52be9`: `pascal/pascal.g4`
- Sample inputs in `valid/`: `pascal/examples/*.pas` (all 16 files, copied unchanged)

## License

BSD 3-clause license, as stated in the `pascal.g4` header:
Copyright (c) 2013, Tom Everett. All rights reserved.

## Authors

- Tom Everett (ANTLR 4 grammar)
- Adapted from `pascal.g` by Hakki Dogusan, Piet Schoutteten and Marton Papp

## Deviations

- **Case-insensitive keywords.** ANTLR's `caseInsensitive = true` option has no
  counterpart. Every keyword is a lexical category written with per-letter case
  classes (`BEGIN --> [Bb][Ee][Gg][Ii][Nn]`), and `IDENT` excludes all keywords
  with a negative lookahead, so a keyword never ties with `IDENT` under maximal
  munch. `IDENT`, the exponent marker of `NUM_REAL` and the rest are likewise
  written to accept either case.
- **Dangling else.** ANTLR resolves `if a then if b then x else y` greedily. An
  Earley parser reports both derivations, so the statement rules are split: the
  then-branch of an if-then-else is a `closedStatement`, which cannot end in an
  if-then without an else. `closedStatement`, `closedUnlabelledStatement`,
  `closedIfStatement`, `closedWhileStatement`, `closedForStatement` and
  `closedWithStatement` are new. The else binds to the nearest if, as in ANTLR.
- **Expression associativity.** ANTLR's `expression`, `simpleExpression` and
  `term` are right recursive, so `a - b - c` groups as `a - (b - c)`. They are
  rewritten as left-recursive levels with the same language and precedence, so
  trees reflect Pascal's left associativity.
- **Operator alternatives.** `relationaloperator`, `additiveoperator`,
  `multiplicativeoperator` and the built-in part of `typeIdentifier` use one
  parenthesised alternation, which is spliced out of trees.
- **NUM_REAL.** In ANTLR, `NUM_REAL` also matches a bare digit string (its
  fraction is optional) and loses to `NUM_INT` by rule order. Here equal-length
  matches are both kept, so `NUM_REAL` requires a fraction or an exponent.
- **Unused tokens.** `LCURLY` and `RCURLY` are not used by the parser and would
  only compete with `COMMENT_2`; they are omitted.
- **Comments.** `COMMENT_1` (`(* ... *)`) and `COMMENT_2` (`{ ... }`) are skip
  categories. The lazy `.*?` bodies are written as regexes that stop at the
  first closing delimiter.
- **EOF** is implicit: a parse must cover the whole input.
