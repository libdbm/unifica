package com.libdbm.ugf.grammar;

import com.libdbm.ugf.features.Structure;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Element on the right-hand side of a grammar rule. Can be: nonterminal, terminal, regex, or
 * special operator.
 */
public sealed interface RuleElement {

  enum Quantifier {
    ZERO_OR_MORE, // *
    ONE_OR_MORE, // +
    OPTIONAL // ? (optional)
  }

  /** Non-terminal reference (e.g., "np", "clause") */
  record Nonterminal(String name, String label, Structure features) implements RuleElement {
    public Nonterminal {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(features, "features must not be null");
    }

    public Nonterminal(final String name) {
      this(name, null, Structure.EMPTY);
    }

    public Nonterminal(final String name, final String label) {
      this(name, label, Structure.EMPTY);
    }
  }

  /** Terminal string (e.g., 'is', 'the') */
  record Terminal(String text, String label) implements RuleElement {
    public Terminal {
      Objects.requireNonNull(text, "text must not be null");
    }

    public Terminal(final String text) {
      this(text, null);
    }

    public Terminal with(final String label) {
      return new Terminal(text, label);
    }
  }

  /**
   * Regular expression pattern (e.g., [A-Z][a-z]*)
   *
   * <p>Pattern is compiled once during construction for performance.
   */
  record Regex(String pattern, String label, Pattern compiled) implements RuleElement {
    public Regex {
      Objects.requireNonNull(pattern, "pattern must not be null");
      Objects.requireNonNull(compiled, "compiled must not be null");
    }

    public Regex(final String pattern) {
      this(pattern, null, Pattern.compile(pattern));
    }

    public Regex(final String pattern, final String label) {
      this(pattern, label, Pattern.compile(pattern));
    }

    public Regex with(final String label) {
      return new Regex(pattern, label, compiled);
    }

    /** Equal when the pattern and label are equal; the compiled form follows from the pattern. */
    @Override
    public boolean equals(final Object other) {
      return other instanceof Regex(String text, String name, Pattern ignored)
          && pattern.equals(text)
          && Objects.equals(label, name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(pattern, label);
    }
  }

  /**
   * Repetition operators (*, +, ?) Note: ? (ZERO_OR_ONE) is semantically optional, no separate
   * Optional type needed
   */
  record Repetition(RuleElement element, Quantifier quantifier) implements RuleElement {}

  /** Alternation (choice between options) */
  /** A parenthesised sequence of elements, as a group or an alternation option (S-G4). */
  record Sequence(List<RuleElement> elements) implements RuleElement {
    public Sequence {
      elements = List.copyOf(elements);
    }
  }

  record Alternation(List<RuleElement> options) implements RuleElement {
    public Alternation {
      options = List.copyOf(options);
    }
  }

  /** State annotation for lexer state transitions (e.g., {CONTENT}) */
  record StateAnnotation(String state) implements RuleElement {
    public StateAnnotation {
      Objects.requireNonNull(state, "state must not be null");
    }
  }

  /**
   * Token match - matches any token and binds its features.
   *
   * <p>Used with constraints to filter by token properties:
   *
   * <pre>
   * noun --> {TOKEN}:W where equals(W.cat, "NN");
   * </pre>
   *
   * <p>The label binds the token's features (including text, name) for constraint evaluation.
   */
  record TokenMatch(String label) implements RuleElement {
    public TokenMatch() {
      this(null);
    }

    public TokenMatch with(final String label) {
      return new TokenMatch(label);
    }
  }
}
