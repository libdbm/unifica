package com.libdbm.ugf.parser;

import java.util.Optional;

/**
 * Parse result containing the parse tree and accumulated penalty from soft constraint violations.
 *
 * <p>A penalty of 0 indicates a perfect parse with no soft constraint violations. Higher penalties
 * indicate more or more severe soft constraint violations.
 */
public record ParseResult(ParseTree tree, int penalty, ParseDiagnostics diagnostics) {
  public ParseResult(final ParseTree tree, final int penalty) {
    this(tree, penalty, null);
  }

  /** Check if parsing succeeded (tree is present). */
  public boolean success() {
    return tree != null;
  }

  /** Returns the parse tree as an Optional. */
  public Optional<ParseTree> toOptional() {
    return Optional.ofNullable(tree);
  }

  /** Check if there were constraint failures during parsing. */
  public boolean hasConstraintFailures() {
    return diagnostics != null && !diagnostics.constraintFailures().isEmpty();
  }

  /** Get a summary of the parse result including any diagnostics. */
  @Override
  public String toString() {
    final var sb = new StringBuilder();
    sb.append("ParseResult{success=").append(success());
    sb.append(", penalty=").append(penalty);
    if (diagnostics != null && diagnostics.hasFailures()) {
      sb.append(", diagnostics=\n").append(diagnostics.generateReport());
    }
    sb.append("}");
    return sb.toString();
  }
}
