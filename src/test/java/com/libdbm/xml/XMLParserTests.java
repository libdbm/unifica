package com.libdbm.xml;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarNormalizer;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.parser.ChartParser;
import com.libdbm.ugf.parser.LexicalAnalyzer;
import com.libdbm.ugf.parser.ParseTree;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class XMLParserTests {

  private static ChartParser parser;
  private static Grammar grammar;
  private static LexicalAnalyzer lexer;

  @BeforeAll
  static void setup() throws Exception {
    final var resource = XMLParserTests.class.getClassLoader().getResource("xml.ug");
    if (resource == null) {
      throw new RuntimeException("Unable to find XML grammar");
    }
    try (final var stream = resource.openStream()) {
      final var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      grammar = GrammarNormalizer.normalize(UnificationGrammarParserFactory.parse(content));
      // Do not skip whitespace in XML
      lexer = LexicalAnalyzer.build(grammar, false);
      parser = new ChartParser(grammar, lexer);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to load XML grammar", e);
    }
  }

  @Test
  void testEmptyElement() {
    final var input = "<img/>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    assertInstanceOf(XMLNode.EmptyElement.class, node);

    final var element = (XMLNode.EmptyElement) node;
    assertEquals("img", element.tag());
    assertTrue(element.attributes().isEmpty());
  }

  @Test
  void testEmptyElementWithAttributes() {
    final var input = "<img src=\"photo.jpg\" alt=\"A photo\"/>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    assertInstanceOf(XMLNode.EmptyElement.class, node);

    final var element = (XMLNode.EmptyElement) node;
    assertEquals("img", element.tag());
    assertEquals(2, element.attributes().size());
    assertEquals("photo.jpg", element.attributes().get("src"));
    assertEquals("A photo", element.attributes().get("alt"));
  }

  @Test
  void testSimpleElementWithText() {
    final var input = "<title>Hello World</title>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    assertInstanceOf(XMLNode.Element.class, node);

    final var element = (XMLNode.Element) node;
    assertEquals("title", element.tag());
    assertTrue(element.attributes().isEmpty());
    assertInstanceOf(XMLNode.Content.Text.class, element.content());

    final var textContent = (XMLNode.Content.Text) element.content();
    assertEquals("Hello World", textContent.value());
  }

  @Test
  void testSimpleElementWithAttributes() {
    final var input = "<div class=\"container\" id=\"main\">Content</div>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    assertInstanceOf(XMLNode.Element.class, node);

    final var element = (XMLNode.Element) node;
    assertEquals("div", element.tag());
    assertEquals(2, element.attributes().size());
    assertEquals("container", element.attributes().get("class"));
    assertEquals("main", element.attributes().get("id"));

    assertInstanceOf(XMLNode.Content.Text.class, element.content());
    final var textContent = (XMLNode.Content.Text) element.content();
    assertEquals("Content", textContent.value());
  }

  @Test
  void testNestedElements() {
    final var input = "<div><p>Hello</p><p>World</p></div>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    assertInstanceOf(XMLNode.Element.class, node);

    final var div = (XMLNode.Element) node;
    assertEquals("div", div.tag());
    assertInstanceOf(XMLNode.Content.Elements.class, div.content());

    final var elements = (XMLNode.Content.Elements) div.content();
    assertEquals(2, elements.nodes().size());

    // First paragraph
    final var p1 = (XMLNode.Element) elements.nodes().get(0);
    assertEquals("p", p1.tag());
    assertInstanceOf(XMLNode.Content.Text.class, p1.content());
    assertEquals("Hello", ((XMLNode.Content.Text) p1.content()).value());

    // Second paragraph
    final var p2 = (XMLNode.Element) elements.nodes().get(1);
    assertEquals("p", p2.tag());
    assertInstanceOf(XMLNode.Content.Text.class, p2.content());
    assertEquals("World", ((XMLNode.Content.Text) p2.content()).value());
  }

  @Test
  void testSerialization() {
    final var input = "<div class=\"test\"><p>Hello</p><img src=\"test.jpg\"/></div>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var serializer = new XMLSerializer(false, 0);
    final var serialized = serializer.serialize(node);

    // Should produce valid XML (whitespace may differ)
    assertTrue(serialized.contains("<div class=\"test\">"));
    assertTrue(serialized.contains("<p>Hello</p>"));
    assertTrue(serialized.contains("<img src=\"test.jpg\"/>"));
    assertTrue(serialized.contains("</div>"));
  }

  @Test
  void testSimpleText() {
    final var input = "<p>Hello World</p>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var text = (XMLNode.Content.Text) element.content();

    assertEquals("Hello World", text.value());
  }

  @Test
  void testTextWithPunctuation() {
    final var input = "<p>Hello, World!</p>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var text = (XMLNode.Content.Text) element.content();

    // Text content preserves original spacing
    assertEquals("Hello, World!", text.value());
  }

  @Test
  void testTextWithURL() {
    final var input = "<link>https://example.com/path?query=value</link>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var text = (XMLNode.Content.Text) element.content();

    // URL components preserved (colon, slash, dot don't get spaces)
    assertTrue(text.value().contains("https://"));
    assertTrue(text.value().contains(".com/"));
  }

  @Test
  void testTextWithNumbers() {
    final var input = "<price>19.99</price>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var text = (XMLNode.Content.Text) element.content();

    assertEquals("19.99", text.value());
  }

  @Test
  void testAttributeWithFileName() {
    final var input = "<img src=\"photo.jpg\"/>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.EmptyElement) node;

    assertEquals("photo.jpg", element.attributes().get("src"));
  }

  @Test
  void testAttributeWithURL() {
    final var input = "<a href=\"https://example.com/page.html\">Link</a>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;

    assertEquals("https://example.com/page.html", element.attributes().get("href"));
  }

  @Test
  void testAttributeWithSpaces() {
    final var input = "<div title=\"Hello World\">Content</div>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;

    assertEquals("Hello World", element.attributes().get("title"));
  }

  @Test
  void testEmptyAttribute() {
    final var input = "<input value=\"\"/>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.EmptyElement) node;

    assertEquals("", element.attributes().get("value"));
  }

  @Test
  void testMultipleAttributes() {
    final var input = "<img src=\"photo.jpg\" alt=\"A photo\" width=\"800\" height=\"600\"/>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.EmptyElement) node;

    assertEquals(4, element.attributes().size());
    assertEquals("photo.jpg", element.attributes().get("src"));
    assertEquals("A photo", element.attributes().get("alt"));
    assertEquals("800", element.attributes().get("width"));
    assertEquals("600", element.attributes().get("height"));
  }

  @Test
  void testAttributesPreserveOrder() {
    final var input = "<div id=\"main\" class=\"container\" data-value=\"test\">Content</div>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;

    // LinkedHashMap should preserve insertion order
    final var attrKeys = element.attributes().keySet().toArray(new String[0]);
    assertEquals("id", attrKeys[0]);
    assertEquals("class", attrKeys[1]);
    assertEquals("data-value", attrKeys[2]);
  }

  @Test
  void testMixedTextAndElements() {
    final var input = "<div>start<span/>middle<p/>end</div>";

    final var result = parser.parse(input).toOptional();
    assertTrue(result.isPresent(), "Should parse mixed text and elements");

    final var xml = XMLBuilder.build(result.get(), input);
    assertInstanceOf(XMLNode.Element.class, xml);

    final var element = (XMLNode.Element) xml;
    assertInstanceOf(XMLNode.Content.Mixed.class, element.content());

    final var mixed = (XMLNode.Content.Mixed) element.content();
    assertEquals(5, mixed.items().size(), "Should have 5 content items");

    // Verify types: text, element, text, element, text
    assertInstanceOf(XMLNode.ContentItem.Text.class, mixed.items().get(0));
    assertInstanceOf(XMLNode.ContentItem.Element.class, mixed.items().get(1));
    assertInstanceOf(XMLNode.ContentItem.Text.class, mixed.items().get(2));
    assertInstanceOf(XMLNode.ContentItem.Element.class, mixed.items().get(3));
    assertInstanceOf(XMLNode.ContentItem.Text.class, mixed.items().get(4));
  }

  @Test
  void testTextContentSimpleCapture() {
    final var input = "<p>hello . world / test</p>";

    final var result = parser.parse(input).toOptional();
    assertTrue(result.isPresent(), "Should parse simple text");

    final var xml = XMLBuilder.build(result.get(), input);
    assertInstanceOf(XMLNode.Element.class, xml);

    final var element = (XMLNode.Element) xml;
    assertInstanceOf(XMLNode.Content.Text.class, element.content());

    final var text = (XMLNode.Content.Text) element.content();
    // Text content preserves original spacing
    assertEquals("hello . world / test", text.value());
  }

  @Test
  void testTextSerialization() {
    final var input = "<p>Hello, World! Visit https://example.com</p>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var serializer = new XMLSerializer(false, 0);
    final var serialized = serializer.serialize(node);

    // Text should be present and properly formed
    assertTrue(serialized.contains("<p>"));
    assertTrue(serialized.contains("</p>"));
    assertTrue(serialized.contains("Hello"));
  }

  @Test
  void testWhitespaceNormalization() {
    // Note: Whitespace is preserved in text content
    final String input = "<p>Hello     World</p>";
    final Optional<ParseTree> result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var text = (XMLNode.Content.Text) element.content();

    // Whitespace is preserved as-is from input
    assertEquals("Hello     World", text.value());
  }

  @Test
  void testRoundTrip() {
    final var input = "<root><child id=\"1\">Text</child><empty/></root>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var serializer = new XMLSerializer(false, 0);
    final var serialized = serializer.serialize(node);

    // Parse again
    final var result2 = parser.parse(serialized).toOptional();
    assertTrue(result2.isPresent(), "Second parse should succeed");

    final var node2 = XMLBuilder.build(result2.get(), serialized);

    // Both should be semantically equivalent
    assertEquals(node.getClass(), node2.getClass());
  }

  @Test
  void testSimpleComment() {
    final var input = "<div><!-- this is a comment --></div>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    assertInstanceOf(XMLNode.Element.class, node);

    final var element = (XMLNode.Element) node;
    assertEquals("div", element.tag());
    assertInstanceOf(XMLNode.Content.Mixed.class, element.content());

    final var mixed = (XMLNode.Content.Mixed) element.content();
    assertEquals(1, mixed.items().size());
    assertInstanceOf(XMLNode.ContentItem.Comment.class, mixed.items().getFirst());

    final var comment = (XMLNode.ContentItem.Comment) mixed.items().getFirst();
    assertEquals(" this is a comment ", comment.value());
  }

  @Test
  void testCommentWithDashes() {
    final var input = "<div><!-- a-b-c --></div>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var mixed = (XMLNode.Content.Mixed) element.content();
    final var comment = (XMLNode.ContentItem.Comment) mixed.items().getFirst();

    assertEquals(" a-b-c ", comment.value());
  }

  @Test
  void testEmptyComment() {
    final var input = "<div><!----></div>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var mixed = (XMLNode.Content.Mixed) element.content();
    final var comment = (XMLNode.ContentItem.Comment) mixed.items().getFirst();

    assertEquals("", comment.value());
  }

  @Test
  void testTextAndComment() {
    final var input = "<p>Hello<!-- comment -->World</p>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    assertInstanceOf(XMLNode.Content.Mixed.class, element.content());

    final var mixed = (XMLNode.Content.Mixed) element.content();
    assertEquals(3, mixed.items().size());
    assertInstanceOf(XMLNode.ContentItem.Text.class, mixed.items().get(0));
    assertInstanceOf(XMLNode.ContentItem.Comment.class, mixed.items().get(1));
    assertInstanceOf(XMLNode.ContentItem.Text.class, mixed.items().get(2));
  }

  // ---------------------------------------------------------------------
  // Tokenization tests (grouped)
  // ---------------------------------------------------------------------
  @Nested
  class Tokenization {
    @Test
    void tokenizesSimpleOpenAndClose() {
      final var input = "<div>hi</div>";
      final var tokens = lexer.tokenizeSingle(input);

      // Expected categories: <div> text </div>
      assertEquals(7, tokens.size(), () -> "Tokens: " + tokens);
      assertEquals("open_bracket", tokens.get(0).string("cat", null));
      assertEquals("tagname", tokens.get(1).string("cat", null));
      assertEquals("close_open", tokens.get(2).string("cat", null));
      assertEquals("textpart", tokens.get(3).string("cat", null));
      assertEquals("close_open_bracket", tokens.get(4).string("cat", null));
      assertEquals("tagname", tokens.get(5).string("cat", null));
      assertEquals("close_bracket", tokens.get(6).string("cat", null));

      // And texts
      assertEquals("<", tokens.get(0).text());
      assertEquals("div", tokens.get(1).text());
      assertEquals(">", tokens.get(2).text());
      assertEquals("hi", tokens.get(3).text());
      assertEquals("</", tokens.get(4).text());
      assertEquals("div", tokens.get(5).text());
      assertEquals(">", tokens.get(6).text());
    }

    @Test
    void tokenizesAttributesAndStringValues() {
      final var input = "<a href=\"url\" id=\"x\">";
      final var tokens = lexer.tokenizeSingle(input);

      // Expected sequence (ws is significant since skip=false):
      // < tagname ws attrname = " stringpart " ws attrname = " stringpart " >
      // Note: our input already ends with '>' so it's included in tokens.
      assertEquals(15, tokens.size(), () -> "Tokens: " + tokens);
      assertEquals("open_bracket", tokens.get(0).string("cat", null));
      assertEquals("tagname", tokens.get(1).string("cat", null));
      assertEquals("ws", tokens.get(2).string("cat", null));
      // In this grammar, both 'tagname' (constrained) and 'attrname' (unconstrained)
      // regexes can match attribute names; the lexer prefers constrained categories.
      // So attribute names may be categorized as 'tagname'. Accept either.
      assertTrue(
          "attrname".equals(tokens.get(3).string("cat", null))
              || "tagname".equals(tokens.get(3).string("cat", null)),
          () -> "Unexpected name for first attribute name: " + tokens.get(3));
      assertNull(tokens.get(4).string("cat", null)); // '=' has no explicit name in grammar
      assertEquals("open_quote", tokens.get(5).string("cat", null));
      assertEquals("stringpart", tokens.get(6).string("cat", null));
      assertEquals("close_quote", tokens.get(7).string("cat", null));
      assertEquals("ws", tokens.get(8).string("cat", null));
      assertTrue(
          "attrname".equals(tokens.get(9).string("cat", null))
              || "tagname".equals(tokens.get(9).string("cat", null)),
          () -> "Unexpected name for second attribute name: " + tokens.get(9));
      assertNull(tokens.get(10).string("cat", null)); // '='
      assertEquals("open_quote", tokens.get(11).string("cat", null));
      assertEquals("stringpart", tokens.get(12).string("cat", null));
      assertEquals("close_quote", tokens.get(13).string("cat", null));

      // Last token should be the close of open tag
      assertEquals(">", tokens.getLast().text());
      assertEquals("close_open", tokens.getLast().string("cat", null));
    }

    @Test
    void tokenPositionsAreTracked() {
      final var input = "<br/>";
      final var tokens = lexer.tokenizeSingle(input);

      // Expect: '<' 'br' '/>'
      assertEquals(3, tokens.size());
      assertEquals("<", tokens.get(0).text());
      assertEquals(0, tokens.get(0).start());
      assertEquals(1, tokens.get(0).end());

      assertEquals("br", tokens.get(1).text());
      assertEquals(1, tokens.get(1).start());
      assertEquals(3, tokens.get(1).end());

      assertEquals("/>", tokens.get(2).text());
      assertEquals(3, tokens.get(2).start());
      assertEquals(5, tokens.get(2).end());
    }

    @Test
    void tokenizesCommentsWithinContent() {
      final var input = "<div><!-- hi --></div>";
      final var tokens = lexer.tokenizeSingle(input);

      // Find indices for comment tokens
      final var openIdx =
          tokens.stream().map(t -> t.string("cat", null)).toList().indexOf("comment_open");
      assertTrue(openIdx > 0, () -> "No comment_open in tokens: " + tokens);

      // comment_text tokens follow, then comment_close
      int i = openIdx + 1;
      int textCount = 0;
      while (i < tokens.size() && "comment_text".equals(tokens.get(i).string("cat", null))) {
        textCount++;
        i++;
      }
      assertTrue(textCount >= 1, () -> "Expected some comment_text, tokens: " + tokens);
      assertEquals("comment_close", tokens.get(i).string("cat", null));
    }
  }

  // ---------------------------------------------------------------------
  // State management tests (grouped)
  // ---------------------------------------------------------------------
  @Nested
  class StateManagement {

    @Test
    void attributeQuotesCreateAndPopATTRVALUEState() {
      final var input = "<a href=\"http://x\">";
      final var tokens = lexer.tokenizeSingle(input);

      // Find the first open_quote and its matching close_quote, verify only string tokens inside
      final int open =
          tokens.stream().map(t -> t.string("cat", null)).toList().indexOf("open_quote");
      assertTrue(open >= 0, () -> "No open_quote in tokens: " + tokens);

      int close = open + 1;
      while (close < tokens.size()
          && !"close_quote".equals(tokens.get(close).string("cat", null))) {
        close++;
      }
      assertTrue(close < tokens.size(), () -> "No matching close_quote, tokens: " + tokens);

      for (int i = open + 1; i < close; i++) {
        final var tok = tokens.get(i);
        final var cat = tok.string("cat", null);
        assertTrue(
            "stringpart".equals(cat) || "char_ref".equals(cat) || "entity_ref".equals(cat),
            () -> "Unexpected token inside attribute value: " + tok);
      }
    }

    @Test
    void contentStateAllowsNestedElements() {
      final var input = "<p>hi<b>bold</b>!</p>";
      final var tokens = lexer.tokenizeSingle(input);

      // Expect to see nested element tokens between textparts
      final var cats = tokens.stream().map(t -> t.string("cat", null)).toList();
      // At least one open_bracket after a close_open of <p>
      final var firstCloseOpen = cats.indexOf("close_open");
      assertTrue(firstCloseOpen >= 0, () -> "No close_open for <p>: " + tokens);

      final var nestedOpen = cats.subList(firstCloseOpen + 1, cats.size()).indexOf("open_bracket");
      assertTrue(nestedOpen >= 0, () -> "No nested open_bracket found: " + tokens);

      // Ensure nested tag structure exists
      final int idx = firstCloseOpen + 1 + nestedOpen;
      assertEquals("open_bracket", tokens.get(idx).string("cat", null));
      assertEquals("tagname", tokens.get(idx + 1).string("cat", null));
      assertEquals("close_open", tokens.get(idx + 2).string("cat", null));
    }

    @Test
    void commentStateIsIsolatedFromContent() {
      final var input = "<div><!-- a-b --></div>";
      final var tokens = lexer.tokenizeSingle(input);

      boolean insideComment = false;
      for (final var t : tokens) {
        if ("comment_open".equals(t.string("cat", null))) insideComment = true;
        else if ("comment_close".equals(t.string("cat", null))) insideComment = false;
        else if (insideComment) {
          // While in COMMENT state, only comment_text tokens should appear
          assertEquals(
              "comment_text", t.string("cat", null), () -> "Unexpected token in COMMENT: " + t);
        }
      }
    }
  }
}
