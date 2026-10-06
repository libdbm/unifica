package com.libdbm.ugf.generator;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.features.Bindings;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Unification;
import com.libdbm.ugf.features.Unifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * vocab.byPattern("[a-z]+", Structure.EMPTY)  // -> ["dog", "dogs", "run", "runs"]
 * }</pre>
 */
public final class Vocabulary {

  /** The most patterns whose matching entries are cached (least recently used are dropped). */
  static final int PATTERNS = 256;

  private final List<Entry> entries;
  private final Map<String, List<Entry>> cache;

  private Vocabulary(final List<Entry> entries) {
    this.entries = List.copyOf(entries);
    // Thread-safe: the entries never change, and the bounded cache only memoises pattern
    // filtering (GEN-9).
    this.cache =
        Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
              // Inside a map subclass, a bare Entry names the inherited Map.Entry.
              @Override
              protected boolean removeEldestEntry(
                  final Map.Entry<String, List<Vocabulary.Entry>> eldest) {
                return size() > PATTERNS;
              }
            });
  }

  /** The number of cached patterns, for tests. */
  int cached() {
    return cache.size();
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
    final var candidates =
        cache.computeIfAbsent(
            regex,
            key -> {
              final var pattern = Pattern.compile(key);
              return entries.stream().filter(e -> pattern.matcher(e.text()).matches()).toList();
            });
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
      final var unified = Unifier.unify(entry.features(), target, Bindings.EMPTY);
      if (unified instanceof Result.Success<Unification<Structure>, ErrorDetails>) {
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
