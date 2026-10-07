package com.libdbm.ugf.parser;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.compiler.Element;
import com.libdbm.ugf.compiler.Production;
import com.libdbm.ugf.constraints.Environment;
import com.libdbm.ugf.constraints.Evaluator;
import com.libdbm.ugf.constraints.Verdict;
import com.libdbm.ugf.features.*;
import com.libdbm.ugf.lexer.Edge;
import com.libdbm.ugf.lexer.Graph;
import com.libdbm.ugf.lexer.Node;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One parse: the chart, agenda and counters for a single input (PAR-5). Not thread-safe; a {@link
 * Parser} creates one per call.
 *
 * <p>Nodes are processed in topological order. Each node's chart indexes waiting states by the
 * symbol they expect and completed states by symbol and origin, so completion joins a waiting state
 * with a constituent whichever arrives first (PAR-1). A complete state's constraints are evaluated
 * once, when the first state with its key completes (PAR-3).
 */
final class Session {

  private static final Logger LOGGER = LoggerFactory.getLogger(Session.class);

  private final Parser parser;
  private final Memo memo;

  /** The auxiliary root production: the start symbol, or the symbol a caller asked for. */
  private final Production root;

  private final Graph graph;
  private final String input;
  private final long started;
  private final List<Chart> charts = new ArrayList<>();
  private final ParseObserver observer;
  private final DiagnosticCollector collector;
  private final Map<Edge, Structure> renamed = new IdentityHashMap<>();
  private final Map<Integer, Structure> following = new HashMap<>();
  private List<Structure> positions;
  private final long deadline;
  private Stop stop;
  private long fresh;
  private long states;
  private long linked;
  private long built;
  private long checks;
  private long processed;
  private long completions;
  private long unifications;
  private long evaluations;

  Session(
      final Parser parser,
      final Production root,
      final Graph graph,
      final String input,
      final long started) {
    this.parser = parser;
    this.root = root;
    this.graph = graph;
    this.input = input;
    this.started = started;
    final var limit = parser.options.limits().deadline();
    this.deadline = limit.isZero() ? 0 : started + limit.toNanos();
    this.observer = parser.options.observer();
    this.collector =
        new DiagnosticCollector(
            parser.compiled,
            graph,
            root,
            id -> charts.get(id).states.values(),
            parser.options.diagnostics(),
            parser.options.limits().diagnostics());
    this.memo = new Memo(parser.compiled.predicates());
    for (var i = 0; i < graph.nodes().size(); i++) {
      charts.add(new Chart());
    }
  }

  /** Whether {@code links} already holds a link to the same parts, compared by identity. */
  private static boolean contains(final List<State.Link> links, final State.Link link) {
    for (final var other : links) {
      if (other.previous() == link.previous() && Child.same(other.child(), link.child())) {
        return true;
      }
    }
    return false;
  }

  /**
   * The production's variables (S-F5) with their bindings substituted, for constraint arguments.
   */
  private static Map<String, Value> variables(final Bindings bindings) {
    final var values = new HashMap<String, Value>();
    bindings
        .values()
        .forEach((name, value) -> values.put(name, Unifier.substitute(value, bindings)));
    return values;
  }

  // ---------------------------------------------------------------- Earley operations

  /** Appends {@code suffix} to the name of every variable in {@code structure}. */
  private static Structure rename(final Structure structure, final String suffix) {
    return (Structure) Values.rename(structure, variable -> new Variable(variable.name() + suffix));
  }

  /** Token texts along a state's first derivation, for graphs without an input string. */
  private static void leaves(
      final State state, final List<String> tokens, final Set<State> visited) {
    final var stack = new ArrayDeque<Child>();
    stack.push(state);
    while (!stack.isEmpty()) {
      switch (stack.pop()) {
        case Child.Token(var edge) -> tokens.add(edge.text());
        case State current -> {
          if (!visited.add(current)) {
            continue;
          }
          // Children are pushed last to first, so they are visited in order.
          for (var step = current; !step.links.isEmpty(); step = step.links.getFirst().previous()) {
            stack.push(step.links.getFirst().child());
          }
        }
      }
    }
  }

  private static Stop overflow() {
    return new Stop(Stop.PENALTY, -1, -1, "a penalty sum overflowed 64 bits (S-C10)");
  }

  /** Whether a token edge can be advanced over as {@code element}. */
  static boolean matches(final Element element, final Edge edge) {
    return switch (element) {
      case Element.Symbol symbol -> symbol.name().equals(edge.category());
      case Element.Terminal terminal -> terminal.category().equals(edge.category());
      case Element.Token token -> !Edge.ERROR.equals(edge.category());
    };
  }

  ParseResult run() {
    for (final var edge : graph.edges()) {
      if (Edge.ERROR.equals(edge.category())) {
        collector.tokenization(edge);
      }
    }
    final var start = graph.start();
    if (observer != ParseObserver.NOOP) {
      observer.onStart(
          new ParseEvents.Start(graph, ((Element.Symbol) root.rhs().getFirst()).name()));
    }
    add(new State(root, 0, start, start, Bindings.EMPTY, Map.of(), -1, 0));
    for (final var node : graph.nodes()) {
      if (Thread.currentThread().isInterrupted()) {
        stop = new Stop(Stop.INTERRUPT, -1, -1, "the parsing thread was interrupted");
        return result(Outcome.CANCELLED, null, 0, false);
      }
      final var chart = charts.get(node.id());
      while (!chart.agenda.isEmpty() && stop == null) {
        final var state = chart.agenda.poll();
        if (state.superseded) {
          continue;
        }
        processed++;
        if (halted()) {
          break;
        }
        final var limit = parser.options.limits().agenda();
        if (limit > 0 && processed > limit) {
          stop =
              new Stop(
                  Stop.AGENDA,
                  processed,
                  limit,
                  "processed " + processed + " agenda entries, more than the limit of " + limit);
          break;
        }
        process(state, node);
      }
      if (stop != null) {
        return result(stopped(), null, 0, false);
      }
      if (observer != ParseObserver.NOOP) {
        observer.onPosition(new ParseEvents.Position(node.id(), chart.states.size()));
        for (final var edge : graph.edges(node)) {
          if (Edge.ERROR.equals(edge.category())) {
            final var expected = new LinkedHashSet<String>();
            chart.states.values().stream()
                .filter(state -> !state.complete())
                .forEach(state -> expected.add(DiagnosticCollector.describe(state.next())));
            observer.onUnexpected(new ParseEvents.Unexpected(node.id(), edge.text(), expected));
          }
        }
      }
    }
    return select();
  }

  private void process(final State state, final Node node) {
    if (state.complete()) {
      complete(state, node);
      return;
    }
    final var chart = charts.get(node.id());
    switch (state.next()) {
      case Element.Symbol symbol -> {
        chart.waiting.computeIfAbsent(symbol.name(), name -> new ArrayList<>()).add(state);
        predict(symbol.name(), node);
        // Constituents of the symbol that start and end here (nullable) arrived first.
        final var empty =
            chart
                .completed
                .getOrDefault(symbol.name(), Map.of())
                .getOrDefault(node.id(), List.of());
        for (final var constituent : List.copyOf(empty)) {
          if (!constituent.superseded) {
            advance(state, constituent, node);
          }
        }
        scan(state, node);
      }
      case Element.Terminal terminal -> scan(state, node);
      case Element.Token token -> scan(state, node);
    }
  }

  private void predict(final String symbol, final Node node) {
    if (!charts.get(node.id()).predicted.add(symbol)) {
      return;
    }
    for (final var production : parser.compiled.productions(symbol)) {
      if (observer != ParseObserver.NOOP) {
        observer.onPredict(new ParseEvents.Predict(node.id(), symbol, production.id()));
      }
      add(new State(production, 0, node, node, Bindings.EMPTY, Map.of(), -1, 0));
    }
  }

  private void scan(final State state, final Node node) {
    final var element = state.next();
    for (final var edge : graph.edges(node)) {
      if (matches(element, edge)) {
        if (observer != ParseObserver.NOOP) {
          observer.onScan(new ParseEvents.Scan(node.id(), edge.category(), edge.text()));
        }
        advance(state, new Child.Token(edge), edge.to());
      }
    }
  }

  private void complete(final State constituent, final Node node) {
    final var chart = charts.get(node.id());
    if (observer != ParseObserver.NOOP && constituent.production != root) {
      observer.onComplete(
          new ParseEvents.Complete(
              constituent.origin.id(),
              node.id(),
              constituent.symbol(),
              constituent.production.id(),
              constituent.penalty));
    }
    chart
        .completed
        .computeIfAbsent(constituent.symbol(), symbol -> new HashMap<>())
        .computeIfAbsent(constituent.origin.id(), origin -> new ArrayList<>())
        .add(constituent);
    final var waiting =
        charts.get(constituent.origin.id()).waiting.getOrDefault(constituent.symbol(), List.of());
    for (final var state : List.copyOf(waiting)) {
      if (!state.superseded) {
        advance(state, constituent, node);
      }
    }
  }

  /** Advances {@code state} over {@code child} to {@code target}. */
  private void advance(final State state, final Child child, final Node target) {
    if (stop != null) {
      return;
    }
    completions++;
    final var element = state.next();
    final Structure childFeatures;
    final long childPenalty;
    final int childStart;
    switch (child) {
      case State constituent -> {
        childFeatures = constituent.features;
        childPenalty = constituent.penalty;
        childStart = constituent.start;
      }
      case Child.Token(var edge) -> {
        childFeatures = features(edge);
        childPenalty = edge.cost();
        childStart = edge.start();
      }
    }

    // S-F5: the features written on the element unify with the child's, under this use's bindings.
    var bindings = state.bindings;
    if (element instanceof Element.Symbol symbol && !symbol.features().isEmpty()) {
      unifications++;
      final var unified = Unifier.unify(symbol.features(), childFeatures, state.bindings);
      if (observer != ParseObserver.NOOP) {
        observer.onUnification(
            new ParseEvents.Unification(
                target.id(),
                state.production.features(),
                childFeatures,
                symbol.features(),
                unified
                    .map(unification -> Optional.of(unification.value()))
                    .orElse(Optional.empty()),
                unified.map(unification -> unification.bindings().values()).orElse(Map.of())));
      }
      if (!(unified
          instanceof Result.Success<Unification<Structure>, ErrorDetails>(var unification))) {
        if (unified instanceof Result.Failure<Unification<Structure>, ErrorDetails>(var error)) {
          collector.unification(
              symbol.name(),
              state.production.features(),
              childFeatures,
              symbol.features(),
              error.message(),
              target,
              childStart);
        }
        return;
      }
      bindings = unification.bindings();
    }

    var labels = state.labels;
    if (element.label() != null
        && parser.referenced(state.production.id()).contains(element.label())) {
      final var copied = new HashMap<>(labels);
      copied.put(element.label(), Binding.of(text(child), childFeatures));
      labels = copied;
    }

    final long penalty;
    try {
      penalty = Math.addExact(state.penalty, childPenalty);
    } catch (final ArithmeticException exception) {
      stop = overflow();
      return;
    }
    final var next =
        new State(
            state.production,
            state.dot + 1,
            state.origin,
            target,
            bindings,
            labels,
            state.start >= 0 ? state.start : childStart,
            penalty);
    next.links.add(new State.Link(state, child));
    add(next);
  }

  /** Counts a created or replacing state; false (and {@link #stop} set) past the state limit. */
  private boolean created() {
    states++;
    final var limit = parser.options.limits().states();
    if (limit > 0 && states > limit) {
      stop =
          new Stop(
              Stop.STATES,
              states,
              limit,
              "created " + states + " chart states, more than the limit of " + limit);
      return false;
    }
    return true;
  }

  /** Counts {@code count} recorded links; false (and {@link #stop} set) past the link limit. */
  private boolean linked(final int count) {
    linked += count;
    final var limit = parser.options.limits().links();
    if (limit > 0 && linked > limit) {
      stop =
          new Stop(
              Stop.LINKS,
              linked,
              limit,
              "recorded " + linked + " packed derivations, more than the limit of " + limit);
      return false;
    }
    return true;
  }

  /**
   * Checks interruption and the deadline every 1,024 calls, setting {@link #stop}; true when the
   * parse must end.
   */
  private boolean halted() {
    if ((++checks & 1023) != 0) {
      return false;
    }
    if (Thread.currentThread().isInterrupted()) {
      stop = new Stop(Stop.INTERRUPT, -1, -1, "the parsing thread was interrupted");
      return true;
    }
    if (deadline != 0 && System.nanoTime() - deadline > 0) {
      final var elapsed = (System.nanoTime() - started) / 1_000_000;
      final var allowed = parser.options.limits().deadline().toMillis();
      stop =
          new Stop(
              Stop.DEADLINE,
              elapsed,
              allowed,
              "ran " + elapsed + " ms, longer than the deadline of " + allowed + " ms");
      return true;
    }
    return false;
  }

  /** The outcome for {@link #stop}: an interruption is a cancellation, anything else a limit. */
  private Outcome stopped() {
    return Stop.INTERRUPT.equals(stop.resource()) ? Outcome.CANCELLED : Outcome.LIMIT;
  }

  /** Adds a state to its end node's chart, keeping only the cheapest of equivalent states. */
  private void add(final State state) {
    final var chart = charts.get(state.end.id());
    final var key = state.key(parser.canonical);
    final var existing = chart.states.get(key);
    if (state.complete()) {
      if (existing != null) {
        // Same key, same span: same features and the same constraint verdict (PAR-3).
        state.features = existing.features;
        state.own = existing.own;
      } else if (!finish(state)) {
        return;
      }
      try {
        state.penalty = Math.addExact(state.penalty, state.own);
      } catch (final ArithmeticException exception) {
        stop = overflow();
        return;
      }
    }
    if (existing == null) {
      if (!created() || !linked(state.links.size())) {
        return;
      }
      chart.states.put(key, state);
      chart.agenda.add(state);
    } else if (state.penalty < existing.penalty) {
      if (!created() || !linked(state.links.size())) {
        return;
      }
      existing.superseded = true;
      chart.states.put(key, state);
      chart.agenda.add(state);
    } else if (state.penalty == existing.penalty) {
      for (final var link : state.links) {
        if (!contains(existing.links, link)) {
          if (!linked(1)) {
            return;
          }
          existing.links.add(link);
        }
      }
    }
  }

  /**
   * The token indexes at which {@code node} is reached, one feature each (S-C7): the number of
   * tokens on every path from the start node. Computed for the whole graph on first use.
   */
  private Structure positions(final Node node) {
    if (positions == null) {
      // Node ids are a topological order, so every predecessor is visited first.
      final var indexes = new ArrayList<Set<Integer>>();
      graph.nodes().forEach(ignored -> indexes.add(new TreeSet<>()));
      indexes.get(graph.start().id()).add(0);
      for (final var from : graph.nodes()) {
        for (final var edge : graph.edges(from)) {
          for (final var index : indexes.get(from.id())) {
            indexes.get(edge.to().id()).add(index + 1);
          }
        }
      }
      positions =
          indexes.stream()
              .map(
                  set -> {
                    final var builder = Structure.builder();
                    set.forEach(index -> builder.with(index.toString(), BooleanConstant.of(true)));
                    return builder.build();
                  })
              .toList();
    }
    return positions.get(node.id());
  }

  /** The categories of the tokens leaving {@code node}, one feature each. */
  private Structure next(final Node node) {
    return following.computeIfAbsent(
        node.id(),
        id -> {
          final var builder = Structure.builder();
          for (final var edge : graph.edges(node)) {
            builder.with(edge.category(), BooleanConstant.of(true));
          }
          return builder.build();
        });
  }

  /**
   * Evaluates a newly complete state's constraints and sets {@link State#own}. Returns false if a
   * required expression is false (S-C3).
   */
  private boolean finish(final State state) {
    final var production = state.production;
    // S-F5: the constituent's features are its left-hand features under the bindings, with the
    // remaining variables renamed apart from every other constituent's.
    state.features =
        rename(
            Unifier.unify(production.features(), Structure.EMPTY, state.bindings)
                .map(Unification::value)
                .orElse(production.features()),
            "'" + fresh++);
    var own = production.cost();
    if (!production.plan().isEmpty()) {
      evaluations++;
      // Later entries replace earlier ones, as successive Environment.with calls would.
      final var values = new HashMap<>(variables(state.bindings));
      values.putAll(state.labels);
      values.put(Environment.POSITION, positions(state.origin));
      values.put(Environment.END, BooleanConstant.of(graph.isFinal(state.end)));
      values.put(Environment.NEXT, next(state.end));
      values.put(production.symbol(), Binding.of(text(state), state.features));
      final var environment =
          Environment.of(parser.compiled.predicates())
              // S-C7: the first token's start, after skipped whitespace; the node for an empty one.
              .lexical(state.origin.states(), state.start < 0 ? state.origin.offset() : state.start)
              .with(values);
      final var verdict =
          Evaluator.evaluate(production.plan(), call -> memo.test(environment, call));
      if (observer != ParseObserver.NOOP) {
        observer.onConstraint(
            new ParseEvents.ConstraintEval(
                state.end.id(),
                production.symbol(),
                DiagnosticCollector.display(production.plan()),
                verdict instanceof Verdict.Accepted,
                verdict instanceof Verdict.Accepted(long penalty) ? penalty : 0));
      }
      switch (verdict) {
        case Verdict.Rejected(var failed) -> {
          collector.constraint(state, failed);
          return false;
        }
        case Verdict.Overflow overflow -> {
          stop = overflow();
          return false;
        }
        case Verdict.Accepted(long penalty) -> {
          try {
            own = Math.addExact(own, penalty);
          } catch (final ArithmeticException exception) {
            stop = overflow();
            return false;
          }
        }
      }
    }
    state.own = own;
    return true;
  }

  /** A token's features, with any variables renamed apart per edge (S-F5). */
  private Structure features(final Edge edge) {
    return renamed.computeIfAbsent(edge, key -> rename(key.features(), "'e" + key.id()));
  }

  // ---------------------------------------------------------------- selection

  /**
   * A constituent's text: the input from its first token's start to its last token's end (S-C9).
   */
  private String text(final Child child) {
    return switch (child) {
      case Child.Token(var edge) ->
          input != null ? input.substring(edge.start(), edge.end()) : edge.text();
      case State state -> {
        if (state.start < 0) {
          yield "";
        }
        if (input != null) {
          yield input.substring(state.start, state.end.offset());
        }
        final var tokens = new ArrayList<String>();
        leaves(state, tokens, Collections.newSetFromMap(new IdentityHashMap<>()));
        yield String.join(" ", tokens);
      }
    };
  }

  private ParseResult select() {
    final var roots = new ArrayList<State>();
    for (final var node : graph.nodes()) {
      if (graph.isFinal(node)) {
        for (final var state : charts.get(node.id()).states.values()) {
          if (state.production == root && state.complete() && !state.superseded) {
            roots.add(state);
          }
        }
      }
    }
    if (roots.isEmpty()) {
      collector.rejected();
      return result(Outcome.REJECTED, null, 0, false);
    }
    try {
      final var penalty = roots.stream().mapToLong(state -> state.penalty).min().orElseThrow();
      final var tied = roots.stream().filter(state -> state.penalty == penalty).toList();
      final var selector = new ForestSelector(graph, this::halted, this::grow);
      final var chosen = selector.choose(tied);
      final var ambiguous = tied.size() > 1 || selector.ambiguous(chosen);
      final var tree = selector.tree(chosen);
      if (selector.tied()) {
        LOGGER.warn(
            "Ambiguous parse of {} at {}..{}: equal-length tokens of the same category and state"
                + " were chosen by declaration order, or the caller's order for caller tokens (S-P3)",
            parser.compiled.start(),
            tree.start(),
            tree.end());
      }
      if (ambiguous && collector.enabled()) {
        if (tied.size() > 1) {
          collector.ambiguity(
              parser.compiled.start(), new ParseDiagnostics.Span(0, tree.start(), tree.end()));
        }
        selector.ties(chosen, root, collector::ambiguity);
      }
      return result(Outcome.ACCEPTED, tree, penalty, ambiguous);
    } catch (final ForestSelector.Exhausted exhausted) {
      return result(stopped(), null, 0, false);
    }
  }

  /** Counts a tree node; past the tree limit sets {@link #stop} and returns false. */
  private boolean grow() {
    built++;
    final var limit = parser.options.limits().tree();
    if (limit > 0 && built > limit) {
      stop =
          new Stop(Stop.TREE, built, limit, "the selected tree has more than " + limit + " nodes");
      return false;
    }
    return true;
  }

  private ParseResult result(
      final Outcome outcome, final ParseTree tree, final long penalty, final boolean ambiguous) {
    final var elapsed = System.nanoTime() - started;
    final var statistics =
        new Statistics(
            graph.nodes().size(),
            graph.edges().size(),
            states,
            processed,
            completions,
            unifications,
            evaluations,
            memo.calls(),
            memo.hits(),
            elapsed);
    final var result =
        new ParseResult(
            outcome, tree, penalty, ambiguous, collector.diagnostics(), statistics, stop);
    if (observer != ParseObserver.NOOP) {
      observer.onEnd(new ParseEvents.End(result, Duration.ofNanos(elapsed)));
    }
    return result;
  }

  /** The chart for one graph node. */
  private static final class Chart {
    private final Map<State.Key, State> states = new LinkedHashMap<>();
    private final Map<String, List<State>> waiting = new HashMap<>();
    private final Map<String, Map<Integer, List<State>>> completed = new HashMap<>();
    private final ArrayDeque<State> agenda = new ArrayDeque<>();
    private final Set<String> predicted = new HashSet<>();
  }
}
