package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.libdbm.ugf.compiler.Compiled;
import com.libdbm.ugf.compiler.Compiler;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.lexer.TokenSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OptionsTests {

  private static final String AMBIGUOUS = "start S; S --> S S; S --> A; A --> 'a';";
  private static final String AGREEMENT =
      "start S; S --> N{num: X} V{num: X};"
          + " N{num: sg} --> 'dog'; V{num: sg} --> 'runs'; V{num: pl} --> 'run';";

  private static Compiled compile(final String source) {
    return Compiler.compile(
            UnificationGrammarParserFactory.unvalidated(source).orElseThrow(),
            Predicates.standard())
        .orElseThrow();
  }

  private static String as(final int count) {
    return String.join(" ", Collections.nCopies(count, "a"));
  }

  @Test
  void testStateLimit() {
    final var parser =
        Parser.of(
            compile(AMBIGUOUS), new Options(Limits.NONE.states(500), ParseObserver.NOOP, false));

    final var result = parser.parse(as(40));

    assertEquals(Outcome.LIMIT, result.outcome());
    assertNull(result.tree());
    assertTrue(result.statistics().states() <= 501);
  }

  @Test
  void testAgendaLimit() {
    final var parser =
        Parser.of(
            compile(AMBIGUOUS), new Options(Limits.NONE.agenda(50), ParseObserver.NOOP, false));

    assertEquals(Outcome.LIMIT, parser.parse(as(10)).outcome());
  }

  /** Packed derivations keep the worst-case ambiguous grammar polynomial. */
  @Test
  void testAmbiguityStaysPolynomial() {
    final var result = Parser.of(compile(AMBIGUOUS)).parse(as(40));

    assertEquals(Outcome.ACCEPTED, result.outcome());
    assertTrue(result.ambiguous());
    assertTrue(result.statistics().states() < 10_000, "states: " + result.statistics().states());
  }

  @Test
  void testUnlimitedByDefault() {
    assertEquals(Outcome.ACCEPTED, Parser.of(compile(AMBIGUOUS)).parse(as(12)).outcome());
  }

  @Test
  void testCancellation() {
    final var parser = Parser.of(compile(AMBIGUOUS));
    Thread.currentThread().interrupt();
    try {
      assertEquals(Outcome.CANCELLED, parser.parse(as(5)).outcome());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void testDiagnosticsOffByDefault() {
    assertNull(Parser.of(compile(AGREEMENT)).parse("dog run").diagnostics());
  }

  @Test
  void testDiagnosticsRecorded() {
    final var parser =
        Parser.of(compile(AGREEMENT), new Options(Limits.NONE, ParseObserver.NOOP, true));

    final var diagnostics = parser.parse("dog run").diagnostics();

    assertFalse(diagnostics.unificationFailures().isEmpty());
    assertFalse(diagnostics.parsingIssues().isEmpty());
  }

  @Test
  void testDiagnosticsCapped() {
    final var parser =
        Parser.of(
            compile(AGREEMENT), new Options(Limits.NONE.diagnostics(1), ParseObserver.NOOP, true));

    final var diagnostics = parser.parse("dog run dog run").diagnostics();

    assertEquals(
        1,
        diagnostics.unificationFailures().size()
            + diagnostics.parsingIssues().size()
            + diagnostics.tokenizationErrors().size());
  }

  @Test
  void testObserverReceivesEvents() {
    final var unifications = new AtomicInteger();
    final var ends = new AtomicInteger();
    final var observer =
        new ParseObserver() {
          @Override
          public void onUnification(final ParseEvents.Unification event) {
            unifications.incrementAndGet();
          }

          @Override
          public void onEnd(final ParseEvents.End event) {
            ends.incrementAndGet();
          }
        };
    final var compiled = compile(AGREEMENT);
    final var observed =
        Parser.of(compiled, new Options(Limits.NONE, observer, false)).parse("dog runs");
    final var plain = Parser.of(compiled).parse("dog runs");

    assertTrue(unifications.get() > 0);
    assertEquals(1, ends.get());
    assertEquals(plain.tree(), observed.tree());
  }

  @Test
  void testStatisticsPopulated() {
    final var statistics = Parser.of(compile(AGREEMENT)).parse("dog runs").statistics();

    assertEquals(3, statistics.nodes());
    assertTrue(statistics.edges() >= 2);
    assertTrue(statistics.states() > 0);
    assertTrue(statistics.agenda() > 0);
    assertTrue(statistics.completions() > 0);
    assertTrue(statistics.unifications() > 0);
    assertTrue(statistics.nanos() > 0);
  }

  /** Review: every stopped parse says which resource ran out. */
  @Test
  void testStopReasons() {
    final var states =
        Parser.of(
                compile(AMBIGUOUS), new Options(Limits.NONE.states(500), ParseObserver.NOOP, false))
            .parse(as(40));
    assertEquals(Stop.STATES, states.stop().resource());
    assertEquals(500, states.stop().allowed());
    assertTrue(states.stop().observed() > 500);

    final var agenda =
        Parser.of(
                compile(AMBIGUOUS), new Options(Limits.NONE.agenda(50), ParseObserver.NOOP, false))
            .parse(as(10));
    assertEquals(Stop.AGENDA, agenda.stop().resource());

    final var penalty =
        Parser.of(
                compile(
                    "start S; S --> A A where equals('a', 'b'):9223372036854775807,"
                        + " equals('b', 'a'):1; A --> 'a';"))
            .parse("a a");
    assertEquals(Outcome.LIMIT, penalty.outcome());
    assertEquals(Stop.PENALTY, penalty.stop().resource());
  }

  /** A lexer failure is reported with its reason instead of a bare LIMIT. */
  @Test
  void testLexerStopReported() {
    final var result =
        Parser.of(
                compile(
                    "start S; S --> A; A --> 'a' where equals('a', 'b'):9223372036854775807 @1;"))
            .parse("a");

    assertEquals(Outcome.LIMIT, result.outcome());
    assertEquals(Stop.PENALTY, result.stop().resource());
    assertTrue(result.stop().message().contains("overflow"), result.stop().message());
    assertNotNull(result.statistics());
  }

  @Test
  void testCancellationReported() {
    final var parser = Parser.of(compile(AMBIGUOUS));
    Thread.currentThread().interrupt();
    try {
      final var result = parser.parse(as(5));
      assertEquals(Stop.INTERRUPT, result.stop().resource());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void testNoStopWhenFinished() {
    assertNull(Parser.of(compile(AGREEMENT)).parse("dog runs").stop());
    assertNull(Parser.of(compile(AGREEMENT)).parse("dog run").stop());
  }

  private static final String BRANCHING =
      "start S; S --> T*; T --> A | B; A --> 'a' ==> X; B --> 'a' ==> Y;";

  /** Options.DEFAULT ships with finite limits (review H3). */
  @Test
  void testDefaultLimitsAreFinite() {
    final var limits = Options.DEFAULT.limits();

    assertTrue(limits.states() > 0 && limits.agenda() > 0 && limits.links() > 0);
    assertTrue(limits.nodes() > 0 && limits.depth() > 0 && limits.tree() > 0);
    assertTrue(limits.diagnostics() > 0 && !limits.deadline().isZero());
  }

  /** Exponential lexical state branching stops at the node limit, before parsing starts. */
  @Test
  void testNodeLimitStopsLexer() {
    final var result =
        Parser.of(
                compile(BRANCHING),
                new Options(Limits.NONE.nodes(1_000), ParseObserver.NOOP, false))
            .parse("a ".repeat(40).strip());

    assertEquals(Outcome.LIMIT, result.outcome());
    assertEquals(Stop.NODES, result.stop().resource());
    assertEquals(1_000, result.stop().allowed());
  }

  @Test
  void testDepthLimit() {
    final var result =
        Parser.of(
                compile("start S; S --> A*; A --> 'a' ==> X;"),
                new Options(Limits.NONE.depth(10), ParseObserver.NOOP, false))
            .parse("a ".repeat(50).strip());

    assertEquals(Stop.DEPTH, result.stop().resource());
  }

  @Test
  void testDeadline() {
    final var result =
        Parser.of(
                compile(AMBIGUOUS),
                new Options(Limits.NONE.deadline(Duration.ofNanos(1)), ParseObserver.NOOP, false))
            .parse(as(60));

    assertEquals(Outcome.LIMIT, result.outcome());
    assertEquals(Stop.DEADLINE, result.stop().resource());
  }

  @Test
  void testLinkLimit() {
    final var result =
        Parser.of(
                compile(AMBIGUOUS), new Options(Limits.NONE.links(100), ParseObserver.NOOP, false))
            .parse(as(30));

    assertEquals(Stop.LINKS, result.stop().resource());
  }

  @Test
  void testTreeLimit() {
    final var result =
        Parser.of(
                compile("start S; S --> 'x' S | 'y';"),
                new Options(Limits.NONE.tree(100), ParseObserver.NOOP, false))
            .parse("x ".repeat(1_000) + "y");

    assertEquals(Outcome.LIMIT, result.outcome());
    assertEquals(Stop.TREE, result.stop().resource());
  }

  /** A caller-supplied or enhanced graph is held to the same node limit. */
  @Test
  void testCallerGraphNodeLimit() {
    final var tokens = new ArrayList<Token>();
    for (var index = 0; index < 50; index++) {
      tokens.add(new Token("a", Structure.EMPTY, index * 2, index * 2 + 1, "A"));
    }
    final var graph = TokenSource.of(tokens).tokenize("").orElseThrow();
    final var result =
        Parser.of(
                compile("start S; S --> A*; A --> 'a';"),
                new Options(Limits.NONE.nodes(10), ParseObserver.NOOP, false))
            .parse(graph);

    assertEquals(Stop.NODES, result.stop().resource());
  }

  /** Interruption is noticed while lexing, not only between chart columns. */
  @Test
  void testInterruptedWhileLexing() {
    final var parser =
        Parser.of(compile(BRANCHING), new Options(Limits.NONE, ParseObserver.NOOP, false));
    Thread.currentThread().interrupt();
    try {
      final var result = parser.parse("a ".repeat(30).strip());
      assertEquals(Outcome.CANCELLED, result.outcome());
      assertEquals(Stop.INTERRUPT, result.stop().resource());
    } finally {
      Thread.interrupted();
    }
  }

  /** The diagnostics limit bounds the lattice snapshot too, and says when it was cut short. */
  @Test
  void testLatticeBounded() {
    final var result =
        Parser.of(
                compile(AMBIGUOUS + " B --> 'b';"),
                new Options(Limits.NONE.diagnostics(5), ParseObserver.NOOP, true))
            .parse(as(20) + " b");

    assertEquals(Outcome.REJECTED, result.outcome());
    final var lattice = result.diagnostics().lattice();
    final var items = lattice.columns().stream().mapToInt(column -> column.items().size()).sum();
    assertTrue(items <= 5, "items: " + items);
    assertTrue(lattice.truncated());
    assertTrue(lattice.render().contains("truncated"));
  }

  /** A callback's exception is not caught: it ends the parse and reaches the caller. */
  @Test
  void testCallbackExceptionPropagates() {
    final var predicates =
        Predicates.builder()
            .builtins()
            .add(
                "broken",
                0,
                0,
                Predicates.Phase.SYNTACTIC,
                (environment, args) -> {
                  throw new IllegalStateException("broken predicate");
                })
            .build();
    final var compiled =
        Compiler.compile(
                UnificationGrammarParserFactory.unvalidated("start S; S --> 'a' where broken();")
                    .orElseThrow(),
                predicates)
            .orElseThrow();

    final var thrown =
        assertThrows(IllegalStateException.class, () -> Parser.of(compiled).parse("a"));

    assertEquals("broken predicate", thrown.getMessage());
  }
}
