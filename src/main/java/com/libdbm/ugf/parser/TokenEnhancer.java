package com.libdbm.ugf.parser;

import java.util.List;

/**
 * Interface for enhancing tokens with additional information such as POS tags, lemmas, or
 * domain-specific features.
 *
 * <p>TokenEnhancers form a pipeline where each enhancer can add information to tokens before they
 * are consumed by the parser.
 *
 * <p>Examples: - POS tagger: Adds part-of-speech name to tokens - Lemmatizer: Adds lemma feature -
 * Named entity recognizer: Adds entity type - Domain-specific annotator: Adds domain features
 */
@FunctionalInterface
public interface TokenEnhancer {

  /**
   * Create an enhancer that does nothing (identity function). Useful as a default or starting point
   * for pipelines.
   */
  static TokenEnhancer identity() {
    return tokens -> tokens;
  }

  /**
   * Create a pipeline from multiple enhancers. Enhancers are applied in order from first to last.
   *
   * @param enhancers The enhancers to chain together
   * @return A single enhancer that applies all in sequence
   */
  static TokenEnhancer pipeline(final TokenEnhancer... enhancers) {
    var result = identity();
    for (final var enhancer : enhancers) {
      result = result.andThen(enhancer);
    }
    return result;
  }

  /**
   * Enhance a list of tokens with additional information. The enhancer may modify tokens in place
   * or return new token instances.
   *
   * @param tokens The tokens to enhance (may be modified in place)
   * @return The enhanced tokens (may be the same list or a new list)
   */
  List<Token> enhance(List<Token> tokens);

  /**
   * Chain this enhancer with another to form a pipeline. The other enhancer runs after this one.
   *
   * @param after The enhancer to run after this one
   * @return A new enhancer that runs both in sequence
   */
  default TokenEnhancer andThen(final TokenEnhancer after) {
    return tokens -> after.enhance(this.enhance(tokens));
  }
}
