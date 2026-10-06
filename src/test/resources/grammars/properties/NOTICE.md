# Java properties grammar: source and notice

- **Source:** `properties/PropertiesLexer.g4` and `properties/PropertiesParser.g4` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- **Samples:** all of `properties/examples/` from the same commit (`ebean.properties`, `example1.txt`, `example2.txt`, `example3.txt`). Everything under `invalid/` is hand-written for this port.
- **Authors:** none credited in the `.g4` files.
- **License:** the `.g4` headers state no license. The grammars-v4 repository default applies; the checkout used for the port contains only `src/`, so the repository license text could not be confirmed and is not reproduced here.

## Deviations

The parser rules are those of `PropertiesParser.g4`, and the grammar declares `whitespace none;`, so `CHARACTER` and `NEWLINE` tokens are produced exactly as by the ANTLR lexer.

- **Modes.** ANTLR's `VALUE_MODE` is the lexical state `VALUE`: `DELIMITER` pushes it and the line break that ends a value (`VALUE_TERM`) pops it. `COMMENT`, `NEWLINE`, `DELIMITER` and `CHARACTER` are annotated `{DEFAULT}` or `{VALUE}`, because unannotated lexical productions apply in every state while ANTLR modes are exclusive.
- **Escapes.** The `INSIDE` mode and the `-> more` rules (`SLASH`, `SLASH_DELIMITER`, `SLASH_JOINT`, `VALUE_SLASH`) become the escape alternative `[\\](?:[\r]?[\n]|[^\r\n])` of `CHARACTER`, which gives the same two-character `CHARACTER` tokens.
- **End of input.** `eol : NEWLINE+ | EOF` is `eol --> NEWLINE+` plus an empty production guarded by `at_end()`, which holds only at the end of the input.
- **Coverage.** `propertiesFile : row*` has no `EOF` in ANTLR, so the ANTLR parser accepts any prefix it can match and stops silently at the first token it cannot use. This port must cover the whole input (S-P1). So that files starting with a blank line (such as `ebean.properties`) are accepted, `propertiesFile` allows a leading `NEWLINE`. Inputs ANTLR silently truncates, such as `a=1\n: x`, are rejected here.
