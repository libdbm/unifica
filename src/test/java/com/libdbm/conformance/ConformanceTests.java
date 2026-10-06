package com.libdbm.conformance;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.generator.GrammarGenerator;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.parser.ParseResult;
import com.libdbm.ugf.parser.Parser;
import com.libdbm.ugf.parser.ParserFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the conformance corpus in {@code src/test/resources/conformance}.
 *
 * <p>Each case directory holds:
 *
 * <ul>
 *   <li>{@code grammar.ug}: the grammar under test.
 *   <li>{@code inputs.txt}: one input per line ({@code \n}, {@code \r}, {@code \t}, {@code \\}
 *       escapes).
 *   <li>{@code case.json}: {@code {rules, description}} plus an optional {@code generate} object
 *       ({@code start}, {@code count}, {@code seed}) for generation round trips.
 *   <li>{@code expected.json}: {@code {grammar, inputs, generation}}, written by hand from {@code
 *       docs/SEMANTICS.md}. It is never generated from implementation output.
 * </ul>
 *
 * <p>Expected results are patterns. A map matches when every key it names matches, so keys left out
 * (for example {@code tree} or a node's {@code features}) are not compared. Lists must match in
 * length and element by element. Scalars must be equal.
 *
 * <p>Every rule ID in {@code docs/SEMANTICS.md} must be named by some case's {@code rules} or by
 * {@code exempt.txt}, which records rules that grammar text cannot exercise and the test that
 * covers each one instead.
 *
 * <p>Cases named in {@code pending.txt} have an owning task that has not landed. The runner skips
 * them while they fail, and fails the run as soon as one passes, so a fixed case must come off the
 * list in the same change that fixes it.
 */
final class ConformanceTests {

  private static final Path ROOT = Path.of("src/test/resources/conformance");
  private static final Path SEMANTICS = Path.of("docs/SEMANTICS.md");

  private static void run(final Path directory, final String owner) throws IOException {
    final var mismatches = check(directory);
    if (owner != null) {
      if (mismatches.isEmpty()) {
        fail("Pending case now passes; remove it from pending.txt (owner: " + owner + ")");
      }
      Assumptions.abort("Pending on " + owner + ": " + mismatches.getFirst());
    }
    assertTrue(mismatches.isEmpty(), String.join("\n", mismatches));
  }

  private static List<String> check(final Path directory) throws IOException {
    final var expected = CanonicalJson.read(Files.readString(directory.resolve("expected.json")));
    // Round-trip through JSON so numbers and maps have the same types as the expectation.
    final var actual = CanonicalJson.read(CanonicalJson.write(actual(directory)));
    final var inputs = Fixtures.inputs(directory.resolve("inputs.txt"));
    final var mismatches = new ArrayList<String>();
    if (expected instanceof Map<?, ?> map
        && map.get("inputs") instanceof List<?> list
        && list.size() != inputs.size()) {
      mismatches.add(
          "expected.json has " + list.size() + " inputs but inputs.txt has " + inputs.size());
    }
    compare("$", expected, actual, mismatches);
    return mismatches;
  }

  private static Map<String, Object> actual(final Path directory) throws IOException {
    final var source = Files.readString(directory.resolve("grammar.ug"), StandardCharsets.UTF_8);
    final var settings =
        (Map<?, ?>) CanonicalJson.read(Files.readString(directory.resolve("case.json")));
    final var result = new LinkedHashMap<String, Object>();
    final var summary = new LinkedHashMap<String, Object>();
    Grammar grammar = null;
    Function<String, ParseResult> parse = null;
    try {
      final var loaded = UnificationGrammarParserFactory.unvalidated(source);
      switch (loaded) {
        case Result.Success<Grammar, ErrorDetails>(var value) -> grammar = value;
        case Result.Failure<Grammar, ErrorDetails>(var error) -> {
          summary.put("ok", false);
          summary.put("error", code(error));
        }
      }
      if (grammar != null) {
        switch (ParserFactory.create(grammar)) {
          case Result.Success<Parser, ErrorDetails>(var parser) -> parse = parser::parse;
          case Result.Failure<Parser, ErrorDetails>(var error) -> {
            summary.put("ok", false);
            summary.put("error", "validation");
          }
        }
      }
      if (parse != null) {
        summary.put("ok", true);
      }
    } catch (final RuntimeException exception) {
      summary.put("ok", false);
      summary.put("error", "exception:" + exception.getClass().getSimpleName());
    }
    result.put("grammar", summary);
    if (parse == null) {
      return result;
    }
    final var parses = new ArrayList<Object>();
    for (final var input : Fixtures.inputs(directory.resolve("inputs.txt"))) {
      parses.add(parse(parse, input));
    }
    result.put("inputs", parses);
    if (settings.get("generate") instanceof Map<?, ?> generate) {
      result.put("generation", generate(grammar, parse, generate));
    }
    return result;
  }

  /** Maps a loader error code to the corpus vocabulary: {@code syntax} or {@code validation}. */
  private static String code(final ErrorDetails error) {
    return switch (error.code()) {
      case UnificationGrammarParserFactory.SYNTAX -> "syntax";
      case UnificationGrammarParserFactory.VALIDATION -> "validation";
      default -> error.code();
    };
  }

  private static Map<String, Object> parse(
      final Function<String, ParseResult> parse, final String input) {
    final var entry = new LinkedHashMap<String, Object>();
    entry.put("input", input);
    try {
      final var parsed = parse.apply(input);
      entry.put("outcome", parsed.outcome().name().toLowerCase(Locale.ROOT));
      entry.put("penalty", parsed.penalty());
      entry.put("ambiguous", parsed.ambiguous());
      entry.put("tree", parsed.tree() == null ? null : Fixtures.tree(parsed.tree()));
    } catch (final RuntimeException exception) {
      entry.put("outcome", "exception:" + exception.getClass().getSimpleName());
    }
    return entry;
  }

  /** Generates sentences and reports those that do not parse (S-N1). */
  private static Map<String, Object> generate(
      final Grammar grammar, final Function<String, ParseResult> parse, final Map<?, ?> settings) {
    final var start = (String) settings.get("start");
    final var count = ((Long) settings.get("count")).intValue();
    final var seed = (Long) settings.get("seed");
    final var generator =
        GrammarGenerator.builder(grammar).random(new Random(seed)).build().orElseThrow();
    final var sentences = generator.generate(start, Structure.EMPTY, count).orElse(List.of());
    final var rejected =
        sentences.stream()
            .filter(sentence -> !parse.apply(sentence).success())
            .distinct()
            .sorted()
            .toList();
    final var entry = new LinkedHashMap<String, Object>();
    entry.put("empty", sentences.isEmpty());
    entry.put("rejected", rejected);
    return entry;
  }

  private static void compare(
      final String path,
      final Object expected,
      final Object actual,
      final List<String> mismatches) {
    switch (expected) {
      case Map<?, ?> map -> {
        if (!(actual instanceof Map<?, ?> other)) {
          mismatches.add(
              path + ": expected an object but was " + CanonicalJson.write(actual).strip());
          return;
        }
        for (final var entry : map.entrySet()) {
          final var key = (String) entry.getKey();
          if (!other.containsKey(key)) {
            mismatches.add(path + "." + key + ": missing");
          } else {
            compare(path + "." + key, entry.getValue(), other.get(key), mismatches);
          }
        }
      }
      case List<?> list -> {
        if (!(actual instanceof List<?> other) || other.size() != list.size()) {
          mismatches.add(
              path
                  + ": expected "
                  + CanonicalJson.write(list).strip()
                  + " but was "
                  + CanonicalJson.write(actual).strip());
          return;
        }
        for (var i = 0; i < list.size(); i++) {
          compare(path + "[" + i + "]", list.get(i), other.get(i), mismatches);
        }
      }
      case null, default -> {
        if (!Objects.equals(expected, actual)) {
          mismatches.add(
              path
                  + ": expected "
                  + CanonicalJson.write(expected).strip()
                  + " but was "
                  + CanonicalJson.write(actual).strip());
        }
      }
    }
  }

  private static List<Path> cases() throws IOException {
    try (final Stream<Path> stream = Files.list(ROOT)) {
      return stream.filter(Files::isDirectory).sorted().toList();
    }
  }

  /** Reads {@code pending.txt}: {@code <case> <owner>} per line, {@code #} starts a comment. */
  private static Map<String, String> pending() throws IOException {
    return entries(ROOT.resolve("pending.txt"));
  }

  /** Reads {@code exempt.txt}: {@code <rule> <reason and covering test>} per line. */
  private static Map<String, String> exempt() throws IOException {
    return entries(ROOT.resolve("exempt.txt"));
  }

  private static Map<String, String> entries(final Path path) throws IOException {
    final var entries = new LinkedHashMap<String, String>();
    for (final var line : Files.readAllLines(path)) {
      final var content = line.replaceFirst("#.*", "").strip();
      if (content.isEmpty()) {
        continue;
      }
      final var parts = content.split("\\s+", 2);
      entries.put(parts[0], parts.length > 1 ? parts[1] : "unassigned");
    }
    return entries;
  }

  @TestFactory
  Stream<DynamicTest> testCorpus() throws IOException {
    final var pending = pending();
    return cases().stream()
        .map(
            directory -> {
              final var name = directory.getFileName().toString();
              return DynamicTest.dynamicTest(name, () -> run(directory, pending.get(name)));
            });
  }

  @Test
  void testPendingEntriesNameExistingCases() throws IOException {
    final var names =
        cases().stream().map(directory -> directory.getFileName().toString()).toList();
    for (final var name : pending().keySet()) {
      assertTrue(names.contains(name), "pending.txt names unknown case: " + name);
    }
  }

  /** Every rule in docs/SEMANTICS.md is exercised by a case or exempted with a reason (SEM-4). */
  @Test
  void testEveryRuleCovered() throws IOException {
    final var rules = new TreeSet<String>();
    final var matcher =
        Pattern.compile("^- \\*\\*(S-[A-Z][0-9]+a?)\\.\\*\\*", Pattern.MULTILINE)
            .matcher(Files.readString(SEMANTICS));
    while (matcher.find()) {
      rules.add(matcher.group(1));
    }
    final var covered = new TreeSet<String>(exempt().keySet());
    for (final var directory : cases()) {
      final var settings =
          (Map<?, ?>) CanonicalJson.read(Files.readString(directory.resolve("case.json")));
      for (final var rule : (List<?>) settings.get("rules")) {
        covered.add((String) rule);
      }
    }
    final var missing = new TreeSet<>(rules);
    missing.removeAll(covered);
    final var unknown = new TreeSet<>(covered);
    unknown.removeAll(rules);
    assertTrue(missing.isEmpty(), "Rules without a case or exemption: " + missing);
    assertTrue(unknown.isEmpty(), "Cases or exemptions name unknown rules: " + unknown);
  }
}
