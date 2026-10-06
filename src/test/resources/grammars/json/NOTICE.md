# JSON grammar: source and notice

- **Source:** `json/JSON.g4` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- **Samples:** `json/examples/example1.json` and `json/examples/numbers.json` from the same commit (all examples in the source). `valid/mixed.json` and everything under `invalid/` are hand-written for this port.
- **Authors:** Terence Parr ("Taken from The Definitive ANTLR 4 Reference by Terence Parr"), derived from https://json.org.
- **License:** the `.g4` header states no license. The grammars-v4 repository default applies; the checkout used for the port contains only `src/`, so the repository license text could not be confirmed and is not reproduced here.

## Deviations

- `EOF` is dropped: a parse must cover the whole input anyway (S-P1).
- Fragments `ESC`, `UNICODE`, `HEX`, `SAFECODEPOINT`, `INT` and `EXP` are inlined into the `STRING` and `NUMBER` regexes.
- `WS : [ \t\n\r]+ -> skip` is the `whitespace` statement.
