# Lua grammar port

## Source

- grammars-v4, commit `7df52be9`: `lua/LuaLexer.g4`, `lua/LuaParser.g4`
  (and `lua/Java/LuaLexerBase.java` for the comment handling it implements in code)
- Sample inputs in `valid/`:
  - `lua/examples/*.lua` (all 11 top-level files, copied unchanged)
  - `lua/examples/lua-5.4.6-tests/{all,bitwise,closure,constructs,goto,literals,locals,main,vararg}.lua`,
    copied unchanged with a `5.4.6-` prefix

## License

MIT license, as stated in the `LuaParser.g4` header (`LuaLexer.g4` has no header).

## Authors

- Ken Domino (October 2023)
- Based on previous work of Kazunori Sakamoto and Alexander Alexeev

## Deviations

- **Start symbol.** `start_ : chunk EOF` is dropped; `chunk` is the start symbol
  (`start` is reserved in `.ug`) and EOF is implicit.
- **prefixexp, var, functioncall.** The ANTLR parser unrolls these by hand, with a
  semantic predicate (`IsFunctionCall`), to avoid mutual left recursion. They are
  replaced by the left-recursive rules of the Lua 5.4 reference manual
  (`var --> prefixexp '.' NAME`, `prefixexp --> functioncall`,
  `functioncall --> prefixexp args`, ...), which an Earley parser handles directly.
- **Expressions.** ANTLR's single `exp` rule is resolved by alternative order and
  puts the bitwise operators at the lowest precedence. It is rewritten as one
  level per Lua 5.4 manual precedence level (section 3.4.8): `exp` (or),
  `andexp`, `compareexp`, `borexp`, `bxorexp`, `bandexp`, `shiftexp`, `concatexp`
  (right associative), `addexp`, `mulexp`, `unaryexp`, `powexp` (right
  associative, its right operand is a `unaryexp` so `2^-3` parses), `simpleexp`.
  So `a | b == c` groups as `(a | b) == c`, as in Lua, where ANTLR's
  alternative order gives `a | (b == c)`.
- **Ambiguous statement boundaries.** `a = f` followed by `(g)()` can be one call
  or two statements. ANTLR (greedily) and Lua read one call. The statements that
  can start with `(` (assignment and call) carry a soft constraint
  `(!starts_with(..., '(')):1`, so the two-statement reading costs 1 more and
  loses. A file with a statement that really starts with `(` (for example
  `(Message or print)(...)` after `else` in `5.4.6-literals.lua`) is accepted with
  penalty 1 per such statement.
- **Long brackets.** ANTLR's `LONGSTRING` uses a recursive fragment that does not
  check that the opening and closing levels agree, and `COMMENT` is matched by
  target-language code (`LuaLexerBase.HandleComment`). The regex subset has no
  recursion and no counting, so levels 0 to 7 (`[[ ]]` to `[=======[ ]=======]`)
  are spelled out as alternatives with a lazy body. The closing bracket must have
  the same level as the opening one, and the body ends at the first such
  closing bracket, as in Lua. Long strings and long comments of level 8 or more
  are not supported (they are rejected, never misread).
- **Comments.** `LONGCOMMENT` (`--` followed by a long bracket) and `LINECOMMENT`
  (`--` not followed by `[`, `=`*, `[`) are skip categories. An unterminated long
  comment is rejected, as `HandleComment` reports it as an error.
- **Shebang.** ANTLR hides `SHEBANG` (`'#' '!'? ...`) when the predicate
  `IsLine1Col0` holds. Here it is a skip category guarded by `at_char_start()`, so
  a `#` line is skipped only at the start of the input and `#` elsewhere is the
  length operator.
- **Whitespace and newlines.** `WS` and `NL` (ANTLR channels HIDDEN and 2) are the
  `whitespace` statement.
- **Strings.** `NORMALSTRING` and `CHARSTRING` inline the escape fragments as in
  ANTLR, including the World of Warcraft escapes `\|`, `\$`, `\#`. As in ANTLR,
  `\z` does not skip following whitespace and the bodies may contain raw newlines.
- **Excluded examples.** `lua/examples/lua-test-suite/` (third-party projects:
  neovim, luvit, luau and others) is not copied, to keep the sample set focused.
  The `*.lua.fail` files are inputs the ANTLR grammar is expected to fail on.
  `api.lua` (42 KB) and the other 5.4.6 tests also parse but are not copied.
