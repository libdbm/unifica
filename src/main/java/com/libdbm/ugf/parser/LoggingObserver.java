package com.libdbm.ugf.parser;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observer that logs parse events for debugging and visualization.
 *
 * <p>Supports filtering by event type and collects events for later inspection.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * var observer = new LoggingObserver();
 * var parser = Parser.of(compiled, new Options(Limits.NONE, observer, false));
 * parser.parse("input");
 *
 * // Inspect collected events
 * observer.events().forEach(System.out::println);
 * }</pre>
 */
public final class LoggingObserver implements ParseObserver {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoggingObserver.class);
  private final EnumSet<Type> filter;

  /** The most events an observer keeps unless told otherwise. */
  public static final int CAPACITY = 10_000;

  private final List<Object> events = new ArrayList<>();
  private final boolean log;
  private final int capacity;
  private long dropped;

  /** Create observer that logs all event types. */
  public LoggingObserver() {
    this(EnumSet.allOf(Type.class), true);
  }

  /** Create observer with specific event types. */
  public LoggingObserver(final EnumSet<Type> filter) {
    this(filter, true);
  }

  /** Create observer with filtering and optional logging, keeping at most {@link #CAPACITY}. */
  public LoggingObserver(final EnumSet<Type> filter, final boolean log) {
    this(filter, log, CAPACITY);
  }

  /**
   * Create observer with filtering and optional logging that keeps at most {@code capacity} events;
   * later events are still logged but not kept, and are counted by {@link #dropped()}.
   */
  public LoggingObserver(final EnumSet<Type> filter, final boolean log, final int capacity) {
    if (capacity < 0) {
      throw new IllegalArgumentException("capacity must not be negative");
    }
    this.filter = EnumSet.copyOf(filter);
    this.log = log;
    this.capacity = capacity;
  }

  /** How many events were not kept because the observer was full. */
  public long dropped() {
    return dropped;
  }

  private void keep(final Object event) {
    if (events.size() < capacity) {
      events.add(event);
    } else {
      dropped++;
    }
  }

  /** Create observer that only collects events without logging. */
  public static LoggingObserver quiet() {
    return new LoggingObserver(EnumSet.allOf(Type.class), false);
  }

  /** Create observer for specific event types without logging. */
  public static LoggingObserver quiet(final EnumSet<Type> filter) {
    return new LoggingObserver(filter, false);
  }

  @Override
  public void onStart(final ParseEvents.Start event) {
    if (filter.contains(Type.START)) {
      keep(event);
      if (log) {
        LOGGER.info("START: {} tokens, start={}", event.graph().edges().size(), event.start());
      }
    }
  }

  @Override
  public void onEnd(final ParseEvents.End event) {
    if (filter.contains(Type.END)) {
      keep(event);
      if (log) {
        final var tree = event.result().tree();
        LOGGER.info(
            "END: {} in {}ms, penalty={}",
            tree != null ? "SUCCESS" : "FAILED",
            event.elapsed().toMillis(),
            event.result().penalty());
      }
    }
  }

  @Override
  public void onPredict(final ParseEvents.Predict event) {
    if (filter.contains(Type.PREDICT)) {
      keep(event);
      if (log) {
        LOGGER.debug(
            "PREDICT[{}]: {} (production {})",
            event.position(),
            event.symbol(),
            event.production());
      }
    }
  }

  @Override
  public void onScan(final ParseEvents.Scan event) {
    if (filter.contains(Type.SCAN)) {
      keep(event);
      if (log) {
        LOGGER.debug("SCAN[{}]: '{}' as {}", event.position(), event.text(), event.category());
      }
    }
  }

  @Override
  public void onComplete(final ParseEvents.Complete event) {
    if (filter.contains(Type.COMPLETE)) {
      keep(event);
      if (log) {
        LOGGER.debug(
            "COMPLETE[{}..{}]: {} (production {}, penalty {})",
            event.origin(),
            event.position(),
            event.symbol(),
            event.production(),
            event.penalty());
      }
    }
  }

  @Override
  public void onUnification(final ParseEvents.Unification event) {
    if (filter.contains(Type.UNIFICATION)) {
      keep(event);
      if (log) {
        if (event.result().isPresent()) {
          LOGGER.debug("UNIFY[{}]: SUCCESS bindings={}", event.position(), event.bindings());
        } else {
          LOGGER.debug("UNIFY[{}]: FAILED", event.position());
        }
      }
    }
  }

  @Override
  public void onConstraint(final ParseEvents.ConstraintEval event) {
    if (filter.contains(Type.CONSTRAINT)) {
      keep(event);
      if (log) {
        LOGGER.debug(
            "CONSTRAINT[{}]: {} {} {} (penalty {})",
            event.position(),
            event.symbol(),
            event.passed() ? "PASSED" : "FAILED",
            event.expression(),
            event.penalty());
      }
    }
  }

  @Override
  public void onPosition(final ParseEvents.Position event) {
    if (filter.contains(Type.POSITION)) {
      keep(event);
      if (log) {
        LOGGER.debug("POSITION[{}]: {} states", event.position(), event.states());
      }
    }
  }

  @Override
  public void onUnexpected(final ParseEvents.Unexpected event) {
    if (filter.contains(Type.UNEXPECTED)) {
      keep(event);
      if (log) {
        LOGGER.warn(
            "UNEXPECTED[{}]: '{}' expected: {}", event.position(), event.token(), event.expected());
      }
    }
  }

  /** Get all collected events. */
  public List<Object> events() {
    return List.copyOf(events);
  }

  /** Get events of a specific type. */
  @SuppressWarnings("unchecked")
  public <T> List<T> events(final Class<T> type) {
    return events.stream().filter(type::isInstance).map(e -> (T) e).toList();
  }

  /** Clear collected events. */
  public void clear() {
    events.clear();
    dropped = 0;
  }

  /** Get count of events by type. */
  public int count(final Type type) {
    final var clazz =
        switch (type) {
          case START -> ParseEvents.Start.class;
          case END -> ParseEvents.End.class;
          case PREDICT -> ParseEvents.Predict.class;
          case SCAN -> ParseEvents.Scan.class;
          case COMPLETE -> ParseEvents.Complete.class;
          case UNIFICATION -> ParseEvents.Unification.class;
          case CONSTRAINT -> ParseEvents.ConstraintEval.class;
          case POSITION -> ParseEvents.Position.class;
          case UNEXPECTED -> ParseEvents.Unexpected.class;
        };
    return (int) events.stream().filter(clazz::isInstance).count();
  }

  public enum Type {
    START,
    END,
    PREDICT,
    SCAN,
    COMPLETE,
    UNIFICATION,
    CONSTRAINT,
    POSITION,
    UNEXPECTED
  }
}
