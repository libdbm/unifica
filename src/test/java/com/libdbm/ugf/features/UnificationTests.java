package com.libdbm.ugf.features;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UnificationTests {

  @Nested
  @DisplayName("Value Type Tests")
  class ValueTypeTests {
    @Test
    @DisplayName("StringConstant is atomic and not variable")
    void string_constant_properties() {
      final var s = new StringConstant("test");
      assertFalse(s.isVariable());
      assertTrue(s.isAtomic());
      assertEquals("test", s.display());
      assertEquals("test", s.value());
    }

    @Test
    @DisplayName("NumericConstant is atomic and not variable")
    void numeric_constant_properties() {
      final var n = NumericConstant.of(42);
      assertFalse(n.isVariable());
      assertTrue(n.isAtomic());
      assertEquals("42", n.display());
      assertEquals(42, n.asInteger());
    }

    @Test
    @DisplayName("BooleanConstant is atomic and not variable")
    void boolean_constant_properties() {
      final var t = BooleanConstant.TRUE;
      assertFalse(t.isVariable());
      assertTrue(t.isAtomic());
      assertEquals("true", t.display());
      assertTrue(t.value());

      final var f = BooleanConstant.FALSE;
      assertEquals("false", f.display());
      assertFalse(f.value());
    }

    @Test
    @DisplayName("Variable is variable and not atomic")
    void variable_properties() {
      final var v = Variable.of("?x");
      assertTrue(v.isVariable());
      assertFalse(v.isAtomic());
      assertEquals("?x", v.display());
      assertEquals("x", v.name());
    }

    @Test
    @DisplayName("Structure is not variable and not atomic")
    void structure_properties() {
      final var fs = Structure.builder().with("num", "sing").build();
      assertFalse(fs.isVariable());
      assertFalse(fs.isAtomic());
      assertTrue(fs.display().contains("num"));
    }

    @Test
    @DisplayName("Variable.of strips leading ? if present")
    void variable_of_strips_question_mark() {
      final var v1 = Variable.of("?x");
      final var v2 = Variable.of("x");
      assertEquals("x", v1.name());
      assertEquals("x", v2.name());
      assertEquals(v1, v2);
    }

    @Test
    @DisplayName("StringConstant requires non-null value")
    void string_constant_null_check() {
      assertThrows(NullPointerException.class, () -> new StringConstant(null));
    }

    @Test
    @DisplayName("Variable requires non-null name")
    void variable_null_check() {
      assertThrows(NullPointerException.class, () -> new Variable(null));
      assertThrows(NullPointerException.class, () -> Variable.of(null));
    }

    @Test
    @DisplayName("NumericConstant requires non-null value")
    void numeric_constant_null_check() {
      assertThrows(NullPointerException.class, () -> new NumericConstant(null, false));
    }
  }

  @Nested
  @DisplayName("NumericConstant Tests")
  class NumericConstantTests {
    @Test
    @DisplayName("Integer parsing creates integer constant")
    void integer_parsing() {
      final var n = NumericConstant.parse("42");
      assertFalse(n.isFloating());
      assertEquals(42L, n.asLong());
      assertEquals(42, n.asInteger());
    }

    @Test
    @DisplayName("Floating point parsing creates floating constant")
    void floating_parsing() {
      final var n = NumericConstant.parse("3.14");
      assertTrue(n.isFloating());
      assertEquals(3.14, n.asDouble());
    }

    @Test
    @DisplayName("Scientific notation creates floating constant")
    void scientific_notation() {
      final var n = NumericConstant.parse("1.5e10");
      assertTrue(n.isFloating());
      assertEquals(1.5e10, n.asDouble());
    }

    @Test
    @DisplayName("Negative numbers parse correctly")
    void negative_numbers() {
      final var i = NumericConstant.parse("-42");
      assertEquals(-42, i.asInteger());

      final var f = NumericConstant.parse("-3.14");
      assertEquals(-3.14, f.asDouble());
    }

    @Test
    @DisplayName("Factory methods create correct types")
    void factory_methods() {
      final var i = NumericConstant.of(42);
      assertFalse(i.isFloating());

      final var l = NumericConstant.of(42L);
      assertFalse(l.isFloating());

      final var d = NumericConstant.of(3.14);
      assertTrue(d.isFloating());
    }

    @Test
    @DisplayName("isInteger and isFloating pattern recognition")
    void pattern_recognition() {
      assertTrue(NumericConstant.isInteger("42"));
      assertTrue(NumericConstant.isInteger("-123"));
      assertFalse(NumericConstant.isInteger("3.14"));
      assertFalse(NumericConstant.isInteger("not_a_number"));

      assertTrue(NumericConstant.isFloating("3.14"));
      assertTrue(NumericConstant.isFloating("1e10"));
      assertTrue(NumericConstant.isFloating("1.5e-10"));
      assertFalse(NumericConstant.isFloating("42"));
    }

    @Test
    @DisplayName("Invalid numeric literal throws exception")
    void invalid_literal() {
      assertThrows(IllegalArgumentException.class, () -> NumericConstant.parse("not_a_number"));
      assertThrows(IllegalArgumentException.class, () -> NumericConstant.parse(""));
    }

    @Test
    @DisplayName("Display and toString return number string")
    void display_format() {
      final var i = NumericConstant.of(42);
      assertEquals("42", i.display());
      assertEquals("42", i.toString());

      final var d = NumericConstant.of(3.14);
      assertEquals("3.14", d.display());
    }
  }

  @Nested
  @DisplayName("BooleanConstant Tests")
  class BooleanConstantTests {
    @Test
    @DisplayName("of() returns singleton instances")
    void of_returns_singletons() {
      assertSame(BooleanConstant.TRUE, BooleanConstant.of(true));
      assertSame(BooleanConstant.FALSE, BooleanConstant.of(false));
    }

    @Test
    @DisplayName("parse recognizes true and false")
    void parse() {
      assertSame(BooleanConstant.TRUE, BooleanConstant.parse("true"));
      assertSame(BooleanConstant.TRUE, BooleanConstant.parse("TRUE"));
      assertSame(BooleanConstant.TRUE, BooleanConstant.parse("True"));
      assertSame(BooleanConstant.FALSE, BooleanConstant.parse("false"));
      assertSame(BooleanConstant.FALSE, BooleanConstant.parse("FALSE"));
    }

    @Test
    @DisplayName("parse throws on invalid input")
    void parse_invalid() {
      assertThrows(IllegalArgumentException.class, () -> BooleanConstant.parse("yes"));
      assertThrows(IllegalArgumentException.class, () -> BooleanConstant.parse("1"));
      assertThrows(IllegalArgumentException.class, () -> BooleanConstant.parse(""));
    }

    @Test
    @DisplayName("isBoolean recognizes boolean strings")
    void is_boolean() {
      assertTrue(BooleanConstant.isBoolean("true"));
      assertTrue(BooleanConstant.isBoolean("false"));
      assertTrue(BooleanConstant.isBoolean("TRUE"));
      assertTrue(BooleanConstant.isBoolean("FALSE"));
      assertTrue(BooleanConstant.isBoolean("True"));
      assertFalse(BooleanConstant.isBoolean("yes"));
      assertFalse(BooleanConstant.isBoolean("1"));
      assertFalse(BooleanConstant.isBoolean(""));
      assertFalse(BooleanConstant.isBoolean(null));
    }

    @Test
    @DisplayName("display and toString return lowercase string")
    void display() {
      assertEquals("true", BooleanConstant.TRUE.display());
      assertEquals("true", BooleanConstant.TRUE.toString());
      assertEquals("false", BooleanConstant.FALSE.display());
      assertEquals("false", BooleanConstant.FALSE.toString());
    }

    @Test
    @DisplayName("value accessor returns boolean")
    void value_accessor() {
      assertTrue(BooleanConstant.TRUE.value());
      assertFalse(BooleanConstant.FALSE.value());
    }

    @Test
    @DisplayName("equality based on value")
    void equality() {
      assertEquals(BooleanConstant.of(true), BooleanConstant.of(true));
      assertEquals(BooleanConstant.of(false), BooleanConstant.of(false));
      assertNotEquals(BooleanConstant.of(true), BooleanConstant.of(false));
    }
  }

  @Nested
  @DisplayName("Structure Tests")
  class StructureTests {
    @Test
    @DisplayName("Empty structure creation")
    void empty_structure() {
      final var fs = new Structure();
      assertTrue(fs.isEmpty());
      assertEquals(0, fs.size());
      assertEquals("{}", fs.display());
    }

    @Test
    @DisplayName("Builder creates structure with features")
    void builder_pattern() {
      final var fs =
          Structure.builder().with("num", "sing").with("person", "3").with("case", "nom").build();

      assertFalse(fs.isEmpty());
      assertEquals(3, fs.size());
      assertTrue(fs.has("num"));
      assertTrue(fs.has("person"));
      assertTrue(fs.has("case"));
    }

    @Test
    @DisplayName("Get and set feature values")
    void get_and_set() {
      final var fs = new Structure();
      assertNull(fs.get("num"));

      fs.set("num", new StringConstant("sing"));
      assertNotNull(fs.get("num"));
      assertEquals("sing", ((StringConstant) fs.get("num")).value());
    }

    @Test
    @DisplayName("Builder supports different value types")
    void builder_value_types() {
      final var v = Variable.of("?x");
      final var nested = Structure.builder().with("inner", "value").build();

      final var fs =
          Structure.builder()
              .with("str", "text")
              .with("num", 42L)
              .with("var", v)
              .with("nested", nested)
              .build();

      assertEquals(4, fs.size());
      assertInstanceOf(StringConstant.class, fs.get("str"));
      assertInstanceOf(NumericConstant.class, fs.get("num"));
      assertInstanceOf(Variable.class, fs.get("var"));
      assertInstanceOf(Structure.class, fs.get("nested"));
    }

    @Test
    @DisplayName("Structure copy creates independent instance")
    void structure_copy() {
      final var nested = Structure.builder().with("inner", "original").build();
      final var fs = Structure.builder().with("outer", nested).build();

      final var copy = fs.copy();

      // Modify original nested
      ((Structure) fs.get("outer")).set("inner", new StringConstant("modified"));

      // Copy should be unchanged
      final var copyNested = (Structure) copy.get("outer");
      assertEquals("original", ((StringConstant) copyNested.get("inner")).value());
    }

    @Test
    @DisplayName("Keys returns all feature names")
    void keys_method() {
      final var fs = Structure.builder().with("a", "1").with("b", "2").with("c", "3").build();

      final var keys = fs.keys();
      assertEquals(3, keys.size());
      assertTrue(keys.contains("a"));
      assertTrue(keys.contains("b"));
      assertTrue(keys.contains("c"));
    }

    @Test
    @DisplayName("Keys returns unmodifiable set")
    void keys_returns_unmodifiable_set() {
      final var fs = Structure.builder().with("a", "1").with("b", "2").build();

      final var keys = fs.keys();

      // Attempting to modify should throw UnsupportedOperationException
      assertThrows(UnsupportedOperationException.class, () -> keys.remove("a"));
      assertThrows(UnsupportedOperationException.class, () -> keys.add("c"));
      assertThrows(UnsupportedOperationException.class, keys::clear);

      // Original structure should be unchanged
      assertEquals(2, fs.size());
      assertTrue(fs.has("a"));
      assertTrue(fs.has("b"));
    }

    @Test
    @DisplayName("Display formats feature structure correctly")
    void display_format() {
      final var fs = Structure.builder().with("num", "sing").with("case", "nom").build();

      final var display = fs.display();
      assertTrue(display.startsWith("{"));
      assertTrue(display.endsWith("}"));
      assertTrue(display.contains("num: sing"));
      assertTrue(display.contains("case: nom"));
    }

    @Test
    @DisplayName("Structure equality compares features")
    void structure_equality() {
      final var fs1 = Structure.builder().with("num", "sing").build();
      final var fs2 = Structure.builder().with("num", "sing").build();
      final var fs3 = Structure.builder().with("num", "plur").build();

      assertEquals(fs1, fs2);
      assertNotEquals(fs1, fs3);
    }
  }

  @Nested
  @DisplayName("FeatureUtils Tests")
  class FeatureUtilsTests {
    @Test
    @DisplayName("fromMap converts string map to Structure")
    void from_map_conversion() {
      final var map = new HashMap<String, String>();
      map.put("num", "sing");
      map.put("person", "third"); // non-numeric string
      map.put("var", "?x");

      final var fs = Utilities.fromMap(map);

      assertEquals(3, fs.size());
      assertInstanceOf(StringConstant.class, fs.get("num"));
      assertInstanceOf(StringConstant.class, fs.get("person"));
      assertInstanceOf(Variable.class, fs.get("var"));
    }

    @Test
    @DisplayName("fromMap recognizes numeric values")
    void from_map_numeric_recognition() {
      final var map = new HashMap<String, String>();
      map.put("int", "42");
      map.put("float", "3.14");

      final var fs = Utilities.fromMap(map);

      assertInstanceOf(NumericConstant.class, fs.get("int"));
      assertInstanceOf(NumericConstant.class, fs.get("float"));
    }

    @Test
    @DisplayName("fromMap recognizes boolean values")
    void from_map_boolean_recognition() {
      final var map = new HashMap<String, String>();
      map.put("t", "true");
      map.put("f", "false");
      map.put("T", "TRUE");

      final var fs = Utilities.fromMap(map);

      assertInstanceOf(BooleanConstant.class, fs.get("t"));
      assertInstanceOf(BooleanConstant.class, fs.get("f"));
      assertInstanceOf(BooleanConstant.class, fs.get("T"));
      assertEquals(BooleanConstant.TRUE, fs.get("t"));
      assertEquals(BooleanConstant.FALSE, fs.get("f"));
    }

    @Test
    @DisplayName("fromMap treats ? prefix as variable")
    void from_map_variable_prefix() {
      final var map = new HashMap<String, String>();
      map.put("v", "?x");

      final var fs = Utilities.fromMap(map);
      final var v = (Variable) fs.get("v");
      assertEquals("x", v.name());
    }

    @Test
    @DisplayName("valueToString converts values to strings")
    void value_to_string_conversion() {
      assertEquals("test", Utilities.valueToString(new StringConstant("test")));
      assertEquals("42", Utilities.valueToString(NumericConstant.of(42)));
      assertEquals("true", Utilities.valueToString(BooleanConstant.TRUE));
      assertEquals("false", Utilities.valueToString(BooleanConstant.FALSE));
      assertEquals("?x", Utilities.valueToString(Variable.of("x")));
      assertNull(Utilities.valueToString(null));
    }

    @Test
    @DisplayName("valueToString handles nested structures")
    void value_to_string_nested_structure() {
      final var fs = Structure.builder().with("a", "b").build();
      final var str = Utilities.valueToString(fs);
      assertNotNull(str);
      assertTrue(str.contains("a"));
    }

    @Test
    @DisplayName("fromMap skips null values without NPE")
    void from_map_skips_null_values() {
      final var map = new HashMap<String, String>();
      map.put("valid", "value");
      map.put("nullKey", null);

      // Should not throw NPE
      final var fs = Utilities.fromMap(map);

      assertEquals(1, fs.size());
      assertTrue(fs.has("valid"));
      assertFalse(fs.has("nullKey"));
    }
  }

  @Nested
  @DisplayName("Null Handling Tests")
  class NullHandlingTests {
    @Test
    @DisplayName("Unify returns empty when first value is null")
    void unify_with_null_first() {
      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(null, new StringConstant("test"), bindings);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Unify returns empty when second value is null")
    void unify_with_null_second() {
      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(new StringConstant("test"), null, bindings);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Unify returns empty when both values are null")
    void unify_with_both_null() {
      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(null, null, bindings);
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("Atomic Value Unification")
  class AtomicValueTests {
    @Test
    @DisplayName("Identical string constants unify")
    void identical_strings_unify() {
      final var a1 = new StringConstant("sing");
      final var a2 = new StringConstant("sing");

      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(a1, a2, bindings);

      assertTrue(result.isPresent());
      assertEquals("sing", ((StringConstant) result.get()).value());
    }

    @Test
    @DisplayName("Different string constants fail to unify")
    void different_strings_dont_unify() {
      final var a1 = new StringConstant("sing");
      final var a2 = new StringConstant("plur");

      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(a1, a2, bindings);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Identical numeric constants unify")
    void identical_numbers_unify() {
      final var n1 = NumericConstant.of(42);
      final var n2 = NumericConstant.of(42);

      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(n1, n2, bindings);

      assertTrue(result.isPresent());
      assertEquals(42, ((NumericConstant) result.get()).asInteger());
    }

    @Test
    @DisplayName("Different numeric constants fail to unify")
    void different_numbers_dont_unify() {
      final var n1 = NumericConstant.of(42);
      final var n2 = NumericConstant.of(43);

      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(n1, n2, bindings);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("String and numeric with same display fail to unify")
    void string_and_number_distinct() {
      final var s = new StringConstant("42");
      final var n = NumericConstant.of(42);

      // Both display as "42" but should not unify (different types)
      assertEquals("42", s.display());
      assertEquals("42", n.display());

      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(s, n, bindings);

      // They fail to unify because they're different types
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Integer and long with same value unify")
    void integer_and_long_unify() {
      final var i = NumericConstant.of(42);
      final var l = NumericConstant.of(42L);

      final var result = Unifier.unify(i, l);

      assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Integer and double with same value unify")
    void integer_and_double_unify() {
      final var i = NumericConstant.of(5);
      final var d = NumericConstant.of(5.0);

      final var result = Unifier.unify(i, d);

      assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Different numeric values do not unify")
    void different_numeric_values() {
      final var n1 = NumericConstant.of(5);
      final var n2 = NumericConstant.of(6);

      final var result = Unifier.unify(n1, n2);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Floating point values with same value unify")
    void floating_point_unify() {
      final var d1 = NumericConstant.of(3.14);
      final var d2 = NumericConstant.of(3.14);

      final var result = Unifier.unify(d1, d2);

      assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Parsed numeric literals unify correctly")
    void parsed_numeric_literals() {
      final var n1 = NumericConstant.parse("42");
      final var n2 = NumericConstant.of(42);

      final var result = Unifier.unify(n1, n2);

      assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Empty string constants unify")
    void empty_strings_unify() {
      final var s1 = new StringConstant("");
      final var s2 = new StringConstant("");

      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(s1, s2, bindings);

      assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Identical boolean constants unify")
    void identical_booleans_unify() {
      final var result1 = Unifier.unify(BooleanConstant.TRUE, BooleanConstant.TRUE);
      assertTrue(result1.isPresent());
      assertEquals(BooleanConstant.TRUE, result1.get());

      final var result2 = Unifier.unify(BooleanConstant.FALSE, BooleanConstant.FALSE);
      assertTrue(result2.isPresent());
      assertEquals(BooleanConstant.FALSE, result2.get());
    }

    @Test
    @DisplayName("Different boolean constants fail to unify")
    void different_booleans_dont_unify() {
      final var result = Unifier.unify(BooleanConstant.TRUE, BooleanConstant.FALSE);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Boolean and string with same display fail to unify")
    void boolean_and_string_distinct() {
      final var b = BooleanConstant.TRUE;
      final var s = new StringConstant("true");

      // Both display as "true" but should not unify (different types)
      assertEquals("true", b.display());
      assertEquals("true", s.display());

      final var result = Unifier.unify(b, s);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Boolean and numeric fail to unify")
    void boolean_and_numeric_distinct() {
      // Some languages treat true as 1, but we don't
      final var result = Unifier.unify(BooleanConstant.TRUE, NumericConstant.of(1));
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("Variable Unification")
  class VariableTests {
    @Test
    @DisplayName("Variable unifies with atomic value and gets bound")
    void variable_binds_to_atomic() {
      final var v = Variable.of("?x");
      final var a = new StringConstant("sing");

      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(v, a, bindings);

      assertTrue(result.isPresent());
      assertEquals(a, result.get());
      assertEquals(a, bindings.get("x"));
    }

    @Test
    @DisplayName("Variable unifies with boolean constant and gets bound")
    void variable_binds_to_boolean() {
      final var v = Variable.of("?flag");
      final var b = BooleanConstant.TRUE;

      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(v, b, bindings);

      assertTrue(result.isPresent());
      assertEquals(b, result.get());
      assertEquals(b, bindings.get("flag"));
    }

    @Test
    @DisplayName("Two different variables unify")
    void different_variables_unify() {
      final var v1 = Variable.of("?x");
      final var v2 = Variable.of("?y");

      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(v1, v2, bindings);

      assertTrue(result.isPresent());
      assertInstanceOf(Variable.class, result.get());
      // v1 gets bound to v2
      assertEquals(v2, bindings.get("x"));
    }

    @Test
    @DisplayName("Same variable unifies with itself")
    void same_variable_unifies_with_itself() {
      final var v = Variable.of("?x");

      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(v, v, bindings);

      assertTrue(result.isPresent());
      assertEquals(v, result.get());
    }

    @Test
    @DisplayName("Variable dereferencing follows bindings")
    void variable_dereferencing() {
      final var v1 = Variable.of("?x");
      final var v2 = Variable.of("?y");
      final var a = new StringConstant("value");

      final var bindings = new HashMap<String, Value>();
      bindings.put("x", v2);
      bindings.put("y", a);

      final var result = Unifier.unify(v1, a, bindings);
      assertTrue(result.isPresent());
      assertEquals(a, result.get());
    }

    @Test
    @DisplayName("Occurs check prevents cyclic binding")
    void occurs_check_prevents_infinite_loop() {
      final var v = Variable.of("?x");
      final var fs = Structure.builder().with("feature", v).build();

      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(v, fs, bindings);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Occurs check with nested feature structures")
    void occurs_check_nested() {
      final var v = Variable.of("?x");
      final var inner = Structure.builder().with("inner", v).build();
      final var outer = Structure.builder().with("outer", inner).build();

      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(v, outer, bindings);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Variable binds to numeric constant")
    void variable_binds_to_numeric() {
      final var v = Variable.of("?n");
      final var n = NumericConstant.of(42);

      final var bindings = new HashMap<String, Value>();
      final var result = Unifier.unify(v, n, bindings);

      assertTrue(result.isPresent());
      assertEquals(n, result.get());
      assertEquals(n, bindings.get("n"));
    }
  }

  @Nested
  @DisplayName("Feature Structure Unification")
  class FeatureStructureTests {
    @Test
    @DisplayName("Compatible feature structures with disjoint features unify")
    void disjoint_features_unify() {
      final var fs1 = Structure.builder().with("num", "sing").with("person", "3").build();
      final var fs2 = Structure.builder().with("case", "nom").build();

      final var result = Unifier.unify(fs1, fs2);

      assertTrue(result.isPresent());
      final var unified = result.get();
      assertEquals("sing", ((StringConstant) unified.get("num")).value());
      assertEquals("3", ((StringConstant) unified.get("person")).value());
      assertEquals("nom", ((StringConstant) unified.get("case")).value());
    }

    @Test
    @DisplayName("Compatible feature structures with overlapping features unify")
    void overlapping_features_unify() {
      final var fs1 = Structure.builder().with("num", "sing").with("person", "3").build();
      final var fs2 = Structure.builder().with("num", "sing").build();

      final var result = Unifier.unify(fs1, fs2);

      assertTrue(result.isPresent());
      final var unified = result.get();
      assertEquals("sing", ((StringConstant) unified.get("num")).value());
      assertEquals("3", ((StringConstant) unified.get("person")).value());
    }

    @Test
    @DisplayName("Conflicting feature values fail to unify")
    void conflicting_values_fail() {
      final var fs1 = Structure.builder().with("num", "sing").build();
      final var fs2 = Structure.builder().with("num", "plur").build();

      final var result = Unifier.unify(fs1, fs2);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Feature structures with variables unify and bind variables")
    void feature_structures_with_variables() {
      final var numVar = Variable.of("?n");
      final var fs1 = Structure.builder().with("num", numVar).build();
      final var fs2 = Structure.builder().with("num", "sing").build();

      final var result = Unifier.unify(fs1, fs2);

      assertTrue(result.isPresent());
      final var unified = result.get();
      assertEquals("sing", ((StringConstant) unified.get("num")).value());
    }

    @Test
    @DisplayName("Empty feature structures unify")
    void empty_feature_structures_unify() {
      final var fs1 = Structure.builder().build();
      final var fs2 = Structure.builder().build();

      final var result = Unifier.unify(fs1, fs2);

      assertTrue(result.isPresent());
      assertTrue(result.get().isEmpty());
    }

    @Test
    @DisplayName("Nested feature structures unify")
    void nested_feature_structures_unify() {
      final var inner1 = Structure.builder().with("case", "nom").with("gender", "masc").build();
      final var fs1 = Structure.builder().with("num", "sing").with("agr", inner1).build();

      final var inner2 = Structure.builder().with("case", "nom").build();
      final var fs2 = Structure.builder().with("agr", inner2).build();

      final var result = Unifier.unify(fs1, fs2);

      assertTrue(result.isPresent());
      final var unified = result.get();
      assertEquals("sing", ((StringConstant) unified.get("num")).value());

      final var unifiedAgr = (Structure) unified.get("agr");
      assertEquals("nom", ((StringConstant) unifiedAgr.get("case")).value());
      assertEquals("masc", ((StringConstant) unifiedAgr.get("gender")).value());
    }

    @Test
    @DisplayName("Nested feature structures with conflicting values fail")
    void nested_conflict_fails() {
      final var inner1 = Structure.builder().with("case", "nom").build();
      final var fs1 = Structure.builder().with("agr", inner1).build();

      final var inner2 = Structure.builder().with("case", "acc").build();
      final var fs2 = Structure.builder().with("agr", inner2).build();

      final var result = Unifier.unify(fs1, fs2);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Unification with numeric features")
    void numeric_feature_unification() {
      final var fs1 = Structure.builder().with("count", 5L).build();
      final var fs2 = Structure.builder().with("count", 5L).build();

      final var result = Unifier.unify(fs1, fs2);

      assertTrue(result.isPresent());
      final var unified = result.get();
      assertEquals(5L, ((NumericConstant) unified.get("count")).asLong());
    }
  }

  @Nested
  @DisplayName("Complex Unification Scenarios")
  class ComplexScenarios {
    @Test
    @DisplayName("Shared variable binding across multiple features")
    void shared_variable_binding() {
      final var v = Variable.of("?x");
      final var fs1 = Structure.builder().with("subj_num", v).with("verb_num", v).build();
      final var fs2 = Structure.builder().with("subj_num", "sing").build();

      final var result = Unifier.unify(fs1, fs2);

      assertTrue(result.isPresent());
      final var unified = result.get();
      assertEquals("sing", ((StringConstant) unified.get("subj_num")).value());
      assertEquals("sing", ((StringConstant) unified.get("verb_num")).value());
    }

    @Test
    @DisplayName("Transitive variable bindings")
    void transitive_bindings() {
      final var v1 = Variable.of("?x");
      final var v2 = Variable.of("?y");
      final var v3 = Variable.of("?z");

      final var bindings = new HashMap<String, Value>();

      // x = y
      Unifier.unify(v1, v2, bindings);
      // y = z
      Unifier.unify(v2, v3, bindings);
      // z = "value"
      final var result = Unifier.unify(v3, new StringConstant("value"), bindings);

      assertTrue(result.isPresent());
      assertEquals("value", ((StringConstant) result.get()).value());
    }

    @Test
    @DisplayName("Multiple variables in nested structures")
    void multiple_variables_nested() {
      final var num = Variable.of("?n");
      final var person = Variable.of("?p");

      final var fs1 =
          Structure.builder()
              .with("agr", Structure.builder().with("num", num).with("person", person).build())
              .build();

      final var fs2 =
          Structure.builder().with("agr", Structure.builder().with("num", "plur").build()).build();

      final var result = Unifier.unify(fs1, fs2);

      assertTrue(result.isPresent());
      final var unified = result.get();
      final var agr = (Structure) unified.get("agr");
      assertEquals("plur", ((StringConstant) agr.get("num")).value());
    }

    @Test
    @DisplayName("Substitution replaces all variable occurrences")
    void substitution_replacement() {
      final var v = Variable.of("?x");
      final var fs1 =
          Structure.builder()
              .with("a", v)
              .with("b", v)
              .with("nested", Structure.builder().with("c", v).build())
              .build();

      final var fs2 = Structure.builder().with("a", "value").build();

      final var result = Unifier.unify(fs1, fs2);

      assertTrue(result.isPresent());
      final var unified = result.get();

      // All occurrences of ?x should be bound to "value"
      assertEquals("value", ((StringConstant) unified.get("a")).value());
      assertEquals("value", ((StringConstant) unified.get("b")).value());
      final var nested = (Structure) unified.get("nested");
      assertEquals("value", ((StringConstant) nested.get("c")).value());
    }

    @Test
    @DisplayName("Unification with mixed types in structure")
    void mixed_type_structure() {
      final var v = Variable.of("?x");
      final var fs1 =
          Structure.builder()
              .with("str", "text")
              .with("num", 42L)
              .with("var", v)
              .with("nested", Structure.builder().with("inner", "value").build())
              .build();

      final var fs2 = Structure.builder().with("var", "bound").with("extra", "new").build();

      final var result = Unifier.unify(fs1, fs2);

      assertTrue(result.isPresent());
      final var unified = result.get();
      assertEquals(5, unified.size());
      assertInstanceOf(StringConstant.class, unified.get("str"));
      assertInstanceOf(NumericConstant.class, unified.get("num"));
      assertInstanceOf(StringConstant.class, unified.get("var"));
      assertInstanceOf(Structure.class, unified.get("nested"));
      assertInstanceOf(StringConstant.class, unified.get("extra"));
    }
  }
}
