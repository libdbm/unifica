package com.libdbm.ugf.parser;

import com.libdbm.ugf.features.*;

import java.util.Objects;

/**
 * Represents a token with features and position information. Language-specific attributes (e.g.,
 * POS tag, lemma, name) are stored as features to maintain generality across different parsing
 * domains.
 */
public record Token(String text, Structure features, int start, int end) {
    public Token {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(features, "features must not be null");
    }

    /**
     * Creates a token with just text (no features).
     */
    public static Token of(final String text, final int start, final int end) {
        return new Token(text, new Structure(), start, end);
    }

    /**
     * Creates a token with text and features.
     */
    public static Token of(
            final String text, final Structure features, final int start, final int end) {
        return new Token(text, features, start, end);
    }

    /**
     * Builder for constructing tokens incrementally.
     */
    public static Builder builder(final String text, final int start, final int end) {
        return new Builder(text, start, end);
    }

    /**
     * Returns true if this token has features.
     */
    public boolean hasFeatures() {
        return features != null && !features.isEmpty();
    }

    /**
     * Gets a feature value by key.
     */
    public Value feature(final String key) {
        return features.get(key);
    }

    /**
     * Gets a string feature value by key. Returns the provided defaultValue when the key is not
     * present or the value is not a StringConstant. The defaultValue may be null, allowing callers to
     * decide policy; this method itself does not choose null.
     */
    public String string(final String key, final String defaultValue) {
        final var value = features.get(key);
        return value instanceof StringConstant(String v) ? v : defaultValue;
    }

    /**
     * Backward-compatible overload: returns the string feature for the given key, or null if absent.
     * Prefer using {@link #string(String, String)} to specify an explicit default.
     */
    public String string(final String key) {
        return string(key, null);
    }

    public boolean bool(final String key, boolean defaultValue) {
        final var value = features.get(key);
        return value instanceof BooleanConstant(boolean v) ? v : defaultValue;
    }

    public long integer(final String key, long defaultValue) {
        final var value = features.get(key);
        return value instanceof NumericConstant(Number v, boolean floating)
                ? v.longValue()
                : defaultValue;
    }

    public double floating(final String key, double defaultValue) {
        final var value = features.get(key);
        return value instanceof NumericConstant(Number v, boolean floating)
                ? v.doubleValue()
                : defaultValue;
    }

    @Override
    public String toString() {
        final var sb = new StringBuilder();
        sb.append(text);

        if (hasFeatures()) {
            sb.append("{");
            boolean first = true;
            for (final var key : features.keys()) {
                if (!first) sb.append(", ");
                final var value = features.get(key);
                sb.append(key).append("=");
                if (value instanceof StringConstant(String value1)) {
                    sb.append(value1);
                } else {
                    sb.append("...");
                }
                first = false;
            }
            sb.append("}");
        }

        sb.append("[").append(start).append(",").append(end).append("]");

        return sb.toString();
    }

    /**
     * Builder for Token construction.
     */
    public static final class Builder {
        private final String text;
        private final int start;
        private final int end;
        private Structure features;

        private Builder(final String text, final int start, final int end) {
            this.text = text;
            this.start = start;
            this.end = end;
            this.features = new Structure();
        }

        public Builder features(final Structure features) {
            this.features = features;
            return this;
        }

        public Builder feature(final String key, final Value value) {
            this.features.set(key, value);
            return this;
        }

        public Token build() {
            return new Token(text, features, start, end);
        }
    }
}
