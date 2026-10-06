package com.libdbm.ugf.grammar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An immutable unification grammar with rules, imports, and metadata.
 *
 * <p>Use {@link Builder} to create instances:
 *
 * <pre>{@code
 * Grammar grammar = Grammar.builder()
 *     .start("document")
 *     .add(rule1)
 *     .add(rule2)
 *     .build();
 * }</pre>
 *
 * @param rules map of symbol name to list of rules
 * @param imports list of import declarations
 * @param module module info
 * @param start start symbol
 * @param skips lexical categories discarded between tokens ({@code skip A, B;}, S-L4)
 * @param whitespace the pattern skipped between tokens ({@code whitespace ...;}, S-L4): {@code
 *     null} for the default {@code \s+}, empty for none
 */
public record Grammar(
    Map<String, List<GrammarRule>> rules,
    List<ImportDeclaration> imports,
    ModuleInfo module,
    String start,
    Set<String> skips,
    String whitespace) {

  /**
   * Compact canonical constructor for defensive copying. Copies each production list and keeps
   * symbols in declaration order.
   */
  public Grammar {
    final var copied = new LinkedHashMap<String, List<GrammarRule>>();
    rules.forEach((symbol, productions) -> copied.put(symbol, List.copyOf(productions)));
    rules = Collections.unmodifiableMap(copied);
    imports = List.copyOf(imports);
    module = module == null ? ModuleInfo.anonymous() : module;
    skips = Set.copyOf(skips);
  }

  /** A grammar that skips nothing but the default whitespace. */
  public Grammar(
      final Map<String, List<GrammarRule>> rules,
      final List<ImportDeclaration> imports,
      final ModuleInfo module,
      final String start) {
    this(rules, imports, module, start, Set.of(), null);
  }

  /** Create a new builder. */
  public static Builder builder() {
    return new Builder();
  }

  /** Create a builder pre-populated from this grammar. */
  public Builder toBuilder() {
    return Builder.from(this);
  }

  /** Get rules for a specific left-hand-side symbol. */
  public List<GrammarRule> rulesFor(final String lhs) {
    return rules.getOrDefault(lhs, List.of());
  }

  /** Check if rules exist for the given symbol. */
  public boolean hasRulesFor(final String symbol) {
    return rules.containsKey(symbol);
  }

  /** Get all nonterminal symbols in the grammar. */
  public List<String> nonterminals() {
    return List.copyOf(rules.keySet());
  }

  /**
   * Builder for creating Grammar instances.
   *
   * <p>Rules are stored in insertion order, ensuring deterministic behavior when inferring the
   * start symbol from the first rule.
   */
  public static final class Builder {

    private final Map<String, List<GrammarRule>> rules = new LinkedHashMap<>();
    private final List<ImportDeclaration> imports = new ArrayList<>();
    private ModuleInfo module = ModuleInfo.anonymous();
    private String start = null;
    private final Set<String> skips = new LinkedHashSet<>();
    private String whitespace = null;

    /**
     * Create a builder pre-populated from an existing Grammar. Useful for creating modified copies.
     */
    public static Builder from(final Grammar g) {
      final var builder = new Builder();
      for (final var entry : g.rules().entrySet()) {
        for (final var rule : entry.getValue()) {
          builder.add(rule);
        }
      }
      for (final var imp : g.imports()) {
        builder.addImport(imp);
      }
      builder.module(g.module());
      if (g.start() != null) {
        builder.start(g.start());
      }
      g.skips().forEach(builder::skip);
      builder.whitespace(g.whitespace());
      return builder;
    }

    /** Add a grammar rule. */
    public Builder add(final GrammarRule rule) {
      rules.computeIfAbsent(rule.lhs().symbol(), k -> new ArrayList<>()).add(rule);
      return this;
    }

    /** Add an import declaration. */
    public Builder addImport(final ImportDeclaration decl) {
      imports.add(decl);
      return this;
    }

    /** Set the module info. */
    public Builder module(final ModuleInfo info) {
      this.module = info;
      return this;
    }

    /** Declare a lexical category that is matched and discarded between tokens. */
    public Builder skip(final String category) {
      skips.add(category);
      return this;
    }

    /** Set the pattern skipped between tokens: {@code null} for the default, empty for none. */
    public Builder whitespace(final String pattern) {
      this.whitespace = pattern;
      return this;
    }

    /** Set the start symbol explicitly. */
    public Builder start(final String symbol) {
      this.start = symbol;
      return this;
    }

    /** Build the Grammar instance. */
    public Grammar build() {
      final var effective =
          start != null
              ? start
              : (!rules.isEmpty() ? rules.keySet().iterator().next() : "document");
      return new Grammar(rules, imports, module, effective, skips, whitespace);
    }

    /**
     * Validate the grammar using GrammarLinter before building.
     *
     * @return the linting report containing any issues found
     */
    public GrammarLinter.LintReport validate() {
      final var temp = build();
      return new GrammarLinter().lint(temp);
    }

    /**
     * Build the grammar, throwing if validation fails with errors.
     *
     * @return the Grammar instance
     * @throws IllegalStateException if validation finds errors
     */
    public Grammar validated() {
      final var grammar = build();
      final var report = new GrammarLinter().lint(grammar);
      if (report.hasErrors()) {
        throw new IllegalStateException("Grammar validation failed:\n" + report.format());
      }
      return grammar;
    }
  }
}
