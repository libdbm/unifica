# ABNF grammar port

- Source: `abnf/Abnf.g4` in [antlr/grammars-v4](https://github.com/antlr/grammars-v4) at commit `7df52be9`.
- Samples: `abnf/examples/abnf.abnf`, `iri.abnf`, `postal.abnf`, `rfc5322.abnf`, and `abnf/examples/apg-java/ABNFforSABNF.bnf`, `expressions.bnf`, `inifile.bnf`, `mailbox.bnf` (prefixed `apg-java-` here) from the same commit.
- License: BSD 3-Clause, as stated in the `.g4` header. Copyright (c) 2013, Rainer Schuster. All rights reserved.
- Authors: Rainer Schuster. Derived from RFC 5234 and RFC 7405.

## Deviations

- `EOF` is dropped: a parse must cover the whole input anyway (S-P1).
- ABNF has no rule terminator. ANTLR ends a rule with LL(*) prediction; the Earley parser finds the boundary because only `ID '='` can start a rule. All samples parse unambiguously.
- `COMMENT` and `WS` are on ANTLR's hidden channel: here `COMMENT` is a `skip` category and `whitespace [ \t\r\n]+;` declares `WS`.
- Number values accept only lowercase `b`, `d` and `x`, as in the ANTLR grammar (RFC 5234 makes them case-insensitive).
- Examples excluded: the other 17 `apg-java/*.bnf` files are small variants of the included ones (all 21 were accepted when tried). The `*.sabnf.ignore` files are excluded, as the grammars-v4 test harness excludes them.
