package com.libdbm.ugf.parser;

import java.time.Duration;
import java.util.Objects;

/**
 * Resource limits for one parse (PAR-6), covering lexing, parsing, tree selection and diagnostics.
 * A count of zero means unlimited, and a zero deadline means none. A parse that reaches a limit
 * ends with {@link Outcome#LIMIT} and a {@link Stop} naming the resource.
 *
 * @param states the most chart states a parse may create, counting replacements of a state by a
 *     cheaper one
 * @param agenda the most agenda entries a parse may process
 * @param links the most packed derivation links a parse may record
 * @param nodes the most token graph nodes, from the lexer or from a caller's or enhancer's graph
 * @param depth the deepest lexical state stack the lexer may build
 * @param tree the most nodes the selected tree may have
 * @param diagnostics the most diagnostic records retained when diagnostics are enabled
 * @param deadline the longest a parse may take, lexing included
 */
public record Limits(
    int states,
    int agenda,
    int links,
    int nodes,
    int depth,
    int tree,
    int diagnostics,
    Duration deadline) {

  /** No limits. */
  public static final Limits NONE = new Limits(0, 0, 0, 0, 0, 0, 0, Duration.ZERO);

  /**
   * The limits {@link Options#DEFAULT} uses: roughly seven times the largest chart, and forty-six
   * times the largest token graph, of any port sample (see BENCHMARKS.md), and one minute.
   */
  public static final Limits DEFAULT =
      new Limits(
          10_000_000,
          10_000_000,
          20_000_000,
          1_000_000,
          1_000,
          10_000_000,
          10_000,
          Duration.ofMinutes(1));

  public Limits {
    Objects.requireNonNull(deadline, "deadline");
    if (states < 0
        || agenda < 0
        || links < 0
        || nodes < 0
        || depth < 0
        || tree < 0
        || diagnostics < 0
        || deadline.isNegative()) {
      throw new IllegalArgumentException("limits must not be negative");
    }
  }

  public Limits states(final int value) {
    return new Limits(value, agenda, links, nodes, depth, tree, diagnostics, deadline);
  }

  public Limits agenda(final int value) {
    return new Limits(states, value, links, nodes, depth, tree, diagnostics, deadline);
  }

  public Limits links(final int value) {
    return new Limits(states, agenda, value, nodes, depth, tree, diagnostics, deadline);
  }

  public Limits nodes(final int value) {
    return new Limits(states, agenda, links, value, depth, tree, diagnostics, deadline);
  }

  public Limits depth(final int value) {
    return new Limits(states, agenda, links, nodes, value, tree, diagnostics, deadline);
  }

  public Limits tree(final int value) {
    return new Limits(states, agenda, links, nodes, depth, value, diagnostics, deadline);
  }

  public Limits diagnostics(final int value) {
    return new Limits(states, agenda, links, nodes, depth, tree, value, deadline);
  }

  public Limits deadline(final Duration value) {
    return new Limits(states, agenda, links, nodes, depth, tree, diagnostics, value);
  }
}
