package com.libdbm.ugf.grammar;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for ImportGraph to verify: - Dependency tracking - Cycle detection - Topological sorting -
 * Graph visualization
 */
final class ImportGraphTests {

  @Test
  void test_empty_graph() {
    final var graph = new ImportGraph();

    assertTrue(graph.modules().isEmpty());
    assertTrue(graph.cycles().isEmpty());
    assertTrue(graph.sort().isPresent());
  }

  @Test
  void test_single_module() {
    final var graph = new ImportGraph();
    final var grammar = Grammar.builder().module(new ModuleInfo("test", Set.of())).build();

    graph.add("test", grammar);

    assertEquals(1, graph.modules().size());
    assertTrue(graph.modules().contains("test"));
    assertTrue(graph.dependencies("test").isEmpty());
    assertTrue(graph.cycles().isEmpty());
    assertTrue(graph.sort().isPresent());
  }

  @Test
  void test_linear_dependency() {
    final var graph = new ImportGraph();

    // A -> B -> C
    final var a =
        Grammar.builder()
            .module(new ModuleInfo("A", Set.of()))
            .addImport(new ImportDeclaration.All("B"))
            .build();

    final var b =
        Grammar.builder()
            .module(new ModuleInfo("B", Set.of()))
            .addImport(new ImportDeclaration.All("C"))
            .build();

    final var c = Grammar.builder().module(new ModuleInfo("C", Set.of())).build();

    graph.add("A", a);
    graph.add("B", b);
    graph.add("C", c);

    assertEquals(3, graph.modules().size());
    assertEquals(Set.of("B"), graph.dependencies("A"));
    assertEquals(Set.of("C"), graph.dependencies("B"));
    assertEquals(Set.of(), graph.dependencies("C"));

    assertTrue(graph.cycles().isEmpty());

    final var sorted = graph.sort();
    assertTrue(sorted.isPresent());

    // C should come before B, B before A
    final List<String> order = sorted.get();
    assertTrue(order.indexOf("C") < order.indexOf("B"));
    assertTrue(order.indexOf("B") < order.indexOf("A"));
  }

  @Test
  void test_diamond_dependency() {
    final var graph = new ImportGraph();

    // A -> B, C
    // B -> D
    // C -> D
    final var a =
        Grammar.builder()
            .module(new ModuleInfo("A", Set.of()))
            .addImport(new ImportDeclaration.All("B"))
            .addImport(new ImportDeclaration.All("C"))
            .build();

    final var b =
        Grammar.builder()
            .module(new ModuleInfo("B", Set.of()))
            .addImport(new ImportDeclaration.All("D"))
            .build();

    final var c =
        Grammar.builder()
            .module(new ModuleInfo("C", Set.of()))
            .addImport(new ImportDeclaration.All("D"))
            .build();

    final var d = Grammar.builder().module(new ModuleInfo("D", Set.of())).build();

    graph.add("A", a);
    graph.add("B", b);
    graph.add("C", c);
    graph.add("D", d);

    assertTrue(graph.cycles().isEmpty());

    final var sorted = graph.sort();
    assertTrue(sorted.isPresent());

    final List<String> order = sorted.get();
    // D should come before both B and C
    assertTrue(order.indexOf("D") < order.indexOf("B"));
    assertTrue(order.indexOf("D") < order.indexOf("C"));
    // A should come last
    assertEquals("A", order.getLast());
  }

  @Test
  void test_simple_cycle() {
    final var graph = new ImportGraph();

    // A -> B -> C -> A (cycle)
    final var a =
        Grammar.builder()
            .module(new ModuleInfo("A", Set.of()))
            .addImport(new ImportDeclaration.All("B"))
            .build();

    final var b =
        Grammar.builder()
            .module(new ModuleInfo("B", Set.of()))
            .addImport(new ImportDeclaration.All("C"))
            .build();

    final var c =
        Grammar.builder()
            .module(new ModuleInfo("C", Set.of()))
            .addImport(new ImportDeclaration.All("A"))
            .build();

    graph.add("A", a);
    graph.add("B", b);
    graph.add("C", c);

    final var cycles = graph.cycles();
    assertFalse(cycles.isEmpty());
    assertEquals(1, cycles.size());

    final List<String> cycle = cycles.getFirst();
    assertTrue(cycle.contains("A"));
    assertTrue(cycle.contains("B"));
    assertTrue(cycle.contains("C"));

    // Topological sort should fail
    assertTrue(graph.sort().isEmpty());
  }

  @Test
  void test_self_cycle() {
    final var graph = new ImportGraph();

    // A -> A (self-cycle)
    final var a =
        Grammar.builder()
            .module(new ModuleInfo("A", Set.of()))
            .addImport(new ImportDeclaration.All("A"))
            .build();

    graph.add("A", a);

    final var cycles = graph.cycles();
    assertFalse(cycles.isEmpty());
  }

  @Test
  void test_multiple_cycles() {
    final var graph = new ImportGraph();

    // A -> B -> A (cycle 1)
    // C -> D -> C (cycle 2)
    final var a =
        Grammar.builder()
            .module(new ModuleInfo("A", Set.of()))
            .addImport(new ImportDeclaration.All("B"))
            .build();

    final var b =
        Grammar.builder()
            .module(new ModuleInfo("B", Set.of()))
            .addImport(new ImportDeclaration.All("A"))
            .build();

    final var c =
        Grammar.builder()
            .module(new ModuleInfo("C", Set.of()))
            .addImport(new ImportDeclaration.All("D"))
            .build();

    final var d =
        Grammar.builder()
            .module(new ModuleInfo("D", Set.of()))
            .addImport(new ImportDeclaration.All("C"))
            .build();

    graph.add("A", a);
    graph.add("B", b);
    graph.add("C", c);
    graph.add("D", d);

    final var cycles = graph.cycles();
    assertEquals(2, cycles.size());
  }

  @Test
  void test_dot_format() {
    final var graph = new ImportGraph();

    final var a =
        Grammar.builder()
            .module(new ModuleInfo("A", Set.of("X", "Y")))
            .addImport(new ImportDeclaration.All("B"))
            .build();

    final var b = Grammar.builder().module(new ModuleInfo("B", Set.of("Z"))).build();

    graph.add("A", a);
    graph.add("B", b);

    final String dot = graph.toDot();

    assertNotNull(dot);
    assertTrue(dot.contains("digraph ImportGraph"));
    assertTrue(dot.contains("\"A\""));
    assertTrue(dot.contains("\"B\""));
    assertTrue(dot.contains("\"A\" -> \"B\""));
    assertTrue(dot.contains("2 exports")); // A has 2 exports
    assertTrue(dot.contains("1 exports")); // B has 1 export
  }

  @Test
  void test_ascii_format() {
    final var graph = new ImportGraph();

    final var a =
        Grammar.builder()
            .module(new ModuleInfo("A", Set.of("X", "Y")))
            .addImport(new ImportDeclaration.All("B"))
            .build();

    final var b = Grammar.builder().module(new ModuleInfo("B", Set.of())).build();

    graph.add("A", a);
    graph.add("B", b);

    final String ascii = graph.toAscii();

    assertNotNull(ascii);
    assertTrue(ascii.contains("Import Graph"));
    assertTrue(ascii.contains("A"));
    assertTrue(ascii.contains("B"));
    assertTrue(ascii.contains("exports"));
  }

  @Test
  void test_ascii_cycle_detection() {
    final var graph = new ImportGraph();

    // A -> B -> A
    final var a =
        Grammar.builder()
            .module(new ModuleInfo("A", Set.of()))
            .addImport(new ImportDeclaration.All("B"))
            .build();

    final var b =
        Grammar.builder()
            .module(new ModuleInfo("B", Set.of()))
            .addImport(new ImportDeclaration.All("A"))
            .build();

    graph.add("A", a);
    graph.add("B", b);

    final String ascii = graph.toAscii();

    assertTrue(ascii.contains("[CYCLE]"));
  }

  @Test
  void test_graph_statistics() {
    final var graph = new ImportGraph();

    final var a =
        Grammar.builder()
            .module(new ModuleInfo("A", Set.of()))
            .addImport(new ImportDeclaration.All("B"))
            .addImport(new ImportDeclaration.All("C"))
            .build();

    final var b = Grammar.builder().module(new ModuleInfo("B", Set.of())).build();

    final var c = Grammar.builder().module(new ModuleInfo("C", Set.of())).build();

    graph.add("A", a);
    graph.add("B", b);
    graph.add("C", c);

    final var stats = graph.statistics();

    assertEquals(3, stats.totalModules());
    assertEquals(2, stats.totalImports());
    assertEquals(0, stats.cycleCount());
    assertEquals(2, stats.maxFanOut()); // A imports 2 modules
    assertEquals("A", stats.maxFanOutModule());
  }

  @Test
  void test_graph_statistics_with_cycles() {
    final var graph = new ImportGraph();

    // A -> B -> A
    final var a =
        Grammar.builder()
            .module(new ModuleInfo("A", Set.of()))
            .addImport(new ImportDeclaration.All("B"))
            .build();

    final var b =
        Grammar.builder()
            .module(new ModuleInfo("B", Set.of()))
            .addImport(new ImportDeclaration.All("A"))
            .build();

    graph.add("A", a);
    graph.add("B", b);

    final var stats = graph.statistics();

    assertEquals(2, stats.totalModules());
    assertEquals(2, stats.totalImports());
    assertEquals(1, stats.cycleCount());
    assertFalse(stats.cycles().isEmpty());
  }

  @Test
  void test_graph_statistics_formatting() {
    final var graph = new ImportGraph();

    final var a =
        Grammar.builder()
            .module(new ModuleInfo("A", Set.of()))
            .addImport(new ImportDeclaration.All("B"))
            .build();

    final var b = Grammar.builder().module(new ModuleInfo("B", Set.of())).build();

    graph.add("A", a);
    graph.add("B", b);

    final var stats = graph.statistics();
    final String formatted = stats.format();

    assertNotNull(formatted);
    assertTrue(formatted.contains("Import Graph Statistics"));
    assertTrue(formatted.contains("Total modules: 2"));
    assertTrue(formatted.contains("Total imports: 1"));
  }

  @Test
  void test_fan_in_calculation() {
    final var graph = new ImportGraph();

    // A -> C
    // B -> C
    // C is imported by both A and B (fan-in = 2)
    final var a =
        Grammar.builder()
            .module(new ModuleInfo("A", Set.of()))
            .addImport(new ImportDeclaration.All("C"))
            .build();

    final var b =
        Grammar.builder()
            .module(new ModuleInfo("B", Set.of()))
            .addImport(new ImportDeclaration.All("C"))
            .build();

    final var c = Grammar.builder().module(new ModuleInfo("C", Set.of())).build();

    graph.add("A", a);
    graph.add("B", b);
    graph.add("C", c);

    final var stats = graph.statistics();

    assertEquals(2, stats.maxFanIn());
    assertEquals("C", stats.maxFanInModule());
  }

  @Test
  void test_complex_graph() {
    final var graph = new ImportGraph();

    // Complex dependency structure
    // root -> syntax, semantics
    // syntax -> core, features
    // semantics -> core, logic
    // features -> core
    // logic -> core
    final var root =
        Grammar.builder()
            .module(new ModuleInfo("root", Set.of()))
            .addImport(new ImportDeclaration.All("syntax"))
            .addImport(new ImportDeclaration.All("semantics"))
            .build();

    final var syntax =
        Grammar.builder()
            .module(new ModuleInfo("syntax", Set.of()))
            .addImport(new ImportDeclaration.All("core"))
            .addImport(new ImportDeclaration.All("features"))
            .build();

    final var semantics =
        Grammar.builder()
            .module(new ModuleInfo("semantics", Set.of()))
            .addImport(new ImportDeclaration.All("core"))
            .addImport(new ImportDeclaration.All("logic"))
            .build();

    final var features =
        Grammar.builder()
            .module(new ModuleInfo("features", Set.of()))
            .addImport(new ImportDeclaration.All("core"))
            .build();

    final var logic =
        Grammar.builder()
            .module(new ModuleInfo("logic", Set.of()))
            .addImport(new ImportDeclaration.All("core"))
            .build();

    final var core = Grammar.builder().module(new ModuleInfo("core", Set.of())).build();

    graph.add("root", root);
    graph.add("syntax", syntax);
    graph.add("semantics", semantics);
    graph.add("features", features);
    graph.add("logic", logic);
    graph.add("core", core);

    assertEquals(6, graph.modules().size());
    assertTrue(graph.cycles().isEmpty());

    final var sorted = graph.sort();
    assertTrue(sorted.isPresent());

    final List<String> order = sorted.get();
    // Core should be first (no dependencies)
    assertEquals("core", order.getFirst());
    // Root should be last (depends on everything transitively)
    assertEquals("root", order.getLast());

    final var stats = graph.statistics();
    assertEquals(6, stats.totalModules());
    assertEquals(4, stats.maxFanIn()); // core is imported by 4 modules
    assertEquals("core", stats.maxFanInModule());
  }
}
