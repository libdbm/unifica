package com.libdbm.ugf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResultTests {

  private static final ErrorDetails ERROR = ErrorDetails.of("test.error", "failed");

  @Test
  void testMapTransformsSuccess() {
    final Result<Integer, ErrorDetails> result = Result.success(2);

    assertEquals(Result.success(4), result.map(value -> value * 2));
  }

  @Test
  void testMapPreservesFailure() {
    final Result<Integer, ErrorDetails> result = Result.failure(ERROR);

    final var mapped = result.map(value -> value * 2);

    assertSame(ERROR, assertInstanceOf(Result.Failure.class, mapped).error());
  }

  @Test
  void testFlatMapChainsSuccess() {
    final Result<Integer, ErrorDetails> result = Result.success(2);

    assertEquals(Result.success("2"), result.flatMap(value -> Result.success(value.toString())));
  }

  @Test
  void testFlatMapPropagatesInnerFailure() {
    final Result<Integer, ErrorDetails> result = Result.success(2);

    final Result<String, ErrorDetails> chained = result.flatMap(value -> Result.failure(ERROR));

    assertEquals(Result.failure(ERROR), chained);
  }

  @Test
  void testFlatMapSkipsOnFailure() {
    final Result<Integer, ErrorDetails> result = Result.failure(ERROR);

    final var chained =
        result.flatMap(
            value -> {
              throw new AssertionError("must not be called");
            });

    assertEquals(Result.failure(ERROR), chained);
  }

  @Test
  void testOrElse() {
    assertEquals(1, Result.<Integer, ErrorDetails>success(1).orElse(0));
    assertEquals(0, Result.<Integer, ErrorDetails>failure(ERROR).orElse(0));
  }

  @Test
  void testPatternMatching() {
    final Result<String, ErrorDetails> result = Result.failure(ERROR);

    final var text =
        switch (result) {
          case Result.Success<String, ErrorDetails>(var value) -> value;
          case Result.Failure<String, ErrorDetails>(var error) -> error.code();
        };

    assertEquals("test.error", text);
  }

  @Test
  void testFailureRequiresError() {
    assertThrows(NullPointerException.class, () -> Result.failure(null));
  }

  @Test
  void testDetailsListSeveralIssues() {
    final var details = ErrorDetails.of("grammar.invalid", List.of("first", "second"));

    assertEquals("2 issues", details.message());
    assertEquals(List.of("first", "second"), details.issues());
  }

  @Test
  void testDetailsSingleIssueBecomesMessage() {
    assertEquals("only", ErrorDetails.of("grammar.invalid", List.of("only")).message());
  }
}
