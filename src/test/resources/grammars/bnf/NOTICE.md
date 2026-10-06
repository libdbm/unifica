# BNF grammar port

- Source: `bnf/bnfLexer.g4`, `bnf/bnfParser.g4` and the predicate `NotNL` in `bnf/Java/bnfParserBase.java` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- Samples: every file under `bnf/examples/` from the same commit (subdirectory names become a prefix, for example `harvard.edu-pemdas.bnf`).
- License: MIT License, as stated in the `bnfParser.g4` header.
- Authors: Ken Domino (October 2023).

## Deviations

- Whitespace and newlines are discarded. The ANTLR grammar has `WS` and `NL` tokens and a newline between productions (`prods : prod (WS? NL prod)*`), but `xelem`, `assign` and `or` all start with `ws?`, which includes `NL`, so a production may continue over several lines and only ANTLR's adaptive prediction decides where it ends. Here a production ends where the next `lhs assign` begins, which is unambiguous. This accepts some inputs ANTLR rejects, such as two productions on one line (`aa ::= b cc ::= d`).
- The parser-level elements `'"' .*? '"'`, `'\'' .*? '\''` and `'<' .*? '>'` are the single tokens `DQUOTED`, `SQUOTED` and `NAME`, because the whitespace inside them (for example `" "` or `<basic symbol>`) must be kept. Like the non-greedy ANTLR rules, each runs to the first closing character and may span newlines.
- ANTLR's fallback `TEXT : .` and `xelem : ... | .` can consume any token. Here `TEXT` is any single non-whitespace character except `|`, U+2223 and U+2192, which are always operators, and an element is never an assign or or token. ANTLR's README calls its grammar ambiguous; this port parses every sample unambiguously.
- Maximal munch (S-L2) replaces ANTLR's adaptive prediction. A `<` with a later `>` is always one `NAME` token, whereas ANTLR could take `<` as a single-character element instead. No sample depends on that.
- The semantic predicate `NotNL` (no newline inside an angle-bracketed left-hand side) is dropped: a left-hand side `NAME` may span newlines.
- `start_ : prods? ws? EOF`, `xlhs : ws? ...`, `xelem : ws? ...`, `assign : ws? ...` and `or : ws? ...` lose their `ws?` and `EOF`.
- `unicode-ebnf.bnf` writes `o̅r̅` with U+0305 (combining overline), not the U+203E that `OR2` expects. In both grammars it is four single-character elements, not an or operator.
