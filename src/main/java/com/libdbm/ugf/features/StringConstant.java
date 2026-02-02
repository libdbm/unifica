package com.libdbm.ugf.features;

import java.util.Objects;

/**
 * Represents an atomic feature value (string constant).
 *
 * @param value The atomic value
 */
public record StringConstant(String value) implements Constant {

    public StringConstant {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static StringConstant of(final String str) {
        return new StringConstant(str);
    }

    @Override
    public String display() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
