package com.libdbm.ugf.grammar;

import com.libdbm.ugf.features.Structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes grammar by expanding repetitions into separate rules.
 *
 * <p>This expansion allows the standard Earley algorithm to handle repetitions correctly without
 * special-case handling in the parser.
 *
 * <p>Expansion patterns:
 *
 * <ul>
 *   <li>{@code A+} becomes: A_rep → A A_rep | A
 *   <li>{@code A*} becomes: A_rep → A A_rep | ε (empty)
 *   <li>{@code A?} becomes: A_opt → A | ε (empty)
 * </ul>
 */
public final class GrammarNormalizer {

    private final Grammar input;
    private final Grammar.Builder builder = Grammar.builder();
    private final Map<String, String> cache = new HashMap<>();
    private int counter = 0;

    private GrammarNormalizer(final Grammar input) {
        this.input = input;
    }

    /**
     * Normalize the grammar by expanding all repetition elements.
     */
    public static Grammar normalize(final Grammar grammar) {
        return new GrammarNormalizer(grammar).build();
    }

    private Grammar build() {
        // Copy metadata
        builder.module(input.module());
        builder.start(input.start());
        for (final var imp : input.imports()) {
            builder.addImport(imp);
        }

        // Process all rules
        for (final var entry : input.rules().entrySet()) {
            for (final var rule : entry.getValue()) {
                expand(rule);
            }
        }

        return builder.build();
    }

    /**
     * Expand a single rule, creating auxiliary rules for repetitions.
     */
    private void expand(final GrammarRule rule) {
        final var rhs = new ArrayList<RuleElement>();

        for (final var elem : rule.rhs()) {
            rhs.add(expandElement(elem, rule.constraints()));
        }

        builder.add(new GrammarRule(rule.lhs(), rhs, rule.constraints()));
    }

    /**
     * Expand a single element, creating auxiliary rules for repetitions.
     */
    private RuleElement expandElement(
            final RuleElement elem, final List<com.libdbm.ugf.constraints.Constraint> cs) {

        if (elem instanceof RuleElement.Repetition rep) {
            return expandRepetition(rep, cs);
        } else if (elem instanceof RuleElement.Alternation(List<RuleElement> options)) {
            final var opts = new ArrayList<RuleElement>();
            for (final var opt : options) {
                opts.add(expandElement(opt, cs));
            }
            return new RuleElement.Alternation(opts);
        }
        return elem;
    }

    /**
     * Expand a repetition into auxiliary grammar rules.
     */
    private RuleElement expandRepetition(
            final RuleElement.Repetition rep, final List<com.libdbm.ugf.constraints.Constraint> cs) {

        final var inner = rep.element();
        final var expanded = expandElement(inner, cs);
        final var quant = rep.quantifier();

        // Generate unique name for auxiliary symbol
        final var key = makeKey(expanded, quant);
        var aux = cache.get(key);
        if (aux != null) {
            return new RuleElement.Nonterminal(aux, null, new Structure());
        }

        aux = generateName(expanded, quant);
        cache.put(key, aux);

        // Create auxiliary rules
        final var lhs = new GrammarRule.LHS(aux, new Structure());

        // Auxiliary rules should NOT carry constraints from the original rule.
        // Constraints should only be evaluated on the original rule when all children are collected.
        // Propagating constraints to auxiliary rules causes:
        //   1. Constraints evaluated multiple times (once per auxiliary rule invocation)
        //   2. Labels referenced in the original rule become unbound in auxiliary rules
        switch (quant) {
            case ONE_OR_MORE -> {
                // A_rep → A A_rep
                builder.add(new GrammarRule(lhs, List.of(expanded, nt(aux)), List.of()));
                // A_rep → A
                builder.add(new GrammarRule(lhs, List.of(expanded), List.of()));
            }
            case ZERO_OR_MORE -> {
                // A_rep → A A_rep
                builder.add(new GrammarRule(lhs, List.of(expanded, nt(aux)), List.of()));
                // A_rep → ε (empty)
                builder.add(new GrammarRule(lhs, List.of(), List.of()));
            }
            case OPTIONAL -> {
                // A_opt → A
                builder.add(new GrammarRule(lhs, List.of(expanded), List.of()));
                // A_opt → ε (empty)
                builder.add(new GrammarRule(lhs, List.of(), List.of()));
            }
        }

        return new RuleElement.Nonterminal(aux, null, new Structure());
    }

    private RuleElement.Nonterminal nt(final String name) {
        return new RuleElement.Nonterminal(name, null, new Structure());
    }

    private String generateName(final RuleElement elem, final RuleElement.Quantifier q) {
        final var base = baseName(elem);
        final var suffix =
                switch (q) {
                    case ONE_OR_MORE -> "_plus";
                    case ZERO_OR_MORE -> "_star";
                    case OPTIONAL -> "_opt";
                };
        return base + suffix + "_" + (counter++);
    }

    private String baseName(final RuleElement elem) {
        return switch (elem) {
            case RuleElement.Nonterminal nt -> nt.name();
            case RuleElement.Terminal t -> "term";
            case RuleElement.Regex r -> "regex";
            default -> "elem";
        };
    }

    private String makeKey(final RuleElement elem, final RuleElement.Quantifier q) {
        return elem.toString() + ":" + q;
    }
}
