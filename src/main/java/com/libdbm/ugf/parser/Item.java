package com.libdbm.ugf.parser;

import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.RuleElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Earley item: a dotted rule with origin position, accumulated parse tree, and penalty.
 *
 * <p>An Item represents a partial parse: a grammar rule with a dot indicating how much of the
 * right-hand side has been recognized. Items are the core data structure in Earley parsing.
 *
 * <p>Equality is based on rule, dot position, origin, and features. Children, penalty, and
 * alternatives are excluded for chart deduplication purposes - items that differ only in their
 * derivation path are considered equivalent for the purposes of continuing the parse.
 *
 * <p>When multiple derivations with equal penalty exist (structural ambiguity), the {@code
 * alternatives} field stores additional derivation paths. Use {@link #allDerivations()} to
 * enumerate all valid parse trees.
 */
record Item(
    GrammarRule rule,
    int dot,
    int origin,
    List<ParseTree> children,
    Structure features,
    int penalty,
    List<List<ParseTree>> alternatives) {

  /**
   * Canonical constructor adds validation and defensive copying to keep Items immutable and safe
   * for use in chart sets. The {@code features} structure is frozen if not already frozen to
   * prevent accidental mutation while the item is in a set/map.
   */
  public Item {
    Objects.requireNonNull(rule, "rule must not be null");
    Objects.requireNonNull(children, "children must not be null");
    Objects.requireNonNull(features, "features must not be null");
    Objects.requireNonNull(alternatives, "alternatives must not be null");

    if (dot < 0) {
      throw new IllegalArgumentException("dot must be >= 0");
    }
    if (origin < 0) {
      throw new IllegalArgumentException("origin must be >= 0");
    }

    // Defensive copies for immutability
    children = List.copyOf(children);
    if (alternatives.isEmpty()) {
      alternatives = List.of();
    } else {
      final var tmp = new ArrayList<List<ParseTree>>(alternatives.size());
      for (final var alt : alternatives) {
        tmp.add(List.copyOf(Objects.requireNonNull(alt, "alt must not be null")));
      }
      alternatives = List.copyOf(tmp);
    }

    // Defensive copy of features to avoid later mutation through aliasing
    features = features.copy();
  }

  /** Convenience constructor with zero penalty and no alternatives. */
  Item(
      final GrammarRule rule,
      final int dot,
      final int origin,
      final List<ParseTree> children,
      final Structure features) {
    this(rule, dot, origin, children, features, 0, List.of());
  }

  /** Constructor with penalty but no alternatives. */
  Item(
      final GrammarRule rule,
      final int dot,
      final int origin,
      final List<ParseTree> children,
      final Structure features,
      final int penalty) {
    this(rule, dot, origin, children, features, penalty, List.of());
  }

  /** Check if item is complete (dot at end of RHS). */
  boolean complete() {
    return dot >= rule.rhs().size();
  }

  /** Get next expected element (or null if complete). */
  RuleElement next() {
    return complete() ? null : rule.rhs().get(dot);
  }

  /** Add an alternative derivation path with the same penalty. */
  Item withAlternative(final List<ParseTree> alt) {
    final var alts = new ArrayList<>(alternatives);
    alts.add(alt);
    return new Item(rule, dot, origin, children, features, penalty, List.copyOf(alts));
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Item other)) return false;
    return dot == other.dot
        && origin == other.origin
        && rule.equals(other.rule)
        && features.equals(other.features);
    // Note: children, penalty, alternatives not included for chart deduplication
    // Features ARE included because different feature values represent different parse paths
  }

  @Override
  public int hashCode() {
    return Objects.hash(rule, dot, origin, features);
  }

  @Override
  public String toString() {
    final var builder = new StringBuilder();
    builder.append(rule.lhs().symbol()).append(" →");
    for (int i = 0; i < rule.rhs().size(); i++) {
      if (i == dot) builder.append(" •");
      builder.append(" ").append(format(rule.rhs().get(i)));
    }
    if (dot == rule.rhs().size()) builder.append(" •");
    builder.append(" (").append(origin);
    if (penalty > 0) builder.append(", pen=").append(penalty);
    builder.append(")");
    return builder.toString();
  }

  private String format(final RuleElement e) {
    return switch (e) {
      case RuleElement.Terminal t -> "'" + t.text() + "'";
      case RuleElement.Regex r -> "[" + r.pattern() + "]";
      case RuleElement.Nonterminal n -> n.name();
      case RuleElement.Alternation a -> "(" + a.options().size() + " opts)";
      case RuleElement.Repetition r -> format(r.element()) + quantifier(r.quantifier());
      case RuleElement.StateAnnotation s -> "{" + s.state() + "}";
      case RuleElement.TokenMatch tm -> "{TOKEN}" + (tm.label() != null ? ":" + tm.label() : "");
    };
  }

  private String quantifier(final RuleElement.Quantifier q) {
    return switch (q) {
      case ZERO_OR_MORE -> "*";
      case ONE_OR_MORE -> "+";
      case OPTIONAL -> "?";
    };
  }
}
