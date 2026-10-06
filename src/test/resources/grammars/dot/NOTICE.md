# DOT grammar port

- Source: `dot/DOT.g4` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- Samples: `dot/examples/cluster.dot`, `dot/examples/crazy.dot`, `dot/examples/dg.dot`, `dot/examples/java/JavaLexer.FLOAT_LITERAL.dot` and `dot/examples/csharp-v8-spec/CSharpLexer.Comment.dot` from the same commit. `valid/handwritten_html.dot` is hand-written.
- License: BSD licence (3-clause), as stated in the `.g4` header. Copyright (c) 2013 Terence Parr. All rights reserved.
- Authors: Terence Parr; modified by Andrzej Borucki (2025, character set). Derived from http://www.graphviz.org/doc/info/lang.html.

## Deviations

- `EOF` is dropped: a parse must cover the whole input anyway (S-P1).
- `options { caseInsensitive = true; }` is expressed with explicit character classes: keywords use `[sS][tT]...`, and `ID` uses `[A-Za-z_]`.
- ANTLR resolves the tie between a keyword and `ID` by rule order. Unifica keeps every category of the longest length (S-L2), so `ID` excludes the six keywords (case-insensitively) with a negative lookahead.
- `COMMENT`, `LINE_COMMENT` and `PREPROC` (`-> skip`) are `skip` categories, and `WS` is the `whitespace` statement.
- Examples excluded: `graphviz-tests/1864.dot` and `graphviz-tests/2593.dot` (14 MB each, over the size limit). The `java/` and `csharp-v8-spec/` directories hold about 1,300 machine-generated ATN dumps with the same shape; two representative ones are included. No example uses HTML strings, mixed-case keywords, ports or `#` lines, so `valid/handwritten_html.dot` covers them.
