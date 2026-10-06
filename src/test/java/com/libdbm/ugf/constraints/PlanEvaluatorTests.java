package com.libdbm.ugf.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.libdbm.ugf.constraints.Expression.And;
import com.libdbm.ugf.constraints.Expression.Call;
import com.libdbm.ugf.constraints.Expression.Literal;
import com.libdbm.ugf.constraints.Expression.Not;
import com.libdbm.ugf.constraints.Expression.Or;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Value;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PlanEvaluatorTests {

  private static final Call P = new Call("t", List.of());
  private static final Call Q = new Call("t", List.of());
  private static final Call F = new Call("f", List.of());
  private static final Call G = new Call("f", List.of());

  /** Calls named {@code t} are true, {@code f} false, {@code in_state(s)} true for DEFAULT. */
  private static final Function<Call, Boolean> ORACLE =
      call ->
          switch (call.name()) {
            case "t" -> true;
            case "f" -> false;
            case "in_state" -> call.args().getFirst().equals(new StringConstant("DEFAULT"));
            default -> throw new IllegalStateException("unexpected call " + call);
          };

  private static Verdict evaluate(final Plan plan) {
    return Evaluator.evaluate(plan, ORACLE);
  }

  private static Plan soft(final Expression expression, final long weight) {
    return new Plan(List.of(), List.of(new Soft(expression, weight)));
  }

  private static long penalty(final Verdict verdict) {
    return assertInstanceOf(Verdict.Accepted.class, verdict).penalty();
  }

  @Test
  void testTruthIsBoolean() {
    assertTrue(Evaluator.truth(new And(List.of(P, Q)), ORACLE));
    assertFalse(Evaluator.truth(new And(List.of(P, F)), ORACLE));
    assertTrue(Evaluator.truth(new Or(List.of(F, Q)), ORACLE));
    assertFalse(Evaluator.truth(new Or(List.of(F, G)), ORACLE));
    assertTrue(Evaluator.truth(new Not(F), ORACLE));
    assertTrue(Evaluator.truth(Literal.TRUE, ORACLE));
  }

  /** The review's cost table: a false soft group costs its weight once (S-C4). */
  @Test
  void testSoftGroupTable() {
    assertEquals(0, penalty(evaluate(soft(new And(List.of(P, Q)), 7))));
    assertEquals(7, penalty(evaluate(soft(new And(List.of(P, F)), 7))));
    assertEquals(7, penalty(evaluate(soft(new And(List.of(F, G)), 7))));
    assertEquals(0, penalty(evaluate(soft(new Or(List.of(P, F)), 7))));
    assertEquals(7, penalty(evaluate(soft(new Or(List.of(F, G)), 7))));
  }

  @Test
  void testIndependentSoftGroupsAdd() {
    final var plan = new Plan(List.of(), List.of(new Soft(F, 3), new Soft(G, 4), new Soft(P, 9)));

    assertEquals(7, penalty(evaluate(plan)));
  }

  @Test
  void testRequiredFailureRejects() {
    final var plan = new Plan(List.of(P, F), List.of(new Soft(G, 3)));

    final var verdict = assertInstanceOf(Verdict.Rejected.class, evaluate(plan));

    assertEquals(F, verdict.failed());
  }

  @Test
  void testRequiredAndSoftTogether() {
    final var plan = new Plan(List.of(P), List.of(new Soft(new And(List.of(F, G)), 7)));

    assertEquals(7, penalty(evaluate(plan)));
  }

  @Test
  void testOverflow() {
    final var plan = new Plan(List.of(), List.of(new Soft(F, Long.MAX_VALUE), new Soft(G, 1)));

    assertInstanceOf(Verdict.Overflow.class, evaluate(plan));
  }

  @Test
  void testEmptyPlanAccepts() {
    assertEquals(0, penalty(evaluate(Plan.EMPTY)));
  }

  /** S-C7: a lexical predicate is replaced by its truth value; nothing is deleted. */
  @Test
  void testResidualPreservesTruth() {
    final var state = new Call("in_state", List.<Value>of(new StringConstant("DEFAULT")));
    final var equals =
        new Call("equals", List.<Value>of(new StringConstant("a"), new StringConstant("b")));
    final var lexical = Set.of("in_state");

    assertEquals(Literal.TRUE, Evaluator.residual(new Or(List.of(state, equals)), ORACLE, lexical));
    assertEquals(equals, Evaluator.residual(new And(List.of(state, equals)), ORACLE, lexical));
    assertEquals(Literal.FALSE, Evaluator.residual(new Not(state), ORACLE, lexical));
    assertEquals(new Not(equals), Evaluator.residual(new Not(equals), ORACLE, lexical));
  }

  @Test
  void testResidualKeepsUnevaluatedStructure() {
    final var other =
        new Call("equals", List.<Value>of(new StringConstant("a"), new StringConstant("a")));
    final var expression = new Or(List.of(other, new And(List.of(other, other))));

    assertEquals(expression, Evaluator.residual(expression, ORACLE, Set.of("in_state")));
  }
}
