package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.features.Structure;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for ParseTreeUtils. */
class UtilitiesTests {

  @Nested
  @DisplayName("textOf")
  class TextOf {

    @Test
    @DisplayName("extracts text from first leaf child")
    void extracts_text_from_first_leaf() {
      final var leaf = new ParseTree.Leaf("hello", 0, 5);
      final var node = new ParseTree.Node("word", null, List.of(leaf), new Structure());

      assertEquals("hello", Utilities.textOf(node));
    }

    @Test
    @DisplayName("returns null for empty children")
    void returns_null_for_empty_children() {
      final var node = new ParseTree.Node("empty", null, List.of(), new Structure());

      assertNull(Utilities.textOf(node));
    }

    @Test
    @DisplayName("collects text from nested nodes when first child is not leaf")
    void collects_text_from_nested_nodes() {
      final var leaf1 = new ParseTree.Leaf("hello", 0, 5);
      final var leaf2 = new ParseTree.Leaf("world", 6, 11);
      final var inner = new ParseTree.Node("inner", null, List.of(leaf1, leaf2), new Structure());
      final var outer = new ParseTree.Node("outer", null, List.of(inner), new Structure());

      assertEquals("helloworld", Utilities.textOf(outer));
    }

    @Test
    @DisplayName("throws on null node")
    void throws_on_null_node() {
      assertThrows(NullPointerException.class, () -> Utilities.textOf(null));
    }
  }

  @Nested
  @DisplayName("collectText")
  class CollectText {

    @Test
    @DisplayName("collects text from leaf")
    void collects_from_leaf() {
      final var leaf = new ParseTree.Leaf("test", 0, 4);
      final var sb = new StringBuilder();

      Utilities.collectText(leaf, sb);

      assertEquals("test", sb.toString());
    }

    @Test
    @DisplayName("collects text from nested structure")
    void collects_from_nested_structure() {
      final var leaf1 = new ParseTree.Leaf("a", 0, 1);
      final var leaf2 = new ParseTree.Leaf("b", 1, 2);
      final var leaf3 = new ParseTree.Leaf("c", 2, 3);
      final var inner = new ParseTree.Node("inner", null, List.of(leaf2), new Structure());
      final var outer =
          new ParseTree.Node("outer", null, List.of(leaf1, inner, leaf3), new Structure());
      final var sb = new StringBuilder();

      Utilities.collectText(outer, sb);

      assertEquals("abc", sb.toString());
    }

    @Test
    @DisplayName("throws on null tree")
    void throws_on_null_tree() {
      assertThrows(
          NullPointerException.class, () -> Utilities.collectText(null, new StringBuilder()));
    }

    @Test
    @DisplayName("throws on null StringBuilder")
    void throws_on_null_stringbuilder() {
      final var leaf = new ParseTree.Leaf("test", 0, 4);
      assertThrows(NullPointerException.class, () -> Utilities.collectText(leaf, null));
    }
  }

  @Nested
  @DisplayName("extractAllText")
  class ExtractAllText {

    @Test
    @DisplayName("extracts all text from complex tree")
    void extracts_all_text() {
      final var the = new ParseTree.Leaf("the", 0, 3);
      final var dog = new ParseTree.Leaf("dog", 4, 7);
      final var barks = new ParseTree.Leaf("barks", 8, 13);
      final var np = new ParseTree.Node("np", null, List.of(the, dog), new Structure());
      final var vp = new ParseTree.Node("vp", null, List.of(barks), new Structure());
      final var s = new ParseTree.Node("s", null, List.of(np, vp), new Structure());

      assertEquals("thedogbarks", Utilities.extractAllText(s));
    }

    @Test
    @DisplayName("returns empty string for empty node")
    void returns_empty_for_empty_node() {
      final var node = new ParseTree.Node("empty", null, List.of(), new Structure());

      assertEquals("", Utilities.extractAllText(node));
    }
  }

  @Nested
  @DisplayName("extractTextWithSpaces")
  class ExtractTextWithSpaces {

    @Test
    @DisplayName("adds spaces between leaves")
    void adds_spaces_between_leaves() {
      final var word1 = new ParseTree.Leaf("flare", 0, 5);
      final var word2 = new ParseTree.Leaf("stack", 6, 11);
      final var node = new ParseTree.Node("compound", null, List.of(word1, word2), new Structure());

      assertEquals("flare stack", Utilities.extractTextWithSpaces(node));
    }

    @Test
    @DisplayName("returns null for empty children")
    void returns_null_for_empty_children() {
      final var node = new ParseTree.Node("empty", null, List.of(), new Structure());

      assertNull(Utilities.extractTextWithSpaces(node));
    }

    @Test
    @DisplayName("handles nested nodes with spaces")
    void handles_nested_nodes() {
      final var a = new ParseTree.Leaf("heat", 0, 4);
      final var b = new ParseTree.Leaf("exchanger", 5, 14);
      final var inner = new ParseTree.Node("n", null, List.of(a, b), new Structure());
      final var det = new ParseTree.Leaf("the", 0, 3);
      final var outer = new ParseTree.Node("np", null, List.of(det, inner), new Structure());

      assertEquals("the heat exchanger", Utilities.extractTextWithSpaces(outer));
    }
  }

  @Nested
  @DisplayName("find")
  class Find {

    @Test
    @DisplayName("finds direct child by symbol")
    void finds_direct_child() {
      final var leaf = new ParseTree.Leaf("word", 0, 4);
      final var child = new ParseTree.Node("target", null, List.of(leaf), new Structure());
      final var parent = new ParseTree.Node("parent", null, List.of(child), new Structure());

      final var found = Utilities.find(parent, "target");

      assertNotNull(found);
      assertInstanceOf(ParseTree.Node.class, found);
      assertEquals("target", ((ParseTree.Node) found).symbol());
    }

    @Test
    @DisplayName("finds nested child recursively")
    void finds_nested_child() {
      final var leaf = new ParseTree.Leaf("x", 0, 1);
      final var deep = new ParseTree.Node("deep", null, List.of(leaf), new Structure());
      final var middle = new ParseTree.Node("middle", null, List.of(deep), new Structure());
      final var root = new ParseTree.Node("root", null, List.of(middle), new Structure());

      final var found = Utilities.find(root, "deep");

      assertNotNull(found);
      assertEquals("deep", ((ParseTree.Node) found).symbol());
    }

    @Test
    @DisplayName("returns null when symbol not found")
    void returns_null_when_not_found() {
      final var leaf = new ParseTree.Leaf("x", 0, 1);
      final var node = new ParseTree.Node("node", null, List.of(leaf), new Structure());

      assertNull(Utilities.find(node, "nonexistent"));
    }

    @Test
    @DisplayName("returns null for leaf input")
    void returns_null_for_leaf() {
      final var leaf = new ParseTree.Leaf("x", 0, 1);

      assertNull(Utilities.find(leaf, "anything"));
    }
  }

  @Nested
  @DisplayName("firstLeaf")
  class FirstLeaf {

    @Test
    @DisplayName("returns text of leaf directly")
    void returns_leaf_text_directly() {
      final var leaf = new ParseTree.Leaf("hello", 0, 5);

      assertEquals("hello", Utilities.firstLeaf(leaf));
    }

    @Test
    @DisplayName("finds first leaf in tree")
    void finds_first_leaf_in_tree() {
      final var first = new ParseTree.Leaf("first", 0, 5);
      final var second = new ParseTree.Leaf("second", 6, 12);
      final var node = new ParseTree.Node("node", null, List.of(first, second), new Structure());

      assertEquals("first", Utilities.firstLeaf(node));
    }

    @Test
    @DisplayName("returns null for empty node")
    void returns_null_for_empty_node() {
      final var node = new ParseTree.Node("empty", null, List.of(), new Structure());

      assertNull(Utilities.firstLeaf(node));
    }
  }

  @Nested
  @DisplayName("spaced")
  class Spaced {

    @Test
    @DisplayName("joins leaves with spaces")
    void joins_leaves_with_spaces() {
      final var a = new ParseTree.Leaf("the", 0, 3);
      final var b = new ParseTree.Leaf("quick", 4, 9);
      final var c = new ParseTree.Leaf("fox", 10, 13);
      final var node = new ParseTree.Node("s", null, List.of(a, b, c), new Structure());

      assertEquals("the quick fox", Utilities.spaced(node));
    }

    @Test
    @DisplayName("trims result")
    void trims_result() {
      final var leaf = new ParseTree.Leaf("word", 0, 4);
      final var node = new ParseTree.Node("s", null, List.of(leaf), new Structure());

      assertEquals("word", Utilities.spaced(node));
    }
  }
}
