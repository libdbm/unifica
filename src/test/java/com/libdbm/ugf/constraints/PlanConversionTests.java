package com.libdbm.ugf.constraints;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.constraints.Expression.Call;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PlanConversionTests {

  private static final Call P = new Call("p", List.of());
  private static final Call Q = new Call("q", List.of());
  private static final Call R = new Call("r", List.of());

  private static Plan plan(final String constraints) {
    final var grammar =
        UnificationGrammarParserFactory.unvalidated("start S; S --> 'x' where " + constraints + ";")
            .orElseThrow();
    return Plan.of(grammar.rulesFor("S").getFirst().constraints()).orElseThrow();
  }

  private static ErrorDetails failure(final String constraints) {
    final var grammar =
        UnificationGrammarParserFactory.unvalidated("start S; S --> 'x' where " + constraints + ";")
            .orElseThrow();
    final var result = Plan.of(grammar.rulesFor("S").getFirst().constraints());
    return (ErrorDetails) assertInstanceOf(Result.Failure.class, result).error();
  }

  /** Conformance grammars that are deliberately invalid say so in their expected results. */
  private static boolean invalid(final Path grammar) throws IOException {
    final var expected = grammar.resolveSibling("expected.json");
    return Files.exists(expected)
        && Files.readString(expected).replaceAll("\\s", "").contains("\"ok\":false");
  }

  @Test
  void testRequiredAndSoftSplit() {
    assertEquals(new Plan(List.of(R), List.of(new Soft(P, 3))), plan("r(), p():3"));
  }

  @Test
  void testWeightedGroupIsOneSoftGroup() {
    final var expected =
        new Plan(List.of(), List.of(new Soft(new Expression.And(List.of(P, Q)), 7)));

    assertEquals(expected, plan("(p(), q()):7"));
  }

  /**
   * S-C6: unweighted conjunction groups are flattened, so the review's nested case keeps its costs.
   */
  @Test
  void testUnweightedGroupsFlatten() {
    final var expected = new Plan(List.of(R), List.of(new Soft(P, 3), new Soft(Q, 4)));

    assertEquals(expected, plan("r(), (p():3, q():4)"));
    assertEquals(new Plan(List.of(), List.of(new Soft(P, 7))), plan("((p():7))"));
  }

  /** S-C5: weights inside a weighted group are ignored. */
  @Test
  void testNestedWeightsIgnored() {
    final var expected =
        new Plan(List.of(), List.of(new Soft(new Expression.And(List.of(P, Q)), 9)));

    assertEquals(expected, plan("(p():3, q():4):9"));
  }

  @Test
  void testWeightUnderOrRejected() {
    assertEquals("constraint.weight", failure("(p():3 | q())").code());
  }

  @Test
  void testWeightUnderNotRejected() {
    assertEquals("constraint.weight", failure("!p():3").code());
  }

  @Test
  void testWeightedNegationIsSoft() {
    assertEquals(
        new Plan(List.of(), List.of(new Soft(new Expression.Not(P), 5))), plan("(!p()):5"));
  }

  @Test
  void testEveryErrorReported() {
    assertEquals(2, failure("(p():3 | q()), !r():2").issues().size());
  }

  @Test
  void testRequiredDisjunction() {
    assertEquals(new Plan(List.of(new Expression.Or(List.of(P, Q))), List.of()), plan("p() | q()"));
  }

  @Test
  void testOutOfRangeWeightIsSyntaxFailure() {
    final var result =
        UnificationGrammarParserFactory.unvalidated(
            "start S; S --> 'x' where p():99999999999999999999;");

    assertEquals(
        UnificationGrammarParserFactory.SYNTAX,
        ((ErrorDetails) assertInstanceOf(Result.Failure.class, result).error()).code());
  }

  @Test
  void testEveryResourceGrammarConverts() throws IOException {
    try (final Stream<Path> files = Files.walk(Path.of("src/test/resources"))) {
      // Weights beyond the int range load only once the visitor builds plans directly (task 39).
      final var unloadable =
          Path.of("src/test/resources/conformance/constraint-penalty-overflow/grammar.ug");
      for (final var file :
          files
              .filter(path -> path.toString().endsWith(".ug") && !path.equals(unloadable))
              .toList()) {
        if (invalid(file)) {
          continue;
        }
        final var grammar = UnificationGrammarParserFactory.unvalidated(file).orElseThrow();
        for (final var rules : grammar.rules().values()) {
          for (final var rule : rules) {
            assertInstanceOf(Result.Success.class, Plan.of(rule.constraints()), file + ": " + rule);
          }
        }
      }
    }
  }
}
