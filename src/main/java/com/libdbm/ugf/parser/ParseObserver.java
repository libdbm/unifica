package com.libdbm.ugf.parser;

/**
 * Observer interface for parser visualization hooks.
 *
 * <p>Implementations receive events during parsing for visualization, logging, or debugging. All
 * methods have empty default implementations - observers opt-in to events they care about.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * // Create parser with observer
 * var observer = new LoggingObserver();
 * var parser = ParserFactory.create(grammar, observer);
 *
 * // Or use NOOP for no observation overhead (JIT inlines empty methods)
 * var parser = ParserFactory.create(grammar);  // uses ParseObserver.NOOP
 * }</pre>
 *
 * <p>Event records are immutable snapshots - observers can safely store them without worrying about
 * later mutation.
 */
public interface ParseObserver {

  /** No-op observer for when visualization is not needed. */
  ParseObserver NOOP = new ParseObserver() {};

  /** Called when parsing begins. */
  default void onStart(final ParseEvents.Start event) {}

  /** Called when parsing completes (success or failure). */
  default void onEnd(final ParseEvents.End event) {}

  /** Called when predict operation considers a rule. */
  default void onPredict(final ParseEvents.Predict event) {}

  /** Called when scan operation attempts to match a token. */
  default void onScan(final ParseEvents.Scan event) {}

  /** Called when complete operation processes a completed item. */
  default void onComplete(final ParseEvents.Complete event) {}

  /** Called when feature unification is attempted. */
  default void onUnification(final ParseEvents.Unification event) {}

  /** Called when constraints are evaluated. */
  default void onConstraint(final ParseEvents.ConstraintEval event) {}

  /** Called when processing at a chart position completes. */
  default void onPosition(final ParseEvents.Position event) {}

  /** Called when a token is unexpected at a position. */
  default void onUnexpected(final ParseEvents.Unexpected event) {}
}
