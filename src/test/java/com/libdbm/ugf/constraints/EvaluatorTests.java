package com.libdbm.ugf.constraints;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.features.NumericConstant;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Value;
import com.libdbm.ugf.features.Variable;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for Evaluator. */
class EvaluatorTests {

  private Context context;

  @BeforeEach
  void setup() {
    context = new Context().withPredicates(Builtins.all());
  }

  private Predicate required(final String name, final Value... args) {
    return Predicate.of(name, List.of(args), Strength.REQUIRED);
  }

  private Predicate soft(final String name, final int priority, final Value... args) {
    return Predicate.of(name, List.of(args), Strength.DEFEASIBLE, priority);
  }

  @Nested
  @DisplayName("Predicate evaluation")
  class PredicateEvaluation {

    @Test
    @DisplayName("evaluates passing predicate")
    void evaluates_passing() {
      context.withBinding("x", StringConstant.of("hello"));
      final var p = required("is_string", Variable.of("?x"));

      final var result = Evaluator.eval(context, p);

      assertTrue(result.passed());
      assertEquals(0, result.penalty());
    }

    @Test
    @DisplayName("evaluates failing predicate")
    void evaluates_failing() {
      context.withBinding("x", NumericConstant.of(42));
      final var p = required("is_string", Variable.of("?x"));

      final var result = Evaluator.eval(context, p);

      assertFalse(result.passed());
    }

    @Test
    @DisplayName("defeasible predicate applies priority as penalty")
    void defeasible_penalty() {
      context.withBinding("x", NumericConstant.of(42));
      final var p = soft("is_string", 5, Variable.of("?x"));

      final var result = Evaluator.eval(context, p);

      assertFalse(result.passed());
      assertEquals(5, result.penalty());
    }
  }

  @Nested
  @DisplayName("And evaluation")
  class AndEvaluation {

    @Test
    @DisplayName("all conjuncts pass")
    void all_pass() {
      context.withBinding("x", StringConstant.of("hello")).withBinding("y", NumericConstant.of(5));

      final var and =
          new Constraint.And(
              List.of(
                  required("is_string", Variable.of("?x")),
                  required("is_number", Variable.of("?y"))));

      final var result = Evaluator.eval(context, and);

      assertTrue(result.passed());
      assertEquals(0, result.penalty());
    }

    @Test
    @DisplayName("required conjunct fails immediately")
    void required_fails_immediately() {
      context.withBinding("x", NumericConstant.of(42)).withBinding("y", NumericConstant.of(5));

      final var and =
          new Constraint.And(
              List.of(
                  required("is_string", Variable.of("?x")), // fails
                  required("is_number", Variable.of("?y"))));

      final var result = Evaluator.eval(context, and);

      assertFalse(result.passed());
    }

    @Test
    @DisplayName("soft conjuncts accumulate penalties in required And")
    void soft_penalties_accumulate() {
      context.withBinding("s", StringConstant.of("hello"));

      final var and =
          new Constraint.And(
              List.of(
                  soft("starts_with", 2, Variable.of("?s"), StringConstant.of("X")), // fails
                  soft("ends_with", 3, Variable.of("?s"), StringConstant.of("Y")))); // fails

      final var result = Evaluator.eval(context, and);

      assertTrue(result.passed()); // Required And passes despite soft failures
      assertEquals(5, result.penalty());
    }

    @Test
    @DisplayName("defeasible And fails when soft conjuncts fail")
    void defeasible_and_fails() {
      context.withBinding("s", StringConstant.of("hello"));

      final var and =
          Constraint.And.defeasible(
              List.of(
                  soft("starts_with", 2, Variable.of("?s"), StringConstant.of("X")), // fails
                  required("not_empty", Variable.of("?s"))), // passes
              10);

      final var result = Evaluator.eval(context, and);

      assertFalse(result.passed());
    }

    @Test
    @DisplayName("empty conjuncts pass")
    void empty_passes() {
      final var and = new Constraint.And(List.of());
      final var result = Evaluator.eval(context, and);
      assertTrue(result.passed());
    }
  }

  @Nested
  @DisplayName("Or evaluation")
  class OrEvaluation {

    @Test
    @DisplayName("first disjunct passes")
    void first_passes() {
      context.withBinding("x", StringConstant.of("hello"));

      final var or =
          new Constraint.Or(
              List.of(
                  required("is_string", Variable.of("?x")),
                  required("is_number", Variable.of("?x"))));

      final var result = Evaluator.eval(context, or);

      assertTrue(result.passed());
    }

    @Test
    @DisplayName("second disjunct passes")
    void second_passes() {
      context.withBinding("x", NumericConstant.of(42));

      final var or =
          new Constraint.Or(
              List.of(
                  required("is_string", Variable.of("?x")), // fails
                  required("is_number", Variable.of("?x")))); // passes

      final var result = Evaluator.eval(context, or);

      assertTrue(result.passed());
    }

    @Test
    @DisplayName("all disjuncts fail")
    void all_fail() {
      context.withBinding("x", NumericConstant.of(42));

      final var or =
          new Constraint.Or(
              List.of(
                  required("is_string", Variable.of("?x")),
                  required("not_empty", Variable.of("?x"))));

      final var result = Evaluator.eval(context, or);

      assertFalse(result.passed());
    }

    @Test
    @DisplayName("picks best passing disjunct with lowest penalty")
    void picks_best_passing() {
      context.withBinding("s", StringConstant.of("hello"));

      final var or =
          new Constraint.Or(
              List.of(
                  soft("is_number", 5, Variable.of("?s")), // fails
                  required("is_string", Variable.of("?s")))); // passes, 0 penalty

      final var result = Evaluator.eval(context, or);

      assertTrue(result.passed());
      assertEquals(0, result.penalty());
    }

    @Test
    @DisplayName("defeasible Or adds priority to failure penalty")
    void defeasible_or_adds_priority() {
      context.withBinding("x", NumericConstant.of(42));

      final var or =
          Constraint.Or.defeasible(
              List.of(
                  soft("is_string", 2, Variable.of("?x")), soft("not_empty", 3, Variable.of("?x"))),
              7);

      final var result = Evaluator.eval(context, or);

      assertFalse(result.passed());
      assertEquals(7 + 2, result.penalty()); // Or priority + min failure penalty
    }

    @Test
    @DisplayName("empty disjuncts fail")
    void empty_fails() {
      final var or = new Constraint.Or(List.of());
      final var result = Evaluator.eval(context, or);
      assertFalse(result.passed());
    }
  }

  @Nested
  @DisplayName("Not evaluation")
  class NotEvaluation {

    @Test
    @DisplayName("negation of failing predicate passes")
    void negation_passes() {
      context.withBinding("x", NumericConstant.of(42));

      final var not = new Constraint.Not(required("is_string", Variable.of("?x")));

      final var result = Evaluator.eval(context, not);

      assertTrue(result.passed());
      assertEquals(0, result.penalty());
    }

    @Test
    @DisplayName("negation of passing predicate fails")
    void negation_fails() {
      context.withBinding("x", StringConstant.of("hello"));

      final var not = new Constraint.Not(required("is_string", Variable.of("?x")));

      final var result = Evaluator.eval(context, not);

      assertFalse(result.passed());
    }

    @Test
    @DisplayName("defeasible Not applies priority on failure")
    void defeasible_not_penalty() {
      context.withBinding("x", StringConstant.of("hello"));

      final var not = Constraint.Not.defeasible(required("is_string", Variable.of("?x")), 8);

      final var result = Evaluator.eval(context, not);

      assertFalse(result.passed());
      assertEquals(8, result.penalty());
    }

    @Test
    @DisplayName("double negation")
    void double_negation() {
      context.withBinding("x", StringConstant.of("hello"));

      final var inner = new Constraint.Not(required("is_string", Variable.of("?x"))); // fails
      final var outer = new Constraint.Not(inner); // passes

      final var result = Evaluator.eval(context, outer);

      assertTrue(result.passed());
    }
  }

  @Nested
  @DisplayName("Nested constraints")
  class NestedConstraints {

    @Test
    @DisplayName("And inside Or")
    void and_inside_or() {
      context.withBinding("s", StringConstant.of("hello")).withBinding("n", NumericConstant.of(42));

      final var and =
          new Constraint.And(
              List.of(
                  required("is_string", Variable.of("?s")),
                  required("is_number", Variable.of("?n"))));

      final var or =
          new Constraint.Or(
              List.of(
                  required("is_string", Variable.of("?n")), // fails
                  and)); // passes

      final var result = Evaluator.eval(context, or);

      assertTrue(result.passed());
    }

    @Test
    @DisplayName("Or inside And")
    void or_inside_and() {
      context.withBinding("x", StringConstant.of("hello"));

      final var or =
          new Constraint.Or(
              List.of(
                  required("is_number", Variable.of("?x")), // fails
                  required("is_string", Variable.of("?x")))); // passes

      final var and =
          new Constraint.And(List.of(or, required("not_empty", Variable.of("?x")))); // passes

      final var result = Evaluator.eval(context, and);

      assertTrue(result.passed());
    }

    @Test
    @DisplayName("Not inside And")
    void not_inside_and() {
      context.withBinding("x", StringConstant.of("hello"));

      final var not =
          new Constraint.Not(required("is_number", Variable.of("?x"))); // passes (x is string)
      final var and = new Constraint.And(List.of(not, required("is_string", Variable.of("?x"))));

      final var result = Evaluator.eval(context, and);

      assertTrue(result.passed());
    }
  }
}
