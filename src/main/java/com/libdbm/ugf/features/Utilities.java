package com.libdbm.ugf.features;

import java.util.Map;

/** Utility methods for converting between string-based and structured feature representations. */
public final class Utilities {
  private Utilities() {
    // Utility class
  }

  /**
   * Converts a Map&lt;String,String&gt; to a Structure.
   *
   * <p>Value interpretation:
   *
   * <ul>
   *   <li>Strings starting with '?' are treated as variables (e.g., "?x" → Variable("x"))
   *   <li>Boolean strings ("true", "false") are parsed as BooleanConstant
   *   <li>Numeric strings are parsed as NumericConstant
   *   <li>All other strings become StringConstant
   *   <li>Null values are skipped (not added to the structure)
   * </ul>
   */
  public static Structure fromMap(final Map<String, String> map) {
    final var fs = new Structure();
    for (final var entry : map.entrySet()) {
      final var value = entry.getValue();
      if (value != null) {
        fs.set(entry.getKey(), parseValue(value));
      }
    }
    return fs;
  }

  /**
   * Parses a string value into a Value.
   *
   * <p>Interpretation:
   *
   * <ul>
   *   <li>'?' prefix → Variable (e.g., "?x" → Variable("x"))
   *   <li>"true" or "false" → BooleanConstant
   *   <li>Numeric strings → NumericConstant
   *   <li>All other strings → StringConstant
   * </ul>
   *
   * @param value the string to parse (must not be null)
   * @return the parsed Value
   */
  private static Value parseValue(final String value) {
    if (value.isEmpty()) {
      return new StringConstant("");
    }
    if (value.startsWith("?")) {
      return new Variable(value.substring(1));
    }
    if (BooleanConstant.isBoolean(value)) {
      return BooleanConstant.parse(value);
    }
    if (NumericConstant.isFloating(value) || NumericConstant.isInteger(value)) {
      return NumericConstant.parse(value);
    }
    return new StringConstant(value);
  }

  /**
   * Converts a FeatureValue into a plain string for downstream components that expect
   * stringly-typed features (e.g., DRS translation). Variables are rendered with a leading '?'.
   * Returns null for null inputs.
   */
  public static String valueToString(final Value value) {
    if (value == null) return null;
    if (value instanceof StringConstant(String str)) {
      return str;
    }
    if (value instanceof NumericConstant nc) {
      return nc.number().toString();
    }
    if (value instanceof BooleanConstant(boolean bool)) {
      return String.valueOf(bool);
    }
    if (value instanceof Variable(String name)) {
      return "?" + name;
    }
    // Fallback for nested structures or unknown implementations
    return value.display();
  }
}
