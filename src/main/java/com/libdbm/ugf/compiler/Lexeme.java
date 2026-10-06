package com.libdbm.ugf.compiler;

import com.libdbm.ugf.constraints.Plan;
import com.libdbm.ugf.features.Structure;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A compiled lexical category: one atomic pattern that produces a token of {@code category} (S-G2).
 *
 * @param id stable identifier, in compilation order
 * @param category the token category: the production's symbol, or the source text of an inline
 *     literal or regex for an anonymous category
 * @param pattern the complete pattern; labelled parts are named groups {@code l0}, {@code l1}, ...
 * @param features the production's left-hand features, carried by every token
 * @param states the lexical states the lexeme matches in; empty means every state
 * @param transition the state transition applied after a match ({@code S}, {@code _}, {@code !S}),
 *     or {@code null}
 * @param plan the production's constraints
 * @param cost the production cost, added to every token's cost
 * @param labels the labels of the named groups, in group-number order
 * @param anonymous true for an inline literal or regex (S-G3)
 */
public record Lexeme(
    int id,
    String category,
    Pattern pattern,
    Structure features,
    List<String> states,
    String transition,
    Plan plan,
    long cost,
    List<String> labels,
    boolean anonymous) {

  public Lexeme {
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(pattern, "pattern");
    Objects.requireNonNull(features, "features");
    Objects.requireNonNull(plan, "plan");
    states = List.copyOf(states);
    labels = List.copyOf(labels);
  }

  /** The named group that holds the text of the {@code index}th label. */
  public static String group(final int index) {
    return "l" + index;
  }
}
