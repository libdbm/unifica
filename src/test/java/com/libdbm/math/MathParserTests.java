package com.libdbm.math;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.math.MathExpr.*;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.parser.ChartParser;
import com.libdbm.ugf.parser.ParseTree;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Tests mathematical expression parsing and evaluation */
public final class MathParserTests {

  private static Grammar grammar;
  private static ChartParser parser;

  @BeforeAll
  static void setup() throws Exception {
    final var resource = MathParserTests.class.getClassLoader().getResource("math.ug");
    if (resource == null) {
      throw new RuntimeException("Unable to find Math grammar");
    }
    try (final var stream = resource.openStream()) {
      final var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      final var grammar = UnificationGrammarParserFactory.parse(content);
      parser = new ChartParser(grammar);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to load Math grammar", e);
    }
  }

  @Test
  void parses_simple_number() {
    final var expr = parse("42");
    assertInstanceOf(Constant.class, expr);
    assertEquals(42.0, ((Constant) expr).value());
    assertEquals(42.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_addition() {
    final var expr = parse("2 + 3");
    assertInstanceOf(BinaryOp.class, expr);
    assertEquals(5.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_subtraction() {
    final var expr = parse("10 - 4");
    assertEquals(6.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_subtraction_with_negative_numbers() {
    final var expr = parse("10 - -4");
    assertEquals(14.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_multiplication() {
    final var expr = parse("3 * 7");
    assertEquals(21.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_division() {
    final var expr = parse("15 / 3");
    assertEquals(5.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_exponentiation() {
    final var expr = parse("2 ^ 8");
    assertEquals(256.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_operator_precedence() {
    // 2 + 3 * 4 = 2 + 12 = 14
    final var expr = parse("2 + 3 * 4");
    assertEquals(14.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_parentheses() {
    // (2 + 3) * 4 = 5 * 4 = 20
    final var expr = parse("(2 + 3) * 4");
    assertEquals(20.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_nested_parentheses() {
    // ((1 + 2) * (3 + 4)) = (3 * 7) = 21
    final var expr = parse("((1 + 2) * (3 + 4))");
    assertEquals(21.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_unary_minus() {
    final var expr = parse("-5");
    assertEquals(-5.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_unary_plus() {
    final var expr = parse("+5");
    assertEquals(5.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_variables() {
    final var expr = parse("x + y");
    final var result = MathEvaluator.eval(expr, Map.of("x", 10.0, "y", 5.0));
    assertEquals(15.0, result);
  }

  @Test
  void parses_sqrt_function() {
    final var expr = parse("sqrt(16)");
    assertEquals(4.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_abs_function() {
    final var expr = parse("abs(-42)");
    assertEquals(42.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_pow_function() {
    final var expr = parse("pow(2,10)");
    assertEquals(1024.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_max_function() {
    final var expr = parse("max(5,12)");
    assertEquals(12.0, MathEvaluator.eval(expr));
  }

  @Test
  void parses_complex_expression() {
    // sqrt(16) + 2 * 3 = 4 + 6 = 10
    final var expr = parse("sqrt(16)+2*3");
    assertEquals(10.0, MathEvaluator.eval(expr));
  }

  @Test
  void test_various_expressions() {
    System.out.println("\n" + "=".repeat(70));
    System.out.println("MATHEMATICAL EXPRESSION PARSING & EVALUATION");
    System.out.println("=".repeat(70));
    final String[] examples = {
      "2 + 3",
      "10 - 4 * 2",
      "( 2 + 3 ) * 4",
      "2 ^ 8",
      "sqrt ( 25 )",
      "max ( 10 , 20 )",
      "3 + 4 * 5 - 2",
      "- 5 + 10",
      "abs ( - 42 )"
    };

    for (final var input : examples) {
      System.out.println("\nInput: " + input);

      try {
        final var expr = parse(input);
        final var pretty = MathEvaluator.toString(expr);
        final var result = MathEvaluator.eval(expr);

        System.out.println("  Parsed: " + pretty);
        System.out.println("  Result: " + result);
      } catch (Exception e) {
        System.out.println("  Error: " + e.getMessage());
      }
    }

    System.out.println("\n" + "=".repeat(70));
  }

  @Test
  void test_variable_binding() {
    System.out.println("\n" + "=".repeat(70));
    System.out.println("VARIABLE BINDING TESTS");
    System.out.println("=".repeat(70));

    final var expression = "x * x + y * y";
    System.out.println("\nExpression: " + expression);

    final var expr = parse(expression);

    final double[][] tests = {
      {3, 4, 25}, // 3² + 4² = 9 + 16 = 25
      {5, 12, 169}, // 5² + 12² = 25 + 144 = 169
      {0, 0, 0} // 0² + 0² = 0
    };

    for (final var test : tests) {
      final double x = test[0];
      final double y = test[1];
      final double expected = test[2];

      final var vars = Map.of("x", x, "y", y);
      final var result = MathEvaluator.eval(expr, vars);

      System.out.println(
          "  x="
              + x
              + ", y="
              + y
              + " → "
              + result
              + " (expected: "
              + expected
              + ") "
              + (Math.abs(result - expected) < 0.001 ? "✓" : "✗"));
    }

    System.out.println("\n" + "=".repeat(70));
  }

  @Test
  void parses_without_spaces() {
    // Test that expressions parse correctly without spaces
    final var expr1 = parse("5-5");
    assertEquals(0.0, MathEvaluator.eval(expr1));

    final var expr2 = parse("5--5");
    assertEquals(10.0, MathEvaluator.eval(expr2));

    final var expr3 = parse("5+-5");
    assertEquals(0.0, MathEvaluator.eval(expr3));

    final var expr4 = parse("10*-2");
    assertEquals(-20.0, MathEvaluator.eval(expr4));
  }

  private MathExpr parse(final String input) {
    final Optional<ParseTree> result = parser.parse(input).toOptional();
    assertTrue(result.isPresent(), "Should parse: " + input);
    return MathBuilder.build(result.get());
  }

  private String tokenize(final String input) {
    // Simple tokenization: add spaces around operators
    String result = input;
    result = result.replaceAll("\\(", " ( ");
    result = result.replaceAll("\\)", " ) ");
    result = result.replaceAll("\\+", " + ");
    result = result.replaceAll("-", " - ");
    result = result.replaceAll("\\*", " * ");
    result = result.replaceAll("/", " / ");
    result = result.replaceAll("\\^", " ^ ");
    result = result.replaceAll(",", " , ");
    result = result.replaceAll("\\s+", " ").trim();
    return result;
  }
}
