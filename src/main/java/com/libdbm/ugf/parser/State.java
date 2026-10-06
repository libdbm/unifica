package com.libdbm.ugf.parser;

import com.libdbm.ugf.compiler.Element;
import com.libdbm.ugf.compiler.Production;
import com.libdbm.ugf.features.*;
import com.libdbm.ugf.lexer.Node;
import java.util.*;

/**
 * A chart state: a production with a dot, spanning {@code origin} to {@code end}.
 *
 * <p>States with equal {@link #key} at the same end node are equivalent for every future operation
 * (PAR-2, S-P9): the key holds the variable bindings of this use of the production (S-F5) and the
 * bindings of exactly those labels the production's constraints read, up to renaming of the
 * variables renamed apart when a constituent completes. Those variables occur nowhere else, so the
 * names carry no information, and comparing them would make every trip round a nullable cycle a new
 * state. Of equivalent states only the cheapest is kept; equally cheap ones become further {@link
 * #links} (packed derivations) and are never advanced separately.
 */
final class State implements Child {

  final Production production;
  final int dot;
  final Node origin;
  final Node end;

  /** Variable bindings of this use of the production (S-F5). */
  final Bindings bindings;

  final Map<String, Value> labels;

  /** Offset of the first token consumed, or -1 while none has been. */
  final int start;

  final List<Link> links = new ArrayList<>();

  /**
   * For a complete state, the constituent's features: left-hand features under the bindings,
   * renamed apart.
   */
  Structure features;

  /** Total penalty: children, tokens, and for a complete state its own constraints and cost. */
  long penalty;

  /** For a complete state, the penalty of its own constraints plus its production cost. */
  long own;

  boolean superseded;

  State(
      final Production production,
      final int dot,
      final Node origin,
      final Node end,
      final Bindings bindings,
      final Map<String, Value> labels,
      final int start,
      final long penalty) {
    this.production = production;
    this.dot = dot;
    this.origin = origin;
    this.end = end;
    this.bindings = bindings;
    this.labels = Map.copyOf(labels);
    this.start = start;
    this.penalty = penalty;
  }

  private static boolean variable(final Value value) {
    return switch (value) {
      case Variable variable -> true;
      case Structure structure ->
          structure.keys().stream().anyMatch(key -> variable(structure.get(key)));
      case Binding binding -> variable(binding.features());
      case null, default -> false;
    };
  }

  /** {@code value} with renamed-apart variables numbered by first occurrence in {@code names}. */
  private static Value rename(final Value value, final Map<String, String> names) {
    return Values.rename(
        value,
        variable ->
            variable.name().indexOf('\'') >= 0
                ? new Variable(names.computeIfAbsent(variable.name(), key -> "'" + names.size()))
                : variable);
  }

  /**
   * The state's identity. Canonically, the production's own variables are kept by name (in sorted
   * order), each value and each visible label is fully substituted, and renamed-apart variables
   * (whose names contain {@code '}) are numbered by first occurrence, which preserves which of them
   * are shared. Renamed-apart variables that no production variable or label reaches cannot affect
   * this use of the production and are left out. With {@code canonical} false the raw bindings are
   * compared, for testing.
   */
  Key key(final boolean canonical) {
    if (!canonical || plain()) {
      return new Key(production.id(), dot, origin.id(), bindings.values(), labels);
    }
    final var names = new HashMap<String, String>();
    final var values = new TreeMap<String, Value>();
    for (final var name : new TreeSet<>(bindings.values().keySet())) {
      if (name.indexOf('\'') < 0) {
        values.put(name, rename(Unifier.substitute(bindings.get(name), bindings), names));
      }
    }
    final var visible = new TreeMap<String, Value>();
    for (final var entry : new TreeMap<>(labels).entrySet()) {
      visible.put(entry.getKey(), rename(Unifier.substitute(entry.getValue(), bindings), names));
    }
    return new Key(production.id(), dot, origin.id(), values, visible);
  }

  /**
   * True if the raw bindings are already canonical: no label is visible, no binding is for a
   * renamed-apart variable, and no bound value contains a variable to substitute or rename.
   */
  private boolean plain() {
    if (!labels.isEmpty()) {
      return false;
    }
    for (final var entry : bindings.values().entrySet()) {
      if (entry.getKey().indexOf('\'') >= 0 || variable(entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  boolean complete() {
    return dot == production.rhs().size();
  }

  Element next() {
    return production.rhs().get(dot);
  }

  String symbol() {
    return production.symbol();
  }

  /** Identity for merging, within one end node. */
  record Key(
      int production,
      int dot,
      int origin,
      Map<String, Value> bindings,
      Map<String, Value> labels) {}

  /**
   * One derivation of this state: {@code previous} is the state with the dot one place earlier, and
   * {@code child} is the token or completed state it was advanced over. A predicted state has no
   * links.
   */
  record Link(State previous, Child child) {}
}
