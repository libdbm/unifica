package com.libdbm.ugf.constraints;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Evaluates constraint expressions and plans. Truth is Boolean (S-C1); a plan is rejected when a
 * required expression is false (S-C3) and otherwise costs the weights of its false soft groups,
 * each once (S-C4).
 */
public final class Evaluator {

  private Evaluator() {}

  /**
   * Evaluates an expression for Boolean truth (S-C1). {@code calls} decides each predicate call;
   * weights play no part.
   */
  public static boolean truth(
      final Expression expression, final Function<Expression.Call, Boolean> calls) {
    return switch (expression) {
      case Expression.And and -> and.terms().stream().allMatch(term -> truth(term, calls));
      case Expression.Or or -> or.terms().stream().anyMatch(term -> truth(term, calls));
      case Expression.Not not -> !truth(not.term(), calls);
      case Expression.Call call -> calls.apply(call);
      case Expression.Literal literal -> literal.value();
      case Expression.Weighted weighted -> truth(weighted.term(), calls);
    };
  }

  /**
   * Evaluates a production's constraints: rejected if any required expression is false (S-C3);
   * otherwise accepted with the sum of the weights of false soft groups, each charged once (S-C4).
   * A sum that overflows 64 bits yields {@link Verdict.Overflow} (S-C10).
   */
  public static Verdict evaluate(final Plan plan, final Function<Expression.Call, Boolean> calls) {
    for (final var expression : plan.required()) {
      if (!truth(expression, calls)) {
        return new Verdict.Rejected(expression);
      }
    }
    var penalty = 0L;
    for (final var group : plan.soft()) {
      if (!truth(group.expression(), calls)) {
        try {
          penalty = Math.addExact(penalty, group.weight());
        } catch (final ArithmeticException exception) {
          return new Verdict.Overflow();
        }
      }
    }
    return new Verdict.Accepted(penalty);
  }

  /** Evaluates an expression for truth in {@code environment}. */
  public static boolean truth(final Expression expression, final Environment environment) {
    return truth(expression, environment::test);
  }

  /** Evaluates a production's constraints in {@code environment}. */
  public static Verdict evaluate(final Plan plan, final Environment environment) {
    return evaluate(plan, environment::test);
  }

  /**
   * Partially evaluates an expression in {@code environment}, evaluating only calls in {@code
   * names}.
   */
  public static Expression residual(
      final Expression expression, final Environment environment, final Set<String> names) {
    return residual(expression, environment::test, names);
  }

  /**
   * Partially evaluates an expression (S-C7): each call whose name is in {@code names} is replaced
   * by its truth value, and the result is simplified with ordinary Boolean identities. Calls not in
   * {@code names} are kept, so the residual has the same truth as the original under any evaluation
   * of the remaining calls.
   */
  public static Expression residual(
      final Expression expression,
      final Function<Expression.Call, Boolean> calls,
      final Set<String> names) {
    return switch (expression) {
      case Expression.And and -> {
        final var terms = new ArrayList<Expression>();
        for (final var term : and.terms()) {
          final var reduced = residual(term, calls, names);
          if (reduced.equals(Expression.Literal.FALSE)) {
            yield Expression.Literal.FALSE;
          }
          if (!reduced.equals(Expression.Literal.TRUE)) {
            terms.add(reduced);
          }
        }
        yield combine(terms, Expression.Literal.TRUE, Expression.And::new);
      }
      case Expression.Or or -> {
        final var terms = new ArrayList<Expression>();
        for (final var term : or.terms()) {
          final var reduced = residual(term, calls, names);
          if (reduced.equals(Expression.Literal.TRUE)) {
            yield Expression.Literal.TRUE;
          }
          if (!reduced.equals(Expression.Literal.FALSE)) {
            terms.add(reduced);
          }
        }
        yield combine(terms, Expression.Literal.FALSE, Expression.Or::new);
      }
      case Expression.Not not -> {
        final var reduced = residual(not.term(), calls, names);
        yield reduced instanceof Expression.Literal literal
            ? Expression.Literal.of(!literal.value())
            : new Expression.Not(reduced);
      }
      case Expression.Call call ->
          names.contains(call.name()) ? Expression.Literal.of(calls.apply(call)) : call;
      case Expression.Literal literal -> literal;
      case Expression.Weighted weighted -> residual(weighted.term(), calls, names);
    };
  }

  /** An empty term list is the identity, a single term stands alone, otherwise a new node. */
  private static Expression combine(
      final List<Expression> terms,
      final Expression identity,
      final Function<List<Expression>, Expression> node) {
    return switch (terms.size()) {
      case 0 -> identity;
      case 1 -> terms.getFirst();
      default -> node.apply(terms);
    };
  }
}
