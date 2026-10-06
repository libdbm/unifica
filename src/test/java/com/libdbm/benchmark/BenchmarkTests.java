package com.libdbm.benchmark;

import com.libdbm.ugf.compiler.Compiler;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.parser.Limits;
import com.libdbm.ugf.parser.Options;
import com.libdbm.ugf.parser.ParseObserver;
import com.libdbm.ugf.parser.ParseResult;
import com.libdbm.ugf.parser.Parser;
import com.sun.management.ThreadMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Measures the PRF-3 and PRF-4 workloads on the 2.0 pipeline, with the same inputs, warmup and run
 * counts as the 1.0.3 baseline in {@code docs/specs/BENCHMARKS.md}. Writes a Markdown table to
 * {@code target/benchmark/benchmark.md}.
 *
 * <pre>
 * mvn test -Dtest=BenchmarkTests -Dbenchmark=true -Dclean.home=/path/to/clean-star
 * </pre>
 *
 * <p>Without {@code clean.home} the CLEAN* workloads are skipped. CLEAN* files are read in place
 * and never copied into this repository.
 */
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
final class BenchmarkTests {

  private static final Logger LOGGER = LoggerFactory.getLogger(BenchmarkTests.class);
  private static final int WARMUP = 3;
  private static final int RUNS = 10;
  private static final long TIMEOUT = 30;

  private final List<String> rows = new ArrayList<>();

  @Test
  void testBenchmark() throws IOException {
    ambiguity();
    density();
    chain();
    agreement();
    branching();
    clean();
    final var table =
        new StringBuilder()
            .append(
                "| Workload | Accepted | Median ms | p95 ms | States | Calls | Hits | Allocated MB |\n")
            .append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
    rows.forEach(row -> table.append(row).append('\n'));
    table.append('\n').append(maxima());
    final var out = Path.of("target/benchmark/benchmark.md");
    Files.createDirectories(out.getParent());
    Files.writeString(out, table.toString());
    LOGGER.info("Benchmark written to {}\n{}", out, table);
  }

  /** {@code S -> S S | 'a'} over growing inputs: worst-case ambiguity. */
  private void ambiguity() {
    final var parser =
        parser(load("start S;\nS --> S S;\nS --> A;\nA --> 'a';"), Predicates.standard());
    for (final var length : List.of(5, 10, 15, 20, 25, 30, 60, 90)) {
      final var input = String.join(" ", Collections.nCopies(length, "a"));
      if (!measure("ambiguity n=" + length, () -> List.of(parser.parse(input)))) {
        break;
      }
    }
  }

  /** {@code S -> 'x' S | 'y'}: a long right-recursive chain (deep trees, review M1). */
  private void chain() {
    final var parser = parser(load("start S; S --> 'x' S | 'y';"), Predicates.standard());
    for (final var length : List.of(1_000, 5_000)) {
      final var input = "x ".repeat(length) + "y";
      if (!measure("chain n=" + length, () -> List.of(parser.parse(input)))) {
        break;
      }
    }
  }

  /**
   * Documents of agreeing sentences: feature unification on every completion. The last variant ends
   * with a disagreeing sentence, so the whole chart is built and then rejected.
   */
  private void agreement() {
    final var parser =
        parser(
            load(
                "start D; D --> S+; S --> N{num: X} V{num: X} '.';"
                    + " N{num: sg} --> 'dog'; N{num: pl} --> 'dogs';"
                    + " V{num: sg} --> 'runs'; V{num: pl} --> 'run';"),
            Predicates.standard());
    for (final var count : List.of(50, 200)) {
      final var input = "dog runs . dogs run . ".repeat(count / 2);
      if (!measure("agreement sentences=" + count, () -> List.of(parser.parse(input)))) {
        break;
      }
    }
    final var rejected = "dog runs . dogs run . ".repeat(100) + "dog run .";
    measure("agreement rejected at end", () -> List.of(parser.parse(rejected)));
  }

  /**
   * Equal-length tokens with different transitions: every token doubles the distinct lexical state
   * stacks, so the token graph grows exponentially with the input (review H3).
   */
  private void branching() {
    final var parser =
        parser(
            load("start S; S --> T*; T --> A | B; A --> 'a' ==> X; B --> 'a' ==> Y;"),
            Predicates.standard());
    for (final var length : List.of(8, 12, 16)) {
      final var input = "a ".repeat(length).strip();
      if (!measure("state branching n=" + length, () -> List.of(parser.parse(input)))) {
        break;
      }
    }
  }

  /**
   * The largest chart and graph any port sample needs, which the default limits must exceed with
   * wide headroom.
   */
  private String maxima() throws IOException {
    var states = 0L;
    var agenda = 0L;
    var nodes = 0L;
    var edges = 0L;
    var largest = "";
    try (final var directories = Files.list(Path.of("src/test/resources/grammars"))) {
      for (final var directory : directories.filter(Files::isDirectory).sorted().toList()) {
        final var parser =
            parser(
                UnificationGrammarParserFactory.parseWithImports(directory.resolve("grammar.ug"))
                    .orElseThrow(),
                Predicates.standard());
        try (final var samples = Files.list(directory.resolve("valid"))) {
          for (final var sample : samples.sorted().toList()) {
            final var statistics = parser.parse(Files.readString(sample)).statistics();
            if (statistics.states() > states) {
              states = statistics.states();
              largest = directory.getFileName() + "/" + sample.getFileName();
            }
            agenda = Math.max(agenda, statistics.agenda());
            nodes = Math.max(nodes, statistics.nodes());
            edges = Math.max(edges, statistics.edges());
          }
        }
      }
    }
    return String.format(
        "Port sample maxima: states %d (%s), agenda %d, graph nodes %d, graph edges %d%n",
        states, largest, agenda, nodes, edges);
  }

  /** Fifty words where every word has k equal-length categories. */
  private void density() {
    final var words = String.join(" ", Collections.nCopies(50, "word"));
    for (var k = 1; k <= 4; k++) {
      final var source = new StringBuilder("start S;\nS --> T+;\n");
      for (var i = 1; i <= k; i++) {
        source.append("T --> C").append(i).append(";\nC").append(i).append(" --> [a-z]+;\n");
      }
      final var parser = parser(load(source.toString()), Predicates.standard());
      if (!measure("density k=" + k, () -> List.of(parser.parse(words)))) {
        break;
      }
    }
  }

  /**
   * CLEAN* lexical grammar over its corpus. {@code bound} and {@code verb_allows} are not
   * registered by CLEAN*, so 1.0.3 evaluated them as false; the stubs keep that behaviour.
   */
  private void clean() throws IOException {
    final var home = System.getProperty("clean.home");
    if (home == null) {
      LOGGER.warn("clean.home not set; skipping CLEAN* workloads");
      return;
    }
    final var root = Path.of(home);
    final var grammar =
        UnificationGrammarParserFactory.parseWithImports(
                root.resolve("src/main/resources/clean-lexical.ug"))
            .orElseThrow();
    final var predicates =
        Predicates.builder()
            .lexical()
            .builtins()
            .add("bound", 1, 1, Predicates.Phase.SYNTACTIC, (environment, args) -> false)
            .add("verb_allows", 2, 2, Predicates.Phase.SYNTACTIC, (environment, args) -> false)
            .build();
    final var parser = parser(grammar, predicates);
    final var sentences =
        Files.readAllLines(root.resolve("src/test/resources/unified_corpus.cnl")).stream()
            .map(String::strip)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .toList();
    final var diagnosing =
        Parser.of(
            Compiler.compile(grammar, predicates).orElseThrow(),
            new Options(Limits.NONE, ParseObserver.NOOP, true));
    measure(
        "clean corpus with diagnostics", () -> sentences.stream().map(diagnosing::parse).toList());
    measure(
        "clean corpus (" + sentences.size() + " sentences)",
        () -> sentences.stream().map(parser::parse).toList());
    final var accepted =
        sentences.stream().filter(sentence -> parser.parse(sentence).success()).toList();
    for (final var size : List.of(1, 10, 100)) {
      if (accepted.size() < size) {
        break;
      }
      final var document = String.join(" ", accepted.subList(0, size));
      if (!measure("clean document " + size, () -> List.of(parser.parse(document)))) {
        break;
      }
    }
  }

  /**
   * Runs a workload WARMUP + RUNS times and records the accepted count, median and p95 time, and
   * the last run's summed statistics. Returns false if a run exceeded the timeout, so callers stop
   * growing the input.
   */
  private boolean measure(final String name, final Supplier<List<ParseResult>> workload) {
    final var executor =
        Executors.newSingleThreadExecutor(
            runnable -> {
              final var thread = new Thread(runnable);
              thread.setDaemon(true);
              return thread;
            });
    try {
      final var times = new ArrayList<Long>();
      List<ParseResult> results = List.of();
      final var allocated = new AtomicLong();
      final var threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
      for (var i = 0; i < WARMUP + RUNS; i++) {
        final var start = System.nanoTime();
        results =
            executor
                .submit(
                    () -> {
                      final var before = threads.getCurrentThreadAllocatedBytes();
                      final var produced = workload.get();
                      allocated.set(threads.getCurrentThreadAllocatedBytes() - before);
                      return produced;
                    })
                .get(TIMEOUT, TimeUnit.SECONDS);
        if (i >= WARMUP) {
          times.add(System.nanoTime() - start);
        }
      }
      Collections.sort(times);
      final var median = times.get(times.size() / 2) / 1e6;
      final var p95 = times.get((int) Math.ceil(times.size() * 0.95) - 1) / 1e6;
      final var accepted = results.stream().filter(ParseResult::success).count();
      final var states = results.stream().mapToLong(result -> result.statistics().states()).sum();
      final var calls = results.stream().mapToLong(result -> result.statistics().calls()).sum();
      final var hits = results.stream().mapToLong(result -> result.statistics().hits()).sum();
      rows.add(
          String.format(
              "| %s | %d | %.2f | %.2f | %d | %d | %d | %.1f |",
              name, accepted, median, p95, states, calls, hits, allocated.get() / 1e6));
      return true;
    } catch (final TimeoutException exception) {
      rows.add("| " + name + " | | > " + TIMEOUT + " s | | | | | |");
      return false;
    } catch (final InterruptedException | ExecutionException exception) {
      rows.add(
          "| " + name + " | | error: " + exception.getClass().getSimpleName() + " | | | | | |");
      return false;
    } finally {
      executor.shutdownNow();
    }
  }

  private static Grammar load(final String source) {
    return UnificationGrammarParserFactory.parse(source).orElseThrow();
  }

  private static Parser parser(final Grammar grammar, final Predicates predicates) {
    return Parser.of(Compiler.compile(grammar, predicates).orElseThrow());
  }
}
