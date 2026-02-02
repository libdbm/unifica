package com.libdbm.ugf.grammar;

import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Utilities;

import java.util.List;
import java.util.Map;
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

    /**
     * Non-terminal reference (e.g., "np", "clause")
     */
    record Nonterminal(String name, String label, Structure features) implements RuleElement {
        public Nonterminal {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(features, "features must not be null");
        }

        public Nonterminal(final String name) {
            this(name, null, new Structure());
        }

        public Nonterminal(final String name, final String label) {
            this(name, label, new Structure());
        }

        /**
         * Legacy constructor for backward compatibility with string-based features.
         */
        @Deprecated
        public static Nonterminal fromMap(
                final String name, final String label, final Map<String, String> features) {
            return new Nonterminal(name, label, Utilities.fromMap(features));
        }
    }

    /**
     * Terminal string (e.g., 'is', 'the')
     */
    record Terminal(String text, String label, String transition) implements RuleElement {
        public Terminal {
            Objects.requireNonNull(text, "text must not be null");
        }

        public Terminal(final String text) {
            this(text, null, null);
        }

        public Terminal(final String text, final String label) {
            this(text, label, null);
        }

        public Terminal with(final String label) {
            return new Terminal(text, label, transition);
        }

        public Terminal transition(final String next) {
            return new Terminal(text, label, next);
        }
    }

    /**
     * Regular expression pattern (e.g., [A-Z][a-z]*)
     *
     * <p>Pattern is compiled once during construction for performance.
     */
    record Regex(String pattern, String label, Pattern compiled, String transition)
            implements RuleElement {
        public Regex {
            Objects.requireNonNull(pattern, "pattern must not be null");
            Objects.requireNonNull(compiled, "compiled must not be null");
        }

        public Regex(final String pattern) {
            this(pattern, null, Pattern.compile(pattern), null);
        }

        public Regex(final String pattern, final String label) {
            this(pattern, label, Pattern.compile(pattern), null);
        }

        public Regex with(final String label) {
            return new Regex(pattern, label, compiled, transition);
        }

        public Regex transition(final String next) {
            return new Regex(pattern, label, compiled, next);
        }
    }

    /**
     * Repetition operators (*, +, ?) Note: ? (ZERO_OR_ONE) is semantically optional, no separate
     * Optional type needed
     */
    record Repetition(RuleElement element, Quantifier quantifier) implements RuleElement {
    }

    /**
     * Alternation (choice between options)
     */
    record Alternation(List<RuleElement> options) implements RuleElement {
    }

    /**
     * State annotation for lexer state transitions (e.g., {CONTENT})
     */
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
