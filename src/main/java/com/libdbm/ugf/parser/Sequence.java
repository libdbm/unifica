package com.libdbm.ugf.parser;

import java.util.ArrayDeque;

/**
 * An immutable sequence of numbers built by concatenation, used for the keys that order derivations
 * (S-P3). Concatenation shares both parts instead of copying them, so a derivation's keys cost
 * constant space on top of its parts' keys; comparison skips parts the two sequences share.
 */
final class Sequence implements Comparable<Sequence> {

  static final Sequence EMPTY = new Sequence(null, null, 0, 0);

  private final Sequence left;
  private final Sequence right;
  private final long value;
  private final int size;

  private Sequence(final Sequence left, final Sequence right, final long value, final int size) {
    this.left = left;
    this.right = right;
    this.value = value;
    this.size = size;
  }

  static Sequence of(final long value) {
    return new Sequence(null, null, value, 1);
  }

  private static void push(final ArrayDeque<Sequence> stack, final Sequence sequence) {
    if (sequence.size > 0) {
      stack.push(sequence);
    }
  }

  int size() {
    return size;
  }

  /** This sequence followed by {@code value}. */
  Sequence then(final long value) {
    return then(of(value));
  }

  /** This sequence followed by {@code other}. */
  Sequence then(final Sequence other) {
    if (other.size == 0) {
      return this;
    }
    if (size == 0) {
      return other;
    }
    return new Sequence(this, other, 0, size + other.size);
  }

  /** Lexicographic order; a proper prefix comes first. */
  @Override
  public int compareTo(final Sequence other) {
    final var result = differ(other);
    return result != 0 ? result : Integer.compare(size, other.size);
  }

  /**
   * The order at the first position where the two sequences differ, or 0 if one is a prefix of the
   * other. A nonzero result holds whatever is appended to both.
   */
  int differ(final Sequence other) {
    final var mine = new ArrayDeque<Sequence>();
    final var theirs = new ArrayDeque<Sequence>();
    push(mine, this);
    push(theirs, other);
    while (!mine.isEmpty() && !theirs.isEmpty()) {
      final var a = mine.peek();
      final var b = theirs.peek();
      if (a == b) {
        // A shared part is equal and has the same length, so both sides stay aligned.
        mine.pop();
        theirs.pop();
      } else if (a.left != null) {
        mine.pop();
        push(mine, a.right);
        push(mine, a.left);
      } else if (b.left != null) {
        theirs.pop();
        push(theirs, b.right);
        push(theirs, b.left);
      } else {
        mine.pop();
        theirs.pop();
        final var result = Long.compare(a.value, b.value);
        if (result != 0) {
          return result;
        }
      }
    }
    return 0;
  }
}
