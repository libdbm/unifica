# sexpression

- Source: `sexpression/sexpression.g4` and `sexpression/examples/*.txt` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- License: MIT, as stated in the `.g4` header. Copyright (c) 2008 Robert Stehwien.
- Authors: Robert Stehwien (original grammar); ported to ANTLR 4 by Tom Everett.

The MIT license requires the copyright notice and permission notice to be included in all copies or substantial portions; they are reproduced in full in the header of the source `.g4` file at the commit above.

## Deviations

- **Dotted pairs and `DOT` omitted.** The ANTLR grammar has `item : LPAREN item DOT item RPAREN` and `atom : DOT`, but its lexer never emits `DOT`: `SYMBOL` also matches `.` and is declared earlier, so ANTLR always lexes `.` as `SYMBOL` (the grammar's readme notes dot notation "isn't yet working"). Keeping `DOT` here would make `.` lex both ways and `(a . b)` ambiguous. The port drops both alternatives; `(a . b)` is a list of three atoms, exactly as in ANTLR.
- **NUMBER/SYMBOL ties.** `SYMBOL` matches signed numbers such as `+1` or `-1.5` with the same length as `NUMBER`. ANTLR picks `NUMBER` by declaration order; Unifica keeps equal-length matches as alternatives, so `SYMBOL` has a negative lookahead that excludes exactly the texts `NUMBER` matches. Longer matches such as `-1.5x` are still `SYMBOL`, as in ANTLR.
- **Tokens inlined.** `LPAREN` and `RPAREN` are inline literals (leaves in the tree). Fragments `SYMBOL_START` and `DIGIT` are inlined into the regexes. `WHITESPACE -> skip` is the `whitespace` statement. `EOF` dropped.
- **Examples.** Both source examples (`example1.txt`, `example2.txt`) are included. Because there are only two, three hand-written samples were added: `factorial.txt`, `nested.txt` and `atoms.txt` (signed numbers, symbols made of operator characters, `.`, escaped quotes and a newline inside a string, the empty list).
