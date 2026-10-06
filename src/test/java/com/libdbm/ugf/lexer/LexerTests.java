package com.libdbm.ugf.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.libdbm.ugf.compiler.Compiler;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LexerTests {

  private static Graph tokenize(final String grammar, final String input) {
    final var source = UnificationGrammarParserFactory.unvalidated(grammar).orElseThrow();
    final var compiled = Compiler.compile(source, Predicates.standard()).orElseThrow();
    return new Lexer(compiled).tokenize(input).orElseThrow();
  }

  /** The categories of the edges leaving the node at {@code offset} in the initial state. */
  private static Set<String> categories(final Graph graph, final int offset) {
    return graph.nodes().stream()
        .filter(node -> node.offset() == offset)
        .flatMap(node -> graph.edges(node).stream())
        .map(Edge::category)
        .collect(Collectors.toSet());
  }

  private static List<String> texts(final Graph graph) {
    return graph.edges().stream().map(Edge::text).toList();
  }

  // Task 25

  /** S-L2: only the longest match at a node becomes an edge. */
  @Test
  void testMaximalMunch() {
    final var graph =
        tokenize("start S; S --> AB; S --> A B; AB --> 'ab'; A --> 'a'; B --> 'b';", "ab");

    assertEquals(List.of("ab"), texts(graph));
    assertEquals(Set.of("AB"), categories(graph, 0));
  }

  /** S-L2: categories that tie on length remain alternatives. */
  @Test
  void testEqualLengthAlternatives() {
    final var graph = tokenize("start S; S --> K; S --> I; K --> 'if'; I --> [a-z]+;", "if");

    assertEquals(Set.of("K", "I"), categories(graph, 0));
  }

  /** Review 2: SIMPLE's "int x;" lexes into its three tokens. */
  @Test
  void testSimpleGrammar() throws Exception {
    final var source =
        UnificationGrammarParserFactory.unvalidated(Path.of("src/test/resources/simple.ug"))
            .orElseThrow();
    final var compiled = Compiler.compile(source, Predicates.standard()).orElseThrow();

    final var graph = new Lexer(compiled).tokenize("int x;").orElseThrow();

    assertTrue(categories(graph, 0).contains("KW_int"), categories(graph, 0).toString());
    assertEquals(Set.of("Identifier"), categories(graph, 3));
    assertTrue(categories(graph, 5).contains("SEMI"));
    assertTrue(graph.edges().stream().allMatch(edge -> edge.text().length() <= 3));
  }

  /** S-L5: an unmatched character becomes a one-character error edge. */
  @Test
  void testErrorEdge() {
    final var graph = tokenize("start S; S --> A B; A --> 'a'; B --> 'b';", "a#b");

    assertEquals(Set.of(Edge.ERROR), categories(graph, 1));
    assertEquals(List.of("a", "#", "b"), texts(graph));
  }

  /** S-L4: whitespace is skipped between tokens only. */
  @Test
  void testWhitespace() {
    final var graph = tokenize("start S; S --> K X; K --> 'a b'; X --> 'c';", "a b  c ");

    assertEquals(List.of("a b", "c"), texts(graph));
    final var second = graph.edges().get(1);
    assertEquals(5, second.start());
    assertEquals(6, second.end());
    assertTrue(graph.nodes().stream().anyMatch(graph::isFinal));
  }

  @Test
  void testEmptyInput() {
    final var graph = tokenize("start S; S --> A?; A --> 'a';", "");

    assertEquals(1, graph.nodes().size());
    assertTrue(graph.isFinal(graph.start()));
  }

  @Test
  void testNodesInTopologicalOrder() {
    final var graph = tokenize("start S; S --> W+; W --> [a-z]+;", "ab cd ef");

    for (final var edge : graph.edges()) {
      assertTrue(edge.from().id() < edge.to().id());
    }
  }

  // Task 26

  @Test
  void testStateConstrainedLexeme() {
    final var grammar = "start s; s --> open b; open --> '<' ==> IN; b --> {IN} 'y';";

    assertEquals(List.of("<", "y"), texts(tokenize(grammar, "<y")));
    assertEquals(Set.of(Edge.ERROR), categories(tokenize(grammar, "y"), 0));
  }

  /** S-L3: equal spans with different transitions lead to different nodes. */
  @Test
  void testDivergentStates() {
    final var graph =
        tokenize(
            "start s; s --> a x; s --> b y; a --> 'k' ==> P; b --> 'k' ==> Q; x --> {P} 'z'; y --> {Q} 'z';",
            "kz");

    final var targets =
        graph.edges(graph.start()).stream().map(Edge::to).collect(Collectors.toSet());
    assertEquals(2, targets.size());
    assertEquals(Set.of("x", "y"), categories(graph, 1));
  }

  @Test
  void testTransitionPopAndReset() {
    final var graph =
        tokenize(
            "start s; s --> o c r; o --> '(' ==> IN; c --> {IN} ')' ==> _; r --> '!' ==> !DONE;",
            "()!");

    final var last = graph.nodes().getLast();
    assertEquals(List.of("DONE"), last.states());
    assertEquals(List.of("DEFAULT"), graph.nodes().get(2).states());
  }

  /** A longest match that fails a required constraint does not suppress a shorter valid one. */
  @Test
  void testConstrainedLongestDoesNotSuppress() {
    final var graph =
        tokenize(
            "start S; S --> W; S --> A; W --> [a-z]+:w where starts_with(w, 'x'); A --> 'ab';",
            "abc");

    assertEquals(Set.of("A"), categories(graph, 0));
  }

  @Test
  void testLabelsAndTextBoundForConstraints() {
    final var grammar =
        "start S; S --> W; W --> [a-z]+:w where starts_with(w, 'a'), ends_with(W, 'c');";

    assertEquals(Set.of("W"), categories(tokenize(grammar, "abc"), 0));
    assertEquals(Set.of(Edge.ERROR), categories(tokenize(grammar, "bbc"), 0));
  }

  /** S-C4: a false soft group in a lexeme's constraints becomes edge cost. */
  @Test
  void testSoftConstraintCost() {
    final var graph =
        tokenize("start S; S --> W; W --> [a-z]+:w where starts_with(w, 'q'):4 @1;", "abc");

    assertEquals(5, graph.edges().getFirst().cost());
  }

  /** S-C7: lexical predicates read the token's start state. */
  @Test
  void testLexicalPredicate() {
    final var grammar =
        "start S; S --> A; S --> B; S --> C;"
            + " A --> 'y' where (in_state('DEFAULT') | equals('a', 'b'));"
            + " B --> 'z' where !in_state('IN'); C --> 'w' where !in_state('DEFAULT');";

    assertEquals(Set.of("A"), categories(tokenize(grammar, "y"), 0));
    assertEquals(Set.of("B"), categories(tokenize(grammar, "z"), 0));
    assertEquals(Set.of(Edge.ERROR), categories(tokenize(grammar, "w"), 0));
  }

  /** S-L4: declared skip categories are discarded between tokens, like whitespace. */
  @Test
  void testSkipCategories() {
    final var grammar =
        "start S; skip Block, Line; S --> W+; W --> [a-z]+;"
            + " Block --> '/*' (?:[^*]|[*]+[^*/])* [*]+ '/'; Line --> '//' [^\\n]*;";

    final var graph = tokenize(grammar, "ab /* x */ cd // y\n ef/**/gh");

    assertEquals(List.of("ab", "cd", "ef", "gh"), texts(graph));
    assertTrue(graph.nodes().stream().anyMatch(graph::isFinal));
  }

  @Test
  void testSkipRespectsStates() {
    final var grammar =
        "start S; skip C; S --> o W W; o --> '<' ==> IN; W --> [a-z]+; C --> {IN} '#' [a-z]*;";

    assertEquals(List.of("<", "ab", "cd"), texts(tokenize(grammar, "<ab #zz cd")));
    assertEquals(
        Set.of(Edge.ERROR),
        categories(
            tokenize("start S; skip C; S --> W W; W --> [a-z]+; C --> {IN} '#' [a-z]*;", "ab #zz"),
            2));
  }

  @Test
  void testLexemeFeaturesOnEdge() {
    final var graph = tokenize("start S; S --> N; N{num: sg} --> 'dog';", "dog");

    assertEquals("sg", graph.edges().getFirst().features().get("num").display());
  }

  // Behaviour carried over from the 1.x LexicalAnalyzerTests (task 35)

  @Test
  void testTokenPositions() {
    final var edges = tokenize("start S; S --> A B; A --> [a-z]+; B --> [0-9]+;", "ab  12").edges();

    assertEquals(0, edges.get(0).start());
    assertEquals(2, edges.get(0).end());
    assertEquals(4, edges.get(1).start());
    assertEquals(6, edges.get(1).end());
  }

  /** A pattern that can match the empty string never produces a zero-length token. */
  @Test
  void testZeroLengthMatchIgnored() {
    final var graph = tokenize("start S; S --> A B; A --> [a]*; B --> 'b';", "b");

    assertEquals(List.of("b"), texts(graph));
  }

  @Test
  void testWhitespaceOnlyInput() {
    final var graph = tokenize("start S; S --> A?; A --> 'a';", "   \n\t ");

    assertTrue(graph.edges().isEmpty());
    assertTrue(graph.nodes().stream().anyMatch(graph::isFinal));
  }

  @Test
  void testWhitespaceNoneKeepsSpaces() {
    final var graph =
        tokenize("start S; whitespace none; S --> W (SP W)*; W --> [a-z]+; SP --> [ ]+;", "ab  cd");

    assertEquals(List.of("ab", "  ", "cd"), texts(graph));
  }

  @Test
  void testMathTokens() {
    final var grammar =
        "start e; e --> t (op t)*; t --> number | variable | lparen e rparen | op number;"
            + " number --> [0-9]+ ('.' [0-9]+)?; variable --> [a-z]+; op --> [-+*/]; lparen --> '('; rparen --> ')';";

    final var graph = tokenize(grammar, "(x + 2) * 3.14 - -1");

    assertEquals(List.of("(", "x", "+", "2", ")", "*", "3.14", "-", "-", "1"), texts(graph));
    assertEquals(Set.of("number"), categories(graph, 9));
  }

  @Test
  void testXmlElementTokens() {
    final var grammar =
        "start doc; doc --> lt name gt text ltc name gt | lt name slashgt;"
            + " lt --> '<' ==> TAG; ltc --> '</' ==> TAG; gt --> {TAG} '>' ==> _; slashgt --> {TAG} '/>' ==> _;"
            + " name --> {TAG} [a-z]+; text --> {DEFAULT} [^<]+;";

    assertEquals(
        List.of("<", "div", ">", "hello", "</", "div", ">"),
        texts(tokenize(grammar, "<div>hello</div>")));
    assertEquals(List.of("<", "br", "/>"), texts(tokenize(grammar, "<br/>")));
    // Longest match: "</" is one token, not "<" then "/".
    assertEquals(Set.of("ltc"), categories(tokenize(grammar, "<a>x</a>"), 4));
  }

  @Test
  void testCDATAContent() {
    final var grammar =
        "start doc; doc --> open body close; open --> '<![CDATA[' ==> CDATA;"
            + " body --> {CDATA} (?:[^\\]]|[\\]](?![\\]]>))+; close --> {CDATA} ']]>' ==> _;";

    assertEquals(
        List.of("<![CDATA[", "a < b & ] c", "]]>"),
        texts(tokenize(grammar, "<![CDATA[a < b & ] c]]>")));
  }

  @Test
  void testLexicalAlternationAndRepetition() {
    final var graph = tokenize("start S; S --> K+; K --> ('ab' | 'cd')+ [0-9]?;", "abcd1 cdcd");

    assertEquals(List.of("abcd1", "cdcd"), texts(graph));
  }
}
