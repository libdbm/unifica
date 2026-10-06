package com.libdbm.ugf.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValuesTests {

  private static Variable suffixed(final Variable variable) {
    return new Variable(variable.name() + "'1");
  }

  @Test
  void testRenamesNestedVariables() {
    final var value =
        Structure.builder()
            .with("a", new Variable("X"))
            .with("b", Structure.builder().with("c", new Variable("Y")).build())
            .build();

    final var renamed = (Structure) Values.rename(value, ValuesTests::suffixed);

    assertEquals(new Variable("X'1"), renamed.get("a"));
    assertEquals(new Variable("Y'1"), ((Structure) renamed.get("b")).get("c"));
  }

  /** Review: renaming skipped variables inside constituent bindings. */
  @Test
  void testRenamesInsideBindings() {
    final var binding = Binding.of("dog", Structure.builder().with("n", new Variable("N")).build());
    final var value = Structure.builder().with("b", binding).build();

    final var renamed = (Structure) Values.rename(value, ValuesTests::suffixed);

    final var inner = (Binding) renamed.get("b");
    assertEquals("dog", inner.text());
    assertEquals(new Variable("N'1"), inner.features().get("n"));
  }

  @Test
  void testUnchangedValueIsSameInstance() {
    final var value = Structure.builder().with("a", "b").build();

    assertSame(value, Values.rename(value, ValuesTests::suffixed));
  }

  /** Features are visited in sorted order, so first-occurrence numbering is deterministic. */
  @Test
  void testSortedVisitOrder() {
    final var value =
        Structure.builder().with("z", new Variable("A")).with("a", new Variable("B")).build();
    final var order = new ArrayList<String>();

    Values.rename(
        value,
        variable -> {
          order.add(variable.name());
          return variable;
        });

    assertEquals(List.of("B", "A"), order);
  }

  /** Review M1: deep values cannot exhaust the stack. */
  @Test
  void testDeepValue() {
    Value value = new Variable("X");
    for (var depth = 0; depth < 100_000; depth++) {
      value = Structure.builder().with("n", value).build();
    }

    var renamed = Values.rename(value, ValuesTests::suffixed);

    while (renamed instanceof Structure structure) {
      renamed = structure.get("n");
    }
    assertEquals(new Variable("X'1"), renamed);
  }
}
