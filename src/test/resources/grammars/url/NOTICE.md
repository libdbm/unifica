# NOTICE: url

- Source: `url/url.g4` and `url/examples/` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- License: BSD 3-Clause License, as stated in the `.g4` header.
- Authors: Copyright (c) 2016, Tom Everett.

## Samples

- `valid/`: all 29 files from `url/examples/` copied unchanged.
- `invalid/`: hand-written (non-numeric port, missing `:` before `//`, unterminated IPv6 host).

## Deviations

- **Whitespace.** The grammar declares `whitespace none;`: as in ANTLR, nothing is skipped and a URL may only be followed by a run of line breaks (`WS`).
- **`(string | DIGITS)` simplified to `string`.** `string` already derives `DIGITS`, so these alternations are ambiguous; ANTLR resolves them to the first alternative (`string`). The port writes `string`, giving the tree ANTLR builds, without the ambiguity. Likewise `(string | DIGITS | HEX)` becomes `string` or `HEX`.
- **Lexer ties.** ANTLR gives equal-length ties to the first rule, so a digit run is `DIGITS` and a run of `%XX` escapes is `HEX`, never `STRING`. Unifica keeps every tied category, so `STRING` has a negative lookahead that excludes exactly those two cases.
- The alternative labels `# DomainNameOrIPv4Host` and `# IPv6Host` are dropped; both alternatives are plain `hostname` productions.
