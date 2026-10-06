# calculator

- Source: `calculator/calculator.g4` and `calculator/examples/*.txt` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- License: BSD 3-clause, as stated in the `.g4` header. Copyright (c) 2013, Tom Everett. All rights reserved.
- Authors: Tom Everett.

The BSD license requires redistributions to retain the copyright notice, the list of conditions and the disclaimer; they are reproduced in full in the header of the source `.g4` file at the commit above.

## Deviations

- **Precedence levels nested.** ANTLR writes `expression`, `multiplyingExpression` and `powExpression` as flat lists (`x (op x)*`), so the ANTLR tree does not express associativity. The port keeps the rule names but nests them: `+ -` and `* /` are left associative (`8/4/2` is `(8/4)/2`) and `^` is right associative (`2^3^4` is `2^(3^4)`, the usual mathematical reading; ANTLR leaves this to the consumer). Prefix signs bind tighter than `^` (`-2^2` is `(-2)^2`) because `signedAtom` is the operand of `^`, as in ANTLR.
- **Function arguments.** `(COMMA expression)*` in `func_` is the group `(',' expression)*`, so each argument's comma and expression sit directly under `func_`, as in ANTLR.
- **Keyword tokens.** `COS`, `SIN`, `TAN`, `ACOS`, `ASIN`, `ATAN`, `LN`, `LOG`, `SQRT`, `PI`, `EULER` (`e`) and `I` are inline literals and appear as leaves under `funcname` and `constant`. ANTLR gives them priority over `VARIABLE` by declaration order; Unifica keeps equal-length matches as alternatives, so `VARIABLE` excludes exactly these words with a negative lookahead. Longer identifiers such as `cosx` or `e2` are still variables, as in ANTLR.
- **Operator tokens inlined** (`+ - * / ^ ( ) , = > <`), so they appear as leaves.
- **Unused token dropped.** `POINT` (`.`) is never used by a parser rule. In ANTLR a stray `.` reaches the parser and is rejected; here it is a lexical error. Both reject the input.
- **Fragments inlined** into `VARIABLE` and `SCIENTIFIC_NUMBER`. `EOF` dropped (a parse always covers the whole input).
- **Examples.** All 21 `.txt` examples are included. The `.tree` files are not used.
