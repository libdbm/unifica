package com.libdbm.ugf.features;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Implements unification algorithm for feature structures. Unification finds the most general
 * feature structure that subsumes two given structures.
 */
public class Unifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(Unifier.class);

    /**
     * Perform three-way unification for chart parser completion.
     *
     * <p>Only features specified in the rule's nonterminal reference ({@code expected}) are unified.
     * Other features stay attached to constituents but don't propagate.
     *
     * @param item      features from the waiting parse item
     * @param completed features from the completed constituent
     * @param expected  features expected by the grammar rule nonterminal
     * @return unified feature structure, or empty if unification fails
     */
    public static Optional<Structure> unify(
            final Structure item, final Structure completed, final Structure expected) {
        return unify(item, completed, expected, new HashMap<>());
    }

    /**
     * Perform three-way unification with detailed failure reason.
     *
     * @param item      features from the waiting parse item
     * @param completed features from the completed constituent
     * @param expected  features expected by the grammar rule nonterminal
     * @return UnifyResult with either unified structure or failure reason
     */
    public static UnifyResult unifyWithReason(
            final Structure item, final Structure completed, final Structure expected) {
        final var bindings = new HashMap<String, Value>();

        // First verify expected features are compatible with completed
        if (!expected.isEmpty()) {
            for (final var key : expected.keys()) {
                final var required = expected.get(key);
                final var actual = completed.get(key);

                if (actual != null) {
                    // Both have the feature - must unify
                    final var check = Unifier.unify(required, actual, bindings);
                    if (check.isEmpty()) {
                        return UnifyResult.failure(
                                String.format(
                                        "Feature '%s' incompatible: expected %s but got %s", key, required, actual));
                    }
                } else if (!required.isVariable()) {
                    // Expected has a concrete value but completed lacks the feature - fail
                    return UnifyResult.failure(
                            String.format(
                                    "Feature '%s' missing: expected %s but feature not present", key, required));
                }
                // If required is a variable and completed lacks the feature, that's OK (unbound)
            }

            // Verify shared features between item and completed are compatible
            for (final var key : expected.keys()) {
                if (item.has(key) && completed.has(key)) {
                    final var result = Unifier.unify(item.get(key), completed.get(key), bindings);
                    if (result.isEmpty()) {
                        return UnifyResult.failure(
                                String.format(
                                        "Feature '%s' conflict: item has %s but completed has %s",
                                        key, item.get(key), completed.get(key)));
                    }
                }
            }
        }

        // Build result with item features plus propagated expected features
        final var result = item.copy();
        for (final var key : expected.keys()) {
            if (completed.has(key)) {
                result.set(key, completed.get(key));
            }
        }

        // Apply accumulated bindings to resolve variables in the result
        return UnifyResult.success(substitute(result, bindings));
    }

    /**
     * Perform three-way unification with bindings output.
     *
     * <p>Expected features are enforced: if expected specifies a feature, completed must either:
     *
     * <ul>
     *   <li>Have a compatible value for that feature, OR
     *   <li>Not have that feature (in which case the expected value is used)
     * </ul>
     *
     * <p>If expected contains a variable for a feature and completed lacks it, the variable remains
     * unbound (this supports optional agreement patterns).
     *
     * @param item      features from the waiting parse item
     * @param completed features from the completed constituent
     * @param expected  features expected by the grammar rule nonterminal
     * @param bindings  map to populate with variable bindings
     * @return unified feature structure, or empty if unification fails
     */
    public static Optional<Structure> unify(
            final Structure item,
            final Structure completed,
            final Structure expected,
            final Map<String, Value> bindings) {

        // First verify expected features are compatible with completed
        if (!expected.isEmpty()) {
            for (final var key : expected.keys()) {
                final var required = expected.get(key);
                final var actual = completed.get(key);

                if (actual != null) {
                    // Both have the feature - must unify
                    final var check = Unifier.unify(required, actual, bindings);
                    if (check.isEmpty()) {
                        LOGGER.debug(
                                "Three-way unification failed: feature '{}' incompatible: {} vs {}",
                                key,
                                required,
                                actual);
                        return Optional.empty();
                    }
                } else if (!required.isVariable()) {
                    // Expected has a concrete value but completed lacks the feature - fail
                    LOGGER.debug(
                            "Three-way unification failed: expected feature '{}' = {} but completed lacks it",
                            key,
                            required);
                    return Optional.empty();
                }
                // If required is a variable and completed lacks the feature, that's OK (unbound)
            }

            // Verify shared features between item and completed are compatible
            for (final var key : expected.keys()) {
                if (item.has(key) && completed.has(key)) {
                    final var result = Unifier.unify(item.get(key), completed.get(key), bindings);
                    if (result.isEmpty()) {
                        return Optional.empty();
                    }
                }
            }
        }

        // Build result with item features plus propagated expected features
        final var result = item.copy();
        for (final var key : expected.keys()) {
            if (completed.has(key)) {
                result.set(key, completed.get(key));
            }
        }

        return Optional.of(result);
    }

    /**
     * Unifies two feature values.
     *
     * @param v1       First value (must not be null)
     * @param v2       Second value (must not be null)
     * @param bindings Variable bindings accumulated during unification
     * @return Unified value, or empty if unification fails
     */
    public static Optional<Value> unify(
            final Value v1, final Value v2, final Map<String, Value> bindings) {
        // Null input check - deref() preserves null, so check inputs early
        if (v1 == null || v2 == null) {
            LOGGER.debug("Unification failed: null input (v1={}, v2={})", v1, v2);
            return Optional.empty();
        }

        // Dereference variables - returns original value for unbound variables
        final var val1 = deref(v1, bindings);
        final var val2 = deref(v2, bindings);

        // Both are variables
        if (val1 instanceof Variable(String a) && val2 instanceof Variable(String b)) {
            if (a.equals(b)) {
                return Optional.of(val1);
            }
            // Bind var1 to var2
            bindings.put(a, val2);
            return Optional.of(val2);
        }

        // val1 is variable
        if (val1 instanceof Variable(String name)) {
            if (occurs((Variable) val1, val2, bindings)) {
                LOGGER.debug("Occurs check failed for {} in {}", val1, val2);
                return Optional.empty();
            }
            bindings.put(name, val2);
            return Optional.of(val2);
        }

        // val2 is variable
        if (val2 instanceof Variable(String name)) {
            if (occurs((Variable) val2, val1, bindings)) {
                LOGGER.debug("Occurs check failed for {} in {}", val2, val1);
                return Optional.empty();
            }
            bindings.put(name, val1);
            return Optional.of(val1);
        }

        // Both atomic - require compatible types and equal values
        if (val1.isAtomic() && val2.isAtomic()) {
            // Both numeric constants - compare by numeric value
            if (val1 instanceof NumericConstant n1 && val2 instanceof NumericConstant n2) {
                if (numericEquals(n1, n2)) {
                    return Optional.of(val1);
                }
                LOGGER.debug("Numeric values don't match: {} vs {}", val1, val2);
                return Optional.empty();
            }
            // Both boolean constants - compare by value
            if (val1 instanceof BooleanConstant(boolean value1) && val2 instanceof BooleanConstant(boolean value)) {
                if (value1 == value) {
                    return Optional.of(val1);
                }
                LOGGER.debug("Boolean values don't match: {} vs {}", val1, val2);
                return Optional.empty();
            }
            // Type must match exactly (StringConstant vs NumericConstant vs BooleanConstant)
            if (!val1.getClass().equals(val2.getClass())) {
                LOGGER.debug(
                        "Atomic type mismatch: {} vs {}",
                        val1.getClass().getSimpleName(),
                        val2.getClass().getSimpleName());
                return Optional.empty();
            }
            // Use equals for value comparison (StringConstant)
            if (val1.equals(val2)) {
                return Optional.of(val1);
            }
            LOGGER.debug("Atomic values don't match: {} vs {}", val1, val2);
            return Optional.empty();
        }

        // Both feature structures
        switch (val1) {
            case Structure a when val2 instanceof Structure b -> {
                return unifyFeatureStructures(a, b, bindings);
            }
            // Handle Binding values - unify their features
            // Bindings are used for labeled constituents (e.g., NP:subject)
            case Binding b1 when val2 instanceof Binding b2 -> {
                return unifyFeatureStructures(b1.features(), b2.features(), bindings);
            }
            case Binding b1 when val2 instanceof Structure s2 -> {
                return unifyFeatureStructures(b1.features(), s2, bindings);
            }
            case Structure s1 when val2 instanceof Binding b2 -> {
                return unifyFeatureStructures(s1, b2.features(), bindings);
            }
            default -> {
            }
        }

        // Type mismatch
        LOGGER.debug(
                "Type mismatch: {} vs {}",
                val1.getClass().getSimpleName(),
                val2.getClass().getSimpleName());
        return Optional.empty();
    }

    /**
     * Unifies two feature structures.
     */
    private static Optional<Value> unifyFeatureStructures(
            final Structure fs1, final Structure fs2, final Map<String, Value> bindings) {
        final Structure result = new Structure();

        // Collect all feature names
        final Set<String> allKeys = new HashSet<>();
        allKeys.addAll(fs1.keys());
        allKeys.addAll(fs2.keys());

        for (final var key : allKeys) {
            final Value v1 = fs1.get(key);
            final Value v2 = fs2.get(key);

            if (v1 != null && v2 != null) {
                // Both have this feature - must unify
                final Optional<Value> unified = unify(v1, v2, bindings);
                if (unified.isEmpty()) {
                    LOGGER.debug("Failed to unify feature '{}': {} vs {}", key, v1, v2);
                    return Optional.empty();
                }
                result.set(key, unified.get());
            } else if (v1 != null) {
                // Only fs1 has this feature
                result.set(key, deref(v1, bindings));
            } else {
                // Only fs2 has this feature
                result.set(key, deref(v2, bindings));
            }
        }

        return Optional.of(result);
    }

    /**
     * Dereferences a value by following variable bindings.
     */
    private static Value deref(final Value value, final Map<String, Value> bindings) {
        if (value instanceof Variable(String name)) {
            final Value bound = bindings.get(name);
            if (bound != null) {
                return deref(bound, bindings);
            }
        }
        return value;
    }

    /**
     * Occurs check: prevents binding a variable to a structure containing itself.
     */
    private static boolean occurs(
            final Variable var, final Value value, final Map<String, Value> bindings) {
        final var derefed = deref(value, bindings);

        if (derefed instanceof Variable(String name)) {
            return name.equals(var.name());
        }

        if (derefed instanceof Structure fs) {
            for (final var key : fs.keys()) {
                if (occurs(var, fs.get(key), bindings)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Compares two numeric constants for equality, handling different Number types. Two numerics are
     * equal if they have the same numeric value, regardless of whether they're stored as Integer,
     * Long, or Double.
     */
    private static boolean numericEquals(final NumericConstant n1, final NumericConstant n2) {
        // If both are floating or both are integral, compare appropriately
        if (n1.isFloating() || n2.isFloating()) {
            // Compare as doubles for floating point
            return Double.compare(n1.asDouble(), n2.asDouble()) == 0;
        }
        // Both are integral - compare as longs
        return n1.asLong() == n2.asLong();
    }

    /**
     * Unifies two feature structures, returning a new unified structure or empty. This is a
     * convenience method that creates a fresh bindings map.
     */
    public static Optional<Structure> unify(final Structure fs1, final Structure fs2) {
        final Map<String, Value> bindings = new HashMap<>();
        final Optional<Value> result = unify(fs1, fs2, bindings);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        if (result.get() instanceof Structure fs) {
            return Optional.of(substitute(fs, bindings));
        }

        LOGGER.warn("Unification result is not a feature structure: {}", result.get());
        return Optional.empty();
    }

    /**
     * Unifies two values, returning the unified value or empty. This is a convenience method that
     * creates a fresh bindings map and handles substitution.
     */
    public static Optional<Value> unify(final Value v1, final Value v2) {
        final Map<String, Value> bindings = new HashMap<>();
        final Optional<Value> result = unify(v1, v2, bindings);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        final var unified = result.get();
        if (unified instanceof Structure fs) {
            return Optional.of(substitute(fs, bindings));
        }

        // For atomic values and variables, apply substitution
        return Optional.of(substituteValue(unified, bindings));
    }

    /**
     * Substitutes all variable bindings in a feature structure.
     *
     * @param fs       the feature structure with variables
     * @param bindings the variable bindings to apply
     * @return a new structure with all bound variables replaced by their values
     */
    public static Structure substitute(final Structure fs, final Map<String, Value> bindings) {
        final Structure result = new Structure();

        for (final var key : fs.keys()) {
            final Value value = fs.get(key);
            final Value substituted = substituteValue(value, bindings);
            result.set(key, substituted);
        }

        return result;
    }

    /**
     * Substitutes variable bindings in a feature value.
     */
    private static Value substituteValue(final Value value, final Map<String, Value> bindings) {
        final Value derefed = deref(value, bindings);

        if (derefed instanceof Structure fs) {
            return substitute(fs, bindings);
        }

        return derefed;
    }

    /**
     * Result of a unification attempt that includes failure reason.
     */
    public record UnifyResult(Optional<Structure> structure, String reason) {
        public static UnifyResult success(final Structure structure) {
            return new UnifyResult(Optional.of(structure), null);
        }

        public static UnifyResult failure(final String reason) {
            return new UnifyResult(Optional.empty(), reason);
        }

        public boolean succeeded() {
            return structure.isPresent();
        }
    }
}
