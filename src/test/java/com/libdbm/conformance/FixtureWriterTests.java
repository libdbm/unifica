package com.libdbm.conformance;

import com.libdbm.ugf.features.Binding;
import com.libdbm.ugf.features.BooleanConstant;
import com.libdbm.ugf.features.FeaturePath;
import com.libdbm.ugf.features.NumericConstant;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Value;
import com.libdbm.ugf.features.Variable;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarLinter;
import com.libdbm.ugf.grammar.loader.GrammarSyntaxException;
import com.libdbm.ugf.grammar.loader.GrammarValidationException;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.parser.LexicalAnalyzer;
import com.libdbm.ugf.parser.ParseDiagnostics;
import com.libdbm.ugf.parser.ParseDiagnostics.Span;
import com.libdbm.ugf.parser.ParseTree;
import com.libdbm.ugf.parser.ParserFactory;
import com.libdbm.ugf.parser.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Writes conformance fixtures for the Dart port (libunifica).
 *
 * <p>Each case is a directory holding {@code grammar.ug} and {@code inputs.txt} (one input per
 * line, with {@code \n}, {@code \t} and {@code \\} escapes). For every case this writes
 * {@code <case>.json} describing how this library loads the grammar, tokenizes each input and parses
 * it. Runs only when {@code fixtures.cases} and {@code fixtures.out} are set:
 *
 * <pre>
 * mvn test -Dtest=FixtureWriterTests -Dfixtures.cases=DIR -Dfixtures.out=DIR
 * </pre>
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
        final var inputs = inputs(directory.resolve("inputs.txt"));
        final var result = new LinkedHashMap<String, Object>();
        final var loaded = load(source);
        result.put("grammar", loaded.summary());
        final var parses = new ArrayList<Object>();
        if (loaded.grammar() != null) {
            final var lexer = LexicalAnalyzer.build(loaded.grammar());
            final var factory = ParserFactory.create(loaded.grammar());
            for (final var input : inputs) {
                parses.add(run(lexer, factory, input));
            }
        }
        result.put("inputs", parses);
        return result;
    }

    private record Loaded(Grammar grammar, Map<String, Object> summary) {
    }

    private static Loaded load(final String source) {
        final var summary = new LinkedHashMap<String, Object>();
        final Grammar grammar;
        try {
            grammar = UnificationGrammarParserFactory.unvalidated(source);
        } catch (final GrammarSyntaxException exception) {
            summary.put("ok", false);
            summary.put("error", "syntax");
            summary.put("lint", List.of());
            return new Loaded(null, summary);
        }
        String error = null;
        try {
            UnificationGrammarParserFactory.parse(source);
        } catch (final GrammarValidationException exception) {
            error = "validation";
        }
        summary.put("ok", error == null);
        summary.put("error", error);
        summary.put("lint", lint(grammar));
        return new Loaded(error == null ? grammar : null, summary);
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

    private static Map<String, Object> run(
            final LexicalAnalyzer lexer, final ParserFactory factory, final String input) {
        final var entry = new LinkedHashMap<String, Object>();
        entry.put("input", input);
        try {
            entry.put("lattice", lattice(lexer.tokenize(input)));
        } catch (final RuntimeException exception) {
            entry.put("lattice", exception(exception));
        }
        try {
            final var parsed = factory.parse(input);
            final var parse = new LinkedHashMap<String, Object>();
            parse.put("success", parsed.success());
            parse.put("penalty", parsed.penalty());
            parse.put("ambiguous", parsed.ambiguous());
            parse.put("tree", parsed.tree() == null ? null : tree(parsed.tree()));
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

    private static List<Object> lattice(final List<List<Token>> columns) {
        final var tokens = new ArrayList<Map<String, Object>>();
        for (final var column : columns) {
            for (final var token : column) {
                final var entry = new LinkedHashMap<String, Object>();
                entry.put("start", token.start());
                entry.put("end", token.end());
                entry.put("text", token.text());
                entry.put("features", structure(token.features()));
                tokens.add(entry);
            }
        }
        return sorted(tokens);
    }

    private static Map<String, Object> tree(final ParseTree tree) {
        final var entry = new LinkedHashMap<String, Object>();
        switch (tree) {
            case ParseTree.Node node -> {
                entry.put("symbol", node.symbol());
                entry.put("label", node.label());
                entry.put("features", structure(node.features()));
                final var children = new ArrayList<Object>();
                for (final var child : node.children()) {
                    children.add(tree(child));
                }
                entry.put("children", children);
            }
            case ParseTree.Leaf leaf -> {
                entry.put("text", leaf.text());
                entry.put("start", leaf.start());
                entry.put("end", leaf.end());
                entry.put("features", structure(leaf.features()));
            }
        }
        return entry;
    }

    private static List<Object> diagnostics(final ParseDiagnostics diagnostics) {
        final var entries = new ArrayList<Map<String, Object>>();
        if (diagnostics == null) {
            return List.of();
        }
        diagnostics.constraintFailures().forEach(failure -> entries.add(diagnostic("constraint", failure.span(), failure.position())));
        diagnostics.tokenizationErrors().forEach(failure -> entries.add(diagnostic("tokenization", failure.span(), failure.position())));
        diagnostics.parsingIssues().forEach(failure -> entries.add(diagnostic("parsing", failure.span(), failure.position())));
        diagnostics.regexFailures().forEach(failure -> entries.add(diagnostic("regex", failure.span(), failure.position())));
        diagnostics.quantifierLoops().forEach(failure -> entries.add(diagnostic("quantifier", failure.span(), failure.position())));
        diagnostics.unificationFailures().forEach(failure -> entries.add(diagnostic("unification", failure.span(), failure.position())));
        diagnostics.ambiguities().forEach(ambiguity -> entries.add(diagnostic("ambiguity", ambiguity.span(), -1)));
        return sorted(entries.stream().distinct().toList());
    }

    private static Map<String, Object> diagnostic(final String kind, final Span span, final int position) {
        final var entry = new LinkedHashMap<String, Object>();
        entry.put("kind", kind);
        entry.put("position", span == null ? position : span.tokenIndex());
        entry.put("start", span == null ? null : span.charStart());
        entry.put("end", span == null ? null : span.charEnd());
        return entry;
    }

    private static Map<String, Object> structure(final Structure structure) {
        final var map = new TreeMap<String, Object>();
        for (final var key : structure.keys()) {
            map.put(key, value(structure.get(key)));
        }
        return map;
    }

    private static Map<String, Object> value(final Value value) {
        final var entry = new LinkedHashMap<String, Object>();
        switch (value) {
            case StringConstant constant -> entry.put("s", constant.value());
            case NumericConstant constant when constant.floating() ->
                    entry.put("f", constant.asDouble());
            case NumericConstant constant -> entry.put("i", constant.asLong());
            case BooleanConstant constant -> entry.put("b", constant.value());
            case Variable variable -> entry.put("v", variable.name());
            case Structure structure -> entry.put("m", structure(structure));
            case Binding binding -> {
                final var inner = new LinkedHashMap<String, Object>();
                inner.put("text", binding.text());
                inner.put("features", structure(binding.features()));
                entry.put("binding", inner);
            }
            case FeaturePath path -> entry.put("path", List.copyOf(path.parts()));
        }
        return entry;
    }

    /**
     * Sorts entries by their canonical JSON form, which gives a stable order independent of
     * HashMap iteration and lattice construction order.
     */
    private static List<Object> sorted(final List<? extends Map<String, Object>> entries) {
        return entries.stream()
                .sorted(Comparator.comparing(CanonicalJson::write))
                .map(entry -> (Object) entry)
                .toList();
    }

    private static List<String> inputs(final Path path) throws IOException {
        final var content = Files.readString(path, StandardCharsets.UTF_8);
        final var lines = new ArrayList<>(List.of(content.split("\n", -1)));
        if (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        return lines.stream().map(FixtureWriterTests::unescape).toList();
    }

    private static String unescape(final String line) {
        final var builder = new StringBuilder();
        for (var i = 0; i < line.length(); i++) {
            final var c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                final var next = line.charAt(++i);
                switch (next) {
                    case 'n' -> builder.append('\n');
                    case 't' -> builder.append('\t');
                    case '\\' -> builder.append('\\');
                    default -> builder.append(c).append(next);
                }
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
