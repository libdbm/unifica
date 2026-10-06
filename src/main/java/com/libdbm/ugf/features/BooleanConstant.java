package com.libdbm.ugf.features;

/**
 * Represents a boolean constant value.
 *
 * <p>Provides singleton instances for TRUE and FALSE to avoid unnecessary object creation.
 *
 * @param value The boolean value
 */
public record BooleanConstant(boolean value) implements Constant {

  /** Singleton for true. */
  public static final BooleanConstant TRUE = new BooleanConstant(true);

  /** Singleton for false. */
  public static final BooleanConstant FALSE = new BooleanConstant(false);

  /**
   * Returns a BooleanConstant for the given value.
   *
   * @param value the boolean value
   * @return TRUE or FALSE singleton
   */
  public static BooleanConstant of(final boolean value) {
    return value ? TRUE : FALSE;
  }

  /**
   * Parses a string to a BooleanConstant.
   *
   * @param str the string to parse ("true" or "false", case-insensitive)
   * @return the corresponding BooleanConstant
   * @throws IllegalArgumentException if the string is not a valid boolean
   */
  public static BooleanConstant parse(final String str) {
    if ("true".equalsIgnoreCase(str)) {
      return TRUE;
    }
    if ("false".equalsIgnoreCase(str)) {
      return FALSE;
    }
    throw new IllegalArgumentException("Invalid boolean: " + str);
  }

  /**
   * Checks if a string represents a boolean value.
   *
   * @param str the string to check
   * @return true if the string is "true" or "false" (case-insensitive)
   */
  public static boolean isBoolean(final String str) {
    return "true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str);
  }

  @Override
  public String display() {
    return String.valueOf(value);
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}
