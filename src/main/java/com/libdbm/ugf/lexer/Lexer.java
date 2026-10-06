package com.libdbm.ugf.lexer;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.compiler.Compiled;
import com.libdbm.ugf.compiler.Lexeme;
import com.libdbm.ugf.constraints.Environment;
import com.libdbm.ugf.constraints.Evaluator;
import com.libdbm.ugf.constraints.Verdict;
import com.libdbm.ugf.features.Binding;
import com.libdbm.ugf.features.Structure;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the token graph for an input from a {@link Compiled} grammar's lexemes (S-L1 to S-L5).
 *
 * <p>Nodes are discovered in offset order. From each node, every lexeme allowed in the node's state
 * is tried at the next non-whitespace offset; matches that fail a required constraint are dropped,
 * and of the rest only those of maximal length become edges (S-L2). Each edge's target node carries
 * the state stack after the lexeme's transition (S-L3). A lexeme's constraints can only refer to
 * its own text and labelled parts, so they are evaluated in full here: false soft groups become
 * edge cost. If nothing matches, a one-character {@link Edge#ERROR} edge is added (S-L5).
 *
 * <p>Before each token, whitespace and matches of the grammar's {@code skip} categories (comments,
 * for example) are discarded, repeatedly, until neither matches (S-L4). Skip lexemes respect their
 * states but never apply transitions and never become edges.
 *
 * <p>A lexer is immutable and may be shared between threads.
 */
public final class Lexer implements TokenSource {

  /** The penalty of a token's constraints overflowed 64 bits (S-C10). */
  public static final String OVERFLOW = "limit.overflow";

  private static final Pattern LITERAL = Pattern.compile("\\\\Q(.+?)\\\\E");

  private final Compiled compiled;
  private final Map<Character, List<Lexeme>> literals = new HashMap<>();
  private final List<Lexeme> general = new ArrayList<>();
  private final List<Lexeme> skips = new ArrayList<>();

  public Lexer(final Compiled compiled) {
    this.compiled = compiled;
    // Literal lexemes are indexed by their first character (LEX-10); the rest are tried everywhere.
    for (final var lexeme : compiled.lexemes()) {
      if (!lexeme.anonymous() && compiled.skips().contains(lexeme.category())) {
        skips.add(lexeme);
        continue;
      }
      final var matcher = LITERAL.matcher(lexeme.pattern().pattern());
      if (matcher.matches()) {
        literals.computeIfAbsent(matcher.group(1).charAt(0), key -> new ArrayList<>()).add(lexeme);
      } else {
        general.add(lexeme);
      }
    }
  }

  private record Key(int offset, List<String> states) {}

  private record Pending(
      Key from, Key to, int start, String text, String category, Structure features, long cost) {}

  private record Match(Lexeme lexeme, int length, long cost) {}

  /** Failure code: the token graph would exceed the node limit. */
  public static final String NODES = "limit.nodes";

  /** Failure code: a lexical state stack would exceed the depth limit. */
  public static final String DEPTH = "limit.depth";

  /** Failure code: lexing passed its deadline. */
  public static final String DEADLINE = "limit.deadline";

  /** Failure code: the lexing thread was interrupted. */
  public static final String INTERRUPT = "limit.interrupt";

  @Override
  public Result<Graph, ErrorDetails> tokenize(final String input) {
    return tokenize(input, 0, 0, 0);
  }

  /**
   * Builds the token graph within limits: at most {@code nodes} graph nodes and state stacks at
   * most {@code depth} deep (zero means unlimited), finishing before {@code deadline} (a {@link
   * System#nanoTime()} value; zero means none). The thread's interrupt flag is honoured.
   */
  public Result<Graph, ErrorDetails> tokenize(
      final String input, final int nodes, final int depth, final long deadline) {
    final var frontier = new TreeMap<Integer, List<Key>>();
    final var discovered = new LinkedHashMap<Key, Boolean>();
    final var pending = new ArrayList<Pending>();
    final var finals = new ArrayList<Key>();
    final var start = new Key(0, List.of("DEFAULT"));
    discover(start, frontier, discovered);
    while (!frontier.isEmpty()) {
      for (final var key : frontier.pollFirstEntry().getValue()) {
        if (Thread.currentThread().isInterrupted()) {
          return Result.failure(ErrorDetails.of(INTERRUPT, "the lexing thread was interrupted"));
        }
        if (deadline != 0 && System.nanoTime() - deadline > 0) {
          return Result.failure(ErrorDetails.of(DEADLINE, "lexing passed the deadline"));
        }
        if (nodes > 0 && discovered.size() > nodes) {
          return Result.failure(
              ErrorDetails.of(
                  NODES,
                  "the token graph has more than " + nodes + " nodes (lexical state branching?)"));
        }
        final var position = skip(input, key.offset(), key.states());
        if (position == input.length()) {
          finals.add(key);
          continue;
        }
        final var matches = matches(input, key, position);
        if (matches == null) {
          return Result.failure(
              ErrorDetails.of(OVERFLOW, "token penalty overflow at offset " + position));
        }
        if (matches.isEmpty()) {
          final var length = Character.charCount(input.codePointAt(position));
          final var target = new Key(position + length, key.states());
          pending.add(
              new Pending(
                  key,
                  target,
                  position,
                  input.substring(position, position + length),
                  Edge.ERROR,
                  Structure.EMPTY,
                  0));
          discover(target, frontier, discovered);
          continue;
        }
        for (final var match : matches) {
          final var lexeme = match.lexeme();
          final var target =
              new Key(position + match.length(), apply(key.states(), lexeme.transition()));
          if (depth > 0 && target.states().size() > depth) {
            return Result.failure(
                ErrorDetails.of(
                    DEPTH,
                    "a lexical state stack is deeper than "
                        + depth
                        + " at offset "
                        + target.offset()));
          }
          pending.add(
              new Pending(
                  key,
                  target,
                  position,
                  input.substring(position, position + match.length()),
                  lexeme.category(),
                  lexeme.features(),
                  match.cost()));
          discover(target, frontier, discovered);
        }
      }
    }
    return Result.success(build(discovered.keySet(), pending, finals));
  }

  private static void discover(
      final Key key,
      final TreeMap<Integer, List<Key>> frontier,
      final Map<Key, Boolean> discovered) {
    if (discovered.putIfAbsent(key, Boolean.TRUE) == null) {
      frontier.computeIfAbsent(key.offset(), offset -> new ArrayList<>()).add(key);
    }
  }

  /** The offset after any whitespace and skip-category matches at {@code offset} (S-L4). */
  private int skip(final String input, final int offset, final List<String> states) {
    final var state = states.getLast();
    var position = offset;
    var moved = true;
    while (moved && position < input.length()) {
      moved = false;
      if (compiled.whitespace() != null) {
        final var matcher = region(compiled.whitespace(), input, position);
        if (matcher.lookingAt() && matcher.end() > position) {
          position = matcher.end();
          moved = true;
        }
      }
      for (final var lexeme : skips) {
        if (position >= input.length()
            || !lexeme.states().isEmpty() && !lexeme.states().contains(state)) {
          continue;
        }
        final var matcher = region(lexeme.pattern(), input, position);
        if (matcher.lookingAt()
            && matcher.end() > position
            && allowed(lexeme, matcher, input, states, position)) {
          position = matcher.end();
          moved = true;
        }
      }
    }
    return position;
  }

  /**
   * The accepted matches of maximal length at {@code position}, or {@code null} if a token's
   * penalty overflowed.
   */
  private List<Match> matches(final String input, final Key key, final int position) {
    final var state = key.states().getLast();
    final var environment = Environment.of(compiled.predicates()).lexical(key.states(), position);
    final var accepted = new ArrayList<Match>();
    var longest = 0;
    final var candidates = new ArrayList<>(general);
    candidates.addAll(literals.getOrDefault(input.charAt(position), List.of()));
    for (final var lexeme : candidates) {
      if (!lexeme.states().isEmpty() && !lexeme.states().contains(state)) {
        continue;
      }
      final var matcher = region(lexeme.pattern(), input, position);
      if (!matcher.lookingAt() || matcher.end() == position) {
        continue;
      }
      final var length = matcher.end() - position;
      if (length < longest) {
        continue;
      }
      final var bound =
          bind(environment, lexeme, matcher, input.substring(position, matcher.end()));
      final long cost;
      switch (Evaluator.evaluate(lexeme.plan(), bound)) {
        case Verdict.Rejected rejected -> {
          continue;
        }
        case Verdict.Overflow overflow -> {
          return null;
        }
        case Verdict.Accepted(long penalty) -> {
          try {
            cost = Math.addExact(penalty, lexeme.cost());
          } catch (final ArithmeticException exception) {
            return null;
          }
        }
      }
      if (length > longest) {
        longest = length;
        accepted.clear();
      }
      accepted.add(new Match(lexeme, length, cost));
    }
    accepted.sort((a, b) -> Integer.compare(a.lexeme().id(), b.lexeme().id()));
    return accepted;
  }

  /**
   * A matcher for {@code input} from {@code position}. Bounds are transparent, so lookbehind and
   * lookahead see the whole input, and not anchoring, so {@code ^} and {@code $} mean the input's
   * start and end.
   */
  private static Matcher region(final Pattern pattern, final String input, final int position) {
    return pattern
        .matcher(input)
        .region(position, input.length())
        .useTransparentBounds(true)
        .useAnchoringBounds(false);
  }

  /** True if a skip lexeme's required constraints hold for this match (S-L4). */
  private boolean allowed(
      final Lexeme lexeme,
      final Matcher matcher,
      final String input,
      final List<String> states,
      final int position) {
    if (lexeme.plan().isEmpty()) {
      return true;
    }
    final var environment = Environment.of(compiled.predicates()).lexical(states, position);
    final var bound = bind(environment, lexeme, matcher, input.substring(position, matcher.end()));
    return Evaluator.evaluate(lexeme.plan(), bound) instanceof Verdict.Accepted;
  }

  /** Binds the lexeme's symbol to the whole token and each label to its part. */
  private static Environment bind(
      final Environment environment,
      final Lexeme lexeme,
      final Matcher matcher,
      final String text) {
    if (lexeme.plan().isEmpty()) {
      return environment;
    }
    var bound = environment.with(lexeme.category(), Binding.of(text, lexeme.features()));
    for (var i = 0; i < lexeme.labels().size(); i++) {
      final var part = matcher.group(Lexeme.group(i));
      if (part != null) {
        bound = bound.with(lexeme.labels().get(i), Binding.of(part));
      }
    }
    return bound;
  }

  /**
   * Applies a transition: push {@code S}, pop {@code _} (never the bottom), reset {@code !S}, or
   * replace the top with {@code ^S}.
   */
  private static List<String> apply(final List<String> states, final String transition) {
    if (transition == null) {
      return states;
    }
    if (transition.equals("_")) {
      return states.size() > 1 ? states.subList(0, states.size() - 1) : states;
    }
    if (transition.startsWith("!")) {
      return List.of(transition.substring(1));
    }
    if (transition.startsWith("^")) {
      final var replaced = new ArrayList<>(states.subList(0, states.size() - 1));
      replaced.add(transition.substring(1));
      return replaced;
    }
    final var pushed = new ArrayList<>(states);
    pushed.add(transition);
    return pushed;
  }

  /** Assigns ids in offset order (a topological order, since every edge moves forward). */
  private static Graph build(
      final Iterable<Key> keys, final List<Pending> pending, final List<Key> finals) {
    final var sorted = new ArrayList<Key>();
    keys.forEach(sorted::add);
    sorted.sort((a, b) -> Integer.compare(a.offset(), b.offset()));
    final var nodes = new LinkedHashMap<Key, Node>();
    for (final var key : sorted) {
      nodes.put(key, new Node(nodes.size(), key.offset(), key.states()));
    }
    final var outgoing = new ArrayList<List<Edge>>();
    nodes.values().forEach(node -> outgoing.add(new ArrayList<>()));
    final var ordered = new ArrayList<>(pending);
    ordered.sort((a, b) -> Integer.compare(nodes.get(a.from()).id(), nodes.get(b.from()).id()));
    var id = 0;
    for (final var edge : ordered) {
      final var from = nodes.get(edge.from());
      outgoing
          .get(from.id())
          .add(
              new Edge(
                  id++,
                  from,
                  nodes.get(edge.to()),
                  edge.start(),
                  edge.text(),
                  edge.category(),
                  edge.features(),
                  edge.cost()));
    }
    final var ids = new HashSet<Integer>();
    finals.forEach(key -> ids.add(nodes.get(key).id()));
    return new Graph(List.copyOf(nodes.values()), outgoing, ids);
  }
}
