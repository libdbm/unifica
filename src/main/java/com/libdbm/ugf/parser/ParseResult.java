package com.libdbm.ugf.parser;

import java.util.Optional;

/**
 * Parse result containing the parse tree and accumulated penalty from soft constraint violations.
 *
 * <p>A penalty of 0 indicates a perfect parse with no soft constraint violations. Higher penalties
 * indicate more or more severe soft constraint violations.
 *
 * <p>{@code ambiguous} is true when two or more distinct derivations share the lowest penalty. The
 * penalties could not rank them, so {@code tree} is the one declaration order selects (S-P3); a
 * caller that must not guess should refuse the parse.
 *
 * <p>{@code stop} says why a {@link Outcome#LIMIT} or {@link Outcome#CANCELLED} parse ended, and is
 * null otherwise.
 */
public record ParseResult(
    Outcome outcome,
    ParseTree tree,
    long penalty,
    boolean ambiguous,
    ParseDiagnostics diagnostics,
    Statistics statistics,
    Stop stop) {

  public boolean success() {
    return outcome == Outcome.ACCEPTED;
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
    sb.append("ParseResult{outcome=").append(outcome);
    sb.append(", penalty=").append(penalty);
    if (stop != null) {
      sb.append(", stop=").append(stop.message());
    }
    if (diagnostics != null && diagnostics.hasFailures()) {
      sb.append(", diagnostics=\n").append(diagnostics.generateReport());
    }
    sb.append("}");
    return sb.toString();
  }
}
