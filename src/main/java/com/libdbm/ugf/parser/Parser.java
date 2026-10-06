package com.libdbm.ugf.parser;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.compiler.Compiled;
import com.libdbm.ugf.compiler.Element;
import com.libdbm.ugf.compiler.Production;
import com.libdbm.ugf.constraints.Expression;
import com.libdbm.ugf.constraints.Plan;
import com.libdbm.ugf.features.FeaturePath;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Variable;
import com.libdbm.ugf.lexer.Graph;
import com.libdbm.ugf.lexer.Lexer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An Earley parser over the token graph of a {@link Compiled} grammar (S-P1 to S-P8).
 *
 * <p>A parser holds only immutable configuration and may be shared between threads; each call to
 * {@link #parse} runs in its own session (PAR-5). Its predicates, enhancer and observer are shared
 * by every session, so they must be safe for concurrent use when the parser is.
 */
public final class Parser {

  final Compiled compiled;
  final Options options;
  final Production root;
  private final Lexer lexer;
  private final Map<Integer, Set<String>> referenced;

  /** Whether chart states are identified up to renamed variables (S-P9); false only in tests. */
  final boolean canonical;

  private Parser(final Compiled compiled, final Options options, final boolean canonical) {
    this.compiled = compiled;
    this.options = options;
    this.canonical = canonical;
    this.lexer = new Lexer(compiled);
    // The start symbol is wrapped in an auxiliary root, so a lexical start symbol works too.
    this.root =
        new Production(
            -1,
            "",
            Structure.EMPTY,
            List.of(new Element.Symbol(compiled.start(), null, Structure.EMPTY)),
            Plan.EMPTY,
            0,
            true);
    final var labels = new HashMap<Integer, Set<String>>();
    for (final var production : compiled.productions()) {
      labels.put(production.id(), referenced(production));
    }
    this.referenced = Map.copyOf(labels);
  }

  public static Parser of(final Compiled compiled, final Options options) {
    return new Parser(compiled, options, true);
  }

  /** A parser that compares raw bindings when {@code canonical} is false (for tests, S-P9). */
  static Parser of(final Compiled compiled, final Options options, final boolean canonical) {
    return new Parser(compiled, options, canonical);
  }

  public static Parser of(final Compiled compiled) {
    return of(compiled, Options.DEFAULT);
  }

  public Compiled compiled() {
    return compiled;
  }

  /** Lexes {@code input} with the grammar's lexemes and parses the token graph. */
  public ParseResult parse(final String input) {
    return parse(root, input, System.nanoTime());
  }

  /** The token graph for {@code input}, from the same lexer {@link #parse(String)} uses (LEX-9). */
  public Result<Graph, ErrorDetails> tokenize(final String input) {
    return lexer.tokenize(input);
  }

  /** Parses a token graph, for example one built from caller-supplied tokens (S-L6). */
  public ParseResult parse(final Graph graph) {
    return run(root, graph, null, System.nanoTime());
  }

  /**
   * Parses {@code input} as {@code symbol} rather than the start symbol, requiring the root
   * constituent's features to unify with {@code features} (used to verify generated text, S-N1).
   */
  public ParseResult parse(final String input, final String symbol, final Structure features) {
    final var started = System.nanoTime();
    final var top =
        new Production(
            -1,
            "",
            Structure.EMPTY,
            List.of(new Element.Symbol(symbol, null, features)),
            Plan.EMPTY,
            0,
            true);
    return parse(top, input, started);
  }

  /** Lexes {@code input} within the limits and parses it as {@code top}. */
  private ParseResult parse(final Production top, final String input, final long started) {
    final var limits = options.limits();
    final var deadline = limits.deadline().isZero() ? 0 : started + limits.deadline().toNanos();
    return switch (lexer.tokenize(input, limits.nodes(), limits.depth(), deadline)) {
      case Result.Success<Graph, ErrorDetails>(var graph) -> run(top, graph, input, started);
      case Result.Failure<Graph, ErrorDetails>(var error) -> stopped(error, started);
    };
  }

  /** Enhances {@code graph}, holds it to the node limit and parses it as {@code top}. */
  private ParseResult run(
      final Production top, final Graph graph, final String input, final long started) {
    final var enhanced = options.enhancer().enhance(graph);
    final var nodes = options.limits().nodes();
    if (nodes > 0 && enhanced.nodes().size() > nodes) {
      return stopped(
          Outcome.LIMIT,
          new Stop(
              Stop.NODES,
              enhanced.nodes().size(),
              nodes,
              "the token graph has " + enhanced.nodes().size() + " nodes, more than " + nodes),
          started);
    }
    return new Session(this, top, enhanced, input, started).run();
  }

  /** The result of a parse whose input could not be lexed within the limits. */
  private ParseResult stopped(final ErrorDetails error, final long started) {
    final var limits = options.limits();
    return switch (error.code()) {
      case Lexer.NODES ->
          stopped(
              Outcome.LIMIT,
              new Stop(Stop.NODES, limits.nodes() + 1L, limits.nodes(), error.message()),
              started);
      case Lexer.DEPTH ->
          stopped(
              Outcome.LIMIT,
              new Stop(Stop.DEPTH, limits.depth() + 1L, limits.depth(), error.message()),
              started);
      case Lexer.DEADLINE ->
          stopped(
              Outcome.LIMIT,
              new Stop(
                  Stop.DEADLINE,
                  (System.nanoTime() - started) / 1_000_000,
                  limits.deadline().toMillis(),
                  error.message()),
              started);
      case Lexer.INTERRUPT ->
          stopped(Outcome.CANCELLED, new Stop(Stop.INTERRUPT, -1, -1, error.message()), started);
      case Lexer.OVERFLOW ->
          stopped(Outcome.LIMIT, new Stop(Stop.PENALTY, -1, -1, error.message()), started);
      default -> stopped(Outcome.LIMIT, new Stop(Stop.LEXER, -1, -1, error.message()), started);
    };
  }

  private static ParseResult stopped(final Outcome outcome, final Stop stop, final long started) {
    final var statistics = new Statistics(0, 0, 0, 0, 0, 0, 0, 0, 0, System.nanoTime() - started);
    return new ParseResult(outcome, null, 0, false, null, statistics, stop);
  }

  /** The labels whose bindings the production's constraints read; only these enter state keys. */
  Set<String> referenced(final int production) {
    return referenced.getOrDefault(production, Set.of());
  }

  private static Set<String> referenced(final Production production) {
    final var names = new HashSet<String>();
    production.plan().required().forEach(expression -> names(expression, names));
    production.plan().soft().forEach(group -> names(group.expression(), names));
    final var labels = new HashSet<String>();
    for (final var element : production.rhs()) {
      if (element.label() != null && names.contains(element.label())) {
        labels.add(element.label());
      }
    }
    return Set.copyOf(labels);
  }

  private static void names(final Expression expression, final Set<String> names) {
    switch (expression) {
      case Expression.And and -> and.terms().forEach(term -> names(term, names));
      case Expression.Or or -> or.terms().forEach(term -> names(term, names));
      case Expression.Not not -> names(not.term(), names);
      case Expression.Call call ->
          call.args()
              .forEach(
                  arg -> {
                    switch (arg) {
                      case FeaturePath path -> names.add(path.root());
                      case Variable variable -> names.add(variable.name());
                      default -> {}
                    }
                  });
      case Expression.Literal literal -> {}
      case Expression.Weighted weighted -> names(weighted.term(), names);
    }
  }
}
