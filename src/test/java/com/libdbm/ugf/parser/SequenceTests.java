package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SequenceTests {

  private record Pair(Sequence sequence, List<Long> list) {}

  private static int signum(final int value) {
    return Integer.signum(value);
  }

  private static int compare(final List<Long> a, final List<Long> b) {
    for (var index = 0; index < Math.min(a.size(), b.size()); index++) {
      final var result = Long.compare(a.get(index), b.get(index));
      if (result != 0) {
        return result;
      }
    }
    return Integer.compare(a.size(), b.size());
  }

  /** Random concatenations, including shared parts, order like the lists they represent. */
  @Test
  void testOrderMatchesLists() {
    final var random = new Random(11);
    final var pool = new ArrayList<Pair>();
    pool.add(new Pair(Sequence.EMPTY, List.of()));
    for (var index = 0; index < 400; index++) {
      if (random.nextInt(3) == 0) {
        final var value = (long) random.nextInt(4);
        pool.add(new Pair(Sequence.of(value), List.of(value)));
      } else {
        final var a = pool.get(random.nextInt(pool.size()));
        final var b = pool.get(random.nextInt(pool.size()));
        final var list = new ArrayList<>(a.list());
        list.addAll(b.list());
        pool.add(new Pair(a.sequence().then(b.sequence()), list));
      }
    }
    for (final var a : pool) {
      for (final var b : pool) {
        assertEquals(
            signum(compare(a.list(), b.list())),
            signum(a.sequence().compareTo(b.sequence())),
            a.list() + " vs " + b.list());
      }
      assertEquals(a.list().size(), a.sequence().size());
    }
  }

  @Test
  void testPrefixComesFirst() {
    final var prefix = Sequence.of(1).then(2);

    assertEquals(-1, signum(prefix.compareTo(prefix.then(0))));
    assertEquals(1, signum(prefix.then(0).compareTo(prefix)));
    assertEquals(0, prefix.compareTo(Sequence.of(1).then(Sequence.of(2))));
  }

  /** A deep concatenation, as a long chain builds, compares without exhausting the stack. */
  @Test
  void testDeepSequence() {
    var left = Sequence.EMPTY;
    var right = Sequence.EMPTY;
    for (var index = 0; index < 100_000; index++) {
      left = left.then(index);
      right = Sequence.of(index).then(right);
    }

    assertEquals(0, left.compareTo(left.then(Sequence.EMPTY)));
    assertEquals(1, signum(right.compareTo(left)));
  }
}
