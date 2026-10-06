package com.libdbm.ugf.parser;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.compiler.Compiler;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import java.nio.file.Path;

/**
 * Convenience entry points that compile a grammar and create a {@link Parser} (PAR-10).
 *
 * <pre>{@code
 * switch (ParserFactory.create(Path.of("grammar.ug"))) {
 *   case Result.Success<Parser, ErrorDetails>(var parser) -> use(parser.parse("input text"));
 *   case Result.Failure<Parser, ErrorDetails>(var error) -> report(error);
 * }
 * }</pre>
 *
 * <p>For control over each step, use {@link UnificationGrammarParserFactory}, {@link Compiler} and
 * {@link Parser#of} directly.
 */
public final class ParserFactory {

  private ParserFactory() {}

  /** Compiles {@code grammar} with the standard predicates and default options. */
  public static Result<Parser, ErrorDetails> create(final Grammar grammar) {
    return create(grammar, Predicates.standard(), Options.DEFAULT);
  }

  /**
   * Compiles {@code grammar} against {@code predicates} and creates a parser with {@code options}.
   */
  public static Result<Parser, ErrorDetails> create(
      final Grammar grammar, final Predicates predicates, final Options options) {
    return Compiler.compile(grammar, predicates).map(compiled -> Parser.of(compiled, options));
  }

  /**
   * Loads a grammar file, resolving its imports, and creates a parser with the standard predicates.
   */
  public static Result<Parser, ErrorDetails> create(final Path path) {
    return UnificationGrammarParserFactory.parseWithImports(path).flatMap(ParserFactory::create);
  }
}
