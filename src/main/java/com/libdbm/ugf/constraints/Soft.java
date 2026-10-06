package com.libdbm.ugf.constraints;

import java.util.Objects;

/**
 * A soft group: an expression with a positive weight. When the expression is false the derivation
 * is kept and the weight is added to its penalty, once (S-C4).
 *
 * @param expression the group's expression, evaluated for truth only
 * @param weight the penalty charged when the expression is false
 */
public record Soft(Expression expression, long weight) {
  public Soft {
    Objects.requireNonNull(expression, "expression");
    if (weight <= 0) {
      throw new IllegalArgumentException("weight must be positive: " + weight);
    }
  }
}
