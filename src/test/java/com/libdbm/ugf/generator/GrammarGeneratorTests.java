package com.libdbm.ugf.generator;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.RuleElement;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.parser.Limits;
import com.libdbm.ugf.parser.Outcome;
import com.libdbm.ugf.parser.ParserFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
          GrammarGenerator.builder(grammar)
              .vocabulary(vocabulary)
              .random(new Random(42))
              .build()
              .orElseThrow();

      final var result =
          generator
              .generateOne("sentence", Structure.EMPTY)
              .map(Optional::of)
              .orElse(Optional.empty());

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
              .add("dog", Structure.EMPTY)
              .add("cat", Structure.EMPTY)
              .add("elephant", Structure.EMPTY) // won't match [a-z]{3}
              .build();

      final var generator =
          GrammarGenerator.builder(grammar)
              .vocabulary(vocabulary)
              .random(new Random(42))
              .build()
              .orElseThrow();

      // Generate multiple to test pattern matching
      boolean foundDog = false;
      boolean foundCat = false;
      boolean foundElephant = false;

      for (int i = 0; i < 100; i++) {
        final var result =
            generator
                .generateOne("word", Structure.EMPTY)
                .map(Optional::of)
                .orElse(Optional.empty());
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

      final var vocabulary = Vocabulary.builder().add("word", Structure.EMPTY).build();
      final var generator =
          GrammarGenerator.builder(grammar).vocabulary(vocabulary).build().orElseThrow();
      final var result =
          generator
              .generateOne("punct", Structure.EMPTY)
              .map(Optional::of)
              .orElse(Optional.empty());

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
              .build()
              .orElseThrow();

      // Should find words from both vocabularies
      boolean found = false;
      for (int i = 0; i < 20; i++) {
        final var result =
            generator
                .generateOne("word", Structure.EMPTY)
                .map(Optional::of)
                .orElse(Optional.empty());
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
          GrammarGenerator.builder(grammar)
              .maxDepth(3)
              .random(new Random(42))
              .build()
              .orElseThrow();

      // Should complete without infinite loop due to maxDepth
      final var results = generator.generate("expr", Structure.EMPTY, 5).orElse(List.of());

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

      final var generator =
          GrammarGenerator.builder(grammar)
              .terminal(new LiteralTerminalGenerator())
              .build()
              .orElseThrow();

      final var results = generator.generate("word", Structure.EMPTY, 5).orElse(List.of());

      assertEquals(5, results.size());
      assertTrue(results.stream().allMatch(r -> r.equals("hello")));
    }
  }

  @Nested
  @DisplayName("2.0 generation (tasks 36 to 38)")
  class Guarantees {

    private GrammarGenerator generator(final String source, final long seed) {
      final var grammar = UnificationGrammarParserFactory.unvalidated(source).orElseThrow();
      return GrammarGenerator.builder(grammar).random(new Random(seed)).build().orElseThrow();
    }

    private static final String AGREEMENT =
        "start S; S --> N{num: X} V{num: X};"
            + " N{num: sg} --> 'dog'; N{num: pl} --> 'dogs'; V{num: sg} --> 'runs'; V{num: pl} --> 'run';";

    /** Review 2: bindings flow between siblings, so no output mixes numbers. */
    @Test
    void testSiblingAgreement() {
      final var sentences =
          generator(AGREEMENT, 1).generate("S", Structure.EMPTY, 50).orElseThrow();

      assertEquals(50, sentences.size());
      assertTrue(
          sentences.stream()
              .allMatch(sentence -> sentence.equals("dog runs") || sentence.equals("dogs run")),
          sentences::toString);
      assertTrue(
          sentences.contains("dog runs") && sentences.contains("dogs run"), sentences::toString);
    }

    /** S-N1: the requested features constrain the output. */
    @Test
    void testRequestedFeatures() {
      final var grammar =
          "start S; S{num: X} --> N{num: X} V{num: X};"
              + " N{num: sg} --> 'dog'; N{num: pl} --> 'dogs'; V{num: sg} --> 'runs'; V{num: pl} --> 'run';";
      final var plural = Structure.builder().with("num", "pl").build();

      final var sentences = generator(grammar, 2).generate("S", plural, 20).orElseThrow();

      assertTrue(sentences.stream().allMatch("dogs run"::equals), sentences::toString);
    }

    /** S-N2: required constraints on labelled constituents hold in every output. */
    @Test
    void testConstraintOnLabels() {
      final var grammar =
          "start S; S --> W:w where starts_with(w, 'a'); W --> 'apple'; W --> 'banana'; W --> 'avocado';";

      final var sentences = generator(grammar, 3).generate("S", Structure.EMPTY, 30).orElseThrow();

      assertTrue(
          sentences.stream().allMatch(sentence -> sentence.startsWith("a")), sentences::toString);
    }

    @Test
    void testFailureReported() {
      final var grammar =
          "start S; S{num: X} --> N{num: X} V{num: X};"
              + " N{num: sg} --> 'dog'; N{num: pl} --> 'dogs'; V{num: sg} --> 'runs'; V{num: pl} --> 'run';";
      final var result =
          generator(grammar, 4).generateOne("S", Structure.builder().with("num", "dual").build());

      final var error = (ErrorDetails) assertInstanceOf(Result.Failure.class, result).error();
      assertEquals(GrammarGenerator.FAILED, error.code());
      assertFalse(error.message().isEmpty());
    }

    /** GEN-8: builder call order does not change seeded output. */
    @Test
    void testSeedOrderIndependent() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated("start S; S --> W W W; W --> [a-z]+;")
              .orElseThrow();
      final var vocabulary =
          Vocabulary.builder()
              .add("alpha", Structure.EMPTY)
              .add("beta", Structure.EMPTY)
              .add("gamma", Structure.EMPTY)
              .add("delta", Structure.EMPTY)
              .build();

      final var first =
          GrammarGenerator.builder(grammar)
              .vocabulary(vocabulary)
              .random(new Random(7))
              .build()
              .orElseThrow();
      final var second =
          GrammarGenerator.builder(grammar)
              .random(new Random(7))
              .vocabulary(vocabulary)
              .build()
              .orElseThrow();

      assertEquals(
          first.generate("S", Structure.EMPTY, 10).orElseThrow(),
          second.generate("S", Structure.EMPTY, 10).orElseThrow());
    }

    @Test
    void testPolicyDepthAndJoiner() {
      final var grammar =
          UnificationGrammarParserFactory.unvalidated("start S; S --> 'a' S | 'b';").orElseThrow();
      final var generator =
          GrammarGenerator.builder(grammar)
              .random(new Random(5))
              .policy(Policy.DEFAULT.depth(3).joiner(Joiner.SPACE))
              .build()
              .orElseThrow();

      final var sentences = generator.generate("S", Structure.EMPTY, 20).orElseThrow();

      assertTrue(
          sentences.stream().allMatch(sentence -> sentence.split(" ").length <= 3),
          sentences::toString);
    }

    /**
     * S-N1 as a property: every generated sentence parses, for every test and ported grammar that
     * generates.
     */
    @Test
    void testRoundTripProperty() throws Exception {
      final var files =
          new ArrayList<Path>(
              List.of(
                  Path.of("src/test/resources/math.ug"), Path.of("src/test/resources/simple.ug")));
      try (final var stream = Files.list(Path.of("src/test/resources/grammars"))) {
        stream.sorted().forEach(directory -> files.add(directory.resolve("grammar.ug")));
      }
      var generated = 0;
      for (final var file : files) {
        final var grammar = UnificationGrammarParserFactory.parseWithImports(file).orElseThrow();
        final var parser = ParserFactory.create(grammar).orElseThrow();
        final var generator =
            GrammarGenerator.builder(grammar)
                .random(new Random(11))
                .policy(Policy.DEFAULT.depth(12))
                .build()
                .orElseThrow();
        for (final var sentence :
            generator.generate(grammar.start(), Structure.EMPTY, 20).orElse(List.of())) {
          assertEquals(Outcome.ACCEPTED, parser.parse(sentence).outcome(), file + ": " + sentence);
          generated++;
        }
      }
      assertTrue(generated > 100, "generated " + generated);
    }
  }

  private static GrammarGenerator generator(
      final String source, final Policy policy, final long seed) {
    return GrammarGenerator.builder(UnificationGrammarParserFactory.parse(source).orElseThrow())
        .random(new Random(seed))
        .policy(policy)
        .build()
        .orElseThrow();
  }

  /** Review: a partial result says how many sentences were requested and why the rest failed. */
  @Test
  void testBatchReportsCounts() {
    final var generator =
        generator(
            "start S; S --> A | B; A --> 'a'; B --> C; C --> D; D --> 'd';",
            new Policy(20, 1, 3, 100, 3, Joiner.SPACED),
            7);

    final var batch = generator.batch("S", Structure.EMPTY, 20);

    assertEquals(20, batch.requested());
    assertFalse(batch.sentences().isEmpty());
    assertFalse(batch.failures().isEmpty());
    assertEquals(
        20,
        batch.sentences().size()
            + batch.failures().values().stream().mapToInt(Integer::intValue).sum());
  }

  /** The validation parse uses the generator's limits and reports when it stops. */
  @Test
  void testValidationLimitReported() {
    final var generator =
        GrammarGenerator.builder(
                UnificationGrammarParserFactory.parse("start S; S --> 'a' 'b' 'c';").orElseThrow())
            .limits(Limits.NONE.states(1))
            .build()
            .orElseThrow();

    final var result = generator.generateOne("S", Structure.EMPTY);

    final var failure = assertInstanceOf(Result.Failure.class, result);
    assertTrue(
        ((ErrorDetails) failure.error()).message().contains("states"),
        ((ErrorDetails) failure.error()).message());
  }

  /** The length policy stops an expansion as soon as its tokens are too long. */
  @Test
  void testLengthCheckedWhileBuilding() {
    final var generator =
        generator(
            "start S; S --> 'x' S | 'x';",
            new Policy(10_000, 1, 3, 10, 1_000_000, Joiner.SPACED),
            3);

    for (var index = 0; index < 20; index++) {
      if (generator.generateOne("S", Structure.EMPTY)
          instanceof Result.Success<String, ErrorDetails>(var sentence)) {
        assertTrue(sentence.length() <= 10, sentence);
      }
    }
  }

  /** An expansion abandoned for length is reported as such, not as a missing derivation. */
  @Test
  void testLengthFailureReported() {
    final var generator =
        generator("start S; S --> 'x' 'x' 'x';", new Policy(10, 1, 3, 2, 1_000, Joiner.SPACED), 1);

    final var result = generator.generateOne("S", Structure.EMPTY);

    final var failure = assertInstanceOf(Result.Failure.class, result);
    assertTrue(
        ((ErrorDetails) failure.error()).message().contains("exceed"),
        ((ErrorDetails) failure.error()).message());
  }
}
