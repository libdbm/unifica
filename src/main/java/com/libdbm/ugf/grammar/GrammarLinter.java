package com.libdbm.ugf.grammar;

import java.util.*;
import com.libdbm.ugf.constraints.Constraint;
import com.libdbm.ugf.constraints.Predicate;
import com.libdbm.ugf.features.Variable;

/**
 * Linter for detecting common issues in unification grammars: - Unreachable rules (symbols never
 * referenced from start) - Duplicate lexical entries - Constraints referencing unbound labels -
 * Undefined nonterminals - Invalid state usage on non-lexical rules
 */
public final class GrammarLinter {

  /**
   * Predicates that are only valid in lexical rules.
   * Note: at_start and at_position are parser position predicates, not lexer-only.
   */
  private static final Set<String> LEXER_ONLY_PREDICATES =
      Set.of("in_state", "state_depth", "state_contains");

  /** Lint a grammar and return all detected issues. */
  public LintReport lint(final Grammar grammar) {
    final var issues = new ArrayList<LintIssue>();

    // Check for unreachable rules
    issues.addAll(findUnreachableRules(grammar));

    // Check for duplicate lexical entries
    issues.addAll(findDuplicateLexicalEntries(grammar));

    // Check for unbound labels in constraints
    issues.addAll(findUnboundLabels(grammar));

    // Check for undefined nonterminals
    issues.addAll(findUndefinedNonterminals(grammar));

    // Check for invalid state usage on non-lexical rules
    issues.addAll(findInvalidStateUsage(grammar));

    return new LintReport(issues);
  }

  /** Find rules that are unreachable from the start symbol. */
  private List<LintIssue> findUnreachableRules(final Grammar grammar) {
    final var issues = new ArrayList<LintIssue>();

    if (grammar.start() == null || grammar.start().isEmpty()) {
      return issues; // No start symbol defined
    }

    // Find all reachable symbols via BFS from start
    final var reachable = new HashSet<String>();
    final var queue = new LinkedList<String>();

    queue.add(grammar.start());
    reachable.add(grammar.start());

    while (!queue.isEmpty()) {
      final var current = queue.poll();
      final var rules = grammar.rulesFor(current);

      for (final var rule : rules) {
        for (final var elem : rule.rhs()) {
          collectNonterminals(elem, reachable, queue);
        }
      }
    }

    // Find unreachable symbols
    for (final var symbol : grammar.rules().keySet()) {
      if (!reachable.contains(symbol) && !isLexicalSymbol(grammar, symbol)) {
        issues.add(
            new LintIssue(
                LintIssue.Severity.WARNING,
                "Unreachable rule",
                "Symbol '"
                    + symbol
                    + "' is not reachable from start symbol '"
                    + grammar.start()
                    + "'",
                symbol));
      }
    }

    return issues;
  }

  /** Find duplicate lexical entries (multiple rules for same terminal/regex). */
  private List<LintIssue> findDuplicateLexicalEntries(final Grammar grammar) {
    final var issues = new ArrayList<LintIssue>();
    final var patterns = new HashMap<String, List<String>>();

    for (final var entry : grammar.rules().entrySet()) {
      final var symbol = entry.getKey();

      for (final var rule : entry.getValue()) {
        if (isLexicalRule(rule)) {
          final var elem = rule.rhs().get(0);
          final var pattern = getPatternString(elem);

          patterns.computeIfAbsent(pattern, k -> new ArrayList<>()).add(symbol);
        }
      }
    }

    // Report duplicates
    for (final var entry : patterns.entrySet()) {
      if (entry.getValue().size() > 1) {
        issues.add(
            new LintIssue(
                LintIssue.Severity.INFO,
                "Duplicate lexical entry",
                "Pattern '"
                    + entry.getKey()
                    + "' defined in multiple symbols: "
                    + String.join(", ", entry.getValue()),
                null));
      }
    }

    return issues;
  }

  /** Find constraints that reference labels not present in the rule. */
  private List<LintIssue> findUnboundLabels(final Grammar grammar) {
    final var issues = new ArrayList<LintIssue>();

    for (final var entry : grammar.rules().entrySet()) {
      final var symbol = entry.getKey();

      for (final var rule : entry.getValue()) {
        // Collect all labels in the RHS
        final var labels = collectLabels(rule.rhs());

        // Also add the LHS symbol (it's implicitly bound)
        labels.add(symbol);

        // Check each constraint
        for (final var constraint : rule.constraints()) {
          final var usedVars = extractVariables(constraint);

          for (final var var : usedVars) {
            if (!labels.contains(var)) {
              issues.add(
                  new LintIssue(
                      LintIssue.Severity.ERROR,
                      "Unbound label in constraint",
                      "Constraint in rule '"
                          + symbol
                          + "' references undefined label '"
                          + var
                          + "'",
                      symbol));
            }
          }
        }
      }
    }

    return issues;
  }

  /** Find references to undefined nonterminals. */
  private List<LintIssue> findUndefinedNonterminals(final Grammar grammar) {
    final var issues = new ArrayList<LintIssue>();
    final var defined = grammar.rules().keySet();

    for (final var entry : grammar.rules().entrySet()) {
      final var symbol = entry.getKey();

      for (final var rule : entry.getValue()) {
        for (final var elem : rule.rhs()) {
          collectUndefinedNonterminals(elem, defined, symbol, issues);
        }
      }
    }

    return issues;
  }

  /** Find invalid state usage on non-lexical rules. */
  private List<LintIssue> findInvalidStateUsage(final Grammar grammar) {
    final var issues = new ArrayList<LintIssue>();

    for (final var entry : grammar.rules().entrySet()) {
      final var sym = entry.getKey();

      for (final var rule : entry.getValue()) {
        final var lex = isLexicalRule(rule);

        // Check 1: Transition on Terminal/Regex in non-lexical rule
        if (!lex && hasTransition(rule.rhs())) {
          issues.add(
              new LintIssue(
                  LintIssue.Severity.ERROR,
                  "State transition on non-lexical rule",
                  String.format(
                      "Rule '%s' has state transition but is not a lexical rule. "
                          + "State transitions are only valid on lexical rules.",
                      sym),
                  sym));
        }

        // Check 2: StateAnnotation in non-lexical rule
        if (!lex && hasStateAnnotation(rule.rhs())) {
          issues.add(
              new LintIssue(
                  LintIssue.Severity.ERROR,
                  "State annotation in non-lexical rule",
                  String.format(
                      "Rule '%s' contains state annotation but is not a lexical rule. "
                          + "State annotations are only valid in lexical rules.",
                      sym),
                  sym));
        }

        // Check 3: Lexer-only predicates in non-lexical constraints
        // This is a WARNING rather than ERROR because these predicates on non-lexical rules
        // can be intentional (e.g., to restrict when certain nonterminals can be expanded)
        if (!lex) {
          final var preds = extractLexerPredicates(rule.constraints());
          if (!preds.isEmpty()) {
            issues.add(
                new LintIssue(
                    LintIssue.Severity.WARNING,
                    "Lexer-only predicate on non-lexical rule",
                    String.format(
                        "Rule '%s' uses lexer-only predicate(s) %s but is not a lexical rule. "
                            + "This is unusual; consider if this is intentional.",
                        sym, preds),
                    sym));
          }
        }
      }
    }

    return issues;
  }

  /** Check if any element in the list has a state transition. */
  private boolean hasTransition(final List<RuleElement> elems) {
    return elems.stream().anyMatch(this::containsTransition);
  }

  private boolean containsTransition(final RuleElement elem) {
    return switch (elem) {
      case RuleElement.Terminal t -> t.transition() != null;
      case RuleElement.Regex r -> r.transition() != null;
      case RuleElement.Alternation alt -> alt.options().stream().anyMatch(this::containsTransition);
      case RuleElement.Repetition rep -> containsTransition(rep.element());
      default -> false;
    };
  }

  /** Check if any element in the list contains a state annotation. */
  private boolean hasStateAnnotation(final List<RuleElement> elems) {
    return elems.stream().anyMatch(this::containsStateAnnotation);
  }

  private boolean containsStateAnnotation(final RuleElement elem) {
    return switch (elem) {
      case RuleElement.StateAnnotation sa -> true;
      case RuleElement.Alternation alt ->
          alt.options().stream().anyMatch(this::containsStateAnnotation);
      case RuleElement.Repetition rep -> containsStateAnnotation(rep.element());
      default -> false;
    };
  }

  /** Extract lexer-only predicate names used in constraints. */
  private Set<String> extractLexerPredicates(final List<Constraint> constraints) {
    final var out = new HashSet<String>();
    for (final var c : constraints) {
      collectLexerPredicates(c, out);
    }
    return out;
  }

  private void collectLexerPredicates(final Constraint c, final Set<String> out) {
    switch (c) {
      case Predicate p -> {
        if (LEXER_ONLY_PREDICATES.contains(p.name())) {
          out.add(p.name());
        }
      }
      case Constraint.And a -> a.conjuncts().forEach(x -> collectLexerPredicates(x, out));
      case Constraint.Or o -> o.disjuncts().forEach(x -> collectLexerPredicates(x, out));
      case Constraint.Not n -> collectLexerPredicates(n.constraint(), out);
    }
  }

  // Helper methods

  private void collectNonterminals(
      final RuleElement elem, final Set<String> reachable, final Queue<String> queue) {
    switch (elem) {
      case RuleElement.Nonterminal nt -> {
        if (reachable.add(nt.name())) {
          queue.add(nt.name());
        }
      }
      case RuleElement.Repetition rep -> collectNonterminals(rep.element(), reachable, queue);
      case RuleElement.Alternation alt -> {
        for (final var opt : alt.options()) {
          collectNonterminals(opt, reachable, queue);
        }
      }
      default -> {} // Terminal, Regex
    }
  }

  private void collectUndefinedNonterminals(
      final RuleElement elem,
      final Set<String> defined,
      final String context,
      final List<LintIssue> issues) {
    switch (elem) {
      case RuleElement.Nonterminal nt -> {
        if (!defined.contains(nt.name())) {
          issues.add(
              new LintIssue(
                  LintIssue.Severity.ERROR,
                  "Undefined nonterminal",
                  "Rule '" + context + "' references undefined nonterminal '" + nt.name() + "'",
                  context));
        }
      }
      case RuleElement.Repetition rep ->
          collectUndefinedNonterminals(rep.element(), defined, context, issues);
      case RuleElement.Alternation alt -> {
        for (final var opt : alt.options()) {
          collectUndefinedNonterminals(opt, defined, context, issues);
        }
      }
      default -> {} // Terminal, Regex
    }
  }

  /**
   * Check if a rule is lexical (RHS consists only of terminals, regexes, state annotations).
   * Lexical rules may have state transitions; grammar rules may not.
   */
  private boolean isLexicalRule(final GrammarRule rule) {
    // Filter out state annotations before checking
    final var elems =
        rule.rhs().stream().filter(e -> !(e instanceof RuleElement.StateAnnotation)).toList();

    if (elems.isEmpty()) {
      return false;
    }

    return elems.stream().allMatch(this::isLexicalElement);
  }

  /** Check if a single element is lexical (terminal, regex, or composition thereof). */
  private boolean isLexicalElement(final RuleElement elem) {
    return switch (elem) {
      case RuleElement.Terminal t -> true;
      case RuleElement.Regex r -> true;
      case RuleElement.Alternation alt -> alt.options().stream().allMatch(this::isLexicalElement);
      case RuleElement.Repetition rep -> isLexicalElement(rep.element());
      case RuleElement.StateAnnotation sa -> true; // State annotations are only in lexical rules
      default -> false; // Nonterminal
    };
  }

  private boolean isLexicalSymbol(final Grammar grammar, final String symbol) {
    final List<GrammarRule> rules = grammar.rulesFor(symbol);
    return !rules.isEmpty() && rules.stream().allMatch(this::isLexicalRule);
  }

  private String getPatternString(final RuleElement elem) {
    return switch (elem) {
      case RuleElement.Terminal term -> "'" + term.text() + "'";
      case RuleElement.Regex regex -> "/" + regex.pattern() + "/";
      default -> "";
    };
  }

  private Set<String> collectLabels(final List<RuleElement> rhs) {
    final var labels = new HashSet<String>();

    for (final var elem : rhs) {
      collectLabelsRecursive(elem, labels);
    }

    return labels;
  }

  private void collectLabelsRecursive(final RuleElement elem, final Set<String> labels) {
    switch (elem) {
      case RuleElement.Nonterminal nt -> {
        if (nt.label() != null) labels.add(nt.label());
        // Also add the nonterminal name itself (it's accessible in constraints)
        labels.add(nt.name());
      }
      case RuleElement.Terminal term -> {
        if (term.label() != null) labels.add(term.label());
      }
      case RuleElement.Regex regex -> {
        if (regex.label() != null) labels.add(regex.label());
      }
      case RuleElement.Repetition rep -> collectLabelsRecursive(rep.element(), labels);
      case RuleElement.Alternation alt -> {
        for (final var opt : alt.options()) {
          collectLabelsRecursive(opt, labels);
        }
      }
      case RuleElement.StateAnnotation stateAnnotation -> {
        // State annotations don't have labels
      }
      case RuleElement.TokenMatch tm -> {
        if (tm.label() != null) labels.add(tm.label());
      }
    }
  }

  private Set<String> extractVariables(final Constraint constraint) {
    final var vars = new HashSet<String>();

    switch (constraint) {
      case Predicate pred -> {
        // All predicate arguments are potential variables
        // Filter out string literals (quoted strings)
        for (final var arg : pred.args()) {
          if (arg instanceof Variable(String name)) {
            vars.add(name);
          }
        }
      }
      case Constraint.And and -> {
        for (final var c : and.conjuncts()) {
          vars.addAll(extractVariables(c));
        }
      }
      case Constraint.Or or -> {
        for (final var c : or.disjuncts()) {
          vars.addAll(extractVariables(c));
        }
      }
      case Constraint.Not not -> vars.addAll(extractVariables(not.constraint()));
    }

    return vars;
  }

  /** Report containing all linting issues found. */
  public record LintReport(List<LintIssue> issues) {

    public boolean hasErrors() {
      return issues.stream().anyMatch(i -> i.severity() == LintIssue.Severity.ERROR);
    }

    public boolean hasWarnings() {
      return issues.stream().anyMatch(i -> i.severity() == LintIssue.Severity.WARNING);
    }

    public List<LintIssue> errors() {
      return issues.stream().filter(i -> i.severity() == LintIssue.Severity.ERROR).toList();
    }

    public List<LintIssue> warnings() {
      return issues.stream().filter(i -> i.severity() == LintIssue.Severity.WARNING).toList();
    }

    public String format() {
      if (issues.isEmpty()) {
        return "No issues found.";
      }

      final var sb = new StringBuilder();
      sb.append("Grammar Linting Report\n");
      sb.append("======================\n\n");

      final var errors = errors();
      final var warnings = warnings();
      final var infos =
          issues.stream().filter(i -> i.severity() == LintIssue.Severity.INFO).toList();

      if (!errors.isEmpty()) {
        sb.append("ERRORS (").append(errors.size()).append("):\n");
        for (final var issue : errors) {
          sb.append("  ").append(issue.format()).append("\n");
        }
        sb.append("\n");
      }

      if (!warnings.isEmpty()) {
        sb.append("WARNINGS (").append(warnings.size()).append("):\n");
        for (final var issue : warnings) {
          sb.append("  ").append(issue.format()).append("\n");
        }
        sb.append("\n");
      }

      if (!infos.isEmpty()) {
        sb.append("INFO (").append(infos.size()).append("):\n");
        for (final var issue : infos) {
          sb.append("  ").append(issue.format()).append("\n");
        }
      }

      return sb.toString();
    }
  }

  /** A single linting issue. */
  public record LintIssue(Severity severity, String title, String message, String context) {
    public String format() {
      final var prefix =
          switch (severity) {
            case ERROR -> "[ERROR]";
            case WARNING -> "[WARN]";
            case INFO -> "[INFO]";
          };

      if (context != null) {
        return String.format("%s %s: %s (in %s)", prefix, title, message, context);
      } else {
        return String.format("%s %s: %s", prefix, title, message);
      }
    }

    public enum Severity {
      ERROR, // Grammar is invalid
      WARNING, // Potentially problematic
      INFO // Informational
    }
  }
}
