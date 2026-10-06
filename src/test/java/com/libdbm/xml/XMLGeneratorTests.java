package com.libdbm.xml;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.generator.GrammarGenerator;
import com.libdbm.ugf.generator.LiteralTerminalGenerator;
import com.libdbm.ugf.generator.RegexTerminalGenerator;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.parser.Parser;
import com.libdbm.ugf.parser.ParserFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for generating XML documents using {@link GrammarGenerator}. */
final class XMLGeneratorTests {

  private Grammar grammar;
  private GrammarGenerator generator;
  private Parser parser;

  @BeforeEach
  void setUp() throws Exception {
    // Load XML grammar
    final var path = Path.of(XMLGeneratorTests.class.getResource("/xml.ug").getPath());
    grammar = UnificationGrammarParserFactory.parse(path).orElseThrow();

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
            // Whitespace and attribute text: without these the literal fallback emits the regex
            // source, which can never parse, and 2.0 returns only sentences that parse (S-N1).
            .register("[ \\t\\n\\r]+", features -> " ")
            .register("[^\"<&]+", features -> "value")
            .fallback(new LiteralTerminalGenerator())
            .build();

    generator =
        GrammarGenerator.builder(grammar)
            .terminal(terminalGen)
            .random(random)
            .maxDepth(10)
            .build()
            .orElseThrow();
    parser = ParserFactory.create(grammar).orElseThrow();
  }

  @Test
  void testGenerateSingleDocument() {
    final var result =
        generator
            .generateOne("document", Structure.EMPTY)
            .map(Optional::of)
            .orElse(Optional.empty());

    assertTrue(result.isPresent(), "Should generate a document");
    assertFalse(result.get().isEmpty(), "Document should not be empty");

    System.out.println("Generated document: " + result.get());
  }

  @Test
  void testGenerateMultipleDocuments() {
    final var documents = generator.generate("document", Structure.EMPTY, 5).orElse(List.of());

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
        GrammarGenerator.builder(grammar)
            .terminal(
                RegexTerminalGenerator.builder()
                    .register("[a-zA-Z][a-zA-Z0-9_\\-]*", f -> "div")
                    .register("[a-zA-Z0-9_\\-]+", f -> "text")
                    .fallback(new LiteralTerminalGenerator())
                    .build())
            .random(new Random(42))
            .maxDepth(3)
            .build()
            .orElseThrow(); // Shallow depth

    final var documents = shallowGen.generate("document", Structure.EMPTY, 5).orElse(List.of());

    for (final var doc : documents) {
      final var parseResult = parser.parse(doc).toOptional();
      assertTrue(parseResult.isPresent(), "Generated document should be parseable: " + doc);
    }
  }

  @Test
  void testGenerateNormalElement() {
    final var elements = generator.generate("normal_element", Structure.EMPTY, 5).orElse(List.of());

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
    final var elements = generator.generate("empty_element", Structure.EMPTY, 5).orElse(List.of());

    assertEquals(5, elements.size(), "Should generate 5 empty elements");

    for (final var element : elements) {
      System.out.println("Generated empty element: " + element);
      assertTrue(element.contains("<"), "Should contain opening tag");
      assertTrue(element.contains("/>"), "Should be self-closing");
    }
  }

  @Test
  void testGenerateTagNames() {
    final var tagnames = generator.generate("tagname", Structure.EMPTY, 5).orElse(List.of());

    assertEquals(5, tagnames.size(), "Should generate 5 tag names");

    for (final var tagname : tagnames) {
      System.out.println("Generated tagname: " + tagname);
      assertTrue(
          tagname.matches("[a-zA-Z][a-zA-Z0-9_-]*"), "Should be a valid tag name: " + tagname);
    }
  }

  @Test
  void testGenerateAttributes() {
    final var attributes = generator.generate("attributes", Structure.EMPTY, 3).orElse(List.of());

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
    final var documents = generator.generate("document", Structure.EMPTY, 10).orElse(List.of());

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
    final var documents = generator.generate("document", Structure.EMPTY, 3).orElse(List.of());

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
    final var gen1 =
        GrammarGenerator.builder(grammar)
            .terminal(new LiteralTerminalGenerator())
            .random(random1)
            .maxDepth(10)
            .build()
            .orElseThrow();

    final var random2 = new Random(123);
    final var gen2 =
        GrammarGenerator.builder(grammar)
            .terminal(new LiteralTerminalGenerator())
            .random(random2)
            .maxDepth(10)
            .build()
            .orElseThrow();

    final var doc1 =
        gen1.generateOne("document", Structure.EMPTY).map(Optional::of).orElse(Optional.empty());
    final var doc2 =
        gen2.generateOne("document", Structure.EMPTY).map(Optional::of).orElse(Optional.empty());

    assertEquals(doc1, doc2, "Same seed should produce same result");
  }

  /**
   * Text content only lexes in the CONTENT state, entered after '>', so a bare textcontent never
   * parses as one (S-N1): 1.x returned such strings unchecked; 2.0 reports the failure instead.
   * Text content still appears inside generated documents.
   */
  @Test
  void testGenerateTextContent() {
    final var standalone = generator.generate("textcontent", Structure.EMPTY, 5);

    final var error = assertInstanceOf(Result.Failure.class, standalone).error();
    assertTrue(((ErrorDetails) error).message().contains("does not parse"), String.valueOf(error));

    final var documents = generator.generate("document", Structure.EMPTY, 10).orElse(List.of());
    assertFalse(documents.isEmpty(), "documents should generate");
    assertTrue(
        documents.stream().anyMatch(document -> document.matches("(?s).*>[^<]+<.*")),
        () -> "some document should contain text content: " + documents);
  }

  @Test
  void testGenerateAttributeValues() {
    final var values = generator.generate("attrvalue", Structure.EMPTY, 5).orElse(List.of());

    assertEquals(5, values.size(), "Should generate 5 attribute values");

    for (final var value : values) {
      System.out.println("Generated attribute value: " + value);
      assertTrue(value.startsWith("\""), "Should start with quote");
      assertTrue(value.endsWith("\""), "Should end with quote");
    }
  }
}
