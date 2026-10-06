package com.libdbm.ugf.grammar.loader;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.grammar.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for ModuleResolver. */
class ModuleResolverTests {

  @TempDir Path tempDir;
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
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(new GrammarRule("S", List.of(new RuleElement.Terminal("x"))))
              .build();

      final var resolved = resolver.resolve(grammar).orElseThrow();

      assertNotNull(resolved);
      assertEquals("S", resolved.start());
      assertEquals(1, resolved.rulesFor("S").size());
    }

    @Test
    @DisplayName("preserves module info")
    void preserves_module_info() throws IOException {
      final var grammar =
          Grammar.builder().module(new ModuleInfo("test", Set.of("S"))).start("S").build();

      final var resolved = resolver.resolve(grammar).orElseThrow();

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
      final var moduleContent =
          """
                            module imported;
                            export A;
                            A --> 'a';
                            """;
      final var moduleFile = tempDir.resolve("imported.ug");
      Files.writeString(moduleFile, moduleContent);

      // Create main grammar with import
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(new GrammarRule("S", List.of(new RuleElement.Nonterminal("A"))))
              .addImport(new ImportDeclaration.File(moduleFile.toString()))
              .build();

      resolver.addSearchPath(tempDir);
      final var resolved = resolver.resolve(grammar).orElseThrow();

      assertNotNull(resolved);
      assertFalse(resolved.rulesFor("A").isEmpty());
    }

    @Test
    @DisplayName("throws for missing file")
    void throws_for_missing_file() {
      final var grammar =
          Grammar.builder()
              .start("S")
              .addImport(new ImportDeclaration.File("nonexistent.ug"))
              .build();

      final var result = resolver.resolve(grammar);
      assertEquals(
          ModuleResolver.MISSING,
          ((ErrorDetails) assertInstanceOf(Result.Failure.class, result).error()).code());
    }
  }

  @Nested
  @DisplayName("All import resolution")
  class AllImports {

    @Test
    @DisplayName("imports all exported symbols")
    void imports_all_exported() throws IOException {
      // Create module with exports
      final var moduleContent =
          """
                            module base;
                            export A, B;
                            A --> 'a';
                            B --> 'b';
                            internal --> 'x';
                            """;
      final var moduleFile = tempDir.resolve("base.ug");
      Files.writeString(moduleFile, moduleContent);

      // Create grammar importing all
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(new GrammarRule("S", List.of(new RuleElement.Nonterminal("A"))))
              .addImport(new ImportDeclaration.All(moduleFile.toString()))
              .build();

      resolver.addSearchPath(tempDir);
      final var resolved = resolver.resolve(grammar).orElseThrow();

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
      final var moduleContent =
          """
                            module base;
                            export A, B, C;
                            A --> 'a';
                            B --> 'b';
                            C --> 'c';
                            """;
      final var moduleFile = tempDir.resolve("base.ug");
      Files.writeString(moduleFile, moduleContent);

      // Create grammar importing selectively
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(new GrammarRule("S", List.of(new RuleElement.Nonterminal("A"))))
              .addImport(new ImportDeclaration.Selective(moduleFile.toString(), Set.of("A")))
              .build();

      resolver.addSearchPath(tempDir);
      final var resolved = resolver.resolve(grammar).orElseThrow();

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
      Files.writeString(
          moduleFile,
          """
                            module syntax.core;
                            export A;
                            A --> 'a';
                            """);

      // Create grammar with module path import
      final var grammar =
          Grammar.builder().start("S").addImport(new ImportDeclaration.All("syntax.core")).build();

      resolver.addSearchPath(tempDir);
      final var resolved = resolver.resolve(grammar).orElseThrow();

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
      Files.writeString(
          baseFile,
          """
                            module base;
                            export X;
                            X --> 'x';
                            """);

      // Create middle module that imports base
      final var middleFile = tempDir.resolve("middle.ug");
      Files.writeString(
          middleFile,
          String.format(
              """
                                    module middle;
                                    import "%s";
                                    export Y;
                                    Y --> X;
                                    """,
              baseFile));

      // Create top grammar that imports middle
      final var grammar =
          Grammar.builder()
              .start("S")
              .addImport(new ImportDeclaration.File(middleFile.toString()))
              .build();

      resolver.addSearchPath(tempDir);
      final var resolved = resolver.resolve(grammar).orElseThrow();

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
      Files.writeString(
          moduleFile,
          """
                            module cached;
                            export A;
                            A --> 'a';
                            """);

      // Create two grammars that import same module
      final var g1 =
          Grammar.builder()
              .start("S")
              .addImport(new ImportDeclaration.File(moduleFile.toString()))
              .build();

      final var g2 =
          Grammar.builder()
              .start("T")
              .addImport(new ImportDeclaration.File(moduleFile.toString()))
              .build();

      resolver.addSearchPath(tempDir);

      // Resolve both
      final var r1 = resolver.resolve(g1).orElseThrow();
      final var r2 = resolver.resolve(g2).orElseThrow();

      // Both should have A (cache should work)
      assertFalse(r1.rulesFor("A").isEmpty());
      assertFalse(r2.rulesFor("A").isEmpty());
    }

    @Test
    @DisplayName("clearCache clears all caches")
    void clear_cache() throws IOException {
      // Create module
      final var moduleFile = tempDir.resolve("clearable.ug");
      Files.writeString(
          moduleFile,
          """
                            module clearable;
                            export A;
                            A --> 'a';
                            """);

      final var grammar =
          Grammar.builder()
              .start("S")
              .addImport(new ImportDeclaration.File(moduleFile.toString()))
              .build();

      resolver.addSearchPath(tempDir);
      resolver.resolve(grammar).orElseThrow();

      // Clear cache
      resolver.clearCache();

      // Should still work (reloads from file)
      final var resolved = resolver.resolve(grammar).orElseThrow();
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

      Files.writeString(
          moduleA,
          String.format(
              """
                                    module moduleA;
                                    import "%s";
                                    export A;
                                    A --> 'a';
                                    """,
              moduleB));

      Files.writeString(
          moduleB,
          String.format(
              """
                                    module moduleB;
                                    import "%s";
                                    export B;
                                    B --> 'b';
                                    """,
              moduleA));

      final var grammar =
          Grammar.builder()
              .start("S")
              .addImport(new ImportDeclaration.File(moduleA.toString()))
              .build();

      resolver.addSearchPath(tempDir);

      // Should not throw or infinite loop
      final var resolved = resolver.resolve(grammar).orElseThrow();
      assertNotNull(resolved);
    }
  }

  @Nested
  @DisplayName("Anchoring")
  class Anchoring {

    @Test
    void testNestedRelativeImportAnchored() throws IOException {
      final var root = Files.createDirectories(tempDir.resolve("a"));
      final var lib = Files.createDirectories(root.resolve("lib"));
      Files.writeString(root.resolve("main.ug"), "import \"lib/x.ug\";\nstart S;\nS --> X;\n");
      Files.writeString(lib.resolve("x.ug"), "import \"y.ug\";\nX --> Y;\n");
      Files.writeString(lib.resolve("y.ug"), "Y --> 'y';\n");

      final var grammar =
          UnificationGrammarParserFactory.parseWithImports(root.resolve("main.ug")).orElseThrow();

      assertFalse(grammar.rulesFor("Y").isEmpty());
    }

    @Test
    void testBareFilename() throws IOException {
      final var file = Path.of("module-resolver-" + System.nanoTime() + ".ug");
      file.toFile().deleteOnExit();
      try {
        Files.writeString(file, "start S;\nS --> 'x';\n");

        final var grammar = UnificationGrammarParserFactory.parseWithImports(file).orElseThrow();

        assertFalse(grammar.rulesFor("S").isEmpty());
      } finally {
        Files.deleteIfExists(file);
      }
    }

    @Test
    void testImportedDirectoryPrecedesSearchPath() throws IOException {
      final var root = Files.createDirectories(tempDir.resolve("b"));
      final var lib = Files.createDirectories(root.resolve("lib"));
      Files.writeString(root.resolve("main.ug"), "import \"lib/x.ug\";\nstart S;\nS --> X;\n");
      Files.writeString(lib.resolve("x.ug"), "import \"y.ug\";\nX --> Y;\n");
      Files.writeString(lib.resolve("y.ug"), "Y --> 'near';\n");
      Files.writeString(root.resolve("y.ug"), "Y --> 'far';\n");

      final var grammar =
          UnificationGrammarParserFactory.parseWithImports(root.resolve("main.ug")).orElseThrow();

      final var terminal = (RuleElement.Terminal) grammar.rulesFor("Y").getFirst().rhs().getFirst();
      assertEquals("near", terminal.text());
      assertEquals(1, grammar.rulesFor("Y").size());
    }
  }

  @Nested
  @DisplayName("Cache invalidation")
  class Invalidation {

    private String text(final Grammar grammar, final String symbol) {
      return ((RuleElement.Terminal) grammar.rulesFor(symbol).getFirst().rhs().getFirst()).text();
    }

    private Grammar root(final String path) {
      return Grammar.builder()
          .start("S")
          .add(new GrammarRule("S", List.of(new RuleElement.Nonterminal("A"))))
          .addImport(new ImportDeclaration.File(path))
          .build();
    }

    @Test
    void testCacheInvalidation() throws IOException {
      final var module = tempDir.resolve("m.ug");
      Files.writeString(module, "A --> 'a';\n");
      final var cached = new ModuleResolver(List.of(tempDir));
      assertEquals("a", text(cached.resolve(root("m.ug")).orElseThrow(), "A"));

      Files.writeString(module, "A --> 'changed';\n");

      assertEquals("changed", text(cached.resolve(root("m.ug")).orElseThrow(), "A"));
    }

    @Test
    void testTransitiveCacheInvalidation() throws IOException {
      Files.writeString(tempDir.resolve("m.ug"), "import \"n.ug\";\nA --> B;\n");
      final var nested = tempDir.resolve("n.ug");
      Files.writeString(nested, "B --> 'b';\n");
      final var cached = new ModuleResolver(List.of(tempDir));
      assertEquals("b", text(cached.resolve(root("m.ug")).orElseThrow(), "B"));

      Files.writeString(nested, "B --> 'changed';\n");

      assertEquals("changed", text(cached.resolve(root("m.ug")).orElseThrow(), "B"));
    }

    @Test
    void testImportWithoutModule() throws IOException {
      Files.writeString(tempDir.resolve("plain.ug"), "A --> 'a';\n");

      final var resolved =
          new ModuleResolver(List.of(tempDir)).resolve(root("plain.ug")).orElseThrow();

      assertEquals("a", text(resolved, "A"));
    }

    @Test
    void testGrammarWithoutModuleInfoExportsAll() throws IOException {
      final var grammar = new Grammar(Map.of(), List.of(), null, "S");

      assertTrue(grammar.module().isExported("anything"));
    }
  }
}
