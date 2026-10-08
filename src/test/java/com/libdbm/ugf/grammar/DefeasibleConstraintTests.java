package com.libdbm.ugf.grammar;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.constraints.Expression;
import com.libdbm.ugf.constraints.Plan;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Weight notation in grammar files ({@code pred($X):N}, {@code (...):N}) and the plan it compiles
 * to: unweighted top-level conjuncts are required, weighted ones are soft groups (S-C2, S-C6).
 * Ported from the 1.x strength/priority tests.
 */
@DisplayName("Weighted constraint parsing")
class DefeasibleConstraintTests {

  private static Result<Plan, ErrorDetails> compile(final String source, final String symbol) {
    final var grammar = UnificationGrammarParserFactory.unvalidated(source).orElseThrow();
    return Plan.of(grammar.rulesFor(symbol).getFirst().constraints());
  }

  private static Plan plan(final String source, final String symbol) {
    return compile(source, symbol).orElseThrow();
  }

  private static String name(final Expression expression) {
    return ((Expression.Call) expression).name();
  }

  private static List<String> required(final Plan plan) {
    return plan.required().stream().map(DefeasibleConstraintTests::name).toList();
  }

  private static List<String> soft(final Plan plan) {
    return plan.soft().stream()
        .map(group -> name(group.expression()) + ":" + group.weight())
        .toList();
  }

  @Nested
  @DisplayName("Predicate weights")
  class PredicateWeights {

    @Test
    void testPredicateWithoutWeightIsRequired() {
      final var plan = plan("rule --> 'a' where foo($X);", "rule");

      assertEquals(List.of("foo"), required(plan));
      assertTrue(plan.soft().isEmpty());
    }

    @Test
    void testPredicateWithWeightIsSoft() {
      final var plan = plan("rule --> 'a' where foo($X):10;", "rule");

      assertTrue(plan.required().isEmpty());
      assertEquals(List.of("foo:10"), soft(plan));
    }

    @Test
    void testDifferentWeights() {
      assertEquals(
          List.of("foo:5", "bar:100"),
          soft(plan("rule --> 'a' where foo($X):5, bar($Y):100;", "rule")));
    }

    @Test
    void testMixedRequiredAndSoft() {
      final var plan = plan("rule --> 'a' where foo($X), bar($Y):10;", "rule");

      assertEquals(List.of("foo"), required(plan));
      assertEquals(List.of("bar:10"), soft(plan));
    }
  }

  @Nested
  @DisplayName("Grouped expressions")
  class Groups {

    @Test
    void testGroupedConjunctionWithWeightIsOneSoftGroup() {
      final var plan = plan("rule --> 'a' where (foo($X), bar($Y)):5;", "rule");

      assertEquals(1, plan.soft().size());
      assertEquals(5, plan.soft().getFirst().weight());
      assertEquals(
          2,
          assertInstanceOf(Expression.And.class, plan.soft().getFirst().expression())
              .terms()
              .size());
    }

    @Test
    void testGroupedDisjunctionWithWeight() {
      final var plan = plan("rule --> 'a' where (foo($X) | bar($Y)):8;", "rule");

      assertEquals(8, plan.soft().getFirst().weight());
      assertInstanceOf(Expression.Or.class, plan.soft().getFirst().expression());
    }

    @Test
    void testUnweightedGroupIsFlattenedIntoRequired() {
      assertEquals(
          List.of("foo", "bar"), required(plan("rule --> 'a' where (foo($X), bar($Y));", "rule")));
    }
  }

  @Nested
  @DisplayName("Weights in invalid or ignored positions")
  class Placement {

    /** S-C6: a weight under 'not' is a compile error (1.x applied it to the inner predicate). */
    @Test
    void testWeightUnderNotRejected() {
      assertInstanceOf(Result.Failure.class, compile("rule --> 'a' where !foo($X):10;", "rule"));
    }

    /** S-C6: a weight under 'or' is a compile error. */
    @Test
    void testWeightUnderOrRejected() {
      assertInstanceOf(
          Result.Failure.class, compile("rule --> 'a' where foo($X):5 | bar($Y);", "rule"));
    }

    /** S-C5: a weight inside a weighted group is ignored; the outer weight is charged once. */
    @Test
    void testNestedWeightIgnored() {
      final var plan = plan("rule --> 'a' where ((foo($X), bar($Y)):3 | baz($Z)):7;", "rule");

      assertEquals(1, plan.soft().size());
      assertEquals(7, plan.soft().getFirst().weight());
      final var or = assertInstanceOf(Expression.Or.class, plan.soft().getFirst().expression());
      assertInstanceOf(Expression.And.class, or.terms().getFirst());
    }
  }

  @Nested
  @DisplayName("Real-world examples")
  class Examples {

    @Test
    void testAgreementRequiredSelectionalSoft() {
      final var plan =
          plan(
              """
                                    clause --> np:S vp:V
                                        where equals(S.num, V.num), verb_allows(V.lemma, S.type):10;
                                    """,
              "clause");

      assertEquals(List.of("equals"), required(plan));
      assertEquals(List.of("verb_allows:10"), soft(plan));
    }

    @Test
    void testMultipleSoftPreferences() {
      final var plan =
          plan(
              """
                                    np --> det noun:N
                                        where animate(N.text):5, concrete(N.text):3;
                                    """,
              "np");

      assertEquals(List.of("animate:5", "concrete:3"), soft(plan));
    }
  }
}
