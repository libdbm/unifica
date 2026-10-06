package com.libdbm.ugf.features;

import java.util.HashMap;
import java.util.Map;

/**
 * An immutable set of variable bindings produced by unification. Operations that add a binding
 * return a new instance.
 *
 * @param values variable name to bound value
 */
public record Bindings(Map<String, Value> values) {

  /** No bindings. */
  public static final Bindings EMPTY = new Bindings(Map.of());

  public Bindings {
    values = Map.copyOf(values);
  }

  /** Returns the value bound to {@code name}, or {@code null} if it is unbound. */
  public Value get(final String name) {
    return values.get(name);
  }

  /** Returns new bindings with {@code name} bound to {@code value}. */
  public Bindings with(final String name, final Value value) {
    final var copied = new HashMap<>(values);
    copied.put(name, value);
    return new Bindings(copied);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }
}
