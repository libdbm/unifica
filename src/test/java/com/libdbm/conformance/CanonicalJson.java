package com.libdbm.conformance;

import java.util.List;
import java.util.Map;

/**
 * Minimal deterministic JSON writer for conformance fixtures. Maps are written in their iteration
 * order, so callers pass a LinkedHashMap for fixed field order and a TreeMap for sorted keys.
 * Output is indented with two spaces so fixture diffs are reviewable.
 */
final class CanonicalJson {

    private CanonicalJson() {
    }

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
            default -> throw new IllegalArgumentException(
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
}
