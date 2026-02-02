package com.libdbm.ugf.constraints;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Value;
import com.libdbm.ugf.features.Variable;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for Predicate. */
class PredicateTests {

  @Nested
  @DisplayName("Factory methods")
  class FactoryMethods {

    @Test
    @DisplayName("of with name and args uses REQUIRED strength and priority 0")
    void of_name_args() {
      final var args = List.<Value>of(Variable.of("?x"));
      final var p = Predicate.of("test", args);

      assertEquals("test", p.name());
      assertEquals(args, p.args());
      assertEquals(Strength.REQUIRED, p.strength());
      assertEquals(0, p.priority());
    }

    @Test
    @DisplayName("of with name, args, and priority uses REQUIRED strength")
    void of_name_args_priority() {
      final var args = List.<Value>of(StringConstant.of("hello"));
      final var p = Predicate.of("check", args, 5);

      assertEquals("check", p.name());
      assertEquals(5, p.priority());
      assertEquals(Strength.REQUIRED, p.strength());
    }

    @Test
    @DisplayName("of with name, args, and strength uses priority 0")
    void of_name_args_strength() {
      final var args = List.<Value>of();
      final var p = Predicate.of("empty", args, Strength.DEFEASIBLE);

      assertEquals(Strength.DEFEASIBLE, p.strength());
      assertEquals(0, p.priority());
    }

    @Test
    @DisplayName("of with all parameters")
    void of_all_params() {
      final var args = List.<Value>of(Variable.of("?a"), Variable.of("?b"));
      final var p = Predicate.of("full", args, Strength.DEFEASIBLE, 10);

      assertEquals("full", p.name());
      assertEquals(args, p.args());
      assertEquals(Strength.DEFEASIBLE, p.strength());
      assertEquals(10, p.priority());
    }
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    @Test
    @DisplayName("throws on null name")
    void throws_on_null_name() {
      assertThrows(NullPointerException.class, () -> Predicate.of(null, List.of()));
    }

    @Test
    @DisplayName("throws on blank name")
    void throws_on_blank_name() {
      assertThrows(IllegalArgumentException.class, () -> Predicate.of("", List.of()));
      assertThrows(IllegalArgumentException.class, () -> Predicate.of("   ", List.of()));
    }

    @Test
    @DisplayName("accepts single character name")
    void accepts_single_char() {
      final var p = Predicate.of("x", List.of());
      assertEquals("x", p.name());
    }
  }

  @Nested
  @DisplayName("Record behavior")
  class RecordBehavior {
    @Test
    @DisplayName("equals and hashCode work correctly")
    void equals_hashcode() {
      final var args = List.<Value>of(Variable.of("?x"));
      final var p1 = Predicate.of("test", args);
      final var p2 = Predicate.of("test", args);
      final var p3 = Predicate.of("other", args);

      assertEquals(p1, p2);
      assertEquals(p1.hashCode(), p2.hashCode());
      assertNotEquals(p1, p3);
    }

    @Test
    @DisplayName("toString includes name")
    void to_string() {
      final var p = Predicate.of("myPredicate", List.of());
      assertTrue(p.toString().contains("myPredicate"));
    }
  }
}
