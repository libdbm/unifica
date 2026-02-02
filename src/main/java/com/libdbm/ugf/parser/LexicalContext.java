package com.libdbm.ugf.parser;

import com.libdbm.ugf.constraints.Context;
import com.libdbm.ugf.constraints.Result;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Value;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Per-tokenization context for lexical analysis.
 *
 * <p>This class encapsulates all mutable state needed during tokenization, making {@link
 * LexicalAnalyzer} thread-safe by ensuring each tokenization operation uses its own context
 * instance.
 *
 * <p>The context maintains:
 *
 * <ul>
 *   <li>A state stack for context-sensitive tokenization (e.g., CDATA sections, string literals)
 *   <li>Integration with the constraint evaluation {@link Context}
 *   <li>State-related predicates for constraint evaluation
 * </ul>
 *
 * <h3>Lexer-Only Predicates</h3>
 *
 * <p>This class defines which predicates are lexer-only (not valid during parsing):
 *
 * <ul>
 *   <li>{@code in_state} - Check current lexer state
 *   <li>{@code state_depth} - Check state stack depth
 *   <li>{@code state_contains} - Check if state is in stack
 *   <li>{@code at_char_start} - Check if at character position 0 (not to be confused with parser's
 *       {@code at_start} which checks token index)
 *   <li>{@code at_char_position} - Check specific character position (not to be confused with
 *       parser's {@code at_position} which checks token index)
 * </ul>
 *
 * <h3>Position Predicate Disambiguation</h3>
 *
 * <p>The lexer and parser both provide position predicates, but with different semantics:
 *
 * <ul>
 *   <li>Lexer: {@code at_char_start}, {@code at_char_position} - Character offsets in input
 *   <li>Parser: {@code at_start}, {@code at_position}, {@code at_token_start}, {@code
 *       at_token_position} - Token indices in token stream
 * </ul>
 */
public final class LexicalContext {

  /**
   * Predicates that are only valid during lexical analysis. These should be filtered out during
   * parser constraint evaluation.
   */
  public static final Set<String> LEXER_ONLY_PREDICATES =
      Set.of("in_state", "state_depth", "state_contains", "at_char_start", "at_char_position");

  private final Deque<String> states;
  private final Context context;

  /** Creates a new lexical context with a fresh constraint context. */
  public LexicalContext() {
    this(new Context(), defaultStateStack());
  }

  /** Creates a new lexical context using the given constraint context. */
  public LexicalContext(final Context context) {
    this(context, defaultStateStack());
  }

  private LexicalContext(final Context base, final Deque<String> stateStack) {
    if (stateStack == null || stateStack.isEmpty()) {
      throw new IllegalArgumentException("State stack must not be empty");
    }
    this.context = base.extend();
    this.states = new ArrayDeque<>(stateStack);
    register(this.context);
  }

  /** Check if a predicate name is lexer-only. */
  public static boolean isLexerOnly(final String name) {
    return LEXER_ONLY_PREDICATES.contains(name);
  }

  private static Deque<String> defaultStateStack() {
    final var stack = new ArrayDeque<String>();
    stack.push("DEFAULT");
    return stack;
  }

  /** Create a copy carrying the same state stack and bindings. */
  public LexicalContext copy() {
    return new LexicalContext(this.context, this.states);
  }

  /** Register state and position predicates into constraint context. */
  private void register(final Context context) {
    // Register all generic built-in predicates (matches, starts_with, etc.)
    context.withPredicates(com.libdbm.ugf.constraints.Builtins.all());

    // State predicates (lexer-only, not valid during parsing)
    context.withPredicate("in_state", (ctx, args) -> inState(args));
    context.withPredicate("state_depth", (ctx, args) -> stateDepth(args));
    context.withPredicate("state_contains", (ctx, args) -> stateContains(args));

    // Position predicates (lexer-only, character-based not token-based)
    // Use at_char_* names to avoid collision with parser's token-based predicates.
    context.withPredicate("at_char_start", (ctx, args) -> atCharStart());
    context.withPredicate("at_char_position", (ctx, args) -> atCharPosition(args));
  }

  /** Get the underlying constraint context. */
  public Context context() {
    return context;
  }

  /**
   * Get the current state (top of stack).
   *
   * @return the current state, never null (stack invariant: always has at least one element)
   */
  public String state() {
    return states.peek();
  }

  /** Get the current stack depth. */
  public int depth() {
    return states.size();
  }

  /** Check if a state is anywhere in the stack. */
  public boolean contains(final String state) {
    return states.contains(state);
  }

  /** Set the current position for constraint evaluation. */
  public void position(final int pos) {
    context.withBinding("position", pos);
  }

  /** Get the current position. */
  public int position() {
    final var value = context.get("position");
    if (value instanceof com.libdbm.ugf.features.NumericConstant n) {
      return n.value().intValue();
    }
    return 0;
  }

  /**
   * Apply a state transition.
   *
   * <ul>
   *   <li>{@code "_"} - Pop current state from stack (if not at bottom)
   *   <li>{@code "!STATE"} - Reset stack to single state
   *   <li>Otherwise - Push new state onto stack
   * </ul>
   */
  public void apply(final String transition) {
    if (transition == null) return;

    if (transition.equals("_")) {
      if (states.size() > 1) {
        states.pop();
      }
    } else if (transition.startsWith("!")) {
      states.clear();
      states.push(transition.substring(1));
    } else {
      states.push(transition);
    }
  }

  private Result inState(final List<Value> args) {
    final var current = states.peek();
    for (final var arg : args) {
      if (arg instanceof StringConstant(String value) && value.equals(current)) {
        return new Result(true, null);
      }
    }
    return new Result(false, "State mismatch: " + current);
  }

  private Result stateDepth(final List<Value> args) {
    if (args.isEmpty()) {
      return new Result(false, "state_depth requires argument");
    }
    final var arg = args.getFirst();
    if (arg instanceof StringConstant(String value)) {
      try {
        final var max = Integer.parseInt(value);
        final var depth = states.size();
        return new Result(depth <= max, "Depth " + depth + " > " + max);
      } catch (NumberFormatException e) {
        return new Result(false, "Invalid depth: " + value);
      }
    }
    return new Result(false, "Invalid argument type");
  }

  private Result stateContains(final List<Value> args) {
    for (final var arg : args) {
      if (arg instanceof StringConstant(String value) && states.contains(value)) {
        return new Result(true, null);
      }
    }
    return new Result(false, "State not in stack");
  }

  private Result atCharStart() {
    return new Result(position() == 0, "Not at character position 0");
  }

  private Result atCharPosition(final List<Value> args) {
    if (args.isEmpty()) {
      return new Result(false, "at_char_position requires argument");
    }
    final var arg = args.getFirst();
    if (arg instanceof StringConstant(String value)) {
      try {
        final var expected = Integer.parseInt(value);
        return new Result(position() == expected, "Character position mismatch");
      } catch (NumberFormatException e) {
        return new Result(false, "Invalid position: " + value);
      }
    }
    return new Result(false, "Invalid argument type");
  }

  /** Deterministic signature for deduplicating state stacks. */
  String stateSignature() {
    return String.join(">", states);
  }
}
