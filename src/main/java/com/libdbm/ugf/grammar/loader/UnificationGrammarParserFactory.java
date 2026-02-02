package com.libdbm.ugf.grammar.loader;

import com.libdbm.ugf.UnificationGrammarLexer;
import com.libdbm.ugf.UnificationGrammarParser;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarLinter;
import com.libdbm.ugf.parser.ModuleResolver;
import org.antlr.v4.runtime.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating UnificationGrammar parsers from files or strings.
 *
 * <p>By default, grammars are both parsed and validated. Use {@link #unvalidated} to skip
 * validation.
 */
public final class UnificationGrammarParserFactory {

    private UnificationGrammarParserFactory() {
    }

    /**
     * Parse and validate a unification grammar from a file.
     *
     * @throws GrammarSyntaxException     if the grammar contains syntax errors
     * @throws GrammarValidationException if validation fails (e.g., undefined nonterminals)
     */
    public static Grammar parse(final Path path) throws IOException {
        final var content = Files.readString(path);
        return parse(content, path.toString());
    }

    /**
     * Parse and validate a unification grammar from a string.
     *
     * @throws GrammarSyntaxException     if the grammar contains syntax errors
     * @throws GrammarValidationException if validation fails (e.g., undefined nonterminals)
     */
    public static Grammar parse(final String content) {
        return parse(content, "<string>");
    }

    /**
     * Parse a grammar without validation. Use this when you need to work with incomplete or
     * intentionally invalid grammars (e.g., during grammar development or testing).
     *
     * @throws GrammarSyntaxException if the grammar contains syntax errors
     */
    public static Grammar unvalidated(final Path path) throws IOException {
        final var content = Files.readString(path);
        return unvalidated(content, path.toString());
    }

    /**
     * Parse a grammar without validation. Use this when you need to work with incomplete or
     * intentionally invalid grammars (e.g., during grammar development or testing).
     *
     * @throws GrammarSyntaxException if the grammar contains syntax errors
     */
    public static Grammar unvalidated(final String content) {
        return unvalidated(content, "<string>");
    }

    /**
     * Parse a grammar and resolve all imports. Uses the grammar file's parent directory as the search
     * path. Validation is performed on the final resolved grammar.
     *
     * @throws GrammarSyntaxException     if the grammar contains syntax errors
     * @throws GrammarValidationException if validation fails on the resolved grammar
     * @throws IOException                if module resolution fails
     */
    public static Grammar parseWithImports(final Path path) throws IOException {
        // Use unvalidated parsing since imports aren't resolved yet
        final var grammar = unvalidated(path);
        final var resolver = new ModuleResolver(List.of(path.getParent()));
        final var resolved = resolver.resolve(grammar);
        // Validate the fully resolved grammar
        validate(resolved, path.toString());
        return resolved;
    }

    /**
     * Parse a grammar and resolve all imports with custom search paths. Validation is performed on
     * the final resolved grammar.
     *
     * @throws GrammarSyntaxException     if the grammar contains syntax errors
     * @throws GrammarValidationException if validation fails on the resolved grammar
     * @throws IOException                if module resolution fails
     */
    public static Grammar parseWithImports(final Path path, final List<Path> search)
            throws IOException {
        final var grammar = unvalidated(path);
        final var resolver = new ModuleResolver(search);
        final var resolved = resolver.resolve(grammar);
        validate(resolved, path.toString());
        return resolved;
    }

    /**
     * Parse a grammar and resolve all imports with custom search paths.
     *
     * @throws GrammarSyntaxException     if the grammar contains syntax errors
     * @throws GrammarValidationException if validation fails
     * @throws IOException                if module resolution fails
     */
    public static Grammar parseWithImports(final Path path, final Path... search) throws IOException {
        return parseWithImports(path, List.of(search));
    }

    private static Grammar parse(final String content, final String source) {
        final var grammar = unvalidated(content, source);
        validate(grammar, source);
        return grammar;
    }

    private static Grammar unvalidated(final String content, final String source) {
        final var input = CharStreams.fromString(content);
        final var lexer = new UnificationGrammarLexer(input);
        final var tokens = new CommonTokenStream(lexer);
        final var parser = new UnificationGrammarParser(tokens);

        // Collect syntax errors
        final var errors = new SyntaxErrorCollector();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);

        final var tree = parser.grammarFile();

        // Throw if syntax errors occurred
        if (!errors.errors.isEmpty()) {
            throw new GrammarSyntaxException(format(source, errors.errors));
        }

        return UnificationGrammarVisitorImpl.build(tree);
    }

    private static void validate(final Grammar grammar, final String source) {
        final var report = new GrammarLinter().lint(grammar);
        if (report.hasErrors()) {
            throw new GrammarValidationException(format(source, report));
        }
    }

    private static String format(final String source, final List<String> errors) {
        final var builder = new StringBuilder();
        builder.append("Syntax errors in ").append(source).append(":\n");
        for (final var error : errors) {
            builder.append("  ").append(error).append("\n");
        }
        return builder.toString().trim();
    }

    private static String format(final String source, final GrammarLinter.LintReport report) {
        final var builder = new StringBuilder();
        builder.append("Validation errors in ").append(source).append(":\n");
        for (final var error : report.errors()) {
            builder.append("  ").append(error.format()).append("\n");
        }
        return builder.toString().trim();
    }

    /**
     * Error listener that collects syntax errors.
     */
    private static class SyntaxErrorCollector extends BaseErrorListener {
        final List<String> errors = new ArrayList<>();

        @Override
        public void syntaxError(
                final Recognizer<?, ?> recognizer,
                final Object symbol,
                final int line,
                final int position,
                final String message,
                final RecognitionException exception) {
            errors.add("line " + line + ":" + position + " " + message);
        }
    }
}
