# NOTICE: rfc1035

- Source: `rfc1035/domain.g4` and `rfc1035/examples/` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- License: BSD 3-Clause License, as stated in the `.g4` header.
- Authors: Copyright (c) 2020, Tom Everett.

## Samples

- `valid/example1.txt`, `valid/example2.txt`, `valid/example3.txt`: copied unchanged from `rfc1035/examples/` (all of them; the `.tree` files are not used).
- `invalid/`: hand-written (label starting with a digit, label ending with a hyphen, empty label).

## Deviations

- **Unused lexer rules inlined.** `LDH_STR`, `LET_DIG_HYP`, `LET_DIG`, `LETTER` and `DIGIT` are non-fragment rules in ANTLR but no parser rule uses them. They are inlined into `LABEL` as `[a-zA-Z] (?:[a-zA-Z0-9\-]*[a-zA-Z0-9])?`. Acceptance is unchanged: where ANTLR would emit one of those tokens (for example `LDH_STR` for `abc-`), the input is rejected in both grammars.
- **Whitespace.** The grammar declares `whitespace none;`, so the `' '` alternative of `domain` is a token, and leading or trailing whitespace is rejected, as in ANTLR.
