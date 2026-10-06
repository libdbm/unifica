package com.libdbm.ports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.libdbm.ugf.compiler.Compiler;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.parser.Outcome;
import com.libdbm.ugf.parser.Parser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Grammars ported from ANTLR grammars-v4, in {@code src/test/resources/grammars/<name>/}.
 *
 * <p>Each directory holds {@code grammar.ug}, a {@code NOTICE.md} naming the source grammar and its
 * license, sample inputs in {@code valid/} that must be accepted, and inputs in {@code invalid/}
 * that must be rejected. Each grammar also has conformance cases ({@code port-<name>}) with
 * hand-written expected trees.
 */
final class GrammarPortTests {

  private static final Path ROOT = Path.of("src/test/resources/grammars");
  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  @TestFactory
  Stream<DynamicNode> testPorts() throws IOException {
    final var containers = new ArrayList<DynamicNode>();
    for (final var directory : directories(ROOT)) {
      containers.add(
          DynamicContainer.dynamicContainer(directory.getFileName().toString(), tests(directory)));
    }
    return containers.stream();
  }

  private static List<DynamicNode> tests(final Path directory) throws IOException {
    final var tests = new ArrayList<DynamicNode>();
    tests.add(
        DynamicTest.dynamicTest(
            "notice",
            () ->
                assertTrue(Files.exists(directory.resolve("NOTICE.md")), "NOTICE.md is required")));
    final var grammar =
        UnificationGrammarParserFactory.parseWithImports(directory.resolve("grammar.ug"));
    final var compiled = grammar.flatMap(source -> Compiler.compile(source, Predicates.standard()));
    tests.add(DynamicTest.dynamicTest("compiles", () -> compiled.orElseThrow()));
    for (final var expected : List.of(Outcome.ACCEPTED, Outcome.REJECTED)) {
      final var folder = directory.resolve(expected == Outcome.ACCEPTED ? "valid" : "invalid");
      for (final var input : files(folder)) {
        tests.add(
            DynamicTest.dynamicTest(
                folder.getFileName() + "/" + input.getFileName(),
                () -> {
                  final var parser = Parser.of(compiled.orElseThrow());
                  final var text = Files.readString(input, StandardCharsets.UTF_8);
                  final var result = assertTimeoutPreemptively(TIMEOUT, () -> parser.parse(text));
                  assertEquals(expected, result.outcome(), input.toString());
                }));
      }
    }
    return tests;
  }

  private static List<Path> directories(final Path root) throws IOException {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    try (final Stream<Path> stream = Files.list(root)) {
      return stream.filter(Files::isDirectory).sorted().toList();
    }
  }

  private static List<Path> files(final Path folder) throws IOException {
    if (!Files.isDirectory(folder)) {
      return List.of();
    }
    try (final Stream<Path> stream = Files.list(folder)) {
      return stream.filter(Files::isRegularFile).sorted().toList();
    }
  }
}
