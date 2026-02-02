package com.libdbm.ugf.grammar;

import java.util.Set;

/** Represents module metadata for a unification grammar. */
public record ModuleInfo(String name, Set<String> exports) {
  public ModuleInfo(final String name) {
    this(name, Set.of());
  }

  public static ModuleInfo anonymous() {
    return new ModuleInfo("");
  }

  public boolean isExported(final String symbol) {
    // null or empty exports means "export all"
    return exports == null || exports.isEmpty() || exports.contains(symbol);
  }
}
