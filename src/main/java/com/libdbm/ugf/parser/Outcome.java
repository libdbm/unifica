package com.libdbm.ugf.parser;

/** How a parse ended (S-P5). Only {@link #ACCEPTED} carries a tree. */
public enum Outcome {
  /** A derivation of the start symbol covers the input. */
  ACCEPTED,
  /** No derivation covers the input. */
  REJECTED,
  /** A configured resource limit was reached, or a penalty overflowed 64 bits. */
  LIMIT,
  /** The parsing thread was interrupted. */
  CANCELLED
}
