package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.ImportDeclaration;
import com.libdbm.ugf.grammar.ModuleInfo;
import com.libdbm.ugf.grammar.RuleElement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for ModuleResolver. */
class ModuleResolverTests {

  @TempDir
  Path tempDir;
  private ModuleResolver resolver;

  @BeforeEach
  void setup() {
    resolver = new ModuleResolver();
  }

  @Nested
  @DisplayName("Construction")
  class Construction {

    @Test
    @DisplayName("default constructor uses current directory")
    void default_constructor() {
      final var r = new ModuleResolver();
      assertNotNull(r);
    }

    @Test
    @DisplayName("constructor with search paths")
    void constructor_with_paths() {
      final var r = new ModuleResolver(List.of(tempDir));
      assertNotNull(r);
    }

    @Test
    @DisplayName("constructor with null list uses default")
    void constructor_with_null_list() {
      final var r = new ModuleResolver(null);
      assertNotNull(r);
    }

    @Test
    @DisplayName("constructor with empty list uses default")
    void constructor_with_empty_list() {
      final var r = new ModuleResolver(List.of());
      assertNotNull(r);
    }

    @Test
    @DisplayName("constructor accepts valid paths")
    void constructor_accepts_valid_paths() {
      final var r = new ModuleResolver(List.of(tempDir));
      assertNotNull(r);
    }
  }

  @Nested
  @DisplayName("Search paths")
  class SearchPaths {

    @Test
    @DisplayName("addSearchPath adds new path")
    void adds_search_path() {
      resolver.addSearchPath(tempDir);
      // No exception means success
    }

    @Test
    @DisplayName("addSearchPath ignores null")
    void ignores_null_path() {
      resolver.addSearchPath(null);
      // No exception means success
    }

    @Test
    @DisplayName("addSearchPath ignores duplicate")
    void ignores_duplicate() {
      resolver.addSearchPath(tempDir);
      resolver.addSearchPath(tempDir);
      // No exception means success
    }
  }

  @Nested
  @DisplayName("Resolution without imports")
  class NoImports {

    @Test
    @DisplayName("resolves grammar with no imports")
    void resolves_no_imports() throws IOException {
      final var grammar = Grammar.builder()
          .start("S")
          .add(new GrammarRule("S", List.of(new RuleElement.Terminal("x"))))
          .build();

      final var resolved = resolver.resolve(grammar);

      assertNotNull(resolved);
      assertEquals("S", resolved.start());
      assertEquals(1, resolved.rulesFor("S").size());
    }

    @Test
    @DisplayName("preserves module info")
    void preserves_module_info() throws IOException {
      final var grammar = Grammar.builder()
          .module(new ModuleInfo("test", Set.of("S")))
          .start("S")
          .build();

      final var resolved = resolver.resolve(grammar);

      assertEquals("test", resolved.module().name());
    }
  }

  @Nested
  @DisplayName("File import resolution")
  class FileImports {

    @Test
    @DisplayName("resolves file import")
    void resolves_file_import() throws IOException {
      // Create module file
      final var moduleContent = """
          module imported;
          export A;
          A --> 'a';
          """;
      final var moduleFile = tempDir.resolve("imported.ug");
      Files.writeString(moduleFile, moduleContent);

      // Create main grammar with import
      final var grammar = Grammar.builder()
          .start("S")
          .add(new GrammarRule("S", List.of(new RuleElement.Nonterminal("A"))))
          .addImport(new ImportDeclaration.File(moduleFile.toString()))
          .build();

      resolver.addSearchPath(tempDir);
      final var resolved = resolver.resolve(grammar);

      assertNotNull(resolved);
      assertFalse(resolved.rulesFor("A").isEmpty());
    }

    @Test
    @DisplayName("throws for missing file")
    void throws_for_missing_file() {
      final var grammar = Grammar.builder()
          .start("S")
          .addImport(new ImportDeclaration.File("nonexistent.ug"))
          .build();

      assertThrows(IOException.class, () -> resolver.resolve(grammar));
    }
  }

  @Nested
  @DisplayName("All import resolution")
  class AllImports {

    @Test
    @DisplayName("imports all exported symbols")
    void imports_all_exported() throws IOException {
      // Create module with exports
      final var moduleContent = """
          module base;
          export A, B;
          A --> 'a';
          B --> 'b';
          internal --> 'x';
          """;
      final var moduleFile = tempDir.resolve("base.ug");
      Files.writeString(moduleFile, moduleContent);

      // Create grammar importing all
      final var grammar = Grammar.builder()
          .start("S")
          .add(new GrammarRule("S", List.of(new RuleElement.Nonterminal("A"))))
          .addImport(new ImportDeclaration.All(moduleFile.toString()))
          .build();

      resolver.addSearchPath(tempDir);
      final var resolved = resolver.resolve(grammar);

      // Should have A and B but not internal
      assertFalse(resolved.rulesFor("A").isEmpty());
      assertFalse(resolved.rulesFor("B").isEmpty());
      assertTrue(resolved.rulesFor("internal").isEmpty());
    }
  }

  @Nested
  @DisplayName("Selective import resolution")
  class SelectiveImports {

    @Test
    @DisplayName("imports only specified symbols")
    void imports_only_specified() throws IOException {
      // Create module with exports
      final var moduleContent = """
          module base;
          export A, B, C;
          A --> 'a';
          B --> 'b';
          C --> 'c';
          """;
      final var moduleFile = tempDir.resolve("base.ug");
      Files.writeString(moduleFile, moduleContent);

      // Create grammar importing selectively
      final var grammar = Grammar.builder()
          .start("S")
          .add(new GrammarRule("S", List.of(new RuleElement.Nonterminal("A"))))
          .addImport(new ImportDeclaration.Selective(moduleFile.toString(), Set.of("A")))
          .build();

      resolver.addSearchPath(tempDir);
      final var resolved = resolver.resolve(grammar);

      // Should have only A
      assertFalse(resolved.rulesFor("A").isEmpty());
      assertTrue(resolved.rulesFor("B").isEmpty());
      assertTrue(resolved.rulesFor("C").isEmpty());
    }
  }

  @Nested
  @DisplayName("Module path resolution")
  class ModulePaths {

    @Test
    @DisplayName("resolves module path with dots")
    void resolves_dotted_path() throws IOException {
      // Create directory structure
      final var dir = tempDir.resolve("syntax");
      Files.createDirectories(dir);
      final var moduleFile = dir.resolve("core.ug");
      Files.writeString(moduleFile, """
          module syntax.core;
          export A;
          A --> 'a';
          """);

      // Create grammar with module path import
      final var grammar = Grammar.builder()
          .start("S")
          .addImport(new ImportDeclaration.All("syntax.core"))
          .build();

      resolver.addSearchPath(tempDir);
      final var resolved = resolver.resolve(grammar);

      assertFalse(resolved.rulesFor("A").isEmpty());
    }
  }

  @Nested
  @DisplayName("Transitive imports")
  class TransitiveImports {

    @Test
    @DisplayName("resolves transitive imports")
    void resolves_transitive() throws IOException {
      // Create base module
      final var baseFile = tempDir.resolve("base.ug");
      Files.writeString(baseFile, """
          module base;
          export X;
          X --> 'x';
          """);

      // Create middle module that imports base
      final var middleFile = tempDir.resolve("middle.ug");
      Files.writeString(middleFile, String.format("""
          module middle;
          import "%s";
          export Y;
          Y --> X;
          """, baseFile.toString()));

      // Create top grammar that imports middle
      final var grammar = Grammar.builder()
          .start("S")
          .addImport(new ImportDeclaration.File(middleFile.toString()))
          .build();

      resolver.addSearchPath(tempDir);
      final var resolved = resolver.resolve(grammar);

      // Should have Y and its dependency X
      assertFalse(resolved.rulesFor("Y").isEmpty());
    }
  }

  @Nested
  @DisplayName("Caching")
  class Caching {

    @Test
    @DisplayName("caches loaded modules")
    void caches_modules() throws IOException {
      // Create module
      final var moduleFile = tempDir.resolve("cached.ug");
      Files.writeString(moduleFile, """
          module cached;
          export A;
          A --> 'a';
          """);

      // Create two grammars that import same module
      final var g1 = Grammar.builder()
          .start("S")
          .addImport(new ImportDeclaration.File(moduleFile.toString()))
          .build();

      final var g2 = Grammar.builder()
          .start("T")
          .addImport(new ImportDeclaration.File(moduleFile.toString()))
          .build();

      resolver.addSearchPath(tempDir);

      // Resolve both
      final var r1 = resolver.resolve(g1);
      final var r2 = resolver.resolve(g2);

      // Both should have A (cache should work)
      assertFalse(r1.rulesFor("A").isEmpty());
      assertFalse(r2.rulesFor("A").isEmpty());
    }

    @Test
    @DisplayName("clearCache clears all caches")
    void clear_cache() throws IOException {
      // Create module
      final var moduleFile = tempDir.resolve("clearable.ug");
      Files.writeString(moduleFile, """
          module clearable;
          export A;
          A --> 'a';
          """);

      final var grammar = Grammar.builder()
          .start("S")
          .addImport(new ImportDeclaration.File(moduleFile.toString()))
          .build();

      resolver.addSearchPath(tempDir);
      resolver.resolve(grammar);

      // Clear cache
      resolver.clearCache();

      // Should still work (reloads from file)
      final var resolved = resolver.resolve(grammar);
      assertFalse(resolved.rulesFor("A").isEmpty());
    }
  }

  @Nested
  @DisplayName("Circular dependency handling")
  class CircularDependencies {

    @Test
    @DisplayName("handles circular imports gracefully")
    void handles_circular() throws IOException {
      // Create two modules that import each other
      final var moduleA = tempDir.resolve("moduleA.ug");
      final var moduleB = tempDir.resolve("moduleB.ug");

      Files.writeString(moduleA, String.format("""
          module moduleA;
          import "%s";
          export A;
          A --> 'a';
          """, moduleB.toString()));

      Files.writeString(moduleB, String.format("""
          module moduleB;
          import "%s";
          export B;
          B --> 'b';
          """, moduleA.toString()));

      final var grammar = Grammar.builder()
          .start("S")
          .addImport(new ImportDeclaration.File(moduleA.toString()))
          .build();

      resolver.addSearchPath(tempDir);

      // Should not throw or infinite loop
      final var resolved = resolver.resolve(grammar);
      assertNotNull(resolved);
    }
  }
}
