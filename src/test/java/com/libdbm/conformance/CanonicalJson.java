package com.libdbm.conformance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal deterministic JSON writer and reader for conformance fixtures. Maps are written in their
 * iteration order, so callers pass a LinkedHashMap for fixed field order and a TreeMap for sorted
 * keys. Output is indented with two spaces so fixture diffs are reviewable.
 */
final class CanonicalJson {

  private CanonicalJson() {}

  static String write(final Object value) {
    final var builder = new StringBuilder();
    write(builder, value, 0);
    return builder.append('\n').toString();
  }

  private static void write(final StringBuilder builder, final Object value, final int depth) {
    switch (value) {
      case null -> builder.append("null");
      case String text -> string(builder, text);
      case Boolean flag -> builder.append(flag);
      case Integer number -> builder.append(number);
      case Long number -> builder.append(number);
      case Double number -> real(builder, number);
      case Map<?, ?> map -> object(builder, map, depth);
      case List<?> list -> array(builder, list, depth);
      default ->
          throw new IllegalArgumentException(
              "Unsupported JSON value: " + value.getClass().getSimpleName());
    }
  }

  private static void real(final StringBuilder builder, final double number) {
    if (Double.isNaN(number) || Double.isInfinite(number)) {
      string(builder, Double.toString(number));
    } else {
      builder.append(Double.toString(number));
    }
  }

  private static void object(final StringBuilder builder, final Map<?, ?> map, final int depth) {
    if (map.isEmpty()) {
      builder.append("{}");
      return;
    }
    builder.append("{\n");
    var first = true;
    for (final var entry : map.entrySet()) {
      if (!first) {
        builder.append(",\n");
      }
      indent(builder, depth + 1);
      string(builder, (String) entry.getKey());
      builder.append(": ");
      write(builder, entry.getValue(), depth + 1);
      first = false;
    }
    builder.append('\n');
    indent(builder, depth);
    builder.append('}');
  }

  private static void array(final StringBuilder builder, final List<?> list, final int depth) {
    if (list.isEmpty()) {
      builder.append("[]");
      return;
    }
    builder.append("[\n");
    var first = true;
    for (final var item : list) {
      if (!first) {
        builder.append(",\n");
      }
      indent(builder, depth + 1);
      write(builder, item, depth + 1);
      first = false;
    }
    builder.append('\n');
    indent(builder, depth);
    builder.append(']');
  }

  private static void string(final StringBuilder builder, final String text) {
    builder.append('"');
    for (var i = 0; i < text.length(); i++) {
      final var c = text.charAt(i);
      switch (c) {
        case '"' -> builder.append("\\\"");
        case '\\' -> builder.append("\\\\");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        default -> {
          if (c < 0x20) {
            builder.append(String.format("\\u%04x", (int) c));
          } else {
            builder.append(c);
          }
        }
      }
    }
    builder.append('"');
  }

  private static void indent(final StringBuilder builder, final int depth) {
    builder.append("  ".repeat(depth));
  }

  /**
   * Reads JSON into maps (insertion-ordered), lists, strings, booleans, {@code null}, {@link Long}
   * for integral numbers and {@link Double} otherwise: the same types {@link #write} accepts.
   */
  static Object read(final String text) {
    final var reader = new Reader(text);
    final var value = reader.value();
    reader.space();
    if (reader.position != text.length()) {
      throw reader.error("trailing content");
    }
    return value;
  }

  private static final class Reader {

    private final String text;
    private int position;

    private Reader(final String text) {
      this.text = text;
    }

    private Object value() {
      space();
      if (position >= text.length()) {
        throw error("unexpected end");
      }
      final var c = text.charAt(position);
      return switch (c) {
        case '{' -> object();
        case '[' -> array();
        case '"' -> string();
        case 't' -> literal("true", Boolean.TRUE);
        case 'f' -> literal("false", Boolean.FALSE);
        case 'n' -> literal("null", null);
        default -> number();
      };
    }

    private Map<String, Object> object() {
      final var map = new LinkedHashMap<String, Object>();
      position++;
      space();
      if (peek('}')) {
        position++;
        return map;
      }
      while (true) {
        space();
        final var key = string();
        space();
        expect(':');
        map.put(key, value());
        space();
        if (peek(',')) {
          position++;
          continue;
        }
        expect('}');
        return map;
      }
    }

    private List<Object> array() {
      final var list = new ArrayList<Object>();
      position++;
      space();
      if (peek(']')) {
        position++;
        return list;
      }
      while (true) {
        list.add(value());
        space();
        if (peek(',')) {
          position++;
          continue;
        }
        expect(']');
        return list;
      }
    }

    private String string() {
      expect('"');
      final var builder = new StringBuilder();
      while (position < text.length()) {
        final var c = text.charAt(position++);
        if (c == '"') {
          return builder.toString();
        }
        if (c != '\\') {
          builder.append(c);
          continue;
        }
        final var escape = text.charAt(position++);
        switch (escape) {
          case 'n' -> builder.append('\n');
          case 't' -> builder.append('\t');
          case 'r' -> builder.append('\r');
          case 'b' -> builder.append('\b');
          case 'f' -> builder.append('\f');
          case 'u' -> {
            builder.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
            position += 4;
          }
          default -> builder.append(escape);
        }
      }
      throw error("unterminated string");
    }

    private Object number() {
      final var start = position;
      while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
        position++;
      }
      final var token = text.substring(start, position);
      if (token.isEmpty()) {
        throw error("unexpected character");
      }
      return token.matches("-?[0-9]+")
          ? (Object) Long.parseLong(token)
          : (Object) Double.parseDouble(token);
    }

    private Object literal(final String word, final Object value) {
      if (!text.startsWith(word, position)) {
        throw error("expected " + word);
      }
      position += word.length();
      return value;
    }

    private void space() {
      while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
        position++;
      }
    }

    private boolean peek(final char c) {
      return position < text.length() && text.charAt(position) == c;
    }

    private void expect(final char c) {
      if (!peek(c)) {
        throw error("expected '" + c + "'");
      }
      position++;
    }

    private IllegalArgumentException error(final String message) {
      return new IllegalArgumentException("JSON " + message + " at offset " + position);
    }
  }
}
