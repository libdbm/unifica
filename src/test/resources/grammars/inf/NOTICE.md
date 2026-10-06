# NOTICE: inf

- Source: `inf/inf.g4` and `inf/examples/` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- License: BSD 3-Clause License, as stated in the `.g4` header.
- Authors: Copyright (c) 2017, Tom Everett.

## Samples

- `valid/example1.txt`, `valid/example2.txt`, `valid/example3.txt`: copied unchanged from `inf/examples/` (all of them).
- `invalid/`: hand-written (unterminated section header, key/value line before any section, `==`).

## Deviations

The parser rules are those of `inf.g4`. The grammar declares `whitespace [ \t]+;` (ANTLR's `WS -> skip`), so line ends are `EOL` tokens, and `COMMENT` is a `skip` category that, as in ANTLR, includes the line break that ends it. A comment after a list item therefore joins the next line to the list, as it does in ANTLR.

- **CHARS versus STRING ties.** ANTLR gives equal-length ties to `CHARS` (declared first). `CHARS` includes `"`, so a quoted string made only of `CHARS` characters (for example `"x"`) is a `CHARS` token in ANTLR. Unifica keeps every tied category, so `STRING` carries a lookahead requiring at least one character `CHARS` cannot match; whenever that fails, `CHARS` matches at equal or greater length anyway.
- `EOF` is dropped (S-P1).
