package com.libdbm.ugf.features;

import java.util.List;

/**
 * Represents a feature path expression like W.cat or X.num.
 *
 * <p>Feature paths are used in constraints to access nested features:
 *
 * <pre>
 * noun --> {TOKEN}:W where equals(W.cat, "noun");
 * </pre>
 *
 * <p>The path W.cat means "access feature 'cat' of binding W".
 */
public record FeaturePath(List<String> parts) implements Value {

    public FeaturePath {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("Feature path must have at least one part");
        }
    }

    /**
     * The root variable name (first part).
     */
    public String root() {
        return parts.getFirst();
    }

    /**
     * The feature names to traverse (remaining parts).
     */
    public List<String> path() {
        return parts.size() > 1 ? parts.subList(1, parts.size()) : List.of();
    }

    /**
     * Check if this is a simple variable (no feature path).
     */
    public boolean isSimple() {
        return parts.size() == 1;
    }

    @Override
    public boolean isVariable() {
        return true;
    }

    @Override
    public boolean isAtomic() {
        return false;
    }

    @Override
    public String display() {
        return String.join(".", parts);
    }
}
