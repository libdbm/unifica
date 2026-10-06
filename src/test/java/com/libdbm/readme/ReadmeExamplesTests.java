package com.libdbm.readme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.compiler.Compiled;
import com.libdbm.ugf.compiler.Compiler;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.generator.GrammarGenerator;
import com.libdbm.ugf.generator.Policy;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.lexer.TokenSource;
import com.libdbm.ugf.parser.Limits;
import com.libdbm.ugf.parser.Options;
import com.libdbm.ugf.parser.Outcome;
import com.libdbm.ugf.parser.ParseDiagnostics;
import com.libdbm.ugf.parser.ParseObserver;
import com.libdbm.ugf.parser.ParseTree;
import com.libdbm.ugf.parser.Parser;
import com.libdbm.ugf.parser.ParserFactory;
import com.libdbm.ugf.parser.Token;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every example in {@code README.md} (DOC-1, DOC-2). Grammar examples are the files in {@code
 * src/test/resources/readme/}; Java examples are the regions of this file between {@code //
 * example: <name>} and {@code // end example}. {@link #testReadmeInSync} checks that each tagged
 * README block is byte-identical to its source.
 */
class ReadmeExamplesTests {

  private static final Path README = Path.of("README.md");
  private static final Path FIXTURES = Path.of("src/test/resources/readme");
  private static final Path SOURCE =
      Path.of("src/test/java/com/libdbm/readme/ReadmeExamplesTests.java");
  private static final Pattern TAG =
      Pattern.compile("<!-- example: ([\\w.-]+) -->\\n```\\w*\\n(.*?)```", Pattern.DOTALL);
  private static final Pattern REGION =
      Pattern.compile("\\n( *)// example: ([\\w-]+)\\n(.*?)\\n *// end example", Pattern.DOTALL);
  private static final Pattern BUILTINS =
      Pattern.compile("<!-- builtins -->(.*?)<!-- /builtins -->", Pattern.DOTALL);
  private static final Pattern NAME = Pattern.compile("^\\| `(\\w+)\\(", Pattern.MULTILINE);

  private final List<ParseTree> rendered = new ArrayList<>();
  private final List<Object> reported = new ArrayList<>();
  private int retries;

  @BeforeEach
  void setup() {
    rendered.clear();
    reported.clear();
    retries = 0;
  }

  private void render(final ParseTree tree) {
    rendered.add(tree);
  }

  private void report(final ParseDiagnostics diagnostics) {
    reported.add(diagnostics);
  }

  private void report(final ErrorDetails error) {
    reported.add(error);
  }

  private void retry() {
    retries++;
  }

  private static Grammar load(final String name) {
    return UnificationGrammarParserFactory.parse(FIXTURES.resolve(name)).orElseThrow();
  }

  private static Parser parser(final String name) {
    return ParserFactory.create(load(name)).orElseThrow();
  }

  // Java examples

  @Test
  void testQuickStart() {
    final var grammar = FIXTURES.resolve("arithmetic.ug");
    // example: quickstart
    final var parser = ParserFactory.create(grammar).orElseThrow();
    final var result = parser.parse("2 * (3 + 4)");

    if (result.success()) {
      render(result.tree());
    }
    // end example

    assertEquals(1, rendered.size());
    assertEquals("Expr", ((ParseTree.Node) rendered.getFirst()).symbol());
  }

  @Test
  void testPipeline() {
    final var grammar = FIXTURES.resolve("agreement.ug");
    // example: pipeline
    final var loaded = UnificationGrammarParserFactory.parseWithImports(grammar);
    final var compiled = loaded.flatMap(source -> Compiler.compile(source, Predicates.standard()));

    switch (compiled) {
      case Result.Success<Compiled, ErrorDetails>(var value) -> {
        final var options = new Options(Limits.DEFAULT.states(100_000), ParseObserver.NOOP, true);
        final var result = Parser.of(value, options).parse("the dogs bark");
        switch (result.outcome()) {
          case ACCEPTED -> render(result.tree());
          case REJECTED -> report(result.diagnostics());
          case LIMIT, CANCELLED -> retry();
        }
      }
      case Result.Failure<Compiled, ErrorDetails>(var error) -> report(error);
    }
    // end example

    assertEquals(1, rendered.size());
    assertEquals(0, retries);
    assertTrue(reported.isEmpty());
  }

  @Test
  void testPredicates() {
    final var names = Set.of("Ada", "Grace");
    // example: predicates
    final var predicates =
        Predicates.builder()
            .lexical()
            .builtins()
            .add(
                "known",
                1,
                1,
                Predicates.Phase.SYNTACTIC,
                (environment, args) -> {
                  final var value = environment.resolve(args.getFirst());
                  return value != null && names.contains(value.text());
                })
            .build();
    final var grammar =
        UnificationGrammarParserFactory.parse(
            """
        start S;
        S --> 'hello' Name:n where known(n);
        Name --> [A-Z][a-z]+;
        """);
    final var parser =
        grammar.flatMap(source -> ParserFactory.create(source, predicates, Options.DEFAULT));
    // end example

    assertEquals(Outcome.ACCEPTED, parser.orElseThrow().parse("hello Ada").outcome());
    assertEquals(Outcome.REJECTED, parser.orElseThrow().parse("hello Alan").outcome());
  }

  @Test
  void testTokens() {
    final var parser = parser("tagged.ug");
    // example: tokens
    final var plural = Structure.builder().with("num", "pl").build();
    final var tokens =
        List.of(
            List.of(new Token("dogs", plural, 0, 4, "Noun")),
            List.of(
                new Token("bark", plural, 5, 9, "Verb"), new Token("bark", plural, 5, 9, "Noun")));

    final var graph = TokenSource.alternatives(tokens).tokenize("dogs bark").orElseThrow();
    final var result = parser.parse(graph);
    // end example

    assertEquals(Outcome.ACCEPTED, result.outcome());
    assertFalse(result.ambiguous());
  }

  @Test
  void testGeneration() {
    final var grammar = load("agreement.ug");
    // example: generation
    final var generator =
        GrammarGenerator.builder(grammar)
            .random(new Random(42))
            .policy(Policy.DEFAULT.depth(10))
            .build()
            .orElseThrow();

    final var plural = Structure.builder().with("num", "pl").build();
    final var sentences = generator.generate("S", plural, 5);
    // end example

    final var parser = parser("agreement.ug");
    for (final var sentence : sentences.orElseThrow()) {
      final var result = parser.parse(sentence);
      assertEquals(Outcome.ACCEPTED, result.outcome(), sentence);
      assertEquals(
          new StringConstant("pl"),
          ((ParseTree.Node) result.tree()).features().get("num"),
          sentence);
    }
  }

  // Grammar examples

  @Test
  void testArithmetic() {
    final var parser = parser("arithmetic.ug");

    assertEquals(Outcome.ACCEPTED, parser.parse("2 * (3 + 4.5) - 1").outcome());
    assertFalse(parser.parse("2 * (3 + 4) - 1").ambiguous());
    assertEquals(Outcome.REJECTED, parser.parse("2 +").outcome());
  }

  @Test
  void testAgreement() {
    final var parser = parser("agreement.ug");

    assertEquals(Outcome.ACCEPTED, parser.parse("a dog barks").outcome());
    assertEquals(Outcome.ACCEPTED, parser.parse("the dogs bark").outcome());
    assertEquals(Outcome.REJECTED, parser.parse("a dogs bark").outcome());
    assertEquals(Outcome.REJECTED, parser.parse("the dog bark").outcome());
  }

  @Test
  void testGreeting() {
    final var parser = parser("greeting.ug");

    assertEquals(0, parser.parse("hello, Anna").penalty());
    assertEquals(2, parser.parse("hello, Bob").penalty());
    assertEquals(1, parser.parse("hello Anna").penalty());
    assertEquals(Outcome.REJECTED, parser.parse("hello, anna").outcome());
  }

  @Test
  void testStates() {
    final var parser = parser("states.ug");

    final var result = parser.parse("see `x + 1` here # a comment\nand `y`");
    assertEquals(Outcome.ACCEPTED, result.outcome());
    assertEquals(Outcome.REJECTED, parser.parse("see `x + 1 here").outcome());
    assertEquals(Outcome.REJECTED, parser.parse("see + here").outcome());
  }

  // Synchronization

  @Test
  void testReadmeInSync() throws IOException {
    final var readme = Files.readString(README);
    final var regions = regions(Files.readString(SOURCE));
    final var used = new HashSet<String>();
    final var matcher = TAG.matcher(readme);
    while (matcher.find()) {
      final var name = matcher.group(1);
      final var block = matcher.group(2);
      used.add(name);
      if (name.endsWith(".ug")) {
        assertEquals(Files.readString(FIXTURES.resolve(name)), block, "README block " + name);
      } else {
        assertNotNull(regions.get(name), "no region for README block " + name);
        assertEquals(regions.get(name), block, "README block " + name);
      }
    }
    final var expected = new TreeSet<>(regions.keySet());
    try (final var files = Files.list(FIXTURES)) {
      files.map(path -> path.getFileName().toString()).forEach(expected::add);
    }
    assertEquals(expected, new TreeSet<>(used), "every example appears in the README");
  }

  /** The README's builtin table lists exactly the registered predicates (DOC-3). */
  @Test
  void testBuiltinsListed() throws IOException {
    final var table = BUILTINS.matcher(Files.readString(README));
    assertTrue(table.find(), "README has a builtins table");
    final var listed = new TreeSet<String>();
    NAME.matcher(table.group(1)).results().forEach(match -> listed.add(match.group(1)));
    final var registered = new TreeSet<String>();
    for (final var phase : Predicates.Phase.values()) {
      registered.addAll(Predicates.standard().names(phase));
    }

    assertEquals(registered, listed);
  }

  /** Each Java region, with its indentation removed and a trailing newline, keyed by name. */
  private static Map<String, String> regions(final String source) {
    final var regions = new TreeMap<String, String>();
    final var matcher = REGION.matcher(source);
    while (matcher.find()) {
      final var indent = matcher.group(1).length();
      final var lines =
          matcher
              .group(3)
              .lines()
              .map(line -> line.length() >= indent ? line.substring(indent) : line.strip())
              .toList();
      regions.put(matcher.group(2), String.join("\n", lines) + "\n");
    }
    return regions;
  }
}
