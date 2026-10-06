package com.libdbm.ugf.features;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents a numeric constant value. Integral values are stored as {@code long} and floating
 * values as {@code double}. Instances are immutable and considered atomic feature values.
 *
 * <p>Equality follows the mathematical value (S-F4): {@code 1}, {@code 1L} and {@code 1.0} are
 * equal and hash alike. Integral and floating values are compared exactly through {@link
 * BigDecimal}, never by narrowing the integral value to a double.
 */
public record NumericConstant(Number value, boolean floating) implements Constant {

  private static final Pattern INTEGER_PATTERN = Pattern.compile("^[+-]?\\d+$");
  private static final Pattern FLOATING_PATTERN =
      Pattern.compile("^[+-]?(?:(?:\\d+\\.\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?|\\d+(?:[eE][+-]?\\d+))$");

  public NumericConstant {
    Objects.requireNonNull(value, "value");
    value = floating ? (Number) value.doubleValue() : (Number) value.longValue();
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

  /** Creates an integer constant. */
  public static NumericConstant of(final int value) {
    return new NumericConstant(value, false);
  }

  /** Creates a long constant. */
  public static NumericConstant of(final long value) {
    return new NumericConstant(value, false);
  }

  /** Creates a floating-point (double) constant. */
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
  public boolean equals(final Object object) {
    if (!(object instanceof NumericConstant other)) {
      return false;
    }
    if (!floating && !other.floating) {
      return asLong() == other.asLong();
    }
    if (floating && other.floating) {
      final var a = asDouble();
      final var b = other.asDouble();
      return a == b || Double.compare(a, b) == 0;
    }
    final var real = floating ? asDouble() : other.asDouble();
    final var integral = floating ? other.asLong() : asLong();
    return Double.isFinite(real)
        && new BigDecimal(real).compareTo(BigDecimal.valueOf(integral)) == 0;
  }

  @Override
  public int hashCode() {
    if (!floating) {
      return Long.hashCode(asLong());
    }
    final var real = asDouble();
    // A floating value equal to a long hashes like that long, so equal values hash alike.
    if (real == Math.floor(real) && real >= -0x1p63 && real < 0x1p63) {
      return Long.hashCode((long) real);
    }
    return Double.hashCode(real);
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
