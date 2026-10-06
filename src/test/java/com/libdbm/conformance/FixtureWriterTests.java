package com.libdbm.conformance;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarLinter;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.lexer.Graph;
import com.libdbm.ugf.parser.Limits;
import com.libdbm.ugf.parser.Options;
import com.libdbm.ugf.parser.ParseDiagnostics;
import com.libdbm.ugf.parser.ParseDiagnostics.Span;
import com.libdbm.ugf.parser.ParseObserver;
import com.libdbm.ugf.parser.Parser;
import com.libdbm.ugf.parser.ParserFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes conformance fixtures for the Dart port (libunifica).
 *
 * <p>Each case is a directory holding {@code grammar.ug} and {@code inputs.txt} (one input per
 * line, with {@code \n}, {@code \r}, {@code \t} and {@code \\} escapes). For every case this writes
 * {@code <case>.json} describing how this library loads the grammar, builds each input's token
 * graph and parses it. Runs only when {@code fixtures.cases} and {@code fixtures.out} are set:
 *
 * <pre>
 * mvn test -Dtest=FixtureWriterTests -Dfixtures.cases=DIR -Dfixtures.out=DIR
 * </pre>
 *
 * <p>The output records what this implementation currently does, defects included. It is an
 * observation tool and is not normative: expected results for the conformance corpus are written by
 * hand from {@code docs/SEMANTICS.md} (see {@link ConformanceTests}), never generated here.
 */
@EnabledIfSystemProperty(named = "fixtures.out", matches = ".+")
final class FixtureWriterTests {

  private static final Logger LOGGER = LoggerFactory.getLogger(FixtureWriterTests.class);

  @Test
  void testWrite() throws IOException {
    final var cases = Path.of(System.getProperty("fixtures.cases"));
    final var out = Path.of(System.getProperty("fixtures.out"));
    Files.createDirectories(out);
    try (final Stream<Path> stream = Files.list(cases)) {
      final var directories = stream.filter(Files::isDirectory).sorted().toList();
      for (final var directory : directories) {
        final var name = directory.getFileName().toString();
        final var fixture = fixture(directory);
        Files.writeString(out.resolve(name + ".json"), CanonicalJson.write(fixture));
        LOGGER.info("Wrote fixture {}", name);
      }
    }
  }

  private static Map<String, Object> fixture(final Path directory) throws IOException {
    final var source = Files.readString(directory.resolve("grammar.ug"), StandardCharsets.UTF_8);
    final var inputs = Fixtures.inputs(directory.resolve("inputs.txt"));
    final var result = new LinkedHashMap<String, Object>();
    final var loaded = load(source);
    result.put("grammar", loaded.summary());
    final var parses = new ArrayList<Object>();
    if (loaded.parser() != null) {
      for (final var input : inputs) {
        parses.add(run(loaded.parser(), input));
      }
    }
    result.put("inputs", parses);
    return result;
  }

  private record Loaded(Parser parser, Map<String, Object> summary) {}

  private static Loaded load(final String source) {
    final var summary = new LinkedHashMap<String, Object>();
    final var parsed = UnificationGrammarParserFactory.unvalidated(source);
    if (parsed instanceof Result.Failure<Grammar, ErrorDetails>) {
      summary.put("ok", false);
      summary.put("error", "syntax");
      summary.put("lint", List.of());
      return new Loaded(null, summary);
    }
    final var grammar = parsed.orElseThrow();
    final var created =
        ParserFactory.create(
            grammar, Predicates.standard(), new Options(Limits.NONE, ParseObserver.NOOP, true));
    final var error = created instanceof Result.Failure<Parser, ErrorDetails> ? "validation" : null;
    summary.put("ok", error == null);
    summary.put("error", error);
    summary.put("lint", lint(grammar));
    return new Loaded(error == null ? created.orElseThrow() : null, summary);
  }

  private static List<Object> lint(final Grammar grammar) {
    final var issues = new ArrayList<Map<String, Object>>();
    for (final var issue : new GrammarLinter().lint(grammar).issues()) {
      final var entry = new LinkedHashMap<String, Object>();
      entry.put("severity", issue.severity().name().toLowerCase());
      entry.put("title", issue.title());
      entry.put("context", issue.context());
      issues.add(entry);
    }
    return sorted(issues);
  }

  private static Map<String, Object> run(final Parser parser, final String input) {
    final var entry = new LinkedHashMap<String, Object>();
    entry.put("input", input);
    try {
      entry.put("graph", parser.tokenize(input).map(FixtureWriterTests::graph).orElse(null));
    } catch (final RuntimeException exception) {
      entry.put("graph", exception(exception));
    }
    try {
      final var parsed = parser.parse(input);
      final var parse = new LinkedHashMap<String, Object>();
      parse.put("outcome", parsed.outcome().name().toLowerCase(Locale.ROOT));
      parse.put("penalty", parsed.penalty());
      parse.put("ambiguous", parsed.ambiguous());
      parse.put("tree", parsed.tree() == null ? null : Fixtures.tree(parsed.tree()));
      parse.put("diagnostics", diagnostics(parsed.diagnostics()));
      entry.put("parse", parse);
    } catch (final RuntimeException exception) {
      entry.put("parse", exception(exception));
    }
    return entry;
  }

  private static Map<String, Object> exception(final RuntimeException exception) {
    final var entry = new LinkedHashMap<String, Object>();
    entry.put("exception", exception.getClass().getSimpleName());
    return entry;
  }

  /** The token graph: nodes (offset and state stack) and edges, in graph order. */
  private static Map<String, Object> graph(final Graph graph) {
    final var nodes = new ArrayList<Object>();
    for (final var node : graph.nodes()) {
      final var entry = new LinkedHashMap<String, Object>();
      entry.put("offset", node.offset());
      entry.put("states", node.states());
      entry.put("final", graph.isFinal(node));
      nodes.add(entry);
    }
    final var edges = new ArrayList<Object>();
    for (final var edge : graph.edges()) {
      final var entry = new LinkedHashMap<String, Object>();
      entry.put("from", edge.from().id());
      entry.put("to", edge.to().id());
      entry.put("start", edge.start());
      entry.put("end", edge.end());
      entry.put("text", edge.text());
      entry.put("category", edge.category());
      entry.put("cost", edge.cost());
      entry.put("features", Fixtures.structure(edge.features()));
      edges.add(entry);
    }
    final var result = new LinkedHashMap<String, Object>();
    result.put("nodes", nodes);
    result.put("edges", edges);
    return result;
  }

  private static List<Object> diagnostics(final ParseDiagnostics diagnostics) {
    final var entries = new ArrayList<Map<String, Object>>();
    if (diagnostics == null) {
      return List.of();
    }
    diagnostics
        .constraintFailures()
        .forEach(
            failure -> entries.add(diagnostic("constraint", failure.span(), failure.position())));
    diagnostics
        .tokenizationErrors()
        .forEach(
            failure -> entries.add(diagnostic("tokenization", failure.span(), failure.position())));
    diagnostics
        .parsingIssues()
        .forEach(failure -> entries.add(diagnostic("parsing", failure.span(), failure.position())));
    diagnostics
        .regexFailures()
        .forEach(failure -> entries.add(diagnostic("regex", failure.span(), failure.position())));
    diagnostics
        .quantifierLoops()
        .forEach(
            failure -> entries.add(diagnostic("quantifier", failure.span(), failure.position())));
    diagnostics
        .unificationFailures()
        .forEach(
            failure -> entries.add(diagnostic("unification", failure.span(), failure.position())));
    diagnostics
        .ambiguities()
        .forEach(ambiguity -> entries.add(diagnostic("ambiguity", ambiguity.span(), -1)));
    return sorted(entries.stream().distinct().toList());
  }

  private static Map<String, Object> diagnostic(
      final String kind, final Span span, final int position) {
    final var entry = new LinkedHashMap<String, Object>();
    entry.put("kind", kind);
    entry.put("position", span == null ? position : span.tokenIndex());
    entry.put("start", span == null ? null : span.charStart());
    entry.put("end", span == null ? null : span.charEnd());
    return entry;
  }

  /**
   * Sorts entries by their canonical JSON form, which gives a stable order independent of HashMap
   * iteration and lattice construction order.
   */
  private static List<Object> sorted(final List<? extends Map<String, Object>> entries) {
    return entries.stream()
        .sorted(Comparator.comparing(CanonicalJson::write))
        .map(entry -> (Object) entry)
        .toList();
  }
}
