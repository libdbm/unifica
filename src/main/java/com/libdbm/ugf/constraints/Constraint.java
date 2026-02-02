package com.libdbm.ugf.constraints;

import java.util.List;

/**
 * Constraints that must be satisfied during parsing. Evaluated in the "where" clause of grammar
 * rules. All constraints support defeasible logic with strength and priority.
 *
 * <p>All constraint checking is done via predicates. Built-in predicates include:
 *
 * <ul>
 *   <li>Category checking: type(X), noun(X), verb(X), etc.
 *   <li>Agreement: agree(X, Y) - unifies feature structures
 *   <li>Feature matching: feature_eq(X, f, Y, g) - checks if X.f = Y.g
 * </ul>
 */
public sealed interface Constraint
    permits Constraint.And, Constraint.Not, Constraint.Or, Predicate {

  Strength strength();

  int priority();

  /** Logical conjunction */
  record And(List<Constraint> conjuncts, Strength strength, int priority) implements Constraint {
    public And(final List<Constraint> conjuncts) {
      this(conjuncts, Strength.REQUIRED, 0);
    }

    public static And defeasible(final List<Constraint> conjuncts, final int priority) {
      return new And(conjuncts, Strength.DEFEASIBLE, priority);
    }
  }

  /** Logical disjunction */
  record Or(List<Constraint> disjuncts, Strength strength, int priority) implements Constraint {
    public Or(final List<Constraint> disjuncts) {
      this(disjuncts, Strength.REQUIRED, 0);
    }

    public static Or defeasible(final List<Constraint> disjuncts, final int priority) {
      return new Or(disjuncts, Strength.DEFEASIBLE, priority);
    }
  }

  /** Logical negation */
  record Not(Constraint constraint, Strength strength, int priority) implements Constraint {
    public Not(final Constraint constraint) {
      this(constraint, Strength.REQUIRED, 0);
    }

    public static Not defeasible(final Constraint constraint, final int priority) {
      return new Not(constraint, Strength.DEFEASIBLE, priority);
    }
  }
}
