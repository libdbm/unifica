package com.libdbm.ugf.parser;

import com.libdbm.ugf.constraints.Constraint;
import com.libdbm.ugf.features.Structure;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks diagnostic information about parse failures and constraint violations.
 *
 * <p>In addition to the per-failure records this class also holds a {@link ParseLattice} snapshot,
 * populated on parse failure. The lattice exposes the Earley chart at the moment the parser gave
 * up: every active, completed and rejected item per column, the character span covered by each
 * column, the furthest column reached with meaningful progress, and the specific reason each
 * candidate path failed.
 */
public final class ParseDiagnostics {

    private final List<ConstraintFailure> constraintFailures = new ArrayList<>();
    private final List<TokenizationError> tokenizationErrors = new ArrayList<>();
    private final List<ParsingIssue> parsingIssues = new ArrayList<>();
    private final List<RegexFailure> regexFailures = new ArrayList<>();
    private final List<QuantifierLoop> quantifierLoops = new ArrayList<>();
    private final List<UnificationFailure> unificationFailures = new ArrayList<>();
    private final List<Ambiguity> ambiguities = new ArrayList<>();
    private ParseLattice lattice;

    /**
     * Record a constraint failure without span information.
     */
    public void recordConstraintFailure(
            final String name,
            final Constraint constraint,
            final String reason,
            final int position,
            final boolean isHard) {
        recordConstraintFailure(name, constraint, reason, position, isHard, null);
    }

    /**
     * Record a constraint failure with token span.
     */
    public void recordConstraintFailure(
            final String name,
            final Constraint constraint,
            final String reason,
            final int position,
            final boolean isHard,
            final Span span) {
        constraintFailures.add(new ConstraintFailure(name, constraint, reason, position, isHard, span));
    }

    /**
     * Record a tokenization error.
     */
    public void recordTokenizationError(
            final int position, final String input, final String expected) {
        recordTokenizationError(position, input, expected, null);
    }

    public void recordTokenizationError(
            final int position, final String input, final String expected, final Span span) {
        tokenizationErrors.add(new TokenizationError(position, input, expected, span));
    }

    /**
     * Record a general parsing issue.
     */
    public void recordParsingIssue(final String message, final int position) {
        recordParsingIssue(message, position, null);
    }

    public void recordParsingIssue(final String message, final int position, final Span span) {
        parsingIssues.add(new ParsingIssue(message, position, span));
    }

    /**
     * Record a regex pattern match failure.
     */
    public void recordRegexFailure(final String pattern, final String input, final int position) {
        recordRegexFailure(pattern, input, position, null);
    }

    public void recordRegexFailure(
            final String pattern, final String input, final int position, final Span span) {
        regexFailures.add(new RegexFailure(pattern, input, position, span));
    }

    /**
     * Record a potential quantifier loop (excessive repetition).
     */
    public void recordQuantifierLoop(
            final String ruleName, final String element, final int iterations, final int position) {
        recordQuantifierLoop(ruleName, element, iterations, position, null);
    }

    public void recordQuantifierLoop(
            final String ruleName,
            final String element,
            final int iterations,
            final int position,
            final Span span) {
        quantifierLoops.add(new QuantifierLoop(ruleName, element, iterations, position, span));
    }

    /**
     * Record a feature unification failure.
     */
    public void recordUnificationFailure(
            final String symbol,
            final Structure waiting,
            final Structure completed,
            final Structure expected,
            final String reason,
            final int position) {
        recordUnificationFailure(symbol, waiting, completed, expected, reason, position, null);
    }

    public void recordUnificationFailure(
            final String symbol,
            final Structure waiting,
            final Structure completed,
            final Structure expected,
            final String reason,
            final int position,
            final Span span) {
        unificationFailures.add(
                new UnificationFailure(symbol, waiting, completed, expected, reason, position, span));
    }

    /**
     * Record the parse lattice captured at the point of failure.
     */
    public void recordLattice(final ParseLattice lattice) {
        this.lattice = lattice;
    }

    /**
     * Record where two derivations of the same constituent tied at the same penalty.
     */
    public void recordAmbiguity(final String symbol, final Span span) {
        recordAmbiguity(symbol, span, List.of());
    }

    /**
     * Record a tie together with a sketch of each competing derivation.
     */
    public void recordAmbiguity(final String symbol, final Span span, final List<String> readings) {
        ambiguities.add(new Ambiguity(symbol, span, readings));
    }

    /**
     * Every constituent at which two derivations tied, in the order the ties were found. Empty when
     * the parse is not ambiguous.
     */
    public List<Ambiguity> ambiguities() {
        return List.copyOf(ambiguities);
    }

    public List<ConstraintFailure> constraintFailures() {
        return List.copyOf(constraintFailures);
    }

    public List<TokenizationError> tokenizationErrors() {
        return List.copyOf(tokenizationErrors);
    }

    public List<ParsingIssue> parsingIssues() {
        return List.copyOf(parsingIssues);
    }

    public List<RegexFailure> regexFailures() {
        return List.copyOf(regexFailures);
    }

    public List<QuantifierLoop> quantifierLoops() {
        return List.copyOf(quantifierLoops);
    }

    public List<UnificationFailure> unificationFailures() {
        return List.copyOf(unificationFailures);
    }

    /**
     * Returns the parse lattice snapshot, or null if none was recorded (successful parse, or
     * lattice capture disabled).
     */
    public ParseLattice lattice() {
        return lattice;
    }

    /**
     * Check if there are any failures recorded.
     */
    public boolean hasFailures() {
        return !constraintFailures.isEmpty()
                || !tokenizationErrors.isEmpty()
                || !parsingIssues.isEmpty()
                || !regexFailures.isEmpty()
                || !quantifierLoops.isEmpty()
                || !unificationFailures.isEmpty()
                || lattice != null;
    }

    /**
     * Generate a human-readable diagnostic report.
     */
    public String generateReport() {
        final var sb = new StringBuilder();

        if (!constraintFailures.isEmpty()) {
            sb.append("=== Constraint Failures ===\n");
            for (final var failure : constraintFailures) {
                sb.append(
                        String.format(
                                "  [%s] Rule '%s' at %s: %s\n",
                                failure.isHard ? "HARD" : "SOFT",
                                failure.ruleName,
                                describe(failure.position, failure.span),
                                failure.reason));
                sb.append(String.format("    Constraint: %s\n", failure.constraint));
            }
            sb.append("\n");
        }

        if (!tokenizationErrors.isEmpty()) {
            sb.append("=== Tokenization Errors ===\n");
            for (final var error : tokenizationErrors) {
                sb.append(
                        String.format(
                                "  %s: %s (expected: %s)\n",
                                describe(error.position, error.span), error.input, error.expected));
            }
            sb.append("\n");
        }

        if (!parsingIssues.isEmpty()) {
            sb.append("=== Parsing Issues ===\n");
            for (final var issue : parsingIssues) {
                sb.append(String.format("  %s: %s\n", describe(issue.position, issue.span), issue.message));
            }
            sb.append("\n");
        }

        if (!regexFailures.isEmpty()) {
            sb.append("=== Regex Pattern Failures ===\n");
            for (final var failure : regexFailures) {
                sb.append(
                        String.format(
                                "  %s: Pattern '%s' failed to match input '%s'\n",
                                describe(failure.position, failure.span), failure.pattern, failure.input));
            }
            sb.append("\n");
        }

        if (!quantifierLoops.isEmpty()) {
            sb.append("=== Quantifier Loops (Excessive Repetition) ===\n");
            for (final var loop : quantifierLoops) {
                sb.append(
                        String.format(
                                "  %s: Rule '%s' element '%s' iterated %d times (possible infinite loop)\n",
                                describe(loop.position, loop.span),
                                loop.ruleName,
                                loop.element,
                                loop.iterations));
            }
            sb.append("\n");
        }

        if (!unificationFailures.isEmpty()) {
            sb.append("=== Feature Unification Failures ===\n");
            for (final var failure : unificationFailures) {
                sb.append(
                        String.format(
                                "  %s: Symbol '%s' - %s\n",
                                describe(failure.position, failure.span), failure.symbol, failure.reason));
                sb.append(String.format("    Expected: %s\n", failure.expected));
                sb.append(String.format("    Waiting:  %s\n", failure.waiting));
                sb.append(String.format("    Completed: %s\n", failure.completed));
            }
            sb.append("\n");
        }

        if (!ambiguities.isEmpty()) {
            sb.append("=== Ambiguities ===\n");
            for (final var ambiguity : ambiguities) {
                sb.append(
                        String.format(
                                "  '%s' has more than one derivation at %s\n",
                                ambiguity.symbol, describe(ambiguity.span.tokenIndex(), ambiguity.span)));
                for (final var reading : ambiguity.readings) {
                    sb.append("    reading: ").append(reading).append("\n");
                }
            }
            sb.append("\n");
        }

        if (lattice != null) {
            sb.append(lattice.render());
        }

        if (!hasFailures()) {
            sb.append("No failures recorded.\n");
        }

        return sb.toString();
    }

    private static String describe(final int position, final Span span) {
        if (span == null) {
            return "position " + position;
        }
        return String.format(
                "column %d (token #%d, chars %d..%d)",
                position, span.tokenIndex(), span.charStart(), span.charEnd());
    }

    /**
     * Character span of a token or column in the original input. {@code tokenIndex} is the lattice
     * column index; {@code charStart} and {@code charEnd} are absolute offsets in the input text.
     */
    public record Span(int tokenIndex, int charStart, int charEnd) {
    }

    /**
     * A constituent that two derivations reached at the same penalty, so the penalties could not
     * choose between them. {@code span} covers the input the constituent spans, and {@code readings}
     * sketches each competing derivation: the children it was built from, with their features and
     * the words they cover.
     */
    public record Ambiguity(String symbol, Span span, List<String> readings) {
        public Ambiguity {
            readings = List.copyOf(readings);
        }

        public Ambiguity(final String symbol, final Span span) {
            this(symbol, span, List.of());
        }
    }

    /**
     * State of a lattice item at the moment the snapshot was taken.
     */
    public enum State {
        ACTIVE,
        COMPLETE,
        REJECTED
    }

    /**
     * Why a candidate path failed to produce a successful parse.
     */
    public enum FailureKind {
        /** A complete parse exists but its LHS symbol is not the grammar start symbol. */
        WRONG_START,
        /** The candidate failed a required constraint during completion or extraction. */
        CONSTRAINT,
        /** Feature unification between the waiting and completed items failed. */
        UNIFICATION,
        /** A partial parse got stuck waiting for a symbol that never arrived. */
        DEAD_END,
        /** A duplicate item was dropped by the chart because a lower-penalty path already existed. */
        DROPPED
    }

    /**
     * A single Earley item captured in a lattice snapshot.
     */
    public record LatticeItem(
            String rule, int dot, int origin, int penalty, State state, String features) {
    }

    /**
     * A specific reason a path did not succeed. Associated with a single lattice item and column.
     */
    public record PathFailure(LatticeItem item, Span span, String reason, FailureKind kind) {
    }

    /**
     * A single column in the parse lattice: the set of items present at a chart position together
     * with the next symbols each active item was still expecting and any per-path failures.
     */
    public record Column(
            int index,
            Span span,
            List<LatticeItem> items,
            List<String> expected,
            List<PathFailure> failures) {

        public Column {
            items = List.copyOf(items);
            expected = List.copyOf(expected);
            failures = List.copyOf(failures);
        }
    }

    /**
     * Complete lattice snapshot: all columns of the chart plus a pointer to the furthest column
     * with real progress (useful for error recovery and error-message placement).
     */
    public record ParseLattice(List<Column> columns, int furthestColumn, Span furthestSpan) {

        public ParseLattice {
            columns = List.copyOf(columns);
        }

        /**
         * Return all path failures flattened across all columns.
         */
        public List<PathFailure> allFailures() {
            final var all = new ArrayList<PathFailure>();
            for (final var column : columns) {
                all.addAll(column.failures());
            }
            return List.copyOf(all);
        }

        /**
         * Render the lattice as a human-readable section suitable for inclusion in a diagnostic
         * report. The format is:
         *
         * <pre>
         * === Parse Lattice ===
         * Furthest progress: column N (chars A..B)
         * Column 0 (chars 0..3):
         *   expected: NP, Det
         *   active: S → • NP VP (0)
         *   complete: Det → 'the' • (0)
         *   failure [DEAD_END] at chars 3..3: S → NP • VP (0) expecting VP
         * ...
         * </pre>
         */
        public String render() {
            final var sb = new StringBuilder();
            sb.append("=== Parse Lattice ===\n");
            if (furthestSpan != null) {
                sb.append(
                        String.format(
                                "Furthest progress: column %d (chars %d..%d)\n",
                                furthestColumn, furthestSpan.charStart(), furthestSpan.charEnd()));
            } else {
                sb.append(String.format("Furthest progress: column %d\n", furthestColumn));
            }
            for (final var column : columns) {
                sb.append(String.format("Column %d", column.index()));
                if (column.span() != null) {
                    sb.append(
                            String.format(
                                    " (chars %d..%d)", column.span().charStart(), column.span().charEnd()));
                }
                sb.append(":\n");
                if (!column.expected().isEmpty()) {
                    sb.append("  expected: ").append(String.join(", ", column.expected())).append("\n");
                }
                for (final var item : column.items()) {
                    sb.append(
                            String.format(
                                    "  %s: %s → dot=%d origin=%d pen=%d\n",
                                    item.state().name().toLowerCase(),
                                    item.rule(),
                                    item.dot(),
                                    item.origin(),
                                    item.penalty()));
                }
                for (final var failure : column.failures()) {
                    final var span = failure.span();
                    final var location =
                            span != null
                                    ? String.format("chars %d..%d", span.charStart(), span.charEnd())
                                    : String.format("column %d", column.index());
                    sb.append(
                            String.format(
                                    "  failure [%s] at %s: %s (%s)\n",
                                    failure.kind().name(),
                                    location,
                                    failure.item().rule(),
                                    failure.reason()));
                }
            }
            sb.append("\n");
            return sb.toString();
        }
    }

    /**
     * A constraint that failed during parsing.
     */
    public record ConstraintFailure(
            String ruleName,
            Constraint constraint,
            String reason,
            int position,
            boolean isHard,
            Span span) {
        public ConstraintFailure(
                final String ruleName,
                final Constraint constraint,
                final String reason,
                final int position,
                final boolean isHard) {
            this(ruleName, constraint, reason, position, isHard, null);
        }
    }

    /**
     * An error during tokenization.
     */
    public record TokenizationError(int position, String input, String expected, Span span) {
        public TokenizationError(final int position, final String input, final String expected) {
            this(position, input, expected, null);
        }
    }

    /**
     * A general parsing issue.
     */
    public record ParsingIssue(String message, int position, Span span) {
        public ParsingIssue(final String message, final int position) {
            this(message, position, null);
        }
    }

    /**
     * A regex pattern that failed to match.
     */
    public record RegexFailure(String pattern, String input, int position, Span span) {
        public RegexFailure(final String pattern, final String input, final int position) {
            this(pattern, input, position, null);
        }
    }

    /**
     * A quantifier that may be looping excessively.
     */
    public record QuantifierLoop(
            String ruleName, String element, int iterations, int position, Span span) {
        public QuantifierLoop(
                final String ruleName,
                final String element,
                final int iterations,
                final int position) {
            this(ruleName, element, iterations, position, null);
        }
    }

    /**
     * A feature unification that failed during parsing.
     */
    public record UnificationFailure(
            String symbol,
            Structure waiting,
            Structure completed,
            Structure expected,
            String reason,
            int position,
            Span span) {
        public UnificationFailure(
                final String symbol,
                final Structure waiting,
                final Structure completed,
                final Structure expected,
                final String reason,
                final int position) {
            this(symbol, waiting, completed, expected, reason, position, null);
        }
    }
}
