package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.libdbm.ugf.compiler.Compiled;
import com.libdbm.ugf.compiler.Compiler;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Per-parse predicate memoization (PRF-2). */
class MemoTests {

  private static final String GRAMMAR =
      "start S; S --> T T T T; T --> X:x where counted(x); X --> 'a'; X --> 'b';";
  private static final String LEXICAL =
      "start S; S --> T T T T; T --> X where state_counted(); X --> 'a';";

  private AtomicInteger count;

  @BeforeEach
  void setup() {
    count = new AtomicInteger();
  }

  private Compiled compile(final String source) {
    final var predicates =
        Predicates.builder()
            .lexical()
            .builtins()
            .add(
                "counted",
                1,
                1,
                Predicates.Phase.SYNTACTIC,
                (environment, args) -> count.incrementAndGet() > 0)
            .add(
                "state_counted",
                0,
                0,
                Predicates.Phase.LEXICAL,
                (environment, args) -> count.incrementAndGet() > 0)
            .build();
    return Compiler.compile(
            UnificationGrammarParserFactory.unvalidated(source).orElseThrow(), predicates)
        .orElseThrow();
  }

  @Test
  void testCalledOncePerDistinctArguments() {
    final var result = Parser.of(compile(GRAMMAR)).parse("a b a b");

    assertEquals(Outcome.ACCEPTED, result.outcome());
    assertEquals(2, count.get());
    assertEquals(2, result.statistics().calls());
    assertEquals(2, result.statistics().hits());
  }

  @Test
  void testMemoIsPerParse() {
    final var parser = Parser.of(compile(GRAMMAR));

    parser.parse("a a a a");
    parser.parse("a a a a");

    assertEquals(2, count.get());
  }

  @Test
  void testLexicalPredicatesAlwaysCalled() {
    final var result = Parser.of(compile(LEXICAL)).parse("a a a a");

    assertEquals(Outcome.ACCEPTED, result.outcome());
    assertEquals(4, count.get());
    assertEquals(0, result.statistics().hits());
  }

  /**
   * Positional predicates read the constituent's position, so equal arguments are not enough to
   * reuse a result.
   */
  @Test
  void testPositionalPredicatesNotCached() {
    final var compiled =
        Compiler.compile(
                UnificationGrammarParserFactory.unvalidated(
                        "start S; S --> T U; T --> X where at_start(); U --> X where !at_start(); X --> 'a';")
                    .orElseThrow(),
                Predicates.standard())
            .orElseThrow();

    assertEquals(Outcome.ACCEPTED, Parser.of(compiled).parse("a a").outcome());
  }
}
