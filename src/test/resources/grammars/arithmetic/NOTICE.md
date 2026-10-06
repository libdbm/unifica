# arithmetic

- Source: `arithmetic/arithmetic.g4` and `arithmetic/examples/*.txt` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- License: BSD 3-clause, as stated in the `.g4` header. Copyright (c) 2013, Tom Everett. All rights reserved.
- Authors: Tom Everett.

The BSD license requires redistributions to retain the copyright notice, the list of conditions and the disclaimer; they are reproduced in full in the header of the source `.g4` file at the commit above.

## Deviations

- **Precedence rewritten.** ANTLR's single left-recursive `expression` rule resolves precedence by alternative order. The port splits it into `expression` (`+ -`), `multiplying` (`* /`), `powering` (`^`) and `primary` (parentheses, prefix signs, atoms), each left-recursive. Precedence and associativity match ANTLR's: all binary operators, including `^`, are left associative (ANTLR's default without `<assoc=right>`), so `2^3^4` is `(2^3)^4`. Prefix signs bind to the atom, so `-2^2` is `(-2)^2`, as in ANTLR. Trees therefore have `multiplying`, `powering` and `primary` nodes where the ANTLR tree has nested `expression` nodes.
- **Operator tokens inlined.** `PLUS`, `MINUS`, `TIMES`, `DIV`, `POW`, `LPAREN`, `RPAREN`, `EQ`, `GT`, `LT` are written as inline literals, so they appear as leaves rather than named token nodes.
- **Unused token dropped.** `POINT` (`.`) is never used by a parser rule; it is omitted. In ANTLR a stray `.` is a token the parser rejects; here it is a lexical error. Both reject the input.
- **Fragments inlined** into the `VARIABLE` and `SCIENTIFIC_NUMBER` regexes. `EOF` dropped (a parse always covers the whole input).
- **Examples.** All 18 `.txt` examples are included. The `.tree` files are not used.
