package com.libdbm.ugf.generator;

import com.libdbm.ugf.constraints.Context;
import com.libdbm.ugf.features.Binding;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Unifier;
import com.libdbm.ugf.features.Value;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.RuleElement;
import com.libdbm.ugf.parser.ConstraintChecker;
import com.libdbm.ugf.parser.Utilities;
import java.util.*;

/**
 * Generates text conforming to a unification grammar.
 *
 * <p>This generator works with any unification grammar (CLEAN, XML, math, etc.) by recursively
 * expanding nonterminals according to grammar rules while maintaining feature unification.
 *
 * <p>Terminal generation is delegated to a pluggable {@link TerminalGenerator} strategy, allowing
 * domain-specific handling of terminals and regex patterns.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Grammar mathGrammar = UnificationGrammarParserFactory.parse(Path.of("math.ug"));
 * TerminalGenerator numberGen = (symbol, features) ->
 *     symbol.matches("[0-9]+") ? Optional.of(String.valueOf(random.nextInt(100))) : Optional.empty();
 *
 * GrammarGenerator generator = new GrammarGenerator(mathGrammar, numberGen);
 * List<String> expressions = generator.generate("expr", FeatureStructure.empty(), 10);
 * // Produces: ["42 + 17", "3 * 9", "100 - 25", ...]
 * }</pre>
 */
public final class GrammarGenerator {

  private final Grammar grammar;
  private final TerminalGenerator terminals;
  private final Random random;
  private final int maxDepth;

  /**
   * Creates a generator with default settings.
   *
   * @param grammar the grammar to generate from
   * @param terminals strategy for generating terminals
   */
  public GrammarGenerator(final Grammar grammar, final TerminalGenerator terminals) {
    this(grammar, terminals, new Random(), 20);
  }

  /**
   * Creates a generator with custom settings.
   *
   * @param grammar the grammar to generate from
   * @param terminals strategy for generating terminals
   * @param random random source for selecting among alternatives
   * @param maxDepth maximum recursion depth (prevents infinite loops)
   */
  public GrammarGenerator(
      final Grammar grammar,
      final TerminalGenerator terminals,
      final Random random,
      final int maxDepth) {
    this.grammar = Objects.requireNonNull(grammar);
    this.terminals = Objects.requireNonNull(terminals);
    this.random = Objects.requireNonNull(random);
    this.maxDepth = maxDepth;
  }

  /**
   * Creates a builder for configuring grammar generators.
   *
   * @param grammar the grammar to generate from
   * @return a new builder
   */
  public static Builder builder(final Grammar grammar) {
    return new Builder(grammar);
  }

  /**
   * Generates multiple sentences starting from the given symbol.
   *
   * @param startSymbol the nonterminal to expand (e.g., "sentence", "expr")
   * @param constraints feature constraints to satisfy
   * @param count maximum number of sentences to generate
   * @return list of generated sentences (may be fewer than count if generation fails)
   */
  public List<String> generate(
      final String startSymbol, final Structure constraints, final int count) {
    final var results = new ArrayList<String>();

    int attempts = 0;
    final int maxAttempts = count * 10; // Allow some failed attempts

    while (results.size() < count && attempts < maxAttempts) {
      attempts++;
      generateOne(startSymbol, constraints, 0).ifPresent(results::add);
    }

    return results;
  }

  /**
   * Generates a single sentence starting from the given symbol.
   *
   * @param startSymbol the nonterminal to expand
   * @param constraints feature constraints to satisfy
   * @return generated sentence, or empty if generation fails
   */
  public Optional<String> generateOne(final String startSymbol, final Structure constraints) {
    return generateOne(startSymbol, constraints, 0);
  }

  /** Internal recursive generation with depth tracking. */
  private Optional<String> generateOne(
      final String symbol, final Structure features, final int depth) {
    if (depth > maxDepth) {
      return Optional.empty();
    }

    // Get all rules for this symbol
    final var rules = grammar.rulesFor(symbol);

    if (rules.isEmpty()) {
      return Optional.empty();
    }

    // Try rules in random order until one succeeds
    final var shuffled = new ArrayList<>(rules);
    Collections.shuffle(shuffled, random);

    for (final var rule : shuffled) {
      final var result = tryRule(rule, features, depth);
      if (result.isPresent()) {
        return result;
      }
    }

    return Optional.empty();
  }

  /** Attempts to generate text using the given rule. */
  private Optional<String> tryRule(
      final GrammarRule rule, final Structure targetFeatures, final int depth) {
    // Try to unify rule LHS features with target features
    final Map<String, Value> bindings = new HashMap<>();
    final var unified = Unifier.unify(rule.lhs().features(), targetFeatures, bindings);

    if (unified.isEmpty() || !(unified.get() instanceof Structure structure)) {
      return Optional.empty();
    }

    // Check constraints (if any) - use raw check since we don't have lexer state
    final var constraints = rule.constraints();
    if (constraints != null && !constraints.isEmpty()) {
      final var context = Utilities.context();
      // Bind LHS symbol for constraint access
      context.withBinding(rule.lhs().symbol(), Binding.of("", structure));
      // Bind any variables from unification
      for (final var entry : bindings.entrySet()) {
        context.withBinding(entry.getKey(), entry.getValue());
      }
      final var result = ConstraintChecker.raw(context, constraints);
      if (!result.passed()) {
        return Optional.empty();
      }
    }

    // Expand the RHS elements with unified features, passing LHS symbol as lhs
    final var lhs = rule.lhs().symbol();
    return expandRHS(rule.rhs(), bindings, depth, lhs);
  }

  /** Expands a sequence of RHS elements into text. */
  private Optional<String> expandRHS(
      final List<RuleElement> elements,
      final Map<String, Value> bindings,
      final int depth,
      final String context) {
    final var sb = new StringBuilder();

    for (final var element : elements) {
      final var expansion = expandElement(element, bindings, depth, context);
      if (expansion.isEmpty()) {
        return Optional.empty();
      }
      final var text = expansion.get();
      if (!text.isEmpty()) {
        appendWithSpacing(sb, text);
      }
    }

    return Optional.of(sb.toString());
  }

  /** Appends text with smart spacing - no space around punctuation/brackets. */
  private void appendWithSpacing(final StringBuilder sb, final String text) {
    if (sb.isEmpty()) {
      sb.append(text);
      return;
    }

    final var last = sb.charAt(sb.length() - 1);
    final var first = text.charAt(0);

    // No space after opening brackets/punctuation or before closing brackets/punctuation
    final var noSpaceAfter = "<([{".indexOf(last) >= 0;
    final var noSpaceBefore = ">)]}/>!=".indexOf(first) >= 0 || text.startsWith("/>");

    if (!noSpaceAfter && !noSpaceBefore && needsSpace(last, first)) {
      sb.append(' ');
    }
    sb.append(text);
  }

  /** Determines if space is needed between two characters. */
  private boolean needsSpace(final char last, final char first) {
    // No space if either is punctuation
    return !isPunctuation(last) && !isPunctuation(first);
    // Space between alphanumeric tokens
  }

  /** Checks if character is punctuation that shouldn't have surrounding spaces. */
  private boolean isPunctuation(final char c) {
    return "<>()[]{}=/'\"!?".indexOf(c) >= 0;
  }

  /** Expands a single RHS element. */
  private Optional<String> expandElement(
      final RuleElement element,
      final Map<String, Value> bindings,
      final int depth,
      final String context) {
    return switch (element) {
      case RuleElement.Terminal terminal -> expandTerminal(terminal, bindings, context);
      case RuleElement.Nonterminal nonterminal -> expandNonterminal(nonterminal, bindings, depth);
      case RuleElement.Regex regex -> expandRegex(regex, bindings, context);
      case RuleElement.Repetition repetition ->
          expandRepetition(repetition, bindings, depth, context);
      case RuleElement.Alternation alternation ->
          expandAlternation(alternation, bindings, depth, context);
      case RuleElement.StateAnnotation stateAnnotation ->
          Optional.of(""); // State annotations contribute no text but don't fail expansion
      case RuleElement.TokenMatch tokenMatch ->
          Optional.empty(); // TokenMatch cannot be expanded in generation
    };
  }

  /** Expands a terminal by delegating to the terminal generator. */
  private Optional<String> expandTerminal(
      final RuleElement.Terminal terminal,
      final Map<String, Value> bindings,
      final String context) {
    // Terminals don't have features in current implementation
    return terminals.generate(terminal.text(), new Structure(), context);
  }

  /** Expands a nonterminal recursively, applying variable bindings to features. */
  private Optional<String> expandNonterminal(
      final RuleElement.Nonterminal nonterminal,
      final Map<String, Value> bindings,
      final int depth) {
    // Apply variable bindings to nonterminal features
    final var features = apply(nonterminal.features(), bindings);
    return generateOne(nonterminal.name(), features, depth + 1);
  }

  /** Apply variable bindings to a feature structure, resolving any variables. */
  private Structure apply(final Structure features, final Map<String, Value> bindings) {
    if (bindings.isEmpty() || features.isEmpty()) {
      return features;
    }

    final var result = features.copy();
    for (final var key : features.keys()) {
      final var value = features.get(key);
      if (value instanceof com.libdbm.ugf.features.Variable v) {
        final var bound = bindings.get(v.name());
        if (bound != null) {
          result.set(key, bound);
        }
      }
    }
    return result;
  }

  /** Expands a regex pattern by delegating to the terminal generator with context. */
  private Optional<String> expandRegex(
      final RuleElement.Regex regex, final Map<String, Value> bindings, final String context) {
    // Pass context (parent nonterminal) to enable category-aware generation
    return terminals.generate(regex.pattern(), new Structure(), context);
  }

  /** Expands a repetition element (?, *, +). */
  private Optional<String> expandRepetition(
      final RuleElement.Repetition repetition,
      final Map<String, Value> bindings,
      final int depth,
      final String context) {
    return switch (repetition.quantifier()) {
      case OPTIONAL -> {
        // 50% chance to include
        if (random.nextBoolean()) {
          yield expandElement(repetition.element(), bindings, depth, context);
        } else {
          yield Optional.of("");
        }
      }
      case ZERO_OR_MORE -> {
        // Generate 0-3 repetitions
        final int count = random.nextInt(4);
        yield expandRepeated(repetition.element(), bindings, depth, count, context);
      }
      case ONE_OR_MORE -> {
        // Generate 1-4 repetitions
        final int count = 1 + random.nextInt(4);
        yield expandRepeated(repetition.element(), bindings, depth, count, context);
      }
    };
  }

  /** Expands an element multiple times. */
  private Optional<String> expandRepeated(
      final RuleElement element,
      final Map<String, Value> bindings,
      final int depth,
      final int count,
      final String context) {
    if (count == 0) {
      return Optional.of("");
    }

    final var sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      final var expansion = expandElement(element, bindings, depth, context);
      if (expansion.isEmpty()) {
        return Optional.empty();
      }
      final var text = expansion.get();
      if (!text.isEmpty()) {
        appendWithSpacing(sb, text);
      }
    }

    return Optional.of(sb.toString());
  }

  /** Expands an alternation by randomly choosing one alternative. */
  private Optional<String> expandAlternation(
      final RuleElement.Alternation alternation,
      final Map<String, Value> bindings,
      final int depth,
      final String context) {
    // Try alternatives in random order
    final var alternatives = new ArrayList<>(alternation.options());
    Collections.shuffle(alternatives, random);

    for (final var alternative : alternatives) {
      final var result = expandElement(alternative, bindings, depth, context);
      if (result.isPresent()) {
        return result;
      }
    }

    return Optional.empty();
  }

  /**
   * Builder for configuring grammar generators with vocabulary and terminal handling.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * Vocabulary vocab = Vocabulary.builder()
   *     .add("dog", Structure.builder().with("type", "noun").with("num", "sing").build())
   *     .add("dogs", Structure.builder().with("type", "noun").with("num", "plur").build())
   *     .build();
   *
   * GrammarGenerator generator = GrammarGenerator.builder(grammar)
   *     .vocabulary(vocab)
   *     .random(new Random(42))
   *     .maxDepth(15)
   *     .build();
   * }</pre>
   */
  public static final class Builder {
    private final Grammar grammar;
    private final List<TerminalGenerator> generators = new ArrayList<>();
    private Random random = new Random();
    private int maxDepth = 20;

    private Builder(final Grammar grammar) {
      this.grammar = Objects.requireNonNull(grammar);
    }

    /**
     * Adds a vocabulary for terminal generation.
     *
     * <p>Words from the vocabulary will be selected when regex patterns match and features unify.
     *
     * @param vocabulary the vocabulary to use
     * @return this builder
     */
    public Builder vocabulary(final Vocabulary vocabulary) {
      generators.add(new VocabularyGenerator(vocabulary, random, false));
      return this;
    }

    /**
     * Adds a custom terminal generator.
     *
     * @param generator the generator to add
     * @return this builder
     */
    public Builder terminal(final TerminalGenerator generator) {
      generators.add(generator);
      return this;
    }

    /**
     * Sets the random source.
     *
     * @param random the random source
     * @return this builder
     */
    public Builder random(final Random random) {
      this.random = Objects.requireNonNull(random);
      return this;
    }

    /**
     * Sets the maximum recursion depth.
     *
     * @param maxDepth maximum depth (prevents infinite loops)
     * @return this builder
     */
    public Builder maxDepth(final int maxDepth) {
      this.maxDepth = maxDepth;
      return this;
    }

    /**
     * Builds the grammar generator.
     *
     * <p>If no terminal generators were added, uses a literal generator as fallback.
     *
     * @return configured grammar generator
     */
    public GrammarGenerator build() {
      // Always add literal fallback at the end
      generators.add(new LiteralTerminalGenerator());

      final TerminalGenerator combined =
          generators.size() == 1
              ? generators.getFirst()
              : new CompositeTerminalGenerator(generators);

      return new GrammarGenerator(grammar, combined, random, maxDepth);
    }
  }
}
