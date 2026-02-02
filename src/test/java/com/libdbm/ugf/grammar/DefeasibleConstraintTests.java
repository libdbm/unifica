package com.libdbm.ugf.grammar;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.constraints.Constraint;
import com.libdbm.ugf.constraints.Predicate;
import com.libdbm.ugf.constraints.Strength;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for defeasible constraint penalty notation in grammar files.
 *
 * <p>Syntax: {@code predicate(X):N} where N is the penalty value. Without penalty, constraints are
 * required (hard).
 */
@DisplayName("Defeasible Constraint Parsing")
class DefeasibleConstraintTests {

  @Nested
  @DisplayName("Predicate Penalties")
  class PredicatePenalties {

    @Test
    @DisplayName("predicate without penalty is REQUIRED")
    void required() {
      final var grammar = UnificationGrammarParserFactory.unvalidated("rule --> 'a' where foo(X);");

      final var rule = grammar.rulesFor("rule").getFirst();
      final var constraint = rule.constraints().getFirst();

      assertInstanceOf(Predicate.class, constraint);
      final var pred = (Predicate) constraint;
      assertEquals("foo", pred.name());
      assertEquals(Strength.REQUIRED, pred.strength());
      assertEquals(0, pred.priority());
    }

    @Test
    @DisplayName("predicate with penalty is DEFEASIBLE")
    void defeasible() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated("rule --> 'a' where foo(X):10;");

      final var rule = grammar.rulesFor("rule").getFirst();
      final var constraint = rule.constraints().getFirst();

      assertInstanceOf(Predicate.class, constraint);
      final var pred = (Predicate) constraint;
      assertEquals("foo", pred.name());
      assertEquals(Strength.DEFEASIBLE, pred.strength());
      assertEquals(10, pred.priority());
    }

    @Test
    @DisplayName("different penalty values")
    void penalties() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated("rule --> 'a' where foo(X):5, bar(Y):100;");

      final var rule = grammar.rulesFor("rule").getFirst();
      final var constraint = rule.constraints().getFirst();

      assertInstanceOf(Constraint.And.class, constraint);
      final var and = (Constraint.And) constraint;
      assertEquals(2, and.conjuncts().size());

      final var first = (Predicate) and.conjuncts().get(0);
      assertEquals("foo", first.name());
      assertEquals(Strength.DEFEASIBLE, first.strength());
      assertEquals(5, first.priority());

      final var second = (Predicate) and.conjuncts().get(1);
      assertEquals("bar", second.name());
      assertEquals(Strength.DEFEASIBLE, second.strength());
      assertEquals(100, second.priority());
    }

    @Test
    @DisplayName("mixed required and defeasible")
    void mixed() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated("rule --> 'a' where foo(X), bar(Y):10;");

      final var rule = grammar.rulesFor("rule").getFirst();
      final var constraint = rule.constraints().getFirst();

      assertInstanceOf(Constraint.And.class, constraint);
      final var and = (Constraint.And) constraint;
      assertEquals(2, and.conjuncts().size());

      final var first = (Predicate) and.conjuncts().get(0);
      assertEquals(Strength.REQUIRED, first.strength());

      final var second = (Predicate) and.conjuncts().get(1);
      assertEquals(Strength.DEFEASIBLE, second.strength());
      assertEquals(10, second.priority());
    }
  }

  @Nested
  @DisplayName("Grouped Expressions")
  class GroupedExpressions {

    @Test
    @DisplayName("grouped AND with penalty")
    void groupedAnd() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated("rule --> 'a' where (foo(X), bar(Y)):5;");

      final var rule = grammar.rulesFor("rule").getFirst();
      final var constraint = rule.constraints().getFirst();

      assertInstanceOf(Constraint.And.class, constraint);
      final var and = (Constraint.And) constraint;
      assertEquals(Strength.DEFEASIBLE, and.strength());
      assertEquals(5, and.priority());
    }

    @Test
    @DisplayName("grouped OR with penalty")
    void groupedOr() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated("rule --> 'a' where (foo(X) | bar(Y)):8;");

      final var rule = grammar.rulesFor("rule").getFirst();
      final var constraint = rule.constraints().getFirst();

      assertInstanceOf(Constraint.Or.class, constraint);
      final var or = (Constraint.Or) constraint;
      assertEquals(Strength.DEFEASIBLE, or.strength());
      assertEquals(8, or.priority());
    }

    @Test
    @DisplayName("grouped without penalty is required")
    void groupedRequired() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated("rule --> 'a' where (foo(X), bar(Y));");

      final var rule = grammar.rulesFor("rule").getFirst();
      final var constraint = rule.constraints().getFirst();

      assertInstanceOf(Constraint.And.class, constraint);
      final var and = (Constraint.And) constraint;
      assertEquals(Strength.REQUIRED, and.strength());
      assertEquals(0, and.priority());
    }
  }

  @Nested
  @DisplayName("Complex Expressions")
  class ComplexExpressions {

    @Test
    @DisplayName("NOT with defeasible inner")
    void notWithDefeasible() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated("rule --> 'a' where !foo(X):10;");

      final var rule = grammar.rulesFor("rule").getFirst();
      final var constraint = rule.constraints().getFirst();

      // NOT wraps a defeasible predicate
      assertInstanceOf(Constraint.Not.class, constraint);
      final var not = (Constraint.Not) constraint;

      // The NOT itself is required, but inner predicate is defeasible
      assertInstanceOf(Predicate.class, not.constraint());
      final var inner = (Predicate) not.constraint();
      assertEquals(Strength.DEFEASIBLE, inner.strength());
      assertEquals(10, inner.priority());
    }

    @Test
    @DisplayName("disjunction with mixed penalties")
    void orMixed() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated("rule --> 'a' where foo(X):5 | bar(Y);");

      final var rule = grammar.rulesFor("rule").getFirst();
      final var constraint = rule.constraints().getFirst();

      assertInstanceOf(Constraint.Or.class, constraint);
      final var or = (Constraint.Or) constraint;
      assertEquals(2, or.disjuncts().size());

      final var first = (Predicate) or.disjuncts().get(0);
      assertEquals(Strength.DEFEASIBLE, first.strength());
      assertEquals(5, first.priority());

      final var second = (Predicate) or.disjuncts().get(1);
      assertEquals(Strength.REQUIRED, second.strength());
    }

    @Test
    @DisplayName("nested groups with penalties")
    void nestedGroups() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated(
              "rule --> 'a' where ((foo(X), bar(Y)):3 | baz(Z)):7;");

      final var rule = grammar.rulesFor("rule").getFirst();
      final var constraint = rule.constraints().getFirst();

      // Outer OR has penalty 7
      assertInstanceOf(Constraint.Or.class, constraint);
      final var or = (Constraint.Or) constraint;
      assertEquals(Strength.DEFEASIBLE, or.strength());
      assertEquals(7, or.priority());

      // First disjunct is AND with penalty 3
      final var inner = or.disjuncts().get(0);
      assertInstanceOf(Constraint.And.class, inner);
      final var and = (Constraint.And) inner;
      assertEquals(Strength.DEFEASIBLE, and.strength());
      assertEquals(3, and.priority());
    }
  }

  @Nested
  @DisplayName("Real-World Examples")
  class RealWorld {

    @Test
    @DisplayName("agreement (hard) with selectional (soft)")
    void agreementPlusSelectional() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated(
              """
              clause --> np:S vp:V
                  where equals(S.num, V.num), verb_allows(V.lemma, S.type):10;
              """);

      final var rule = grammar.rulesFor("clause").getFirst();
      final var constraint = rule.constraints().getFirst();

      assertInstanceOf(Constraint.And.class, constraint);
      final var and = (Constraint.And) constraint;

      // First: hard agreement
      final var agreement = (Predicate) and.conjuncts().get(0);
      assertEquals("equals", agreement.name());
      assertEquals(Strength.REQUIRED, agreement.strength());

      // Second: soft selectional
      final var selectional = (Predicate) and.conjuncts().get(1);
      assertEquals("verb_allows", selectional.name());
      assertEquals(Strength.DEFEASIBLE, selectional.strength());
      assertEquals(10, selectional.priority());
    }

    @Test
    @DisplayName("multiple soft preferences")
    void multiplePreferences() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated(
              """
              np --> det noun:N
                  where animate(N.text):5, concrete(N.text):3;
              """);

      final var rule = grammar.rulesFor("np").getFirst();
      final var constraint = rule.constraints().getFirst();

      assertInstanceOf(Constraint.And.class, constraint);
      final var and = (Constraint.And) constraint;

      final var animate = (Predicate) and.conjuncts().get(0);
      assertEquals(5, animate.priority());

      final var concrete = (Predicate) and.conjuncts().get(1);
      assertEquals(3, concrete.priority());
    }
  }
}
