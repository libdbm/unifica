package com.libdbm.ugf.parser;

import com.libdbm.ugf.features.Structure;
import java.util.List;

/** Parse tree node representing the result of a successful parse. */
public sealed interface ParseTree {

  /** Get the start position in the input */
  default int start() {
    if (this instanceof Node n) {
      return n.children().isEmpty() ? 0 : n.children().getFirst().start();
    } else if (this instanceof Leaf l) {
      return l.start();
    }
    return 0;
  }

  /** Get the end position in the input */
  default int end() {
    if (this instanceof Node n) {
      return n.children().isEmpty() ? 0 : n.children().getLast().end();
    } else if (this instanceof Leaf l) {
      return l.end();
    }
    return 0;
  }

  /** Get the text span covered by this tree */
  default String text(final String input) {
    return input.substring(start(), end());
  }

  /** The texts of this tree's leaves, concatenated: a token node's text, or a leaf's own. */
  default String text() {
    return switch (this) {
      case Leaf leaf -> leaf.text();
      case Node node -> {
        final var builder = new StringBuilder();
        node.children().forEach(child -> builder.append(child.text()));
        yield builder.toString();
      }
    };
  }

  /** Display the parse tree in a human-readable format. */
  default String display() {
    final var sb = new StringBuilder();
    display(sb, 0);
    return sb.toString();
  }

  /** Helper for recursive tree display. */
  default void display(final StringBuilder sb, final int indent) {
    final var str = "  ".repeat(indent);

    if (this
        instanceof
        Node(
            String symbol,
            String label,
            List<ParseTree> children,
            Structure features,
            int start,
            int end)) {
      sb.append(str).append(symbol);

      if (label != null) {
        sb.append(":").append(label);
      }

      if (!features.isEmpty()) {
        sb.append(" ").append(features.display());
      }

      sb.append("\n");

      for (final var child : children) {
        child.display(sb, indent + 1);
      }
    } else if (this instanceof Leaf(String text, int start, int end, Structure features)) {
      sb.append(str).append("'").append(text).append("'");
      if (!features.isEmpty()) {
        sb.append(" ").append(features.display());
      }
      sb.append(" [").append(start).append(",").append(end).append("]");
      sb.append("\n");
    }
  }

  /** Non-terminal node with children */
  /**
   * An interior node. {@code start} and {@code end} are explicit, so an empty constituent reports
   * its real position (PAR-11).
   */
  record Node(
      String symbol, String label, List<ParseTree> children, Structure features, int start, int end)
      implements ParseTree {
    public Node {
      children = List.copyOf(children);
    }

    /** A node whose span is that of its children (0 for a node without children). */
    public Node(
        final String symbol,
        final String label,
        final List<ParseTree> children,
        final Structure features) {
      this(
          symbol,
          label,
          children,
          features,
          children.isEmpty() ? 0 : children.getFirst().start(),
          children.isEmpty() ? 0 : children.getLast().end());
    }

    public Node(final String symbol, final List<ParseTree> children) {
      this(symbol, null, children, Structure.EMPTY);
    }
  }

  /** Terminal node (leaf) with optional features from token. */
  record Leaf(String text, int start, int end, Structure features) implements ParseTree {
    public Leaf(final String text, final int start, final int end) {
      this(text, start, end, Structure.EMPTY);
    }
  }
}
