package com.libdbm.ugf.parser;

import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Value;
import com.libdbm.ugf.lexer.Graph;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Events delivered to a {@link ParseObserver}. Every event carries public data only (PAR-12), and
 * none is created when the observer is {@link ParseObserver#NOOP} (PAR-8). Positions are token
 * graph node ids.
 */
public final class ParseEvents {

  private ParseEvents() {}

  /**
   * Parsing begins.
   *
   * @param graph the token graph being parsed
   * @param start the symbol being parsed
   */
  public record Start(Graph graph, String start) {}

  /**
   * Parsing ends.
   *
   * @param result the result
   * @param elapsed time spent, including lexing
   */
  public record End(ParseResult result, Duration elapsed) {}

  /**
   * A production was predicted at a node.
   *
   * @param position the node
   * @param symbol the symbol predicted
   * @param production the production's id
   */
  public record Predict(int position, String symbol, int production) {}

  /**
   * A token was consumed.
   *
   * @param position the node the token leaves from
   * @param category the token's category
   * @param text the token's text
   */
  public record Scan(int position, String category, String text) {}

  /**
   * A constituent was completed.
   *
   * @param origin the node where it starts
   * @param position the node where it ends
   * @param symbol its symbol
   * @param production the production's id
   * @param penalty its total penalty
   */
  public record Complete(int origin, int position, String symbol, int production, long penalty) {}

  /**
   * Features written on an element were unified with a child's features.
   *
   * @param position the node where the child ends
   * @param waiting the waiting production's left-hand features
   * @param completed the child's features
   * @param target the features written on the element
   * @param result the unified features if unification succeeded
   * @param bindings the bindings after unification
   */
  public record Unification(
      int position,
      Structure waiting,
      Structure completed,
      Structure target,
      Optional<Structure> result,
      Map<String, Value> bindings) {
    public Unification {
      bindings = Map.copyOf(bindings);
    }
  }

  /**
   * A production's constraints were evaluated.
   *
   * @param position the node where the constituent ends
   * @param symbol the production's symbol
   * @param expression the constraints, rendered
   * @param passed whether every required expression held
   * @param penalty the weights of false soft groups, when it passed
   */
  public record ConstraintEval(
      int position, String symbol, String expression, boolean passed, long penalty) {}

  /**
   * A node's agenda was finished.
   *
   * @param position the node
   * @param states the states in its chart
   */
  public record Position(int position, int states) {}

  /**
   * No token matched at a position, so the lexer produced an error token (S-L5).
   *
   * @param position the node
   * @param token the unmatched text
   * @param expected elements the chart expected there
   */
  public record Unexpected(int position, String token, Set<String> expected) {
    public Unexpected {
      expected = Set.copyOf(expected);
    }
  }
}
