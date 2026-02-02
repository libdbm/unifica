package com.libdbm.ugf.parser;

import com.libdbm.ugf.constraints.Constraint;
import com.libdbm.ugf.features.Structure;
import java.util.ArrayList;
import java.util.List;

/** Tracks diagnostic information about parse failures and constraint violations. */
public final class ParseDiagnostics {

  private final List<ConstraintFailure> constraintFailures = new ArrayList<>();
  private final List<TokenizationError> tokenizationErrors = new ArrayList<>();
  private final List<ParsingIssue> parsingIssues = new ArrayList<>();
  private final List<RegexFailure> regexFailures = new ArrayList<>();
  private final List<QuantifierLoop> quantifierLoops = new ArrayList<>();
  private final List<UnificationFailure> unificationFailures = new ArrayList<>();

  /** Record a constraint failure. */
  public void recordConstraintFailure(
      final String name,
      final Constraint constraint,
      final String reason,
      final int position,
      final boolean isHard) {
    constraintFailures.add(new ConstraintFailure(name, constraint, reason, position, isHard));
  }

  /** Record a tokenization error. */
  public void recordTokenizationError(
      final int position, final String input, final String expected) {
    tokenizationErrors.add(new TokenizationError(position, input, expected));
  }

  /** Record a general parsing issue. */
  public void recordParsingIssue(final String message, final int position) {
    parsingIssues.add(new ParsingIssue(message, position));
  }

  /** Record a regex pattern match failure. */
  public void recordRegexFailure(final String pattern, final String input, final int position) {
    regexFailures.add(new RegexFailure(pattern, input, position));
  }

  /** Record a potential quantifier loop (excessive repetition). */
  public void recordQuantifierLoop(
      final String ruleName, final String element, final int iterations, final int position) {
    quantifierLoops.add(new QuantifierLoop(ruleName, element, iterations, position));
  }

  /** Record a feature unification failure. */
  public void recordUnificationFailure(
      final String symbol,
      final Structure waiting,
      final Structure completed,
      final Structure expected,
      final String reason,
      final int position) {
    unificationFailures.add(
        new UnificationFailure(symbol, waiting, completed, expected, reason, position));
  }

  /** Get all constraint failures. */
  public List<ConstraintFailure> constraintFailures() {
    return List.copyOf(constraintFailures);
  }

  /** Get all tokenization errors. */
  public List<TokenizationError> tokenizationErrors() {
    return List.copyOf(tokenizationErrors);
  }

  /** Get all parsing issues. */
  public List<ParsingIssue> parsingIssues() {
    return List.copyOf(parsingIssues);
  }

  /** Get all regex failures. */
  public List<RegexFailure> regexFailures() {
    return List.copyOf(regexFailures);
  }

  /** Get all quantifier loops. */
  public List<QuantifierLoop> quantifierLoops() {
    return List.copyOf(quantifierLoops);
  }

  /** Get all unification failures. */
  public List<UnificationFailure> unificationFailures() {
    return List.copyOf(unificationFailures);
  }

  /** Check if there are any failures recorded. */
  public boolean hasFailures() {
    return !constraintFailures.isEmpty()
        || !tokenizationErrors.isEmpty()
        || !parsingIssues.isEmpty()
        || !regexFailures.isEmpty()
        || !quantifierLoops.isEmpty()
        || !unificationFailures.isEmpty();
  }

  /** Generate a human-readable diagnostic report. */
  public String generateReport() {
    final var sb = new StringBuilder();

    if (!constraintFailures.isEmpty()) {
      sb.append("=== Constraint Failures ===\n");
      for (final var failure : constraintFailures) {
        sb.append(
            String.format(
                "  [%s] Rule '%s' at position %d: %s\n",
                failure.isHard ? "HARD" : "SOFT",
                failure.ruleName,
                failure.position,
                failure.reason));
        sb.append(String.format("    Constraint: %s\n", failure.constraint));
      }
      sb.append("\n");
    }

    if (!tokenizationErrors.isEmpty()) {
      sb.append("=== Tokenization Errors ===\n");
      for (final var error : tokenizationErrors) {
        sb.append(
            String.format(
                "  Position %d: %s (expected: %s)\n", error.position, error.input, error.expected));
      }
      sb.append("\n");
    }

    if (!parsingIssues.isEmpty()) {
      sb.append("=== Parsing Issues ===\n");
      for (final var issue : parsingIssues) {
        sb.append(String.format("  Position %d: %s\n", issue.position, issue.message));
      }
      sb.append("\n");
    }

    if (!regexFailures.isEmpty()) {
      sb.append("=== Regex Pattern Failures ===\n");
      for (final var failure : regexFailures) {
        sb.append(
            String.format(
                "  Position %d: Pattern '%s' failed to match input '%s'\n",
                failure.position, failure.pattern, failure.input));
      }
      sb.append("\n");
    }

    if (!quantifierLoops.isEmpty()) {
      sb.append("=== Quantifier Loops (Excessive Repetition) ===\n");
      for (final var loop : quantifierLoops) {
        sb.append(
            String.format(
                "  Position %d: Rule '%s' element '%s' iterated %d times (possible infinite loop)\n",
                loop.position, loop.ruleName, loop.element, loop.iterations));
      }
      sb.append("\n");
    }

    if (!unificationFailures.isEmpty()) {
      sb.append("=== Feature Unification Failures ===\n");
      for (final var failure : unificationFailures) {
        sb.append(
            String.format(
                "  Position %d: Symbol '%s' - %s\n", failure.position, failure.symbol, failure.reason));
        sb.append(String.format("    Expected: %s\n", failure.expected));
        sb.append(String.format("    Waiting:  %s\n", failure.waiting));
        sb.append(String.format("    Completed: %s\n", failure.completed));
      }
      sb.append("\n");
    }

    if (!hasFailures()) {
      sb.append("No failures recorded.\n");
    }

    return sb.toString();
  }

  /** A constraint that failed during parsing. */
  public record ConstraintFailure(
      String ruleName, Constraint constraint, String reason, int position, boolean isHard) {}

  /** An error during tokenization. */
  public record TokenizationError(int position, String input, String expected) {}

  /** A general parsing issue. */
  public record ParsingIssue(String message, int position) {}

  /** A regex pattern that failed to match. */
  public record RegexFailure(String pattern, String input, int position) {}

  /** A quantifier that may be looping excessively. */
  public record QuantifierLoop(String ruleName, String element, int iterations, int position) {}

  /** A feature unification that failed during parsing. */
  public record UnificationFailure(
      String symbol,
      Structure waiting,
      Structure completed,
      Structure expected,
      String reason,
      int position) {}
}
