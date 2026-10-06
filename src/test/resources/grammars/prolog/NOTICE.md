# prolog

Source: grammars-v4 `prolog/prolog.g4` at commit `7df52be9`
(https://github.com/antlr/grammars-v4/tree/7df52be9/prolog).

License: BSD 3-clause, as stated in the `.g4` header.
Copyright (c) 2013, Tom Everett. All rights reserved.

`valid/example1.pl` is `examples/example1.txt` from the same directory (the
only example there), unchanged. `valid/lists.pl`, `valid/operators.pl` and
`valid/literals.pl` are hand-written to cover lists, directives, the operator
table, number forms, quoted atoms, strings and comments. The files in
`invalid/` are hand-written.

## Deviations

- **Operators.** ANTLR's left-recursive `term operator_ term` with
  `<assoc = right>` and a prefix `operator_ term` alternative becomes a
  right-recursive chain over a new `primary` nonterminal
  (`term --> primary operator_ term`, `term --> operator_ term`). As in the
  ANTLR precedence order, a prefix operator applies to the whole remaining
  term. There is still one priority level and no operator table, as in the
  source.
- **Arguments and the comma operator.** `,` is both an operator and the
  argument separator. ANTLR resolves the conflict by its greedy decision.
  Here `termlist` elements are `arg`, a term whose top-level binary operator
  is not `,` (ISO priority 999), enforced by the constraint
  `!matches(op, ',')`, so `termlist` is `arg (',' arg)*`.
- **Directive versus clause.** `:- foo.` is both a directive and a clause
  whose term uses the prefix operator `:-`; ANTLR takes the first alternative.
  Here `clause` rejects a term whose first token is `:-` with a `matches`
  constraint on the term's text.
- **Negative numbers.** The `'-'? integer` and `'-'? FLOAT` alternatives are
  dropped (the source marks them TODO); `- 1` parses as the prefix operator
  `-` applied to `1`. Keeping them made every negative number ambiguous.
- **Names and variables.** ANTLR's `SMALL_LETTER` includes `_`, and
  `LETTER_DIGIT` is declared before `VARIABLE`, so `_` and `_X` were atoms
  there. Here `LETTER_DIGIT` starts with `[a-z]` and `_`-prefixed names are
  variables.
- **Literal tokens over graphic tokens and names.** ANTLR lexes the operator
  words (`dynamic`, `is`, `mod`, ...) and graphic operators (`:-`, `=`, `.`,
  ...) as literal tokens, which win over `LETTER_DIGIT` and `GRAPHIC_TOKEN` on
  equal length. Here those two tokens exclude exactly these spellings with
  negative lookaheads.
- **Quoted tokens.** ANTLR's lazy `(...)*?` loops become greedy loops over
  the same character set; a doubled quote (`''`, `""`, ` `` `) is an escaped
  quote inside the token, as in ISO Prolog.
- **Comments.** `MULTILINE_COMMENT` nests in ANTLR and may run to end of
  input; here block comments do not nest and must be closed. `COMMENT` runs to
  end of line. Both are skip categories (ANTLR: hidden channel).
- **Remaining ambiguity.** `;` is both an atom and an operator, so `;(a)` is
  either a compound term or the prefix operator `;` applied to `(a)` (the
  checker reports `ambiguous=true`). No sample uses this.
- `EOF` is dropped (a parse always covers the whole input).
