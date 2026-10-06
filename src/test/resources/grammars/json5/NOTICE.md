# JSON5 grammar: source and notice

- **Source:** `json5/JSON5.g4` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- **Samples:** all of `json5/examples/` from the same commit (`example1.json`, `example2.json5`, `example3.json5`, `example4.json5`, `issue1960.json5`). Everything under `invalid/` is hand-written for this port.
- **Authors:** Student Main (2020-07-22). Derived from grammars-v4 `json/JSON.g4`.
- **License:** public domain, as stated in the `.g4` header.

## Deviations

- `json5 : value? EOF` becomes `json5 --> value?` (`EOF` is implied by S-P1).
- ANTLR resolves the overlap of `LITERAL` (`true`, `false`, `null`) and `NUMERIC_LITERAL` (`Infinity`, `NaN`) with `IDENTIFIER` by rule order. Maximal munch here keeps every category of the longest length (S-L2), so `IDENTIFIER` starts with a negative lookahead that excludes those five words when they are not followed by another identifier character. `trueish` is still an identifier.
- `NUMBER` is one regex whose hexadecimal alternative comes first, because Java regex alternation takes the first alternative that matches (`0x1F` would otherwise stop at `0`). The language matched is the same as ANTLR's.
- `ESCAPE_SEQUENCE` is written as `\` followed by a line terminator, `u` and four hex digits, `x` and two hex digits, or any character except `1`-`9`, `x`, `u`, CR and LF. This is the union of the ANTLR alternatives (`['"\\/bfnrtv]`, `~['"\\bfnrtv0-9xu\r\n]` and `'0'`).
- `SINGLE_LINE_COMMENT : '//' .*? (NEWLINE | EOF)` becomes `'//'` followed by non-terminator characters and an optional terminator (CRLF, CR, LF, U+2028, U+2029). `MULTI_LINE_COMMENT : '/*' .*? '*/'` becomes `'/*' (?:[^*]|[*]+[^*/])* [*]+ '/'`, which matches the same text without a non-greedy operator. Both are `skip` categories.
- `WS : [ \t\n\r\u00A0\uFEFF\u2003]+ -> skip` is the `whitespace` statement.
- Fragments are inlined into the regexes.
