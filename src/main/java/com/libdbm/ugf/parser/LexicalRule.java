package com.libdbm.ugf.parser;

import com.libdbm.ugf.constraints.Constraint;
import com.libdbm.ugf.features.Structure;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A lexical rule with compiled pattern and metadata.
 *
 * <p>Represents a single tokenization rule extracted from the grammar. Each rule has:
 *
 * <ul>
 *   <li>A compiled regex pattern for matching
 *   <li>The original text/pattern string
 *   <li>An optional name (nonterminal name)
 *   <li>Feature structure for the token
 *   <li>Priority for conflict resolution
 *   <li>Whether it's a literal (exact match) vs regex
 *   <li>Constraints that must be satisfied for the rule to apply
 *   <li>Optional state transition to apply after matching
 * </ul>
 */
public record LexicalRule(
        Pattern pattern,
        String text,
        String name,
        Structure features,
        int priority,
        boolean literal,
        List<Constraint> constraints,
        String next) {

    /**
     * Create a copy with the given name.
     */
    public LexicalRule withCategory(final String cat) {
        return new LexicalRule(pattern, text, cat, features, priority, literal, constraints, next);
    }

    /**
     * Create a copy with the given features.
     */
    public LexicalRule withFeatures(final Structure feat) {
        return new LexicalRule(pattern, text, name, feat, priority, literal, constraints, next);
    }

    /**
     * Check if this rule has any constraints.
     */
    public boolean hasConstraints() {
        return constraints != null && !constraints.isEmpty();
    }
}
