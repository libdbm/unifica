package com.libdbm.ugf.compiler;

import com.libdbm.ugf.constraints.Plan;
import com.libdbm.ugf.features.Structure;
import java.util.List;
import java.util.Objects;

/**
 * A compiled syntactic production. After compilation no repetition, alternation, regex or state
 * annotation remains in a right-hand side (S-G4).
 *
 * @param id stable identifier in compilation order; tie-breaking uses it (S-P3)
 * @param symbol the left-hand symbol
 * @param features the left-hand features
 * @param rhs the right-hand side
 * @param plan the production's constraints
 * @param cost the production cost (S-P7)
 * @param auxiliary true for a production introduced by lowering, which does not appear in trees
 *     (S-P8)
 */
public record Production(
    int id,
    String symbol,
    Structure features,
    List<Element> rhs,
    Plan plan,
    long cost,
    boolean auxiliary) {

  public Production {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(features, "features");
    Objects.requireNonNull(plan, "plan");
    rhs = List.copyOf(rhs);
  }
}
