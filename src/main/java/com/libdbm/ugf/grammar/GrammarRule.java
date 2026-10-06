package com.libdbm.ugf.grammar;

import com.libdbm.ugf.constraints.Expression;
import com.libdbm.ugf.features.Structure;
import java.util.List;
import java.util.Objects;

/**
 * Represents a unification grammar production rule. Format: lhs --> rhs1, rhs2, ... where
 * constraints.
 *
 * @param constraints the top-level constraint expressions as written; weights are {@link
 *     Expression.Weighted} nodes until compilation builds the plan
 * @param kind whether the production is lexical (one atomic token) or syntactic (S-G1)
 * @param cost a non-negative cost added to the penalty whenever the production is used (S-P7)
 * @param transition for a lexical production, the state transition applied after a match ({@code
 *     S}, {@code _}, {@code !S}, {@code ^S}), or {@code null}
 */
public record GrammarRule(
    LHS lhs,
    List<RuleElement> rhs,
    List<Expression> constraints,
    Kind kind,
    long cost,
    String transition) {

  /** Whether a production matches one atomic token or a sequence of constituents (S-G1). */
  public enum Kind {
    LEXICAL,
    SYNTACTIC
  }

  public GrammarRule {
    Objects.requireNonNull(lhs, "lhs must not be null");
    Objects.requireNonNull(rhs, "rhs must not be null");
    Objects.requireNonNull(constraints, "constraints must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
    if (cost < 0) {
      throw new IllegalArgumentException("cost must not be negative: " + cost);
    }
    rhs = List.copyOf(rhs);
    constraints = List.copyOf(constraints);
  }

  /** Creates a production without a transition. */
  public GrammarRule(
      final LHS lhs,
      final List<RuleElement> rhs,
      final List<Expression> constraints,
      final Kind kind,
      final long cost) {
    this(lhs, rhs, constraints, kind, cost, null);
  }

  /** Creates a production with no cost, classified by {@link #classify} (S-G1). */
  public GrammarRule(
      final LHS lhs, final List<RuleElement> rhs, final List<Expression> constraints) {
    this(lhs, rhs, constraints, classify(rhs, false), 0);
  }

  /**
   * Classifies a production (S-G1). A nonterminal or {@code {TOKEN}} anywhere makes it syntactic,
   * as does an empty right-hand side. Otherwise it is lexical if it has a transition, contains a
   * regex or a state annotation, or is exactly one plain literal; any other sequence of literals is
   * syntactic.
   */
  public static Kind classify(final List<RuleElement> rhs, final boolean transition) {
    if (rhs.isEmpty() || rhs.stream().anyMatch(GrammarRule::syntactic)) {
      return Kind.SYNTACTIC;
    }
    if (transition || rhs.stream().anyMatch(GrammarRule::lexical)) {
      return Kind.LEXICAL;
    }
    return rhs.size() == 1 && rhs.getFirst() instanceof RuleElement.Terminal
        ? Kind.LEXICAL
        : Kind.SYNTACTIC;
  }

  private static boolean syntactic(final RuleElement element) {
    return switch (element) {
      case RuleElement.Nonterminal nonterminal -> true;
      case RuleElement.TokenMatch match -> true;
      case RuleElement.Alternation alternation ->
          alternation.options().stream().anyMatch(GrammarRule::syntactic);
      case RuleElement.Sequence sequence ->
          sequence.elements().stream().anyMatch(GrammarRule::syntactic);
      case RuleElement.Repetition repetition -> syntactic(repetition.element());
      default -> false;
    };
  }

  private static boolean lexical(final RuleElement element) {
    return switch (element) {
      case RuleElement.Regex regex -> true;
      case RuleElement.StateAnnotation annotation -> true;
      case RuleElement.Alternation alternation ->
          alternation.options().stream().anyMatch(GrammarRule::lexical);
      case RuleElement.Sequence sequence ->
          sequence.elements().stream().anyMatch(GrammarRule::lexical);
      case RuleElement.Repetition repetition -> lexical(repetition.element());
      default -> false;
    };
  }

  public GrammarRule(final String lhs, final List<RuleElement> rhs) {
    this(new LHS(lhs), rhs, List.of());
  }

  public GrammarRule(
      final String lhs, final List<RuleElement> rhs, final List<Expression> constraints) {
    this(new LHS(lhs), rhs, constraints);
  }

  /** Left-hand side of a rule with optional features. */
  public record LHS(String symbol, Structure features) {
    public LHS {
      Objects.requireNonNull(symbol, "symbol must not be null");
      Objects.requireNonNull(features, "features must not be null");
    }

    public LHS(final String symbol) {
      this(symbol, Structure.EMPTY);
    }

    public boolean hasFeatures() {
      return features.size() > 0;
    }
  }
}
