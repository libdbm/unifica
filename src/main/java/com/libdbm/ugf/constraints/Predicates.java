package com.libdbm.ugf.constraints;

import com.libdbm.ugf.features.Value;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An immutable registry of the predicates a grammar may call (CON-5). Each entry records a name, an
 * accepted argument count, the phase in which it can be evaluated, and its implementation.
 * Predicates must be pure and deterministic.
 *
 * <p>The registry is fixed when a grammar is compiled, which lets the compiler reject unknown names
 * and wrong arities (S-C8); at runtime every call therefore finds its entry.
 */
public final class Predicates {

  /** When a predicate can be evaluated. */
  public enum Phase {
    /** Only during lexing: needs the lexical state stack or character position (S-C7). */
    LEXICAL,
    /** Whenever bindings are available: during parsing, or during lexing for literals. */
    SYNTACTIC,
    /**
     * During parsing, reading the constituent's position ({@link Environment#POSITION}, {@link
     * Environment#END}, {@link Environment#NEXT}) as well as the arguments. Results are never
     * reused between constituents.
     */
    POSITIONAL
  }

  /**
   * A predicate implementation: deterministic, and true or false (S-C8). Apart from {@link
   * Phase#LEXICAL} and {@link Phase#POSITIONAL} predicates, the result depends only on the
   * arguments.
   */
  @FunctionalInterface
  public interface Check {
    boolean test(Environment environment, List<Value> args);
  }

  /**
   * A registered predicate.
   *
   * @param minimum the fewest arguments accepted
   * @param maximum the most arguments accepted, {@link Integer#MAX_VALUE} for no limit
   */
  public record Entry(String name, int minimum, int maximum, Phase phase, Check check) {
    public Entry {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(phase, "phase");
      Objects.requireNonNull(check, "check");
      if (minimum < 0 || maximum < minimum) {
        throw new IllegalArgumentException(
            "invalid arity " + minimum + ".." + maximum + " for " + name);
      }
    }

    /** True if a call with {@code count} arguments is valid. */
    public boolean accepts(final int count) {
      return count >= minimum && count <= maximum;
    }
  }

  private final Map<String, Entry> entries;

  private Predicates(final Map<String, Entry> entries) {
    this.entries = Map.copyOf(entries);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The standard registry: the lexical predicates and every builtin. */
  public static Predicates standard() {
    return builder().lexical().builtins().build();
  }

  /** The entry for {@code name}, or {@code null} if no such predicate is registered. */
  public Entry entry(final String name) {
    return entries.get(name);
  }

  /** The names of every predicate in {@code phase}. */
  public Set<String> names(final Phase phase) {
    return entries.values().stream()
        .filter(entry -> entry.phase() == phase)
        .map(Entry::name)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Evaluates a call. An unregistered name is a programming error, because compilation rejects
   * grammars that call unknown predicates.
   */
  boolean test(final Environment environment, final Expression.Call call) {
    final var entry = entries.get(call.name());
    if (entry == null) {
      throw new IllegalStateException("Unregistered predicate: " + call.name());
    }
    return entry.check().test(environment, call.args());
  }

  /** Builds a registry. A later registration of the same name replaces the earlier one. */
  public static final class Builder {
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    private Builder() {}

    public Builder add(
        final String name,
        final int minimum,
        final int maximum,
        final Phase phase,
        final Check check) {
      entries.put(name, new Entry(name, minimum, maximum, phase, check));
      return this;
    }

    /** Adds the builtin predicates (string, feature, equality, numeric and token position). */
    public Builder builtins() {
      Builtins.register(this);
      return this;
    }

    /** Adds the lexical state and character position predicates. */
    public Builder lexical() {
      Lexical.register(this);
      return this;
    }

    public Predicates build() {
      return new Predicates(entries);
    }
  }
}
