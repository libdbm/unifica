package com.libdbm.ugf.generator;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.compiler.Compiled;
import com.libdbm.ugf.compiler.Compiler;
import com.libdbm.ugf.compiler.Element;
import com.libdbm.ugf.compiler.Production;
import com.libdbm.ugf.constraints.Environment;
import com.libdbm.ugf.constraints.Evaluator;
import com.libdbm.ugf.constraints.Expression;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.features.Binding;
import com.libdbm.ugf.features.Bindings;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Unification;
import com.libdbm.ugf.features.Unifier;
import com.libdbm.ugf.features.Value;
import com.libdbm.ugf.features.Values;
import com.libdbm.ugf.features.Variable;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.RuleElement;
import com.libdbm.ugf.parser.Limits;
import com.libdbm.ugf.parser.Options;
import com.libdbm.ugf.parser.Outcome;
import com.libdbm.ugf.parser.ParseObserver;
import com.libdbm.ugf.parser.Parser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Generates sentences from a grammar (S-N1, S-N2).
 *
 * <p>Generation is a randomized depth-first search over the compiled grammar. Each use of a
 * production has its own variables (S-F5), bound in one set of bindings for the whole sentence, so
 * agreement between siblings holds by construction. A production's required constraints are checked
 * once its constituents exist, and every sentence is parsed before it is returned: it is returned
 * only if it parses as the requested symbol with root features that unify with the requested
 * features.
 *
 * <pre>{@code
 * final var generator = GrammarGenerator.builder(grammar).random(new Random(1)).build().orElseThrow();
 * final var sentences = generator.generate("S", Structure.EMPTY, 10).orElseThrow();
 * }</pre>
 *
 * <p>A generator is not thread-safe: it draws from one {@link Random}.
 */
public final class GrammarGenerator {

  /** No sentence could be generated within the policy's attempts. */
  public static final String FAILED = "generation.failed";

  private static final int UNREACHABLE = Integer.MAX_VALUE / 2;

  private final Compiled compiled;
  private final Parser parser;
  private final TerminalGenerator terminals;
  private final Random random;
  private final Policy policy;
  private final Map<String, List<GrammarRule>> lexical = new HashMap<>();
  private final Map<String, Integer> heights = new HashMap<>();

  /** Lexical and positional predicates, which generation leaves to the final parse. */
  private final Set<String> deferred;

  private GrammarGenerator(
      final Grammar grammar,
      final Compiled compiled,
      final TerminalGenerator terminals,
      final Random random,
      final Policy policy,
      final Limits limits) {
    this.compiled = compiled;
    this.parser = Parser.of(compiled, new Options(limits, ParseObserver.NOOP, false));
    final var names = new HashSet<>(compiled.predicates().names(Predicates.Phase.LEXICAL));
    names.addAll(compiled.predicates().names(Predicates.Phase.POSITIONAL));
    this.deferred = Set.copyOf(names);
    this.terminals = terminals;
    this.random = random;
    this.policy = policy;
    for (final var rules : grammar.rules().values()) {
      for (final var rule : rules) {
        if (rule.kind() == GrammarRule.Kind.LEXICAL) {
          lexical.computeIfAbsent(rule.lhs().symbol(), symbol -> new ArrayList<>()).add(rule);
        }
      }
    }
    heights();
  }

  public static Builder builder(final Grammar grammar) {
    return new Builder(grammar);
  }

  /**
   * Generates up to {@code count} sentences of {@code start} whose features unify with {@code
   * features}.
   */
  public Result<List<String>, ErrorDetails> generate(
      final String start, final Structure features, final int count) {
    final var batch = batch(start, features, count);
    if (batch.sentences().isEmpty() && !batch.failures().isEmpty()) {
      return Result.failure(ErrorDetails.of(FAILED, batch.failures().keySet().iterator().next()));
    }
    return Result.success(batch.sentences());
  }

  /**
   * Tries to generate {@code count} sentences, reporting how many were produced and why the others
   * were not.
   */
  public Generation batch(final String start, final Structure features, final int count) {
    final var sentences = new ArrayList<String>();
    final var failures = new LinkedHashMap<String, Integer>();
    for (var i = 0; i < count; i++) {
      switch (generateOne(start, features)) {
        case Result.Success<String, ErrorDetails>(var sentence) -> sentences.add(sentence);
        case Result.Failure<String, ErrorDetails>(var failure) ->
            failures.merge(failure.message(), 1, Integer::sum);
      }
    }
    return new Generation(sentences, count, failures);
  }

  /** Generates one sentence of {@code start} whose features unify with {@code features}. */
  public Result<String, ErrorDetails> generateOne(final String start, final Structure features) {
    var reason = "no derivation of " + start + " within depth " + policy.depth();
    for (var attempt = 0; attempt < policy.attempts(); attempt++) {
      final var search = new Search();
      final var expansion =
          search.symbol(new Element.Symbol(start, null, features), Bindings.EMPTY, policy.depth());
      if (expansion.isEmpty()) {
        if (search.exceeded) {
          reason = "generated sentences exceed " + policy.length() + " characters";
        }
        continue;
      }
      final var text = policy.joiner().join(expansion.get().tokens());
      if (text.length() > policy.length()) {
        reason = "generated sentences exceed " + policy.length() + " characters";
        continue;
      }
      // S-N1: return only what parses as the requested symbol with the requested features.
      final var parsed = parser.parse(text, start, features);
      if (parsed.outcome() == Outcome.ACCEPTED) {
        return Result.success(text);
      }
      reason =
          parsed.stop() != null
              ? "validating \"" + text + "\" stopped: " + parsed.stop().message()
              : "generated \"" + text + "\" does not parse as " + start;
    }
    return Result.failure(ErrorDetails.of(FAILED, reason));
  }

  /** A way to expand a symbol: one of its productions, or one of its lexical rules. */
  private sealed interface Candidate {
    record Derivation(Production production) implements Candidate {}

    record Lexeme(GrammarRule rule) implements Candidate {}
  }

  /** A partial result: the tokens produced and the bindings after producing them. */
  private record Expansion(List<String> tokens, Structure features, Bindings bindings) {}

  /**
   * One attempt: a fresh variable counter, a step budget and the repetition counts on the current
   * path.
   */
  private final class Search {
    private int fresh;
    private int steps;

    /**
     * Characters in the tokens on the current path, a lower bound on the joined sentence; restored
     * when the search backtracks.
     */
    private long characters;

    /** Whether some expansion was abandoned for exceeding {@link Policy#length()}. */
    private boolean exceeded;

    private final Map<String, Integer> active = new HashMap<>();

    /**
     * Expands a symbol whose written features (already renamed for its scope) are {@code
     * element.features()}.
     */
    private Optional<Expansion> symbol(
        final Element.Symbol element, final Bindings bindings, final int depth) {
      if (++steps > policy.steps() || depth <= 0) {
        return Optional.empty();
      }
      final var candidates = new ArrayList<Candidate>();
      for (final var production : compiled.productions(element.name())) {
        if (height(production) <= depth && allowed(production)) {
          candidates.add(new Candidate.Derivation(production));
        }
      }
      for (final var rule : lexical.getOrDefault(element.name(), List.of())) {
        candidates.add(new Candidate.Lexeme(rule));
      }
      Collections.shuffle(candidates, random);
      for (final var candidate : candidates) {
        final var expansion =
            switch (candidate) {
              case Candidate.Derivation(var production) ->
                  production(production, element, bindings, depth);
              case Candidate.Lexeme(var rule) -> lexeme(rule, element, bindings);
            };
        if (expansion.isPresent()) {
          return expansion;
        }
      }
      return Optional.empty();
    }

    /** Repetitions stop recursing once they have repeated {@link Policy#repetitions()} times. */
    private boolean allowed(final Production production) {
      if (!production.auxiliary()
          || active.getOrDefault(production.symbol(), 0) < policy.repetitions()) {
        return true;
      }
      return production.rhs().stream()
          .noneMatch(
              element ->
                  element instanceof Element.Symbol symbol
                      && symbol.name().equals(production.symbol()));
    }

    private Optional<Expansion> production(
        final Production production,
        final Element.Symbol element,
        final Bindings bindings,
        final int depth) {
      final var suffix = "#" + fresh++;
      final var lhs = rename(production.features(), suffix);
      if (!(Unifier.unify(element.features(), lhs, bindings)
          instanceof Result.Success<Unification<Structure>, ErrorDetails>(var linked))) {
        return Optional.empty();
      }
      active.merge(production.symbol(), 1, Integer::sum);
      final var mark = characters;
      var success = false;
      try {
        var current = linked.bindings();
        final var tokens = new ArrayList<String>();
        final var labels = new HashMap<String, Value>();
        for (final var child : production.rhs()) {
          final List<String> produced;
          final Structure features;
          switch (child) {
            case Element.Symbol symbol -> {
              final var renamed =
                  new Element.Symbol(
                      symbol.name(), symbol.label(), rename(symbol.features(), suffix));
              final var expansion = symbol(renamed, current, depth - 1);
              if (expansion.isEmpty()) {
                return Optional.empty();
              }
              current = expansion.get().bindings();
              produced = expansion.get().tokens();
              features = expansion.get().features();
            }
            case Element.Terminal terminal -> {
              final var token = terminal(terminal.category(), production.symbol());
              if (token.isEmpty()) {
                return Optional.empty();
              }
              produced = List.of(token.get());
              features = Structure.EMPTY;
              if (!count(token.get())) {
                return Optional.empty();
              }
            }
            case Element.Token token -> {
              // A caller-supplied token cannot be generated.
              return Optional.empty();
            }
          }
          tokens.addAll(produced);
          if (child.label() != null) {
            labels.put(child.label(), Binding.of(policy.joiner().join(produced), features));
          }
        }
        final var result = substitute(lhs, current);
        if (!holds(
            production,
            variables(current, suffix),
            labels,
            Binding.of(policy.joiner().join(tokens), result))) {
          return Optional.empty();
        }
        success = true;
        return Optional.of(new Expansion(tokens, result, current));
      } finally {
        active.merge(production.symbol(), -1, Integer::sum);
        if (!success) {
          characters = mark;
        }
      }
    }

    /** Adds a token to the current path, or reports that the sentence is already too long. */
    private boolean count(final String token) {
      characters += token.length();
      if (characters > policy.length()) {
        exceeded = true;
        return false;
      }
      return true;
    }

    /** A lexical production: one token, generated from its parts without spacing (S-G2). */
    private Optional<Expansion> lexeme(
        final GrammarRule rule, final Element.Symbol element, final Bindings bindings) {
      final var lhs = rename(rule.lhs().features(), "#" + fresh++);
      if (!(Unifier.unify(element.features(), lhs, bindings)
          instanceof Result.Success<Unification<Structure>, ErrorDetails>(var linked))) {
        return Optional.empty();
      }
      final var features = substitute(lhs, linked.bindings());
      final var text = new StringBuilder();
      for (final var part : rule.rhs()) {
        if (!part(part, features, rule.lhs().symbol(), text)) {
          return Optional.empty();
        }
      }
      if (!count(text.toString())) {
        return Optional.empty();
      }
      return Optional.of(new Expansion(List.of(text.toString()), features, linked.bindings()));
    }

    private boolean part(
        final RuleElement part,
        final Structure features,
        final String context,
        final StringBuilder text) {
      switch (part) {
        case RuleElement.Terminal terminal -> text.append(terminal.text());
        case RuleElement.Regex regex -> {
          final var token = terminals.generate(regex.pattern(), features, context);
          if (token.isEmpty()) {
            return false;
          }
          text.append(token.get());
        }
        case RuleElement.Sequence sequence -> {
          for (final var inner : sequence.elements()) {
            if (!part(inner, features, context, text)) {
              return false;
            }
          }
        }
        case RuleElement.Alternation alternation -> {
          return part(
              alternation.options().get(random.nextInt(alternation.options().size())),
              features,
              context,
              text);
        }
        case RuleElement.Repetition repetition -> {
          final var count =
              switch (repetition.quantifier()) {
                case OPTIONAL -> random.nextInt(2);
                case ZERO_OR_MORE -> random.nextInt(policy.repetitions() + 1);
                case ONE_OR_MORE -> 1 + random.nextInt(Math.max(1, policy.repetitions()));
              };
          for (var i = 0; i < count; i++) {
            if (!part(repetition.element(), features, context, text)) {
              return false;
            }
          }
        }
        case RuleElement.StateAnnotation annotation -> {}
        case RuleElement.TokenMatch match -> {
          return false;
        }
        case RuleElement.Nonterminal nonterminal -> {
          return false;
        }
      }
      return true;
    }

    /** An anonymous token: a quoted literal's text, or a regex through the terminal generators. */
    private Optional<String> terminal(final String category, final String context) {
      if (category.length() >= 2 && category.startsWith("'") && category.endsWith("'")) {
        return Optional.of(category.substring(1, category.length() - 1));
      }
      return terminals.generate(category, Structure.EMPTY, context);
    }
  }

  /**
   * True if the production's required constraints hold (S-N2). Lexical and positional predicates
   * need a lexical state or a span, so expressions that call them are left to the final parse.
   */
  private boolean holds(
      final Production production,
      final Map<String, Value> variables,
      final Map<String, Value> labels,
      final Binding self) {
    if (production.plan().required().isEmpty()) {
      return true;
    }
    final var environment =
        Environment.of(compiled.predicates())
            .with(variables)
            .with(labels)
            .with(production.symbol(), self);
    for (final var expression : production.plan().required()) {
      if (!calls(expression, deferred) && !Evaluator.truth(expression, environment)) {
        return false;
      }
    }
    return true;
  }

  /** True if {@code expression} calls any predicate in {@code names}. */
  private static boolean calls(final Expression expression, final Set<String> names) {
    return switch (expression) {
      case Expression.Call call -> names.contains(call.name());
      case Expression.And and -> and.terms().stream().anyMatch(term -> calls(term, names));
      case Expression.Or or -> or.terms().stream().anyMatch(term -> calls(term, names));
      case Expression.Not not -> calls(not.term(), names);
      case Expression.Weighted weighted -> calls(weighted.term(), names);
      case Expression.Literal literal -> false;
    };
  }

  /**
   * The minimum derivation height of every symbol, by fixed point; used to stay within the depth.
   */
  private void heights() {
    lexical.keySet().forEach(symbol -> heights.put(symbol, 1));
    var changed = true;
    while (changed) {
      changed = false;
      for (final var production : compiled.productions()) {
        final var height = height(production);
        if (height < heights.getOrDefault(production.symbol(), UNREACHABLE)) {
          heights.put(production.symbol(), height);
          changed = true;
        }
      }
    }
  }

  private int height(final Production production) {
    var tallest = 0;
    for (final var element : production.rhs()) {
      if (element instanceof Element.Symbol symbol) {
        tallest = Math.max(tallest, heights.getOrDefault(symbol.name(), UNREACHABLE));
      }
    }
    return tallest >= UNREACHABLE ? UNREACHABLE : tallest + 1;
  }

  private static Structure substitute(final Structure structure, final Bindings bindings) {
    return Unifier.unify(structure, Structure.EMPTY, bindings)
        .map(Unification::value)
        .orElse(structure);
  }

  /**
   * The variables of the production use renamed with {@code suffix}, under their source names and
   * with their bindings substituted, for constraint arguments.
   */
  private static Map<String, Value> variables(final Bindings bindings, final String suffix) {
    final var values = new HashMap<String, Value>();
    bindings
        .values()
        .forEach(
            (name, value) -> {
              if (name.endsWith(suffix)) {
                values.put(
                    name.substring(0, name.length() - suffix.length()),
                    Unifier.substitute(value, bindings));
              }
            });
    return values;
  }

  /** Appends {@code suffix} to every variable, so each production use has its own (S-F5). */
  private static Structure rename(final Structure structure, final String suffix) {
    return (Structure) Values.rename(structure, variable -> new Variable(variable.name() + suffix));
  }

  /**
   * Builds a generator. The vocabulary generator is created in {@link #build()} from the final
   * {@link Random}, so the order of builder calls does not matter (GEN-8).
   */
  public static final class Builder {
    private final Grammar grammar;
    private final List<TerminalGenerator> generators = new ArrayList<>();
    private final List<Vocabulary> vocabularies = new ArrayList<>();
    private Predicates predicates = Predicates.standard();
    private Random random = new Random();
    private Policy policy = Policy.DEFAULT;
    private Limits limits = Limits.DEFAULT;

    private Builder(final Grammar grammar) {
      this.grammar = Objects.requireNonNull(grammar);
    }

    public Builder vocabulary(final Vocabulary vocabulary) {
      vocabularies.add(Objects.requireNonNull(vocabulary));
      return this;
    }

    public Builder terminal(final TerminalGenerator generator) {
      generators.add(Objects.requireNonNull(generator));
      return this;
    }

    public Builder random(final Random random) {
      this.random = Objects.requireNonNull(random);
      return this;
    }

    public Builder predicates(final Predicates predicates) {
      this.predicates = Objects.requireNonNull(predicates);
      return this;
    }

    public Builder policy(final Policy policy) {
      this.policy = Objects.requireNonNull(policy);
      return this;
    }

    /** The limits of the parse that verifies each generated sentence (S-N1). */
    public Builder limits(final Limits limits) {
      this.limits = Objects.requireNonNull(limits);
      return this;
    }

    /** Shorthand for a policy with a different depth. */
    public Builder maxDepth(final int depth) {
      this.policy = policy.depth(depth);
      return this;
    }

    /** Compiles the grammar and creates the generator. */
    public Result<GrammarGenerator, ErrorDetails> build() {
      final var all = new ArrayList<TerminalGenerator>();
      vocabularies.forEach(
          vocabulary -> all.add(new VocabularyGenerator(vocabulary, random, false)));
      all.addAll(generators);
      all.add(new LiteralTerminalGenerator());
      final TerminalGenerator combined =
          all.size() == 1 ? all.getFirst() : new CompositeTerminalGenerator(all);
      return Compiler.compile(grammar, predicates)
          .map(
              compiled ->
                  new GrammarGenerator(grammar, compiled, combined, random, policy, limits));
    }
  }
}
