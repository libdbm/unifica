package com.libdbm.conformance;

import com.libdbm.ugf.features.Binding;
import com.libdbm.ugf.features.BooleanConstant;
import com.libdbm.ugf.features.FeaturePath;
import com.libdbm.ugf.features.NumericConstant;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Value;
import com.libdbm.ugf.features.Variable;
import com.libdbm.ugf.parser.ParseTree;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonical encodings shared by the fixture writer and the conformance runner: parse trees, feature
 * structures and values as JSON-ready maps, plus the {@code inputs.txt} format.
 */
final class Fixtures {

  private Fixtures() {}

  static Map<String, Object> tree(final ParseTree tree) {
    final var entry = new LinkedHashMap<String, Object>();
    switch (tree) {
      case ParseTree.Node node -> {
        entry.put("symbol", node.symbol());
        entry.put("label", node.label());
        entry.put("features", structure(node.features()));
        final var children = new ArrayList<Object>();
        for (final var child : node.children()) {
          children.add(tree(child));
        }
        entry.put("children", children);
      }
      case ParseTree.Leaf leaf -> {
        entry.put("text", leaf.text());
        entry.put("start", leaf.start());
        entry.put("end", leaf.end());
        entry.put("features", structure(leaf.features()));
      }
    }
    return entry;
  }

  static Map<String, Object> structure(final Structure structure) {
    final var map = new TreeMap<String, Object>();
    for (final var key : structure.keys()) {
      map.put(key, value(structure.get(key)));
    }
    return map;
  }

  static Map<String, Object> value(final Value value) {
    final var entry = new LinkedHashMap<String, Object>();
    switch (value) {
      case StringConstant constant -> entry.put("s", constant.value());
      case NumericConstant constant when constant.floating() -> entry.put("f", constant.asDouble());
      case NumericConstant constant -> entry.put("i", constant.asLong());
      case BooleanConstant constant -> entry.put("b", constant.value());
      case Variable variable -> entry.put("v", variable.name());
      case Structure structure -> entry.put("m", structure(structure));
      case Binding binding -> {
        final var inner = new LinkedHashMap<String, Object>();
        inner.put("text", binding.text());
        inner.put("features", structure(binding.features()));
        entry.put("binding", inner);
      }
      case FeaturePath path -> entry.put("path", List.copyOf(path.parts()));
    }
    return entry;
  }

  /**
   * Reads {@code inputs.txt}: one input per line, with {@code \n}, {@code \r}, {@code \t} and
   * {@code \\} escapes.
   */
  static List<String> inputs(final Path path) throws IOException {
    final var content = Files.readString(path, StandardCharsets.UTF_8);
    final var lines = new ArrayList<>(List.of(content.split("\n", -1)));
    if (!lines.isEmpty() && lines.getLast().isEmpty()) {
      lines.removeLast();
    }
    return lines.stream().map(Fixtures::unescape).toList();
  }

  private static String unescape(final String line) {
    final var builder = new StringBuilder();
    for (var i = 0; i < line.length(); i++) {
      final var c = line.charAt(i);
      if (c == '\\' && i + 1 < line.length()) {
        final var next = line.charAt(++i);
        switch (next) {
          case 'n' -> builder.append('\n');
          case 't' -> builder.append('\t');
          case 'r' -> builder.append('\r');
          case '\\' -> builder.append('\\');
          default -> builder.append(c).append(next);
        }
      } else {
        builder.append(c);
      }
    }
    return builder.toString();
  }
}
