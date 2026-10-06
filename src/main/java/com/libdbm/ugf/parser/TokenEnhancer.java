package com.libdbm.ugf.parser;

import com.libdbm.ugf.lexer.Graph;

/**
 * Transforms a token graph before parsing (LEX-8), for example to add features to tokens or
 * alternative categories from an external tagger.
 */
@FunctionalInterface
public interface TokenEnhancer {

  static TokenEnhancer identity() {
    return graph -> graph;
  }

  static TokenEnhancer pipeline(final TokenEnhancer... enhancers) {
    var result = identity();
    for (final var enhancer : enhancers) {
      result = result.andThen(enhancer);
    }
    return result;
  }

  Graph enhance(Graph graph);

  default TokenEnhancer andThen(final TokenEnhancer after) {
    return graph -> after.enhance(this.enhance(graph));
  }
}
