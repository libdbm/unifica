package com.libdbm.ugf.parser;

import com.libdbm.ugf.constraints.Constraint;
import com.libdbm.ugf.constraints.Predicate;
import com.libdbm.ugf.constraints.Strength;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.RuleElement;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Extracts lexical rules (terminals and regexes) from a grammar.
 *
 * <p>Identifies lexical rules (rules whose RHS is a single terminal/regex or alternation of
 * terminals/regexes) and converts them to {@link LexicalRule} objects for tokenization.
 *
 * <p>The extractor handles:
 *
 * <ul>
 *   <li>Single terminal rules: {@code WORD --> 'hello'}
 *   <li>Single regex rules: {@code NUMBER --> [0-9]+}
 *   <li>Alternation rules: {@code PUNCT --> '.' | '!' | '?'}
 *   <li>Sequences of terminals: {@code ARROW --> '-' '>'}
 *   <li>Inline terminals in production rules
 *   <li>Repetition patterns: {@code DIGITS --> [0-9]+}
 * </ul>
 */
public final class TerminalExtractor {

    private TerminalExtractor() {
    }

    /**
     * Extract all lexical rules from a grammar.
     *
     * @param grammar the grammar to extract from
     * @return list of lexical rules, sorted by priority
     */
    public static List<LexicalRule> extract(final Grammar grammar) {
        final var rules = new ArrayList<LexicalRule>();
        final var seen = new HashSet<String>();

        // Extract from lexical rules (nonterminals that expand to terminals/regexes)
        for (final var entry : grammar.rules().entrySet()) {
            final var name = entry.getKey();
            for (final var rule : entry.getValue()) {
                extract(rule, name, rules, seen);
            }
        }

        // Extract inline terminals from production rules
        extract(grammar, rules, seen);

        // Sort: literals before regexes, longer first, higher priority first
        rules.sort(
                Comparator.<LexicalRule>comparingInt(r -> r.literal() ? 0 : 1)
                        .thenComparingInt(r -> -r.text().length())
                        .thenComparingInt(r -> -r.priority()));

        return rules;
    }

    /**
     * Extract lexical rules from a single grammar rule.
     */
    private static void extract(
            final GrammarRule rule,
            final String name,
            final List<LexicalRule> rules,
            final Set<String> seen) {

        final var rhs = rule.rhs();
        if (rhs.isEmpty()) return;

        // Collect state annotations from RHS and convert to in_state() constraints
        final var all = new ArrayList<Constraint>();
        if (rule.constraints() != null) {
            all.addAll(rule.constraints());
        }
        all.addAll(state(rhs));

        final var constraints = List.copyOf(all);
        final var features = rule.lhs().features();

        if (rhs.size() == 1) {
            // Single element - extract directly
            extract(rhs.getFirst(), name, features, constraints, rules, seen);
        } else if (isTerminalSequence(rhs)) {
            // Sequence of terminals/regexes - combine into single pattern
            final var combined = combine(rhs, constraints);
            if (combined != null) {
                final var key = name + ":" + combined.text() + ":" + constraints + ":" + combined.next();
                if (seen.add(key)) {
                    rules.add(combined.withCategory(name).withFeatures(features));
                }
            }
        }
    }

    /**
     * Extract state annotations from RHS and convert to in_state() predicates.
     */
    private static List<Constraint> state(final List<RuleElement> rhs) {
        final var result = new ArrayList<Constraint>();
        for (final var elem : rhs) {
            state(elem, result);
        }
        return result;
    }

    /**
     * Recursively extract state annotations from an element.
     */
    private static void state(final RuleElement elem, final List<Constraint> result) {
        switch (elem) {
            case RuleElement.StateAnnotation sa -> result.add(
                    new Predicate("in_state", List.of(new StringConstant(sa.state())), Strength.REQUIRED, 0));
            case RuleElement.Alternation alt -> {
                for (final var opt : alt.options()) {
                    state(opt, result);
                }
            }
            case RuleElement.Repetition rep -> state(rep.element(), result);
            default -> {
                // Terminal, Regex, Nonterminal, TokenMatch - no state annotations
            }
        }
    }

    /**
     * Extract lexical rule from a single element.
     */
    private static void extract(
            final RuleElement element,
            final String name,
            final Structure features,
            final List<Constraint> constraints,
            final List<LexicalRule> rules,
            final Set<String> seen) {

        switch (element) {
            case RuleElement.Terminal t -> {
                final var key = buildKey(name, "'" + t.text() + "'", features, constraints, t.transition());
                if (seen.add(key)) {
                    rules.add(
                            new LexicalRule(
                                    Pattern.compile(Pattern.quote(t.text())),
                                    t.text(),
                                    name,
                                    features,
                                    100, // literals get higher priority
                                    true,
                                    constraints,
                                    t.transition())); // next state transition
                }
            }

            case RuleElement.Regex r -> {
                final var key =
                        buildKey(name, "[" + r.pattern() + "]", features, constraints, r.transition());
                if (seen.add(key)) {
                    rules.add(
                            new LexicalRule(
                                    r.compiled(),
                                    r.pattern(),
                                    name,
                                    features,
                                    50,
                                    false,
                                    constraints,
                                    r.transition())); // next state transition
                }
            }

            case RuleElement.Alternation alt -> {
                for (final var opt : alt.options()) {
                    extract(opt, name, features, constraints, rules, seen);
                }
            }

            case RuleElement.Repetition rep -> {
                final var inner = extractPattern(rep.element());
                if (inner != null) {
                    final var suffix =
                            switch (rep.quantifier()) {
                                case ZERO_OR_MORE -> "*";
                                case ONE_OR_MORE -> "+";
                                case OPTIONAL -> "?";
                            };
                    final var modified = "(?:" + inner.pattern + ")" + suffix;
                    final var key =
                            buildKey(name, "[" + modified + "]", features, constraints, inner.transition);
                    if (seen.add(key)) {
                        rules.add(
                                new LexicalRule(
                                        Pattern.compile(modified),
                                        modified,
                                        name,
                                        features,
                                        inner.literal ? 100 : 50,
                                        false, // repetition is never pure literal
                                        constraints,
                                        inner.transition));
                    }
                }
            }

            default -> {
                // Not a lexical element (nonterminal, state annotation, etc.)
            }
        }
    }

    /**
     * Extract inline terminal literals from production rules.
     */
    private static void extract(
            final Grammar grammar, final List<LexicalRule> rules, final Set<String> seen) {

        for (final var entry : grammar.rules().entrySet()) {
            for (final var rule : entry.getValue()) {
                for (final var elem : rule.rhs()) {
                    extract(elem, rules, seen);
                }
            }
        }
    }

    /**
     * Extract inline terminal/regex from a single element (recursively handles alternations).
     */
    private static void extract(
            final RuleElement elem, final List<LexicalRule> rules, final Set<String> seen) {

        switch (elem) {
            case RuleElement.Terminal t -> {
                // Only add if this text doesn't already exist as a rule
                final var exists = rules.stream().anyMatch(r -> r.text().equals(t.text()) && r.literal());
                if (!exists) {
                    final var key = "inline:'" + t.text() + "':" + t.transition();
                    if (seen.add(key)) {
                        rules.add(
                                new LexicalRule(
                                        Pattern.compile(Pattern.quote(t.text())),
                                        t.text(),
                                        null, // no name for inline terminals
                                        new Structure(),
                                        100,
                                        true,
                                        List.of(),
                                        t.transition()));
                    }
                }
            }

            case RuleElement.Regex r -> {
                // Only add if this pattern doesn't already exist as a rule
                final var exists = rules.stream().anyMatch(rule -> rule.text().equals(r.pattern()) && !rule.literal());
                if (!exists) {
                    final var key = "inline:[" + r.pattern() + "]:" + r.transition();
                    if (seen.add(key)) {
                        rules.add(
                                new LexicalRule(
                                        r.compiled(),
                                        r.pattern(),
                                        null, // no name for inline regexes
                                        new Structure(),
                                        50,
                                        false,
                                        List.of(),
                                        r.transition()));
                    }
                }
            }

            case RuleElement.Alternation alt -> {
                for (final var opt : alt.options()) {
                    extract(opt, rules, seen);
                }
            }

            case RuleElement.Repetition rep -> {
                extract(rep.element(), rules, seen);
            }

            default -> {
                // Ignore nonterminals, state annotations, etc.
            }
        }
    }

    /**
     * Check if RHS is entirely composed of terminals and regexes.
     */
    private static boolean isTerminalSequence(final List<RuleElement> rhs) {
        for (final var elem : rhs) {
            if (!(elem instanceof RuleElement.Terminal)
                    && !(elem instanceof RuleElement.Regex)
                    && !(elem instanceof RuleElement.Alternation alt && isLexicalAlt(alt))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if alternation contains only terminals/regexes.
     */
    private static boolean isLexicalAlt(final RuleElement.Alternation alt) {
        for (final var opt : alt.options()) {
            if (!(opt instanceof RuleElement.Terminal) && !(opt instanceof RuleElement.Regex)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Combine a sequence of terminals/regexes into a single pattern.
     */
    private static LexicalRule combine(
            final List<RuleElement> rhs, final List<Constraint> constraints) {
        final var pattern = new StringBuilder();
        var literal = true;
        String transition = null;

        for (final var elem : rhs) {
            switch (elem) {
                case RuleElement.Terminal t -> {
                    pattern.append(Pattern.quote(t.text()));
                    if (t.transition() != null) transition = t.transition();
                }
                case RuleElement.Regex r -> {
                    pattern.append(r.pattern());
                    literal = false;
                    if (r.transition() != null) transition = r.transition();
                }
                case RuleElement.Alternation alt -> {
                    pattern.append("(?:");
                    var first = true;
                    for (final var opt : alt.options()) {
                        if (!first) pattern.append("|");
                        switch (opt) {
                            case RuleElement.Terminal t -> {
                                pattern.append(Pattern.quote(t.text()));
                                if (t.transition() != null) transition = t.transition();
                            }
                            case RuleElement.Regex r -> {
                                pattern.append(r.pattern());
                                literal = false;
                                if (r.transition() != null) transition = r.transition();
                            }
                            default -> {
                                return null;
                            }
                        }
                        first = false;
                    }
                    pattern.append(")");
                }
                default -> {
                    return null;
                }
            }
        }

        return new LexicalRule(
                Pattern.compile(pattern.toString()),
                pattern.toString(),
                null,
                new Structure(),
                literal ? 100 : 50,
                literal,
                constraints,
                transition);
    }

    /**
     * Build a unique key for deduplication.
     */
    private static String buildKey(
            final String name,
            final String pattern,
            final Structure features,
            final List<Constraint> constraints,
            final String transition) {
        return name + ":" + pattern + ":" + features + ":" + constraints + ":" + transition;
    }

    /**
     * Extract regex pattern from a lexical element.
     */
    private static PatternInfo extractPattern(final RuleElement element) {
        return switch (element) {
            case RuleElement.Terminal t -> new PatternInfo(Pattern.quote(t.text()), true, t.transition());
            case RuleElement.Regex r -> new PatternInfo(r.pattern(), false, r.transition());
            case RuleElement.Alternation alt -> {
                final var sb = new StringBuilder();
                var literal = true;
                String transition = null;
                for (int i = 0; i < alt.options().size(); i++) {
                    if (i > 0) sb.append("|");
                    final var opt = alt.options().get(i);
                    final var inner = extractPattern(opt);
                    if (inner == null) yield null;
                    sb.append(inner.pattern);
                    if (!inner.literal) literal = false;
                    if (inner.transition != null) transition = inner.transition;
                }
                yield new PatternInfo(sb.toString(), literal, transition);
            }
            default -> null;
        };
    }

    /**
     * Helper record for pattern extraction.
     */
    private record PatternInfo(String pattern, boolean literal, String transition) {
    }
}
