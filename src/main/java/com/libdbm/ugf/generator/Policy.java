package com.libdbm.ugf.generator;

import java.util.Objects;

/**
 * Generation policy (GEN-7).
 *
 * @param depth the deepest derivation tried
 * @param attempts sentence attempts per requested sentence before giving up
 * @param repetitions the most times a repetition ({@code * +}) repeats
 * @param length the longest sentence returned, in characters
 * @param steps the most symbol expansions in one attempt, which bounds backtracking
 * @param joiner joins tokens into a sentence
 */
public record Policy(
    int depth, int attempts, int repetitions, int length, int steps, Joiner joiner) {

  /**
   * Depth 20, 10 attempts, up to 3 repetitions, 10,000 characters, 100,000 steps, {@link
   * Joiner#SPACED}.
   */
  public static final Policy DEFAULT = new Policy(20, 10, 3, 10_000, 100_000, Joiner.SPACED);

  public Policy {
    Objects.requireNonNull(joiner, "joiner");
    if (depth < 1 || attempts < 1 || repetitions < 0 || length < 1 || steps < 1) {
      throw new IllegalArgumentException("invalid generation policy");
    }
  }

  public Policy depth(final int value) {
    return new Policy(value, attempts, repetitions, length, steps, joiner);
  }

  public Policy joiner(final Joiner value) {
    return new Policy(depth, attempts, repetitions, length, steps, value);
  }
}
