package com.libdbm.ugf.grammar;

import java.util.Set;

/** Represents an import declaration in a unification grammar. */
public sealed interface ImportDeclaration {

  String path();

  /** Import all symbols from a module. Example: import syntax.core.*; */
  record All(String path, String alias) implements ImportDeclaration {
    public All(final String path) {
      this(path, null);
    }
  }

  /** Import specific symbols from a module. Example: import syntax.core{s, np, vp}; */
  record Selective(String path, Set<String> symbols, String alias) implements ImportDeclaration {
    public Selective(final String path, final Set<String> symbols) {
      this(path, symbols, null);
    }
  }

  /** Import a file directly (legacy support). Example: import "base.ug"; */
  record File(String path) implements ImportDeclaration {}
}
