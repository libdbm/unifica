package com.libdbm.ugf.generator;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.RuleElement;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for GrammarGenerator. */
class GrammarGeneratorTests {

  private static Grammar grammar(final String start, final GrammarRule... rules) {
    final var builder = Grammar.builder().start(start);
    for (final var rule : rules) {
      builder.add(rule);
    }
    return builder.build();
  }

  @Nested
  @DisplayName("Builder")
  class BuilderTests {

    @Test
    @DisplayName("builds with vocabulary")
    void builds_with_vocabulary() {
      // Simple grammar: sentence -> word
      final var grammar =
          grammar(
              "sentence", new GrammarRule("sentence", List.of(new RuleElement.Regex("[a-z]+"))));

      // Vocabulary with words
      final var vocabulary =
          Vocabulary.builder()
              .add("hello", Structure.builder().with("type", "greeting").build())
              .add("world", Structure.builder().with("type", "noun").build())
              .build();

      final var generator =
          GrammarGenerator.builder(grammar).vocabulary(vocabulary).random(new Random(42)).build();

      final var result = generator.generateOne("sentence", new Structure());

      assertTrue(result.isPresent());
      assertTrue(result.get().equals("hello") || result.get().equals("world"));
    }

    @Test
    @DisplayName("vocabulary matches by pattern")
    void vocabulary_matches_by_pattern() {
      // Grammar with regex pattern that only matches 3-letter words
      final var grammar =
          grammar("word", new GrammarRule("word", List.of(new RuleElement.Regex("[a-z]{3}"))));

      // Vocabulary with words of different lengths
      final var vocabulary =
          Vocabulary.builder()
              .add("dog", new Structure())
              .add("cat", new Structure())
              .add("elephant", new Structure()) // won't match [a-z]{3}
              .build();

      final var generator =
          GrammarGenerator.builder(grammar).vocabulary(vocabulary).random(new Random(42)).build();

      // Generate multiple to test pattern matching
      boolean foundDog = false;
      boolean foundCat = false;
      boolean foundElephant = false;

      for (int i = 0; i < 100; i++) {
        final var result = generator.generateOne("word", new Structure());
        if (result.isPresent()) {
          if (result.get().equals("dog")) foundDog = true;
          if (result.get().equals("cat")) foundCat = true;
          if (result.get().equals("elephant")) foundElephant = true;
        }
      }

      assertTrue(foundDog || foundCat, "Should find 3-letter words");
      assertFalse(foundElephant, "Should not match longer words");
    }

    @Test
    @DisplayName("falls back to literal for non-matching patterns")
    void falls_back_to_literal() {
      // Grammar with a literal terminal
      final var grammar =
          grammar("punct", new GrammarRule("punct", List.of(new RuleElement.Terminal("."))));

      final var vocabulary = Vocabulary.builder().add("word", new Structure()).build();
      final var generator = GrammarGenerator.builder(grammar).vocabulary(vocabulary).build();
      final var result = generator.generateOne("punct", new Structure());

      assertTrue(result.isPresent());
      assertEquals(".", result.get());
    }

    @Test
    @DisplayName("builds with multiple vocabularies")
    void builds_with_multiple_vocabularies() {
      final var grammar =
          grammar("word", new GrammarRule("word", List.of(new RuleElement.Regex("[a-z]+"))));

      final var nouns =
          Vocabulary.builder().add("dog", Structure.builder().with("type", "noun").build()).build();

      final var verbs =
          Vocabulary.builder().add("run", Structure.builder().with("type", "verb").build()).build();

      final var generator =
          GrammarGenerator.builder(grammar)
              .vocabulary(nouns)
              .vocabulary(verbs)
              .random(new Random(42))
              .build();

      // Should find words from both vocabularies
      boolean found = false;
      for (int i = 0; i < 20; i++) {
        final var result = generator.generateOne("word", new Structure());
        if (result.isPresent() && (result.get().equals("dog") || result.get().equals("run"))) {
          found = true;
          break;
        }
      }

      assertTrue(found);
    }

    @Test
    @DisplayName("respects maxDepth setting")
    void respects_max_depth() {
      // Recursive grammar
      final var grammar =
          grammar(
              "expr",
              new GrammarRule(
                  "expr",
                  List.of(
                      new RuleElement.Nonterminal("expr"),
                      new RuleElement.Terminal("+"),
                      new RuleElement.Nonterminal("expr"))),
              new GrammarRule("expr", List.of(new RuleElement.Terminal("1"))));

      final var generator =
          GrammarGenerator.builder(grammar).maxDepth(3).random(new Random(42)).build();

      // Should complete without infinite loop due to maxDepth
      final var results = generator.generate("expr", new Structure(), 5);

      assertFalse(results.isEmpty());
    }
  }

  @Nested
  @DisplayName("Generation")
  class GenerationTests {

    @Test
    @DisplayName("generates multiple results")
    void generates_multiple() {
      final var grammar =
          grammar("word", new GrammarRule("word", List.of(new RuleElement.Terminal("hello"))));

      final var generator = new GrammarGenerator(grammar, new LiteralTerminalGenerator());

      final var results = generator.generate("word", new Structure(), 5);

      assertEquals(5, results.size());
      assertTrue(results.stream().allMatch(r -> r.equals("hello")));
    }
  }
}
