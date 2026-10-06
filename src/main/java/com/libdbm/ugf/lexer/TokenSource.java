package com.libdbm.ugf.lexer;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.parser.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Produces the token graph for an input (S-L1). The compiled {@link Lexer} is one source;
 * caller-supplied tokens, such as POS-tagged words, are another (S-L6).
 */
@FunctionalInterface
public interface TokenSource {

  /** The error code for caller tokens that do not form a token graph. */
  String TOKENS = "graph.tokens";

  /**
   * A source that ignores its input and yields a linear graph of {@code tokens} (S-L6). Tokens must
   * be in order and must not overlap; otherwise tokenizing fails with {@link #TOKENS}.
   */
  static TokenSource of(final List<Token> tokens) {
    return input -> linear(tokens.stream().map(List::of).toList());
  }

  /**
   * A source that ignores its input and yields a graph where {@code cells.get(i)} are alternative
   * tokens sharing the boundaries of position {@code i} (S-L6). Every cell must be non-empty, its
   * tokens must have the same start and end, and cells must be in order without overlapping;
   * otherwise tokenizing fails with {@link #TOKENS}.
   */
  static TokenSource alternatives(final List<List<Token>> cells) {
    return input -> linear(cells);
  }

  private static Result<Graph, ErrorDetails> linear(final List<List<Token>> cells) {
    final var problems = new ArrayList<String>();
    var previous = Integer.MIN_VALUE;
    for (var index = 0; index < cells.size(); index++) {
      final var cell = cells.get(index);
      if (cell.isEmpty()) {
        problems.add("cell " + index + " has no tokens");
        continue;
      }
      final var first = cell.getFirst();
      if (first.start() < previous) {
        problems.add("cell " + index + " starts at " + first.start() + ", before " + previous);
      }
      if (first.end() < first.start()) {
        problems.add("cell " + index + " ends before it starts");
      }
      for (final var token : cell) {
        if (token.start() != first.start() || token.end() != first.end()) {
          problems.add("cell " + index + " has tokens with different spans");
          break;
        }
      }
      previous = Math.max(previous, first.end());
    }
    if (!problems.isEmpty()) {
      return Result.failure(
          new ErrorDetails(TOKENS, "Caller tokens do not form a token graph", problems));
    }
    final var states = List.of("DEFAULT");
    final var nodes = new ArrayList<Node>();
    final var outgoing = new ArrayList<List<Edge>>();
    nodes.add(new Node(0, cells.isEmpty() ? 0 : cells.getFirst().getFirst().start(), states));
    var id = 0;
    for (final var cell : cells) {
      final var from = nodes.getLast();
      final var to = new Node(nodes.size(), cell.getFirst().end(), states);
      nodes.add(to);
      final var edges = new ArrayList<Edge>();
      for (final var token : cell) {
        // Caller tokens carry their own offsets; the edge text is the token text.
        edges.add(
            new Edge(
                id++,
                from,
                to,
                token.start(),
                token.text(),
                token.category(),
                token.features(),
                0));
      }
      outgoing.add(edges);
    }
    outgoing.add(List.of());
    return Result.success(new Graph(nodes, outgoing, Set.of(nodes.size() - 1)));
  }

  Result<Graph, ErrorDetails> tokenize(String input);
}
