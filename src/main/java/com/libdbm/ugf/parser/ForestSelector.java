package com.libdbm.ugf.parser;

import com.libdbm.ugf.compiler.Element;
import com.libdbm.ugf.compiler.Production;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.lexer.Edge;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * Chooses among the packed derivations of a finished chart and builds the selected tree (S-P3,
 * S-P4, S-P8). Every traversal uses an explicit stack, so deep derivations cannot exhaust the
 * thread stack. Not thread-safe; a session creates one per parse.
 */
final class ForestSelector {

  /** The best derivation of a state: the chosen link and the keys that order derivations (S-P3). */
  private record Best(State.Link link, Sequence productions, Sequence ends, Sequence edges) {
    private static final Best EMPTY =
        new Best(null, Sequence.EMPTY, Sequence.EMPTY, Sequence.EMPTY);
  }

  /** The best derivation of each state resolved so far. */
  private final Map<State, Best> memo = new IdentityHashMap<>();

  /** States whose best derivation is being resolved, to skip cyclic derivations. */
  private final Set<State> active = Collections.newSetFromMap(new IdentityHashMap<>());

  private final BooleanSupplier halted;
  private final BooleanSupplier grow;

  /**
   * @param halted true when the parse must stop; checked as selection and tree building proceed
   * @param grow counts one tree node; false when the tree limit is exceeded
   */
  ForestSelector(final BooleanSupplier halted, final BooleanSupplier grow) {
    this.halted = halted;
    this.grow = grow;
  }

  /** The tied root whose best derivation comes first (S-P3). */
  State choose(final List<State> tied) {
    var chosen = tied.getFirst();
    for (final var candidate : tied) {
      if (compare(best(candidate), best(chosen)) < 0) {
        chosen = candidate;
      }
    }
    return chosen;
  }

  /** The tree of the chosen root's best derivation. */
  ParseTree tree(final State chosen) {
    return build(chosen, null).getFirst();
  }

  /**
   * The best derivation of {@code state}. Nullable recursion makes the packed derivations cyclic; a
   * link that leads back into a state still being resolved ({@code active}) is skipped, which is
   * safe because a cyclic derivation is never cheaper (S-P9). An explicit stack replaces recursion,
   * visiting links and their parts in the same order, so deep derivations cannot exhaust the stack.
   */
  Best best(final State state) {
    final var known = memo.get(state);
    if (known != null) {
      return known;
    }
    final var stack = new ArrayDeque<Selection>();
    active.add(state);
    stack.push(new Selection(state));
    while (!stack.isEmpty()) {
      if (halted.getAsBoolean()) {
        throw new Exhausted();
      }
      final var frame = stack.peek();
      if (frame.index == frame.state.links.size()) {
        stack.pop();
        active.remove(frame.state);
        memo.put(frame.state, frame.chosen);
        continue;
      }
      final var link = frame.state.links.get(frame.index);
      if (frame.previous == null) {
        if (active.contains(link.previous())
            || link.child() instanceof State constituent && active.contains(constituent)) {
          frame.index++;
          continue;
        }
        frame.previous = memo.get(link.previous());
        if (frame.previous == null) {
          active.add(link.previous());
          stack.push(new Selection(link.previous()));
          continue;
        }
      }
      final var previous = frame.previous;
      final Best candidate;
      switch (link.child()) {
        case State constituent -> {
          final var inner = memo.get(constituent);
          if (inner == null) {
            active.add(constituent);
            stack.push(new Selection(constituent));
            continue;
          }
          var productions = previous.productions();
          var ends = previous.ends();
          if (!constituent.production.auxiliary()) {
            productions = productions.then(constituent.production.id());
            ends = ends.then(constituent.end.offset());
          }
          candidate =
              new Best(
                  link,
                  productions.then(inner.productions()),
                  ends.then(inner.ends()),
                  previous.edges().then(inner.edges()));
        }
        case Child.Token(var edge) ->
            candidate =
                new Best(
                    link,
                    previous.productions(),
                    previous.ends().then(edge.end()),
                    previous.edges().then(edge.id()));
      }
      if (frame.chosen == Best.EMPTY || compare(candidate, frame.chosen) < 0) {
        frame.chosen = candidate;
      }
      frame.previous = null;
      frame.index++;
    }
    return memo.get(state);
  }

  /** A state whose best derivation is being chosen: the next link and the best so far. */
  private static final class Selection {
    private final State state;
    private int index;
    private Best previous;
    private Best chosen = Best.EMPTY;

    Selection(final State state) {
      this.state = state;
    }
  }

  /** Pre-order productions first, then constituent ends, then token order (S-P3). */
  private static int compare(final Best a, final Best b) {
    var result = a.productions().compareTo(b.productions());
    if (result == 0) {
      result = a.ends().compareTo(b.ends());
    }
    return result != 0 ? result : a.edges().compareTo(b.edges());
  }

  /**
   * True if any state on the chosen derivation has more than one equally cheap derivation (S-P4).
   */
  boolean ambiguous(final State state) {
    final var stack = new ArrayDeque<State>();
    stack.push(state);
    while (!stack.isEmpty()) {
      final var current = stack.pop();
      if (current.links.size() > 1) {
        return true;
      }
      final var link = memo.get(current).link();
      if (link != null) {
        stack.push(link.previous());
        if (link.child() instanceof State constituent) {
          stack.push(constituent);
        }
      }
    }
    return false;
  }

  /**
   * Reports each constituent on the chosen derivation that has more than one equally cheap
   * derivation, once per symbol and span; a tie inside an auxiliary node is attributed to the
   * nearest real symbol above it.
   */
  void ties(
      final State chosen,
      final Production root,
      final BiConsumer<String, ParseDiagnostics.Span> report) {
    final var stack = new ArrayDeque<State[]>();
    stack.push(new State[] {chosen, chosen});
    final var seen = new HashSet<String>();
    while (!stack.isEmpty()) {
      final var pair = stack.pop();
      final var state = pair[0];
      final var owner = state.production.auxiliary() ? pair[1] : state;
      if (state.links.size() > 1 && state.complete() && owner.production != root) {
        final var start = owner.start >= 0 ? owner.start : owner.origin.offset();
        if (seen.add(owner.symbol() + "@" + start + ":" + owner.end.offset())) {
          report.accept(
              owner.symbol(),
              new ParseDiagnostics.Span(owner.origin.id(), start, owner.end.offset()));
        }
      }
      final var link = memo.get(state).link();
      if (link != null) {
        stack.push(new State[] {link.previous(), pair[1]});
        if (link.child() instanceof State constituent) {
          stack.push(new State[] {constituent, owner});
        }
      }
    }
  }

  /**
   * The trees for a state: one node, or its children spliced if it is auxiliary (S-P8). An explicit
   * stack replaces recursion, so deep trees cannot exhaust the stack.
   */
  private List<ParseTree> build(final State state, final String label) {
    final var stack = new ArrayDeque<Construction>();
    stack.push(new Construction(state, label, path(state)));
    List<ParseTree> finished = null;
    while (true) {
      final var frame = stack.peek();
      if (finished != null) {
        frame.children.addAll(finished);
        finished = null;
      }
      if (frame.index == frame.path.size()) {
        stack.pop();
        finished = frame.finish();
        if (stack.isEmpty()) {
          return finished;
        }
        continue;
      }
      if (halted.getAsBoolean() || !grow.getAsBoolean()) {
        throw new Exhausted();
      }
      final var element = frame.state.production.rhs().get(frame.index);
      final var child = frame.path.get(frame.index++).child();
      switch (child) {
        case State constituent ->
            stack.push(new Construction(constituent, element.label(), path(constituent)));
        case Child.Token(var edge) -> frame.children.add(token(edge, element));
      }
    }
  }

  /** Ends selection or tree building when the budget is spent; the session catches it. */
  static final class Exhausted extends RuntimeException {
    Exhausted() {
      super(null, null, false, false);
    }
  }

  /** The links of a state's chosen derivation, in right-hand-side order. */
  private List<State.Link> path(final State state) {
    final var path = new ArrayList<State.Link>();
    for (var current = state;
        memo.get(current) != null && memo.get(current).link() != null;
        current = memo.get(current).link().previous()) {
      path.add(memo.get(current).link());
    }
    Collections.reverse(path);
    return path;
  }

  /** A state whose tree is being built: its chosen links and the children built so far. */
  private static final class Construction {
    private final State state;
    private final String label;
    private final List<State.Link> path;
    private final List<ParseTree> children = new ArrayList<>();
    private int index;

    Construction(final State state, final String label, final List<State.Link> path) {
      this.state = state;
      this.label = label;
      this.path = path;
    }

    List<ParseTree> finish() {
      if (state.production.auxiliary()) {
        return children;
      }
      final var start = state.start >= 0 ? state.start : state.origin.offset();
      return List.of(
          new ParseTree.Node(
              state.symbol(),
              label,
              children,
              state.features,
              start,
              state.start >= 0 ? state.end.offset() : start));
    }
  }

  /**
   * A named lexical token is a node with one leaf; an anonymous or caller token is a leaf (S-P8).
   */
  private static ParseTree token(final Edge edge, final Element element) {
    final var leaf =
        new ParseTree.Leaf(
            edge.text(),
            edge.start(),
            edge.end(),
            element instanceof Element.Symbol ? Structure.EMPTY : edge.features());
    if (element instanceof Element.Symbol symbol) {
      return new ParseTree.Node(
          edge.category(),
          symbol.label(),
          List.of(leaf),
          edge.features(),
          edge.start(),
          edge.end());
    }
    return leaf;
  }
}
