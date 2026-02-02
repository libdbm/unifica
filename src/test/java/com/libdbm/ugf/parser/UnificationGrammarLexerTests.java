package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the UnificationGrammar ANTLR lexer. Focuses on edge cases for STRING and REGEX tokens.
 */
class UnificationGrammarLexerTests {

  @Nested
  class StringLiterals {

    @Test
    void singleQuotedSimple() {
      final var grammar = "s --> 'hello';";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
      assertEquals(1, g.rulesFor("s").size());
    }

    @Test
    void doubleQuotedSimple() {
      final var grammar = "s --> \"hello\";";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
      assertEquals(1, g.rulesFor("s").size());
    }

    @Test
    void singleQuotedWithEscapedQuote() {
      final var grammar = "s --> 'it\\'s';";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void doubleQuotedWithEscapedQuote() {
      final var grammar = "s --> \"say \\\"hello\\\"\";";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void singleQuotedWithBackslash() {
      final var grammar = "s --> 'path\\\\to\\\\file';";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void doubleQuotedWithBackslash() {
      final var grammar = "s --> \"path\\\\to\\\\file\";";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void emptyString() {
      final var grammar = "s --> '';";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void stringWithSpecialChars() {
      final var grammar = "s --> 'hello\\nworld\\ttab';";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void stringInFeatureValue() {
      final var grammar = "s{val:'quoted'} --> 'x';";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }
  }

  @Nested
  class RegexPatterns {

    @Test
    void simpleCharacterClass() {
      final var grammar = "s --> [a-z]+;";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void negatedCharacterClass() {
      final var grammar = "s --> [^abc]+;";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void charClassWithEscapedBracket() {
      final var grammar = "s --> [\\]]+;";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void charClassWithEscapedBackslash() {
      final var grammar = "s --> [\\\\]+;";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void dotWithQuantifier() {
      final var grammar = "s --> .+;";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void dotStar() {
      // Note: Bare .* is not valid - use character class instead
      final var grammar = "s --> [\\s\\S]*;";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void dotOptional() {
      final var grammar = "s --> .?;";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void nonCapturingGroup() {
      final var grammar = "s --> (?:abc)+;";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void negLookahead() {
      final var grammar = "s --> (?!bad);";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void posLookahead() {
      final var grammar = "s --> (?=good);";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }

    @Test
    void multipleRegexParts() {
      final var grammar = "s --> [a-z]+ [0-9]*;";
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertNotNull(g.rulesFor("s"));
    }
  }

  @Nested
  class ModulePathWithDots {

    @Test
    void moduleNameWithDots() {
      // Ensure '.' in module names doesn't conflict with REGEX '.'
      final var grammar =
          """
          module syntax.core;
          s --> 'x';
          """;
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertEquals("syntax.core", g.module().name());
    }

    @Test
    void importWithDottedModule() {
      final var grammar =
          """
          import syntax.core.*;
          s --> 'x';
          """;
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertFalse(g.imports().isEmpty());
    }

    @Test
    void mixedDotsAndRegex() {
      // Module path with dots, followed by regex with character class (bare .* not valid)
      final var grammar =
          """
          module a.b.c;
          s --> [a-z]+ [\\s\\S]* ;
          """;
      final var g = UnificationGrammarParserFactory.parse(grammar);
      assertEquals("a.b.c", g.module().name());
      assertNotNull(g.rulesFor("s"));
    }
  }
}
