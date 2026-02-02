package com.libdbm.ugf.grammar;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.constraints.Constraint;
import com.libdbm.ugf.constraints.Predicate;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for parsing constraint expressions with disjunction (|) and negation (!).
 *
 * <p>Precedence (highest to lowest): ! -> , -> |
 */
class ConstraintParsingTests {

  private static Constraint parse(final String rule) {
    // Parse with unvalidated to avoid needing defined nonterminals
    final var grammar = UnificationGrammarParserFactory.unvalidated(rule);
    final var constraints = grammar.rules().values().iterator().next().getFirst().constraints();
    assertFalse(constraints.isEmpty(), "Expected at least one constraint");
    return constraints.getFirst();
  }

  @Nested
  @DisplayName("Simple predicates")
  class SimplePredicates {

    @Test
    @DisplayName("parses single predicate")
    void single() {
      final var c = parse("s --> 'a' where foo(X);");
      assertInstanceOf(Predicate.class, c);
      assertEquals("foo", ((Predicate) c).name());
    }

    @Test
    @DisplayName("parses predicate with multiple arguments")
    void multiple_args() {
      final var c = parse("s --> 'a' where agree(X, Y);");
      assertInstanceOf(Predicate.class, c);
      final var p = (Predicate) c;
      assertEquals("agree", p.name());
      assertEquals(2, p.args().size());
    }
  }

  @Nested
  @DisplayName("Negation (!)")
  class Negation {

    @Test
    @DisplayName("parses negated predicate")
    void negated() {
      final var c = parse("s --> 'a' where !reserved(X);");
      assertInstanceOf(Constraint.Not.class, c);
      final var inner = ((Constraint.Not) c).constraint();
      assertInstanceOf(Predicate.class, inner);
      assertEquals("reserved", ((Predicate) inner).name());
    }

    @Test
    @DisplayName("parses double negation")
    void double_negation() {
      final var c = parse("s --> 'a' where !!confirmed(X);");
      assertInstanceOf(Constraint.Not.class, c);
      final var inner1 = ((Constraint.Not) c).constraint();
      assertInstanceOf(Constraint.Not.class, inner1);
      final var inner2 = ((Constraint.Not) inner1).constraint();
      assertInstanceOf(Predicate.class, inner2);
      assertEquals("confirmed", ((Predicate) inner2).name());
    }
  }

  @Nested
  @DisplayName("Disjunction (|)")
  class Disjunction {

    @Test
    @DisplayName("parses simple disjunction")
    void simple() {
      final var c = parse("s --> 'a' where british(X) | american(X);");
      assertInstanceOf(Constraint.Or.class, c);
      final var or = (Constraint.Or) c;
      assertEquals(2, or.disjuncts().size());
      assertInstanceOf(Predicate.class, or.disjuncts().get(0));
      assertInstanceOf(Predicate.class, or.disjuncts().get(1));
    }

    @Test
    @DisplayName("parses three-way disjunction")
    void three_way() {
      final var c = parse("s --> 'a' where a(X) | b(X) | c(X);");
      assertInstanceOf(Constraint.Or.class, c);
      assertEquals(3, ((Constraint.Or) c).disjuncts().size());
    }
  }

  @Nested
  @DisplayName("Conjunction (,)")
  class Conjunction {

    @Test
    @DisplayName("parses conjunction as And")
    void simple() {
      final var c = parse("s --> 'a' where foo(X), bar(Y);");
      assertInstanceOf(Constraint.And.class, c);
      final var and = (Constraint.And) c;
      assertEquals(2, and.conjuncts().size());
    }

    @Test
    @DisplayName("parses three-way conjunction")
    void three_way() {
      final var c = parse("s --> 'a' where a(X), b(X), c(X);");
      assertInstanceOf(Constraint.And.class, c);
      assertEquals(3, ((Constraint.And) c).conjuncts().size());
    }
  }

  @Nested
  @DisplayName("Precedence")
  class Precedence {

    @Test
    @DisplayName("AND binds tighter than OR: a, b | c parses as (a AND b) OR c")
    void and_before_or() {
      final var c = parse("s --> 'a' where a(X), b(X) | c(X);");
      // Should be: Or(And(a, b), c)
      assertInstanceOf(Constraint.Or.class, c);
      final var or = (Constraint.Or) c;
      assertEquals(2, or.disjuncts().size());
      // First disjunct is And(a, b)
      assertInstanceOf(Constraint.And.class, or.disjuncts().get(0));
      final var and = (Constraint.And) or.disjuncts().get(0);
      assertEquals(2, and.conjuncts().size());
      // Second disjunct is c
      assertInstanceOf(Predicate.class, or.disjuncts().get(1));
    }

    @Test
    @DisplayName("NOT binds tighter than AND: !a, b parses as (NOT a) AND b")
    void not_before_and() {
      final var c = parse("s --> 'a' where !a(X), b(X);");
      // Should be: And(Not(a), b)
      assertInstanceOf(Constraint.And.class, c);
      final var and = (Constraint.And) c;
      assertEquals(2, and.conjuncts().size());
      assertInstanceOf(Constraint.Not.class, and.conjuncts().get(0));
      assertInstanceOf(Predicate.class, and.conjuncts().get(1));
    }

    @Test
    @DisplayName("NOT binds tighter than OR: !a | b parses as (NOT a) OR b")
    void not_before_or() {
      final var c = parse("s --> 'a' where !a(X) | b(X);");
      // Should be: Or(Not(a), b)
      assertInstanceOf(Constraint.Or.class, c);
      final var or = (Constraint.Or) c;
      assertEquals(2, or.disjuncts().size());
      assertInstanceOf(Constraint.Not.class, or.disjuncts().get(0));
      assertInstanceOf(Predicate.class, or.disjuncts().get(1));
    }
  }

  @Nested
  @DisplayName("Grouping with parentheses")
  class Grouping {

    @Test
    @DisplayName("parentheses override precedence: a, (b | c)")
    void paren_overrides_precedence() {
      final var c = parse("s --> 'a' where a(X), (b(X) | c(X));");
      // Should be: And(a, Or(b, c))
      assertInstanceOf(Constraint.And.class, c);
      final var and = (Constraint.And) c;
      assertEquals(2, and.conjuncts().size());
      assertInstanceOf(Predicate.class, and.conjuncts().get(0));
      assertInstanceOf(Constraint.Or.class, and.conjuncts().get(1));
    }

    @Test
    @DisplayName("negation of grouped expression: !(a | b)")
    void negation_of_group() {
      final var c = parse("s --> 'a' where !(a(X) | b(X));");
      // Should be: Not(Or(a, b))
      assertInstanceOf(Constraint.Not.class, c);
      final var inner = ((Constraint.Not) c).constraint();
      assertInstanceOf(Constraint.Or.class, inner);
      assertEquals(2, ((Constraint.Or) inner).disjuncts().size());
    }

    @Test
    @DisplayName("nested grouping: ((a | b), c) | d")
    void nested_grouping() {
      final var c = parse("s --> 'a' where ((a(X) | b(X)), c(X)) | d(X);");
      // Should be: Or(And(Or(a, b), c), d)
      assertInstanceOf(Constraint.Or.class, c);
      final var or = (Constraint.Or) c;
      assertEquals(2, or.disjuncts().size());
      assertInstanceOf(Constraint.And.class, or.disjuncts().get(0));
      assertInstanceOf(Predicate.class, or.disjuncts().get(1));
    }
  }

  @Nested
  @DisplayName("Complex expressions")
  class Complex {

    @Test
    @DisplayName("realistic agreement fallback pattern")
    void agreement_fallback() {
      final var c = parse("s --> np:n vp:v where agree(n, v) | (!strict(), lenient(n, v));");
      // Should be: Or(agree, And(Not(strict), lenient))
      assertInstanceOf(Constraint.Or.class, c);
      final var or = (Constraint.Or) c;
      assertEquals(2, or.disjuncts().size());
      // First: agree(n, v)
      assertInstanceOf(Predicate.class, or.disjuncts().get(0));
      // Second: And(Not(strict), lenient)
      assertInstanceOf(Constraint.And.class, or.disjuncts().get(1));
      final var and = (Constraint.And) or.disjuncts().get(1);
      assertInstanceOf(Constraint.Not.class, and.conjuncts().get(0));
      assertInstanceOf(Predicate.class, and.conjuncts().get(1));
    }

    @Test
    @DisplayName("de Morgan style: !(error | warning)")
    void de_morgan() {
      final var c = parse("s --> 'a' where !(error(X) | warning(X));");
      assertInstanceOf(Constraint.Not.class, c);
      final var inner = ((Constraint.Not) c).constraint();
      assertInstanceOf(Constraint.Or.class, inner);
    }

    @Test
    @DisplayName("multiple negations in conjunction: !a, !b, !c")
    void multiple_negations() {
      final var c = parse("s --> 'a' where !a(X), !b(X), !c(X);");
      assertInstanceOf(Constraint.And.class, c);
      final var and = (Constraint.And) c;
      assertEquals(3, and.conjuncts().size());
      for (final var conj : and.conjuncts()) {
        assertInstanceOf(Constraint.Not.class, conj);
      }
    }
  }
}
