package com.libdbm.ugf.constraints;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.libdbm.ugf.features.Binding;
import com.libdbm.ugf.features.BooleanConstant;
import com.libdbm.ugf.features.NumericConstant;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Value;
import com.libdbm.ugf.features.Variable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Builtins are total: grammar data never makes a predicate throw (S-C8). */
class TotalityTests {

  private static final Predicates STANDARD = Predicates.standard();

  private static final List<Value> ARGUMENTS =
      List.of(
          new Variable("Unbound"),
          StringConstant.of(""),
          StringConstant.of("abc"),
          StringConstant.of("999999999999999999999999999999"),
          StringConstant.of("1.5"),
          StringConstant.of("-1"),
          StringConstant.of("[unclosed"),
          NumericConstant.of(1.5),
          NumericConstant.of(Double.NaN),
          NumericConstant.of(Double.POSITIVE_INFINITY),
          NumericConstant.of(Long.MAX_VALUE),
          BooleanConstant.of(true),
          Structure.builder().with("a", "b").build(),
          Binding.of("text"));

  private static Environment environment(final int position) {
    return Environment.of(STANDARD)
        .lexical(List.of("DEFAULT"), position)
        .with(Environment.POSITION, NumericConstant.of(position))
        .with(Environment.END, BooleanConstant.of(false))
        .with(Environment.NEXT, Structure.EMPTY);
  }

  private static boolean test(
      final Environment environment, final String name, final Value... args) {
    return environment.test(new Expression.Call(name, List.of(args)));
  }

  /** Every builtin, at its minimum arity, with every argument kind, returns true or false. */
  @Test
  void testEveryBuiltinTotal() {
    final var environment = environment(1);
    final var names = new ArrayList<String>();
    for (final var phase : Predicates.Phase.values()) {
      names.addAll(STANDARD.names(phase));
    }
    Collections.sort(names);
    for (final var name : names) {
      final var entry = STANDARD.entry(name);
      for (final var argument : ARGUMENTS) {
        final var args = Collections.nCopies(entry.minimum(), argument).toArray(Value[]::new);
        if (entry.minimum() == 0) {
          continue;
        }
        assertDoesNotThrow(() -> test(environment, name, args), name + "(" + argument + ")");
      }
    }
  }

  @Test
  void testOversizedPositionIsFalse() {
    assertFalse(
        test(environment(1), "at_position", StringConstant.of("999999999999999999999999999999")));
  }

  @Test
  void testFractionalPositionIsFalse() {
    assertFalse(test(environment(1), "at_position", StringConstant.of("1.5")));
    assertFalse(test(environment(1), "at_position", NumericConstant.of(1.5)));
  }

  @Test
  void testNonFinitePositionIsFalse() {
    assertFalse(test(environment(0), "at_position", NumericConstant.of(Double.NaN)));
    assertFalse(test(environment(0), "at_char_position", NumericConstant.of(Double.NaN)));
  }

  @Test
  void testOversizedLexicalNumberIsFalse() {
    assertFalse(
        test(environment(0), "at_char_position", StringConstant.of("99999999999999999999999")));
    assertFalse(test(environment(0), "state_depth", StringConstant.of("99999999999999999999999")));
  }

  @Test
  void testIntegralPositionsStillMatch() {
    assertTrue(test(environment(1), "at_position", StringConstant.of("1")));
    assertTrue(test(environment(1), "at_position", NumericConstant.of(1)));
    assertTrue(test(environment(1), "at_position", NumericConstant.of(1.0)));
  }

  @Test
  void testComparisonsWithNonFiniteNumbers() {
    final var environment = environment(0);
    final var nan = NumericConstant.of(Double.NaN);
    final var infinity = NumericConstant.of(Double.POSITIVE_INFINITY);
    assertFalse(test(environment, "lt", nan, NumericConstant.of(1)));
    assertFalse(test(environment, "ge", nan, nan));
    assertTrue(test(environment, "lt", NumericConstant.of(Long.MAX_VALUE), infinity));
    assertTrue(test(environment, "gt", infinity, NumericConstant.of(1.5)));
  }
}
