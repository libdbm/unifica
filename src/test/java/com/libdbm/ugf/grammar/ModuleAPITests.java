package com.libdbm.ugf.grammar;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Module API")
class ModuleAPITests {

  @TempDir Path temp;

  @Nested
  @DisplayName("parseWithImports")
  class ParseWithImports {

    @Test
    @DisplayName("resolves module imports")
    void resolves() throws Exception {
      // Create module file
      final var module = temp.resolve("common.ug");
      Files.writeString(
          module,
          """
          module common;
          export *;
          start s;
          s --> 'hello';
          """);

      // Create main grammar
      final var main = temp.resolve("main.ug");
      Files.writeString(
          main,
          """
          import common.*;
          start doc;
          doc --> s 'world';
          """);

      final var grammar = UnificationGrammarParserFactory.parseWithImports(main);

      assertNotNull(grammar);
      assertFalse(grammar.rulesFor("s").isEmpty(), "Should have imported 's' rule");
      assertFalse(grammar.rulesFor("doc").isEmpty(), "Should have local 'doc' rule");
    }

    @Test
    @DisplayName("uses custom search paths")
    void custom() throws Exception {
      final var modules = temp.resolve("modules");
      Files.createDirectories(modules);

      // Create module in subdirectory
      final var module = modules.resolve("lib.ug");
      Files.writeString(
          module,
          """
          module lib;
          export *;
          helper --> 'x';
          """);

      // Create main grammar in temp root
      final var main = temp.resolve("main.ug");
      Files.writeString(
          main,
          """
          import lib.*;
          start doc;
          doc --> helper;
          """);

      // Without custom search path, should fail to find module
      assertThrows(Exception.class, () -> UnificationGrammarParserFactory.parseWithImports(main));

      // With custom search path, should succeed
      final var grammar = UnificationGrammarParserFactory.parseWithImports(main, modules);
      assertFalse(grammar.rulesFor("helper").isEmpty());
    }
  }

  @Nested
  @DisplayName("export *")
  class ExportAll {

    @Test
    @DisplayName("exports all symbols")
    void all() throws Exception {
      final var file = temp.resolve("test.ug");
      Files.writeString(
          file,
          """
          module test;
          export *;
          s --> 'a';
          t --> 'b';
          """);

      final var grammar = UnificationGrammarParserFactory.parse(file);

      assertTrue(grammar.module().isExported("s"));
      assertTrue(grammar.module().isExported("t"));
      assertTrue(grammar.module().isExported("unknown"), "export * means all");
    }

    @Test
    @DisplayName("explicit list restricts exports")
    void explicit() throws Exception {
      final var file = temp.resolve("test.ug");
      Files.writeString(
          file,
          """
          module test;
          export s;
          s --> 'a';
          t --> 'b';
          """);

      final var grammar = UnificationGrammarParserFactory.parse(file);

      assertTrue(grammar.module().isExported("s"));
      assertFalse(grammar.module().isExported("t"));
    }

    @Test
    @DisplayName("omitting export means export all")
    void omit() throws Exception {
      final var file = temp.resolve("test.ug");
      Files.writeString(
          file,
          """
          module test;
          s --> 'a';
          t --> 'b';
          """);

      final var grammar = UnificationGrammarParserFactory.parse(file);

      assertTrue(grammar.module().isExported("s"));
      assertTrue(grammar.module().isExported("t"));
    }
  }
}
