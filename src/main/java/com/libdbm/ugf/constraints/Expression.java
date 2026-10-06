package com.libdbm.ugf.constraints;

import com.libdbm.ugf.features.Value;
import java.util.List;
import java.util.Objects;

/**
 * A constraint expression, evaluated for Boolean truth only (S-C1). A {@link Weighted} node records
 * a weight written in grammar source ({@code pred():3}, {@code (...):7}); compilation turns
 * weighted top-level conjuncts into the {@link Soft} groups of a {@link Plan} (S-C2, S-C6), and no
 * weight survives inside a plan's expressions.
 */
public sealed interface Expression {

  /** Conjunction: true when every term is true. */
  record And(List<Expression> terms) implements Expression {
    public And {
      terms = List.copyOf(terms);
    }
  }

  /** Disjunction: true when any term is true. */
  record Or(List<Expression> terms) implements Expression {
    public Or {
      terms = List.copyOf(terms);
    }
  }

  /** A weight on an expression, as written in grammar source; truth ignores it. */
  record Weighted(Expression term, long weight) implements Expression {
    public Weighted {
      Objects.requireNonNull(term, "term");
    }
  }

  /** Negation. */
  record Not(Expression term) implements Expression {
    public Not {
      Objects.requireNonNull(term, "term");
    }
  }

  /** A predicate call, such as {@code equals(a, 'b')}. */
  record Call(String name, List<Value> args) implements Expression {
    public Call {
      Objects.requireNonNull(name, "name");
      args = List.copyOf(args);
    }
  }

  /** A truth value, produced when part of an expression has already been evaluated (S-C7). */
  record Literal(boolean value) implements Expression {
    public static final Literal TRUE = new Literal(true);
    public static final Literal FALSE = new Literal(false);

    public static Literal of(final boolean value) {
      return value ? TRUE : FALSE;
    }
  }
}
