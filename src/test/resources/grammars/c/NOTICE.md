# C grammar port

## Source

- `c/CLexer.g4` and `c/CParser.g4` from [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- Licence: BSD licence (3-clause), as stated in the header of both files.
- Author credited: Copyright (c) 2013 Sam Harwell. The grammar has since been extended in grammars-v4 (C23, GNU, Clang and Visual C extensions).

## Samples

`valid/` holds:

- the 32 top-level `*.c` files of `c/examples/` (the `maven/` and `tiny/` directories hold copies of these and are not repeated; `*.trq` files are parse-tree queries, not C);
- 22 files from `c/examples/c-testsuite/tests/single-exec/`, renamed `c-testsuite-NNNNN.c` (they come from the tcc and scc test suites, see the `.otags` files next to them in grammars-v4).

All 220 c-testsuite files were run against the port: 204 parse. The 16 that do not (00062, 00063, 00066 to 00071, 00074, 00115, 00122, 00137, 00162, 00202, 00204, 00210) test the preprocessor or need macro expansion to be C (for example `char s[] = "a" B "c";` with `#define B "b"`, or `va_arg(ap, struct s7)`), or are deliberately invalid (00162 under `#ifdef INVALID`). The ANTLR grammar parses them only after running `gcc -E` from target-language code (`CLexerBase`), which this port cannot do. The `gcc/`, `sqlite/` and `writing-a-c-compiler-tests/` directories were not used for the same reason.

`invalid/` holds three hand-written inputs: a missing semicolon, an unterminated block comment, and an unclosed block.

## Deviations

1. **Preprocessor.** `MultiLineMacro`, `LineDirective` and `Directive` (hidden channels in ANTLR) become one skipped category `Directive`: `#` up to the end of the line, with backslash-newline continuations. Nothing is preprocessed.
2. **Symbol table replaced by production costs.** The ANTLR parser calls `IsTypedefName`, `IsDeclarationSpecifier`, `IsCast`, `IsStatement`, `IsDeclaration`, `IsSomethingOfTypeName`, `IsInitDeclaratorList` and `IsNullStructDeclarationListExtension` (target code with a symbol table). The port drops them, accepts every reading, and selects one by cost:
   - `expressionStatement` costs 1, so a block item that is both a declaration and an expression statement (`T * p;`, `node * a, * b;`) is a declaration;
   - a parenthesised declarator with no pointer and nothing after it (`T (x)`) costs 2, so `g(x);` stays a call;
   - a declaration whose only type is a typedef name and that declares nothing (`x;`) costs 2 (also in `for` initialisers);
   - a cast, `sizeof ( type-name )`, `alignof ( type-name )`, `_Countof ( type-name )`, `_Alignas ( type-name )` and `typeof ( type-name )` cost 1, so `(a) - b` is a subtraction and `sizeof (x)` takes an expression. As a consequence `(T) -x` with a typedef `T` also parses as a subtraction;
   - a parameter with no declaration specifiers costs 1 when it has a declarator or an abstract declarator, so `f(T)` declares an unnamed parameter of type `T`. K&R identifier lists such as `main(t, _, a)` therefore parse as lists of typedef-named parameters; they are still accepted;
   - a label with no statement (`label: }`) costs 1, so a following statement attaches to the label (ANTLR's greedy `statement?`);
   - `goto` followed by an expression (GCC computed goto) costs 1, so `goto x;` uses the plain form.

   Penalties of accepted samples are therefore not zero: they count these readings.
3. **Typedef names in specifier lists.** `declarationSpecifiers` and `specifierQualifierList` carry a feature `t` (`none`, `builtin`, `name`). A list holds either no type specifier, one typedef name and no other type specifier, or builtin type specifiers only (C 6.7.2). ANTLR checks this with `IsDeclarationSpecifier`. `T int x;` is rejected.
4. **Dangling else.** Statements carry a feature `open` (`yes` for an `if` without `else`, or a statement ending in one). The then-branch of an `if ... else` is `statement{open: no}`, so `else` binds to the nearest `if` as in ANTLR.
5. **`declarator`.** ANTLR's `(gnuAttribute? pointer)*` (ambiguous, since `pointer` is itself a repetition) becomes one optional `gnuAttribute? pointer`; an attribute between two `*` is not accepted. `declarator` carries a feature `ptr` used by the cost in item 2.
6. **`DigitSequence`.** In ANTLR, `IntegerConstant` is listed first and wins ties, so `DigitSequence` only appears for digit strings such as `09` that are longer than any integer constant match. The port's `DigitSequence` matches exactly those (`0[0-7]*[89][0-9]*`), because Unifica keeps all equal-length matches. The redundant `assignmentExpression --> DigitSequence` alternative is dropped (it is reachable through `castExpression`).
7. **`enumTypeSpecifier`** is `':' specifierQualifierList` (C23 6.7.2.2). The ANTLR rule omits the colon.
8. **`toplevelAsmArgument`** loses its bare string alternative, which duplicates `simpleAsmExpr`.
9. **`gnuSingleAttribute`.** ANTLR's `~('(' | ')')` (any token) is approximated by identifiers, constants, string literals, `, = + - * .` and the keywords `const`, `deprecated`, `inline`, `restrict`, `volatile`.
10. **Keywords.** Keyword categories the parser names (`Bool`, `Inline`, `Restrict`, `Typeof`, `Alignof`, `While`, `Do`, `For`, `Attribute`, and so on) are lexical productions; the others are inline literals, as in `CParser.g4`. `Identifier` excludes every keyword of `CLexer.g4` with a negative lookahead.
11. **Unreferenced rules** (`identifierList`) and actions (`OutputSymbolTable`, `EnterScope`, `EnterDeclaration`, ...) are omitted.
12. **Whitespace.** `Whitespace` and `Newline` are the `whitespace` statement, which also includes form feed and vertical tab. C treats both as whitespace (C11 6.4), and the grammars-v4 harness lexes `gcc -E` output rather than the source; `c-testsuite-00216.c` contains a form feed.
