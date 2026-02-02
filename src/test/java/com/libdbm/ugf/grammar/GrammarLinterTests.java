package com.libdbm.ugf.grammar;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.constraints.Constraint;
import com.libdbm.ugf.constraints.Predicate;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Variable;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for GrammarLinter to ensure proper detection of: - Unreachable rules - Duplicate lexical
 * entries - Unbound labels in constraints - Undefined nonterminals
 */
final class GrammarLinterTests {

  /** Helper to build a grammar from a start symbol and rules. */
  private static Grammar grammar(final String start, final GrammarRule... rules) {
    final var builder = Grammar.builder().start(start);
    for (final var rule : rules) {
      builder.add(rule);
    }
    return builder.build();
  }

  @Test
  void test_no_issues_with_valid_grammar() {
    final Grammar grammar =
        grammar(
            "S",
            new GrammarRule(
                new GrammarRule.LHS("S"),
                List.of(new RuleElement.Terminal("hello", null)),
                List.of()));

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertFalse(report.hasErrors());
    assertFalse(report.hasWarnings());
    assertTrue(report.issues().isEmpty());
  }

  @Test
  void test_detects_unreachable_rule() {
    final Grammar grammar =
        grammar(
            "S",
            // S -> "hello"
            new GrammarRule(
                new GrammarRule.LHS("S"),
                List.of(new RuleElement.Terminal("hello", null)),
                List.of()),
            // Unreachable non-lexical rule: X -> Y "world"
            new GrammarRule(
                new GrammarRule.LHS("X"),
                List.of(new RuleElement.Nonterminal("Y"), new RuleElement.Terminal("world", null)),
                List.of()),
            // Y -> "test"
            new GrammarRule(
                new GrammarRule.LHS("Y"),
                List.of(new RuleElement.Terminal("test", null)),
                List.of()));

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertTrue(report.hasWarnings());
    assertEquals(1, report.warnings().size());

    final var warning = report.warnings().get(0);
    assertEquals("Unreachable rule", warning.title());
    assertTrue(warning.message().contains("X"));
  }

  @Test
  void test_reachability_through_nonterminals() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> NP VP
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Nonterminal("NP"), new RuleElement.Nonterminal("VP")),
                    List.of()))
            // NP -> "the" N
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("NP"),
                    List.of(
                        new RuleElement.Terminal("the", null), new RuleElement.Nonterminal("N")),
                    List.of()))
            // N -> "dog"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("N"),
                    List.of(new RuleElement.Terminal("dog", null)),
                    List.of()))
            // VP -> "barks"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("VP"),
                    List.of(new RuleElement.Terminal("barks", null)),
                    List.of()))
            // Unreachable non-lexical: X -> Z "test"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("X"),
                    List.of(
                        new RuleElement.Nonterminal("Z"), new RuleElement.Terminal("test", null)),
                    List.of()))
            // Z -> "z"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("Z"),
                    List.of(new RuleElement.Terminal("z", null)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertTrue(report.hasWarnings());
    assertEquals(1, report.warnings().size());

    final var warning = report.warnings().getFirst();
    assertTrue(warning.message().contains("X"));
  }

  @Test
  void test_duplicate_lexical_entries() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> T1 | T2
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Nonterminal("T1")),
                    List.of()))
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Nonterminal("T2")),
                    List.of()))
            // T1 -> "hello" (lexical)
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("T1"),
                    List.of(new RuleElement.Terminal("hello", null)),
                    List.of()))
            // T2 -> "hello" (duplicate lexical)
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("T2"),
                    List.of(new RuleElement.Terminal("hello", null)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    final var information =
        report.issues().stream()
            .filter(i -> i.severity() == GrammarLinter.LintIssue.Severity.INFO)
            .toList();

    assertEquals(1, information.size());
    assertTrue(information.getFirst().message().contains("'hello'"));
    assertTrue(information.getFirst().message().contains("T1"));
    assertTrue(information.getFirst().message().contains("T2"));
  }

  @Test
  void test_duplicate_regex_lexical_entries() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> NUM1 | NUM2
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Nonterminal("NUM1")),
                    List.of()))
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Nonterminal("NUM2")),
                    List.of()))
            // NUM1 -> /[0-9]+/
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("NUM1"),
                    List.of(new RuleElement.Regex("[0-9]+", null)),
                    List.of()))
            // NUM2 -> /[0-9]+/ (duplicate)
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("NUM2"),
                    List.of(new RuleElement.Regex("[0-9]+", null)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    final var information =
        report.issues().stream()
            .filter(i -> i.severity() == GrammarLinter.LintIssue.Severity.INFO)
            .toList();

    assertEquals(1, information.size());
    assertTrue(information.getFirst().message().contains("/[0-9]+/"));
  }

  @Test
  void test_detects_unbound_label_in_constraint() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> NP:np VP
            // Constraint references "unbound" which doesn't appear anywhere
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(
                        new RuleElement.Nonterminal("NP", "np"), new RuleElement.Nonterminal("VP")),
                    List.of(
                        Predicate.of("agree", List.of(Variable.of("np"), Variable.of("unbound"))))))
            // Define NP and VP
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("NP"),
                    List.of(new RuleElement.Terminal("dog", null)),
                    List.of()))
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("VP"),
                    List.of(new RuleElement.Terminal("barks", null)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertTrue(report.hasErrors());
    assertEquals(1, report.errors().size());

    final var error = report.errors().getFirst();
    assertEquals("Unbound label in constraint", error.title());
    assertTrue(error.message().contains("unbound"));
  }

  @Test
  void test_constraint_with_bound_labels() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> NP:np VP:vp
            // Constraint: np.num = vp.num (both bound)
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(
                        new RuleElement.Nonterminal("NP", "np"),
                        new RuleElement.Nonterminal("VP", "vp")),
                    List.of(Predicate.of("agree", List.of(Variable.of("np"), Variable.of("vp"))))))
            // NP -> "dog"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("NP"),
                    List.of(new RuleElement.Terminal("dog", null)),
                    List.of()))
            // VP -> "barks"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("VP"),
                    List.of(new RuleElement.Terminal("barks", null)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertFalse(report.hasErrors());
  }

  @Test
  void test_detects_undefined_nonterminal() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> UndefinedSymbol
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Nonterminal("UndefinedSymbol")),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertTrue(report.hasErrors());
    assertEquals(1, report.errors().size());

    final var error = report.errors().getFirst();
    assertEquals("Undefined nonterminal", error.title());
    assertTrue(error.message().contains("UndefinedSymbol"));
  }

  @Test
  void test_undefined_nonterminal_in_alternation() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> (A | UndefinedB)
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(
                        new RuleElement.Alternation(
                            List.of(
                                new RuleElement.Nonterminal("A"),
                                new RuleElement.Nonterminal("UndefinedB")))),
                    List.of()))
            // A -> "hello"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("A"),
                    List.of(new RuleElement.Terminal("hello", null)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertTrue(report.hasErrors());
    final var errors = report.errors();
    assertTrue(errors.stream().anyMatch(e -> e.message().contains("UndefinedB")));
  }

  @Test
  void test_undefined_nonterminal_in_repetition() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> UndefinedX*
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(
                        new RuleElement.Repetition(
                            new RuleElement.Nonterminal("UndefinedX"),
                            RuleElement.Quantifier.ZERO_OR_MORE)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertTrue(report.hasErrors());
    assertTrue(report.errors().stream().anyMatch(e -> e.message().contains("UndefinedX")));
  }

  @Test
  void test_complex_constraint_with_multiple_variables() {
    // S -> A:a B:b C:c
    // Constraint: (a = b) AND (b = c) AND (a = unbound)
    final var andConstraint =
        new Constraint.And(
            List.of(
                Predicate.of("agree", List.of(Variable.of("a"), Variable.of("b"))),
                Predicate.of("agree", List.of(Variable.of("b"), Variable.of("c"))),
                Predicate.of("agree", List.of(Variable.of("a"), Variable.of("unbound"))) // unbound!
                ));

    final Grammar grammar =
        Grammar.builder()
            .start("S")
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(
                        new RuleElement.Nonterminal("A", "a"),
                        new RuleElement.Nonterminal("B", "b"),
                        new RuleElement.Nonterminal("C", "c")),
                    List.of(andConstraint)))
            // Define A, B, C
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("A"),
                    List.of(new RuleElement.Terminal("a", null)),
                    List.of()))
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("B"),
                    List.of(new RuleElement.Terminal("b", null)),
                    List.of()))
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("C"),
                    List.of(new RuleElement.Terminal("c", null)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertTrue(report.hasErrors());
    assertTrue(report.errors().stream().anyMatch(e -> e.message().contains("unbound")));
  }

  @Test
  void test_labeled_terminals_are_valid_in_constraints() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> "the":determinant N:n
            // Constraint: determinant (just referencing the labeled terminal)
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(
                        new RuleElement.Terminal("the", "determinant"),
                        new RuleElement.Nonterminal("N", "n")),
                    List.of(Predicate.of("terminal", List.of(Variable.of("determinant"))))))
            // N -> "dog"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("N"),
                    List.of(new RuleElement.Terminal("dog", null)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertFalse(report.hasErrors());
  }

  @Test
  void test_labeled_regex_are_valid_in_constraints() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> /[0-9]+/:num
            // Constraint referencing num
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Regex("[0-9]+", "num")),
                    List.of(Predicate.of("regex", List.of(Variable.of("num"))))))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertFalse(report.hasErrors());
  }

  @Test
  void test_lint_report_formatting() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> "hello"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Terminal("hello", null)),
                    List.of()))
            // Unreachable
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("X"),
                    List.of(new RuleElement.Terminal("world", null)),
                    List.of()))
            // Undefined nonterminal
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("Y"),
                    List.of(new RuleElement.Nonterminal("Undef")),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    final String formatted = report.format();

    assertNotNull(formatted);
    assertTrue(formatted.contains("Grammar Linting Report"));
    assertTrue(formatted.contains("ERRORS"));
    assertTrue(formatted.contains("WARNINGS"));
    assertTrue(formatted.contains("[ERROR]"));
    assertTrue(formatted.contains("[WARN]"));
  }

  @Test
  void test_empty_grammar_lint() {
    final Grammar grammar = Grammar.builder().build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertFalse(report.hasErrors());
    assertFalse(report.hasWarnings());
    assertTrue(report.issues().isEmpty());
  }

  @Test
  void test_lhs_symbol_is_implicitly_bound() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> "hello"
            // Constraint: S (references LHS symbol itself)
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Terminal("hello", null)),
                    List.of(Predicate.of("lhs", List.of(Variable.of("S"))))))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertFalse(report.hasErrors());
  }

  @Test
  void test_nonterminal_name_is_implicitly_bound() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> NP VP
            // Constraint: NP.num = VP.num (using nonterminal names directly)
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Nonterminal("NP"), new RuleElement.Nonterminal("VP")),
                    List.of(Predicate.of("agree", List.of(Variable.of("NP"), Variable.of("VP"))))))
            // NP -> "dog"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("NP"),
                    List.of(new RuleElement.Terminal("dog", null)),
                    List.of()))
            // VP -> "barks"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("VP"),
                    List.of(new RuleElement.Terminal("barks", null)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertFalse(report.hasErrors());
  }

  // ========== State Validation Tests ==========

  @Test
  void test_detects_state_transition_on_non_lexical_rule() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> NP VP with state transition on terminal inside non-lexical rule (invalid)
            // This simulates a non-lexical rule with a terminal that has a transition
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(
                        new RuleElement.Terminal("prefix").transition("COMPLETED"),
                        new RuleElement.Nonterminal("NP")),
                    List.of()))
            // NP -> "the"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("NP"),
                    List.of(new RuleElement.Terminal("the", null)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertTrue(report.hasErrors());
    assertTrue(
        report.errors().stream()
            .anyMatch(e -> e.title().contains("State transition on non-lexical")));
  }

  @Test
  void test_allows_state_transition_on_lexical_rule() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> TOKEN
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Nonterminal("TOKEN")),
                    List.of()))
            // TOKEN -> "hello" ==> CONTENT (valid: lexical)
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("TOKEN"),
                    List.of(new RuleElement.Terminal("hello").transition("CONTENT")),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertFalse(report.hasErrors());
  }

  @Test
  void test_warns_about_state_predicate_on_non_lexical_rule() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> NP VP where in_state("CONTENT") (unusual: non-lexical with state predicate)
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Nonterminal("NP"), new RuleElement.Nonterminal("VP")),
                    List.of(Predicate.of("in_state", List.of(StringConstant.of("CONTENT"))))))
            // NP -> "the"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("NP"),
                    List.of(new RuleElement.Terminal("the", null)),
                    List.of()))
            // VP -> "runs"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("VP"),
                    List.of(new RuleElement.Terminal("runs", null)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    // State predicates on non-lexical rules are warnings, not errors (can be intentional)
    assertFalse(report.hasErrors());
    assertTrue(report.hasWarnings());
    assertTrue(report.warnings().stream().anyMatch(e -> e.message().contains("in_state")));
  }

  @Test
  void test_allows_state_predicate_on_lexical_rule() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> TAG
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Nonterminal("TAG")),
                    List.of()))
            // TAG -> "<" where in_state("CONTENT") (valid: lexical)
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("TAG"),
                    List.of(new RuleElement.Terminal("<", null)),
                    List.of(Predicate.of("in_state", List.of(StringConstant.of("CONTENT"))))))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertFalse(report.hasErrors());
  }

  @Test
  void test_detects_state_annotation_in_non_lexical_rule() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> {STATE} NP VP (invalid: non-lexical with state annotation)
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(
                        new RuleElement.StateAnnotation("STATE"),
                        new RuleElement.Nonterminal("NP"),
                        new RuleElement.Nonterminal("VP")),
                    List.of()))
            // NP -> "the"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("NP"),
                    List.of(new RuleElement.Terminal("the", null)),
                    List.of()))
            // VP -> "runs"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("VP"),
                    List.of(new RuleElement.Terminal("runs", null)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertTrue(report.hasErrors());
    assertTrue(
        report.errors().stream()
            .anyMatch(e -> e.title().contains("State annotation in non-lexical")));
  }

  @Test
  void test_allows_state_annotation_in_lexical_rule() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> TOKEN
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Nonterminal("TOKEN")),
                    List.of()))
            // TOKEN -> {STATE} "hello" (valid: lexical with state annotation)
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("TOKEN"),
                    List.of(
                        new RuleElement.StateAnnotation("STATE"),
                        new RuleElement.Terminal("hello", null)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertFalse(report.hasErrors());
  }

  @Test
  void test_handles_alternation_of_lexicals() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> KEYWORD
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(new RuleElement.Nonterminal("KEYWORD")),
                    List.of()))
            // KEYWORD -> ("if" | "while" | "for") with transition on last option (valid:
            // alternation of
            // terminals)
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("KEYWORD"),
                    List.of(
                        new RuleElement.Alternation(
                            List.of(
                                new RuleElement.Terminal("if").transition("KEYWORD_STATE"),
                                new RuleElement.Terminal("while").transition("KEYWORD_STATE"),
                                new RuleElement.Terminal("for").transition("KEYWORD_STATE")))),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertFalse(report.hasErrors());
  }

  @Test
  void test_handles_mixed_rule() {
    final Grammar grammar =
        Grammar.builder()
            .start("S")
            // S -> "if" EXPR (mixed: terminal + nonterminal, NOT lexical)
            // State transition on terminal should be flagged as error
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("S"),
                    List.of(
                        new RuleElement.Terminal("if").transition("IF_STATE"),
                        new RuleElement.Nonterminal("EXPR")),
                    List.of()))
            // EXPR -> "true"
            .add(
                new GrammarRule(
                    new GrammarRule.LHS("EXPR"),
                    List.of(new RuleElement.Terminal("true", null)),
                    List.of()))
            .build();

    final var linter = new GrammarLinter();
    final var report = linter.lint(grammar);

    assertTrue(report.hasErrors());
    assertTrue(
        report.errors().stream()
            .anyMatch(e -> e.title().contains("State transition on non-lexical")));
  }
}
