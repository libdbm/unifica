package com.libdbm.ugf.generator;

import com.libdbm.ugf.features.Structure;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * A {@link TerminalGenerator} that uses a {@link Vocabulary} to produce meaningful text.
 *
 * <p>This generator handles two cases:
 *
 * <ol>
 *   <li><b>Regex patterns</b>: Returns a random word whose text matches the pattern and whose
 *       features unify with the given constraints
 *   <li><b>Literal terminals</b>: Returns the literal text unchanged (e.g., 'if', '.', ',')
 * </ol>
 *
 * <p>Feature constraints are passed in by the caller. If filtering by a specific feature (like
 * grammatical name) is needed, include that constraint in the features parameter.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Vocabulary vocab = Vocabulary.builder()
 *     .add("dog", Structure.builder().with("cat", "noun").with("num", "sing").build())
 *     .add("dogs", Structure.builder().with("cat", "noun").with("num", "plur").build())
 *     .add("barks", Structure.builder().with("cat", "verb").with("num", "sing").build())
 *     .build();
 *
 * TerminalGenerator gen = new VocabularyGenerator(vocab);
 *
 * // Literal: returns as-is
 * gen.generate(".", new Structure())  // -> Optional.of(".")
 *
 * // Regex with feature constraint
 * gen.generate("[a-z]+", Structure.builder().with("num", "sing").build())  // -> "dog" or "barks"
 * }</pre>
 */
public final class VocabularyGenerator implements TerminalGenerator {

  private final Vocabulary vocabulary;
  private final Random random;
  private final boolean fallback;

  /**
   * Create a generator with custom settings.
   *
   * @param vocabulary vocabulary to use for lookups
   * @param random source of randomness
   * @param fallback if true, return symbol as-is when not found in vocabulary
   */
  public VocabularyGenerator(
      final Vocabulary vocabulary, final Random random, final boolean fallback) {
    this.vocabulary = vocabulary;
    this.random = random;
    this.fallback = fallback;
  }

  @Override
  public Optional<String> generate(final String symbol, final Structure features) {
    // Check if symbol is a regex pattern - find matching words
    if (isRegex(symbol)) {
      final var matches = vocabulary.byPattern(symbol, features);
      if (!matches.isEmpty()) {
        return pick(matches);
      }
      // No matches in vocabulary for regex - fallback to symbol if enabled
      return fallback ? Optional.of(symbol) : Optional.empty();
    }

    // Treat as literal (punctuation, keywords, etc.) - return as-is
    return Optional.of(symbol);
  }

  /** Check if symbol looks like a regex pattern. */
  private boolean isRegex(final String symbol) {
    // If it contains special regex chars, it's a pattern
    return symbol.matches(".*[\\[\\]\\+\\*\\?\\|\\(\\)\\{\\}\\\\^$.].*");
  }

  /** Pick a random element from a list. */
  private Optional<String> pick(final List<String> items) {
    if (items.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(items.get(random.nextInt(items.size())));
  }
}
