package com.libdbm.ugf.parser;

import com.libdbm.ugf.compiler.Element;
import com.libdbm.ugf.compiler.Production;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.lexer.Edge;
import com.libdbm.ugf.lexer.Graph;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * Chooses among the packed derivations of a finished chart and builds the selected tree (S-P3,
 * S-P4, S-P8). Every traversal uses an explicit stack, so deep derivations cannot exhaust the
 * thread stack. Not thread-safe; a session creates one per parse.
 */
final class ForestSelector {

  /**
   * The candidate derivations of each state resolved so far: those no other derivation of the state
   * precedes whatever follows it (S-P3). They differ only where one key is a proper prefix of
   * another, so what follows decides between them.
   */
  private final Map<State, List<Best>> memo = new IdentityHashMap<>();

  /** States whose best derivation is being resolved, to skip cyclic derivations. */
  private final Set<State> active = Collections.newSetFromMap(new IdentityHashMap<>());

  private final BooleanSupplier halted;
  private final BooleanSupplier grow;
  private final Graph graph;

  /** Each token's place in the full S-P3 token order, computed on first use. */
  private Map<Edge, Long> ranks;

  /** Each token's place in the S-P3 order without its last rule, declaration or caller order. */
  private Map<Edge, Long> classes;

  /** True if only the last S-P3 rule chose among the root candidates. */
  private boolean split;

  /** The chosen root derivation. */
  private Best selected;

  /**
   * @param graph the token graph the derivations were parsed from
   * @param halted true when the parse must stop; checked as selection and tree building proceed
   * @param grow counts one tree node; false when the tree limit is exceeded
   */
  ForestSelector(final Graph graph, final BooleanSupplier halted, final BooleanSupplier grow) {
    this.graph = graph;
    this.halted = halted;
    this.grow = grow;
  }

  /** The full S-P3 order, ending with declaration or caller order. */
  private static int compare(final Best a, final Best b) {
    final var result = coarse(a, b);
    return result != 0 ? result : a.tokens().compareTo(b.tokens());
  }

  /**
   * Pre-order productions first, then constituent ends, then token categories and state stacks
   * (S-P3), but not the last rule.
   */
  private static int coarse(final Best a, final Best b) {
    var result = a.productions().compareTo(b.productions());
    if (result == 0) {
      result = a.ends().compareTo(b.ends());
    }
    return result != 0 ? result : a.classes().compareTo(b.classes());
  }

  /**
   * The S-P3 token order without its last rule: category (none first), then the state stack the
   * token leads to, compared from the bottom. Strings compare by code point.
   */
  private static int order(final Edge a, final Edge b) {
    if (!Objects.equals(a.category(), b.category())) {
      if (a.category() == null || b.category() == null) {
        return a.category() == null ? -1 : 1;
      }
      return text(a.category(), b.category());
    }
    final var mine = a.to().states();
    final var theirs = b.to().states();
    for (var i = 0; i < Math.min(mine.size(), theirs.size()); i++) {
      final var result = text(mine.get(i), theirs.get(i));
      if (result != 0) {
        return result;
      }
    }
    return Integer.compare(mine.size(), theirs.size());
  }

  /** Code point order; a proper prefix comes first. */
  private static int text(final String a, final String b) {
    var i = 0;
    var j = 0;
    while (i < a.length() && j < b.length()) {
      final var x = a.codePointAt(i);
      final var y = b.codePointAt(j);
      if (x != y) {
        return Integer.compare(x, y);
      }
      i += Character.charCount(x);
      j += Character.charCount(y);
    }
    return Boolean.compare(i < a.length(), j < b.length());
  }

  /**
   * Ranks every token in the S-P3 order. The last rule prefers the earlier of tokens leaving the
   * same node, which the lexer emits in declaration order and caller sources in the caller's order.
   */
  private void rank() {
    final var positions = new IdentityHashMap<Edge, Integer>();
    for (final var node : graph.nodes()) {
      final var outgoing = graph.edges(node);
      for (var i = 0; i < outgoing.size(); i++) {
        positions.put(outgoing.get(i), i);
      }
    }
    final var edges = new ArrayList<>(graph.edges());
    final Comparator<Edge> full =
        ((Comparator<Edge>) ForestSelector::order).thenComparing(positions::get);
    edges.sort(full);
    ranks = new IdentityHashMap<>();
    classes = new IdentityHashMap<>();
    var rank = -1L;
    var klass = -1L;
    Edge last = null;
    for (final var current : edges) {
      if (last == null || full.compare(last, current) != 0) {
        rank++;
      }
      if (last == null || order(last, current) != 0) {
        klass++;
      }
      ranks.put(current, rank);
      classes.put(current, klass);
      last = current;
    }
  }

  /**
   * True if only the last S-P3 rule chose the selected derivation among the root candidates, or
   * chose a derivation inside it.
   */
  boolean tied() {
    return split || selected.tied();
  }

  /**
   * How {@code a} and {@code b} are ordered whatever follows them: negative or positive if one
   * precedes the other for every continuation, {@link #EQUAL} if their keys are equal, and {@link
   * #OPEN} if a continuation decides. {@code last} is set when only the last S-P3 rule decides.
   */
  private static int dominance(final Best a, final Best b, final boolean[] last) {
    final var mine = new Sequence[] {a.productions(), a.ends(), a.classes(), a.tokens()};
    final var theirs = new Sequence[] {b.productions(), b.ends(), b.classes(), b.tokens()};
    for (var i = 0; i < mine.length; i++) {
      final var result = mine[i].differ(theirs[i]);
      if (result != 0) {
        last[0] = i == mine.length - 1;
        return result;
      }
      if (mine[i].size() != theirs[i].size()) {
        return OPEN;
      }
    }
    return EQUAL;
  }

  private static final int EQUAL = 0;
  private static final int OPEN = Integer.MAX_VALUE;

  /** Adds {@code candidate} to {@code pool}, keeping only derivations no other one precedes. */
  private static void offer(final List<Best> pool, final Best candidate) {
    final var last = new boolean[1];
    var current = candidate;
    for (var i = 0; i < pool.size(); i++) {
      last[0] = false;
      final var result = dominance(current, pool.get(i), last);
      if (result == EQUAL) {
        return;
      }
      if (result == OPEN) {
        continue;
      }
      if (result > 0) {
        // Only declaration or caller order put the existing derivation first.
        if (last[0]) {
          pool.set(i, pool.get(i).tie());
        }
        return;
      }
      pool.remove(i--);
      if (last[0]) {
        current = current.tie();
      }
    }
    pool.add(current);
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

  /**
   * Chooses the derivation that comes first among the candidates of the tied roots (S-P3). Nothing
   * follows a root, so a proper prefix comes first. Returns its root.
   */
  State choose(final List<State> tied) {
    State chosen = null;
    final var all = new ArrayList<Best>();
    for (final var root : tied) {
      for (final var candidate : candidates(root)) {
        all.add(candidate);
        if (selected == null || compare(candidate, selected) < 0) {
          selected = candidate;
          chosen = root;
        }
      }
    }
    for (final var other : all) {
      if (coarse(other, selected) == 0 && compare(other, selected) != 0) {
        split = true;
      }
    }
    return chosen;
  }

  /** The tree of the selected derivation of {@code chosen}. */
  ParseTree tree(final State chosen) {
    return build(chosen, selected, null).getFirst();
  }

  /**
   * The candidate derivations of {@code state}. Nullable recursion makes the packed derivations
   * cyclic; a link that leads back into a state still being resolved ({@code active}) is skipped,
   * which is safe because a cyclic derivation is never cheaper (S-P9). An explicit stack replaces
   * recursion, visiting links and their parts in the same order, so deep derivations cannot exhaust
   * the stack.
   */
  List<Best> candidates(final State state) {
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
        memo.put(frame.state, frame.pool.isEmpty() ? List.of(Best.EMPTY) : frame.pool);
        continue;
      }
      final var link = frame.state.links.get(frame.index);
      if (active.contains(link.previous())
          || link.child() instanceof State constituent && active.contains(constituent)) {
        frame.index++;
        continue;
      }
      final var previous = memo.get(link.previous());
      if (previous == null) {
        active.add(link.previous());
        stack.push(new Selection(link.previous()));
        continue;
      }
      switch (link.child()) {
        case State constituent -> {
          final var inner = memo.get(constituent);
          if (inner == null) {
            active.add(constituent);
            stack.push(new Selection(constituent));
            continue;
          }
          for (final var before : previous) {
            var productions = before.productions();
            var ends = before.ends();
            if (!constituent.production.auxiliary()) {
              productions = productions.then(constituent.production.id());
              ends = ends.then(constituent.end.offset());
            }
            for (final var child : inner) {
              offer(
                  frame.pool,
                  new Best(
                      link,
                      before,
                      child,
                      productions.then(child.productions()),
                      ends.then(child.ends()),
                      before.classes().then(child.classes()),
                      before.tokens().then(child.tokens()),
                      before.tied() || child.tied()));
            }
          }
        }
        case Child.Token(var edge) -> {
          if (ranks == null) {
            rank();
          }
          for (final var before : previous) {
            offer(
                frame.pool,
                new Best(
                    link,
                    before,
                    null,
                    before.productions(),
                    before.ends().then(edge.end()),
                    before.classes().then(classes.get(edge)),
                    before.tokens().then(ranks.get(edge)),
                    before.tied()));
          }
        }
      }
      frame.index++;
    }
    return memo.get(state);
  }

  /**
   * True if any state on the chosen derivation has more than one equally cheap derivation (S-P4).
   */
  boolean ambiguous(final State state) {
    final var stack = new ArrayDeque<Derivation>();
    stack.push(new Derivation(state, selected));
    while (!stack.isEmpty()) {
      final var current = stack.pop();
      if (current.state().links.size() > 1) {
        return true;
      }
      final var link = current.best().link();
      if (link != null) {
        stack.push(new Derivation(link.previous(), current.best().previous()));
        if (link.child() instanceof State constituent) {
          stack.push(new Derivation(constituent, current.best().inner()));
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
    final var stack = new ArrayDeque<Visit>();
    stack.push(new Visit(chosen, selected, chosen));
    final var seen = new HashSet<String>();
    while (!stack.isEmpty()) {
      final var visit = stack.pop();
      final var state = visit.state();
      final var best = visit.best();
      final var owner = state.production.auxiliary() ? visit.owner() : state;
      if (state.links.size() > 1 && state.complete() && owner.production != root) {
        final var start = owner.start >= 0 ? owner.start : owner.origin.offset();
        if (seen.add(owner.symbol() + "@" + start + ":" + owner.end.offset())) {
          report.accept(
              owner.symbol(),
              new ParseDiagnostics.Span(owner.origin.id(), start, owner.end.offset()));
        }
      }
      final var link = best.link();
      if (link != null) {
        stack.push(new Visit(link.previous(), best.previous(), visit.owner()));
        if (link.child() instanceof State constituent) {
          stack.push(new Visit(constituent, best.inner(), owner));
        }
      }
    }
  }

  /**
   * The trees for a state: one node, or its children spliced if it is auxiliary (S-P8). An explicit
   * stack replaces recursion, so deep trees cannot exhaust the stack.
   */
  private List<ParseTree> build(final State state, final Best best, final String label) {
    final var stack = new ArrayDeque<Construction>();
    stack.push(new Construction(state, label, path(best)));
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
      final var step = frame.path.get(frame.index++);
      switch (step.link().child()) {
        case State constituent ->
            stack.push(new Construction(constituent, element.label(), path(step.inner())));
        case Child.Token(var edge) -> frame.children.add(token(edge, element));
      }
    }
  }

  /** The steps of a derivation, one per right-hand-side element, in order. */
  private static List<Best> path(final Best best) {
    final var path = new ArrayList<Best>();
    for (var current = best;
        current != null && current.link() != null;
        current = current.previous()) {
      path.add(current);
    }
    Collections.reverse(path);
    return path;
  }

  /**
   * A candidate derivation of a state: its last link, the derivations it extends (of the link's
   * previous state, and of its child constituent, if any) and the keys that order derivations
   * (S-P3).
   */
  private record Best(
      State.Link link,
      Best previous,
      Best inner,
      Sequence productions,
      Sequence ends,
      Sequence classes,
      Sequence tokens,
      boolean tied) {
    private static final Best EMPTY =
        new Best(
            null,
            null,
            null,
            Sequence.EMPTY,
            Sequence.EMPTY,
            Sequence.EMPTY,
            Sequence.EMPTY,
            false);

    /** This derivation, marked as chosen by the last S-P3 rule. */
    private Best tie() {
      return new Best(link, previous, inner, productions, ends, classes, tokens, true);
    }
  }

  /** A state on a derivation, with the candidate chosen for it. */
  private record Derivation(State state, Best best) {}

  /** A state on a derivation, its chosen candidate, and the nearest real symbol above it. */
  private record Visit(State state, Best best, State owner) {}

  /** A state whose candidates are being collected: the next link and the candidates so far. */
  private static final class Selection {
    private final State state;
    private int index;
    private final List<Best> pool = new ArrayList<>();

    Selection(final State state) {
      this.state = state;
    }
  }

  /** Ends selection or tree building when the budget is spent; the session catches it. */
  static final class Exhausted extends RuntimeException {
    Exhausted() {
      super(null, null, false, false);
    }
  }

  /** A state whose tree is being built: its chosen steps and the children built so far. */
  private static final class Construction {
    private final State state;
    private final String label;
    private final List<Best> path;
    private final List<ParseTree> children = new ArrayList<>();
    private int index;

    Construction(final State state, final String label, final List<Best> path) {
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
}
