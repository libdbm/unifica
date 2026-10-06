package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.compiler.Compiled;
import com.libdbm.ugf.compiler.Compiler;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Chart state identity up to renamed variables (S-P9, PAR-2). */
class IdentityTests {

  private static final Path CONFORMANCE = Path.of("src/test/resources/conformance");
  private static final Path PORTS = Path.of("src/test/resources/grammars");

  private static Compiled compile(final String source) {
    return Compiler.compile(
            UnificationGrammarParserFactory.unvalidated(source).orElseThrow(),
            Predicates.standard())
        .orElseThrow();
  }

  /** Review H1: a nullable cycle through unresolved features terminates. */
  @Test
  void testNullableFeatureCycleTerminates() {
    final var parser =
        Parser.of(
            compile("start S; S{v: X} --> S{v: X}; S{v: X} --> ;"),
            new Options(Limits.NONE.states(1_000), ParseObserver.NOOP, false));

    final var result = parser.parse("");

    assertEquals(Outcome.ACCEPTED, result.outcome());
    assertTrue(result.statistics().states() < 20, "states: " + result.statistics().states());
  }

  /** Sibling agreement still holds through a cycle. */
  @Test
  void testAgreementThroughCycle() {
    final var parser =
        Parser.of(
            compile(
                "start S; S --> A{n: X} B{n: X}; A{n: X} --> A{n: X} | N{n: X};"
                    + " B{n: X} --> V{n: X}; N{n: sg} --> 'dog'; V{n: sg} --> 'runs';"
                    + " V{n: pl} --> 'run';"));

    assertEquals(Outcome.ACCEPTED, parser.parse("dog runs").outcome());
    assertEquals(Outcome.REJECTED, parser.parse("dog run").outcome());
  }

  /**
   * Merging states that differ only in renamed variables never changes a result: every conformance
   * input and port sample parses the same with raw and canonical keys.
   */
  @Test
  void testCanonicalMergingChangesNoResult() throws IOException {
    var compared = 0;
    for (final var input : inputs()) {
      final var compiled =
          UnificationGrammarParserFactory.parseWithImports(input.grammar())
              .flatMap(grammar -> Compiler.compile(grammar, Predicates.standard()));
      if (!(compiled instanceof Result.Success<Compiled, ErrorDetails>(var value))) {
        continue;
      }
      final var canonical = Parser.of(value, Options.DEFAULT, true).parse(input.text());
      final var raw = Parser.of(value, Options.DEFAULT, false).parse(input.text());
      final var label = input.grammar() + ": " + input.text().lines().findFirst().orElse("");
      assertEquals(raw.outcome(), canonical.outcome(), label);
      assertEquals(raw.penalty(), canonical.penalty(), label);
      assertEquals(raw.ambiguous(), canonical.ambiguous(), label);
      assertEquals(raw.tree(), canonical.tree(), label);
      compared++;
    }
    assertTrue(compared > 400, "compared " + compared);
  }

  private record Input(Path grammar, String text) {}

  private static List<Input> inputs() throws IOException {
    final var inputs = new ArrayList<Input>();
    try (final Stream<Path> cases = Files.list(CONFORMANCE)) {
      for (final var directory : cases.filter(Files::isDirectory).sorted().toList()) {
        // Cycle cases do not terminate with raw keys; they are covered by the tests above.
        if (directory.getFileName().toString().startsWith("feature-nullable")) {
          continue;
        }
        for (final var line : Files.readString(directory.resolve("inputs.txt")).split("\n")) {
          inputs.add(new Input(directory.resolve("grammar.ug"), unescape(line)));
        }
      }
    }
    try (final Stream<Path> ports = Files.list(PORTS)) {
      for (final var directory : ports.filter(Files::isDirectory).sorted().toList()) {
        for (final var folder : List.of("valid", "invalid")) {
          try (final Stream<Path> files = Files.list(directory.resolve(folder))) {
            for (final var file : files.sorted().toList()) {
              inputs.add(
                  new Input(
                      directory.resolve("grammar.ug"),
                      Files.readString(file, StandardCharsets.UTF_8)));
            }
          }
        }
      }
    }
    return inputs;
  }

  /** The corpus escapes: {@code \\n}, {@code \\r}, {@code \\t} and {@code \\\\}. */
  private static String unescape(final String line) {
    final var builder = new StringBuilder();
    for (var index = 0; index < line.length(); index++) {
      final var character = line.charAt(index);
      if (character == '\\' && index + 1 < line.length()) {
        final var next = line.charAt(++index);
        builder.append(
            switch (next) {
              case 'n' -> "\n";
              case 'r' -> "\r";
              case 't' -> "\t";
              case '\\' -> "\\";
              default -> "\\" + next;
            });
      } else {
        builder.append(character);
      }
    }
    return builder.toString();
  }
}
