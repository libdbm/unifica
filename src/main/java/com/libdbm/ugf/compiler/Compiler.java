package com.libdbm.ugf.compiler;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.constraints.Expression;
import com.libdbm.ugf.constraints.Plan;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Value;
import com.libdbm.ugf.features.Variable;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.RuleElement;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compiles a source {@link Grammar} against a {@link Predicates} registry into an immutable {@link
 * Compiled} grammar (CMP-1). Every consumer (lexer, parser, generator) starts from the result, so
 * preparation happens once and the same way everywhere.
 *
 * <ol>
 *   <li>Each lexical production becomes one atomic {@link Lexeme} (S-G2).
 *   <li>Each syntactic production becomes a {@link Production}; inline literals and regexes become
 *       shared anonymous lexemes (S-G3), and repetition and alternation are lowered into auxiliary
 *       productions (S-G4).
 *   <li>Constraints become {@link Plan}s, and the whole grammar is validated: undefined symbols,
 *       unknown predicates and arity (S-C8), weight placement (S-C6), and invalid constant regexes.
 *       Every error is reported.
 *   <li>Every {@code skip} declaration must name a lexical production.
 *   <li>Ids are assigned in compilation order; nullable symbols and the production index are
 *       computed.
 * </ol>
 */
public final class Compiler {

  /** The grammar failed validation; {@link ErrorDetails#issues()} lists every problem. */
  public static final String INVALID = "grammar.invalid";

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private final Grammar grammar;
  private final Predicates predicates;
  private final List<String> errors = new ArrayList<>();
  private final List<Lexeme> lexemes = new ArrayList<>();
  private final Map<String, Lexeme> anonymous = new HashMap<>();
  private final List<Production> productions = new ArrayList<>();
  private final Map<String, String> auxiliaries = new HashMap<>();
  private int counter;

  private Compiler(final Grammar grammar, final Predicates predicates) {
    this.grammar = grammar;
    this.predicates = predicates;
  }

  /** Compiles {@code grammar}; a failure lists every validation error. */
  public static Result<Compiled, ErrorDetails> compile(
      final Grammar grammar, final Predicates predicates) {
    return new Compiler(grammar, predicates).run();
  }

  /** Appends {@code regex}, inside a named group when it is labelled. */
  private static void group(
      final String label,
      final String regex,
      final StringBuilder pattern,
      final List<String> labels) {
    if (label == null) {
      pattern.append(regex);
      return;
    }
    pattern.append("(?<").append(Lexeme.group(labels.size())).append('>').append(regex).append(')');
    labels.add(label);
  }

  /**
   * The features that link an auxiliary production to the production it was lowered from: one
   * feature per variable the lowered elements use, holding that variable. Written both on the
   * auxiliary's left-hand side and on its reference, it makes bindings flow in and out exactly as
   * in the expansion (S-G4, S-F5).
   */
  private static Structure link(final RuleElement element) {
    final var names = new TreeSet<String>();
    variables(element, names);
    final var builder = Structure.builder();
    for (final var name : names) {
      builder.with("$" + name, new Variable(name));
    }
    return builder.build();
  }

  private static void variables(final RuleElement element, final Set<String> names) {
    switch (element) {
      case RuleElement.Nonterminal nonterminal -> variables(nonterminal.features(), names);
      case RuleElement.Repetition repetition -> variables(repetition.element(), names);
      case RuleElement.Alternation alternation ->
          alternation.options().forEach(option -> variables(option, names));
      case RuleElement.Sequence sequence ->
          sequence.elements().forEach(inner -> variables(inner, names));
      default -> {}
    }
  }

  // ---------------------------------------------------------------- lexemes

  private static void variables(final Value value, final Set<String> names) {
    switch (value) {
      case Variable variable -> names.add(variable.name());
      case Structure structure ->
          structure.keys().forEach(key -> variables(structure.get(key), names));
      default -> {}
    }
  }

  private static String base(final RuleElement element) {
    return switch (element) {
      case RuleElement.Nonterminal nonterminal -> nonterminal.name();
      case RuleElement.Terminal terminal -> "term";
      case RuleElement.Regex regex -> "regex";
      case RuleElement.Sequence sequence -> "seq";
      case RuleElement.Alternation alternation -> "alt";
      default -> "elem";
    };
  }

  private static void collect(final Expression expression, final List<Expression.Call> calls) {
    switch (expression) {
      case Expression.And and -> and.terms().forEach(term -> collect(term, calls));
      case Expression.Or or -> or.terms().forEach(term -> collect(term, calls));
      case Expression.Not not -> collect(not.term(), calls);
      case Expression.Call call -> calls.add(call);
      case Expression.Literal literal -> {}
      case Expression.Weighted weighted -> collect(weighted.term(), calls);
    }
  }

  private Result<Compiled, ErrorDetails> run() {
    for (final var rules : grammar.rules().values()) {
      for (final var rule : rules) {
        final var plan = plan(rule);
        if (rule.kind() == GrammarRule.Kind.LEXICAL) {
          lexeme(rule, plan);
        } else {
          production(rule, plan);
        }
      }
    }
    final var index = new LinkedHashMap<String, List<Production>>();
    for (final var production : productions) {
      index.computeIfAbsent(production.symbol(), symbol -> new ArrayList<>()).add(production);
    }
    final var categories = new HashSet<String>();
    for (final var lexeme : lexemes) {
      if (!lexeme.anonymous()) {
        categories.add(lexeme.category());
      }
    }
    validate(index, categories);
    if (!errors.isEmpty()) {
      return Result.failure(ErrorDetails.of(INVALID, errors));
    }
    final var whitespace = whitespace();
    if (!errors.isEmpty()) {
      return Result.failure(ErrorDetails.of(INVALID, errors));
    }
    return Result.success(
        new Compiled(
            grammar.start(),
            lexemes,
            productions,
            predicates,
            whitespace,
            nullable(),
            index,
            categories,
            grammar.skips()));
  }

  /** The whitespace pattern: the default {@code \\s+}, none, or the grammar's own (S-L4). */
  private Pattern whitespace() {
    if (grammar.whitespace() == null) {
      return WHITESPACE;
    }
    if (grammar.whitespace().isEmpty()) {
      return null;
    }
    try {
      return Pattern.compile(grammar.whitespace());
    } catch (final PatternSyntaxException exception) {
      errors.add("whitespace: invalid pattern: " + exception.getDescription());
      return null;
    }
  }

  private Plan plan(final GrammarRule rule) {
    return switch (Plan.of(rule.constraints())) {
      case Result.Success<Plan, ErrorDetails>(var plan) -> plan;
      case Result.Failure<Plan, ErrorDetails>(var error) -> {
        error.issues().forEach(issue -> errors.add(rule.lhs().symbol() + ": " + issue));
        yield Plan.EMPTY;
      }
    };
  }

  /** Compiles a lexical production into one atomic lexeme (CMP-2). */
  private void lexeme(final GrammarRule rule, final Plan plan) {
    final var symbol = rule.lhs().symbol();
    final var pattern = new StringBuilder();
    final var states = new ArrayList<String>();
    final var labels = new ArrayList<String>();
    for (final var element : rule.rhs()) {
      append(symbol, element, pattern, states, labels);
    }
    if (pattern.isEmpty()) {
      errors.add(symbol + ": lexical production matches no characters");
      return;
    }
    try {
      lexemes.add(
          new Lexeme(
              lexemes.size(),
              symbol,
              Pattern.compile(pattern.toString()),
              rule.lhs().features(),
              states,
              rule.transition(),
              plan,
              rule.cost(),
              labels,
              false));
    } catch (final PatternSyntaxException exception) {
      errors.add(symbol + ": invalid pattern: " + exception.getDescription());
    }
  }

  private void append(
      final String symbol,
      final RuleElement element,
      final StringBuilder pattern,
      final List<String> states,
      final List<String> labels) {
    switch (element) {
      case RuleElement.Terminal terminal ->
          group(terminal.label(), Pattern.quote(terminal.text()), pattern, labels);
      case RuleElement.Regex regex ->
          group(regex.label(), "(?:" + regex.pattern() + ")", pattern, labels);
      case RuleElement.StateAnnotation annotation -> states.add(annotation.state());
      case RuleElement.Alternation alternation -> {
        pattern.append("(?:");
        for (var i = 0; i < alternation.options().size(); i++) {
          if (i > 0) {
            pattern.append('|');
          }
          append(symbol, alternation.options().get(i), pattern, states, labels);
        }
        pattern.append(')');
      }
      case RuleElement.Repetition repetition -> {
        pattern.append("(?:");
        append(symbol, repetition.element(), pattern, states, labels);
        pattern
            .append(')')
            .append(
                switch (repetition.quantifier()) {
                  case ONE_OR_MORE -> '+';
                  case ZERO_OR_MORE -> '*';
                  case OPTIONAL -> '?';
                });
      }
      case RuleElement.Sequence sequence -> {
        for (final var inner : sequence.elements()) {
          append(symbol, inner, pattern, states, labels);
        }
      }
      case RuleElement.TokenMatch match ->
          errors.add(symbol + ": a lexical production cannot contain {TOKEN}");
      case RuleElement.Nonterminal nonterminal ->
          errors.add(
              symbol + ": a lexical production cannot contain the symbol " + nonterminal.name());
    }
  }

  /** The shared anonymous lexeme for an inline literal or regex (CMP-3). */
  private String anonymous(final String category, final String regex) {
    if (!anonymous.containsKey(category)) {
      try {
        final var lexeme =
            new Lexeme(
                lexemes.size(),
                category,
                Pattern.compile(regex),
                Structure.EMPTY,
                List.of(),
                null,
                Plan.EMPTY,
                0,
                List.of(),
                true);
        lexemes.add(lexeme);
        anonymous.put(category, lexeme);
      } catch (final PatternSyntaxException exception) {
        errors.add(category + ": invalid pattern: " + exception.getDescription());
      }
    }
    return category;
  }

  private void production(final GrammarRule rule, final Plan plan) {
    final var rhs = new ArrayList<Element>();
    for (final var element : rule.rhs()) {
      rhs.add(element(rule.lhs().symbol(), element));
    }
    add(rule.lhs().symbol(), rule.lhs().features(), rhs, plan, rule.cost(), false);
  }

  private void add(
      final String symbol,
      final Structure features,
      final List<Element> rhs,
      final Plan plan,
      final long cost,
      final boolean auxiliary) {
    productions.add(
        new Production(productions.size(), symbol, features, rhs, plan, cost, auxiliary));
  }

  private Element element(final String symbol, final RuleElement element) {
    return switch (element) {
      case RuleElement.Nonterminal nonterminal ->
          new Element.Symbol(nonterminal.name(), nonterminal.label(), nonterminal.features());
      case RuleElement.Terminal terminal ->
          new Element.Terminal(
              anonymous("'" + terminal.text() + "'", Pattern.quote(terminal.text())),
              terminal.label());
      case RuleElement.Regex regex ->
          new Element.Terminal(anonymous(regex.pattern(), regex.pattern()), regex.label());
      case RuleElement.TokenMatch match -> new Element.Token(match.label());
      case RuleElement.Repetition repetition ->
          new Element.Symbol(repeat(symbol, repetition), null, link(repetition));
      case RuleElement.Alternation alternation ->
          new Element.Symbol(choose(symbol, alternation), null, link(alternation));
      case RuleElement.Sequence sequence ->
          new Element.Symbol(group(symbol, sequence), null, link(sequence));
      case RuleElement.StateAnnotation annotation -> {
        errors.add(symbol + ": a state annotation is only allowed in a lexical production");
        yield new Element.Token(null);
      }
    };
  }

  /** Lowers a repetition into an auxiliary symbol (CMP-5). */
  private String repeat(final String symbol, final RuleElement.Repetition repetition) {
    final var key = repetition.element() + ":" + repetition.quantifier();
    final var existing = auxiliaries.get(key);
    if (existing != null) {
      return existing;
    }
    final var suffix =
        switch (repetition.quantifier()) {
          case ONE_OR_MORE -> "_plus";
          case ZERO_OR_MORE -> "_star";
          case OPTIONAL -> "_opt";
        };
    final var name = base(repetition.element()) + suffix + "_" + counter++;
    auxiliaries.put(key, name);
    final var link = link(repetition);
    final var inner = element(symbol, repetition.element());
    final var self = new Element.Symbol(name, null, link);
    switch (repetition.quantifier()) {
      case ONE_OR_MORE -> {
        add(name, link, List.of(inner, self), Plan.EMPTY, 0, true);
        add(name, link, List.of(inner), Plan.EMPTY, 0, true);
      }
      case ZERO_OR_MORE -> {
        add(name, link, List.of(inner, self), Plan.EMPTY, 0, true);
        add(name, link, List.of(), Plan.EMPTY, 0, true);
      }
      case OPTIONAL -> {
        add(name, link, List.of(inner), Plan.EMPTY, 0, true);
        add(name, link, List.of(), Plan.EMPTY, 0, true);
      }
    }
    return name;
  }

  /** Lowers an alternation into an auxiliary symbol with one production per option. */
  private String choose(final String symbol, final RuleElement.Alternation alternation) {
    final var key = alternation.toString();
    final var existing = auxiliaries.get(key);
    if (existing != null) {
      return existing;
    }
    final var name = "alt_" + counter++;
    auxiliaries.put(key, name);
    final var link = link(alternation);
    for (final var option : alternation.options()) {
      final var rhs =
          option instanceof RuleElement.Sequence(List<RuleElement> elements)
              ? elements.stream().map(inner -> element(symbol, inner)).toList()
              : List.of(element(symbol, option));
      add(name, link, rhs, Plan.EMPTY, 0, true);
    }
    return name;
  }

  /** Lowers a parenthesised sequence into an auxiliary symbol with one production (S-G4). */
  private String group(final String symbol, final RuleElement.Sequence sequence) {
    final var key = sequence.toString();
    final var existing = auxiliaries.get(key);
    if (existing != null) {
      return existing;
    }
    final var name = "seq_" + counter++;
    auxiliaries.put(key, name);
    add(
        name,
        link(sequence),
        sequence.elements().stream().map(inner -> element(symbol, inner)).toList(),
        Plan.EMPTY,
        0,
        true);
    return name;
  }

  private void validate(final Map<String, List<Production>> index, final Set<String> categories) {
    for (final var skip : grammar.skips()) {
      if (!categories.contains(skip)) {
        errors.add("skip " + skip + " does not name a lexical production");
      }
    }
    if (!grammar.rules().isEmpty()
        && !index.containsKey(grammar.start())
        && !categories.contains(grammar.start())) {
      errors.add("start symbol " + grammar.start() + " is not defined");
    }
    for (final var production : productions) {
      for (final var element : production.rhs()) {
        if (element instanceof Element.Symbol symbol
            && !index.containsKey(symbol.name())
            && !categories.contains(symbol.name())) {
          errors.add(production.symbol() + ": undefined symbol " + symbol.name());
        }
      }
      check(production.symbol(), production.plan());
    }
    for (final var lexeme : lexemes) {
      check(lexeme.category(), lexeme.plan());
    }
  }

  /** Checks every call in a plan against the registry (S-C8). */
  private void check(final String symbol, final Plan plan) {
    final var calls = new ArrayList<Expression.Call>();
    plan.required().forEach(expression -> collect(expression, calls));
    plan.soft().forEach(group -> collect(group.expression(), calls));
    for (final var call : calls) {
      final var entry = predicates.entry(call.name());
      if (entry == null) {
        errors.add(symbol + ": unknown predicate " + call.name());
      } else if (!entry.accepts(call.args().size())) {
        errors.add(
            symbol
                + ": predicate "
                + call.name()
                + " expects "
                + entry.minimum()
                + (entry.maximum() == entry.minimum()
                    ? ""
                    : ".." + (entry.maximum() == Integer.MAX_VALUE ? "n" : entry.maximum()))
                + " arguments, got "
                + call.args().size());
      } else if (call.name().equals("matches")
          && call.args().get(1) instanceof StringConstant(String regex)) {
        try {
          Pattern.compile(regex);
        } catch (final PatternSyntaxException exception) {
          errors.add(symbol + ": invalid regex in matches: " + exception.getDescription());
        }
      }
    }
  }

  /** Symbols that derive the empty string, by fixed point (CMP-7). */
  private Set<String> nullable() {
    final var nullable = new HashSet<String>();
    var changed = true;
    while (changed) {
      changed = false;
      for (final var production : productions) {
        if (!nullable.contains(production.symbol())
            && production.rhs().stream()
                .allMatch(
                    element ->
                        element instanceof Element.Symbol symbol
                            && nullable.contains(symbol.name()))) {
          nullable.add(production.symbol());
          changed = true;
        }
      }
    }
    return nullable;
  }
}
