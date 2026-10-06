# Unifica Semantics

Version: 2.0.0 draft (2026-10-05)

This document is normative for every Unifica implementation (Java and Dart). Each rule has a stable ID. Implementations
are checked against the conformance corpus in `src/test/resources/conformance/`, whose expected results are written from
this document, never from implementation output.

The grammar surface syntax is defined by `src/main/antlr4/UnificationGrammar.g4`. In constraint expressions, `,` is and,
`|` is or, `!` is not, and `:N` attaches a weight to a group or predicate. An identifier starting with an uppercase
letter in a feature value is a variable.

## Grammar classification

- **S-G1.** Every production is **lexical** or **syntactic**. The classification is decided once, at load time, and
  recorded. Downstream stages never infer it again. A production is lexical if its RHS is a single literal, or contains
  a regex, a state annotation, or a transition, and contains no nonterminal and no `{TOKEN}`. The same classification
  applies to productions built through the API. Every other production, including a sequence of two or more plain
  literals, is syntactic.
- **S-G2.** A lexical production is atomic. It matches one contiguous character span as one token whose category is the
  production's LHS. Its RHS elements (literals, regexes, state annotations, transitions) are parts of one pattern and
  are never visible to other productions on their own.
- **S-G3.** A literal or regex written inline in a syntactic production defines an anonymous lexical category for
  exactly that literal or pattern. Regex fragments inside lexical productions never define anonymous categories.
- **S-G4.** Parenthesised groups may hold a sequence of elements, and alternation options may be sequences or empty. A
  top-level `|` separates alternatives that each become their own production, and an empty alternative is an empty
  production. Repetition (`?`, `*`, `+`), groups and alternation mean exactly the same as their expansion into
  productions, including the variables they share with the enclosing production (S-F5).

## Lexical analysis

- **S-L1.** Lexical analysis produces a directed acyclic **token graph**. A node is (character offset, lexical state
  stack). An edge is (source node, target node, text, category, features, cost). Costs are non-negative, and every edge
  leads to a later node, so the graph is acyclic.
- **S-L2.** Maximal munch. At each node, consider every lexical production whose state constraints and required lexical
  constraints hold. Take the longest match length among them. Every production that matches exactly that length
  contributes one edge, so categories that tie on length remain alternatives. Shorter matches contribute no edge.
- **S-L2a.** Because edges of equal length can lead to different state stacks (S-L3), the graph can still branch.
  Different token boundaries arise only when those state paths later diverge, or when the caller supplies them (S-L6).
- **S-L3.** An edge's target state stack is the source stack with the matched production's transition applied (push `S`,
  pop `_` (never below the bottom), reset `!S`). Edges with equal span and different resulting stacks lead to different
  nodes. `==> ^S` replaces the top of the stack with `S`.
- **S-L4.** Before each token, whitespace and matches of the lexical categories a grammar declares with `skip A, B;` are
  discarded, repeatedly, until neither matches. Whitespace is `\s+` unless the grammar declares `whitespace <pattern>;`
  or `whitespace none;`. Skipping applies between tokens only. Skip categories respect their state annotations and
  required constraints, but apply no transitions and never become tokens.
- **S-L5.** If no edge leaves a reachable node that is not at end of input, the lexer adds a one-character error edge
  with category `error`. No production accepts it unless one names `error` explicitly.
- **S-L6.** A token stream supplied by the caller (for example POS-tagged tokens) is a token graph. A list of tokens is
  a linear graph. A list of alternative lists is a graph whose alternatives share boundaries. Caller tokens that
  overlap, are out of order, or whose alternatives have different spans are not a token graph and are refused.

## Constraints

- **S-C1.** A constraint expression is evaluated for Boolean truth only. `and` (written `,`), `or` (`|`), and `not` (
  `!`) have ordinary Boolean meaning.
- **S-C2.** A production's constraints are a set of **required expressions** and a set of **soft groups**. A soft group
  is an expression with a positive integer weight, written `(...):w` or `pred(...):w`.
- **S-C3.** A derivation is rejected if any required expression is false.
- **S-C4.** A false soft group adds its weight exactly once, however many of its sub-expressions are false. Distinct
  soft groups add their weights together.
- **S-C5.** Weights inside an enclosing soft group are ignored. The linter reports them as warnings.
- **S-C6.** A soft group may appear only as a top-level conjunct of a production's constraint expression. Unweighted
  parenthesized conjunctions are flattened into the enclosing top-level conjunction first, because `and` is associative
  and this preserves truth. So `R, (P:3, Q:4)` has one required expression and two soft groups. A soft group under `or`
  or `not` is a compile error.
- **S-C7.** Normalization and phase splitting preserve Boolean truth. Lexical predicates (`in_state`, `state_depth`,
  `state_contains`, `at_char_start`, `at_char_position`) read the lexical state stack and character position. In a
  lexical production they are evaluated by the lexer for the token's start node, replaced by their truth values, and the
  remaining expression is evaluated later. In a syntactic production they are evaluated against the state stack and
  offset of the node where the constituent starts. Predicates are never deleted from an expression. Positional
  predicates (`at_start`, `at_position`, `at_end`, `before`) read the constituent's span: the node where it starts,
  whether it ends at a final node, and the categories of the edges leaving its end node.
- **S-C8.** A predicate name the grammar uses but that is not registered, or a call with the wrong arity, is a compile
  error. At runtime a predicate is either true or false.
- **S-C9.** The text of a constituent is the input substring from its first token's start to its last token's end.
  String builtins treat a literal string, a labelled terminal, and a labelled nonterminal the same way, using that text.
- **S-C10.** Penalties are non-negative 64-bit integers. If a sum would overflow, the parse ends with the `limit`
  outcome (S-P5).

## Features and unification

- **S-F1.** Unification is one operation. It returns the unified structure with all bindings substituted and the binding
  set. Any operation that also reports why unification failed returns the same structure as unification itself when it
  succeeds.
- **S-F2.** The occurs check covers every value that contains features, including constituent bindings.
- **S-F3.** Feature structures are acyclic. Building a cyclic structure is an error.
- **S-F4.** Numeric values are equal when they denote the same mathematical value: the integer `1` and the floating
  value `1.0` are equal. Integral values are compared exactly. Integral and floating values are compared by converting
  the integral value to an exact decimal, never by narrowing it to a double.
- **S-F5.** Each use of a production has its own variables. The features written on a right-hand-side element must unify
  with the features of the constituent or token that fills it, and the resulting bindings hold for the rest of that use
  of the production. A completed constituent's features are its left-hand features with those bindings substituted; its
  remaining variables are renamed apart from every other constituent's. Features written on right-hand-side elements are
  never copied into the parent: a parent sees a child's features only through shared variables.

## Parsing and selection

- **S-P1.** A parse succeeds if a derivation of the start symbol, satisfying every required constraint, covers a path
  through the token graph from the initial node to a final node (end offset, any state stack).
- **S-P2.** A parse returns exactly one tree: the one with the lowest total penalty. Total penalty is the sum of edge
  costs, production costs, and soft-group weights along the derivation.
- **S-P3.** Ties are broken deterministically. Compare the two trees' productions in pre-order, and prefer the one whose
  first differing production appears earlier in declaration order. If the productions are identical, prefer the tree
  whose first differing constituent span ends earlier.
- **S-P4.** `ambiguous` is true when more than one distinct derivation reaches the lowest penalty.
- **S-P5.** A parse ends with one of four outcomes: `accepted`, `rejected`, `limit` (a configured resource limit or
  penalty overflow), or `cancelled`. Only `accepted` carries a tree.
- **S-P6.** Production order does not change acceptance or the minimum penalty. It only affects tie-breaking (S-P3).
- **S-P7.** A production may declare a non-negative cost, written `@N` at the end of the production (after any
  constraints and transition), which is added to the penalty whenever the production is used.
- **S-P8.** Tree shape. A syntactic constituent is a node with its symbol, its label from the parent's RHS (if any), its
  features, and its children in order. A token consumed for a named lexical category is a node with that category as its
  symbol and one leaf child holding the token's text and offsets. A token consumed for an anonymous category (S-G3) is a
  leaf. Auxiliary productions introduced by repetition and alternation (S-G4) do not appear: their children are spliced
  into the parent in order.
- **S-P9.** Cycles. Two uses of a production are equivalent when they have the same production, span and progress, and
  their bindings and the labels their constraints read are equal up to renaming of the variables renamed apart under
  S-F5. A derivation never contains a constituent equivalent to itself, so a cycle of empty or unit productions adds no
  derivation and parsing terminates. S-P2 to S-P4 apply to the remaining derivations.

## Generation

- **S-N1.** A sentence generated from start symbol `X` with requested features `F` parses under `X`, and the root
  features of the selected tree unify with `F`.
- **S-N2.** Generation respects every required constraint. Soft groups affect generation only through the parse-time
  penalty of the result.

## Trust

- **S-T1.** A grammar is trusted. Its regexes, imports and predicates run without isolation; an implementation need not
  bound the time a regex takes or restrict which files an import reads.
- **S-T2.** Input text and caller token graphs are untrusted. An implementation validates a caller's graph (S-L1, S-L6)
  and ends every parse within its limits with a stop reason.

## Conformance corpus

Each case is a directory:

| File            | Content                                                                                                  |
|-----------------|----------------------------------------------------------------------------------------------------------|
| `grammar.ug`    | The grammar under test.                                                                                  |
| `inputs.txt`    | One input per line. `\n`, `\r`, `\t` and `\\` are escapes. An empty line is the empty input.             |
| `case.json`     | `{"rules": [rule IDs], "description": text}`, plus an optional `"generate": {"start", "count", "seed"}`. |
| `expected.json` | `{"grammar": {"ok", "error"}, "inputs": [...], "generation": {...}}`                                     |

Each `inputs` entry is `{"input", "outcome", "penalty", "ambiguous", "tree"}`:

- `outcome` is one of `accepted`, `rejected`, `limit`, `cancelled` (S-P5).
- `tree` is a node `{"symbol", "label", "features", "children"}` or a leaf `{"text", "start", "end", "features"}`.
  Feature values are tagged: `{"s": string}`, `{"i": integer}`, `{"f": floating}`, `{"b": boolean}`, `{"v": variable}`,
  `{"m": structure}`, `{"binding": {"text", "features"}}`, `{"path": [names]}`.
- `grammar.error` is `syntax` or `validation` when `ok` is false. A case with an invalid grammar has no `inputs`.
- `generation` is `{"empty": false, "rejected": []}`: generation produced sentences, and none of them failed to parse (
  S-N1).

Expected results are patterns. An object matches when every key it names matches, so omitted keys (for example `tree`,
or a node's `features`) are not compared. Lists must match in length and element by element. Scalars must be equal.

`pending.txt` lists cases whose implementation work has not landed. `exempt.txt` lists rules that grammar text cannot
exercise, with the test that covers each one instead.
