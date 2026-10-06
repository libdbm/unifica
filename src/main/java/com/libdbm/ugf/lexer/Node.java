package com.libdbm.ugf.lexer;

import java.util.List;

/**
 * A token graph node: a character offset and the lexical state stack in force there (S-L1).
 *
 * @param id position in the graph's node list, which is a topological order
 * @param offset the character offset
 * @param states the state stack, bottom to top; never empty
 */
public record Node(int id, int offset, List<String> states) {
  public Node {
    states = List.copyOf(states);
    if (states.isEmpty()) {
      throw new IllegalArgumentException("the state stack is never empty");
    }
  }
}
