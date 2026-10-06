package com.libdbm.ugf.parser;

import com.libdbm.ugf.lexer.Edge;

/** What a {@link State.Link} advances over: a token edge or a completed constituent. */
sealed interface Child permits State, Child.Token {

  /** A token edge of the graph. */
  record Token(Edge edge) implements Child {}

  /** Whether two children are the same edge or the same constituent, compared by identity. */
  static boolean same(final Child a, final Child b) {
    return a == b || a instanceof Token(var x) && b instanceof Token(var y) && x == y;
  }
}
