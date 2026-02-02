package com.libdbm.ugf.generator;

import com.libdbm.ugf.features.Structure;

import java.util.Optional;

/**
 * Strategy interface for generating terminal symbols and regex patterns. Implementations can
 * provide domain-specific terminal generation logic.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Lexicon-based: lookup words matching features ({@code num: pl, pos: noun})
 *   <li>Regex sampling: generate text matching a pattern ({@code [0-9]+})
 *   <li>Fixed vocabulary: return predefined strings
 * </ul>
 */
@FunctionalInterface
public interface TerminalGenerator {

  /**
   * Generate a terminal string matching the given symbol and features.
   *
   * @param symbol the terminal symbol (for Terminal elements) or regex pattern (for Regex elements)
   * @param features feature constraints that the generated terminal should satisfy
   * @return generated text, or empty if generation fails
   */
  Optional<String> generate(String symbol, Structure features);

  /**
   * Generate a terminal string with context about the parent grammar symbol.
   *
   * <p>This method allows generators to use the parent nonterminal (e.g., "noun", "verb") when
   * generating text for regex patterns, enabling more meaningful output.
   *
   * @param symbol the terminal symbol or regex pattern
   * @param features feature constraints
   * @param context the parent nonterminal symbol (e.g., "noun" for rule {@code noun --> [a-z]+})
   * @return generated text, or empty if generation fails
   */
  default Optional<String> generate(
      final String symbol, final Structure features, final String context) {
    // Default: ignore context and delegate to basic method
    return generate(symbol, features);
  }
}
