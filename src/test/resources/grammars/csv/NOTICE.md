# CSV grammar: source and notice

- **Source:** `csv/CSV.g4` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- **Samples:** `csv/examples/example1.csv` from the same commit (the only example). `valid/crlf-quoted.csv`, `valid/empty-fields.csv` and everything under `invalid/` are hand-written for this port.
- **Authors:** Copyright (c) 2013 Terence Parr.
- **License:** BSD licence (3-clause), as stated in the `.g4` header.

## Deviations

- The grammar declares `whitespace none;`, so newlines are tokens and spaces belong to fields, as in ANTLR. The rules are those of `CSV.g4`, including the empty alternative of `field`.
- `EOF` is dropped (S-P1).
- Text directly after a closing quote (`"x"y`) or directly before an opening quote (`ab"c"`) lexes as two adjacent fields, which `row` rejects; ANTLR rejects both too.
