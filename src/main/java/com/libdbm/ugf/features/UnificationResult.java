package com.libdbm.ugf.features;

import java.util.Map;
import java.util.Optional;

/**
 * Result of a unification operation with diagnostic information.
 *
 * <p>Unlike {@link Optional}, this type provides information about why unification failed, enabling
 * better error messages and debugging.
 *
 * <p><b>Usage:</b>
 *
 * <pre>{@code
 * UnificationResult result = unifier.tryUnify(fs1, fs2);
 * if (result.succeeded()) {
 *     Structure unified = result.value();
 * } else {
 *     String reason = result.reason(); // e.g., "Atomic values don't match: sing vs pl"
 * }
 * }</pre>
 *
 * @param <T> the type of the unified value
 */
public sealed interface UnificationResult<T extends Value> {

  /** Creates a successful result with the unified value and bindings. */
  static <T extends Value> UnificationResult<T> ok(
      final T value, final Map<String, Value> bindings) {
    return new Success<>(value, bindings);
  }

  /** Creates a successful result with just the unified value (no bindings). */
  static <T extends Value> UnificationResult<T> ok(final T value) {
    return new Success<>(value, Map.of());
  }

  /** Creates a failure result with a reason. */
  static <T extends Value> UnificationResult<T> fail(final String reason) {
    return new Failure<>(reason);
  }

  /** Returns true if unification failed. */
  boolean failed();

  /** Returns the unified value or throws if unification failed. */
  T value();

  /** Returns the unified value as Optional (for backward compatibility). */
  Optional<T> toOptional();

  /** Returns the reason for failure, or null if succeeded. */
  String reason();

  /** Returns the bindings accumulated during unification. */
  Map<String, Value> bindings();

  /** Successful unification result. */
  record Success<T extends Value>(T value, Map<String, Value> bindings)
      implements UnificationResult<T> {
    @Override
    public boolean failed() {
      return false;
    }

    @Override
    public Optional<T> toOptional() {
      return Optional.of(value);
    }

    @Override
    public String reason() {
      return null;
    }
  }

  /** Failed unification result. */
  record Failure<T extends Value>(String reason) implements UnificationResult<T> {
    @Override
    public boolean failed() {
      return true;
    }

    @Override
    public T value() {
      throw new IllegalStateException("Unification failed: " + reason);
    }

    @Override
    public Optional<T> toOptional() {
      return Optional.empty();
    }

    @Override
    public Map<String, Value> bindings() {
      return Map.of();
    }
  }
}
