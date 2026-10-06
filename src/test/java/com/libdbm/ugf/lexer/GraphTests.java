package com.libdbm.ugf.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.libdbm.ugf.Result;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.parser.Token;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphTests {

  private static Token token(final String text, final int start, final String category) {
    return new Token(
        text,
        Structure.builder().with("pos", category).build(),
        start,
        start + text.length(),
        category);
  }

  /** S-L6: a token list is a linear graph. */
  @Test
  void testLinearGraph() {
    final var graph =
        TokenSource.of(List.of(token("the", 0, "DT"), token("dogs", 4, "NNS")))
            .tokenize("ignored")
            .orElseThrow();

    assertEquals(3, graph.nodes().size());
    assertEquals(List.of(0, 3, 8), graph.nodes().stream().map(Node::offset).toList());
    final var first = graph.edges(graph.start()).getFirst();
    assertEquals("DT", first.category());
    assertEquals(0, first.start());
    assertEquals(3, first.end());
    final var second = graph.edges(graph.nodes().get(1)).getFirst();
    assertEquals(4, second.start());
    assertTrue(graph.isFinal(graph.nodes().get(2)));
  }

  /** S-L6: alternatives share boundaries. */
  @Test
  void testAlternatives() {
    final var graph =
        TokenSource.alternatives(
                List.of(
                    List.of(token("run", 0, "VB"), token("run", 0, "NN")),
                    List.of(token("fast", 4, "RB"))))
            .tokenize("ignored")
            .orElseThrow();

    assertEquals(3, graph.nodes().size());
    assertEquals(2, graph.edges(graph.start()).size());
    assertEquals(3, graph.edges().size());
  }

  @Test
  void testEmptyTokenList() {
    final var graph = TokenSource.of(List.of()).tokenize("").orElseThrow();

    assertEquals(1, graph.nodes().size());
    assertTrue(graph.isFinal(graph.start()));
  }

  @Test
  void testTokenCategoryIsExplicit() {
    assertEquals("NN", new Token("x", Structure.EMPTY, 0, 1, "NN").category());
    assertNull(new Token("x", Structure.builder().with("cat", "NN").build(), 0, 1).category());
  }

  private static final List<String> STATES = List.of("DEFAULT");

  private static Edge edge(final int id, final Node from, final Node to, final long cost) {
    return new Edge(id, from, to, from.offset(), "x", "X", Structure.EMPTY, cost);
  }

  /** S-L1: costs are non-negative, so a caller cannot lower a derivation's penalty. */
  @Test
  void testNegativeCostRejected() {
    final var from = new Node(0, 0, STATES);
    final var to = new Node(1, 1, STATES);

    assertThrows(IllegalArgumentException.class, () -> edge(0, from, to, -7));
  }

  @Test
  void testTokenSpanOutsideNodesRejected() {
    final var from = new Node(0, 2, STATES);
    final var to = new Node(1, 3, STATES);

    assertThrows(
        IllegalArgumentException.class,
        () -> new Edge(0, from, to, 1, "x", "X", Structure.EMPTY, 0));
  }

  @Test
  void testNodeIdMustBeItsIndex() {
    final var node = new Node(8, 0, STATES);

    assertThrows(
        IllegalArgumentException.class,
        () -> new Graph(List.of(node), List.of(List.of()), Set.of(8)));
  }

  @Test
  void testFinalMustBeANode() {
    final var node = new Node(0, 0, STATES);

    assertThrows(
        IllegalArgumentException.class,
        () -> new Graph(List.of(node), List.of(List.of()), Set.of(3)));
  }

  @Test
  void testEdgeMustMoveForward() {
    // Equal offsets keep the edge itself valid; only its direction in the graph is wrong.
    final var first = new Node(0, 0, STATES);
    final var second = new Node(1, 0, STATES);
    final var back = edge(0, second, first, 0);

    assertThrows(
        IllegalArgumentException.class,
        () -> new Graph(List.of(first, second), List.of(List.of(), List.of(back)), Set.of(1)));
  }

  @Test
  void testEdgeMustLeaveItsListsNode() {
    final var first = new Node(0, 0, STATES);
    final var second = new Node(1, 1, STATES);
    final var third = new Node(2, 2, STATES);
    final var misplaced = edge(0, second, third, 0);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Graph(
                List.of(first, second, third),
                List.of(List.of(misplaced), List.of(), List.of()),
                Set.of(2)));
  }

  @Test
  void testEdgeTargetMustBeInTheGraph() {
    final var first = new Node(0, 0, STATES);
    final var stranger = new Node(1, 1, List.of("OTHER"));
    final var member = new Node(1, 1, STATES);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Graph(
                List.of(first, member),
                List.of(List.of(edge(0, first, stranger, 0)), List.of()),
                Set.of(1)));
  }

  @Test
  void testEdgeIdsUnique() {
    final var first = new Node(0, 0, STATES);
    final var second = new Node(1, 1, STATES);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Graph(
                List.of(first, second),
                List.of(List.of(edge(0, first, second, 0), edge(0, first, second, 0)), List.of()),
                Set.of(1)));
  }

  /** S-L6: alternatives share both boundaries. */
  @Test
  void testAlternativesWithDifferentSpansRejected() {
    final var cells = List.of(List.of(token("dogs", 0, "NNS"), token("dog", 0, "NN")));

    assertInstanceOf(Result.Failure.class, TokenSource.alternatives(cells).tokenize(""));
  }

  @Test
  void testOverlappingTokensRejected() {
    final var tokens = List.of(token("dogs", 0, "NNS"), token("s", 3, "S"));

    assertInstanceOf(Result.Failure.class, TokenSource.of(tokens).tokenize(""));
  }

  @Test
  void testEmptyCellRejected() {
    final var cells = List.of(List.of(token("a", 0, "A")), List.<Token>of());

    assertInstanceOf(Result.Failure.class, TokenSource.alternatives(cells).tokenize(""));
  }
}
