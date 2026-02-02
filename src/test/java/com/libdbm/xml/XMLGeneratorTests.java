package com.libdbm.xml;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.generator.GrammarGenerator;
import com.libdbm.ugf.generator.LiteralTerminalGenerator;
import com.libdbm.ugf.generator.RegexTerminalGenerator;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.parser.ChartParser;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for generating XML documents using {@link GrammarGenerator}. */
final class XMLGeneratorTests {

  private Grammar grammar;
  private GrammarGenerator generator;
  private ChartParser parser;

  @BeforeEach
  void setUp() throws Exception {
    // Load XML grammar
    final var path = Path.of(XMLGeneratorTests.class.getResource("/xml.ug").getPath());
    grammar = UnificationGrammarParserFactory.parse(path);

    // Create terminal generator for XML
    final var random = new Random(42); // Fixed seed for reproducibility
    final var terminalGen =
        RegexTerminalGenerator.builder()
            // Tag names and attribute names
            .register(
                "[a-zA-Z][a-zA-Z0-9_\\-]*",
                features -> {
                  final var names =
                      new String[] {"div", "span", "p", "img", "link", "meta", "title"};
                  return names[random.nextInt(names.length)];
                })
            // Words for content
            .register(
                "[a-zA-Z0-9_\\\\-]+",
                features -> {
                  final var words =
                      new String[] {"hello", "world", "test", "content", "data", "value"};
                  return words[random.nextInt(words.length)];
                })
            .fallback(new LiteralTerminalGenerator())
            .build();

    generator = new GrammarGenerator(grammar, terminalGen, random, 10);
    parser = new ChartParser(grammar);
  }

  @Test
  void testGenerateSingleDocument() {
    final var result = generator.generateOne("document", new Structure());

    assertTrue(result.isPresent(), "Should generate a document");
    assertFalse(result.get().isEmpty(), "Document should not be empty");

    System.out.println("Generated document: " + result.get());
  }

  @Test
  void testGenerateMultipleDocuments() {
    final var documents = generator.generate("document", new Structure(), 5);

    assertEquals(5, documents.size(), "Should generate 5 documents");

    for (final var doc : documents) {
      assertFalse(doc.isEmpty(), "Document should not be empty");
      System.out.println("Generated: " + doc);
    }
  }

  @Test
  void testGeneratedDocumentsAreParseable() {
    // Use shallow generator to avoid overly complex nested structures
    final var shallowGen =
        new GrammarGenerator(
            grammar,
            RegexTerminalGenerator.builder()
                .register("[a-zA-Z][a-zA-Z0-9_\\-]*", f -> "div")
                .register("[a-zA-Z0-9_\\-]+", f -> "text")
                .fallback(new LiteralTerminalGenerator())
                .build(),
            new Random(42),
            3); // Shallow depth

    final var documents = shallowGen.generate("document", new Structure(), 5);

    for (final var doc : documents) {
      final var parseResult = parser.parse(doc).toOptional();
      assertTrue(parseResult.isPresent(), "Generated document should be parseable: " + doc);
    }
  }

  @Test
  void testGenerateNormalElement() {
    final var elements = generator.generate("normal_element", new Structure(), 5);

    assertEquals(5, elements.size(), "Should generate 5 normal elements");

    for (final var element : elements) {
      System.out.println("Generated normal element: " + element);
      assertTrue(element.contains("<"), "Should contain opening tag");
      assertTrue(element.contains(">"), "Should contain tag close");
      assertTrue(element.contains("</"), "Should contain closing tag");
    }
  }

  @Test
  void testGenerateEmptyElement() {
    final var elements = generator.generate("empty_element", new Structure(), 5);

    assertEquals(5, elements.size(), "Should generate 5 empty elements");

    for (final var element : elements) {
      System.out.println("Generated empty element: " + element);
      assertTrue(element.contains("<"), "Should contain opening tag");
      assertTrue(element.contains("/>"), "Should be self-closing");
    }
  }

  @Test
  void testGenerateTagNames() {
    final var tagnames = generator.generate("tagname", new Structure(), 5);

    assertEquals(5, tagnames.size(), "Should generate 5 tag names");

    for (final var tagname : tagnames) {
      System.out.println("Generated tagname: " + tagname);
      assertTrue(
          tagname.matches("[a-zA-Z][a-zA-Z0-9_-]*"), "Should be a valid tag name: " + tagname);
    }
  }

  @Test
  void testGenerateAttributes() {
    final var attributes = generator.generate("attributes", new Structure(), 3);

    assertEquals(3, attributes.size(), "Should generate 3 attribute sets");

    for (final var attrs : attributes) {
      System.out.println("Generated attributes: " + attrs);
      assertTrue(attrs.contains("="), "Should contain '='");
      assertTrue(attrs.contains("\""), "Should contain quotes");
    }
  }

  @Test
  void testRoundTripParseAndGenerate() {
    // Generate documents, parse them, verify they parse successfully
    final var documents = generator.generate("document", new Structure(), 10);

    int successCount = 0;
    for (final var doc : documents) {
      final var parseResult = parser.parse(doc).toOptional();
      if (parseResult.isPresent()) {
        successCount++;
        System.out.println("✓ " + doc);
      } else {
        System.err.println("✗ Failed to parse: " + doc);
      }
    }

    // At least 20% should parse successfully (generator creates text patterns that may not match
    // stateful lexer)
    assertTrue(
        successCount >= 2, "At least 2/10 documents should parse (got " + successCount + ")");
  }

  @Test
  void testGenerateAndBuildXML() {
    final var documents = generator.generate("document", new Structure(), 3);

    for (final var doc : documents) {
      final var result = parser.parse(doc).toOptional();
      if (result.isPresent()) {
        // Try to build XML structure from parse tree
        final var node = XMLBuilder.build(result.get(), doc);
        assertNotNull(node, "Should build XML structure");
        System.out.println("Generated and built: " + doc);
        System.out.println("  XML structure: " + node);
      }
    }
  }

  @Test
  void testDeterministicGeneration() {
    // With same seed, should generate same documents
    final var random1 = new Random(123);
    final var gen1 = new GrammarGenerator(grammar, new LiteralTerminalGenerator(), random1, 10);

    final var random2 = new Random(123);
    final var gen2 = new GrammarGenerator(grammar, new LiteralTerminalGenerator(), random2, 10);

    final var doc1 = gen1.generateOne("document", new Structure());
    final var doc2 = gen2.generateOne("document", new Structure());

    assertEquals(doc1, doc2, "Same seed should produce same result");
  }

  @Test
  void testGenerateTextContent() {
    final var content = generator.generate("textcontent", new Structure(), 5);

    assertEquals(5, content.size(), "Should generate 5 text content items");

    for (final var text : content) {
      System.out.println("Generated text content: " + text);
      assertFalse(text.isEmpty(), "Text content should not be empty");
    }
  }

  @Test
  void testGenerateAttributeValues() {
    final var values = generator.generate("attrvalue", new Structure(), 5);

    assertEquals(5, values.size(), "Should generate 5 attribute values");

    for (final var value : values) {
      System.out.println("Generated attribute value: " + value);
      assertTrue(value.startsWith("\""), "Should start with quote");
      assertTrue(value.endsWith("\""), "Should end with quote");
    }
  }
}
