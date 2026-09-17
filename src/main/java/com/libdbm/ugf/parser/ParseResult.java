package com.libdbm.ugf.parser;

import java.util.Optional;

/**
 * Parse result containing the parse tree and accumulated penalty from soft constraint violations.
 *
 * <p>A penalty of 0 indicates a perfect parse with no soft constraint violations. Higher penalties
 * indicate more or more severe soft constraint violations.
 *
 * <p>{@code ambiguous} is true when two or more distinct derivations share the lowest penalty. The
 * penalties could not rank them, so {@code tree} is one of them chosen arbitrarily; a caller that
 * must not guess should refuse the parse.
 */
public record ParseResult(
        ParseTree tree, int penalty, ParseDiagnostics diagnostics, boolean ambiguous) {
    public ParseResult(final ParseTree tree, final int penalty, final ParseDiagnostics diagnostics) {
        this(tree, penalty, diagnostics, false);
    }

    public ParseResult(final ParseTree tree, final int penalty) {
        this(tree, penalty, null, false);
    }

    /**
     * Check if parsing succeeded (tree is present).
     */
    public boolean success() {
        return tree != null;
    }

    /**
     * Returns the parse tree as an Optional.
     */
    public Optional<ParseTree> toOptional() {
        return Optional.ofNullable(tree);
    }

    /**
     * Check if there were constraint failures during parsing.
     */
    public boolean hasConstraintFailures() {
        return diagnostics != null && !diagnostics.constraintFailures().isEmpty();
    }

    /**
     * Get a summary of the parse result including any diagnostics.
     */
    @Override
    public String toString() {
        final var sb = new StringBuilder();
        sb.append("ParseResult{success=").append(success());
        sb.append(", penalty=").append(penalty);
        if (diagnostics != null && diagnostics.hasFailures()) {
            sb.append(", diagnostics=\n").append(diagnostics.generateReport());
        }
        sb.append("}");
        return sb.toString();
    }
}
