package com.libdbm.ugf.grammar.loader;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.UnificationGrammarLexer;
import com.libdbm.ugf.UnificationGrammarParser;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarLinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import org.antlr.v4.runtime.*;

/**
 * Loads unification grammars from files or strings.
 *
 * <p>By default, grammars are both parsed and validated. Use {@link #unvalidated} to skip
 * validation. Every method returns a {@link Result}; a failure's {@link ErrorDetails#code()} is one
 * of {@link #SYNTAX}, {@link #VALIDATION}, {@link #IO}, or a {@link ModuleResolver} import code,
 * and its {@link ErrorDetails#issues()} lists each individual error.
 */
public final class UnificationGrammarParserFactory {

  /** The grammar text is not valid grammar syntax. */
  public static final String SYNTAX = "grammar.syntax";

  /** The grammar parsed but failed validation, for example an undefined nonterminal. */
  public static final String VALIDATION = "grammar.validation";

  /** The grammar file could not be read. */
  public static final String IO = "grammar.io";

  private UnificationGrammarParserFactory() {}

  /** Parses and validates a grammar file. */
  public static Result<Grammar, ErrorDetails> parse(final Path path) {
    return read(path).flatMap(content -> parse(content, path.toString()));
  }

  /** Parses and validates a grammar string. */
  public static Result<Grammar, ErrorDetails> parse(final String content) {
    return parse(content, "<string>");
  }

  /**
   * Parses a grammar file without validation. Use this when you need to work with incomplete or
   * intentionally invalid grammars (e.g., during grammar development or testing).
   */
  public static Result<Grammar, ErrorDetails> unvalidated(final Path path) {
    return read(path).flatMap(content -> unvalidated(content, path.toString()));
  }

  /**
   * Parses a grammar string without validation. Use this when you need to work with incomplete or
   * intentionally invalid grammars (e.g., during grammar development or testing).
   */
  public static Result<Grammar, ErrorDetails> unvalidated(final String content) {
    return unvalidated(content, "<string>");
  }

  /**
   * Parses a grammar file and resolves its imports, searching the file's own directory. Validation
   * is performed on the final resolved grammar.
   */
  public static Result<Grammar, ErrorDetails> parseWithImports(final Path path) {
    // A bare filename has no parent until made absolute (IMP-2).
    final var directory = path.toAbsolutePath().getParent();
    return parseWithImports(path, new ModuleResolver(List.of(directory)));
  }

  /**
   * Parses a grammar file and resolves its imports with custom search paths. Validation is
   * performed on the final resolved grammar.
   */
  public static Result<Grammar, ErrorDetails> parseWithImports(
      final Path path, final List<Path> search) {
    return parseWithImports(path, new ModuleResolver(search));
  }

  /**
   * Parses a grammar file and resolves its imports with custom search paths. Validation is
   * performed on the final resolved grammar.
   */
  public static Result<Grammar, ErrorDetails> parseWithImports(
      final Path path, final Path... search) {
    return parseWithImports(path, List.of(search));
  }

  private static Result<Grammar, ErrorDetails> parseWithImports(
      final Path path, final ModuleResolver resolver) {
    // Imports are not resolved yet, so the root is parsed without validation.
    return unvalidated(path)
        .flatMap(grammar -> resolver.resolve(grammar, path.toAbsolutePath().getParent()))
        .flatMap(resolved -> validate(resolved, path.toString()));
  }

  private static Result<String, ErrorDetails> read(final Path path) {
    try {
      return Result.success(Files.readString(path));
    } catch (final IOException exception) {
      return Result.failure(
          ErrorDetails.of(IO, "Cannot read " + path + ": " + exception.getMessage()));
    }
  }

  private static Result<Grammar, ErrorDetails> parse(final String content, final String source) {
    return unvalidated(content, source).flatMap(grammar -> validate(grammar, source));
  }

  private static Result<Grammar, ErrorDetails> unvalidated(
      final String content, final String source) {
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

    if (!errors.errors.isEmpty()) {
      return Result.failure(new ErrorDetails(SYNTAX, "Syntax errors in " + source, errors.errors));
    }

    try {
      return Result.success(UnificationGrammarVisitorImpl.build(tree));
    } catch (final NumberFormatException exception) {
      return Result.failure(
          new ErrorDetails(
              SYNTAX,
              "Syntax errors in " + source,
              List.of("number out of range: " + exception.getMessage())));
    } catch (final PatternSyntaxException exception) {
      return Result.failure(
          new ErrorDetails(
              SYNTAX,
              "Syntax errors in " + source,
              List.of("invalid regex: " + exception.getDescription())));
    } catch (final IllegalArgumentException exception) {
      return Result.failure(
          new ErrorDetails(SYNTAX, "Syntax errors in " + source, List.of(exception.getMessage())));
    }
  }

  private static Result<Grammar, ErrorDetails> validate(
      final Grammar grammar, final String source) {
    final var report = new GrammarLinter().lint(grammar);
    if (report.hasErrors()) {
      final var issues = report.errors().stream().map(GrammarLinter.LintIssue::format).toList();
      return Result.failure(new ErrorDetails(VALIDATION, "Validation errors in " + source, issues));
    }
    return Result.success(grammar);
  }

  /** Error listener that collects syntax errors. */
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
