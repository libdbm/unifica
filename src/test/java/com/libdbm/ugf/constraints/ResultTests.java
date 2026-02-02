package com.libdbm.ugf.constraints;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for Result. */
class ResultTests {

  @Nested
  @DisplayName("Factory methods")
  class FactoryMethods {

    @Test
    @DisplayName("ok returns passed result with null reason")
    void ok_returns_passed() {
      final var r = Result.ok();

      assertTrue(r.passed());
      assertNull(r.reason());
      assertEquals(0, r.penalty());
    }

    @Test
    @DisplayName("fail returns failed result with reason")
    void fail_returns_failed() {
      final var r = Result.fail("something went wrong");

      assertFalse(r.passed());
      assertEquals("something went wrong", r.reason());
      assertEquals(0, r.penalty());
    }

    @Test
    @DisplayName("result with true returns ok")
    void result_true_returns_ok() {
      final var r = Result.result(true, "ignored reason");

      assertTrue(r.passed());
      assertNull(r.reason());
    }

    @Test
    @DisplayName("result with false returns fail with reason")
    void result_false_returns_fail() {
      final var r = Result.result(false, "the reason");

      assertFalse(r.passed());
      assertEquals("the reason", r.reason());
    }
  }

  @Nested
  @DisplayName("Constructors")
  class Constructors {

    @Test
    @DisplayName("two-arg constructor sets penalty to 0")
    void two_arg_constructor() {
      final var r = new Result(true, "note");

      assertTrue(r.passed());
      assertEquals("note", r.reason());
      assertEquals(0, r.penalty());
    }

    @Test
    @DisplayName("three-arg constructor sets all fields")
    void three_arg_constructor() {
      final var r = new Result(false, "failed", 5);

      assertFalse(r.passed());
      assertEquals("failed", r.reason());
      assertEquals(5, r.penalty());
    }
  }

  @Nested
  @DisplayName("Record behavior")
  class RecordBehavior {

    @Test
    @DisplayName("equals and hashCode work correctly")
    void equals_hashcode() {
      final var r1 = new Result(true, "ok", 0);
      final var r2 = new Result(true, "ok", 0);
      final var r3 = new Result(false, "ok", 0);

      assertEquals(r1, r2);
      assertEquals(r1.hashCode(), r2.hashCode());
      assertNotEquals(r1, r3);
    }

    @Test
    @DisplayName("different penalties are not equal")
    void different_penalties() {
      final var r1 = new Result(true, null, 0);
      final var r2 = new Result(true, null, 5);

      assertNotEquals(r1, r2);
    }

    @Test
    @DisplayName("different reasons are not equal")
    void different_reasons() {
      final var r1 = Result.fail("reason1");
      final var r2 = Result.fail("reason2");

      assertNotEquals(r1, r2);
    }
  }
}
