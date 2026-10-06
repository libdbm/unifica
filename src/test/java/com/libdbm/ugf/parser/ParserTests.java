package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.compiler.Compiler;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.lexer.TokenSource;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ParserTests {

  private static Parser parser(final String source, final Predicates predicates) {
    final var grammar = UnificationGrammarParserFactory.unvalidated(source).orElseThrow();
    return Parser.of(Compiler.compile(grammar, predicates).orElseThrow());
  }

  private static Parser parser(final String source) {
    return parser(source, Predicates.standard());
  }

  private static boolean accepts(final Parser parser, final String input) {
    return parser.parse(input).outcome() == Outcome.ACCEPTED;
  }

  private static ParseTree.Node root(final ParseResult result) {
    return assertInstanceOf(ParseTree.Node.class, result.tree());
  }

  private static String symbol(final ParseTree tree) {
    return ((ParseTree.Node) tree).symbol();
  }

  // Task 27: recognition with both arrival orders

  /** Review 2: S -> A A with nullable A. */
  @Test
  void testRepeatedNullable() {
    final var parser = parser("start S; S --> A A; A --> E?; E --> 'a';");

    assertTrue(accepts(parser, ""));
    assertTrue(accepts(parser, "a"));
    assertTrue(accepts(parser, "aa"));
    assertFalse(accepts(parser, "aaa"));
  }

  @Test
  void testNullableRecursion() {
    final var parser = parser("start S; S --> S S; S --> T?; T --> 'a';");

    assertTrue(accepts(parser, ""));
    assertTrue(accepts(parser, "a"));
    assertTrue(accepts(parser, "a a"));
  }

  @Test
  void testNullableOperators() {
    final var parser = parser("start S; S --> A* 'x'; A --> B?; B --> 'a';");

    assertTrue(accepts(parser, "x"));
    assertTrue(accepts(parser, "a x"));
    assertTrue(accepts(parser, "a a x"));
    assertFalse(accepts(parser, "a a"));
  }

  @Test
  void testCallerSuppliedAlternatives() {
    final var parser = parser("start S; S --> NN VB; NN --> 'zz'; VB --> 'yy';");
    final var tokens =
        List.of(
            List.of(
                new Token("dogs", Structure.EMPTY, 0, 4, "NN"),
                new Token("dogs", Structure.EMPTY, 0, 4, "VB")),
            List.of(
                new Token("run", Structure.EMPTY, 5, 8, "VB"),
                new Token("run", Structure.EMPTY, 5, 8, "NN")));

    final var result = parser.parse(TokenSource.alternatives(tokens).tokenize("").orElseThrow());

    assertEquals(Outcome.ACCEPTED, result.outcome());
    assertEquals(
        List.of("NN", "VB"), root(result).children().stream().map(ParserTests::symbol).toList());
    assertFalse(result.ambiguous());
  }

  /** LEX-9: tokenize and parse use the same lexer. */
  @Test
  void testTokenizeMatchesParse() {
    final var created =
        ParserFactory.create(
            UnificationGrammarParserFactory.unvalidated(
                    "start S; S --> K | I; K --> 'if'; I --> [a-z]+;")
                .orElseThrow());
    final var parser = created.orElseThrow();

    final var graph = parser.tokenize("if").orElseThrow();

    assertEquals(2, graph.edges().size());
    assertEquals(graph.edges().size(), parser.parse("if").statistics().edges());
  }

  @Test
  void testLexicalStartSymbol() {
    assertTrue(accepts(parser("start A; A --> 'y';"), "y"));
  }

  // Task 28: forest, selection, tree shape

  @Test
  void testTieBreakDeclarationOrder() {
    final var first = parser("start S; S --> A; S --> B; A --> X; B --> X; X --> 'x';").parse("x");
    final var second = parser("start S; S --> B; S --> A; A --> X; B --> X; X --> 'x';").parse("x");

    assertEquals("A", symbol(root(first).children().getFirst()));
    assertTrue(first.ambiguous());
    assertEquals("B", symbol(root(second).children().getFirst()));
  }

  @Test
  void testUnambiguous() {
    assertFalse(parser("start S; S --> A B; A --> 'a'; B --> 'b';").parse("a b").ambiguous());
  }

  /** "a" under S -> A A with nullable A has two derivations. */
  @Test
  void testNullableAmbiguity() {
    final var parser = parser("start S; S --> A A; A --> E?; E --> 'a';");

    assertTrue(parser.parse("a").ambiguous());
    assertFalse(parser.parse("aa").ambiguous());
    assertFalse(parser.parse("").ambiguous());
  }

  @Test
  void testEmptyConstituentPosition() {
    final var result = parser("start S; S --> 'a' E 'b'; E --> F?; F --> 'f';").parse("a b");

    final var empty = (ParseTree.Node) root(result).children().get(1);
    assertEquals("E", empty.symbol());
    assertEquals(1, empty.start());
    assertEquals(1, empty.end());
  }

  /**
   * S-P8: named lexical tokens are nodes with a leaf, anonymous ones are leaves, auxiliaries
   * vanish.
   */
  @Test
  void testTreeShape() {
    final var tree =
        root(
            parser("start S; S --> N:n '&' L; L --> W+; N --> [0-9]+; W --> [a-z]+;")
                .parse("12 & ab cd"));

    assertEquals(3, tree.children().size());
    final var number = (ParseTree.Node) tree.children().get(0);
    assertEquals("N", number.symbol());
    assertEquals("n", number.label());
    assertEquals(new ParseTree.Leaf("12", 0, 2), number.children().getFirst());
    assertEquals(new ParseTree.Leaf("&", 3, 4), tree.children().get(1));
    final var list = (ParseTree.Node) tree.children().get(2);
    assertEquals(List.of("W", "W"), list.children().stream().map(ParserTests::symbol).toList());
    assertEquals(10, list.end());
  }

  /** S-P6: production order changes neither acceptance nor the minimum penalty. */
  @Test
  void testRuleOrderInvariance() {
    final var lines =
        new ArrayList<>(
            List.of(
                "S --> A B where equals('x', 'y'):3;",
                "S --> C;",
                "A --> 'a';",
                "A --> 'a' 'a';",
                "B --> 'a';",
                "B --> 'a' 'a';",
                "C --> A A A where equals('x', 'y'):2;",
                "C --> B B B B @9;"));
    final var random = new Random(11);
    final var inputs = List.of("a a a", "a a a a", "a a", "a");
    final List<String> expected = new ArrayList<>();
    for (var round = 0; round < 20; round++) {
      Collections.shuffle(lines, random);
      final var parser = parser("start S;\n" + String.join("\n", lines));
      final var observed =
          inputs.stream()
              .map(
                  input -> {
                    final var result = parser.parse(input);
                    return result.outcome() + ":" + result.penalty();
                  })
              .toList();
      if (expected.isEmpty()) {
        expected.addAll(observed);
      }
      assertEquals(expected, observed, "round " + round + " with " + lines);
    }
    assertEquals("ACCEPTED:2", expected.getFirst());
  }

  // Task 29: features

  @Test
  void testAgreement() {
    final var parser =
        parser(
            "start S; S{num: X} --> N{num: X} V{num: X};"
                + " N{num: sg} --> 'dog'; N{num: pl} --> 'dogs'; V{num: sg} --> 'runs'; V{num: pl} --> 'run';");

    assertTrue(accepts(parser, "dog runs"));
    assertTrue(accepts(parser, "dogs run"));
    assertFalse(accepts(parser, "dog run"));
    assertFalse(accepts(parser, "dogs runs"));
    assertEquals("sg", root(parser.parse("dog runs")).features().get("num").display());
  }

  @Test
  void testSyntacticAgreement() {
    final var parser =
        parser(
            "start S; S --> NP{num: X} VP{num: X}; NP{num: Y} --> D N{num: Y};"
                + " VP{num: Y} --> V{num: Y}; D --> 'the'; N{num: sg} --> 'dog'; N{num: pl} --> 'dogs';"
                + " V{num: sg} --> 'runs'; V{num: pl} --> 'run';");

    assertTrue(accepts(parser, "the dogs run"));
    assertFalse(accepts(parser, "the dogs runs"));
  }

  // Task 30: constraints

  /** Review 2: two splits of "aaa" are not merged; only left = "aa" is valid. */
  @Test
  void testConstraintVisibleSplit() {
    final var result =
        parser(
                "start S; S --> A:left B:right 'c' where matches(left, 'aa');"
                    + " A --> 'a'; A --> 'a' 'a'; B --> 'a'; B --> 'a' 'a';")
            .parse("aaac");

    assertEquals(Outcome.ACCEPTED, result.outcome());
    final var left = (ParseTree.Node) root(result).children().getFirst();
    assertEquals("left", left.label());
    assertEquals(2, left.end() - left.start());
    assertFalse(result.ambiguous());
  }

  /** Review 3: a predicate is called once per distinct completed state, not once per derivation. */
  @Test
  void testOneEvaluationPerCompletion() {
    final var count = new AtomicInteger();
    final var predicates =
        Predicates.builder()
            .builtins()
            .add(
                "accept",
                0,
                0,
                Predicates.Phase.SYNTACTIC,
                (environment, args) -> count.incrementAndGet() > 0)
            .build();
    final var parser =
        parser(
            "start S; S --> A:left C where accept(); A --> B; A --> D; B --> 'a'; D --> 'a'; C --> 'c';",
            predicates);

    final var result = parser.parse("a c");

    assertEquals(Outcome.ACCEPTED, result.outcome());
    assertEquals(1, count.get());
    assertEquals(1, result.statistics().calls());
  }

  @Test
  void testSoftGroups() {
    final var parser =
        parser(
            "start S; S --> P where (equals('a', 'a'), equals('a', 'b')):7;"
                + " S --> Q where equals('a', 'b'):3, equals('c', 'd'):4;"
                + " S --> R where equals('a', 'a'), (equals('a', 'b'):3, equals('c', 'd'):4);"
                + " S --> T where equals('a', 'b'), equals('c', 'c'):2; P --> 'p'; Q --> 'q'; R --> 'r'; T --> 't';");

    assertEquals(7, parser.parse("p").penalty());
    assertEquals(7, parser.parse("q").penalty());
    assertEquals(7, parser.parse("r").penalty());
    assertEquals(Outcome.REJECTED, parser.parse("t").outcome());
  }

  @Test
  void testMixedPhase() {
    final var parser =
        parser(
            "start S; S --> A; S --> B; S --> C;"
                + " A --> 'y' where (in_state('DEFAULT') | equals('a', 'b'));"
                + " B --> 'z' where !in_state('IN'); C --> 'w' where !in_state('DEFAULT');");

    assertTrue(accepts(parser, "y"));
    assertTrue(accepts(parser, "z"));
    assertFalse(accepts(parser, "w"));
  }

  /**
   * S-C7: a lexical predicate in a syntactic production reads the state where the constituent
   * starts.
   */
  @Test
  void testLexicalPredicateInSyntacticProduction() {
    final var parser =
        parser(
            "start s; s --> o t; s --> t; o --> '<' ==> IN; t --> W where in_state('IN'); W --> [a-z]+;");

    assertTrue(accepts(parser, "<ab"));
    assertFalse(accepts(parser, "ab"));
  }

  @Test
  void testStringBuiltinsOnLabels() {
    final var parser =
        parser(
            "start S; S --> W:w '!' where starts_with(w, 'ab');"
                + " S --> P:p '?' where starts_with(p, 'ab'); P --> W W; W --> [a-z]+;");

    assertTrue(accepts(parser, "abc !"));
    assertFalse(accepts(parser, "xbc !"));
    assertTrue(accepts(parser, "ab cd ?"));
    assertFalse(accepts(parser, "xb cd ?"));
  }

  @Test
  void testProductionCost() {
    final var result = parser("start S; S --> A @2; S --> B @1; A --> 'x'; B --> 'x';").parse("x");

    assertEquals(1, result.penalty());
    assertEquals("B", symbol(root(result).children().getFirst()));
    assertFalse(result.ambiguous());
  }

  @Test
  void testPenaltyOverflow() {
    final var weight = Long.MAX_VALUE;
    final var result =
        parser("start S; S --> A A @" + weight + "; A --> 'a' @" + weight + ";").parse("aa");

    assertEquals(Outcome.LIMIT, result.outcome());
  }

  // Task 32: concurrency

  @Test
  void testConcurrentParses() throws Exception {
    final var parser = parser("start S; S --> S S; S --> A; A --> 'a';");
    final var inputs = Arrays.asList("a", "a a", "a a a", "a a a a", "a a a a a", "a a a a a a");
    final var expected =
        inputs.stream()
            .map(input -> parser.parse(input).penalty() + ":" + parser.parse(input).tree())
            .toList();
    final var executor = Executors.newFixedThreadPool(8);
    try {
      final var futures = new ArrayList<Future<List<String>>>();
      for (var thread = 0; thread < 8; thread++) {
        futures.add(
            executor.submit(
                () ->
                    inputs.stream()
                        .map(
                            input ->
                                parser.parse(input).penalty() + ":" + parser.parse(input).tree())
                        .toList()));
      }
      for (final var future : futures) {
        assertEquals(expected, future.get());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  // Framework fixes found by the grammar ports (task 32a)

  @Test
  void testTopLevelAlternatives() {
    final var parser = parser("start s; s --> 'a' | b; b --> 'x' | 'y';");

    assertTrue(accepts(parser, "a"));
    assertTrue(accepts(parser, "x"));
    assertTrue(accepts(parser, "y"));
    assertEquals("b", symbol(root(parser.parse("y")).children().getFirst()));
  }

  @Test
  void testEmptyProduction() {
    final var parser = parser("start s; s --> 'a' e 'b'; e --> | 'c';");

    assertTrue(accepts(parser, "a b"));
    assertTrue(accepts(parser, "a c b"));
  }

  /** S-G4: parenthesised groups may be sequences, alone or as alternation options. */
  @Test
  void testGroupedSequences() {
    final var list = parser("start s; s --> A (',' A)*; A --> [a-z]+;");
    final var choice = parser("start s; s --> A ('x' | 'y' A); A --> [a-z]+;");
    final var optional =
        parser("start s; s --> 'if' A ('else' A)?; A --> (?!(?:if|else)(?![a-z]))[a-z]+;");

    assertTrue(accepts(list, "a, b, c"));
    assertEquals(
        List.of("A", "','", "A", "','", "A"),
        root(list.parse("a, b, c")).children().stream()
            .map(
                child ->
                    child instanceof ParseTree.Node node ? node.symbol() : "'" + child.text() + "'")
            .toList());
    assertTrue(accepts(choice, "a x"));
    assertTrue(accepts(choice, "a y b"));
    assertFalse(accepts(choice, "a y"));
    assertTrue(accepts(optional, "if a else b"));
    assertTrue(accepts(optional, "if a"));
  }

  @Test
  void testLexicalGroupsAndRegexForms() {
    final var parser =
        parser("start s; s --> T; T --> 'a' (?:[^)])* (?:[a-z]+:){2,} ('!' | '?' '?');");

    assertTrue(accepts(parser, "abx:y:!"));
    assertTrue(accepts(parser, "ax:y:z:??"));
    assertFalse(accepts(parser, "a)x:y:!"));
    assertFalse(accepts(parser, "ax:!"));
  }

  @Test
  void testLookbehindSeesBeforeToken() {
    assertTrue(accepts(parser("start s; s --> A X; A --> 'a'; X --> (?<=a)[b];"), "ab"));
    assertFalse(accepts(parser("start s; s --> C X; C --> 'c'; X --> (?<=a)[b];"), "cb"));
  }

  /** S-L4: a grammar can replace or disable the default whitespace. */
  @Test
  void testWhitespaceStatement() {
    final var lines =
        parser("start s; whitespace [ \\t]+; s --> W NL W; NL --> [\\n]; W --> [a-z]+;");
    final var tight = parser("start s; whitespace none; s --> 'a' 'b';");

    assertTrue(accepts(lines, "ab \n cd"));
    assertFalse(accepts(parser("start s; s --> W NL W; NL --> [\\n]; W --> [a-z]+;"), "ab\ncd"));
    assertTrue(accepts(tight, "ab"));
    assertFalse(accepts(tight, "a b"));
  }

  /** S-L3: ==> ^X replaces the top of the state stack. */
  @Test
  void testReplaceTransition() {
    final var parser =
        parser("start s; s --> o r c; o --> '<' ==> A; r --> {A} 'x' ==> ^B; c --> {B} '>' ==> _;");

    assertTrue(accepts(parser, "<x>"));
    assertFalse(accepts(parser, "<x x>"));
  }

  /** S-L4: a skip category's constraints decide whether a match is discarded. */
  @Test
  void testSkipConstraints() {
    final var parser =
        parser("start S; skip H; S --> W+; W --> [a-z]+; H --> '#' [^\\n]* where at_char_start();");

    assertTrue(accepts(parser, "#note\nab"));
    assertFalse(accepts(parser, "a #b"));
  }

  /** S-F5: features written on a child are not copied into the parent. */
  @Test
  void testSiblingFeaturesIndependent() {
    final var parser =
        parser("start s; s --> w{v: no} w{v: yes}; w{v: no} --> 'n'; w{v: yes} --> 'k';");

    assertTrue(accepts(parser, "n k"));
    assertTrue(root(parser.parse("n k")).features().isEmpty());
  }

  /**
   * S-F5: a constant on a non-final child does not constrain the parent's variable (Go port
   * reproduction).
   */
  @Test
  void testVariablesLinkFeatures() {
    final var parser =
        parser(
            "start s; s --> 'v' a{nl: yes}; a{nl: E} --> l{nl: no} b{nl: E};"
                + " l{nl: E} --> I{nl: E}; b{nl: E} --> I{nl: E};"
                + " I{nl: yes} --> [a-z]+ (?=[\\n]|$); I{nl: no} --> [a-z]+ (?![\\n]|$);");

    assertTrue(accepts(parser, "v x y"));
  }

  /** S-G4, S-F5: X{f: V}? means the same as its expansion; the binding survives the lowering. */
  @Test
  void testOptionalKeepsBinding() {
    final var parser =
        parser(
            "start s; s --> w{open: no}; w{open: O} --> 'w' x{open: O}?;"
                + " x{open: yes} --> 'a'; x{open: no} --> 'b';");

    assertFalse(accepts(parser, "w a"));
    assertTrue(accepts(parser, "w b"));
    assertTrue(accepts(parser, "w"));
  }

  @Test
  void testRepetitionSharesVariables() {
    final var parser =
        parser(
            "start s; s --> x{v: V}+ y{v: V}; x{v: a} --> 'xa'; x{v: b} --> 'xb';"
                + " y{v: a} --> 'ya'; y{v: b} --> 'yb';");

    assertTrue(accepts(parser, "xa xa ya"));
    assertFalse(accepts(parser, "xa xb ya"));
  }

  /** at_end() lets an inner rule match the end of input, as ANTLR's EOF does. */
  @Test
  void testAtEnd() {
    final var parser =
        parser("start S; S --> L*; L --> 'a' E; E --> NL; E --> where at_end(); NL --> ';';");

    assertTrue(accepts(parser, "a ; a ;"));
    assertTrue(accepts(parser, "a ; a"));
    assertFalse(accepts(parser, "a a"));
  }

  /** before() looks at the next token, as ANTLR's LT(1) predicates do. */
  @Test
  void testBefore() {
    final var parser =
        parser(
            "start S; S --> A E (B | C); E --> SEMI; E --> where before('B'); "
                + "A --> 'a'; B --> 'b'; C --> 'c'; SEMI --> ';';");

    assertTrue(accepts(parser, "a b"));
    assertTrue(accepts(parser, "a ; c"));
    assertFalse(accepts(parser, "a c"));
  }

  /** Review M1: a deep derivation is selected and built without recursion. */
  @Test
  void testDeepDerivationOnSmallStack() throws InterruptedException {
    final var parser = parser("start S; S --> 'x' S | 'y';");
    final var input = "x ".repeat(5_000) + "y";
    final var outcome = new AtomicReference<Object>();
    final var thread =
        new Thread(
            null,
            () -> {
              try {
                outcome.set(parser.parse(input).outcome());
              } catch (final StackOverflowError error) {
                outcome.set(error);
              }
            },
            "small-stack",
            256 * 1024);
    thread.start();
    thread.join();

    assertEquals(Outcome.ACCEPTED, outcome.get());
  }
}
