package com.libdbm.ugf.constraints;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import java.util.ArrayList;
import java.util.List;

/**
 * The constraints of a production: required expressions, every one of which must be true (S-C3),
 * and soft groups, each of which charges its weight when false (S-C2, S-C4).
 *
 * @param required expressions that reject the derivation when false
 * @param soft weighted groups that add to the penalty when false
 */
public record Plan(List<Expression> required, List<Soft> soft) {

  /** No constraints. */
  public static final Plan EMPTY = new Plan(List.of(), List.of());

  public Plan {
    required = List.copyOf(required);
    soft = List.copyOf(soft);
  }

  /**
   * Builds a plan from the top-level constraint expressions of a production (CON-2). Unweighted
   * conjunctions at the top are flattened; each remaining unweighted conjunct is required and each
   * {@link Expression.Weighted} one is a soft group (S-C6). Weights inside a soft group are ignored
   * (S-C5). A weight anywhere else (under {@code or} or {@code not}, or nested in a required
   * expression) is an error (S-C6); every such error is reported. A zero weight can never change a
   * derivation, so that group is dropped.
   */
  public static Result<Plan, ErrorDetails> of(final List<Expression> constraints) {
    final var required = new ArrayList<Expression>();
    final var soft = new ArrayList<Soft>();
    final var errors = new ArrayList<String>();
    for (final var constraint : constraints) {
      top(constraint, required, soft, errors);
    }
    return errors.isEmpty()
        ? Result.success(new Plan(required, soft))
        : Result.failure(ErrorDetails.of("constraint.weight", errors));
  }

  private static void top(
      final Expression expression,
      final List<Expression> required,
      final List<Soft> soft,
      final List<String> errors) {
    switch (expression) {
      case Expression.Weighted weighted -> {
        final var term = strip(weighted.term(), true, errors);
        if (weighted.weight() > 0) {
          soft.add(new Soft(term, weighted.weight()));
        }
      }
      case Expression.And and -> and.terms().forEach(term -> top(term, required, soft, errors));
      default -> required.add(strip(expression, false, errors));
    }
  }

  /**
   * Removes weights; {@code inside} is true within a soft group, where weights are ignored (S-C5).
   */
  private static Expression strip(
      final Expression expression, final boolean inside, final List<String> errors) {
    return switch (expression) {
      case Expression.Weighted weighted -> {
        if (!inside) {
          errors.add(
              "weight "
                  + weighted.weight()
                  + " on "
                  + display(weighted.term())
                  + " is under 'or' or 'not'; a soft group must be a top-level conjunct");
        }
        yield strip(weighted.term(), true, errors);
      }
      case Expression.And and ->
          new Expression.And(
              and.terms().stream().map(term -> strip(term, inside, errors)).toList());
      case Expression.Or or ->
          new Expression.Or(or.terms().stream().map(term -> strip(term, inside, errors)).toList());
      case Expression.Not not -> new Expression.Not(strip(not.term(), inside, errors));
      case Expression.Call call -> call;
      case Expression.Literal literal -> literal;
    };
  }

  private static String display(final Expression expression) {
    return expression instanceof Expression.Call call ? call.name() + "(...)" : "a group";
  }

  public boolean isEmpty() {
    return required.isEmpty() && soft.isEmpty();
  }
}
