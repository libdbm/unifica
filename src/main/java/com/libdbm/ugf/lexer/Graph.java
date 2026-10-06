package com.libdbm.ugf.lexer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A token graph (S-L1): a directed acyclic graph of tokens. {@link #nodes()} is a topological order
 * (by offset), the first node is the start, and every path from the start to a final node is one
 * tokenization of the input. The constructor enforces these invariants, so a graph a caller builds
 * by hand cannot break the parser.
 *
 * @param nodes every node, in topological order; a node's id is its index
 * @param outgoing outgoing edges by node id
 * @param finals ids of nodes at the end of the input
 */
public record Graph(List<Node> nodes, List<List<Edge>> outgoing, Set<Integer> finals) {

  /**
   * @throws IllegalArgumentException if a node's id is not its index, an edge does not leave the
   *     node whose list holds it or lead to a later node of this graph, two edges share an id, or a
   *     final id is not a node
   */
  public Graph {
    nodes = List.copyOf(nodes);
    final var copied = new ArrayList<List<Edge>>();
    outgoing.forEach(edges -> copied.add(List.copyOf(edges)));
    outgoing = List.copyOf(copied);
    finals = Set.copyOf(finals);
    if (nodes.isEmpty() || outgoing.size() != nodes.size()) {
      throw new IllegalArgumentException("a graph needs a start node and one edge list per node");
    }
    validate(nodes, outgoing, finals);
  }

  private static void validate(
      final List<Node> nodes, final List<List<Edge>> outgoing, final Set<Integer> finals) {
    for (var index = 0; index < nodes.size(); index++) {
      if (nodes.get(index).id() != index) {
        throw new IllegalArgumentException(
            "node " + nodes.get(index).id() + " is at index " + index);
      }
    }
    final var ids = new HashSet<Integer>();
    for (var index = 0; index < nodes.size(); index++) {
      for (final var edge : outgoing.get(index)) {
        final var to = edge.to().id();
        if (!edge.from().equals(nodes.get(index))) {
          throw new IllegalArgumentException("edge " + edge.id() + " does not leave node " + index);
        }
        if (to <= index || to >= nodes.size() || !edge.to().equals(nodes.get(to))) {
          throw new IllegalArgumentException(
              "edge " + edge.id() + " does not lead to a later node of the graph");
        }
        if (!ids.add(edge.id())) {
          throw new IllegalArgumentException("edge id " + edge.id() + " is used twice");
        }
      }
    }
    for (final var id : finals) {
      if (id < 0 || id >= nodes.size()) {
        throw new IllegalArgumentException("final " + id + " is not a node");
      }
    }
  }

  public Node start() {
    return nodes.getFirst();
  }

  public List<Edge> edges(final Node node) {
    return outgoing.get(node.id());
  }

  public boolean isFinal(final Node node) {
    return finals.contains(node.id());
  }

  /** Every edge, in node order. */
  public List<Edge> edges() {
    return outgoing.stream().flatMap(List::stream).toList();
  }
}
