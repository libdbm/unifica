package com.libdbm.ugf;

import java.util.List;
import java.util.Objects;

/**
 * Describes an expected failure.
 *
 * @param code a stable, machine-readable identifier such as {@code grammar.syntax}
 * @param message a human-readable summary
 * @param issues individual problems, for operations that report every error at once
 */
public record ErrorDetails(String code, String message, List<String> issues) {

  public ErrorDetails {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
    issues = List.copyOf(issues);
  }

  /** Creates details with a single message and no separate issues. */
  public static ErrorDetails of(final String code, final String message) {
    return new ErrorDetails(code, message, List.of());
  }

  /** Creates details listing several issues, summarized by their count. */
  public static ErrorDetails of(final String code, final List<String> issues) {
    final var message = issues.size() == 1 ? issues.getFirst() : issues.size() + " issues";
    return new ErrorDetails(code, message, issues);
  }
}
