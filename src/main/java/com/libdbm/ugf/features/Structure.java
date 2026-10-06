package com.libdbm.ugf.features;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents an immutable feature structure - a map from feature names to feature values. Feature
 * structures can be nested, and values can be atomic, variables, or other feature structures.
 *
 * <p>Instances are built with {@link #builder()} or derived with {@link #with(String, Value)};
 * nothing changes a structure after construction, so structures may be shared freely. Because a
 * structure can only contain values that already exist, it cannot contain itself.
 */
public final class Structure implements Value {

  /** The empty structure. */
  public static final Structure EMPTY = new Structure(Map.of());

  private final Map<String, Value> features;
  private int hash;

  private Structure(final Map<String, Value> features) {
    this.features = Collections.unmodifiableMap(new HashMap<>(features));
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Creates a builder pre-populated with the features of {@code base}. The base is not changed. */
  public static Builder builder(final Structure base) {
    final var builder = new Builder();
    builder.features.putAll(base.features);
    return builder;
  }

  /**
   * Returns a new structure equal to this one with {@code feature} set to {@code value}. This
   * structure is not changed.
   */
  public Structure with(final String feature, final Value value) {
    final var copied = new HashMap<>(features);
    copied.put(feature, value);
    return new Structure(copied);
  }

  /** Gets the value for a feature. */
  public Value get(final String feature) {
    return features.get(feature);
  }

  /** Checks if a feature is present. */
  public boolean has(final String feature) {
    return features.containsKey(feature);
  }

  /** Returns all feature names as an unmodifiable set. */
  public Set<String> keys() {
    return Set.copyOf(features.keySet());
  }

  /** Returns the number of features. */
  public int size() {
    return features.size();
  }

  /** Returns true if this feature structure is empty. */
  public boolean isEmpty() {
    return features.isEmpty();
  }

  @Override
  public boolean isVariable() {
    return false;
  }

  @Override
  public boolean isAtomic() {
    return false;
  }

  @Override
  public String display() {
    // Iterative so deeply nested structures cannot overflow the stack (FEA-6).
    final var out = new StringBuilder();
    final var stack = new ArrayDeque<Object>();
    stack.push(this);
    while (!stack.isEmpty()) {
      switch (stack.pop()) {
        case String text -> out.append(text);
        case Structure structure when structure.features.isEmpty() -> out.append("{}");
        case Structure structure -> {
          final var parts = new ArrayList<Object>();
          parts.add("{");
          var first = true;
          for (final var entry : structure.features.entrySet()) {
            if (!first) {
              parts.add(", ");
            }
            parts.add(entry.getKey() + ": ");
            parts.add(entry.getValue());
            first = false;
          }
          parts.add("}");
          for (var i = parts.size() - 1; i >= 0; i--) {
            stack.push(parts.get(i));
          }
        }
        case Value value -> out.append(value.display());
        default -> throw new IllegalStateException("unexpected display part");
      }
    }
    return out.toString();
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Structure other)) return false;
    // Iterative so deeply nested structures cannot overflow the stack (FEA-6).
    final var stack = new ArrayDeque<Structure[]>();
    stack.push(new Structure[] {this, other});
    while (!stack.isEmpty()) {
      final var pair = stack.pop();
      final var left = pair[0];
      final var right = pair[1];
      if (left == right) {
        continue;
      }
      if (left.features.size() != right.features.size()) {
        return false;
      }
      for (final var entry : left.features.entrySet()) {
        final var a = entry.getValue();
        final var b = right.features.get(entry.getKey());
        if (b == null) {
          return false;
        }
        if (a instanceof Structure sa && b instanceof Structure sb) {
          stack.push(new Structure[] {sa, sb});
        } else if (!a.equals(b)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Computed once, bottom-up, without recursion (FEA-6). Zero means "not yet computed", so a
   * computed hash is never zero.
   *
   * <p>The cache is safe to share between threads without synchronization, as for {@link
   * String#hashCode()}: an {@code int} is written atomically, the features are final, and every
   * thread computes the same value, so a thread that reads the cache sees either zero, and computes
   * the hash itself, or the correct hash.
   */
  @Override
  public int hashCode() {
    if (hash == 0) {
      final var stack = new ArrayDeque<Structure>();
      stack.push(this);
      while (!stack.isEmpty()) {
        final var top = stack.peek();
        if (top.hash != 0) {
          stack.pop();
          continue;
        }
        var ready = true;
        for (final var value : top.features.values()) {
          if (value instanceof Structure child && child.hash == 0) {
            stack.push(child);
            ready = false;
          }
        }
        if (ready) {
          stack.pop();
          var computed = 31;
          for (final var entry : top.features.entrySet()) {
            computed += entry.getKey().hashCode() ^ entry.getValue().hashCode();
          }
          top.hash = computed == 0 ? 1 : computed;
        }
      }
    }
    return hash;
  }

  @Override
  public String toString() {
    return display();
  }

  /** Builder for creating feature structures fluently. */
  public static class Builder {
    private final Map<String, Value> features = new HashMap<>();

    public Builder with(final String feature, final String value) {
      features.put(feature, new StringConstant(value));
      return this;
    }

    public Builder with(final String feature, final long value) {
      features.put(feature, NumericConstant.of(value));
      return this;
    }

    public Builder with(final String feature, final Variable variable) {
      features.put(feature, variable);
      return this;
    }

    public Builder with(final String feature, final Structure nested) {
      features.put(feature, nested);
      return this;
    }

    public Builder with(final String feature, final Value value) {
      features.put(feature, value);
      return this;
    }

    public Structure build() {
      return new Structure(features);
    }
  }
}
