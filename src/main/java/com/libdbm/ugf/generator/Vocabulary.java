package com.libdbm.ugf.generator;

import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Unifier;
import com.libdbm.ugf.features.Value;
import java.util.*;
import java.util.regex.Pattern;

/**
 * A vocabulary for generation that maps words to features.
 *
 * <p>Supports two modes of lookup:
 *
 * <ul>
 *   <li>By features: returns words whose features unify with given constraints
 *   <li>By regex: returns words whose text matches a regex pattern and features unify
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Vocabulary vocab = Vocabulary.builder()
 *     .add("dog", Structure.builder().with("type", "noun").with("num", "sing").build())
 *     .add("dogs", Structure.builder().with("type", "noun").with("num", "plur").build())
 *     .add("run", Structure.builder().with("type", "verb").with("num", "plur").build())
 *     .add("runs", Structure.builder().with("type", "verb").with("num", "sing").build())
 *     .build();
 *
 * // Lookup by features
 * vocab.byFeatures(Structure.builder().with("type", "noun").with("num", "sing").build())  // -> ["dog"]
 *
 * // Lookup by regex pattern
 * vocab.byPattern("[a-z]+", new Structure())  // -> ["dog", "dogs", "run", "runs"]
 * }</pre>
 */
public final class Vocabulary {

  private final List<Entry> entries;
  private final Map<String, List<Entry>> cache;

  private Vocabulary(final List<Entry> entries) {
    this.entries = List.copyOf(entries);
    this.cache = new HashMap<>();
  }

  /** Create an empty vocabulary. */
  public static Vocabulary empty() {
    return new Vocabulary(List.of());
  }

  /** Create a builder for constructing vocabularies. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Find words whose features unify with target constraints.
   *
   * @param target feature constraints that must unify
   * @return list of matching words (text only)
   */
  public List<String> byFeatures(final Structure target) {
    return filter(entries, target);
  }

  /**
   * Find words whose text matches a regex pattern and feature constraints.
   *
   * @param regex the pattern to match against word text
   * @param target feature constraints that must unify
   * @return list of matching words (text only)
   */
  public List<String> byPattern(final String regex, final Structure target) {
    // Check cache first
    var candidates = cache.get(regex);
    if (candidates == null) {
      final var pattern = Pattern.compile("^" + regex + "$");
      candidates = entries.stream().filter(e -> pattern.matcher(e.text()).matches()).toList();
      cache.put(regex, candidates);
    }
    return filter(candidates, target);
  }

  /**
   * Get all entries (for iteration or debugging).
   *
   * @return unmodifiable list of all entries
   */
  public List<Entry> all() {
    return entries;
  }

  /** Filter entries by feature unification. */
  private List<String> filter(final List<Entry> candidates, final Structure target) {
    if (target.keys().isEmpty()) {
      // No constraints - return all
      return candidates.stream().map(Entry::text).toList();
    }

    final var result = new ArrayList<String>();
    for (final var entry : candidates) {
      final var bindings = new HashMap<String, Value>();
      final var unified = Unifier.unify(entry.features(), target, bindings);
      if (unified.isPresent()) {
        result.add(entry.text());
      }
    }
    return result;
  }

  /** A word entry with text and features. */
  public record Entry(String text, Structure features) {
    public static Entry of(final String text, final Structure features) {
      return new Entry(text, features);
    }
  }

  /** Builder for constructing Vocabulary instances. */
  public static final class Builder {
    private final List<Entry> entries = new ArrayList<>();

    /**
     * Add a word with features.
     *
     * @param text the word text
     * @param features feature structure
     * @return this builder
     */
    public Builder add(final String text, final Structure features) {
      entries.add(Entry.of(text, features));
      return this;
    }

    /**
     * Add entries from another vocabulary.
     *
     * @param other vocabulary to merge
     * @return this builder
     */
    public Builder merge(final Vocabulary other) {
      entries.addAll(other.entries);
      return this;
    }

    /**
     * Build the vocabulary.
     *
     * @return immutable vocabulary
     */
    public Vocabulary build() {
      return new Vocabulary(entries);
    }
  }
}
