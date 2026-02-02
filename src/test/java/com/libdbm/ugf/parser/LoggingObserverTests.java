package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.constraints.Result;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.RuleElement;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for LoggingObserver. */
class LoggingObserverTests {

  private LoggingObserver observer;

  @BeforeEach
  void setup() {
    observer = LoggingObserver.quiet(); // Quiet mode to avoid log noise in tests
  }

  private Grammar empty() {
    return Grammar.builder().build();
  }

  @Nested
  @DisplayName("Construction")
  class Construction {

    @Test
    @DisplayName("default constructor enables all types")
    void default_enables_all_types() {
      final var obs = new LoggingObserver();
      // Just verify it doesn't throw
      assertNotNull(obs);
    }

    @Test
    @DisplayName("filtered constructor accepts specific types")
    void filtered_constructor() {
      final var filter = EnumSet.of(LoggingObserver.Type.START, LoggingObserver.Type.END);
      final var obs = new LoggingObserver(filter);
      assertNotNull(obs);
    }

    @Test
    @DisplayName("quiet factory creates non-logging observer")
    void quiet_factory() {
      final var obs = LoggingObserver.quiet();
      assertNotNull(obs);
    }

    @Test
    @DisplayName("quiet factory with filter")
    void quiet_with_filter() {
      final var filter = EnumSet.of(LoggingObserver.Type.SCAN);
      final var obs = LoggingObserver.quiet(filter);
      assertNotNull(obs);
    }
  }

  @Nested
  @DisplayName("Event collection")
  class EventCollection {

    @Test
    @DisplayName("collects Start events")
    void collects_start_events() {
      final var grammar = Grammar.builder().start("S").build();
      final var event = new ParseEvents.Start(List.of(List.of(Token.of("x", 0, 1))), grammar, "S");

      observer.onStart(event);

      assertEquals(1, observer.events().size());
      assertEquals(1, observer.count(LoggingObserver.Type.START));
    }

    @Test
    @DisplayName("collects End events")
    void collects_end_events() {
      final var result = new ParseResult(null, 0);
      final var event = new ParseEvents.End(result, Duration.ofMillis(100));

      observer.onEnd(event);

      assertEquals(1, observer.events().size());
      assertEquals(1, observer.count(LoggingObserver.Type.END));
    }

    @Test
    @DisplayName("collects Predict events")
    void collects_predict_events() {
      final var rule = new GrammarRule("S", List.of(new RuleElement.Terminal("x")));
      final var trigger = new RuleElement.Nonterminal("S");
      final var event = new ParseEvents.Predict(0, trigger, rule, true, null, null);

      observer.onPredict(event);

      assertEquals(1, observer.events().size());
      assertEquals(1, observer.count(LoggingObserver.Type.PREDICT));
    }

    @Test
    @DisplayName("collects Scan events")
    void collects_scan_events() {
      final var token = Token.of("word", 0, 4);
      final var event = new ParseEvents.Scan(0, null, token, true, null, null);

      observer.onScan(event);

      assertEquals(1, observer.events().size());
      assertEquals(1, observer.count(LoggingObserver.Type.SCAN));
    }

    @Test
    @DisplayName("collects Complete events")
    void collects_complete_events() {
      final var event = new ParseEvents.Complete(5, null, List.of(), List.of(), Result.ok());

      observer.onComplete(event);

      assertEquals(1, observer.events().size());
      assertEquals(1, observer.count(LoggingObserver.Type.COMPLETE));
    }

    @Test
    @DisplayName("collects Unification events")
    void collects_unification_events() {
      final var event =
          new ParseEvents.Unification(
              0,
              new Structure(),
              new Structure(),
              new Structure(),
              Optional.of(new Structure()),
              Map.of());

      observer.onUnification(event);

      assertEquals(1, observer.events().size());
      assertEquals(1, observer.count(LoggingObserver.Type.UNIFICATION));
    }

    @Test
    @DisplayName("collects ConstraintEval events")
    void collects_constraint_events() {
      final var rule = new GrammarRule("S", List.of());
      final var event =
          new ParseEvents.ConstraintEval(
              0, rule, List.of(), Result.ok(), ParseEvents.ConstraintEval.Phase.PREDICT);

      observer.onConstraint(event);

      assertEquals(1, observer.events().size());
      assertEquals(1, observer.count(LoggingObserver.Type.CONSTRAINT));
    }

    @Test
    @DisplayName("collects Position events")
    void collects_position_events() {
      final var event = new ParseEvents.Position(0, List.of(), 5, 3);

      observer.onPosition(event);

      assertEquals(1, observer.events().size());
      assertEquals(1, observer.count(LoggingObserver.Type.POSITION));
    }

    @Test
    @DisplayName("collects Unexpected events")
    void collects_unexpected_events() {
      final var event = new ParseEvents.Unexpected(0, "bad", Set.of("good", "better"));

      observer.onUnexpected(event);

      assertEquals(1, observer.events().size());
      assertEquals(1, observer.count(LoggingObserver.Type.UNEXPECTED));
    }
  }

  @Nested
  @DisplayName("Filtering")
  class Filtering {

    @Test
    @DisplayName("filters events by type")
    void filters_by_type() {
      final var obs = LoggingObserver.quiet(EnumSet.of(LoggingObserver.Type.SCAN));

      // Should be collected
      obs.onScan(new ParseEvents.Scan(0, null, Token.of("x", 0, 1), true, null, null));

      // Should be filtered out
      obs.onPosition(new ParseEvents.Position(0, List.of(), 0, 0));

      assertEquals(1, obs.events().size());
      assertEquals(1, obs.count(LoggingObserver.Type.SCAN));
      assertEquals(0, obs.count(LoggingObserver.Type.POSITION));
    }
  }

  @Nested
  @DisplayName("Event retrieval")
  class EventRetrieval {

    @Test
    @DisplayName("events returns all collected events")
    void events_returns_all() {
      observer.onStart(new ParseEvents.Start(List.of(), empty(), "S"));
      observer.onPosition(new ParseEvents.Position(0, List.of(), 0, 0));

      assertEquals(2, observer.events().size());
    }

    @Test
    @DisplayName("events returns immutable copy")
    void events_returns_copy() {
      observer.onPosition(new ParseEvents.Position(0, List.of(), 0, 0));

      assertThrows(UnsupportedOperationException.class, () -> observer.events().clear());
    }

    @Test
    @DisplayName("events by class filters by type")
    void events_by_class() {
      observer.onStart(new ParseEvents.Start(List.of(), empty(), "S"));
      observer.onPosition(new ParseEvents.Position(0, List.of(), 0, 0));
      observer.onPosition(new ParseEvents.Position(1, List.of(), 1, 0));

      final var positions = observer.events(ParseEvents.Position.class);

      assertEquals(2, positions.size());
      assertTrue(positions.stream().allMatch(e -> e instanceof ParseEvents.Position));
    }
  }

  @Nested
  @DisplayName("Clear")
  class Clear {

    @Test
    @DisplayName("clear removes all events")
    void clear_removes_events() {
      observer.onPosition(new ParseEvents.Position(0, List.of(), 0, 0));
      observer.onPosition(new ParseEvents.Position(1, List.of(), 0, 0));

      assertEquals(2, observer.events().size());

      observer.clear();

      assertTrue(observer.events().isEmpty());
    }
  }

  @Nested
  @DisplayName("Count")
  class Count {

    @Test
    @DisplayName("count returns count for specific type")
    void count_returns_type_count() {
      observer.onStart(new ParseEvents.Start(List.of(), empty(), "S"));
      observer.onPosition(new ParseEvents.Position(0, List.of(), 0, 0));
      observer.onPosition(new ParseEvents.Position(1, List.of(), 0, 0));

      assertEquals(1, observer.count(LoggingObserver.Type.START));
      assertEquals(2, observer.count(LoggingObserver.Type.POSITION));
      assertEquals(0, observer.count(LoggingObserver.Type.SCAN));
    }

    @Test
    @DisplayName("count handles all event types")
    void count_handles_all_types() {
      // Verify count works for all event types
      for (final var type : LoggingObserver.Type.values()) {
        assertEquals(0, observer.count(type));
      }
    }
  }
}
