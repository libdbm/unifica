package com.libdbm.ugf.grammar;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for GrammarBuilder. */
class GrammarBuilderTests {

  @Nested
  @DisplayName("Basic building")
  class BasicBuilding {

    @Test
    @DisplayName("builds empty grammar")
    void builds_empty_grammar() {
      final var grammar = Grammar.builder().build();

      assertNotNull(grammar);
      assertTrue(grammar.rules().isEmpty());
    }

    @Test
    @DisplayName("builds grammar with single rule")
    void builds_with_single_rule() {
      final var rule = new GrammarRule("S", List.of(new RuleElement.Terminal("x")));

      final var grammar = Grammar.builder().add(rule).build();

      assertEquals(1, grammar.rules().size());
      assertEquals(1, grammar.rulesFor("S").size());
    }

    @Test
    @DisplayName("builds grammar with multiple rules")
    void builds_with_multiple_rules() {
      final var rule1 = new GrammarRule("S", List.of(new RuleElement.Nonterminal("A")));
      final var rule2 = new GrammarRule("A", List.of(new RuleElement.Terminal("a")));
      final var rule3 = new GrammarRule("A", List.of(new RuleElement.Terminal("b")));

      final var grammar = Grammar.builder().add(rule1).add(rule2).add(rule3).build();

      assertEquals(2, grammar.rules().size()); // S and A
      assertEquals(1, grammar.rulesFor("S").size());
      assertEquals(2, grammar.rulesFor("A").size());
    }
  }

  @Nested
  @DisplayName("Start symbol")
  class StartSymbol {

    @Test
    @DisplayName("explicit start symbol")
    void explicit_start_symbol() {
      final var grammar =
          Grammar.builder().start("document").add(new GrammarRule("document", List.of())).build();

      assertEquals("document", grammar.start());
    }

    @Test
    @DisplayName("infers start from first rule")
    void infers_start_from_first_rule() {
      final var rule1 = new GrammarRule("first", List.of());
      final var rule2 = new GrammarRule("second", List.of());

      final var grammar = Grammar.builder().add(rule1).add(rule2).build();

      assertEquals("first", grammar.start());
    }

    @Test
    @DisplayName("explicit start overrides inference")
    void explicit_overrides_inference() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("A", List.of()))
              .add(new GrammarRule("B", List.of()))
              .start("B")
              .build();

      assertEquals("B", grammar.start());
    }
  }

  @Nested
  @DisplayName("Module info")
  class ModuleInfoTests {

    @Test
    @DisplayName("sets module info")
    void sets_module_info() {
      final var info = new ModuleInfo("myModule", Set.of("export1", "export2"));

      final var grammar = Grammar.builder().module(info).build();

      assertEquals("myModule", grammar.module().name());
      assertTrue(grammar.module().isExported("export1"));
      assertTrue(grammar.module().isExported("export2"));
    }

    @Test
    @DisplayName("defaults to anonymous module")
    void defaults_to_anonymous() {
      final var grammar = Grammar.builder().build();

      assertNotNull(grammar.module());
    }
  }

  @Nested
  @DisplayName("Imports")
  class ImportTests {

    @Test
    @DisplayName("adds import declaration")
    void adds_import() {
      final var decl = new ImportDeclaration.All("some.module");

      final var grammar = Grammar.builder().addImport(decl).build();

      assertEquals(1, grammar.imports().size());
      assertEquals("some.module", grammar.imports().getFirst().path());
    }

    @Test
    @DisplayName("adds multiple imports")
    void adds_multiple_imports() {
      final var grammar =
          Grammar.builder()
              .addImport(new ImportDeclaration.All("module1"))
              .addImport(new ImportDeclaration.Selective("module2", Set.of("sym1", "sym2")))
              .addImport(new ImportDeclaration.File("path/to/file.ug"))
              .build();

      assertEquals(3, grammar.imports().size());
    }
  }

  @Nested
  @DisplayName("Fluent API")
  class FluentApi {

    @Test
    @DisplayName("methods return builder for chaining")
    void methods_return_builder() {
      final var builder = Grammar.builder();

      assertSame(builder, builder.add(new GrammarRule("S", List.of())));
      assertSame(builder, builder.start("S"));
      assertSame(builder, builder.module(ModuleInfo.anonymous()));
      assertSame(builder, builder.addImport(new ImportDeclaration.All("x")));
    }

    @Test
    @DisplayName("supports full chaining")
    void supports_chaining() {
      final var grammar =
          Grammar.builder()
              .module(new ModuleInfo("test", Set.of("S")))
              .start("S")
              .add(new GrammarRule("S", List.of(new RuleElement.Nonterminal("A"))))
              .add(new GrammarRule("A", List.of(new RuleElement.Terminal("x"))))
              .addImport(new ImportDeclaration.All("base"))
              .build();

      assertEquals("test", grammar.module().name());
      assertEquals("S", grammar.start());
      assertEquals(2, grammar.rules().size());
      assertEquals(1, grammar.imports().size());
    }
  }

  @Nested
  @DisplayName("From existing grammar")
  class FromExisting {

    @Test
    @DisplayName("creates builder from grammar")
    void creates_from_grammar() {
      final var original =
          Grammar.builder()
              .module(new ModuleInfo("original", Set.of("A")))
              .start("S")
              .add(new GrammarRule("S", List.of(new RuleElement.Nonterminal("A"))))
              .add(new GrammarRule("A", List.of(new RuleElement.Terminal("a"))))
              .addImport(new ImportDeclaration.All("dep"))
              .build();

      final var copy = Grammar.Builder.from(original).build();

      assertEquals(original.module().name(), copy.module().name());
      assertEquals(original.start(), copy.start());
      assertEquals(original.rules().size(), copy.rules().size());
      assertEquals(original.imports().size(), copy.imports().size());
    }

    @Test
    @DisplayName("allows modification of copy")
    void allows_modification() {
      final var original =
          Grammar.builder().start("S").add(new GrammarRule("S", List.of())).build();

      final var modified =
          Grammar.Builder.from(original)
              .add(new GrammarRule("B", List.of(new RuleElement.Terminal("b"))))
              .build();

      assertEquals(1, original.rules().size());
      assertEquals(2, modified.rules().size());
    }
  }

  @Nested
  @DisplayName("Validation")
  class ValidationTests {

    @Test
    @DisplayName("validate returns lint report")
    void validate_returns_report() {
      final var builder =
          Grammar.builder()
              .start("S")
              .add(new GrammarRule("S", List.of(new RuleElement.Terminal("x"))));

      final var report = builder.validate();

      assertNotNull(report);
      assertFalse(report.hasErrors());
    }

    @Test
    @DisplayName("buildValidated succeeds for valid grammar")
    void build_validated_succeeds() {
      final var grammar =
          Grammar.builder()
              .start("S")
              .add(new GrammarRule("S", List.of(new RuleElement.Terminal("x"))))
              .validated();

      assertNotNull(grammar);
    }

    @Test
    @DisplayName("buildValidated throws for invalid grammar")
    void build_validated_throws() {
      final var builder =
          Grammar.builder()
              .start("S")
              .add(new GrammarRule("S", List.of(new RuleElement.Nonterminal("undefined"))));

      assertThrows(IllegalStateException.class, builder::validated);
    }
  }
}
