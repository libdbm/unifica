package com.libdbm.ugf.generator;

import com.libdbm.ugf.features.Structure;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Terminal generator that tries multiple generators in sequence.
 *
 * <p>This generator delegates to a chain of terminal generators, trying each one until one
 * succeeds. Useful for combining multiple generation strategies.
 *
 * <p>Example:
 *
 * <pre>{@code
 * TerminalGenerator combined = new CompositeTerminalGenerator(
 *     lexiconGenerator,      // Try lexicon first
 *     regexGenerator,        // Then try regex patterns
 *     literalGenerator       // Finally fall back to literal
 * );
 * }</pre>
 */
public final class CompositeTerminalGenerator implements TerminalGenerator {

  private final List<TerminalGenerator> generators;

  /**
   * Creates a composite generator from multiple generators.
   *
   * @param generators the generators to try, in order
   */
  public CompositeTerminalGenerator(final TerminalGenerator... generators) {
    this(Arrays.asList(generators));
  }

  /**
   * Creates a composite generator from a list of generators.
   *
   * @param generators the generators to try, in order
   */
  public CompositeTerminalGenerator(final List<TerminalGenerator> generators) {
    this.generators = List.copyOf(generators);
  }

  @Override
  public Optional<String> generate(final String symbol, final Structure features) {
    for (final var generator : generators) {
      final var result = generator.generate(symbol, features);
      if (result.isPresent()) {
        return result;
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<String> generate(
      final String symbol, final Structure features, final String context) {
    for (final var generator : generators) {
      final var result = generator.generate(symbol, features, context);
      if (result.isPresent()) {
        return result;
      }
    }
    return Optional.empty();
  }
}
