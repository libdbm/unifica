package com.libdbm.math;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.generator.GrammarGenerator;
import com.libdbm.ugf.generator.LiteralTerminalGenerator;
import com.libdbm.ugf.generator.RegexTerminalGenerator;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.parser.ChartParser;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for generating mathematical expressions using {@link GrammarGenerator}. */
final class MathGeneratorTest {

  private Grammar grammar;
  private GrammarGenerator generator;
  private ChartParser parser;

  @BeforeEach
  void setup() throws Exception {
    // Load math grammar
    final var path = Path.of(MathGeneratorTest.class.getResource("/math.ug").getPath());
    grammar = UnificationGrammarParserFactory.parse(path);

    // Create terminal generator for math (numbers and variables)
    final var random = new Random(42); // Fixed seed for reproducibility
    final var terminalGen =
        RegexTerminalGenerator.builder()
            .register("[0-9]+", features -> String.valueOf(random.nextInt(100)))
            .register(
                "[+-]?",
                features -> random.nextDouble() < 0.3 ? "" : (random.nextBoolean() ? "+" : "-"))
            .register(
                "[a-zA-Z_][a-zA-Z0-9_]*",
                features -> {
                  final String[] vars = {"x", "y", "z", "a", "b"};
                  return vars[random.nextInt(vars.length)];
                })
            .fallback(new LiteralTerminalGenerator())
            .build();

    generator = new GrammarGenerator(grammar, terminalGen, random, 15);
    parser = new ChartParser(grammar);
  }

  @Test
  void test_generate_single_expression() {
    final var result = generator.generateOne("expr", new Structure());

    assertTrue(result.isPresent(), "Should generate an expression");
    assertFalse(result.get().isEmpty(), "Expression should not be empty");

    System.out.println("Generated expression: " + result.get());
  }

  @Test
  void test_generate_multiple_expressions() {
    final var expressions = generator.generate("expr", new Structure(), 10);

    assertEquals(10, expressions.size(), "Should generate 10 expressions");

    for (final var expr : expressions) {
      assertFalse(expr.isEmpty(), "Expression should not be empty");
      System.out.println("Generated: " + expr);
    }
  }

  @Test
  void test_generated_expressions_are_parseable() {
    // Use shallower depth to avoid generating overly complex expressions
    final var shallowGen =
        new GrammarGenerator(
            grammar,
            RegexTerminalGenerator.builder()
                .register("[0-9]+", f -> "42")
                .register("[+-]?", f -> "+")
                .register("[a-zA-Z_][a-zA-Z0-9_]*", f -> "x")
                .fallback(new LiteralTerminalGenerator())
                .build(),
            new Random(42),
            3); // Shallow depth

    final var expressions = shallowGen.generate("expr", new Structure(), 5);

    for (final var expr : expressions) {
      final var parseResult = parser.parse(expr).toOptional();
      assertTrue(parseResult.isPresent(), "Generated expression should be parseable: " + expr);
    }
  }

  @Test
  void test_generate_atoms() {
    final var atoms = generator.generate("atom", new Structure(), 10);

    assertEquals(10, atoms.size(), "Should generate 10 atoms");

    for (final var atom : atoms) {
      System.out.println("Generated atom: " + atom);
      // Atoms should be simple: numbers, variables, or parenthesized expressions
      assertFalse(atom.isEmpty());
    }
  }

  @Test
  void test_generate_numbers() {
    // Use simple generator with all patterns registered
    final var simpleGen =
        new GrammarGenerator(
            grammar,
            RegexTerminalGenerator.builder()
                .register("[0-9]+", f -> "42")
                .register("[+-]?", f -> "")
                .register("[a-zA-Z_][a-zA-Z0-9_]*", f -> "x")
                .fallback(new LiteralTerminalGenerator())
                .build(),
            new Random(42),
            5);

    final var numbers = simpleGen.generate("number", new Structure(), 5);

    assertEquals(5, numbers.size(), "Should generate 5 numbers");

    for (final var number : numbers) {
      System.out.println("Generated number: " + number);
      // Generator produces numbers with spaces, e.g., "42" or "42 . 42" or "42 . 42 e 42"
      assertFalse(number.isEmpty(), "Number should not be empty");
    }
  }

  @Test
  void test_generate_variables() {
    final var variables = generator.generate("variable", new Structure(), 5);

    assertEquals(5, variables.size(), "Should generate 5 variables");

    for (final var variable : variables) {
      System.out.println("Generated variable: " + variable);
      // Should be a valid identifier
      assertTrue(
          variable.matches("[a-zA-Z_][a-zA-Z0-9_]*"), "Should be a valid variable: " + variable);
    }
  }

  @Test
  void test_generate_terms() {
    final var terms = generator.generate("term", new Structure(), 5);

    assertEquals(5, terms.size(), "Should generate 5 terms");

    for (final var term : terms) {
      System.out.println("Generated term: " + term);
      // Just verify terms are generated, not empty
      assertFalse(term.isEmpty(), "Term should not be empty");
    }
  }

  @Test
  void test_round_trip_parse_and_generate() {
    // Use very shallow generator with simple atoms only
    final var shallowGen =
        new GrammarGenerator(
            grammar,
            RegexTerminalGenerator.builder()
                .register("[0-9]+", f -> "5")
                .register("[+-]?", f -> "")
                .register("[a-zA-Z_][a-zA-Z0-9_]*", f -> "x")
                .fallback(new LiteralTerminalGenerator())
                .build(),
            new Random(999),
            2); // Very shallow depth = simple expressions

    final var expressions = shallowGen.generate("atom", new Structure(), 20);

    int successCount = 0;
    for (final var expr : expressions) {
      final var parseResult = parser.parse(expr).toOptional();
      if (parseResult.isPresent()) {
        successCount++;
        System.out.println("✓ " + expr);
      } else {
        System.err.println("✗ Failed to parse: " + expr);
      }
    }

    // At least 80% should parse successfully
    assertTrue(
        successCount >= 16, "At least 16/20 expressions should parse (got " + successCount + ")");
  }

  @Test
  void test_deterministic_generation() {
    // With same seed, should generate same expressions
    final var random1 = new Random(123);
    final var gen1 =
        new GrammarGenerator(grammar, RegexTerminalGenerator.forNumbers(random1), random1, 15);

    final var random2 = new Random(123);
    final var gen2 =
        new GrammarGenerator(grammar, RegexTerminalGenerator.forNumbers(random2), random2, 15);

    final var expr1 = gen1.generateOne("expr", new Structure());
    final var expr2 = gen2.generateOne("expr", new Structure());

    assertEquals(expr1, expr2, "Same seed should produce same result");
  }

  @Test
  void test_generate_functions() {
    final var random = new Random(42);
    final var terminalGen =
        RegexTerminalGenerator.builder()
            .register("[0-9]+", features -> "5")
            .register("[a-zA-Z_][a-zA-Z0-9_]*", features -> "x")
            .fallback(new LiteralTerminalGenerator())
            .build();

    final var funcGenerator = new GrammarGenerator(grammar, terminalGen, random, 10);

    // Try to generate expressions with functions
    final var expressions = funcGenerator.generate("expr", new Structure(), 20);

    // Some expressions should contain function calls
    final var withFunctions =
        expressions.stream()
            .filter(
                expr ->
                    expr.contains("sin")
                        || expr.contains("cos")
                        || expr.contains("sqrt")
                        || expr.contains("log"))
            .toList();

    System.out.println("Expressions with functions:");
    withFunctions.forEach(System.out::println);

    // At least some should have functions
    assertFalse(withFunctions.isEmpty(), "Should generate some expressions with functions");
  }
}
