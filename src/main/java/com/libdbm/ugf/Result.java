package com.libdbm.ugf;

import java.util.Objects;
import java.util.function.Function;

/**
 * The outcome of an operation that can fail in an expected way.
 *
 * <p>Expected failures (invalid grammar, unresolved import, unknown predicate) are returned as a
 * {@link Failure} rather than thrown. Exceptions are reserved for programming errors.
 *
 * <pre>{@code
 * switch (Compiler.compile(grammar, predicates)) {
 *   case Result.Success<Compiled, ErrorDetails>(var compiled) -> use(compiled);
 *   case Result.Failure<Compiled, ErrorDetails>(var error) -> report(error);
 * }
 * }</pre>
 *
 * @param <T> the success value type
 * @param <E> the error type, normally {@link ErrorDetails}
 */
public sealed interface Result<T, E> {

  /** Creates a successful result. */
  static <T, E> Result<T, E> success(final T value) {
    return new Success<>(value);
  }

  /** Creates a failed result. */
  static <T, E> Result<T, E> failure(final E error) {
    return new Failure<>(error);
  }

  /** Transforms the success value, leaving a failure unchanged. */
  default <U> Result<U, E> map(final Function<? super T, ? extends U> function) {
    return switch (this) {
      case Success<T, E>(var value) -> new Success<>(function.apply(value));
      case Failure<T, E>(var error) -> new Failure<>(error);
    };
  }

  /** Chains an operation that can itself fail, leaving a failure unchanged. */
  default <U> Result<U, E> flatMap(final Function<? super T, Result<U, E>> function) {
    return switch (this) {
      case Success<T, E>(var value) -> function.apply(value);
      case Failure<T, E>(var error) -> new Failure<>(error);
    };
  }

  /** Returns the success value, or {@code fallback} on failure. */
  default T orElse(final T fallback) {
    return switch (this) {
      case Success<T, E>(var value) -> value;
      case Failure<T, E> failure -> fallback;
    };
  }

  /**
   * Returns the success value, or throws {@link IllegalStateException} carrying the error. Intended
   * for tests and for code where a failure is a programming error; callers that can handle the
   * failure should pattern match or use {@link #map}/{@link #flatMap} instead.
   */
  default T orElseThrow() {
    return switch (this) {
      case Success<T, E>(var value) -> value;
      case Failure<T, E>(var error) -> throw new IllegalStateException(String.valueOf(error));
    };
  }

  /** A successful result. */
  record Success<T, E>(T value) implements Result<T, E> {}

  /** A failed result. */
  record Failure<T, E>(E error) implements Result<T, E> {
    public Failure {
      Objects.requireNonNull(error, "error");
    }
  }
}
