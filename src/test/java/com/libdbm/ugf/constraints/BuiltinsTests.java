package com.libdbm.ugf.constraints;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.features.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the builtin predicates, ported from the 1.x Context API: {@code context} keeps the
 * old binding calls on top of an immutable {@link Environment}. Arity and unknown names are
 * compile-time errors in 2.0 (S-C8); here a call the registry would reject evaluates as not passed.
 */
class BuiltinsTests {

  private static final Predicates REGISTRY = Predicates.standard();

  private Bindings context;

  @BeforeEach
  void setup() {
    context = new Bindings();
  }

  private Outcome eval(final String name, final Value... args) {
    final var entry = REGISTRY.entry(name);
    if (entry == null || !entry.accepts(args.length)) {
      return new Outcome(false);
    }
    return new Outcome(context.environment.test(new Expression.Call(name, List.of(args))));
  }

  /** Accumulates bindings into the environment the next {@link #eval} uses. */
  private static final class Bindings {
    private Environment environment = Environment.of(REGISTRY);

    Bindings withBinding(final String name, final Value value) {
      environment = environment.with(name, value);
      return this;
    }
  }

  private record Outcome(boolean passed) {}

  @Nested
  @DisplayName("all()")
  class All {

    @Test
    @DisplayName("returns all built-in predicates")
    void returns_all() {
      final var predicates = REGISTRY.names(Predicates.Phase.SYNTACTIC);

      assertFalse(predicates.isEmpty());
      assertTrue(predicates.contains("agree"));
      assertTrue(predicates.contains("unify"));
      assertTrue(predicates.contains("equals"));
      assertTrue(predicates.contains("has_feature"));
      assertTrue(predicates.contains("is_string"));
      assertTrue(predicates.contains("lt"));
      assertTrue(predicates.contains("matches"));
    }
  }

  @Nested
  @DisplayName("agree/unify")
  class Agree {

    @Test
    @DisplayName("passes when structures unify")
    void passes_when_unify() {
      final var s1 = Structure.builder().with("num", "sing").build();
      final var s2 = Structure.builder().with("num", "sing").build();
      context.withBinding("x", s1).withBinding("y", s2);

      final var result = eval("agree", Variable.of("?x"), Variable.of("?y"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("fails when structures don't unify")
    void fails_when_no_unify() {
      final var s1 = Structure.builder().with("num", "sing").build();
      final var s2 = Structure.builder().with("num", "plur").build();
      context.withBinding("x", s1).withBinding("y", s2);

      final var result = eval("agree", Variable.of("?x"), Variable.of("?y"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("fails with wrong arity")
    void fails_wrong_arity() {
      final var result = eval("agree", StringConstant.of("x"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("fails when first arg is unbound")
    void fails_unbound_first() {
      context.withBinding("y", Structure.builder().build());
      final var result = eval("agree", Variable.of("?x"), Variable.of("?y"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("unify is alias for agree")
    void unify_alias() {
      final var s = Structure.builder().with("a", "b").build();
      context.withBinding("x", s).withBinding("y", s);

      final var result = eval("unify", Variable.of("?x"), Variable.of("?y"));
      assertTrue(result.passed());
    }
  }

  @Nested
  @DisplayName("has_feature")
  class HasFeature {

    @Test
    @DisplayName("passes when feature exists")
    void passes_when_exists() {
      final var s = Structure.builder().with("num", "sing").build();
      context.withBinding("x", s);

      final var result = eval("has_feature", Variable.of("?x"), StringConstant.of("num"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("fails when feature missing")
    void fails_when_missing() {
      final var s = Structure.builder().with("num", "sing").build();
      context.withBinding("x", s);

      final var result = eval("has_feature", Variable.of("?x"), StringConstant.of("case"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("fails when not a structure")
    void fails_not_structure() {
      context.withBinding("x", StringConstant.of("hello"));

      final var result = eval("has_feature", Variable.of("?x"), StringConstant.of("num"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("fails with wrong arity")
    void fails_wrong_arity() {
      final var result = eval("has_feature", StringConstant.of("x"));
      assertFalse(result.passed());
    }
  }

  @Nested
  @DisplayName("get_feature")
  class GetFeature {

    @Test
    @DisplayName("passes when feature value matches")
    void passes_when_matches() {
      final var s = Structure.builder().with("num", "sing").build();
      context.withBinding("x", s);

      final var result =
          eval(
              "get_feature",
              Variable.of("?x"),
              StringConstant.of("num"),
              StringConstant.of("sing"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("fails when feature value differs")
    void fails_when_differs() {
      final var s = Structure.builder().with("num", "sing").build();
      context.withBinding("x", s);

      final var result =
          eval(
              "get_feature",
              Variable.of("?x"),
              StringConstant.of("num"),
              StringConstant.of("plur"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("fails when feature missing")
    void fails_when_missing() {
      final var s = Structure.builder().with("num", "sing").build();
      context.withBinding("x", s);

      final var result =
          eval(
              "get_feature",
              Variable.of("?x"),
              StringConstant.of("case"),
              StringConstant.of("nom"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("fails with wrong arity")
    void fails_wrong_arity() {
      final var result = eval("get_feature", StringConstant.of("x"), StringConstant.of("y"));
      assertFalse(result.passed());
    }
  }

  @Nested
  @DisplayName("feature_eq")
  class FeatureEq {

    @Test
    @DisplayName("passes when features equal")
    void passes_when_equal() {
      final var s1 = Structure.builder().with("num", "sing").build();
      final var s2 = Structure.builder().with("number", "sing").build();
      context.withBinding("x", s1).withBinding("y", s2);

      final var result =
          eval(
              "feature_eq",
              Variable.of("?x"),
              StringConstant.of("num"),
              Variable.of("?y"),
              StringConstant.of("number"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("fails when features differ")
    void fails_when_differ() {
      final var s1 = Structure.builder().with("num", "sing").build();
      final var s2 = Structure.builder().with("num", "plur").build();
      context.withBinding("x", s1).withBinding("y", s2);

      final var result =
          eval(
              "feature_eq",
              Variable.of("?x"),
              StringConstant.of("num"),
              Variable.of("?y"),
              StringConstant.of("num"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("fails when feature missing")
    void fails_when_missing() {
      final var s1 = Structure.builder().with("num", "sing").build();
      final var s2 = Structure.builder().build();
      context.withBinding("x", s1).withBinding("y", s2);

      final var result =
          eval(
              "feature_eq",
              Variable.of("?x"),
              StringConstant.of("num"),
              Variable.of("?y"),
              StringConstant.of("num"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("fails when not structures")
    void fails_not_structures() {
      context.withBinding("x", StringConstant.of("a")).withBinding("y", StringConstant.of("b"));

      final var result =
          eval(
              "feature_eq",
              Variable.of("?x"),
              StringConstant.of("f"),
              Variable.of("?y"),
              StringConstant.of("g"));
      assertFalse(result.passed());
    }
  }

  @Nested
  @DisplayName("equals/not_equals")
  class Equality {

    @Test
    @DisplayName("equals passes for same values")
    void equals_passes() {
      context.withBinding("x", StringConstant.of("hello"));
      final var result = eval("equals", Variable.of("?x"), StringConstant.of("hello"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("equals fails for different values")
    void equals_fails() {
      context.withBinding("x", StringConstant.of("hello"));
      final var result = eval("equals", Variable.of("?x"), StringConstant.of("world"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("not_equals passes for different values")
    void not_equals_passes() {
      context.withBinding("x", StringConstant.of("hello"));
      final var result = eval("not_equals", Variable.of("?x"), StringConstant.of("world"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("not_equals fails for same values")
    void not_equals_fails() {
      context.withBinding("x", StringConstant.of("hello"));
      final var result = eval("not_equals", Variable.of("?x"), StringConstant.of("hello"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("not_equals passes when unbound")
    void not_equals_unbound() {
      final var result = eval("not_equals", Variable.of("?x"), StringConstant.of("hello"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("a labelled constituent and a string compare by text (S-C9)")
    void equals_label_and_string() {
      context.withBinding("w", Binding.of("hello", Structure.builder().with("f", "v").build()));
      assertTrue(eval("equals", Variable.of("?w"), StringConstant.of("hello")).passed());
      assertTrue(eval("equals", StringConstant.of("hello"), Variable.of("?w")).passed());
      assertFalse(eval("equals", Variable.of("?w"), StringConstant.of("world")).passed());
      assertFalse(eval("not_equals", Variable.of("?w"), StringConstant.of("hello")).passed());
      assertTrue(eval("not_equals", StringConstant.of("world"), Variable.of("?w")).passed());
    }

    @Test
    @DisplayName("two labelled constituents compare by value")
    void equals_two_labels() {
      context.withBinding("a", Binding.of("hello", Structure.builder().with("f", "v").build()));
      context.withBinding("b", Binding.of("hello", Structure.EMPTY));
      context.withBinding("c", Binding.of("hello", Structure.builder().with("f", "v").build()));
      assertFalse(eval("equals", Variable.of("?a"), Variable.of("?b")).passed());
      assertTrue(eval("not_equals", Variable.of("?a"), Variable.of("?b")).passed());
      assertTrue(eval("equals", Variable.of("?a"), Variable.of("?c")).passed());
    }
  }

  @Nested
  @DisplayName("Type checking")
  class TypeChecking {

    @Test
    @DisplayName("is_string passes for StringConstant")
    void is_string_passes() {
      context.withBinding("x", StringConstant.of("hello"));
      final var result = eval("is_string", Variable.of("?x"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("is_string fails for NumericConstant")
    void is_string_fails() {
      context.withBinding("x", NumericConstant.of(42));
      final var result = eval("is_string", Variable.of("?x"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("is_number passes for NumericConstant")
    void is_number_passes() {
      context.withBinding("x", NumericConstant.of(3.14));
      final var result = eval("is_number", Variable.of("?x"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("is_number fails for StringConstant")
    void is_number_fails() {
      context.withBinding("x", StringConstant.of("42"));
      final var result = eval("is_number", Variable.of("?x"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("is_structure passes for Structure")
    void is_structure_passes() {
      context.withBinding("x", Structure.builder().with("a", "b").build());
      final var result = eval("is_structure", Variable.of("?x"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("is_structure fails for StringConstant")
    void is_structure_fails() {
      context.withBinding("x", StringConstant.of("hello"));
      final var result = eval("is_structure", Variable.of("?x"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("is_bound passes for bound variable")
    void is_bound_passes() {
      context.withBinding("x", StringConstant.of("val"));
      final var result = eval("is_bound", Variable.of("?x"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("is_bound fails for unbound variable")
    void is_bound_fails() {
      final var result = eval("is_bound", Variable.of("?unknown"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("is_bound passes for non-variable")
    void is_bound_non_variable() {
      final var result = eval("is_bound", StringConstant.of("literal"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("is_bound passes for a path that leads to a value")
    void is_bound_path_passes() {
      context.withBinding("w", Binding.of("x", Structure.builder().with("f", "v").build()));
      final var result = eval("is_bound", new FeaturePath(List.of("w", "f")));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("is_bound fails for a path that leads nowhere")
    void is_bound_path_fails() {
      context.withBinding("w", Binding.of("x", Structure.builder().with("f", "v").build()));
      assertFalse(eval("is_bound", new FeaturePath(List.of("w", "missing"))).passed());
      assertFalse(eval("is_bound", new FeaturePath(List.of("unknown", "f"))).passed());
    }

    @Test
    @DisplayName("is_bound fails for a value that is a variable")
    void is_bound_variable_value() {
      context.withBinding(
          "w", Binding.of("x", Structure.builder().with("f", Variable.of("Y")).build()));
      context.withBinding("X", Variable.of("Y"));
      assertFalse(eval("is_bound", new FeaturePath(List.of("w", "f"))).passed());
      assertFalse(eval("is_bound", Variable.of("X")).passed());
    }

    @Test
    @DisplayName("is_bound passes for a structure that contains variables")
    void is_bound_partial_structure() {
      context.withBinding("X", Structure.builder().with("f", Variable.of("Y")).build());
      assertTrue(eval("is_bound", Variable.of("X")).passed());
    }
  }

  @Nested
  @DisplayName("String predicates")
  class StringPredicates {

    @Test
    @DisplayName("starts_with passes")
    void starts_with_passes() {
      context.withBinding("s", StringConstant.of("hello world"));
      final var result = eval("starts_with", Variable.of("?s"), StringConstant.of("hello"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("starts_with fails")
    void starts_with_fails() {
      context.withBinding("s", StringConstant.of("hello world"));
      final var result = eval("starts_with", Variable.of("?s"), StringConstant.of("world"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("ends_with passes")
    void ends_with_passes() {
      context.withBinding("s", StringConstant.of("hello world"));
      final var result = eval("ends_with", Variable.of("?s"), StringConstant.of("world"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("ends_with fails")
    void ends_with_fails() {
      context.withBinding("s", StringConstant.of("hello world"));
      final var result = eval("ends_with", Variable.of("?s"), StringConstant.of("hello"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("contains passes")
    void contains_passes() {
      context.withBinding("s", StringConstant.of("hello world"));
      final var result = eval("contains", Variable.of("?s"), StringConstant.of("lo wo"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("contains fails")
    void contains_fails() {
      context.withBinding("s", StringConstant.of("hello world"));
      final var result = eval("contains", Variable.of("?s"), StringConstant.of("xyz"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("matches passes with regex")
    void matches_passes() {
      context.withBinding("s", StringConstant.of("abc123"));
      final var result = eval("matches", Variable.of("?s"), StringConstant.of("[a-z]+[0-9]+"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("matches fails")
    void matches_fails() {
      context.withBinding("s", StringConstant.of("abc"));
      final var result = eval("matches", Variable.of("?s"), StringConstant.of("[0-9]+"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("matches fails on invalid regex")
    void matches_invalid_regex() {
      context.withBinding("s", StringConstant.of("abc"));
      final var result = eval("matches", Variable.of("?s"), StringConstant.of("[invalid"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("not_empty passes for non-empty string")
    void not_empty_passes() {
      context.withBinding("s", StringConstant.of("hello"));
      final var result = eval("not_empty", Variable.of("?s"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("not_empty fails for empty string")
    void not_empty_fails() {
      context.withBinding("s", StringConstant.of(""));
      final var result = eval("not_empty", Variable.of("?s"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("not_empty fails for unbound")
    void not_empty_unbound() {
      final var result = eval("not_empty", Variable.of("?unknown"));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("not_empty fails for non-string")
    void not_empty_non_string() {
      context.withBinding("x", NumericConstant.of(42));
      final var result = eval("not_empty", Variable.of("?x"));
      assertFalse(result.passed());
    }
  }

  @Nested
  @DisplayName("Numeric predicates")
  class NumericPredicates {

    @Test
    @DisplayName("lt passes when less")
    void lt_passes() {
      context.withBinding("x", NumericConstant.of(5));
      final var result = eval("lt", Variable.of("?x"), NumericConstant.of(10));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("lt fails when greater or equal")
    void lt_fails() {
      context.withBinding("x", NumericConstant.of(10));
      final var result = eval("lt", Variable.of("?x"), NumericConstant.of(5));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("le passes when less or equal")
    void le_passes() {
      context.withBinding("x", NumericConstant.of(5));
      assertTrue(eval("le", Variable.of("?x"), NumericConstant.of(10)).passed());
      assertTrue(eval("le", Variable.of("?x"), NumericConstant.of(5)).passed());
    }

    @Test
    @DisplayName("le fails when greater")
    void le_fails() {
      context.withBinding("x", NumericConstant.of(10));
      final var result = eval("le", Variable.of("?x"), NumericConstant.of(5));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("gt passes when greater")
    void gt_passes() {
      context.withBinding("x", NumericConstant.of(10));
      final var result = eval("gt", Variable.of("?x"), NumericConstant.of(5));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("gt fails when less or equal")
    void gt_fails() {
      context.withBinding("x", NumericConstant.of(5));
      final var result = eval("gt", Variable.of("?x"), NumericConstant.of(10));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("ge passes when greater or equal")
    void ge_passes() {
      context.withBinding("x", NumericConstant.of(10));
      assertTrue(eval("ge", Variable.of("?x"), NumericConstant.of(5)).passed());
      assertTrue(eval("ge", Variable.of("?x"), NumericConstant.of(10)).passed());
    }

    @Test
    @DisplayName("ge fails when less")
    void ge_fails() {
      context.withBinding("x", NumericConstant.of(5));
      final var result = eval("ge", Variable.of("?x"), NumericConstant.of(10));
      assertFalse(result.passed());
    }

    @Test
    @DisplayName("numeric predicates fail for non-numbers")
    void fails_non_numeric() {
      context.withBinding("x", StringConstant.of("5"));
      final var result = eval("lt", Variable.of("?x"), NumericConstant.of(10));
      assertFalse(result.passed());
    }
  }

  @Nested
  @DisplayName("Feature path dereferencing")
  class FeaturePathDereferencing {

    @Test
    @DisplayName("dereferences simple path")
    void simple_path() {
      context.withBinding("x", StringConstant.of("value"));
      final var path = new FeaturePath(List.of("x"));
      final var result = eval("equals", path, StringConstant.of("value"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("dereferences nested path through Structure")
    void nested_path_structure() {
      final var inner = Structure.builder().with("val", "inner").build();
      final var outer = Structure.builder().with("nested", inner).build();
      context.withBinding("x", outer);

      final var path = new FeaturePath(List.of("x", "nested", "val"));
      final var result = eval("equals", path, StringConstant.of("inner"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("dereferences path through Binding")
    void path_through_binding() {
      final var features = Structure.builder().with("cat", "noun").build();
      final var binding = Binding.of("word", features);
      context.withBinding("w", binding);

      final var path = new FeaturePath(List.of("w", "cat"));
      final var result = eval("equals", path, StringConstant.of("noun"));
      assertTrue(result.passed());
    }

    @Test
    @DisplayName("returns null for missing path")
    void missing_path() {
      final var s = Structure.builder().with("a", "b").build();
      context.withBinding("x", s);

      // When dereferencing a path to a missing feature, the value is null
      // so equals should fail when comparing to any value
      final var path = new FeaturePath(List.of("x", "missing"));
      final var result = eval("equals", path, StringConstant.of("anything"));
      assertFalse(result.passed());
    }
  }

  @Nested
  @DisplayName("Unknown predicate")
  class UnknownPredicate {

    @Test
    @DisplayName("returns failure for unknown predicate")
    void unknown_fails() {
      // An unknown predicate is a compile error (S-C8): the registry has no entry for it.
      assertNull(REGISTRY.entry("nonexistent"));
      assertFalse(eval("nonexistent").passed());
    }
  }
}
