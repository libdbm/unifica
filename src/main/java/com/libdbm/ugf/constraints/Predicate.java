package com.libdbm.ugf.constraints;

import java.util.List;
import java.util.Objects;
import com.libdbm.ugf.features.Value;

public record Predicate(String name, List<Value> args, Strength strength, int priority)
    implements Constraint {

  public Predicate(final String name, final List<Value> args, Strength strength, int priority) {
    this.strength = strength;
    this.priority = priority;
    Objects.requireNonNull(name);
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    this.name = name;
    this.args = args;
  }

  public static Predicate of(final String name, final List<Value> args) {
    return new Predicate(name, args, Strength.REQUIRED, 0);
  }

  public static Predicate of(final String name, final List<Value> args, final int priority) {
    return new Predicate(name, args, Strength.REQUIRED, priority);
  }

  public static Predicate of(final String name, final List<Value> args, final Strength strength) {
    return new Predicate(name, args, strength, 0);
  }

  public static Predicate of(
      final String name, final List<Value> args, final Strength strength, final int priority) {
    return new Predicate(name, args, strength, priority);
  }
}
