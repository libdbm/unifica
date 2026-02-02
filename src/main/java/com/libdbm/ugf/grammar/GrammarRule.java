package com.libdbm.ugf.grammar;

import com.libdbm.ugf.constraints.Constraint;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Utilities;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a unification grammar production rule. Format: lhs --> rhs1, rhs2, ... where
 * constraints.
 */
public record GrammarRule(LHS lhs, List<RuleElement> rhs, List<Constraint> constraints) {
    public GrammarRule {
        Objects.requireNonNull(lhs, "lhs must not be null");
        Objects.requireNonNull(rhs, "rhs must not be null");
        Objects.requireNonNull(constraints, "constraints must not be null");
    }

    public GrammarRule(final String lhs, final List<RuleElement> rhs) {
        this(new LHS(lhs), rhs, List.of());
    }

    public GrammarRule(
            final String lhs, final List<RuleElement> rhs, final List<Constraint> constraints) {
        this(new LHS(lhs), rhs, constraints);
    }

    /**
     * Backward compatibility: create rule with constraints.
     */
    public static GrammarRule of(
            final String lhs, final List<RuleElement> rhs, final List<Constraint> constraints) {
        return new GrammarRule(new LHS(lhs), rhs, constraints);
    }

    /**
     * Left-hand side of a rule with optional features.
     */
    public record LHS(String symbol, Structure features) {
        public LHS {
            Objects.requireNonNull(symbol, "symbol must not be null");
            Objects.requireNonNull(features, "features must not be null");
        }

        public LHS(final String symbol) {
            this(symbol, new Structure());
        }

        /**
         * Legacy constructor for backward compatibility with string-based features. Converts
         * Map<String,String> to FeatureStructure.
         */
        @Deprecated
        public static LHS fromMap(final String symbol, final Map<String, String> features) {
            return new LHS(symbol, Utilities.fromMap(features));
        }

        public boolean hasFeatures() {
            return features.size() > 0;
        }
    }
}
