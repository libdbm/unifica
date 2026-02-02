package com.libdbm.ugf.features;

/**
 * Represents a feature value in a feature structure. Can be an atomic value, a variable, or a
 * nested feature structure.
 */
public sealed interface Value permits Constant, Variable, Structure, Binding, FeaturePath {

  /** Checks if this value is a variable. */
  boolean isVariable();

  /** Checks if this value is atomic. */
  boolean isAtomic();

  /** Returns a string representation for display. */
  String display();
}
