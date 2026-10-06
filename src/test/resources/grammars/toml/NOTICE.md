# NOTICE: toml

- Source: `toml/TomlLexer.g4`, `toml/TomlParser.g4` and `toml/examples/` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- License: Apache License, Version 2.0, as stated in both `.g4` headers (Apache Software Foundation notice; `toml/LICENSE` holds the full text).
- Authors: no individual authors are credited in the grammar files.

## Samples

- `valid/fruit.toml`, `valid/hard.toml`, `valid/long.toml`, `valid/test.toml`: all of `toml/examples/`, copied unchanged.
- `invalid/`: hand-written (unterminated basic string, unclosed array, missing value after `=`).

## Deviations

- **Lexer modes as states.** ANTLR's default mode, `SIMPLE_VALUE_MODE`, `INLINE_TABLE_MODE` and `ARRAY_MODE` are the states `DEFAULT`, `VALUE`, `INLINE` and `ARRAY`. `pushMode(X)` is `==> X`, `mode(X)` (which replaces the top of the mode stack) is `==> ^X`, and `popMode` is `==> _`. Every lexical production is annotated with its state, because unannotated productions apply in every state while ANTLR modes are exclusive.
- **`type(X)` tokens.** ANTLR rules such as `ARRAY_COMMA : COMMA -> type(COMMA)` are additional productions of the category they retype to (`COMMA`, `L_BRACKET`, `BASIC_STRING` and so on), restricted to the right state.
- **Line breaks and comments inside arrays.** `WS` is the `whitespace` statement, and outside arrays `NL` and `COMMENT` are tokens, so `document`, `expression` and `comment` are ANTLR's rules. Inside arrays, ANTLR wraps every value in `comment_or_nl` and `nl_or_comment`, which are adjacent at the end of an array, so a multi-line array has several equally valid derivations that ANTLR's greedy prediction chooses between. Here `ARRAY_NL` and `ARRAY_COMMENT` are `skip` categories of the `ARRAY` state, `array_` is `L_BRACKET array_values? R_BRACKET`, and `array_values` drops `comment_or_nl` and `nl_or_comment`. Their (often empty) nodes do not appear in trees. One input differs: a comment directly after an array value and before the comma (`[1 # c\n, 2]`) is rejected by ANTLR, whose `nl_or_comment` requires a line break first, and accepted here.
- **Regex fidelity.** Non-greedy loops whose body cannot contain the closing delimiter (`BASIC_STRING`, `ML_BASIC_STRING`, `LITERAL_STRING`) are written greedily, which matches the same text; `ML_LITERAL_STRING` keeps a lazy `[\s\S]*?`. As in the original, a multi-line basic string cannot contain `"`.
- `EOF` is dropped (S-P1).
