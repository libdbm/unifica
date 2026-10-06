package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.libdbm.ugf.compiler.Compiled;
import com.libdbm.ugf.compiler.Compiler;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for LoggingObserver. */
class LoggingObserverTests {

  private static final String AGREEMENT =
      "start S; S --> N{num: X} V{num: X} where equals(X, X);"
          + " N{num: sg} --> 'dog'; V{num: sg} --> 'runs';";

  private LoggingObserver observer;

  @BeforeEach
  void setup() {
    observer = LoggingObserver.quiet(); // Quiet mode to avoid log noise in tests
  }

  private static Compiled compile(final String source) {
    return Compiler.compile(
            UnificationGrammarParserFactory.unvalidated(source).orElseThrow(),
            Predicates.standard())
        .orElseThrow();
  }

  private static ParseEvents.Start start() {
    return new ParseEvents.Start(
        Parser.of(compile(AGREEMENT)).tokenize("dog runs").orElseThrow(), "S");
  }

  @Nested
  @DisplayName("Construction")
  class Construction {

    @Test
    void testDefaultConstructor() {
      assertNotNull(new LoggingObserver());
    }

    @Test
    void testFilteredConstructor() {
      assertNotNull(
          new LoggingObserver(EnumSet.of(LoggingObserver.Type.START, LoggingObserver.Type.END)));
    }

    @Test
    void testQuietFactory() {
      assertNotNull(LoggingObserver.quiet());
    }

    @Test
    void testQuietWithFilter() {
      assertNotNull(LoggingObserver.quiet(EnumSet.of(LoggingObserver.Type.SCAN)));
    }

    /** The logging path formats every event type without failing. */
    @Test
    void testLoggingParse() {
      final var logging = new LoggingObserver();

      Parser.of(compile(AGREEMENT), new Options(Limits.NONE, logging, false)).parse("dog runs");

      assertTrue(logging.count(LoggingObserver.Type.END) > 0);
    }
  }

  @Nested
  @DisplayName("Event collection")
  class EventCollection {

    @Test
    void testCollectsStart() {
      observer.onStart(start());

      assertEquals(1, observer.events().size());
      assertEquals(1, observer.count(LoggingObserver.Type.START));
    }

    @Test
    void testCollectsEnd() {
      observer.onEnd(
          new ParseEvents.End(
              new ParseResult(Outcome.REJECTED, null, 0, false, null, null, null),
              Duration.ofMillis(100)));

      assertEquals(1, observer.count(LoggingObserver.Type.END));
    }

    @Test
    void testCollectsPredict() {
      observer.onPredict(new ParseEvents.Predict(0, "S", 0));

      assertEquals(1, observer.count(LoggingObserver.Type.PREDICT));
    }

    @Test
    void testCollectsScan() {
      observer.onScan(new ParseEvents.Scan(0, "word", "dog"));

      assertEquals(1, observer.count(LoggingObserver.Type.SCAN));
    }

    @Test
    void testCollectsComplete() {
      observer.onComplete(new ParseEvents.Complete(0, 5, "S", 0, 0));

      assertEquals(1, observer.count(LoggingObserver.Type.COMPLETE));
    }

    @Test
    void testCollectsUnification() {
      observer.onUnification(
          new ParseEvents.Unification(
              0,
              Structure.EMPTY,
              Structure.EMPTY,
              Structure.EMPTY,
              Optional.of(Structure.EMPTY),
              Map.of()));

      assertEquals(1, observer.count(LoggingObserver.Type.UNIFICATION));
    }

    @Test
    void testCollectsConstraint() {
      observer.onConstraint(new ParseEvents.ConstraintEval(0, "S", "equals(X, X)", true, 0));

      assertEquals(1, observer.count(LoggingObserver.Type.CONSTRAINT));
    }

    @Test
    void testCollectsPosition() {
      observer.onPosition(new ParseEvents.Position(0, 5));

      assertEquals(1, observer.count(LoggingObserver.Type.POSITION));
    }

    @Test
    void testCollectsUnexpected() {
      observer.onUnexpected(new ParseEvents.Unexpected(0, "bad", Set.of("good", "better")));

      assertEquals(1, observer.count(LoggingObserver.Type.UNEXPECTED));
    }

    /** A real parse delivers each kind of event the grammar exercises. */
    @Test
    void testCollectsFromParse() {
      Parser.of(compile(AGREEMENT), new Options(Limits.NONE, observer, false)).parse("dog runs");

      assertEquals(1, observer.count(LoggingObserver.Type.START));
      assertEquals(1, observer.count(LoggingObserver.Type.END));
      for (final var type :
          EnumSet.of(
              LoggingObserver.Type.PREDICT,
              LoggingObserver.Type.SCAN,
              LoggingObserver.Type.COMPLETE,
              LoggingObserver.Type.UNIFICATION,
              LoggingObserver.Type.CONSTRAINT,
              LoggingObserver.Type.POSITION)) {
        assertTrue(observer.count(type) > 0, type.name());
      }
    }
  }

  @Nested
  @DisplayName("Filtering")
  class Filtering {

    @Test
    void testFiltersByType() {
      final var filtered = LoggingObserver.quiet(EnumSet.of(LoggingObserver.Type.SCAN));

      filtered.onScan(new ParseEvents.Scan(0, "word", "x"));
      filtered.onPosition(new ParseEvents.Position(0, 0));

      assertEquals(1, filtered.events().size());
      assertEquals(1, filtered.count(LoggingObserver.Type.SCAN));
      assertEquals(0, filtered.count(LoggingObserver.Type.POSITION));
    }
  }

  @Nested
  @DisplayName("Event retrieval")
  class EventRetrieval {

    @Test
    void testEventsReturnsAll() {
      observer.onStart(start());
      observer.onPosition(new ParseEvents.Position(0, 0));

      assertEquals(2, observer.events().size());
    }

    @Test
    void testEventsReturnsCopy() {
      observer.onPosition(new ParseEvents.Position(0, 0));

      assertThrows(UnsupportedOperationException.class, () -> observer.events().clear());
    }

    @Test
    void testEventsByClass() {
      observer.onStart(start());
      observer.onPosition(new ParseEvents.Position(0, 0));
      observer.onPosition(new ParseEvents.Position(1, 0));

      final var positions = observer.events(ParseEvents.Position.class);

      assertEquals(2, positions.size());
    }
  }

  @Nested
  @DisplayName("Clear and count")
  class Counting {

    @Test
    void testClearRemovesEvents() {
      observer.onPosition(new ParseEvents.Position(0, 0));
      observer.onPosition(new ParseEvents.Position(1, 0));

      observer.clear();

      assertTrue(observer.events().isEmpty());
    }

    @Test
    void testCountByType() {
      observer.onStart(start());
      observer.onPosition(new ParseEvents.Position(0, 0));
      observer.onPosition(new ParseEvents.Position(1, 0));

      assertEquals(1, observer.count(LoggingObserver.Type.START));
      assertEquals(2, observer.count(LoggingObserver.Type.POSITION));
      assertEquals(0, observer.count(LoggingObserver.Type.SCAN));
    }

    @Test
    void testCountEmpty() {
      for (final var type : LoggingObserver.Type.values()) {
        assertEquals(0, observer.count(type));
      }
    }
  }

  /** Review: collected events are bounded even when nothing is logged. */
  @Test
  void testCapacityBoundsEvents() {
    final var bounded = new LoggingObserver(EnumSet.allOf(LoggingObserver.Type.class), false, 10);
    for (var index = 0; index < 25; index++) {
      bounded.onPosition(new ParseEvents.Position(index, 0));
    }

    assertEquals(10, bounded.events().size());
    assertEquals(15, bounded.dropped());
  }
}
