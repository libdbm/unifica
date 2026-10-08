package com.libdbm.ugf.compiler;

import com.libdbm.ugf.features.Structure;
import java.util.Objects;

/** An element of a compiled syntactic production's right-hand side. */
public sealed interface Element {

  /** The label that binds this element in the production's constraints, or {@code null}. */
  String label();

  /**
   * A named symbol. It is satisfied by a syntactic production of {@code name}, or by a token whose
   * category is {@code name} (a named lexeme, which appears in the tree as a node, S-P8).
   */
  record Symbol(String name, String label, Structure features) implements Element {
    public Symbol {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(features, "features");
    }
  }

  /** A token of an anonymous category: an inline literal or regex (S-G3). It appears as a leaf. */
  record Terminal(String category, String label) implements Element {
    public Terminal {
      Objects.requireNonNull(category, "category");
    }
  }

  /**
   * Any token ({@code {TOKEN}}), whose features must unify with {@code features} (S-F5); a token
   * with features of its own that conflict does not fill it.
   */
  record Token(String label, Structure features) implements Element {
    public Token {
      Objects.requireNonNull(features, "features");
    }
  }
}
