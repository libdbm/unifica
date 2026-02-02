package com.libdbm.ugf.parser;

import com.libdbm.ugf.constraints.Constraint;
import com.libdbm.ugf.constraints.Context;
import com.libdbm.ugf.constraints.Evaluator;
import com.libdbm.ugf.constraints.Predicate;
import com.libdbm.ugf.constraints.Result;
import com.libdbm.ugf.constraints.Strength;
import com.libdbm.ugf.features.Binding;
import com.libdbm.ugf.features.Structure;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for evaluating constraints during parsing.
 *
 * <p>Encapsulates the logic for:
 *
 * <ul>
 *   <li>Filtering lexer-only predicates from parser constraints
 *   <li>Binding parse tree nodes to constraint variables
 *   <li>Evaluating constraints with penalty accumulation
 * </ul>
 *
 * <p>This separates constraint evaluation concerns from the core parsing algorithm.
 */
public final class ConstraintChecker {

  private ConstraintChecker() {}

  /**
   * Evaluate constraints with the given context, filtering out lexer-only predicates.
   *
   * <p>This is the standard method for parser constraint evaluation. Lexer-only predicates
   * (in_state, state_depth, etc.) are filtered out since they're not valid during parsing.
   *
   * @param context the evaluation context with bindings
   * @param constraints the constraints to check
   * @return Result with passed=true and accumulated penalty, or passed=false with reason
   */
  public static Result check(final Context context, final List<Constraint> constraints) {
    if (constraints == null || constraints.isEmpty()) {
      return new Result(true, null, 0);
    }

    // Filter out lexer-only predicates using context's name system
    final var filtered = lexer(context, constraints);
    if (filtered.isEmpty()) {
      return new Result(true, null, 0);
    }

    return eval(context, filtered);
  }

  /**
   * Evaluate constraints directly without filtering lexer-only predicates.
   *
   * <p>This method is used for:
   *
   * <ul>
   *   <li>State constraint checking during prediction (where lexer predicates ARE valid)
   *   <li>Generation with stateful grammars
   * </ul>
   *
   * @param context the evaluation context with bindings and state
   * @param constraints the constraints to check (not filtered)
   * @return Result with passed=true and accumulated penalty, or passed=false with reason
   */
  public static Result raw(final Context context, final List<Constraint> constraints) {
    if (constraints == null || constraints.isEmpty()) {
      return new Result(true, null, 0);
    }
    return eval(context, constraints);
  }

  /** Common evaluation logic for both check and raw. */
  private static Result eval(final Context context, final List<Constraint> constraints) {
    var penalty = 0;
    for (final var constraint : constraints) {
      final var result = Evaluator.eval(context, constraint);

      if (result.passed()) {
        // Even passing results may carry penalty from soft sub-constraints
        penalty += result.penalty();
      } else {
        // REQUIRED constraints fail the whole check
        if (constraint.strength() == Strength.REQUIRED) {
          return result;
        }
        // DEFEASIBLE constraints accumulate penalty but allow parsing to continue
        final var p = result.penalty() > 0 ? result.penalty() : constraint.priority();
        penalty += p;
      }
    }
    return new Result(true, null, penalty);
  }

  /**
   * Filter out lexer-only predicates from constraints. Lexer predicates (in_state,
   * at_char_position, etc.) are not valid during parsing.
   *
   * @param context the context used for name lookup
   * @param constraints the constraints to filter
   * @return filtered constraints with lexer-only predicates removed
   */
  public static List<Constraint> lexer(final Context context, final List<Constraint> constraints) {
    final var result = new ArrayList<Constraint>();
    for (final var c : constraints) {
      final var filtered = filter(context, c);
      if (filtered != null) {
        result.add(filtered);
      }
    }
    return result;
  }

  /**
   * Filter out lexer-only predicates using static fallback.
   *
   * @deprecated Use {@link #lexer(Context, List)} for name-aware filtering
   */
  @Deprecated
  public static List<Constraint> lexer(final List<Constraint> constraints) {
    return lexer(null, constraints);
  }

  /** Filter lexer-only predicates from a single constraint. */
  private static Constraint filter(final Context context, final Constraint constraint) {
    return switch (constraint) {
      case Predicate p -> isLexerOnly(context, p.name()) ? null : p;
      case Constraint.And a -> {
        final var filtered = lexer(context, a.conjuncts());
        yield filtered.isEmpty() ? null : new Constraint.And(filtered, a.strength(), a.priority());
      }
      case Constraint.Or o -> {
        final var filtered = lexer(context, o.disjuncts());
        yield filtered.isEmpty() ? null : new Constraint.Or(filtered, o.strength(), o.priority());
      }
      case Constraint.Not n -> {
        final var inner = filter(context, n.constraint());
        yield inner != null ? new Constraint.Not(inner, n.strength(), n.priority()) : null;
      }
    };
  }

  /** Check if a predicate is lexer-only using the static set in LexicalContext. */
  private static boolean isLexerOnly(final Context context, final String name) {
    return LexicalContext.isLexerOnly(name);
  }

  /**
   * Bind a parse tree to a variable in the context. Creates a Binding with both text and features
   * for predicate access.
   */
  public static void bind(final Context context, final String label, final ParseTree tree) {
    if (label == null) return;

    final var text = text(tree);
    final var features = features(tree);
    context.withBinding(label, Binding.of(text, features));
  }

  /** Extract feature structure from a parse tree. */
  public static Structure features(final ParseTree tree) {
    return switch (tree) {
      case ParseTree.Leaf leaf -> leaf.features();
      case ParseTree.Node node -> node.features();
    };
  }

  /**
   * Extract text from a parse tree (concatenating all leaf texts).
   *
   * <p>Uses a single shared StringBuilder to avoid O(N²) complexity from repeated string copying in
   * deep trees.
   */
  public static String text(final ParseTree tree) {
    // Fast path for leaves - no StringBuilder needed
    if (tree instanceof ParseTree.Leaf leaf) {
      return leaf.text();
    }
    final var builder = new StringBuilder();
    text(tree, builder);
    return builder.toString();
  }

  /**
   * Extract text from an Item by concatenating all children's texts.
   *
   * @param item the item to extract text from
   * @return concatenated text of all children
   */
  public static String text(final Item item) {
    final var builder = new StringBuilder();
    for (final var child : item.children()) {
      text(child, builder);
    }
    return builder.toString();
  }

  /** Recursive helper that appends text to a shared StringBuilder. */
  private static void text(final ParseTree tree, final StringBuilder builder) {
    switch (tree) {
      case ParseTree.Leaf leaf -> builder.append(leaf.text());
      case ParseTree.Node node -> {
        for (final var child : node.children()) {
          text(child, builder);
        }
      }
    }
  }
}
