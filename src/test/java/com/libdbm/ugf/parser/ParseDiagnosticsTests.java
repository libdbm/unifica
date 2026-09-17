package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.constraints.Predicate;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for ParseDiagnostics. */
class ParseDiagnosticsTests {

  private ParseDiagnostics diagnostics;

  @BeforeEach
  void setup() {
    diagnostics = new ParseDiagnostics();
  }

  @Nested
  @DisplayName("Initial state")
  class InitialState {

    @Test
    @DisplayName("has no failures initially")
    void no_failures_initially() {
      assertFalse(diagnostics.hasFailures());
    }

    @Test
    @DisplayName("all lists are empty initially")
    void all_lists_empty() {
      assertTrue(diagnostics.constraintFailures().isEmpty());
      assertTrue(diagnostics.tokenizationErrors().isEmpty());
      assertTrue(diagnostics.parsingIssues().isEmpty());
      assertTrue(diagnostics.regexFailures().isEmpty());
      assertTrue(diagnostics.quantifierLoops().isEmpty());
      assertTrue(diagnostics.unificationFailures().isEmpty());
    }
  }

  @Nested
  @DisplayName("Constraint failures")
  class ConstraintFailures {

    @Test
    @DisplayName("records constraint failure")
    void records_constraint_failure() {
      final var constraint = Predicate.of("test", List.of());

      diagnostics.recordConstraintFailure("rule1", constraint, "mismatch", 5, true);

      assertTrue(diagnostics.hasFailures());
      assertEquals(1, diagnostics.constraintFailures().size());

      final var failure = diagnostics.constraintFailures().getFirst();
      assertEquals("rule1", failure.ruleName());
      assertEquals(constraint, failure.constraint());
      assertEquals("mismatch", failure.reason());
      assertEquals(5, failure.position());
      assertTrue(failure.isHard());
    }

    @Test
    @DisplayName("records soft constraint failure")
    void records_soft_constraint_failure() {
      final var constraint = Predicate.of("soft", List.of());

      diagnostics.recordConstraintFailure("rule2", constraint, "preference", 10, false);

      final var failure = diagnostics.constraintFailures().getFirst();
      assertFalse(failure.isHard());
    }
  }

  @Nested
  @DisplayName("Tokenization errors")
  class TokenizationErrors {

    @Test
    @DisplayName("records tokenization error")
    void records_tokenization_error() {
      diagnostics.recordTokenizationError(15, "@#$", "identifier");

      assertTrue(diagnostics.hasFailures());
      assertEquals(1, diagnostics.tokenizationErrors().size());

      final var error = diagnostics.tokenizationErrors().getFirst();
      assertEquals(15, error.position());
      assertEquals("@#$", error.input());
      assertEquals("identifier", error.expected());
    }
  }

  @Nested
  @DisplayName("Parsing issues")
  class ParsingIssues {

    @Test
    @DisplayName("records parsing issue")
    void records_parsing_issue() {
      diagnostics.recordParsingIssue("unexpected end of input", 100);

      assertTrue(diagnostics.hasFailures());
      assertEquals(1, diagnostics.parsingIssues().size());

      final var issue = diagnostics.parsingIssues().getFirst();
      assertEquals("unexpected end of input", issue.message());
      assertEquals(100, issue.position());
    }
  }

  @Nested
  @DisplayName("Regex failures")
  class RegexFailures {

    @Test
    @DisplayName("records regex failure")
    void records_regex_failure() {
      diagnostics.recordRegexFailure("[0-9]+", "abc", 20);

      assertTrue(diagnostics.hasFailures());
      assertEquals(1, diagnostics.regexFailures().size());

      final var failure = diagnostics.regexFailures().getFirst();
      assertEquals("[0-9]+", failure.pattern());
      assertEquals("abc", failure.input());
      assertEquals(20, failure.position());
    }
  }

  @Nested
  @DisplayName("Quantifier loops")
  class QuantifierLoops {

    @Test
    @DisplayName("records quantifier loop")
    void records_quantifier_loop() {
      diagnostics.recordQuantifierLoop("expr", "term*", 1000, 50);

      assertTrue(diagnostics.hasFailures());
      assertEquals(1, diagnostics.quantifierLoops().size());

      final var loop = diagnostics.quantifierLoops().getFirst();
      assertEquals("expr", loop.ruleName());
      assertEquals("term*", loop.element());
      assertEquals(1000, loop.iterations());
      assertEquals(50, loop.position());
    }
  }

  @Nested
  @DisplayName("Unification failures")
  class UnificationFailures {

    @Test
    @DisplayName("records unification failure")
    void records_unification_failure() {
      final var waiting = new Structure();
      waiting.set("num", new StringConstant("sg"));
      final var completed = new Structure();
      completed.set("num", new StringConstant("pl"));
      final var expected = new Structure();
      expected.set("num", new StringConstant("sg"));

      diagnostics.recordUnificationFailure(
          "NP", waiting, completed, expected, "Feature 'num' incompatible: expected sg but got pl", 5);

      assertTrue(diagnostics.hasFailures());
      assertEquals(1, diagnostics.unificationFailures().size());

      final var failure = diagnostics.unificationFailures().getFirst();
      assertEquals("NP", failure.symbol());
      assertEquals(waiting, failure.waiting());
      assertEquals(completed, failure.completed());
      assertEquals(expected, failure.expected());
      assertEquals("Feature 'num' incompatible: expected sg but got pl", failure.reason());
      assertEquals(5, failure.position());
    }

    @Test
    @DisplayName("records multiple unification failures")
    void records_multiple_failures() {
      final var structure = new Structure();
      diagnostics.recordUnificationFailure("NP", structure, structure, structure, "reason1", 1);
      diagnostics.recordUnificationFailure("VP", structure, structure, structure, "reason2", 2);

      assertEquals(2, diagnostics.unificationFailures().size());
    }
  }

  @Nested
  @DisplayName("Report generation")
  class ReportGeneration {

    @Test
    @DisplayName("generates report for no failures")
    void generates_empty_report() {
      final var report = diagnostics.generateReport();

      assertTrue(report.contains("No failures recorded"));
    }

    @Test
    @DisplayName("generates report with constraint failures")
    void generates_constraint_report() {
      final var constraint = Predicate.of("eq", List.of());
      diagnostics.recordConstraintFailure("np", constraint, "number mismatch", 5, true);

      final var report = diagnostics.generateReport();

      assertTrue(report.contains("Constraint Failures"));
      assertTrue(report.contains("[HARD]"));
      assertTrue(report.contains("np"));
      assertTrue(report.contains("number mismatch"));
    }

    @Test
    @DisplayName("generates report with soft constraint failures")
    void generates_soft_constraint_report() {
      final var constraint = Predicate.of("prefer", List.of());
      diagnostics.recordConstraintFailure("vp", constraint, "style issue", 10, false);

      final var report = diagnostics.generateReport();

      assertTrue(report.contains("[SOFT]"));
    }

    @Test
    @DisplayName("generates report with tokenization errors")
    void generates_tokenization_report() {
      diagnostics.recordTokenizationError(0, "###", "word");

      final var report = diagnostics.generateReport();

      assertTrue(report.contains("Tokenization Errors"));
      assertTrue(report.contains("###"));
      assertTrue(report.contains("word"));
    }

    @Test
    @DisplayName("generates report with parsing issues")
    void generates_parsing_report() {
      diagnostics.recordParsingIssue("ambiguous parse", 25);

      final var report = diagnostics.generateReport();

      assertTrue(report.contains("Parsing Issues"));
      assertTrue(report.contains("ambiguous parse"));
      assertTrue(report.contains("25"));
    }

    @Test
    @DisplayName("generates report with regex failures")
    void generates_regex_report() {
      diagnostics.recordRegexFailure("[a-z]+", "123", 30);

      final var report = diagnostics.generateReport();

      assertTrue(report.contains("Regex Pattern Failures"));
      assertTrue(report.contains("[a-z]+"));
      assertTrue(report.contains("123"));
    }

    @Test
    @DisplayName("generates report with quantifier loops")
    void generates_loop_report() {
      diagnostics.recordQuantifierLoop("list", "item+", 5000, 0);

      final var report = diagnostics.generateReport();

      assertTrue(report.contains("Quantifier Loops"));
      assertTrue(report.contains("list"));
      assertTrue(report.contains("item+"));
      assertTrue(report.contains("5000"));
    }

    @Test
    @DisplayName("generates report with unification failures")
    void generates_unification_report() {
      final var waiting = new Structure();
      waiting.set("num", new StringConstant("sg"));
      final var completed = new Structure();
      completed.set("num", new StringConstant("pl"));
      final var expected = new Structure();
      expected.set("num", new StringConstant("sg"));

      diagnostics.recordUnificationFailure(
          "NP", waiting, completed, expected, "Feature 'num' incompatible: expected sg but got pl", 5);

      final var report = diagnostics.generateReport();

      assertTrue(report.contains("Feature Unification Failures"));
      assertTrue(report.contains("NP"));
      assertTrue(report.contains("Feature 'num' incompatible"));
      assertTrue(report.contains("Expected:"));
      assertTrue(report.contains("Waiting:"));
      assertTrue(report.contains("Completed:"));
    }

    @Test
    @DisplayName("generates comprehensive report with all failure types")
    void generates_comprehensive_report() {
      final var constraint = Predicate.of("test", List.of());
      diagnostics.recordConstraintFailure("r1", constraint, "reason1", 1, true);
      diagnostics.recordTokenizationError(2, "bad", "good");
      diagnostics.recordParsingIssue("issue", 3);
      diagnostics.recordRegexFailure("pat", "in", 4);
      diagnostics.recordQuantifierLoop("r2", "e*", 100, 5);
      diagnostics.recordUnificationFailure(
          "S", new Structure(), new Structure(), new Structure(), "feature mismatch", 6);

      final var report = diagnostics.generateReport();

      assertTrue(report.contains("Constraint Failures"));
      assertTrue(report.contains("Tokenization Errors"));
      assertTrue(report.contains("Parsing Issues"));
      assertTrue(report.contains("Regex Pattern Failures"));
      assertTrue(report.contains("Quantifier Loops"));
      assertTrue(report.contains("Feature Unification Failures"));
      assertFalse(report.contains("No failures recorded"));
    }
  }

  @Nested
  @DisplayName("List immutability")
  class ListImmutability {

    @Test
    @DisplayName("returned lists are immutable copies")
    void returned_lists_are_copies() {
      diagnostics.recordParsingIssue("test", 0);

      final var issues = diagnostics.parsingIssues();
      assertThrows(UnsupportedOperationException.class, () -> issues.clear());
    }
  }

  @Nested
  @DisplayName("Span threading")
  class SpanThreading {

    @Test
    @DisplayName("legacy 5-arg constraint failure carries null span")
    void legacy_constraint_null_span() {
      final var constraint = Predicate.of("eq", List.of());
      diagnostics.recordConstraintFailure("r", constraint, "why", 3, true);
      assertNull(diagnostics.constraintFailures().getFirst().span());
    }

    @Test
    @DisplayName("new span overload preserves character offsets")
    void constraint_span_preserved() {
      final var constraint = Predicate.of("eq", List.of());
      final var span = new ParseDiagnostics.Span(2, 7, 12);
      diagnostics.recordConstraintFailure("r", constraint, "why", 2, true, span);
      assertEquals(span, diagnostics.constraintFailures().getFirst().span());
    }

    @Test
    @DisplayName("unification failure span carries token offsets")
    void unification_span() {
      final var span = new ParseDiagnostics.Span(1, 4, 7);
      diagnostics.recordUnificationFailure(
          "NP", new Structure(), new Structure(), new Structure(), "conflict", 1, span);
      assertEquals(span, diagnostics.unificationFailures().getFirst().span());
    }

    @Test
    @DisplayName("report includes char offsets when span is present")
    void report_includes_char_offsets() {
      final var constraint = Predicate.of("eq", List.of());
      final var span = new ParseDiagnostics.Span(2, 7, 12);
      diagnostics.recordConstraintFailure("r", constraint, "why", 2, true, span);
      final var report = diagnostics.generateReport();
      assertTrue(report.contains("chars 7..12"), "report should mention char range: " + report);
    }
  }

  @Nested
  @DisplayName("Parse lattice")
  class LatticeRecording {

    @Test
    @DisplayName("records and returns lattice snapshot")
    void records_lattice() {
      final var span = new ParseDiagnostics.Span(0, 0, 3);
      final var item =
          new ParseDiagnostics.LatticeItem(
              "S → NP • VP", 1, 0, 0, ParseDiagnostics.State.ACTIVE, "{}");
      final var failure =
          new ParseDiagnostics.PathFailure(
              item, span, "blocked waiting for VP", ParseDiagnostics.FailureKind.DEAD_END);
      final var column =
          new ParseDiagnostics.Column(
              1, span, List.of(item), List.of("VP"), List.of(failure));
      final var lattice = new ParseDiagnostics.ParseLattice(List.of(column), 1, span);

      diagnostics.recordLattice(lattice);

      assertEquals(lattice, diagnostics.lattice());
      assertEquals(1, diagnostics.lattice().allFailures().size());
      assertTrue(diagnostics.hasFailures(), "lattice presence implies failures");
    }

    @Test
    @DisplayName("lattice render produces structured section")
    void lattice_render() {
      final var span = new ParseDiagnostics.Span(1, 4, 7);
      final var item =
          new ParseDiagnostics.LatticeItem(
              "S → NP • VP", 1, 0, 0, ParseDiagnostics.State.ACTIVE, "{}");
      final var failure =
          new ParseDiagnostics.PathFailure(
              item, span, "blocked waiting for VP", ParseDiagnostics.FailureKind.DEAD_END);
      final var column =
          new ParseDiagnostics.Column(
              1, span, List.of(item), List.of("VP"), List.of(failure));
      final var lattice = new ParseDiagnostics.ParseLattice(List.of(column), 1, span);

      final var rendered = lattice.render();

      assertTrue(rendered.contains("=== Parse Lattice ==="));
      assertTrue(rendered.contains("Furthest progress: column 1"));
      assertTrue(rendered.contains("expected: VP"));
      assertTrue(rendered.contains("[DEAD_END]"));
      assertTrue(rendered.contains("chars 4..7"));
    }

    @Test
    @DisplayName("generateReport includes lattice section when present")
    void report_contains_lattice_section() {
      final var span = new ParseDiagnostics.Span(0, 0, 0);
      final var lattice =
          new ParseDiagnostics.ParseLattice(
              List.of(
                  new ParseDiagnostics.Column(
                      0, span, List.of(), List.of(), List.of())),
              0,
              span);
      diagnostics.recordLattice(lattice);

      final var report = diagnostics.generateReport();
      assertTrue(report.contains("=== Parse Lattice ==="));
    }
  }
}
