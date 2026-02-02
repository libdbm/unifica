package com.libdbm.ugf.constraints;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates constraints during parsing.
 *
 * <p>Supports:
 *
 * <ul>
 *   <li>Feature unification and agreement checking
 *   <li>Custom predicates and name checkers
 *   <li>Logical combinators (and, or, not)
 *   <li>Defeasible constraints with strength and priority
 * </ul>
 *
 * <p>Defeasible constraints (strength != REQUIRED) accumulate penalties when they fail. Nested
 * defeasible constraints properly aggregate their penalties.
 */
public final class Evaluator {
    private static final Logger LOGGER = LoggerFactory.getLogger(Evaluator.class);

    /**
     * Evaluate a constraint and return result with penalty based on its strength/priority. This is
     * the main entry point that properly handles defeasible semantics.
     */
    public static Result eval(final Context context, final Constraint constraint) {
        LOGGER.debug("Evaluating constraint: {}", constraint);
        final var result = evaluate(context, constraint);
        LOGGER.debug(
                "Constraint result: {} (penalty={})", result.passed() ? "PASS" : "FAIL", result.penalty());
        return result;
    }

    /**
     * Evaluate a constraint and return detailed result with reason and penalty. Recursively handles
     * all constraint types with proper defeasible semantics.
     */
    private static Result evaluate(final Context context, final Constraint constraint) {
        if (constraint instanceof Predicate p) {
            return predicate(context, p);
        } else if (constraint instanceof Constraint.And a) {
            return evalAnd(context, a);
        } else if (constraint instanceof Constraint.Or o) {
            return evalOr(context, o);
        } else if (constraint instanceof Constraint.Not n) {
            return evalNot(context, n);
        }
        LOGGER.debug("Unknown constraint type: {}", constraint.getClass().getSimpleName());
        return new Result(false, "Unknown constraint type");
    }

    /**
     * Evaluate conjunction - all must pass, penalties accumulate.
     */
    private static Result evalAnd(final Context context, final Constraint.And and) {
        LOGGER.debug("Evaluating AND with {} conjuncts", and.conjuncts().size());
        var total = 0;
        for (final var c : and.conjuncts()) {
            final var result = evaluate(context, c);
            if (!result.passed()) {
                // For REQUIRED constraints, fail immediately
                if (c.strength() == Strength.REQUIRED) {
                    LOGGER.debug("AND failed: required conjunct failed");
                    return result;
                }
                // For defeasible, accumulate penalty but continue
                final var penalty = result.penalty() > 0 ? result.penalty() : c.priority();
                total += penalty;
                LOGGER.debug("AND: defeasible conjunct failed, penalty={}, total={}", penalty, total);
            }
        }
        // If we accumulated penalties, mark as defeasible failure with total penalty
        if (total > 0 && and.strength() == Strength.DEFEASIBLE) {
            LOGGER.debug("AND defeasible failure, total penalty={}", total);
            return new Result(false, "Defeasible conjuncts failed", total);
        }
        LOGGER.debug("AND passed, total penalty={}", total);
        return new Result(true, "All conjuncts passed", total);
    }

    /**
     * Evaluate disjunction - at least one must pass.
     *
     * <p>Tracks passing and failing disjuncts separately: - If any disjunct passes, returns the one
     * with minimum penalty - If all fail, returns the minimum failure penalty + OR's own priority
     */
    private static Result evalOr(final Context context, final Constraint.Or or) {
        LOGGER.debug("Evaluating OR with {} disjuncts", or.disjuncts().size());
        var bestPass = Integer.MAX_VALUE;
        var bestFail = Integer.MAX_VALUE;
        String failReason = null;

        for (final var d : or.disjuncts()) {
            final var result = evaluate(context, d);
            if (result.passed()) {
                // Track best passing disjunct (lowest penalty)
                if (result.penalty() < bestPass) {
                    bestPass = result.penalty();
                    LOGGER.debug("OR: disjunct passed with penalty={}", result.penalty());
                }
            } else {
                // Track best failing disjunct for "least-bad" selection
                if (result.penalty() < bestFail) {
                    bestFail = result.penalty();
                    failReason = result.reason();
                    LOGGER.debug(
                            "OR: disjunct failed with penalty={}, reason={}", result.penalty(), failReason);
                }
            }
        }

        // If any disjunct passed, return success with minimum passing penalty
        if (bestPass < Integer.MAX_VALUE) {
            LOGGER.debug("OR passed with best penalty={}", bestPass);
            return new Result(true, null, bestPass);
        }

        // All failed - combine best failure penalty with OR's own priority
        final var penalty = or.strength() == Strength.DEFEASIBLE ? or.priority() : 0;
        final var total = (bestFail == Integer.MAX_VALUE ? 0 : bestFail) + penalty;
        LOGGER.debug("OR failed: all disjuncts failed, total penalty={}", total);
        return new Result(false, failReason != null ? failReason : "All disjuncts failed", total);
    }

    /**
     * Evaluate negation.
     */
    private static Result evalNot(final Context context, final Constraint.Not not) {
        LOGGER.debug("Evaluating NOT");
        final var result = evaluate(context, not.constraint());
        final var negated = !result.passed();
        if (negated) {
            LOGGER.debug("NOT passed (inner constraint failed)");
            return new Result(true, null, 0);
        }
        // Negation failed - apply this constraint's penalty if defeasible
        final var penalty = not.strength() == Strength.DEFEASIBLE ? not.priority() : 0;
        LOGGER.debug("NOT failed (inner constraint passed), penalty={}", penalty);
        return new Result(false, "Negation failed: " + result.reason(), penalty);
    }

    /**
     * Evaluate a predicate constraint.
     */
    private static Result predicate(final Context context, final Predicate constraint) {
        LOGGER.debug("Evaluating predicate: {}({})", constraint.name(), constraint.args());
        final var p = context.predicate(constraint.name());
        if (p == null) {
            LOGGER.debug("Unknown predicate: {}", constraint.name());
            return new Result(false, "Unknown predicate: " + constraint.name());
        }
        final var base = p.apply(context, constraint.args());
        // Apply defeasible penalty if this predicate fails and is defeasible
        if (!base.passed() && constraint.strength() == Strength.DEFEASIBLE) {
            LOGGER.debug(
                    "Predicate {} failed (defeasible), penalty={}", constraint.name(), constraint.priority());
            return new Result(false, base.reason(), constraint.priority());
        }
        LOGGER.debug("Predicate {} {}", constraint.name(), base.passed() ? "passed" : "failed");
        return base;
    }
}
