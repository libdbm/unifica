package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Variable;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarNormalizer;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.RuleElement;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for ChartParser. */
class ChartParserTests {

  @Nested
  @DisplayName("Epsilon derivations")
  class EpsilonDerivations {

    @Test
    @DisplayName("Parses empty input when grammar has epsilon rule")
    void parsesEmptyInputWithEpsilonRule() {
      // Grammar: S --> (empty RHS = epsilon production)
      final var grammar = Grammar.builder().start("S").add(new GrammarRule("S", List.of())).build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("");

      assertNotNull(result.tree(), "Should parse empty input with ε-rule");
      assertEquals("S", ((ParseTree.Node) result.tree()).symbol());
      assertTrue(((ParseTree.Node) result.tree()).children().isEmpty());
    }

    @Test
    @DisplayName("Parses whitespace-only input when grammar has epsilon rule")
    void parsesWhitespaceWithEpsilonRule() {
      final var grammar = Grammar.builder().start("S").add(new GrammarRule("S", List.of())).build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("   ");

      assertNotNull(result.tree(), "Should parse whitespace-only with ε-rule");
    }

    @Test
    @DisplayName("Optional element allows empty match")
    void optionalElementAllowsEmpty() {
      // Grammar: S --> A?, A --> 'x'
      // GrammarNormalizer converts S --> A? into S --> A | ε
      final var grammar =
          GrammarNormalizer.normalize(
              Grammar.builder()
                  .start("S")
                  .add(
                      new GrammarRule(
                          "S",
                          List.of(
                              new RuleElement.Repetition(
                                  new RuleElement.Nonterminal("A"),
                                  RuleElement.Quantifier.OPTIONAL))))
                  .add(new GrammarRule("A", List.of(new RuleElement.Terminal("x"))))
                  .build());

      final var parser = new ChartParser(grammar);

      // Empty input should match S --> (empty)
      final var empty = parser.parse("");
      assertNotNull(empty.tree(), "Optional element should allow empty input");

      // Non-empty should still work
      final var one = parser.parse("x");
      assertNotNull(one.tree(), "Optional element should match single 'x'");
    }

    @Test
    @DisplayName("Kleene star allows empty match")
    void kleeneStarAllowsEmpty() {
      // Grammar: S --> A*, A --> 'y'
      // GrammarNormalizer converts S --> A* into S --> A A* | ε
      final var grammar =
          GrammarNormalizer.normalize(
              Grammar.builder()
                  .start("S")
                  .add(
                      new GrammarRule(
                          "S",
                          List.of(
                              new RuleElement.Repetition(
                                  new RuleElement.Nonterminal("A"),
                                  RuleElement.Quantifier.ZERO_OR_MORE))))
                  .add(new GrammarRule("A", List.of(new RuleElement.Terminal("y"))))
                  .build());

      final var parser = new ChartParser(grammar);

      // Empty input
      final var empty = parser.parse("");
      assertNotNull(empty.tree(), "Kleene star should allow empty input");

      // One match
      final var one = parser.parse("y");
      assertNotNull(one.tree(), "Kleene star should match single 'y'");

      // Multiple matches
      final var many = parser.parse("y y y");
      assertNotNull(many.tree(), "Kleene star should match multiple 'y'");
    }
  }

  @Nested
  @DisplayName("Basic parsing")
  class BasicParsing {

    @Test
    @DisplayName("Parses single terminal")
    void parsesSingleTerminal() {
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(new GrammarRule("S", List.of(new RuleElement.Terminal("hello"))))
              .build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("hello").toOptional();

      assertTrue(result.isPresent());
      assertEquals("S", ((ParseTree.Node) result.get()).symbol());
    }

    @Test
    @DisplayName("Parses sequence of terminals")
    void parsesSequenceOfTerminals() {
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(
                  new GrammarRule(
                      "S",
                      List.of(
                          new RuleElement.Terminal("a"),
                          new RuleElement.Terminal("b"),
                          new RuleElement.Terminal("c"))))
              .build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("a b c").toOptional();

      assertTrue(result.isPresent());
      final var node = (ParseTree.Node) result.get();
      assertEquals(3, node.children().size());
    }

    @Test
    @DisplayName("Parses with nonterminal")
    void parsesWithNonterminal() {
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(
                  new GrammarRule(
                      "S",
                      List.of(new RuleElement.Nonterminal("A"), new RuleElement.Nonterminal("B"))))
              .add(new GrammarRule("A", List.of(new RuleElement.Terminal("x"))))
              .add(new GrammarRule("B", List.of(new RuleElement.Terminal("y"))))
              .build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("x y").toOptional();

      assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Handles left recursion")
    void handlesLeftRecursion() {
      // Grammar: E --> E '+' N | N, N --> [0-9]+
      final var grammar =
          Grammar.builder()
              .start("E")
              .add(
                  new GrammarRule(
                      "E",
                      List.of(
                          new RuleElement.Nonterminal("E"),
                          new RuleElement.Terminal("+"),
                          new RuleElement.Nonterminal("N"))))
              .add(new GrammarRule("E", List.of(new RuleElement.Nonterminal("N"))))
              .add(new GrammarRule("N", List.of(new RuleElement.Regex("[0-9]+"))))
              .build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("1 + 2 + 3").toOptional();

      assertTrue(result.isPresent(), "Should handle left-recursive grammar");
    }

    @Test
    @DisplayName("Returns empty for non-matching input")
    void returnsEmptyForNonMatch() {
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(new GrammarRule("S", List.of(new RuleElement.Terminal("hello"))))
              .build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("world").toOptional();

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("Feature unification")
  class FeatureUnification {

    @Test
    @DisplayName("Preserves features from lexical rules")
    void preservesFeaturesFromLexicalRules() {
      // DET[num: sing] --> 'a'
      final var singFeats = Structure.builder().with("num", "sing").build();

      final var grammar =
          Grammar.builder()
              .start("S")
              // S --> DET N
              .add(
                  new GrammarRule(
                      "S",
                      List.of(
                          new RuleElement.Nonterminal("DET"), new RuleElement.Nonterminal("N"))))
              // DET[num: sing] --> 'a'
              .add(
                  new GrammarRule(
                      new GrammarRule.LHS("DET", singFeats),
                      List.of(new RuleElement.Terminal("a")),
                      List.of()))
              // N --> 'cat'
              .add(new GrammarRule("N", List.of(new RuleElement.Terminal("cat"))))
              .build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("a cat").toOptional();

      assertTrue(result.isPresent(), "Should parse 'a cat'");
    }
  }

  @Nested
  @DisplayName("Penalty handling")
  class PenaltyHandling {

    @Test
    @DisplayName("Reports zero penalty for constraint-free parse")
    void zeroePenaltyWhenNoConstraints() {
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(new GrammarRule("S", List.of(new RuleElement.Terminal("ok"))))
              .build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("ok");

      assertNotNull(result.tree());
      assertEquals(0, result.penalty());
    }
  }

  @Nested
  @DisplayName("Constraint diagnostics")
  class ConstraintDiagnostics {

    @Test
    @DisplayName("Records constraint failures in diagnostics")
    void recordsConstraintFailures() {
      // Track if predicate was called during parsing (not lexing)
      final var called = new java.util.concurrent.atomic.AtomicBoolean(false);

      // Grammar with SEPARATE lexical and parsing rules:
      // - WORD --> 'test' (no constraint, allows tokenization)
      // - S --> WORD where always_fails (constraint checked during parsing)
      final var grammar =
          Grammar.builder()
              .start("S")
              // Lexical rule: WORD matches 'test' (no constraints)
              .add(new GrammarRule("WORD", List.of(new RuleElement.Terminal("test"))))
              // Parsing rule: S --> WORD with always-failing constraint
              .add(
                  new GrammarRule(
                      "S",
                      List.of(new RuleElement.Nonterminal("WORD")),
                      List.of(
                          new com.libdbm.ugf.constraints.Predicate(
                              "always_fails",
                              List.of(),
                              com.libdbm.ugf.constraints.Strength.REQUIRED,
                              0))))
              .build();

      // Register failing predicate that tracks invocation
      final var context =
          new com.libdbm.ugf.constraints.Context()
              .withPredicate(
                  "always_fails",
                  (ctx, args) -> {
                    called.set(true);
                    return com.libdbm.ugf.constraints.Result.fail("This constraint always fails");
                  });

      final var parser = new ChartParser(context, grammar, LexicalAnalyzer.build(grammar));

      // Verify rule has constraint
      final var rules = grammar.rulesFor("S");
      System.out.println("Rules for S: " + rules.size());
      for (final var rule : rules) {
        System.out.println("  Rule: " + rule.lhs().symbol() + " --> " + rule.rhs());
        System.out.println("  Constraints: " + rule.constraints());
      }

      final var result = parser.parse("test");

      System.out.println("Predicate called: " + called.get());
      System.out.println("Result: " + result);
      System.out.println("Diagnostics: " + result.diagnostics());
      if (result.diagnostics() != null) {
        System.out.println("Constraint failures: " + result.diagnostics().constraintFailures());
      }

      // Verify predicate was actually called during parsing
      assertTrue(called.get(), "Predicate should have been called during constraint evaluation");

      // Parse should fail due to hard constraint
      assertFalse(result.success(), "Parse should fail with hard constraint failure");

      // Diagnostics should be present
      assertNotNull(result.diagnostics(), "Diagnostics should be recorded");
      assertTrue(result.hasConstraintFailures(), "Should have constraint failures");

      // Check failure details
      final var failures = result.diagnostics().constraintFailures();
      assertFalse(failures.isEmpty(), "Should have at least one failure");

      final var failure = failures.getFirst();
      assertEquals("S", failure.ruleName(), "Failure should be for rule S");
      assertTrue(failure.isHard(), "Should be a hard constraint failure");
      assertTrue(
          failure.reason().contains("always fails"),
          "Reason should contain failure message: " + failure.reason());
    }

    @Test
    @DisplayName("Includes diagnostics in toString")
    void diagnosticsInToString() {
      // Separate lexical and parsing rules to ensure constraint is evaluated during parsing
      final var grammar =
          Grammar.builder()
              .start("S")
              // Lexical rule: WORD matches 'test' (no constraints)
              .add(new GrammarRule("WORD", List.of(new RuleElement.Terminal("test"))))
              // Parsing rule: S --> WORD with failing constraint
              .add(
                  new GrammarRule(
                      "S",
                      List.of(new RuleElement.Nonterminal("WORD")),
                      List.of(
                          new com.libdbm.ugf.constraints.Predicate(
                              "fails_with_message",
                              List.of(),
                              com.libdbm.ugf.constraints.Strength.REQUIRED,
                              0))))
              .build();

      final var context =
          new com.libdbm.ugf.constraints.Context()
              .withPredicate(
                  "fails_with_message",
                  (ctx, args) -> com.libdbm.ugf.constraints.Result.fail("Expected error message"));

      final var parser = new ChartParser(context, grammar, LexicalAnalyzer.build(grammar));
      final var result = parser.parse("test");

      final var output = result.toString();
      assertTrue(
          output.contains("Constraint Failures") || output.contains("Expected error message"),
          "toString should include constraint failure info: " + output);
    }
  }

  @Nested
  @DisplayName("Unification failure diagnostics")
  class UnificationDiagnostics {

    @Test
    @DisplayName("Records unification failure when features conflict")
    void records_unification_failure() {
      // Grammar: S --> NP[num:?n] VP[num:?n] (agreement required)
      //          NP[num:sg] --> 'cat'
      //          NP[num:pl] --> 'cats'
      //          VP[num:sg] --> 'runs'
      //          VP[num:pl] --> 'run'
      // Input: 'cat run' should fail with unification diagnostic

      final var sg = Structure.builder().with("num", "sg").build();
      final var pl = Structure.builder().with("num", "pl").build();
      final var agreement = Structure.builder().with("num", Variable.of("n")).build();

      final var grammar =
          Grammar.builder()
              .start("S")
              .add(
                  new GrammarRule(
                      "S",
                      List.of(
                          new RuleElement.Nonterminal("NP", null, agreement),
                          new RuleElement.Nonterminal("VP", null, agreement))))
              .add(new GrammarRule(new GrammarRule.LHS("NP", sg), List.of(new RuleElement.Terminal("cat")), List.of()))
              .add(new GrammarRule(new GrammarRule.LHS("NP", pl), List.of(new RuleElement.Terminal("cats")), List.of()))
              .add(new GrammarRule(new GrammarRule.LHS("VP", sg), List.of(new RuleElement.Terminal("runs")), List.of()))
              .add(new GrammarRule(new GrammarRule.LHS("VP", pl), List.of(new RuleElement.Terminal("run")), List.of()))
              .build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("cat run"); // sg NP + pl VP = disagreement

      assertFalse(result.success(), "Parse should fail due to number disagreement");
      assertNotNull(result.diagnostics(), "Diagnostics should be present");

      final var failures = result.diagnostics().unificationFailures();
      assertFalse(failures.isEmpty(), "Should have unification failures recorded");

      // Check failure details
      final var found = failures.stream().anyMatch(f -> f.reason().contains("num"));
      assertTrue(found, "Should have failure mentioning 'num' feature: " + failures);
    }

    @Test
    @DisplayName("Includes unification failures in report")
    void includes_in_report() {
      final var sg = Structure.builder().with("num", "sg").build();
      final var pl = Structure.builder().with("num", "pl").build();
      final var agreement = Structure.builder().with("num", Variable.of("n")).build();

      final var grammar =
          Grammar.builder()
              .start("S")
              .add(
                  new GrammarRule(
                      "S",
                      List.of(
                          new RuleElement.Nonterminal("NP", null, agreement),
                          new RuleElement.Nonterminal("VP", null, agreement))))
              .add(new GrammarRule(new GrammarRule.LHS("NP", sg), List.of(new RuleElement.Terminal("he")), List.of()))
              .add(new GrammarRule(new GrammarRule.LHS("VP", pl), List.of(new RuleElement.Terminal("walk")), List.of()))
              .build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("he walk");

      final var report = result.diagnostics().generateReport();
      assertTrue(report.contains("Feature Unification Failures"), "Report should include unification failures section");
    }
  }

  @Nested
  @DisplayName("Parse lattice diagnostics")
  class LatticeDiagnostics {

    @Test
    @DisplayName("Failed parse records a lattice with a DEAD_END failure")
    void records_dead_end() {
      // S --> 'a' 'b' 'c'; input 'a b' is a prefix that can't complete.
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(
                  new GrammarRule(
                      "S",
                      List.of(
                          new RuleElement.Terminal("a"),
                          new RuleElement.Terminal("b"),
                          new RuleElement.Terminal("c"))))
              .build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("a b");

      assertFalse(result.success(), "truncated input must fail");
      final var lattice = result.diagnostics().lattice();
      assertNotNull(lattice, "failed parse must carry a lattice");

      final var deadEnds =
          lattice.allFailures().stream()
              .filter(f -> f.kind() == ParseDiagnostics.FailureKind.DEAD_END)
              .toList();
      assertFalse(deadEnds.isEmpty(), "must report at least one DEAD_END: " + lattice.render());
      final var first = deadEnds.getFirst();
      assertTrue(
          first.reason().contains("'c'"),
          "reason should mention the expected terminal: " + first.reason());
    }

    @Test
    @DisplayName("Lattice furthest-progress column matches the farthest token consumed")
    void furthest_progress() {
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(
                  new GrammarRule(
                      "S",
                      List.of(
                          new RuleElement.Terminal("a"),
                          new RuleElement.Terminal("b"),
                          new RuleElement.Terminal("c"))))
              .build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("a b");

      final var lattice = result.diagnostics().lattice();
      // After scanning 'a' and 'b', the chart has progressed to column 2 (dot before 'c').
      assertEquals(2, lattice.furthestColumn(), "expected to reach column 2 after 2 tokens");
      assertNotNull(lattice.furthestSpan(), "furthest span should be populated");
    }

    @Test
    @DisplayName("Unification-failure span carries real character offsets")
    void unification_span_has_char_offsets() {
      final var sg = Structure.builder().with("num", "sg").build();
      final var pl = Structure.builder().with("num", "pl").build();
      final var agreement = Structure.builder().with("num", Variable.of("n")).build();

      final var grammar =
          Grammar.builder()
              .start("S")
              .add(
                  new GrammarRule(
                      "S",
                      List.of(
                          new RuleElement.Nonterminal("NP", null, agreement),
                          new RuleElement.Nonterminal("VP", null, agreement))))
              .add(
                  new GrammarRule(
                      new GrammarRule.LHS("NP", sg),
                      List.of(new RuleElement.Terminal("cat")),
                      List.of()))
              .add(
                  new GrammarRule(
                      new GrammarRule.LHS("VP", pl),
                      List.of(new RuleElement.Terminal("run")),
                      List.of()))
              .build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("cat run");

      final var failure = result.diagnostics().unificationFailures().getFirst();
      assertNotNull(failure.span(), "unification failure should carry a span");
      // 'run' starts at char 4 and ends at char 7 in "cat run"
      assertEquals(4, failure.span().charStart());
      assertEquals(7, failure.span().charEnd());
    }

    @Test
    @DisplayName("Constraint failure contributes a CONSTRAINT entry to the lattice")
    void constraint_failure_in_lattice() {
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(new GrammarRule("WORD", List.of(new RuleElement.Terminal("test"))))
              .add(
                  new GrammarRule(
                      "S",
                      List.of(new RuleElement.Nonterminal("WORD")),
                      List.of(
                          new com.libdbm.ugf.constraints.Predicate(
                              "always_fails",
                              List.of(),
                              com.libdbm.ugf.constraints.Strength.REQUIRED,
                              0))))
              .build();

      final var context =
          new com.libdbm.ugf.constraints.Context()
              .withPredicate(
                  "always_fails",
                  (ctx, args) -> com.libdbm.ugf.constraints.Result.fail("nope"));

      final var parser = new ChartParser(context, grammar, LexicalAnalyzer.build(grammar));
      final var result = parser.parse("test");

      assertFalse(result.success());
      final var lattice = result.diagnostics().lattice();
      assertNotNull(lattice);
      final var hasConstraint =
          lattice.allFailures().stream()
              .anyMatch(f -> f.kind() == ParseDiagnostics.FailureKind.CONSTRAINT);
      assertTrue(
          hasConstraint,
          "lattice should contain a CONSTRAINT path failure: " + lattice.render());
    }

    @Test
    @DisplayName("Successful parse does not record a lattice")
    void success_has_no_lattice() {
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(new GrammarRule("S", List.of(new RuleElement.Terminal("a"))))
              .build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("a");

      assertTrue(result.success());
      assertNull(
          result.diagnostics().lattice(),
          "successful parse should not pay the lattice-snapshot cost");
    }

    @Test
    @DisplayName("Report includes parse lattice section on failure")
    void report_includes_lattice_on_failure() {
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(
                  new GrammarRule(
                      "S",
                      List.of(
                          new RuleElement.Terminal("a"), new RuleElement.Terminal("b"))))
              .build();

      final var parser = new ChartParser(grammar);
      final var result = parser.parse("a");

      final var report = result.diagnostics().generateReport();
      assertTrue(report.contains("=== Parse Lattice ==="), "report: " + report);
      assertTrue(report.contains("Furthest progress"), "report: " + report);
    }
  }

  @Nested
  @DisplayName("Ambiguity")
  class Ambiguity {

    private static final String NESTED =
        """
        start s;
        s --> y 'z';
        y --> x x;
        x --> 'a';
        x --> 'a' 'a';
        """;

    private ParseResult parse(final String grammar, final String input) {
      return new ChartParser(UnificationGrammarParserFactory.parse(grammar)).parse(input);
    }

    @Test
    @DisplayName("two derivations of the whole input at the same penalty are ambiguous")
    void testTopLevelTieIsAmbiguous() {
      final var result =
          parse(
              """
              start s;
              s --> x x;
              x --> 'a';
              x --> 'a' 'a';
              """,
              "a a a");

      assertTrue(result.success());
      assertTrue(result.ambiguous(), "(a)(a a) and (a a)(a) tie");
    }

    @Test
    @DisplayName("a tie inside a constituent makes the whole parse ambiguous")
    void testNestedTieIsAmbiguous() {
      final var result = parse(NESTED, "a a a z");

      assertTrue(result.success());
      assertTrue(result.ambiguous(), "the tie is inside y, below the start symbol");
    }

    @Test
    @DisplayName("diagnostics name the constituent where the tie arose")
    void testDiagnosticsNameTheTie() {
      final var result = parse(NESTED, "a a a z");

      final var ambiguities = result.diagnostics().ambiguities();
      assertFalse(ambiguities.isEmpty(), "an ambiguous parse must say where");
      assertEquals("y", ambiguities.getFirst().symbol());
      assertTrue(
          result.diagnostics().generateReport().contains("=== Ambiguities ==="),
          result.diagnostics().generateReport());
    }

    @Test
    @DisplayName("two lexical rules matching the same word do not make one derivation look like two")
    void testSharedLiteralIsNotAmbiguous() {
      final var result =
          parse(
              """
              start s;
              s --> y;
              x --> 'a';
              y --> 'a';
              """,
              "a");

      assertTrue(result.success());
      assertFalse(result.ambiguous(), () -> result.diagnostics().generateReport());
    }

    @Test
    @DisplayName("a tie the penalties break is not ambiguous")
    void testPenaltyBreaksTheTie() {
      final var result =
          parse(
              """
              start s;
              s --> x y;
              x --> 'a';
              x --> 'a' 'a' where equals("p", "q"):50;
              y --> 'a';
              y --> 'a' 'a';
              """,
              "a a a");

      assertTrue(result.success());
      assertFalse(
          result.ambiguous(),
          () ->
              "(a a)(a) costs 50, so (a)(a a) wins outright; penalty "
                  + result.penalty()
                  + "\n"
                  + result.diagnostics().generateReport());
      assertEquals(0, result.penalty());
    }

    @Test
    @DisplayName("start items that differ only in their features are ambiguous")
    void testFeatureTieIsAmbiguous() {
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(new GrammarRule("S", List.of(new RuleElement.Terminal("a"))))
              .add(
                  new GrammarRule(
                      new GrammarRule.LHS("S", Structure.builder().with("kind", "other").build()),
                      List.of(new RuleElement.Terminal("a")),
                      List.of()))
              .build();

      final var result = new ChartParser(grammar).parse("a");

      assertTrue(result.success());
      assertTrue(result.ambiguous());
    }

    @Test
    @DisplayName("a grammar with one derivation is not ambiguous")
    void testSingleDerivationIsNotAmbiguous() {
      final var result =
          parse(
              """
              start s;
              s --> x 'b';
              x --> 'a';
              """,
              "a b");

      assertTrue(result.success());
      assertFalse(result.ambiguous());
      assertTrue(result.diagnostics().ambiguities().isEmpty());
    }
  }

  @Nested
  @DisplayName("Constraint penalties")
  class Penalties {

    private ParseResult parse(final String grammar, final String input) {
      return new ChartParser(UnificationGrammarParserFactory.parse(grammar)).parse(input);
    }

    @Test
    @DisplayName("a rule over terminals pays its penalty once")
    void testTerminalRulePaysOnce() {
      final var result =
          parse(
              """
              start s;
              s --> x;
              x --> 'a' 'a' where equals("p", "q"):50;
              """,
              "a a");

      assertEquals(50, result.penalty());
    }

    @Test
    @DisplayName("a rule over nonterminals pays its penalty once")
    void testNonterminalRulePaysOnce() {
      final var result =
          parse(
              """
              start s;
              s --> x;
              x --> z z where equals("p", "q"):50;
              z --> 'a';
              """,
              "a a");

      assertEquals(50, result.penalty());
    }

    @Test
    @DisplayName("the start rule pays its own penalty when completed by a token")
    void testStartRulePaysWhenScanned() {
      final var result =
          parse(
              """
              start s;
              s --> 'a' where equals("p", "q"):30;
              """,
              "a");

      assertEquals(30, result.penalty());
    }

    @Test
    @DisplayName("the start rule pays its own penalty when completed by a constituent")
    void testStartRulePaysWhenCompleted() {
      final var result =
          parse(
              """
              start s;
              s --> x where equals("p", "q"):30;
              x --> 'a';
              """,
              "a");

      assertEquals(30, result.penalty());
    }
  }
}
