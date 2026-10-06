# Go grammar: provenance

- Source: `golang/GoLexer.g4` and `golang/GoParser.g4` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4), commit `7df52be9`. The semantic predicates in `golang/Java/GoParserBase.java` were read to understand what they decide. No code from them is used.
- Samples: every file under `valid/` is copied unchanged from `golang/examples/` at the same commit. Everything under `invalid/` is hand-written for this port.
- License: BSD licence ("The BSD licence"), as stated in both `.g4` headers, with its three conditions and warranty disclaimer. The README names BSD-3.
- Authors (from the copyright lines): Sasa Coh and Michał Błotniak (2017); Ivan Kochurkin, Positive Technologies (2019); Dmitry Rassadin, Positive Technologies (2019); Martin Mirchev (2021); Dmitry Litovchenko (2023, parser).

## Deviations

### Automatic semicolon insertion

This follows the ANTLR lexer. The ANTLR mode `NLSEMI` is the lexical state `NLSEMI`. The tokens that switch to it in ANTLR (an identifier, a basic literal, `break`, `continue`, `fallthrough`, `return`, `nil`, `)`, `]`, `}`, `++`, `--`) end with `==> ^NLSEMI`, and every other token with `==> ^DEFAULT`, which is what ANTLR's zero-width `OTHER` rule achieves. The grammar declares `whitespace none;`: `WS`, `COMMENT` and `LINE_COMMENT` are `skip` categories of the `DEFAULT` state, and `WS_NLSEMI`, `COMMENT_NLSEMI` (single-line only) and `LINE_COMMENT_NLSEMI` of the `NLSEMI` state, so in `NLSEMI` a line break, `;` or multi-line comment is an `EOS` token.

- Every token is a named category, because only a named lexical production can carry a mode change; an inline literal such as `'func'` could not. Keywords and punctuation therefore appear in trees as a node with one leaf (`FUNC("func")`), and the parser rules use the token names, as `GoParser.g4` does.
- `eos : SEMI | EOS | {closingBracket()}?` is `SEMI | EOS` plus an empty production guarded by `before('R_PAREN', 'R_CURLY') | at_end()`, the same lookahead as `closingBracket()`. The ANTLR `EOS` at end of input is covered by `at_end()`.

### Parser

The parser rules are those of `GoParser.g4`, except:

- **Expressions.** The left-recursive `expression` with operator alternatives becomes the precedence levels `expression` (`||`), `andExpr` (`&&`), `relExpr`, `addExpr`, `mulExpr` and `unaryExpr`, all left-associative, matching the Go specification.
- **`primaryExpr`.** The ANTLR rule chooses `operand | conversion | methodExpr` with symbol-table predicates (`isOperand`, `isConversion`, `isMethodExpr`) before its flat list of suffixes. Here:
  - `methodExpr` is dropped: `T.m` and `(*T).m` parse as selector expressions.
  - `conversion` is limited to `conversionType`: types that cannot be read as an expression, for example `[]byte(s)`, `map[K]V(m)`, `interface{}(x)`, `chan T(c)`, or such a type in parentheses like `([]int)(nil)` and `(*[]int)(p)`. `T(x)`, `p.T(x)` and `(*T)(x)` parse as calls, as `go/parser` does.
  - `operandName` has neither `qualifiedIdent` (`p.x` is a selector) nor `typeArgs`. Explicit type arguments in expressions are the suffix `instantiation`, which takes two or more types (`hashmap.New[int, string]`) or one type that cannot be an expression (`f[[]int]`). One type name in brackets (`f[T]`) parses as an index.
- **`arguments`.** The `isTypeArgument()` predicate admits a leading type only after `make` or `new`. Here a leading type is admitted after any callee, but only a type that cannot be an expression (`nonExprType`). `new(T)` and `make(T, n)` with a named type parse the type as an expression.
- **`parameters`.** The Go rule "all parameters are named or none is" replaces the ANTLR `identifierList? '...'? type_`, which is ambiguous for `(a, b int)`: a parameter list is `parameterDecl (COMMA parameterDecl)*` (named) or `parameterType (COMMA parameterType)*` (unnamed).
- **`result`.** A result starting with `(` is always a parameter list, so `type_`'s parenthesised alternative is left out of `result`. The ANTLR rule chooses this by alternative order.
- **`channelType`.** The `isNotReceive()` predicate is replaced by `chanElementType`, which is `type_` without the `<-chan` form. So `chan<- chan int` associates `<-` with the leftmost `chan`.
- **Composite literals in `if`/`for`/`switch` headers.** The specification's parenthesisation rule is not enforced. `if x {` followed by a line break cannot be a composite literal `x{...}`, because the line break after the first element is an `EOS`. The remaining difference is `if x {} {}` on one line: Go reads the first braces as the body and rejects the input; here it is accepted, unambiguously, with `x{}` as a composite literal.
- The actions `myreset()` and `addImportSpec()` and `EOF` are dropped (S-P1 requires covering the input).

### Lexer

- `IDENTIFIER` excludes the 25 keywords and `nil` with a negative lookahead (S-L2 would otherwise make them alternatives).
- Fragments are inlined. `FLOAT_LIT` is three productions (decimal with integer part, decimal starting with `.`, hexadecimal). The standalone ANTLR tokens `DECIMAL_FLOAT_LIT`, `HEX_FLOAT_LIT`, `BYTE_VALUE`, `OCTAL_BYTE_VALUE`, `HEX_BYTE_VALUE`, `LITTLE_U_VALUE` and `BIG_U_VALUE` are dropped. The parser never uses them, and outside literals they would only produce stray tokens.
- `RUNE_LIT` tries escapes before the single-character form, because Java regex alternation is leftmost-first (`'\''` must be one rune).

## Samples

All grammars-v4 examples are included except `4733.go`, which is an error example: its `4733.go.errors` lists the expected syntax errors, and it has no package clause. This includes `4739.go` (88 KB, about 0.6 s). Every sample parses unambiguously with penalty 0. The invalid inputs cover two statements on one line without `;`, `else` on the line after `}` (an `EOS` follows the `}`), and an unterminated string.
