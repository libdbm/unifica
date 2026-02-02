package com.libdbm.ugf.constraints;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.features.NumericConstant;
import com.libdbm.ugf.features.StringConstant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for Context. */
class ContextTests {

  private Context context;

  @BeforeEach
  void setup() {
    context = new Context();
  }

  @Nested
  @DisplayName("position binding")
  class PositionBinding {

    @Test
    @DisplayName("position binding returns current position when set")
    void binding() {
      context.withBinding("position", 42);
      final var value = context.get("position");

      assertNotNull(value);
      assertInstanceOf(NumericConstant.class, value);
      assertEquals(42L, ((NumericConstant) value).value().longValue());
    }

    @Test
    @DisplayName("position returns null when not set")
    void not_set() {
      assertNull(context.get("position"));
    }
  }

  @Nested
  @DisplayName("Value bindings")
  class ValueBindings {

    @Test
    @DisplayName("withBinding Value sets binding")
    void with_binding_value() {
      final var value = StringConstant.of("hello");
      context.withBinding("x", value);

      assertEquals(value, context.get("x"));
    }

    @Test
    @DisplayName("withBinding String creates StringConstant")
    void with_binding_string() {
      context.withBinding("s", "world");
      final var result = context.get("s");

      assertInstanceOf(StringConstant.class, result);
      assertEquals("world", ((StringConstant) result).value());
    }

    @Test
    @DisplayName("withBinding long creates NumericConstant")
    void with_binding_long() {
      context.withBinding("n", 123L);
      final var result = context.get("n");

      assertInstanceOf(NumericConstant.class, result);
      assertEquals(123L, ((NumericConstant) result).value().longValue());
    }

    @Test
    @DisplayName("withBinding double creates NumericConstant")
    void with_binding_double() {
      context.withBinding("d", 3.14);
      final var result = context.get("d");

      assertInstanceOf(NumericConstant.class, result);
      assertEquals(3.14, ((NumericConstant) result).value().doubleValue(), 0.001);
    }

    @Test
    @DisplayName("withBindings adds multiple bindings")
    void with_bindings() {
      context.withBindings(Map.of("a", StringConstant.of("A"), "b", StringConstant.of("B")));

      assertEquals(StringConstant.of("A"), context.get("a"));
      assertEquals(StringConstant.of("B"), context.get("b"));
    }

    @Test
    @DisplayName("get returns null for unknown binding")
    void get_unknown() {
      assertNull(context.get("unknown"));
    }

    @Test
    @DisplayName("bindings can be overwritten")
    void overwrite() {
      context.withBinding("x", "first");
      context.withBinding("x", "second");

      assertEquals(StringConstant.of("second"), context.get("x"));
    }
  }

  @Nested
  @DisplayName("Predicates")
  class Predicates {

    @Test
    @DisplayName("withPredicate registers predicate")
    void with_predicate() {
      context.withPredicate("always_pass", (c, args) -> Result.ok());
      assertNotNull(context.predicate("always_pass"));
    }

    @Test
    @DisplayName("withPredicates registers multiple")
    void with_predicates() {
      context.withPredicates(
          Map.of(
              "p1", (c, args) -> Result.ok(),
              "p2", (c, args) -> Result.fail("no")));

      assertNotNull(context.predicate("p1"));
      assertNotNull(context.predicate("p2"));
    }

    @Test
    @DisplayName("predicate returns null for unknown")
    void predicate_unknown() {
      assertNull(context.predicate("unknown"));
    }
  }

  @Nested
  @DisplayName("Child contexts")
  class ChildContexts {

    @Test
    @DisplayName("extend creates child context")
    void extend_creates_child() {
      final var child = context.extend();
      assertNotNull(child);
      assertNotSame(context, child);
    }

    @Test
    @DisplayName("child inherits bindings")
    void child_inherits_bindings() {
      context.withBinding("x", "parent");
      final var child = context.extend();

      assertEquals(StringConstant.of("parent"), child.get("x"));
    }

    @Test
    @DisplayName("child can shadow bindings")
    void child_shadows_bindings() {
      context.withBinding("x", "parent");
      final var child = context.extend();
      child.withBinding("x", "child");

      assertEquals(StringConstant.of("child"), child.get("x"));
      assertEquals(StringConstant.of("parent"), context.get("x"));
    }

    @Test
    @DisplayName("child inherits predicates")
    void child_inherits_predicates() {
      context.withPredicate("p", (c, args) -> Result.ok());
      final var child = context.extend();

      assertNotNull(child.predicate("p"));
    }

    @Test
    @DisplayName("extend with overrides adds bindings")
    void extend_with_overrides() {
      context.withBinding("x", "original");
      final var child = context.extend(Map.of("x", StringConstant.of("override")));

      assertEquals(StringConstant.of("override"), child.get("x"));
    }
  }

  @Nested
  @DisplayName("Predicate execution")
  class PredicateExecution {

    @Test
    @DisplayName("predicate can access context bindings")
    void predicate_accesses_bindings() {
      context.withBinding("name", "test");
      context.withPredicate(
          "check_name",
          (c, args) -> {
            final var name = c.get("name");
            return name != null ? Result.ok() : Result.fail("no name");
          });

      final var predicate = context.predicate("check_name");
      final var result = predicate.apply(context, List.of());

      assertTrue(result.passed());
    }

    @Test
    @DisplayName("predicate receives arguments")
    void predicate_receives_args() {
      context.withPredicate(
          "count_args", (c, args) -> Result.result(args.size() == 2, "expected 2 args"));

      final var predicate = context.predicate("count_args");
      final var result =
          predicate.apply(context, List.of(StringConstant.of("a"), StringConstant.of("b")));

      assertTrue(result.passed());
    }
  }
}
