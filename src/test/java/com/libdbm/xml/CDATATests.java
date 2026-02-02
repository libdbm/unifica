package com.libdbm.xml;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.grammar.GrammarNormalizer;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.parser.ChartParser;
import com.libdbm.ugf.parser.LexicalAnalyzer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Tests for CDATA section support in XML parser */
class CDATATests {

  private static ChartParser parser;

  @BeforeAll
  static void setup() throws Exception {
    final var resource = XMLParserTests.class.getClassLoader().getResource("xml.ug");
    if (resource == null) {
      throw new RuntimeException("Unable to find XML grammar");
    }
    try (final var stream = resource.openStream()) {
      final var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      final var grammar =
          GrammarNormalizer.normalize(UnificationGrammarParserFactory.parse(content));
      // Do not skip whitespace in XML - preserves CDATA content exactly
      final var lexer = LexicalAnalyzer.build(grammar, false);
      parser = new ChartParser(grammar, lexer);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to load XML grammar", e);
    }
  }

  @Test
  void testEmptyCDATA() {
    final var input = "<data><![CDATA[]]></data>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    assertInstanceOf(XMLNode.Element.class, node);

    final var element = (XMLNode.Element) node;
    assertEquals("data", element.tag());
    assertInstanceOf(XMLNode.Content.CDATA.class, element.content());

    final var cdata = (XMLNode.Content.CDATA) element.content();
    assertEquals("", cdata.value());
  }

  @Test
  void testCDATAWithText() {
    final var input = "<data><![CDATA[Hello World]]></data>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var cdata = (XMLNode.Content.CDATA) element.content();

    // CDATA preserves exact whitespace
    assertEquals("Hello World", cdata.value());
  }

  @Test
  void testCDATAWithXMLSpecialChars() {
    final var input = "<data><![CDATA[<tag>content</tag>]]></data>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var cdata = (XMLNode.Content.CDATA) element.content();

    // CDATA should preserve < and > characters
    assertEquals("<tag>content</tag>", cdata.value());
  }

  @Test
  void testCDATAWithAmpersand() {
    final var input = "<data><![CDATA[A & B]]></data>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var cdata = (XMLNode.Content.CDATA) element.content();

    // CDATA preserves exact content including spaces
    assertEquals("A & B", cdata.value());
  }

  @Test
  void testCDATAWithQuotes() {
    final var input = "<data><![CDATA[\"quoted\"]]></data>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var cdata = (XMLNode.Content.CDATA) element.content();

    assertEquals("\"quoted\"", cdata.value());
  }

  @Test
  void testCDATAWithScript() {
    final var input = "<script><![CDATA[function test() { return x < y; }]]></script>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var cdata = (XMLNode.Content.CDATA) element.content();

    assertTrue(cdata.value().contains("<"));
    assertTrue(cdata.value().contains("{"));
    assertTrue(cdata.value().contains("}"));
  }

  @Test
  void testCDATASerialization() {
    final var input = "<data><![CDATA[<b>Bold</b>]]></data>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var serializer = new XMLSerializer(false, 0);
    final var serialized = serializer.serialize(node);

    assertTrue(serialized.contains("<![CDATA["));
    assertTrue(serialized.contains("]]>"));
    assertTrue(serialized.contains("<b>Bold</b>"));
    assertFalse(serialized.contains("&lt;"), "CDATA content should not be escaped");
  }

  @Test
  void testCDATARoundTrip() {
    final var input = "<root><![CDATA[<tag attr=\"val\">Text & more</tag>]]></root>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var serializer = new XMLSerializer(false, 0);
    final var serialized = serializer.serialize(node);

    // Parse the serialized output
    final var result2 = parser.parse(serialized).toOptional();
    assertTrue(result2.isPresent(), "Second parse should succeed");

    final var node2 = XMLBuilder.build(result2.get(), serialized);

    // Both should have CDATA content
    final var elem1 = (XMLNode.Element) node;
    final var elem2 = (XMLNode.Element) node2;

    assertInstanceOf(XMLNode.Content.CDATA.class, elem1.content());
    assertInstanceOf(XMLNode.Content.CDATA.class, elem2.content());

    // Content should match
    final var content1 = ((XMLNode.Content.CDATA) elem1.content()).value();
    final var content2 = ((XMLNode.Content.CDATA) elem2.content()).value();
    assertEquals(content1, content2);
  }

  @Test
  void testCDATAPreservesMultipleSpaces() {
    final var input = "<data><![CDATA[Hello     World]]></data>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var cdata = (XMLNode.Content.CDATA) element.content();

    // CDATA should preserve EXACT whitespace - 5 spaces
    assertEquals(
        "Hello     World",
        cdata.value(),
        "CDATA should preserve exact whitespace, got: '" + cdata.value() + "'");
  }

  @Test
  void testCDATAPreservesNewlines() {
    final var input = "<data><![CDATA[Line1\nLine2\nLine3]]></data>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var cdata = (XMLNode.Content.CDATA) element.content();

    // CDATA should preserve exact newlines
    assertEquals(
        "Line1\nLine2\nLine3",
        cdata.value(),
        "CDATA should preserve newlines, got: '" + cdata.value() + "'");
  }

  @Test
  void testCDATAPreservesTabs() {
    final var input = "<data><![CDATA[Col1\tCol2\tCol3]]></data>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var cdata = (XMLNode.Content.CDATA) element.content();

    // CDATA should preserve exact tabs
    assertEquals(
        "Col1\tCol2\tCol3",
        cdata.value(),
        "CDATA should preserve tabs, got: '" + cdata.value() + "'");
  }

  @Test
  void testCDATAPreservesLeadingTrailingWhitespace() {
    final var input = "<data><![CDATA[  Hello  ]]></data>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var cdata = (XMLNode.Content.CDATA) element.content();

    // CDATA should preserve leading and trailing spaces
    assertEquals(
        "  Hello  ",
        cdata.value(),
        "CDATA should preserve leading/trailing whitespace, got: '" + cdata.value() + "'");
  }

  @Test
  void testCDATAPreservesComplexWhitespace() {
    final var input = "<data><![CDATA[  Line 1  \n\n  Line 2\t\tValue  ]]></data>";
    final var result = parser.parse(input).toOptional();

    assertTrue(result.isPresent(), "Parse should succeed");

    final var node = XMLBuilder.build(result.get(), input);
    final var element = (XMLNode.Element) node;
    final var cdata = (XMLNode.Content.CDATA) element.content();

    // CDATA should preserve ALL whitespace exactly
    assertEquals(
        "  Line 1  \n\n  Line 2\t\tValue  ",
        cdata.value(),
        "CDATA should preserve complex whitespace exactly, got: '" + cdata.value() + "'");
  }

  @Test
  void testMultipleCDATASections() {
    final var input = "<p><![CDATA[first]]>text<![CDATA[second]]></p>";

    final var result = parser.parse(input).toOptional();
    assertTrue(result.isPresent(), "Should parse document with multiple CDATA sections");

    final var xml = XMLBuilder.build(result.get(), input);
    assertInstanceOf(XMLNode.Element.class, xml);

    final var element = (XMLNode.Element) xml;
    assertEquals("p", element.tag());
    assertInstanceOf(XMLNode.Content.Mixed.class, element.content());

    final var mixed = (XMLNode.Content.Mixed) element.content();
    assertEquals(3, mixed.items().size(), "Should have 3 content items");

    // First CDATA
    assertInstanceOf(XMLNode.ContentItem.CDATA.class, mixed.items().get(0));
    assertEquals("first", ((XMLNode.ContentItem.CDATA) mixed.items().get(0)).value());

    // Text
    assertInstanceOf(XMLNode.ContentItem.Text.class, mixed.items().get(1));
    assertEquals("text", ((XMLNode.ContentItem.Text) mixed.items().get(1)).value());

    // Second CDATA
    assertInstanceOf(XMLNode.ContentItem.CDATA.class, mixed.items().get(2));
    assertEquals("second", ((XMLNode.ContentItem.CDATA) mixed.items().get(2)).value());
  }

  @Test
  void testCDATAWithSpecialCharacters() {
    final var input = "<code><![CDATA[<html>&entities</body>]]></code>";

    final var result = parser.parse(input).toOptional();
    assertTrue(result.isPresent(), "Should parse CDATA with special chars");

    final var xml = XMLBuilder.build(result.get(), input);
    assertInstanceOf(XMLNode.Element.class, xml);

    final var element = (XMLNode.Element) xml;
    assertInstanceOf(XMLNode.Content.CDATA.class, element.content());

    final var cdata = (XMLNode.Content.CDATA) element.content();
    assertEquals("<html>&entities</body>", cdata.value());
  }

  @Test
  void testMixedCDATAAndTextElement() {
    final var input = "<div><![CDATA[cdata1]]>some text<em/><![CDATA[cdata2]]></div>";

    final var result = parser.parse(input).toOptional();
    assertTrue(result.isPresent(), "Should parse CDATA, text, and elements mixed");

    final var xml = XMLBuilder.build(result.get(), input);
    assertInstanceOf(XMLNode.Element.class, xml);

    final var element = (XMLNode.Element) xml;
    assertInstanceOf(XMLNode.Content.Mixed.class, element.content());

    final var mixed = (XMLNode.Content.Mixed) element.content();
    assertEquals(4, mixed.items().size());

    // CDATA, text, element, CDATA
    assertInstanceOf(XMLNode.ContentItem.CDATA.class, mixed.items().get(0));
    assertEquals("cdata1", ((XMLNode.ContentItem.CDATA) mixed.items().get(0)).value());

    assertInstanceOf(XMLNode.ContentItem.Text.class, mixed.items().get(1));
    assertEquals("some text", ((XMLNode.ContentItem.Text) mixed.items().get(1)).value());

    assertInstanceOf(XMLNode.ContentItem.Element.class, mixed.items().get(2));

    assertInstanceOf(XMLNode.ContentItem.CDATA.class, mixed.items().get(3));
    assertEquals("cdata2", ((XMLNode.ContentItem.CDATA) mixed.items().get(3)).value());
  }
}
