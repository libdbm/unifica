package com.libdbm.ugf.constraints;

import com.libdbm.ugf.features.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The immutable environment one constraint evaluation runs in (CON-6): the predicate registry,
 * label and variable bindings, and, during lexing only, the lexical state stack and character
 * position. Operations that add information return a new environment.
 */
public final class Environment {

  /**
   * The binding holding where the constituent being checked starts, as a structure with one feature
   * per position (S-C7): during parsing, the token index of every path that reaches its start node;
   * during lexing, the offset where the token starts. No grammar identifier can name it.
   */
  public static final String POSITION = "@position";

  /** The binding that is true when the constituent being checked ends at the end of the input. */
  public static final String END = "@end";

  /**
   * The binding holding the categories of the tokens that follow the constituent being checked, as
   * a structure with one feature per category.
   */
  public static final String NEXT = "@next";

  private static final List<String> INITIAL = List.of("DEFAULT");
  private final Predicates predicates;
  private final Map<String, Value> bindings;
  private final List<String> states;
  private final int position;

  private Environment(
      final Predicates predicates,
      final Map<String, Value> bindings,
      final List<String> states,
      final int position) {
    this.predicates = Objects.requireNonNull(predicates, "predicates");
    this.bindings = Map.copyOf(bindings);
    this.states = List.copyOf(states);
    this.position = position;
  }

  /** An environment with no bindings, in the initial lexical state. */
  public static Environment of(final Predicates predicates) {
    return new Environment(predicates, Map.of(), INITIAL, 0);
  }

  /** Returns a new environment with {@code name} bound to {@code value}. */
  public Environment with(final String name, final Value value) {
    final var copied = new HashMap<>(bindings);
    copied.put(name, value);
    return new Environment(predicates, copied, states, position);
  }

  /** Returns a new environment with every binding in {@code values} added. */
  public Environment with(final Map<String, Value> values) {
    final var copied = new HashMap<>(bindings);
    copied.putAll(values);
    return new Environment(predicates, copied, states, position);
  }

  /**
   * Returns a new environment for lexing at {@code position} with the state stack {@code states},
   * listed from bottom to top.
   */
  public Environment lexical(final List<String> states, final int position) {
    if (states.isEmpty()) {
      throw new IllegalArgumentException("the state stack is never empty");
    }
    return new Environment(predicates, bindings, states, position);
  }

  public Predicates predicates() {
    return predicates;
  }

  /** The value bound to {@code name}, or {@code null}. */
  public Value get(final String name) {
    return bindings.get(name);
  }

  /** The lexical state stack, bottom to top. */
  public List<String> states() {
    return states;
  }

  /** The current lexical state: the top of the stack. */
  public String state() {
    return states.getLast();
  }

  /** The character position being lexed. */
  public int position() {
    return position;
  }

  /**
   * Resolves a predicate argument: a variable to its binding, a feature path to the value it names
   * (through constituent bindings and structures), anything else to itself. Returns {@code null}
   * for an unbound variable or a path that leads nowhere.
   */
  public Value resolve(final Value value) {
    return switch (value) {
      case Variable(String name) -> bindings.get(name);
      case FeaturePath path -> lookup(path);
      case null -> null;
      default -> value;
    };
  }

  private Value lookup(final FeaturePath path) {
    var current = bindings.get(path.root());
    for (final var feature : path.path()) {
      current =
          switch (current) {
            case Binding binding -> binding.features().get(feature);
            case Structure structure -> structure.get(feature);
            case null, default -> null;
          };
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  /** Evaluates a predicate call in this environment. */
  public boolean test(final Expression.Call call) {
    return predicates.test(this, call);
  }
}
