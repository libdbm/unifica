package com.libdbm.ugf.constraints;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.features.NumericConstant;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Value;
import com.libdbm.ugf.features.Variable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for the Constraint engine (Context, Builtins predicates, and Evaluator). */
class ConstraintEngineTests {

  private static Context context() {
    return new Context().withPredicates(Builtins.all());
  }

  private static Predicate predicate(final String name, final List<Value> args) {
    return Predicate.of(name, args);
  }

  private static Predicate soft(final String name, final List<Value> args, final int priority) {
    return Predicate.of(name, args, Strength.DEFEASIBLE, priority);
  }

  private static Structure fs(final Map<String, Value> m) {
    final var b = Structure.builder();
    m.forEach(b::with);
    return b.build();
  }

  @Nested
  @DisplayName("Builtins: agree and feature equality")
  class BuiltinsCore {
    @Test
    void agree_passes_and_fails() {
      final var s1 = Structure.builder().with("num", "sing").build();
      final var s2 = Structure.builder().with("num", "sing").build();
      final var s3 = Structure.builder().with("num", "pl").build();
      final var context = context().withBinding("x", s1).withBinding("y", s2).withBinding("z", s3);

      // agree(?x, ?y) should pass
      var r =
          Evaluator.eval(
              context, predicate("agree", List.of(Variable.of("?x"), Variable.of("?y"))));
      assertTrue(r.passed());

      // agree(?x, ?z) should fail
      r =
          Evaluator.eval(
              context, predicate("agree", List.of(Variable.of("?x"), Variable.of("?z"))));
      assertFalse(r.passed());
      assertNotNull(r.reason());
    }

    @Test
    void feature_eq_four_arg_form() {
      final var subj = Structure.builder().with("num", "sing").with("case", "nom").build();
      final var obj = Structure.builder().with("num", "sing").with("case", "acc").build();
      final var context = context().withBinding("subj", subj).withBinding("obj", obj);

      // feature_eq(?subj, "num", ?obj, "num") - comparing same feature on two structures
      var r =
          Evaluator.eval(
              context,
              predicate(
                  "feature_eq",
                  List.of(
                      Variable.of("?subj"),
                      new StringConstant("num"),
                      Variable.of("?obj"),
                      new StringConstant("num"))));
      assertTrue(r.passed());

      // Mismatch: feature_eq(?subj, "case", ?obj, "case")
      r =
          Evaluator.eval(
              context,
              predicate(
                  "feature_eq",
                  List.of(
                      Variable.of("?subj"),
                      new StringConstant("case"),
                      Variable.of("?obj"),
                      new StringConstant("case"))));
      assertFalse(r.passed());
      assertNotNull(r.reason());
    }
  }

  @Nested
  @DisplayName("Logical combinators and defeasible semantics")
  class LogicAndDefeasible {
    @Test
    void and_accumulates_soft_penalties_but_passes_when_and_is_required() {
      final var context = context().withBinding("w", new StringConstant("word"));
      // Two soft predicates that both fail (string predicates), priorities 2 and 5
      final var p1 = soft("starts_with", List.of(Variable.of("?w"), new StringConstant("ZZ")), 2);
      final var p2 = soft("ends_with", List.of(Variable.of("?w"), new StringConstant("XX")), 5);
      final var and = new Constraint.And(List.of(p1, p2), Strength.REQUIRED, 0);

      final var r = Evaluator.eval(context, and);
      // Because And is REQUIRED, failing soft conjuncts do not flip it to failure; penalties sum
      assertTrue(r.passed());
      assertEquals(2 + 5, r.penalty());
    }

    @Test
    void and_fails_when_and_is_defeasible_and_any_soft_fails() {
      final var context = context().withBinding("w", new StringConstant("word"));
      final var p1 = soft("starts_with", List.of(Variable.of("?w"), new StringConstant("ZZ")), 3);
      final var p2 = soft("not_empty", List.of(Variable.of("?w")), 4); // passes
      final var and = new Constraint.And(List.of(p1, p2), Strength.DEFEASIBLE, 0);

      final var r = Evaluator.eval(context, and);
      assertFalse(r.passed());
      assertEquals(3, r.penalty()); // only failing soft contributes here
    }

    @Test
    void or_picks_best_passing_or_best_failure_plus_priority() {
      final var context = context().withBinding("n", NumericConstant.of(10));
      // First fails softly with penalty 7, second passes with penalty 0
      final var d1 =
          soft(
              "lt", List.of(new NumericConstant(5, false), Variable.of("?n")), 7); // 5 < 10 => pass
      final var d2 =
          soft(
              "gt",
              List.of(Variable.of("?n"), new NumericConstant(20, false)),
              5); // 10 > 20 => fail

      // Swap to ensure OR chooses the passing one regardless of order
      final var or = new Constraint.Or(List.of(d2, d1), Strength.DEFEASIBLE, 9);
      final var r1 = Evaluator.eval(context, or);
      assertTrue(r1.passed());
      assertEquals(0, r1.penalty());

      // If both fail, OR should fail with best failure + its own priority (since defeasible)
      final var e1 = soft("gt", List.of(Variable.of("?n"), new NumericConstant(100, false)), 4);
      final var e2 = soft("lt", List.of(Variable.of("?n"), new NumericConstant(0, false)), 6);
      final var orAllFail = new Constraint.Or(List.of(e1, e2), Strength.DEFEASIBLE, 3);
      final var r2 = Evaluator.eval(context, orAllFail);
      assertFalse(r2.passed());
      assertEquals(3 + Math.min(4, 6), r2.penalty());
    }

    @Test
    void not_inverts_and_uses_its_own_priority_when_defeasible() {
      final var context = context().withBinding("s", new StringConstant("hello"));
      final var predicate = predicate("not_empty", List.of(Variable.of("?s"))); // passes

      // not(p) should fail; as defeasible with priority 8 → penalty 8
      final var negated = new Constraint.Not(predicate, Strength.DEFEASIBLE, 8);
      final var r1 = Evaluator.eval(context, negated);
      assertFalse(r1.passed());
      assertEquals(8, r1.penalty());

      // Negating a failing predicate should pass with no penalty
      final var bad =
          predicate("matches", List.of(Variable.of("?s"), new StringConstant("[0-9]+")));
      final var good = new Constraint.Not(bad, Strength.REQUIRED, 0);
      final var r2 = Evaluator.eval(context, good);
      assertTrue(r2.passed());
      assertEquals(0, r2.penalty());
    }
  }

  @Nested
  @DisplayName("Context behavior")
  class ContextBehavior {
    @Test
    void shadowing_and_predicate_inheritance() {
      final var root =
          context().withBinding("position", 5).withBinding("x", new StringConstant("root"));
      final var child = root.extend().withBinding("x", new StringConstant("child"));

      // Shadowed binding should be visible in child
      final var r =
          Evaluator.eval(
              child, predicate("equals", List.of(Variable.of("?x"), new StringConstant("child"))));
      assertTrue(r.passed());

      // Predicates are inherited; also position is available
      final var rp =
          Evaluator.eval(
              child,
              predicate("equals", List.of(NumericConstant.of(5L), Variable.of("?position"))));
      assertTrue(rp.passed());
    }
  }
}
