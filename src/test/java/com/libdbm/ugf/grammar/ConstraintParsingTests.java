package com.libdbm.ugf.grammar;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.constraints.Expression;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for parsing constraint expressions with disjunction (|) and negation (!).
 *
 * <p>Precedence (highest to lowest): ! -> , -> |
 */
class ConstraintParsingTests {

  private static Expression parse(final String rule) {
    // Parse with unvalidated to avoid needing defined nonterminals
    final var grammar = UnificationGrammarParserFactory.unvalidated(rule).orElseThrow();
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
      final var c = parse("s --> 'a' where foo($X);");
      assertInstanceOf(Expression.Call.class, c);
      assertEquals("foo", ((Expression.Call) c).name());
    }

    @Test
    @DisplayName("parses predicate with multiple arguments")
    void multiple_args() {
      final var c = parse("s --> 'a' where agree($X, $Y);");
      assertInstanceOf(Expression.Call.class, c);
      final var p = (Expression.Call) c;
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
      final var c = parse("s --> 'a' where !reserved($X);");
      assertInstanceOf(Expression.Not.class, c);
      final var inner = ((Expression.Not) c).term();
      assertInstanceOf(Expression.Call.class, inner);
      assertEquals("reserved", ((Expression.Call) inner).name());
    }

    @Test
    @DisplayName("parses double negation")
    void double_negation() {
      final var c = parse("s --> 'a' where !!confirmed($X);");
      assertInstanceOf(Expression.Not.class, c);
      final var inner1 = ((Expression.Not) c).term();
      assertInstanceOf(Expression.Not.class, inner1);
      final var inner2 = ((Expression.Not) inner1).term();
      assertInstanceOf(Expression.Call.class, inner2);
      assertEquals("confirmed", ((Expression.Call) inner2).name());
    }
  }

  @Nested
  @DisplayName("Disjunction (|)")
  class Disjunction {

    @Test
    @DisplayName("parses simple disjunction")
    void simple() {
      final var c = parse("s --> 'a' where british($X) | american($X);");
      assertInstanceOf(Expression.Or.class, c);
      final var or = (Expression.Or) c;
      assertEquals(2, or.terms().size());
      assertInstanceOf(Expression.Call.class, or.terms().get(0));
      assertInstanceOf(Expression.Call.class, or.terms().get(1));
    }

    @Test
    @DisplayName("parses three-way disjunction")
    void three_way() {
      final var c = parse("s --> 'a' where a($X) | b($X) | c($X);");
      assertInstanceOf(Expression.Or.class, c);
      assertEquals(3, ((Expression.Or) c).terms().size());
    }
  }

  @Nested
  @DisplayName("Conjunction (,)")
  class Conjunction {

    @Test
    @DisplayName("parses conjunction as And")
    void simple() {
      final var c = parse("s --> 'a' where foo($X), bar($Y);");
      assertInstanceOf(Expression.And.class, c);
      final var and = (Expression.And) c;
      assertEquals(2, and.terms().size());
    }

    @Test
    @DisplayName("parses three-way conjunction")
    void three_way() {
      final var c = parse("s --> 'a' where a($X), b($X), c($X);");
      assertInstanceOf(Expression.And.class, c);
      assertEquals(3, ((Expression.And) c).terms().size());
    }
  }

  @Nested
  @DisplayName("Precedence")
  class Precedence {

    @Test
    @DisplayName("AND binds tighter than OR: a, b | c parses as (a AND b) OR c")
    void and_before_or() {
      final var c = parse("s --> 'a' where a($X), b($X) | c($X);");
      // Should be: Or(And(a, b), c)
      assertInstanceOf(Expression.Or.class, c);
      final var or = (Expression.Or) c;
      assertEquals(2, or.terms().size());
      // First disjunct is And(a, b)
      assertInstanceOf(Expression.And.class, or.terms().get(0));
      final var and = (Expression.And) or.terms().get(0);
      assertEquals(2, and.terms().size());
      // Second disjunct is c
      assertInstanceOf(Expression.Call.class, or.terms().get(1));
    }

    @Test
    @DisplayName("NOT binds tighter than AND: !a, b parses as (NOT a) AND b")
    void not_before_and() {
      final var c = parse("s --> 'a' where !a($X), b($X);");
      // Should be: And(Not(a), b)
      assertInstanceOf(Expression.And.class, c);
      final var and = (Expression.And) c;
      assertEquals(2, and.terms().size());
      assertInstanceOf(Expression.Not.class, and.terms().get(0));
      assertInstanceOf(Expression.Call.class, and.terms().get(1));
    }

    @Test
    @DisplayName("NOT binds tighter than OR: !a | b parses as (NOT a) OR b")
    void not_before_or() {
      final var c = parse("s --> 'a' where !a($X) | b($X);");
      // Should be: Or(Not(a), b)
      assertInstanceOf(Expression.Or.class, c);
      final var or = (Expression.Or) c;
      assertEquals(2, or.terms().size());
      assertInstanceOf(Expression.Not.class, or.terms().get(0));
      assertInstanceOf(Expression.Call.class, or.terms().get(1));
    }
  }

  @Nested
  @DisplayName("Grouping with parentheses")
  class Grouping {

    @Test
    @DisplayName("parentheses override precedence: a, (b | c)")
    void paren_overrides_precedence() {
      final var c = parse("s --> 'a' where a($X), (b($X) | c($X));");
      // Should be: And(a, Or(b, c))
      assertInstanceOf(Expression.And.class, c);
      final var and = (Expression.And) c;
      assertEquals(2, and.terms().size());
      assertInstanceOf(Expression.Call.class, and.terms().get(0));
      assertInstanceOf(Expression.Or.class, and.terms().get(1));
    }

    @Test
    @DisplayName("negation of grouped expression: !(a | b)")
    void negation_of_group() {
      final var c = parse("s --> 'a' where !(a($X) | b($X));");
      // Should be: Not(Or(a, b))
      assertInstanceOf(Expression.Not.class, c);
      final var inner = ((Expression.Not) c).term();
      assertInstanceOf(Expression.Or.class, inner);
      assertEquals(2, ((Expression.Or) inner).terms().size());
    }

    @Test
    @DisplayName("nested grouping: ((a | b), c) | d")
    void nested_grouping() {
      final var c = parse("s --> 'a' where ((a($X) | b($X)), c($X)) | d($X);");
      // Should be: Or(And(Or(a, b), c), d)
      assertInstanceOf(Expression.Or.class, c);
      final var or = (Expression.Or) c;
      assertEquals(2, or.terms().size());
      assertInstanceOf(Expression.And.class, or.terms().get(0));
      assertInstanceOf(Expression.Call.class, or.terms().get(1));
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
      assertInstanceOf(Expression.Or.class, c);
      final var or = (Expression.Or) c;
      assertEquals(2, or.terms().size());
      // First: agree(n, v)
      assertInstanceOf(Expression.Call.class, or.terms().get(0));
      // Second: And(Not(strict), lenient)
      assertInstanceOf(Expression.And.class, or.terms().get(1));
      final var and = (Expression.And) or.terms().get(1);
      assertInstanceOf(Expression.Not.class, and.terms().get(0));
      assertInstanceOf(Expression.Call.class, and.terms().get(1));
    }

    @Test
    @DisplayName("de Morgan style: !(error | warning)")
    void de_morgan() {
      final var c = parse("s --> 'a' where !(error($X) | warning($X));");
      assertInstanceOf(Expression.Not.class, c);
      final var inner = ((Expression.Not) c).term();
      assertInstanceOf(Expression.Or.class, inner);
    }

    @Test
    @DisplayName("multiple negations in conjunction: !a, !b, !c")
    void multiple_negations() {
      final var c = parse("s --> 'a' where !a($X), !b($X), !c($X);");
      assertInstanceOf(Expression.And.class, c);
      final var and = (Expression.And) c;
      assertEquals(3, and.terms().size());
      for (final var conj : and.terms()) {
        assertInstanceOf(Expression.Not.class, conj);
      }
    }
  }

  @Nested
  @DisplayName("Production kind (S-G1)")
  class Kinds {

    private GrammarRule.Kind kind(final String source, final String symbol) {
      return UnificationGrammarParserFactory.unvalidated(source)
          .orElseThrow()
          .rulesFor(symbol)
          .getFirst()
          .kind();
    }

    @Test
    void testLexicalKindRecorded() {
      assertEquals(GrammarRule.Kind.LEXICAL, kind("start K; K --> 'if';", "K"));
      assertEquals(GrammarRule.Kind.LEXICAL, kind("start S; S --> '\"' [^\"]* '\"';", "S"));
      assertEquals(GrammarRule.Kind.LEXICAL, kind("start b; b --> {IN} 'y';", "b"));
      assertEquals(GrammarRule.Kind.LEXICAL, kind("start o; o --> '<' ==> IN;", "o"));
      assertEquals(GrammarRule.Kind.LEXICAL, kind("start I; I --> ([a-z] | '_')+;", "I"));
    }

    @Test
    void testSyntacticKindRecorded() {
      assertEquals(GrammarRule.Kind.SYNTACTIC, kind("start A; A --> 'a' 'b';", "A"));
      assertEquals(GrammarRule.Kind.SYNTACTIC, kind("start S; S --> A 'x'; A --> 'a';", "S"));
      assertEquals(GrammarRule.Kind.SYNTACTIC, kind("start T; T --> {TOKEN};", "T"));
      assertEquals(GrammarRule.Kind.SYNTACTIC, kind("start A; A --> 'a'+;", "A"));
    }

    private long cost(final String source, final String symbol) {
      return UnificationGrammarParserFactory.unvalidated(source)
          .orElseThrow()
          .rulesFor(symbol)
          .getFirst()
          .cost();
    }

    @Test
    void testProductionCost() {
      assertEquals(
          2L, cost("start np; np --> det n where agree(det, n) @2; det --> 'a'; n --> 'b';", "np"));
      assertEquals(1L, cost("start S; S --> A @1; A --> 'a';", "S"));
      assertEquals(5L, cost("start o; o --> '<' ==> IN @5;", "o"));
      assertEquals(0L, cost("start S; S --> A; A --> 'a';", "S"));
      // "cost" is not a keyword, so it remains available as a symbol name.
      assertEquals(3L, cost("start cost; cost --> A @3; A --> 'a';", "cost"));
    }

    @Test
    void testLiteralEscapes() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated(
                  "start S; S --> '\\'' '\\n' '\\\\' \"\\\"\" '\\u0041';")
              .orElseThrow();

      final var texts =
          grammar.rulesFor("S").getFirst().rhs().stream()
              .map(element -> ((RuleElement.Terminal) element).text())
              .toList();
      assertEquals(List.of("'", "\n", "\\", "\"", "A"), texts);
    }

    @Test
    void testMalformedUnicodeEscape() {
      for (final var literal : List.of("'\\u12'", "'\\uzz12'", "'\\u'")) {
        final var result =
            UnificationGrammarParserFactory.unvalidated("start S; S --> " + literal + ";");
        assertInstanceOf(Result.Failure.class, result, literal);
        assertEquals(
            UnificationGrammarParserFactory.SYNTAX,
            ((Result.Failure<Grammar, ErrorDetails>) result).error().code(),
            literal);
      }
    }

    @Test
    void testSkipStatement() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated(
                  "start S; skip Comment, Line; skip Other; S --> 'a'; Comment --> '#' [a-z]*;")
              .orElseThrow();

      assertEquals(Set.of("Comment", "Line", "Other"), grammar.skips());
    }

    @Test
    void testDefaultsForProgrammaticRules() {
      final var rule = new GrammarRule("S", List.of(new RuleElement.Terminal("x")));

      // Classified by S-G1 like a loaded rule: a single literal is lexical.
      assertEquals(GrammarRule.Kind.LEXICAL, rule.kind());
      assertEquals(0L, rule.cost());
    }
  }
}
