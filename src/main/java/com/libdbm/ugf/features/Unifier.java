package com.libdbm.ugf.features;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Implements unification for feature values. Unification finds the most general value that both
 * inputs subsume.
 *
 * <p>Every entry point shares one implementation and returns the unified value with all bound
 * variables substituted, together with the resulting {@link Bindings} (S-F1). A failure carries the
 * reason in its {@link ErrorDetails}.
 *
 * <p>Unification runs in two passes, both iterative so that deeply nested structures cannot
 * overflow the stack: {@link #bind} walks pairs of values with an explicit work stack and records
 * variable bindings, then {@link #build} constructs the merged, fully substituted result bottom-up.
 */
public final class Unifier {

  private static final String MISMATCH = "unification.mismatch";
  private static final String MISSING = "unification.missing";
  private static final String OCCURS = "unification.occurs";

  private Unifier() {}

  /** Unifies two values under {@code bindings}. */
  public static Result<Unification<Value>, ErrorDetails> unify(
      final Value left, final Value right, final Bindings bindings) {
    final var working = new HashMap<>(bindings.values());
    final var failure = bind(left, right, working);
    if (failure != null) {
      return Result.failure(failure);
    }
    return Result.success(new Unification<>(build(left, right, working), new Bindings(working)));
  }

  /** Returns {@code value} with every variable bound in {@code bindings} substituted. */
  public static Value substitute(final Value value, final Bindings bindings) {
    return build(value, null, bindings.values());
  }

  /** Unifies two feature structures under {@code bindings}. */
  public static Result<Unification<Structure>, ErrorDetails> unify(
      final Structure left, final Structure right, final Bindings bindings) {
    return unify((Value) left, right, bindings)
        .map(
            unification ->
                new Unification<>((Structure) unification.value(), unification.bindings()));
  }

  /**
   * Three-way unification for chart parser completion.
   *
   * <p>Only features named by the rule's nonterminal reference ({@code expected}) are unified. Each
   * expected feature must unify with the completed constituent's value; a concrete expected value
   * the constituent lacks fails, while an expected variable the constituent lacks stays unbound
   * (optional agreement). Features the waiting item and the constituent share under an expected
   * name must also unify. The result is the item's features plus the constituent's values for the
   * expected features, fully substituted.
   *
   * @param item features from the waiting parse item
   * @param completed features from the completed constituent
   * @param expected features expected by the grammar rule nonterminal
   * @param bindings bindings in force before unification
   */
  public static Result<Unification<Structure>, ErrorDetails> unify(
      final Structure item,
      final Structure completed,
      final Structure expected,
      final Bindings bindings) {
    final var working = new HashMap<>(bindings.values());
    for (final var key : expected.keys()) {
      final var required = expected.get(key);
      final var actual = completed.get(key);
      if (actual != null) {
        if (bind(required, actual, working) != null) {
          return Result.failure(
              ErrorDetails.of(
                  MISMATCH,
                  String.format(
                      "Feature '%s' incompatible: expected %s but got %s", key, required, actual)));
        }
      } else if (!required.isVariable()) {
        return Result.failure(
            ErrorDetails.of(
                MISSING,
                String.format(
                    "Feature '%s' missing: expected %s but feature not present", key, required)));
      }
    }
    for (final var key : expected.keys()) {
      if (item.has(key)
          && completed.has(key)
          && bind(item.get(key), completed.get(key), working) != null) {
        return Result.failure(
            ErrorDetails.of(
                MISMATCH,
                String.format(
                    "Feature '%s' conflict: item has %s but completed has %s",
                    key, item.get(key), completed.get(key))));
      }
    }
    final var builder = Structure.builder(item);
    for (final var key : expected.keys()) {
      if (completed.has(key)) {
        builder.with(key, completed.get(key));
      }
    }
    final var result = (Structure) build(builder.build(), null, working);
    return Result.success(new Unification<>(result, new Bindings(working)));
  }

  /** A pair of values still to be unified, and the feature path that led to it (for messages). */
  private record Pair(Value left, Value right, String path) {}

  /**
   * First pass: unifies {@code left} with {@code right}, adding variable bindings to {@code
   * bindings}. Returns {@code null} on success, or the reason for failure.
   */
  private static ErrorDetails bind(
      final Value left, final Value right, final Map<String, Value> bindings) {
    final var stack = new ArrayDeque<Pair>();
    stack.push(new Pair(left, right, ""));
    while (!stack.isEmpty()) {
      final var pair = stack.pop();
      if (pair.left() == null || pair.right() == null) {
        return failure(
            MISMATCH, pair.path(), "Cannot unify null: " + pair.left() + " vs " + pair.right());
      }
      final var first = deref(pair.left(), bindings);
      final var second = deref(pair.right(), bindings);

      if (first instanceof Variable(String a) && second instanceof Variable(String b)) {
        if (!a.equals(b)) {
          bindings.put(a, second);
        }
      } else if (first instanceof Variable variable) {
        if (occurs(variable, second, bindings)) {
          return failure(
              OCCURS, pair.path(), "Occurs check failed: " + variable + " occurs in " + second);
        }
        bindings.put(variable.name(), second);
      } else if (second instanceof Variable variable) {
        if (occurs(variable, first, bindings)) {
          return failure(
              OCCURS, pair.path(), "Occurs check failed: " + variable + " occurs in " + first);
        }
        bindings.put(variable.name(), first);
      } else if (first.isAtomic() && second.isAtomic()) {
        // Value equality: numbers compare by mathematical value (S-F4), and a string never
        // equals a number or boolean with the same display.
        if (!first.equals(second)) {
          return failure(MISMATCH, pair.path(), "Values don't match: " + first + " vs " + second);
        }
      } else {
        final var a = features(first);
        final var b = features(second);
        if (a == null || b == null) {
          return failure(
              MISMATCH,
              pair.path(),
              String.format(
                  "Type mismatch: %s vs %s",
                  first.getClass().getSimpleName(), second.getClass().getSimpleName()));
        }
        for (final var key : a.keys()) {
          if (b.has(key)) {
            stack.push(
                new Pair(
                    a.get(key), b.get(key), pair.path().isEmpty() ? key : pair.path() + "." + key));
          }
        }
      }
    }
    return null;
  }

  private static ErrorDetails failure(final String code, final String path, final String message) {
    return ErrorDetails.of(code, path.isEmpty() ? message : "Feature '" + path + "': " + message);
  }

  /** A structure under construction in the second pass. */
  private static final class Frame {
    private final Structure left;
    private final Structure right;
    private final String text;
    private final List<String> keys;
    private final Structure.Builder builder = Structure.builder();
    private int index;

    private Frame(final Structure left, final Structure right, final String text) {
      this.left = left;
      this.right = right;
      this.text = text;
      final var union = new LinkedHashSet<>(left.keys());
      if (right != null) {
        union.addAll(right.keys());
      }
      this.keys = new ArrayList<>(union);
    }

    private Value finish() {
      final var structure = builder.build();
      return text == null ? structure : new Binding(text, structure);
    }
  }

  /**
   * Second pass: the merge of {@code left} and {@code right} (either may be {@code null}) with
   * every bound variable substituted. Assumes {@link #bind} succeeded for the pair, so wherever
   * both sides are present they are compatible. With {@code right == null} this is plain
   * substitution.
   */
  private static Value build(
      final Value left, final Value right, final Map<String, Value> bindings) {
    final var root = open(left, right, bindings);
    if (!(root instanceof Frame first)) {
      return (Value) root;
    }
    final var stack = new ArrayDeque<Frame>();
    stack.push(first);
    Value finished = null;
    while (true) {
      final var frame = stack.peek();
      if (finished != null) {
        frame.builder.with(frame.keys.get(frame.index - 1), finished);
        finished = null;
      }
      if (frame.index == frame.keys.size()) {
        stack.pop();
        finished = frame.finish();
        if (stack.isEmpty()) {
          return finished;
        }
        continue;
      }
      final var key = frame.keys.get(frame.index++);
      final var next =
          open(frame.left.get(key), frame.right == null ? null : frame.right.get(key), bindings);
      if (next instanceof Frame child) {
        stack.push(child);
      } else {
        frame.builder.with(key, (Value) next);
      }
    }
  }

  /** Either the finished value for a pair, or a {@link Frame} when it is a structure to build. */
  private static Object open(
      final Value left, final Value right, final Map<String, Value> bindings) {
    final var a = left == null ? null : deref(left, bindings);
    final var b = right == null ? null : deref(right, bindings);
    if (a == null || b == null) {
      final var single = a == null ? b : a;
      return switch (single) {
        case Structure structure -> new Frame(structure, null, null);
        case Binding binding -> new Frame(binding.features(), null, binding.text());
        default -> single;
      };
    }
    final var fa = features(a);
    final var fb = features(b);
    // Two feature-bearing values merge into a plain structure; anything else is already equal.
    return fa != null && fb != null ? new Frame(fa, fb, null) : a;
  }

  /**
   * The feature structure of a structure or a constituent binding, or {@code null} for other
   * values.
   */
  private static Structure features(final Value value) {
    return switch (value) {
      case Structure structure -> structure;
      case Binding binding -> binding.features();
      default -> null;
    };
  }

  private static Value deref(final Value value, final Map<String, Value> bindings) {
    var current = value;
    while (current instanceof Variable(String name) && bindings.get(name) != null) {
      current = bindings.get(name);
    }
    return current;
  }

  /**
   * Occurs check: true if {@code variable} appears in {@code value}, including inside bindings
   * (S-F2).
   */
  private static boolean occurs(
      final Variable variable, final Value value, final Map<String, Value> bindings) {
    final var stack = new ArrayDeque<Value>();
    stack.push(value);
    while (!stack.isEmpty()) {
      final var current = deref(stack.pop(), bindings);
      if (current instanceof Variable(String name)) {
        if (name.equals(variable.name())) {
          return true;
        }
        continue;
      }
      final var structure = features(current);
      if (structure != null) {
        for (final var key : structure.keys()) {
          stack.push(structure.get(key));
        }
      }
    }
    return false;
  }
}
