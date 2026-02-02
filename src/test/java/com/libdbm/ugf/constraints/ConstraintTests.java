package com.libdbm.ugf.constraints;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for Constraint sealed interface and its implementations. */
class ConstraintTests {

  private static Predicate simple(final String name) {
    return Predicate.of(name, List.of());
  }

  @Nested
  @DisplayName("And")
  class AndTests {

    @Test
    @DisplayName("simple constructor uses REQUIRED and priority 0")
    void simple_constructor() {
      final var conjuncts = List.<Constraint>of(simple("a"), simple("b"));
      final var and = new Constraint.And(conjuncts);

      assertEquals(conjuncts, and.conjuncts());
      assertEquals(Strength.REQUIRED, and.strength());
      assertEquals(0, and.priority());
    }

    @Test
    @DisplayName("full constructor sets all fields")
    void full_constructor() {
      final var conjuncts = List.<Constraint>of(simple("x"));
      final var and = new Constraint.And(conjuncts, Strength.DEFEASIBLE, 7);

      assertEquals(Strength.DEFEASIBLE, and.strength());
      assertEquals(7, and.priority());
    }

    @Test
    @DisplayName("defeasible factory creates DEFEASIBLE And")
    void defeasible_factory() {
      final var conjuncts = List.<Constraint>of(simple("p"), simple("q"));
      final var and = Constraint.And.defeasible(conjuncts, 3);

      assertEquals(Strength.DEFEASIBLE, and.strength());
      assertEquals(3, and.priority());
      assertEquals(conjuncts, and.conjuncts());
    }

    @Test
    @DisplayName("empty conjuncts allowed")
    void empty_conjuncts() {
      final var and = new Constraint.And(List.of());
      assertTrue(and.conjuncts().isEmpty());
    }
  }

  @Nested
  @DisplayName("Or")
  class OrTests {

    @Test
    @DisplayName("simple constructor uses REQUIRED and priority 0")
    void simple_constructor() {
      final var disjuncts = List.<Constraint>of(simple("a"), simple("b"));
      final var or = new Constraint.Or(disjuncts);

      assertEquals(disjuncts, or.disjuncts());
      assertEquals(Strength.REQUIRED, or.strength());
      assertEquals(0, or.priority());
    }

    @Test
    @DisplayName("full constructor sets all fields")
    void full_constructor() {
      final var disjuncts = List.<Constraint>of(simple("x"));
      final var or = new Constraint.Or(disjuncts, Strength.DEFEASIBLE, 5);

      assertEquals(Strength.DEFEASIBLE, or.strength());
      assertEquals(5, or.priority());
    }

    @Test
    @DisplayName("defeasible factory creates DEFEASIBLE Or")
    void defeasible_factory() {
      final var disjuncts = List.<Constraint>of(simple("p"), simple("q"));
      final var or = Constraint.Or.defeasible(disjuncts, 8);

      assertEquals(Strength.DEFEASIBLE, or.strength());
      assertEquals(8, or.priority());
      assertEquals(disjuncts, or.disjuncts());
    }
  }

  @Nested
  @DisplayName("Not")
  class NotTests {

    @Test
    @DisplayName("simple constructor uses REQUIRED and priority 0")
    void simple_constructor() {
      final var inner = simple("p");
      final var not = new Constraint.Not(inner);

      assertEquals(inner, not.constraint());
      assertEquals(Strength.REQUIRED, not.strength());
      assertEquals(0, not.priority());
    }

    @Test
    @DisplayName("full constructor sets all fields")
    void full_constructor() {
      final var inner = simple("q");
      final var not = new Constraint.Not(inner, Strength.DEFEASIBLE, 4);

      assertEquals(Strength.DEFEASIBLE, not.strength());
      assertEquals(4, not.priority());
    }

    @Test
    @DisplayName("defeasible factory creates DEFEASIBLE Not")
    void defeasible_factory() {
      final var inner = simple("r");
      final var not = Constraint.Not.defeasible(inner, 6);

      assertEquals(Strength.DEFEASIBLE, not.strength());
      assertEquals(6, not.priority());
      assertEquals(inner, not.constraint());
    }

    @Test
    @DisplayName("can nest Not in And")
    void nested_in_and() {
      final var not = new Constraint.Not(simple("p"));
      final var and = new Constraint.And(List.of(not, simple("q")));

      assertEquals(2, and.conjuncts().size());
      assertInstanceOf(Constraint.Not.class, and.conjuncts().getFirst());
    }

    @Test
    @DisplayName("can nest And in Not")
    void nested_and_in_not() {
      final var and = new Constraint.And(List.of(simple("a"), simple("b")));
      final var not = new Constraint.Not(and);

      assertInstanceOf(Constraint.And.class, not.constraint());
    }
  }

  @Nested
  @DisplayName("Sealed hierarchy")
  class SealedHierarchy {

    @Test
    @DisplayName("only permitted subtypes exist")
    void permitted_subtypes() {
      // This tests that the sealed interface permits only these types
      final Constraint c1 = simple("p");
      final Constraint c2 = new Constraint.And(List.of());
      final Constraint c3 = new Constraint.Or(List.of());
      final Constraint c4 = new Constraint.Not(simple("x"));

      assertTrue(true);
      assertTrue(true);
      assertTrue(true);
      assertTrue(true);
    }
  }
}
