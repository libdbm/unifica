package com.libdbm.ugf.generator;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for Vocabulary. */
class VocabularyTests {

  private Vocabulary vocab;

  @BeforeEach
  void setup() {
    vocab =
        Vocabulary.builder()
            .add("cat", Structure.builder().with("type", "noun").with("num", "sing").build())
            .add("cats", Structure.builder().with("type", "noun").with("num", "plur").build())
            .add("dog", Structure.builder().with("type", "noun").with("num", "sing").build())
            .add("dogs", Structure.builder().with("type", "noun").with("num", "plur").build())
            .add(
                "run",
                Structure.builder()
                    .with("type", "verb")
                    .with("num", "plur")
                    .with("tense", "pres")
                    .build())
            .add(
                "runs",
                Structure.builder()
                    .with("type", "verb")
                    .with("num", "sing")
                    .with("tense", "pres")
                    .build())
            .add("ran", Structure.builder().with("type", "verb").with("tense", "past").build())
            .add("the", Structure.builder().with("type", "det").build())
            .add("a", Structure.builder().with("type", "det").build())
            .build();
  }

  @Nested
  @DisplayName("Empty vocabulary")
  class EmptyVocabulary {

    @Test
    @DisplayName("empty vocabulary has no entries")
    void empty_has_no_entries() {
      final var empty = Vocabulary.empty();

      assertTrue(empty.all().isEmpty());
    }

    @Test
    @DisplayName("empty vocabulary returns empty for queries")
    void empty_returns_empty() {
      final var empty = Vocabulary.empty();

      assertTrue(empty.byFeatures(Structure.builder().with("type", "noun").build()).isEmpty());
      assertTrue(empty.byPattern("[a-z]+", new Structure()).isEmpty());
    }
  }

  @Nested
  @DisplayName("Building")
  class Building {

    @Test
    @DisplayName("builder creates vocabulary")
    void builder_creates_vocabulary() {
      assertNotNull(vocab);
      assertFalse(vocab.all().isEmpty());
    }

    @Test
    @DisplayName("add with features")
    void add_with_features() {
      final var v =
          Vocabulary.builder()
              .add("word", Structure.builder().with("type", "name").build())
              .build();

      assertEquals(1, v.all().size());
      final var entry = v.all().getFirst();
      assertEquals("word", entry.text());
      assertEquals(StringConstant.of("name"), entry.features().get("type"));
    }

    @Test
    @DisplayName("merge combines vocabularies")
    void merge_combines() {
      final var v1 =
          Vocabulary.builder()
              .add("word1", Structure.builder().with("type", "cat1").build())
              .build();

      final var v2 =
          Vocabulary.builder()
              .add("word2", Structure.builder().with("type", "cat2").build())
              .merge(v1)
              .build();

      assertEquals(2, v2.all().size());
      // Check both types exist via feature lookup
      assertEquals(1, v2.byFeatures(Structure.builder().with("type", "cat1").build()).size());
      assertEquals(1, v2.byFeatures(Structure.builder().with("type", "cat2").build()).size());
    }
  }

  @Nested
  @DisplayName("Entry record")
  class EntryTests {

    @Test
    @DisplayName("Entry.of with features")
    void entry_of_with_features() {
      final var features = Structure.builder().with("k", "v").with("type", "noun").build();
      final var entry = Vocabulary.Entry.of("text", features);

      assertEquals("text", entry.text());
      assertEquals(StringConstant.of("v"), entry.features().get("k"));
      assertEquals(StringConstant.of("noun"), entry.features().get("type"));
    }

    @Test
    @DisplayName("Entry without specific feature has null for that feature")
    void entry_without_feature() {
      final var features = Structure.builder().with("k", "v").build();
      final var entry = Vocabulary.Entry.of("text", features);

      assertEquals("text", entry.text());
      assertNull(entry.features().get("type"));
    }
  }

  @Nested
  @DisplayName("Feature lookup")
  class FeatureLookup {

    @Test
    @DisplayName("finds words by feature")
    void finds_by_feature() {
      final var nouns = vocab.byFeatures(Structure.builder().with("type", "noun").build());

      assertEquals(4, nouns.size());
      assertTrue(nouns.contains("cat"));
      assertTrue(nouns.contains("cats"));
      assertTrue(nouns.contains("dog"));
      assertTrue(nouns.contains("dogs"));
    }

    @Test
    @DisplayName("filters by multiple feature constraints")
    void filters_by_features() {
      final var singNouns =
          vocab.byFeatures(Structure.builder().with("type", "noun").with("num", "sing").build());

      assertEquals(2, singNouns.size());
      assertTrue(singNouns.contains("cat"));
      assertTrue(singNouns.contains("dog"));
    }

    @Test
    @DisplayName("returns empty for unknown feature value")
    void returns_empty_for_unknown() {
      final var result = vocab.byFeatures(Structure.builder().with("type", "unknown").build());

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("returns empty when no features match")
    void returns_empty_when_no_match() {
      final var result =
          vocab.byFeatures(Structure.builder().with("type", "noun").with("num", "dual").build());

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("Pattern lookup")
  class PatternLookup {

    @Test
    @DisplayName("finds words by regex pattern")
    void finds_by_pattern() {
      final var matches = vocab.byPattern("c.*", new Structure());

      assertEquals(2, matches.size());
      assertTrue(matches.contains("cat"));
      assertTrue(matches.contains("cats"));
    }

    @Test
    @DisplayName("pattern with feature filter")
    void pattern_with_features() {
      final var matches = vocab.byPattern(".*s", Structure.builder().with("num", "plur").build());

      assertEquals(2, matches.size());
      assertTrue(matches.contains("cats"));
      assertTrue(matches.contains("dogs"));
    }

    @Test
    @DisplayName("matches full word")
    void matches_full_word() {
      final var matches = vocab.byPattern("cat", new Structure());

      assertEquals(1, matches.size());
      assertTrue(matches.contains("cat"));
    }

    @Test
    @DisplayName("caches pattern results")
    void caches_patterns() {
      // Call twice - second should use cache
      final var first = vocab.byPattern("[a-z]+", new Structure());
      final var second = vocab.byPattern("[a-z]+", new Structure());

      assertEquals(first.size(), second.size());
    }
  }

  @Nested
  @DisplayName("Queries")
  class Queries {

    @Test
    @DisplayName("all returns all entries")
    void all_returns_entries() {
      assertEquals(9, vocab.all().size());
    }

    @Test
    @DisplayName("all returns immutable list")
    void all_is_immutable() {
      assertThrows(UnsupportedOperationException.class, () -> vocab.all().clear());
    }
  }

  @Nested
  @DisplayName("Feature unification")
  class FeatureUnification {

    @Test
    @DisplayName("empty constraints return all entries")
    void empty_constraints() {
      final var all = vocab.byFeatures(new Structure());

      assertEquals(9, all.size());
    }

    @Test
    @DisplayName("multiple feature constraints")
    void multiple_constraints() {
      final var result =
          vocab.byFeatures(
              Structure.builder()
                  .with("type", "verb")
                  .with("num", "sing")
                  .with("tense", "pres")
                  .build());

      assertEquals(1, result.size());
      assertTrue(result.contains("runs"));
    }
  }
}
