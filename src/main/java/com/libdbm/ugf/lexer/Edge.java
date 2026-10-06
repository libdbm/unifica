package com.libdbm.ugf.lexer;

import com.libdbm.ugf.features.Structure;
import java.util.Objects;

/**
 * A token graph edge: one token from {@code from} to {@code to} (S-L1).
 *
 * @param id position in the graph's edge order
 * @param from the source node
 * @param to the target node; its offset is the token's end and its state stack reflects the token's
 *     transition
 * @param start the character offset where the token starts (after any skipped whitespace)
 * @param text the token text
 * @param category the token category: a named lexeme's symbol, an anonymous lexeme's source text,
 *     {@code error} for an unmatched character (S-L5), or a caller-supplied category
 * @param features the token's features
 * @param cost lexeme cost plus weights of false soft groups in the lexeme's constraints; never
 *     negative (S-L1)
 */
public record Edge(
    int id,
    Node from,
    Node to,
    int start,
    String text,
    String category,
    Structure features,
    long cost) {

  /** The category of an edge for a character no lexeme matches (S-L5). */
  public static final String ERROR = "error";

  /**
   * @throws IllegalArgumentException if the id or cost is negative, or the token does not start
   *     between its two nodes
   */
  public Edge {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(features, "features");
    if (id < 0 || cost < 0) {
      throw new IllegalArgumentException(
          "edge id and cost must not be negative: " + id + ", " + cost);
    }
    if (start < from.offset() || start > to.offset()) {
      throw new IllegalArgumentException(
          "edge " + id + " starts at " + start + ", outside " + from.offset() + ".." + to.offset());
    }
  }

  /** The character offset where the token ends. */
  public int end() {
    return to.offset();
  }
}
