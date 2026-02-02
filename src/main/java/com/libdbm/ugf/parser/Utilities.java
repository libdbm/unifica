package com.libdbm.ugf.parser;

import com.libdbm.ugf.constraints.Builtins;
import com.libdbm.ugf.constraints.Context;
import com.libdbm.ugf.constraints.Result;
import com.libdbm.ugf.features.NumericConstant;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Value;
import java.util.List;
import java.util.Objects;

/**
 * Utility methods for working with ParseTree structures. Provides common traversal and text
 * extraction operations used by various builder classes.
 */
public final class Utilities {

  private Utilities() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Extract text from the first leaf child of a node. Returns null if the node has no children or
   * the first child is not a leaf.
   *
   * @param node The node to extract text from
   * @return The text of the first leaf child, or null
   */
  public static String textOf(final ParseTree.Node node) {
    Objects.requireNonNull(node, "node must not be null");

    if (node.children().isEmpty()) {
      return null;
    }

    if (node.children().getFirst() instanceof ParseTree.Leaf leaf) {
      return leaf.text();
    }

    // Fallback: collect all leaf text
    final var sb = new StringBuilder();
    collectText(node, sb);
    return sb.toString();
  }

  /**
   * Recursively collect all text from a parse tree into a StringBuilder.
   *
   * @param tree The parse tree to collect text from
   * @param sb The StringBuilder to append text to
   */
  public static void collectText(final ParseTree tree, final StringBuilder sb) {
    Objects.requireNonNull(tree, "tree must not be null");
    Objects.requireNonNull(sb, "StringBuilder must not be null");

    switch (tree) {
      case ParseTree.Leaf leaf -> sb.append(leaf.text());
      case ParseTree.Node node -> {
        for (final var child : node.children()) {
          collectText(child, sb);
        }
      }
    }
  }

  /**
   * Extract all text from a parse tree as a single string. Concatenates all leaf node text in
   * depth-first order.
   *
   * @param tree The parse tree to extract text from
   * @return All text concatenated together
   */
  public static String extractAllText(final ParseTree tree) {
    Objects.requireNonNull(tree, "tree must not be null");

    final var sb = new StringBuilder();
    collectText(tree, sb);
    return sb.toString();
  }

  /**
   * Extract all text from a node with spaces between leaves. Used for compound constructions like
   * "flare stack".
   *
   * @param node The node to extract text from
   * @return Text with spaces between leaf nodes, trimmed
   */
  public static String extractTextWithSpaces(final ParseTree.Node node) {
    Objects.requireNonNull(node, "node must not be null");

    if (node.children().isEmpty()) {
      return null;
    }

    final var sb = new StringBuilder();
    extractTextRecursive(node, sb);
    return sb.toString().trim();
  }

  /** Recursively extract text with spaces between leaves. Helper for extractTextWithSpaces. */
  private static void extractTextRecursive(final ParseTree tree, final StringBuilder sb) {
    if (tree instanceof ParseTree.Leaf leaf) {
      if (!sb.isEmpty()) {
        sb.append(" ");
      }
      sb.append(leaf.text());
    } else if (tree instanceof ParseTree.Node node) {
      for (final var child : node.children()) {
        extractTextRecursive(child, sb);
      }
    }
  }

  /**
   * Find a child node with the given symbol name.
   *
   * @param tree the tree to search in
   * @param symbol the symbol name to find
   * @return the first matching child node, or null if not found
   */
  public static ParseTree find(final ParseTree tree, final String symbol) {
    if (tree instanceof ParseTree.Node node) {
      for (final var child : node.children()) {
        if (child instanceof ParseTree.Node childNode && symbol.equals(childNode.symbol())) {
          return childNode;
        }
        // Recurse into children
        final var found = find(child, symbol);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /**
   * Get the text of the first leaf in the tree.
   *
   * @param tree the tree to extract from
   * @return the text of the first leaf, or null if no leaves
   */
  public static String firstLeaf(final ParseTree tree) {
    if (tree instanceof ParseTree.Leaf leaf) {
      return leaf.text();
    }
    if (tree instanceof ParseTree.Node node) {
      for (final var child : node.children()) {
        final var result = firstLeaf(child);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  /**
   * Gather all text from the tree as a space-delimited string.
   *
   * @param tree the tree to extract text from
   * @return space-delimited text from all leaves
   */
  public static String spaced(final ParseTree tree) {
    final var sb = new StringBuilder();
    collectSpaced(tree, sb);
    return sb.toString().trim();
  }

  /** Helper to collect text with spaces between leaves. */
  private static void collectSpaced(final ParseTree tree, final StringBuilder sb) {
    if (tree instanceof ParseTree.Leaf leaf) {
      if (!sb.isEmpty()) {
        sb.append(" ");
      }
      sb.append(leaf.text());
    } else if (tree instanceof ParseTree.Node node) {
      for (final var child : node.children()) {
        collectSpaced(child, sb);
      }
    }
  }

  // =====================
  // Parser utilities
  // =====================

  /**
   * Creates a Context with standard parser predicates registered.
   *
   * <p>Includes: - All built-in predicates from Builtins - Parser position predicates: at_start,
   * at_position - Lexer-state predicates as no-ops: in_state, state_depth, state_contains
   */
  public static Context context() {
    return new Context()
        .withPredicates(Builtins.all())
        .withPredicate("at_start", Utilities::atStart)
        .withPredicate("at_position", Utilities::atPosition)
        // State predicates - always pass in parser (lexer already handled state filtering)
        .withPredicate("in_state", (context, args) -> Result.ok())
        .withPredicate("state_depth", (context, args) -> Result.ok())
        .withPredicate("state_contains", (context, args) -> Result.ok());
  }

  /** Check if parser is at the start of input (position 0). */
  private static Result atStart(final Context context, final List<Value> args) {
    final var position = context.get("position");
    if (position instanceof NumericConstant number) {
      return Result.result(number.value().intValue() == 0, "Not at start");
    }
    return Result.fail("Position unknown");
  }

  /**
   * Check if parser is at a specific position.
   *
   * @param args single argument: the expected position (as number or string)
   */
  private static Result atPosition(final Context context, final List<Value> args) {
    if (args.isEmpty()) {
      return Result.fail("at_position requires argument");
    }
    final var position = context.get("position");
    final var arg = args.getFirst();

    int expected;
    if (arg instanceof NumericConstant number) {
      expected = number.value().intValue();
    } else if (arg instanceof StringConstant(String value)) {
      try {
        expected = Integer.parseInt(value);
      } catch (NumberFormatException e) {
        return Result.fail("Invalid position: " + value);
      }
    } else {
      return Result.fail("Invalid argument type");
    }

    if (position instanceof NumericConstant number) {
      return Result.result(number.value().intValue() == expected, "Position mismatch");
    }
    return Result.fail("Position unknown");
  }
}
