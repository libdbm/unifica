package com.libdbm.ugf.features;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

/**
 * Traversals over feature values that need no recursion, so deep values cannot exhaust the stack.
 */
public final class Values {

  private Values() {}

  /**
   * {@code value} with every variable replaced by {@code rename}'s result. Structure features are
   * visited in sorted order, so a renaming that numbers variables by first occurrence is
   * deterministic, and constituent bindings are entered through their features. Unchanged parts are
   * returned as the same instances.
   */
  public static Value rename(final Value value, final UnaryOperator<Variable> rename) {
    if (!(value instanceof Structure) && !(value instanceof Binding)) {
      return leaf(value, rename);
    }
    final var stack = new ArrayDeque<Frame>();
    stack.push(new Frame(value));
    Value finished = null;
    while (true) {
      final var frame = stack.peek();
      if (finished != null) {
        frame.accept(finished);
        finished = null;
      }
      if (frame.done()) {
        stack.pop();
        finished = frame.finish();
        if (stack.isEmpty()) {
          return finished;
        }
        continue;
      }
      final var child = frame.next();
      if (child instanceof Structure || child instanceof Binding) {
        stack.push(new Frame(child));
      } else {
        frame.accept(leaf(child, rename));
      }
    }
  }

  private static Value leaf(final Value value, final UnaryOperator<Variable> rename) {
    return value instanceof Variable variable ? rename.apply(variable) : value;
  }

  /** A structure or binding being rebuilt: its children in order and their renamed results. */
  private static final class Frame {
    private final Value source;
    private final List<String> keys;
    private final List<Value> children = new ArrayList<>();
    private final List<Value> results = new ArrayList<>();
    private boolean changed;

    Frame(final Value source) {
      this.source = source;
      switch (source) {
        case Structure structure -> {
          this.keys = List.copyOf(new TreeSet<>(structure.keys()));
          keys.forEach(key -> children.add(structure.get(key)));
        }
        case Binding binding -> {
          this.keys = List.of();
          children.add(binding.features());
        }
        default -> throw new IllegalArgumentException("not a structure or binding: " + source);
      }
    }

    boolean done() {
      return results.size() == children.size();
    }

    Value next() {
      return children.get(results.size());
    }

    void accept(final Value result) {
      changed |= result != children.get(results.size());
      results.add(result);
    }

    Value finish() {
      if (!changed) {
        return source;
      }
      if (source instanceof Binding binding) {
        return Binding.of(binding.text(), (Structure) results.getFirst());
      }
      final var builder = Structure.builder();
      for (var index = 0; index < keys.size(); index++) {
        builder.with(keys.get(index), results.get(index));
      }
      return builder.build();
    }
  }
}
