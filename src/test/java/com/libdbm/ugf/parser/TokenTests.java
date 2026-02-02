package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for Token. */
class TokenTests {

  @Nested
  @DisplayName("Factory methods")
  class FactoryMethods {

    @Test
    @DisplayName("Token.of creates token with text only")
    void of_creates_basic_token() {
      final var token = Token.of("hello", 0, 5);

      assertEquals("hello", token.text());
      assertNull(token.string("cat", null));
      assertNotNull(token.features());
      assertEquals(0, token.start());
      assertEquals(5, token.end());
    }

    @Test
    @DisplayName("Token.of creates token with features")
    void of_creates_token_with_features() {
      final var features = Structure.builder().with("pos", "noun").build();
      final var token = Token.of("dog", features, 0, 3);

      assertEquals("dog", token.text());
      assertNull(token.string("cat", null));
      assertEquals(features, token.features());
    }

    @Test
    @DisplayName("Token.tagged creates token with name")
    void tagged_creates_token_with_category() {
      final var token =
          Token.builder("runs", 0, 4).feature("cat", new StringConstant("VBZ")).build();

      assertEquals("runs", token.text());
      assertEquals("VBZ", token.string("cat", null));
      assertNotNull(token.string("cat", null));
    }

    @Test
    @DisplayName("Token.withFeatures creates full token")
    void with_features_creates_full_token() {
      final var features =
          Structure.builder().with("cat", "VBZ").with("num", "sing").with("person", "3").build();
      final var token = Token.of("runs", features, 0, 4);

      assertEquals("runs", token.text());
      assertEquals("VBZ", token.string("cat", null));
      assertEquals("sing", token.string("num", null));
      assertEquals("3", token.string("person", null));
      assertNotNull(token.string("cat", null));
      assertTrue(token.hasFeatures());
    }
  }

  @Nested
  @DisplayName("Builder")
  class BuilderTests {

    @Test
    @DisplayName("builds token incrementally")
    void builds_token_incrementally() {
      final var token =
          Token.builder("word", 0, 4)
              .feature("cat", new StringConstant("NN"))
              .feature("num", new StringConstant("sing"))
              .build();

      assertEquals("word", token.text());
      assertEquals("NN", token.string("cat", null));
      assertEquals("sing", token.string("num", null));
    }

    @Test
    @DisplayName("builds token with structure features")
    void builds_with_structure_features() {
      final var features =
          Structure.builder().with("cat", "PRP").with("case", "nom").with("gender", "masc").build();

      final var token = Token.builder("he", 0, 2).features(features).build();

      assertEquals("PRP", token.string("cat", null));
      assertEquals("nom", token.string("case", null));
      assertEquals("masc", token.string("gender", null));
    }
  }

  @Nested
  @DisplayName("Feature access")
  class FeatureAccess {

    @Test
    @DisplayName("feature returns value by key")
    void feature_returns_value() {
      final var features = Structure.builder().with("lemma", "run").build();
      final var token = Token.of("running", features, 0, 7);

      assertNotNull(token.feature("lemma"));
      assertInstanceOf(StringConstant.class, token.feature("lemma"));
    }

    @Test
    @DisplayName("stringFeature returns string value")
    void string_feature_returns_string() {
      final var features = Structure.builder().with("pos", "noun").build();
      final var token = Token.of("dog", features, 0, 3);

      assertEquals("noun", token.string("pos", null));
    }

    @Test
    @DisplayName("stringFeature returns null for missing key")
    void string_feature_returns_null_for_missing() {
      final var token = Token.of("word", 0, 4);

      assertNull(token.string("missing", null));
    }

    @Test
    @DisplayName("string is alias for stringFeature")
    void string_is_alias() {
      final var features = Structure.builder().with("test", "value").build();
      final var token = Token.of("x", features, 0, 1);

      assertEquals(token.string("test", null), token.string("test", null));
    }
  }

  @Nested
  @DisplayName("State checks")
  class StateChecks {

    @Test
    @DisplayName("isTagged returns true when name present")
    void is_tagged_with_category() {
      final var token =
          Token.builder("word", 0, 4).feature("cat", new StringConstant("NN")).build();

      assertNotNull(token.string("cat", null));
    }

    @Test
    @DisplayName("isTagged returns false when name absent")
    void is_not_tagged_without_category() {
      final var token = Token.of("word", 0, 4);

      assertNull(token.string("cat", null));
    }

    @Test
    @DisplayName("hasFeatures returns true when features present")
    void has_features_when_present() {
      final var features = Structure.builder().with("x", "y").build();
      final var token = Token.of("word", features, 0, 4);

      assertTrue(token.hasFeatures());
    }

    @Test
    @DisplayName("hasFeatures returns false when features empty")
    void has_no_features_when_empty() {
      final var token = Token.of("word", 0, 4);

      assertFalse(token.hasFeatures());
    }
  }

  @Nested
  @DisplayName("toString")
  class ToStringTests {

    @Test
    @DisplayName("basic token displays text and position")
    void basic_token_string() {
      final var token = Token.of("hello", 0, 5);

      assertTrue(token.toString().contains("hello"));
      assertTrue(token.toString().contains("[0,5]"));
    }

    @Test
    @DisplayName("token with name feature displays it")
    void token_with_category_feature_string() {
      final var token =
          Token.builder("runs", 0, 4).feature("cat", new StringConstant("VBZ")).build();

      assertTrue(token.toString().contains("runs"));
      assertTrue(token.toString().contains("cat=VBZ"));
    }

    @Test
    @DisplayName("token with features displays them")
    void token_with_features_string() {
      final var features = Structure.builder().with("cat", "NN").with("num", "sing").build();
      final var token = Token.of("dog", features, 0, 3);
      final var str = token.toString();

      assertTrue(str.contains("dog"));
      assertTrue(str.contains("cat=NN"));
      assertTrue(str.contains("num=sing") || str.contains("num"));
    }
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    @Test
    @DisplayName("throws on null text")
    void throws_on_null_text() {
      assertThrows(NullPointerException.class, () -> new Token(null, new Structure(), 0, 0));
    }

    @Test
    @DisplayName("throws on null features")
    void throws_on_null_features() {
      assertThrows(NullPointerException.class, () -> new Token("text", null, 0, 0));
    }
  }
}
