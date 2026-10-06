# smalltalk

Source: grammars-v4 `smalltalk/Smalltalk.g4` at commit `7df52be9`
(https://github.com/antlr/grammars-v4/tree/7df52be9/smalltalk). The older
`Smalltalk.g42` in the same directory was not used.

License: none is stated in the `.g4` header, and grammars-v4 has no
repository-wide default licence. The header credits James Ladd (Redline
Smalltalk Project, object@redline.st), 2015, who converted it to ANTLR 4
from the Amber Smalltalk `parser.pegjs` grammar.

`valid/helloworld.st` is `examples/helloworld.st` (the only example there),
unchanged. `valid/collections.st`, `valid/literals.st` and `valid/blocks.st`
are hand-written for this port. Some of their constructs are accepted here but
rejected by the ANTLR grammar (block parameters followed by `|`, `16r`
numbers, exponents, `a | b`, `x:=1`); see below. The files in `invalid/` are
hand-written.

## Deviations

- **Whitespace and comments.** ANTLR keeps whitespace (`SEPARATOR`) and
  comments as tokens and threads an optional `ws` rule through the parser.
  Every use of `ws` is optional, so here `ws` is removed, whitespace is skipped
  and `COMMENT` is a skip category.
- **Numbers.** ANTLR builds numbers from one-character `DIGIT` tokens with
  `HEX`, `HEXDIGIT`, `EXP` and `MINUS`. Its lexer never produces `EXP` (`e` is
  an `IDENTIFIER`), `HEXDIGIT` (`DIGIT` or `IDENTIFIER` win) or `MINUS`
  (`BINARY_SELECTOR` wins), so exponents, `16r` digits and negative literals
  are unreachable there. Here `numberExp`, `hex_`, `stFloat` and `stInteger`
  are tokens: exponents and `16r` numbers work, negative literals are still
  absent (a `-` before a digit is a binary selector). The unused tokens
  `DIGIT`, `HEXDIGIT`, `HEX`, `EXP`, `MINUS`, `COLON` and `DOLLAR` are dropped.
- **Blocks.** The ANTLR rule `BLOCK_START blockParamList? ws? sequence?
  BLOCK_END` has no `|` between parameters and body, so `[:x | x + 1]` is
  rejected there (and `[:x | t | x]` reads `t` as a temporary). Here a block
  is `[body]`, `[:x]` or `[:x | body]`, with temporaries at the start of the
  body as usual (`[:x | | t | ...]`).
- **binaryMessage.** `BINARY_SELECTOR (unarySend | operand)` becomes
  `BINARY_SELECTOR unarySend`: `unarySend` already covers a bare operand, so
  the second option only duplicated parses (ANTLR always chose the first).
- **Symbols and literal arrays.** `symbol --> HASH bareSymbol` with
  `bareSymbol` allowing a string made `#('abc')` ambiguous between a string
  literal and a bare symbol. The string option moves to `symbol` itself
  (`HASH (bareSymbol | string)`). `KEYWORD+` in `bareSymbol` (adjacent
  keywords in ANTLR, as in `#at:put:`) is a single `KEYWORDS` token, with a
  single `KEYWORD` as its own option; otherwise `#(at: put:)` and
  `#(at:put:)` would be indistinguishable once whitespace is skipped. `HASH`
  and the selector may be separated by whitespace here (`# foo`), which ANTLR
  rejects.
- **`|` as a binary selector.** ANTLR declares `PIPE` before
  `BINARY_SELECTOR`, so a single `|` is always `PIPE` and `a | b` is
  rejected. Here both tokens are produced and the parser chooses.
- **Primitives.** ANTLR's `LT`/`GT` are never produced (`BINARY_SELECTOR`
  wins on `<` and `>`), so `primitive` is unreachable there. Here the tokens
  tie and `<primitive: 60>` parses.
- **Assignment without spaces.** In ANTLR `x:=1` lexes as `KEYWORD x:` and
  `=`, so it is rejected. Here `KEYWORD` refuses a following `=`, and `x:=1`
  is an assignment.
- **Reserved words.** ANTLR's `RESERVED_WORD` wins over `IDENTIFIER` on equal
  length by declaration order; here `IDENTIFIER` excludes `nil`, `true`,
  `false`, `self` and `super` with a negative lookahead.
- **Unchanged limitations.** `CHARACTER_CONSTANT` accepts only `$` followed by
  a hex digit or `$` (so `$z` is rejected), and `STRING` has no `''` escape,
  as in the source. A cascade part is one message, so `a foo; bar baz` is
  rejected.
- `EOF` is dropped (a parse always covers the whole input).
