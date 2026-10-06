package com.libdbm.ugf.parser;

import com.libdbm.ugf.compiler.Compiled;
import com.libdbm.ugf.compiler.Element;
import com.libdbm.ugf.compiler.Production;
import com.libdbm.ugf.constraints.Expression;
import com.libdbm.ugf.constraints.Plan;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.lexer.Edge;
import com.libdbm.ugf.lexer.Graph;
import com.libdbm.ugf.lexer.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Collects the diagnostics of one parse when they are enabled (PAR-8): tokenization, unification
 * and constraint failures, ambiguities, and for a rejected input the chart as a lattice. At most
 * {@link Limits#diagnostics()} records are kept. Not thread-safe; a session creates one per parse.
 */
final class DiagnosticCollector {

  private final Compiled compiled;
  private final Graph graph;
  private final Production root;
  private final IntFunction<Collection<State>> states;

  /** Null when diagnostics are disabled. */
  private final ParseDiagnostics diagnostics;

  private final int cap;
  private long records;
  private final Map<Integer, List<ParseDiagnostics.PathFailure>> rejections = new HashMap<>();

  /**
   * @param root the auxiliary root production, left out of the lattice
   * @param states the chart states at a node id
   * @param enabled whether diagnostics are collected at all
   * @param cap the most records kept; zero for no limit
   */
  DiagnosticCollector(
      final Compiled compiled,
      final Graph graph,
      final Production root,
      final IntFunction<Collection<State>> states,
      final boolean enabled,
      final int cap) {
    this.compiled = compiled;
    this.graph = graph;
    this.root = root;
    this.states = states;
    this.diagnostics = enabled ? new ParseDiagnostics() : null;
    this.cap = cap;
  }

  /** The diagnostics collected, or null when they are disabled. */
  ParseDiagnostics diagnostics() {
    return diagnostics;
  }

  boolean enabled() {
    return diagnostics != null;
  }

  /** An error edge: text the lexer could not tokenize. */
  void tokenization(final Edge edge) {
    if (record()) {
      diagnostics.recordTokenizationError(
          edge.start(),
          edge.text(),
          "a token",
          new ParseDiagnostics.Span(edge.from().id(), edge.start(), edge.end()));
    }
  }

  /** A child whose features do not unify with those written on its element (S-F5). */
  void unification(
      final String symbol,
      final Structure parent,
      final Structure child,
      final Structure expected,
      final String message,
      final Node target,
      final int start) {
    if (record()) {
      final var from = start >= 0 ? start : target.offset();
      diagnostics.recordUnificationFailure(
          symbol,
          parent,
          child,
          expected,
          message,
          target.id(),
          new ParseDiagnostics.Span(target.id(), from, target.offset()));
    }
  }

  /** A complete state rejected by a required constraint; also kept for the lattice. */
  void constraint(final State state, final Expression failed) {
    if (!record()) {
      return;
    }
    final var production = state.production;
    final var reason = "required constraint is false: " + display(failed);
    final var span = span(state, state.end);
    diagnostics.recordConstraintFailure(
        production.symbol(), display(failed), reason, state.origin.id(), true, span);
    rejections
        .computeIfAbsent(state.end.id(), id -> new ArrayList<>())
        .add(
            new ParseDiagnostics.PathFailure(
                new ParseDiagnostics.LatticeItem(
                    describe(production),
                    state.dot,
                    state.origin.id(),
                    (int) Math.min(Integer.MAX_VALUE, state.penalty),
                    ParseDiagnostics.State.REJECTED,
                    state.bindings.values().toString()),
                span,
                reason,
                ParseDiagnostics.FailureKind.CONSTRAINT));
  }

  /** A constituent with more than one equally cheap derivation (S-P4). */
  void ambiguity(final String symbol, final ParseDiagnostics.Span span) {
    if (record()) {
      diagnostics.recordAmbiguity(symbol, span);
    }
  }

  /** No derivation covers the input: the furthest progress and the chart as a lattice. */
  void rejected() {
    if (record()) {
      final var furthest = furthest();
      diagnostics.recordParsingIssue(
          "no derivation of " + compiled.start() + " covers the input",
          furthest.id(),
          new ParseDiagnostics.Span(furthest.id(), furthest.offset(), furthest.offset()));
      diagnostics.recordLattice(lattice(furthest));
    }
  }

  /** A readable rendering of a plan: required expressions, then weighted soft groups. */
  static String display(final Plan plan) {
    final var parts = new ArrayList<String>();
    plan.required().forEach(expression -> parts.add(display(expression)));
    plan.soft()
        .forEach(group -> parts.add("(" + display(group.expression()) + "):" + group.weight()));
    return String.join(", ", parts);
  }

  /** A readable rendering of a constraint expression for diagnostics. */
  static String display(final Expression expression) {
    return switch (expression) {
      case Expression.And and ->
          String.join(", ", and.terms().stream().map(DiagnosticCollector::display).toList());
      case Expression.Or or ->
          "("
              + String.join(" | ", or.terms().stream().map(DiagnosticCollector::display).toList())
              + ")";
      case Expression.Not not -> "!" + display(not.term());
      case Expression.Call call ->
          call.name()
              + "("
              + String.join(", ", call.args().stream().map(arg -> arg.display()).toList())
              + ")";
      case Expression.Literal literal -> String.valueOf(literal.value());
      case Expression.Weighted weighted ->
          "(" + display(weighted.term()) + "):" + weighted.weight();
    };
  }

  /** True if diagnostics are enabled and below their cap (PAR-8); counts the record. */
  private boolean record() {
    if (diagnostics == null) {
      return false;
    }
    if (cap > 0 && records >= cap) {
      return false;
    }
    records++;
    return true;
  }

  private static ParseDiagnostics.Span span(final State state, final Node end) {
    return new ParseDiagnostics.Span(
        state.origin.id(), state.start >= 0 ? state.start : state.origin.offset(), end.offset());
  }

  /**
   * The chart as a lattice for error reporting: one column per node that has states, listing its
   * items, the elements expected there, and dead ends (items whose next token never arrived). At
   * most {@link Limits#diagnostics()} items and failures are kept; columns are collected from the
   * end backwards, so a truncated snapshot keeps those nearest the furthest progress.
   */
  private ParseDiagnostics.ParseLattice lattice(final Node furthest) {
    final var columns = new ArrayList<ParseDiagnostics.Column>();
    var kept = 0;
    var truncated = false;
    for (final var node : graph.nodes().reversed()) {
      if (cap > 0 && kept >= cap) {
        truncated = true;
        break;
      }
      final var chart = states.apply(node.id());
      // A column with no states can still hold a rejected completion (for example a failed
      // required constraint), which is the reason the parse stopped there.
      if (chart.isEmpty() && !rejections.containsKey(node.id())) {
        continue;
      }
      final var span = new ParseDiagnostics.Span(node.id(), node.offset(), node.offset());
      final var items = new ArrayList<ParseDiagnostics.LatticeItem>();
      final var expected = new LinkedHashSet<String>();
      final var failures = new ArrayList<ParseDiagnostics.PathFailure>();
      for (final var state : chart) {
        if (state.superseded || state.production == root) {
          continue;
        }
        if (cap > 0 && kept >= cap) {
          truncated = true;
          break;
        }
        kept++;
        final var item =
            new ParseDiagnostics.LatticeItem(
                describe(state.production),
                state.dot,
                state.origin.id(),
                (int) Math.min(Integer.MAX_VALUE, state.penalty),
                state.complete() ? ParseDiagnostics.State.COMPLETE : ParseDiagnostics.State.ACTIVE,
                state.complete() ? state.features.display() : state.bindings.values().toString());
        items.add(item);
        if (state.complete()) {
          continue;
        }
        final var next = describe(state.next());
        expected.add(next);
        if (!(state.next() instanceof Element.Symbol symbol && !compiled.lexical(symbol.name()))
            && graph.edges(node).stream().noneMatch(edge -> Session.matches(state.next(), edge))) {
          failures.add(
              new ParseDiagnostics.PathFailure(
                  item, span, "expected " + next, ParseDiagnostics.FailureKind.DEAD_END));
        }
      }
      for (final var rejection : rejections.getOrDefault(node.id(), List.of())) {
        if (cap > 0 && kept >= cap) {
          truncated = true;
          break;
        }
        kept++;
        failures.add(rejection);
      }
      columns.add(
          new ParseDiagnostics.Column(node.id(), span, items, List.copyOf(expected), failures));
    }
    return new ParseDiagnostics.ParseLattice(
        columns.reversed(),
        furthest.id(),
        new ParseDiagnostics.Span(furthest.id(), furthest.offset(), furthest.offset()),
        truncated);
  }

  static String describe(final Production production) {
    final var builder = new StringBuilder(production.symbol()).append(" -->");
    production.rhs().forEach(element -> builder.append(' ').append(describe(element)));
    return builder.toString();
  }

  static String describe(final Element element) {
    return switch (element) {
      case Element.Symbol symbol -> symbol.name();
      case Element.Terminal terminal -> terminal.category();
      case Element.Token token -> "{TOKEN}";
    };
  }

  /** The furthest node any state reached. */
  private Node furthest() {
    for (var i = graph.nodes().size() - 1; i >= 0; i--) {
      if (!states.apply(i).isEmpty()) {
        return graph.nodes().get(i);
      }
    }
    return graph.start();
  }
}
