package com.libdbm.ugf.constraints;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.constraints.Expression.Call;
import com.libdbm.ugf.constraints.Expression.Or;
import com.libdbm.ugf.features.*;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class PredicatesTests {

  private static final Predicates LEXICAL = Predicates.builder().lexical().build();
  private static final Predicates STANDARD = Predicates.standard();

  private static Call call(final String name, final String... args) {
    return new Call(
        name, List.of(args).stream().map(arg -> (Value) new StringConstant(arg)).toList());
  }

  private static Environment lexing(final int position, final String... states) {
    return Environment.of(LEXICAL).lexical(List.of(states), position);
  }

  private static boolean test(
      final Environment environment, final String name, final Value... args) {
    return environment.test(new Call(name, List.of(args)));
  }

  private static StringConstant string(final String text) {
    return new StringConstant(text);
  }

  @Test
  void testLexicalPredicateNames() {
    assertEquals(
        Set.of("in_state", "state_depth", "state_contains", "at_char_start", "at_char_position"),
        LEXICAL.names(Predicates.Phase.LEXICAL));
    assertTrue(LEXICAL.names(Predicates.Phase.SYNTACTIC).isEmpty());
  }

  @Test
  void testInState() {
    final var environment = lexing(0, "DEFAULT", "IN");

    assertTrue(environment.test(call("in_state", "IN")));
    assertTrue(environment.test(call("in_state", "X", "IN")));
    assertFalse(environment.test(call("in_state", "DEFAULT")));
  }

  @Test
  void testStateDepthAndContains() {
    final var environment = lexing(0, "DEFAULT", "A", "B");

    assertTrue(environment.test(call("state_depth", "3")));
    assertFalse(environment.test(call("state_depth", "2")));
    assertTrue(environment.test(call("state_contains", "A")));
    assertFalse(environment.test(call("state_contains", "C")));
  }

  @Test
  void testCharacterPosition() {
    assertTrue(lexing(0, "DEFAULT").test(call("at_char_start")));
    assertFalse(lexing(4, "DEFAULT").test(call("at_char_start")));
    assertTrue(lexing(4, "DEFAULT").test(call("at_char_position", "4")));
  }

  @Test
  void testArityLookup() {
    final var entry = LEXICAL.entry("at_char_position");

    assertEquals(1, entry.minimum());
    assertEquals(1, entry.maximum());
    assertEquals(Predicates.Phase.LEXICAL, entry.phase());
    assertTrue(entry.accepts(1));
    assertFalse(entry.accepts(2));
    assertTrue(LEXICAL.entry("in_state").accepts(3));
    assertNull(LEXICAL.entry("missing"));
  }

  @Test
  void testRegistryImmutableAfterBuild() {
    final var builder = Predicates.builder();
    final var built = builder.build();

    builder.add("later", 0, 0, Predicates.Phase.SYNTACTIC, (environment, args) -> true);

    assertNull(built.entry("later"));
    assertNotNull(builder.build().entry("later"));
  }

  @Test
  void testUnknownCallIsProgrammingError() {
    assertThrows(IllegalStateException.class, () -> Environment.of(LEXICAL).test(call("missing")));
  }

  @Test
  void testCustomPredicateSeesResolvedBindings() {
    final var predicates =
        Predicates.builder()
            .add(
                "is_dog",
                1,
                1,
                Predicates.Phase.SYNTACTIC,
                (environment, args) ->
                    new StringConstant("dog").equals(environment.resolve(args.getFirst())))
            .build();
    final var environment =
        Environment.of(predicates)
            .with("x", new StringConstant("dog"))
            .with("np", Binding.of("the dog", Structure.builder().with("head", "dog").build()));

    assertTrue(environment.test(new Call("is_dog", List.of(new Variable("x")))));
    assertTrue(
        environment.test(new Call("is_dog", List.of(new FeaturePath(List.of("np", "head"))))));
    assertFalse(environment.test(new Call("is_dog", List.of(new StringConstant("cat")))));
  }

  @Test
  void testEnvironmentIsImmutable() {
    final var base = Environment.of(LEXICAL);

    base.with("x", new StringConstant("v"));

    assertNull(base.get("x"));
  }

  @Test
  void testEvaluatorWithEnvironment() {
    final var environment = lexing(0, "DEFAULT");
    final var expression = new Or(List.of(call("in_state", "IN"), call("at_char_start")));

    assertTrue(Evaluator.truth(expression, environment));
    assertEquals(
        Expression.Literal.TRUE,
        Evaluator.residual(expression, environment, LEXICAL.names(Predicates.Phase.LEXICAL)));
  }

  @Test
  void testStandardNames() {
    final var names = new TreeSet<>(STANDARD.names(Predicates.Phase.SYNTACTIC));

    assertEquals(
        new TreeSet<>(
            Set.of(
                "agree",
                "unify",
                "has_feature",
                "get_feature",
                "feature_eq",
                "equals",
                "not_equals",
                "is_string",
                "is_number",
                "is_structure",
                "is_bound",
                "starts_with",
                "ends_with",
                "contains",
                "matches",
                "not_empty",
                "is_upper",
                "is_lower",
                "is_capitalized",
                "lt",
                "le",
                "gt",
                "ge")),
        names);
    assertEquals(
        Set.of("at_start", "at_position", "at_end", "before"),
        STANDARD.names(Predicates.Phase.POSITIONAL));
    assertEquals(LEXICAL.names(Predicates.Phase.LEXICAL), STANDARD.names(Predicates.Phase.LEXICAL));
  }

  /** S-C9: literals, labelled terminals and labelled nonterminals behave alike. */
  @Test
  void testStringBuiltinsOnLabels() {
    final var environment =
        Environment.of(STANDARD)
            .with("w", Binding.of("abc"))
            .with("p", Binding.of("ab cd", Structure.builder().with("num", "sg").build()));

    assertTrue(test(environment, "starts_with", string("abc"), string("ab")));
    assertTrue(test(environment, "starts_with", new Variable("w"), string("ab")));
    assertTrue(test(environment, "starts_with", new Variable("p"), string("ab")));
    assertTrue(test(environment, "ends_with", new Variable("p"), string("cd")));
    assertTrue(test(environment, "contains", new Variable("p"), string("b c")));
    assertTrue(test(environment, "matches", new Variable("w"), string("[a-c]+")));
    assertFalse(test(environment, "matches", new Variable("p"), string("ab")));
    assertTrue(test(environment, "not_empty", new Variable("w")));
    assertTrue(test(environment, "is_lower", new Variable("w")));
    assertFalse(test(environment, "is_capitalized", new Variable("w")));
  }

  @Test
  void testFeatureNamesResolveAsPaths() {
    final var environment = Environment.of(STANDARD).with("w", Binding.of("abc"));

    // A bare label in a grammar is parsed as a one-part feature path.
    assertTrue(test(environment, "starts_with", new FeaturePath(List.of("w")), string("ab")));
  }

  @Test
  void testInvalidRegexIsFalse() {
    assertFalse(test(Environment.of(STANDARD), "matches", string("abc"), string("[unclosed")));
  }

  @Test
  void testFeatureBuiltinsAcceptBinding() {
    final var features = Structure.builder().with("num", "sg").build();
    final var environment =
        Environment.of(STANDARD)
            .with("np", Binding.of("the dog", features))
            .with("vp", Binding.of("runs", features));

    assertTrue(test(environment, "has_feature", new Variable("np"), string("num")));
    assertTrue(test(environment, "get_feature", new Variable("np"), string("num"), string("sg")));
    assertTrue(
        test(
            environment,
            "feature_eq",
            new Variable("np"),
            string("num"),
            new Variable("vp"),
            string("num")));
    assertTrue(test(environment, "agree", new Variable("np"), new Variable("vp")));
  }

  @Test
  void testNumericBuiltinsAreExact() {
    final var environment = Environment.of(STANDARD);
    final var big = NumericConstant.of(9007199254740992L);
    final var bigger = NumericConstant.of(9007199254740993L);

    assertTrue(test(environment, "lt", big, bigger));
    assertFalse(test(environment, "ge", big, bigger));
    assertTrue(test(environment, "equals", NumericConstant.of(1), NumericConstant.of(1.0)));
  }

  @Test
  void testUnboundArguments() {
    final var environment = Environment.of(STANDARD);

    assertFalse(test(environment, "equals", new Variable("missing"), string("a")));
    assertTrue(test(environment, "not_equals", new Variable("missing"), string("a")));
    assertFalse(test(environment, "is_bound", new Variable("missing")));
    assertFalse(test(environment, "starts_with", new Variable("missing"), string("a")));
  }

  @Test
  void testTokenPosition() {
    final var environment =
        Environment.of(STANDARD).with(Environment.POSITION, NumericConstant.of(0));

    assertTrue(test(environment, "at_start"));
    assertTrue(test(environment, "at_position", string("0")));
    assertFalse(test(environment, "at_position", string("1")));
  }
}
