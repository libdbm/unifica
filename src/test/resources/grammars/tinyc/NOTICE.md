# tinyc

Source: grammars-v4 `tinyc/tinyc.g4` at commit `7df52be9`
(https://github.com/antlr/grammars-v4/tree/7df52be9/tinyc), describing the
language of Marc Feeley's `tinyc.c`.

License: BSD 3-clause, as stated in the `.g4` header.
Copyright (c) 2013, Tom Everett. All rights reserved.

Samples in `valid/` are `examples/example1.c` to `examples/example5.c` from the
same directory, unchanged. The files in `invalid/` are hand-written.

## Deviations

- **Dangling else.** ANTLR binds an `else` to the nearest `if` by its greedy
  decision. An Earley parser sees both readings, so `statement` is split into
  `matched` (every `if` has an `else`) and `open` forms. Trees gain a
  `matched` or `open` node under each `statement`.
- **Keywords.** In ANTLR the implicit `'if'`, `'else'`, `'while'` and `'do'`
  tokens win over `STRING` on equal length. Here `STRING` excludes them with a
  negative lookahead.
- **Whitespace.** `WS -> skip` is the `whitespace` statement.
- `EOF` is dropped (a parse always covers the whole input).
