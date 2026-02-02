package com.libdbm.ugf.features;

/**
 * Marker supertype for constant (atomic) feature values.
 *
 * <p>This sealed interface groups all constant value kinds (e.g., strings, numerics, booleans) so
 * callers can distinguish variables and structured values from atomic constants.
 */
public sealed interface Constant extends Value
        permits StringConstant, NumericConstant, BooleanConstant {

    @Override
    default boolean isVariable() {
        return false;
    }

    @Override
    default boolean isAtomic() {
        return true;
    }
}
