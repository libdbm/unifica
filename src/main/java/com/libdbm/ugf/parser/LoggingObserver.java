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
 * var parser = ParserFactory.create(grammar, observer);
 * parser.parse("input");
 *
 * // Inspect collected events
 * observer.events().forEach(System.out::println);
 * }</pre>
 */
public final class LoggingObserver implements ParseObserver {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoggingObserver.class);
  private final EnumSet<Type> filter;
  private final List<Object> events = new ArrayList<>();
  private final boolean log;
  /** Create observer that logs all event types. */
  public LoggingObserver() {
    this(EnumSet.allOf(Type.class), true);
  }

  /** Create observer with specific event types. */
  public LoggingObserver(final EnumSet<Type> filter) {
    this(filter, true);
  }

  /** Create observer with filtering and optional logging. */
  public LoggingObserver(final EnumSet<Type> filter, final boolean log) {
    this.filter = EnumSet.copyOf(filter);
    this.log = log;
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
      events.add(event);
      if (log) {
        LOGGER.info(
            "START: {} tokens, start={}", event.lattice().size(), event.start());
      }
    }
  }

  @Override
  public void onEnd(final ParseEvents.End event) {
    if (filter.contains(Type.END)) {
      events.add(event);
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
      events.add(event);
      if (log) {
        if (event.accepted()) {
          LOGGER.debug(
              "PREDICT[{}]: {} → {}", event.position(), event.trigger().name(), display(event.rule()));
        } else {
          LOGGER.debug(
              "PREDICT[{}]: REJECTED {} - {}",
              event.position(),
              event.rule().lhs().symbol(),
              event.reason());
        }
      }
    }
  }

  @Override
  public void onScan(final ParseEvents.Scan event) {
    if (filter.contains(Type.SCAN)) {
      events.add(event);
      if (log) {
        if (event.matched()) {
          LOGGER.debug(
              "SCAN[{}]: '{}' matched", event.position(), event.token().text());
        } else {
          LOGGER.debug(
              "SCAN[{}]: '{}' not matched", event.position(), event.token().text());
        }
      }
    }
  }

  @Override
  public void onComplete(final ParseEvents.Complete event) {
    if (filter.contains(Type.COMPLETE)) {
      events.add(event);
      if (log) {
        LOGGER.debug(
            "COMPLETE[{}]: {} (waiting={}, advanced={})",
            event.position(),
            event.completed().rule().lhs().symbol(),
            event.waiting().size(),
            event.advanced().size());
      }
    }
  }

  @Override
  public void onUnification(final ParseEvents.Unification event) {
    if (filter.contains(Type.UNIFICATION)) {
      events.add(event);
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
      events.add(event);
      if (log) {
        LOGGER.debug(
            "CONSTRAINT[{}]: {} {} - {}",
            event.position(),
            event.phase(),
            event.result().passed() ? "PASSED" : "FAILED",
            event.result().reason());
      }
    }
  }

  @Override
  public void onPosition(final ParseEvents.Position event) {
    if (filter.contains(Type.POSITION)) {
      events.add(event);
      if (log) {
        LOGGER.debug(
            "POSITION[{}]: {} items ({} complete, {} incomplete)",
            event.position(),
            event.items().size(),
            event.complete(),
            event.incomplete());
      }
    }
  }

  @Override
  public void onUnexpected(final ParseEvents.Unexpected event) {
    if (filter.contains(Type.UNEXPECTED)) {
      events.add(event);
      if (log) {
        LOGGER.warn(
            "UNEXPECTED[{}]: '{}' expected: {}",
            event.position(),
            event.token(),
            event.expected());
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

  private String display(final com.libdbm.ugf.grammar.GrammarRule rule) {
    final var sb = new StringBuilder();
    sb.append(rule.lhs().symbol()).append(" →");
    for (final var elem : rule.rhs()) {
      sb.append(" ").append(elem);
    }
    return sb.toString();
  }

  /** Event types that can be filtered. */
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
