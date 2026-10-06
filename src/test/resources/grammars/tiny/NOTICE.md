# tiny

Source: grammars-v4 `tiny/tiny.g4` at commit `7df52be9`
(https://github.com/antlr/grammars-v4/tree/7df52be9/tiny).

License: BSD 3-clause, as stated in the `.g4` header.
Copyright (c) 2016, Tom Everett. All rights reserved.

Samples in `valid/` are `examples/example1.txt` to `examples/example3.txt`
from the same directory, unchanged. The files in `invalid/` are hand-written.

## Deviations

- **Keywords.** In ANTLR the implicit `'BEGIN'`, `'END'`, `'READ'` and
  `'WRITE'` tokens win over `ID` on equal length. Here `ID` excludes them with
  a negative lookahead.
- **op is lexical.** `op --> '+'` and `op --> '-'` have a single literal on the
  right-hand side, so `op` is a token category (S-G1). Its tree is a node `op`
  with one leaf, the same shape as the ANTLR parser rule. At a `-` the lexer
  produces both an `op` token and the anonymous `'-'` token of `integer`; the
  parser chooses.
- **Whitespace.** `WS -> skip` is the `whitespace` statement, so, as in ANTLR, a tab is an
  error.
- `EOF` is dropped (a parse always covers the whole input).
