package com.libdbm.ugf.generator;

import com.libdbm.ugf.features.Structure;

import java.util.Optional;

/**
 * Terminal generator that returns terminal symbols literally.
 *
 * <p>This is the simplest terminal generator - it just returns the terminal text as-is. Useful for
 * grammars where terminals are fixed strings (e.g., XML tags, keywords).
 *
 * <p>Example:
 *
 * <pre>{@code
 * TerminalGenerator literal = new LiteralTerminalGenerator();
 * literal.generate("<tag>", FeatureStructure.empty());  // Returns: "<tag>"
 * literal.generate("if", FeatureStructure.empty());     // Returns: "if"
 * }</pre>
 */
public final class LiteralTerminalGenerator implements TerminalGenerator {

  @Override
  public Optional<String> generate(final String symbol, final Structure features) {
    return Optional.of(symbol);
  }
}
