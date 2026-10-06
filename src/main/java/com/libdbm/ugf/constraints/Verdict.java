package com.libdbm.ugf.constraints;

/** The outcome of evaluating a {@link Plan}. */
public sealed interface Verdict {

  /**
   * Every required expression held; {@code penalty} is the sum of the weights of false soft groups.
   */
  record Accepted(long penalty) implements Verdict {}

  /** A required expression was false. */
  record Rejected(Expression failed) implements Verdict {}

  /** The penalty does not fit in 64 bits; the parse ends with the {@code limit} outcome (S-C10). */
  record Overflow() implements Verdict {}
}
