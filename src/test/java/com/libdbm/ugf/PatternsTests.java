package com.libdbm.ugf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** S-L7: the portable regex subset. */
class PatternsTests {

  private static Pattern compile(final String source) {
    return Patterns.compile(source).orElseThrow();
  }

  @Test
  void testSubsetAccepted() {
    for (final var source :
        List.of(
            "abc",
            "a\\.b\\*\\[\\]\\{\\}\\(\\)\\|\\?\\+\\^\\$\\/\\-\\\\",
            "\\n\\r\\t\\f\\x41\\u00e9",
            "\\d\\D\\w\\W\\s\\S\\bx\\B",
            "\\p{L}\\p{Lu}\\P{Nd}[\\p{M}\\p{N}\\p{Pc}]",
            "[a-z][^\\]\\[\\-][-a]",
            ".",
            "(a)(?:b)(?=c)(?!d)(?<=e)(?<!f)",
            "a|b",
            "a*b+c?d{2}e{2,}f{2,3}",
            "a*?b+?c??d{2}?e{2,}?f{2,3}?",
            "(?i)abc",
            "(?s).",
            "(?is).",
            "^a$",
            "[\\u0080-\\uFFFF]")) {
      assertInstanceOf(Result.Success.class, Patterns.compile(source), source);
    }
  }

  @Test
  void testOutsideSubsetRejected() {
    for (final var source :
        List.of(
            "(a)\\1",
            "(?<name>a)",
            "a*+",
            "a++",
            "a?+",
            "a{2}+",
            "(?>a)",
            "[a[b]]",
            "[a&&b]",
            "[]a]",
            "\\Qa\\E",
            "\\A",
            "\\z",
            "\\Z",
            "\\G",
            "\\R",
            "\\h",
            "\\v",
            "\\e",
            "\\cA",
            "\\0",
            "\\x{41}",
            "\\pL",
            "\\p{Alpha}",
            "\\p{IsLatin}",
            "\\p{javaLowerCase}",
            "a(?i)b",
            "(?i:a)",
            "(?m)a",
            "(?x)a",
            "(?u)a",
            "a{",
            "a}",
            "a]",
            "[\\b]",
            "\\")) {
      final var result = Patterns.compile(source);
      final var error =
          (ErrorDetails) assertInstanceOf(Result.Failure.class, result, source).error();
      assertEquals(Patterns.SUBSET, error.code(), source);
    }
  }

  @Test
  void testDollarIsEndOfInput() {
    assertTrue(compile("a$").matcher("a").find());
    assertFalse(compile("a$").matcher("a\n").find());
    assertTrue(compile("[$]").matcher("$").matches());
    assertTrue(compile("\\$").matcher("$").matches());
  }

  @Test
  void testCaseFoldingIsUnicode() {
    assertTrue(compile("(?i)é").matcher("É").matches());
    assertTrue(compile("(?i)abc").matcher("ABC").matches());
  }

  @Test
  void testJavaSyntaxErrorsAreReported() {
    final var result = Patterns.compile("(a");
    final var error = (ErrorDetails) assertInstanceOf(Result.Failure.class, result).error();
    assertEquals(Patterns.SUBSET, error.code());
  }
}
