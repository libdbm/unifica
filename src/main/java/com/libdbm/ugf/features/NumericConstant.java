package com.libdbm.ugf.features;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents a numeric constant value. Distinguishes between integer, long, and floating-point
 * constants. Instances are immutable and considered atomic feature values.
 */
public record NumericConstant(Number value, boolean floating) implements Constant {

    private static final Pattern INTEGER_PATTERN = Pattern.compile("^[+-]?\\d+$");
    private static final Pattern FLOATING_PATTERN =
            Pattern.compile("^[+-]?(?:(?:\\d+\\.\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?|\\d+(?:[eE][+-]?\\d+))$");

    public NumericConstant(final Number value, final boolean floating) {
        this.value = Objects.requireNonNull(value, "value");
        this.floating = floating;
    }

    /**
     * Factory that parses a numeric literal. Heuristics: - If it contains a decimal point or an
     * exponent (e/E), it's FLOATING (parsed as double). - Otherwise parsed as long; if within int
     * range -> INTEGER, else LONG.
     */
    public static NumericConstant parse(final String literal) {
        Objects.requireNonNull(literal, "literal");
        if (isFloating(literal)) {
            return new NumericConstant(Double.parseDouble(literal.trim()), true);
        }
        if (isInteger(literal)) {
            return new NumericConstant(Long.parseLong(literal.trim()), false);
        }
        throw new IllegalArgumentException("Invalid numeric literal: " + literal);
    }

    /**
     * Creates an integer constant.
     */
    public static NumericConstant of(final int value) {
        return new NumericConstant(value, false);
    }

    /**
     * Creates a long constant.
     */
    public static NumericConstant of(final long value) {
        return new NumericConstant(value, false);
    }

    /**
     * Creates a floating-point (double) constant.
     */
    public static NumericConstant of(final double value) {
        return new NumericConstant(value, true);
    }

    public static boolean isFloating(final String s) {
        if (s == null || s.trim().isEmpty()) return false;
        return FLOATING_PATTERN.matcher(s.trim()).matches();
    }

    public static boolean isInteger(final String s) {
        if (s == null || s.trim().isEmpty()) return false;
        return INTEGER_PATTERN.matcher(s.trim()).matches();
    }

    public Number number() {
        return value;
    }

    public boolean isFloating() {
        return floating;
    }

    public int asInteger() {
        return value.intValue();
    }

    public long asLong() {
        return value.longValue();
    }

    public double asDouble() {
        return value.doubleValue();
    }

    @Override
    public String display() {
        return number().toString();
    }

    @Override
    public String toString() {
        return number().toString();
    }
}
