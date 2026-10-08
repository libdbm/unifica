package com.libdbm.ugf.features;

import java.util.Objects;

/**
 * Represents a unification variable (e.g., ?x, ?n). Variables are used for feature unification and
 * must be bound during parsing.
 *
 * @param name The variable name (without the ? prefix)
 */
public record Variable(String name) implements Value {

  public Variable {
    Objects.requireNonNull(name, "name must not be null");
  }

  /** Creates a variable from a string that may or may not have ? prefix. */
  public static Variable of(final String str) {
    Objects.requireNonNull(str, "str must not be null");
    return new Variable(str.startsWith("?") ? str.substring(1) : str);
  }

  @Override
  public boolean isVariable() {
    return true;
  }

  @Override
  public boolean isAtomic() {
    return false;
  }

  /**
   * Shows the variable as {@code ?name}, without the {@code $} a grammar variable is named with.
   */
  @Override
  public String display() {
    return "?" + (name.startsWith("$") ? name.substring(1) : name);
  }

  @Override
  public String toString() {
    return display();
  }
}
