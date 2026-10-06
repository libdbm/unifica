package com.libdbm.ugf.features;

/**
 * A successful unification: the unified value with every bound variable substituted, and the
 * bindings that produced it (S-F1).
 *
 * @param value the unified value
 * @param bindings the bindings after unification
 * @param <T> the value type, {@link Structure} for structure unification
 */
public record Unification<T extends Value>(T value, Bindings bindings) {}
