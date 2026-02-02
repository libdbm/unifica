package com.libdbm.ugf.features;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a feature structure - a map from feature names to feature values. Feature structures
 * can be nested, and values can be atomic, variables, or other feature structures.
 */
public final class Structure implements Value {
    private final Map<String, Value> features;

    /**
     * Creates an empty feature structure.
     */
    public Structure() {
        this.features = new HashMap<>();
    }

    /**
     * Creates a feature structure from a map.
     */
    public Structure(final Map<String, Value> features) {
        this.features = new HashMap<>(features);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a copy of this feature structure.
     */
    public Structure copy() {
        final var copied = new HashMap<String, Value>();
        for (final var entry : features.entrySet()) {
            final var value = entry.getValue();
            if (value instanceof Structure fs) {
                copied.put(entry.getKey(), fs.copy());
            } else {
                copied.put(entry.getKey(), value);
            }
        }
        return new Structure(copied);
    }

    /**
     * Gets the value for a feature.
     */
    public Value get(final String feature) {
        return features.get(feature);
    }

    /**
     * Sets a feature value.
     */
    public void set(final String feature, final Value value) {
        features.put(feature, value);
    }

    /**
     * Checks if a feature is present.
     */
    public boolean has(final String feature) {
        return features.containsKey(feature);
    }

    /**
     * Returns all feature names as an unmodifiable set.
     */
    public Set<String> keys() {
        return Set.copyOf(features.keySet());
    }

    /**
     * Returns the number of features.
     */
    public int size() {
        return features.size();
    }

    /**
     * Returns true if this feature structure is empty.
     */
    public boolean isEmpty() {
        return features.isEmpty();
    }

    @Override
    public boolean isVariable() {
        return false;
    }

    @Override
    public boolean isAtomic() {
        return false;
    }

    @Override
    public String display() {
        if (features.isEmpty()) {
            return "{}";
        }

        final var sb = new StringBuilder("{");
        var first = true;
        for (final var entry : features.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append(": ").append(entry.getValue().display());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Structure other)) return false;
        return Objects.equals(features, other.features);
    }

    @Override
    public int hashCode() {
        return Objects.hash(features);
    }

    @Override
    public String toString() {
        return display();
    }

    /**
     * Builder for creating feature structures fluently.
     */
    public static class Builder {
        private final Map<String, Value> features = new HashMap<>();

        public Builder with(final String feature, final String value) {
            features.put(feature, new StringConstant(value));
            return this;
        }

        public Builder with(final String feature, final long value) {
            features.put(feature, NumericConstant.of(value));
            return this;
        }

        public Builder with(final String feature, final Variable variable) {
            features.put(feature, variable);
            return this;
        }

        public Builder with(final String feature, final Structure nested) {
            features.put(feature, nested);
            return this;
        }

        public Builder with(final String feature, final Value value) {
            features.put(feature, value);
            return this;
        }

        public Structure build() {
            return new Structure(features);
        }
    }
}
