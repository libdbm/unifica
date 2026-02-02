package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.constraints.Builtins;
import com.libdbm.ugf.constraints.Context;
import com.libdbm.ugf.constraints.Predicate;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.RuleElement;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for LexicalAnalyzer - grammar-driven lexical analysis. */
class LexicalAnalyzerTests {

  @Nested
  class RuleExtraction {

    @Test
    void extractsTerminalRules() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("keyword", List.of(new RuleElement.Terminal("if"))))
              .add(new GrammarRule("keyword", List.of(new RuleElement.Terminal("else"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var rules = lexer.rules();

      assertEquals(2, rules.size());
      assertTrue(rules.stream().anyMatch(r -> r.text().equals("if")));
      assertTrue(rules.stream().anyMatch(r -> r.text().equals("else")));
    }

    @Test
    void extractsRegexRules() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("number", List.of(new RuleElement.Regex("[0-9]+"))))
              .add(new GrammarRule("ident", List.of(new RuleElement.Regex("[a-z]+"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var rules = lexer.rules();

      assertEquals(2, rules.size());
      assertTrue(rules.stream().anyMatch(r -> r.text().equals("[0-9]+")));
      assertTrue(rules.stream().anyMatch(r -> r.text().equals("[a-z]+")));
    }

    @Test
    void extractsAlternationRules() {
      final var grammar =
          Grammar.builder()
              .add(
                  new GrammarRule(
                      "op",
                      List.of(
                          new RuleElement.Alternation(
                              List.of(
                                  new RuleElement.Terminal("+"),
                                  new RuleElement.Terminal("-"),
                                  new RuleElement.Terminal("*"))))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var rules = lexer.rules();

      assertEquals(3, rules.size());
      assertTrue(rules.stream().anyMatch(r -> r.text().equals("+")));
      assertTrue(rules.stream().anyMatch(r -> r.text().equals("-")));
      assertTrue(rules.stream().anyMatch(r -> r.text().equals("*")));
    }

    @Test
    void extractsRepetitionOfTerminal() {
      // Rule: dots --> '.'+ (one or more dots)
      final var grammar =
          Grammar.builder()
              .add(
                  new GrammarRule(
                      "dots",
                      List.of(
                          new RuleElement.Repetition(
                              new RuleElement.Terminal("."), RuleElement.Quantifier.ONE_OR_MORE))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var rules = lexer.rules();

      // 2 rules: the repetition pattern AND the inline terminal '.'
      assertEquals(2, rules.size());
      // Find the repetition rule (name = "dots")
      final var repRule = rules.stream().filter(r -> "dots".equals(r.name())).findFirst();
      assertTrue(repRule.isPresent());
      assertTrue(repRule.get().pattern().matcher("...").matches());
      assertFalse(repRule.get().pattern().matcher("").matches());
    }

    @Test
    void extractsRepetitionOfAlternation() {
      // Rule: signs --> ('+' | '-')* (zero or more signs)
      final var grammar =
          Grammar.builder()
              .add(
                  new GrammarRule(
                      "signs",
                      List.of(
                          new RuleElement.Repetition(
                              new RuleElement.Alternation(
                                  List.of(
                                      new RuleElement.Terminal("+"),
                                      new RuleElement.Terminal("-"))),
                              RuleElement.Quantifier.ZERO_OR_MORE))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var rules = lexer.rules();

      // 3 rules: the repetition pattern AND inline terminals '+' and '-'
      assertEquals(3, rules.size());
      final var repRule = rules.stream().filter(r -> "signs".equals(r.name())).findFirst();
      assertTrue(repRule.isPresent());
      assertTrue(repRule.get().pattern().matcher("+-+-").matches());
      assertTrue(repRule.get().pattern().matcher("").matches());
      assertFalse(repRule.get().pattern().matcher("*").matches());
    }

    @Test
    void extractsOptionalTerminal() {
      // Rule: opt --> 'x'? (optional x)
      final var grammar =
          Grammar.builder()
              .add(
                  new GrammarRule(
                      "opt",
                      List.of(
                          new RuleElement.Repetition(
                              new RuleElement.Terminal("x"), RuleElement.Quantifier.OPTIONAL))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var rules = lexer.rules();

      // 2 rules: the optional pattern AND the inline terminal 'x'
      assertEquals(2, rules.size());
      final var optRule = rules.stream().filter(r -> "opt".equals(r.name())).findFirst();
      assertTrue(optRule.isPresent());
      assertTrue(optRule.get().pattern().matcher("x").matches());
      assertTrue(optRule.get().pattern().matcher("").matches());
      assertFalse(optRule.get().pattern().matcher("xx").matches());
    }

    @Test
    void extractsFeatures() {
      final var features = Structure.builder().with("num", "sing").build();
      final var lhs = new GrammarRule.LHS("det", features);
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule(lhs, List.of(new RuleElement.Terminal("a")), List.of()))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var rules = lexer.rules();

      assertEquals(1, rules.size());
      final var rule = rules.getFirst();
      assertEquals("det", rule.name());
      assertEquals(new StringConstant("sing"), rule.features().get("num"));
    }

    @Test
    void ignoresNonterminalRules() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("expr", List.of(new RuleElement.Nonterminal("term"))))
              .add(
                  new GrammarRule(
                      "expr",
                      List.of(
                          new RuleElement.Nonterminal("expr"),
                          new RuleElement.Terminal("+"),
                          new RuleElement.Nonterminal("term"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var rules = lexer.rules();

      // Inline terminals (like '+' in mixed rules) ARE extracted to ensure proper tokenization
      assertEquals(1, rules.size());
      assertEquals("+", rules.getFirst().text());
    }

    @Test
    void extractsSequenceOfTerminals() {
      // Rule like: keyword --> '<!' 'CDATA' '['
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("cdata", List.of(new RuleElement.Terminal("<![CDATA["))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var rules = lexer.rules();

      assertEquals(1, rules.size());
      assertEquals("<![CDATA[", rules.getFirst().text());
    }

    @Test
    void deduplicatesIdenticalRules() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("a", List.of(new RuleElement.Terminal("the"))))
              .add(new GrammarRule("a", List.of(new RuleElement.Terminal("the"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);

      // Should only have one rule for 'the'
      assertEquals(1, lexer.rules().size());
    }
  }

  @Nested
  class Tokenization {

    @Test
    void tokenizesSimpleInput() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("word", List.of(new RuleElement.Regex("[a-z]+"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var tokens = lexer.tokenizeSingle("hello world");

      assertEquals(2, tokens.size());
      assertEquals("hello", tokens.get(0).text());
      assertEquals("world", tokens.get(1).text());
    }

    @Test
    void tokenizesWithPositions() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("word", List.of(new RuleElement.Regex("[a-z]+"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var tokens = lexer.tokenizeSingle("hello world");

      assertEquals(0, tokens.get(0).start());
      assertEquals(5, tokens.get(0).end());
      assertEquals(6, tokens.get(1).start());
      assertEquals(11, tokens.get(1).end());
    }

    @Test
    void assignsCategories() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("word", List.of(new RuleElement.Regex("[a-z]+"))))
              .add(new GrammarRule("number", List.of(new RuleElement.Regex("[0-9]+"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var tokens = lexer.tokenizeSingle("hello 42");

      assertEquals("word", tokens.get(0).string("cat", null));
      assertEquals("number", tokens.get(1).string("cat", null));
    }

    @Test
    void usesLongestMatch() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("keyword", List.of(new RuleElement.Terminal("if"))))
              .add(new GrammarRule("keyword", List.of(new RuleElement.Terminal("iffy"))))
              .add(new GrammarRule("ident", List.of(new RuleElement.Regex("[a-z]+"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var tokens = lexer.tokenizeSingle("iffy");

      assertEquals(1, tokens.size());
      assertEquals("iffy", tokens.getFirst().text());
      assertEquals("keyword", tokens.getFirst().string("cat", null));
    }

    @Test
    void literalsPrecedeRegexes() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("keyword", List.of(new RuleElement.Terminal("if"))))
              .add(new GrammarRule("ident", List.of(new RuleElement.Regex("[a-z]+"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var tokens = lexer.tokenizeSingle("if else");

      assertEquals(2, tokens.size());
      assertEquals("keyword", tokens.get(0).string("cat", null));
      assertEquals("ident", tokens.get(1).string("cat", null));
    }

    @Test
    void skipsWhitespace() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("word", List.of(new RuleElement.Regex("[a-z]+"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar, true);
      final var tokens = lexer.tokenizeSingle("  hello   world  ");

      assertEquals(2, tokens.size());
      assertEquals("hello", tokens.get(0).text());
      assertEquals("world", tokens.get(1).text());
    }

    @Test
    void preservesWhitespaceWhenConfigured() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("word", List.of(new RuleElement.Regex("[a-z]+"))))
              .add(new GrammarRule("space", List.of(new RuleElement.Regex("\\s+"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar, false);
      final var tokens = lexer.tokenizeSingle("hello world");

      assertEquals(3, tokens.size());
      assertEquals("hello", tokens.get(0).text());
      assertEquals(" ", tokens.get(1).text());
      assertEquals("world", tokens.get(2).text());
    }

    @Test
    void handlesUnmatchedCharacters() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("word", List.of(new RuleElement.Regex("[a-z]+"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var tokens = lexer.tokenizeSingle("hello@world");

      assertEquals(3, tokens.size());
      assertEquals("hello", tokens.get(0).text());
      assertEquals("@", tokens.get(1).text());
      assertNull(tokens.get(1).string("cat", null)); // Error token
      assertEquals("world", tokens.get(2).text());
    }

    @Test
    void tokenizesEmptyInput() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("word", List.of(new RuleElement.Regex("[a-z]+"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var tokens = lexer.tokenizeSingle("");

      assertTrue(tokens.isEmpty());
    }

    @Test
    void handlesZeroLengthMatch() {
      // Test that zero-length matches don't cause infinite loops
      // The x? pattern can match empty at position 0, but we prevent that
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("opt", List.of(new RuleElement.Regex("x?"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      // Should not infinite loop - zero-length matches are treated as error tokens
      final var tokens = lexer.tokenizeSingle("abc");

      // Each character becomes an error token since 'x?' matches empty at each position
      // but we reject zero-length matches and emit error tokens instead
      assertFalse(tokens.isEmpty());
      assertEquals(3, tokens.size()); // Individual error chars 'a', 'b', 'c'
    }

    @Test
    void tokenizesWhitespaceOnlyInput() {
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule("word", List.of(new RuleElement.Regex("[a-z]+"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var tokens = lexer.tokenizeSingle("   ");

      assertTrue(tokens.isEmpty());
    }

    @Test
    void preservesFeaturesOnTokens() {
      final var features = Structure.builder().with("num", "sing").build();
      final var lhs = new GrammarRule.LHS("det", features);
      final var grammar =
          Grammar.builder()
              .add(new GrammarRule(lhs, List.of(new RuleElement.Terminal("a")), List.of()))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var tokens = lexer.tokenizeSingle("a");

      assertEquals(1, tokens.size());
      assertTrue(tokens.getFirst().hasFeatures());
      assertEquals("sing", tokens.getFirst().string("num", null));
    }
  }

  @Nested
  class MathGrammar {

    private Grammar grammar() {
      return Grammar.builder()
          .start("expr")
          // Operators
          .add(new GrammarRule("op", List.of(new RuleElement.Terminal("+"))))
          .add(new GrammarRule("op", List.of(new RuleElement.Terminal("-"))))
          .add(new GrammarRule("op", List.of(new RuleElement.Terminal("*"))))
          .add(new GrammarRule("op", List.of(new RuleElement.Terminal("/"))))
          .add(new GrammarRule("op", List.of(new RuleElement.Terminal("^"))))
          // Parentheses
          .add(new GrammarRule("lparen", List.of(new RuleElement.Terminal("("))))
          .add(new GrammarRule("rparen", List.of(new RuleElement.Terminal(")"))))
          // Numbers
          .add(new GrammarRule("number", List.of(new RuleElement.Regex("[0-9]+"))))
          .add(new GrammarRule("number", List.of(new RuleElement.Regex("[0-9]+\\.[0-9]+"))))
          // Variables
          .add(
              new GrammarRule("variable", List.of(new RuleElement.Regex("[a-zA-Z_][a-zA-Z0-9_]*"))))
          .build();
    }

    @Test
    void tokenizesSimpleExpression() {
      final var lexer = LexicalAnalyzer.build(grammar());
      final var tokens = lexer.tokenizeSingle("2 + 3");

      assertEquals(3, tokens.size());
      assertEquals("number", tokens.get(0).string("cat", null));
      assertEquals("op", tokens.get(1).string("cat", null));
      assertEquals("number", tokens.get(2).string("cat", null));
    }

    @Test
    void tokenizesComplexExpression() {
      final var lexer = LexicalAnalyzer.build(grammar());
      final var tokens = lexer.tokenizeSingle("(x + 2) * 3.14");

      assertEquals(7, tokens.size());
      assertEquals("lparen", tokens.get(0).string("cat", null));
      assertEquals("variable", tokens.get(1).string("cat", null));
      assertEquals("op", tokens.get(2).string("cat", null));
      assertEquals("number", tokens.get(3).string("cat", null));
      assertEquals("rparen", tokens.get(4).string("cat", null));
      assertEquals("op", tokens.get(5).string("cat", null));
      assertEquals("number", tokens.get(6).string("cat", null));
      assertEquals("3.14", tokens.get(6).text());
    }

    @Test
    void handlesNegativeNumbers() {
      final var lexer = LexicalAnalyzer.build(grammar());
      final var tokens = lexer.tokenizeSingle("-5");

      // With this grammar, - and 5 are separate tokens
      assertEquals(2, tokens.size());
      assertEquals("op", tokens.get(0).string("cat", null));
      assertEquals("number", tokens.get(1).string("cat", null));
    }
  }

  @Nested
  class XMLGrammar {

    private Grammar grammar() {
      return Grammar.builder()
          .start("element")
          // Tags - multi-character first for longest match
          .add(new GrammarRule("ltag_close", List.of(new RuleElement.Terminal("</"))))
          .add(new GrammarRule("empty_close", List.of(new RuleElement.Terminal("/>"))))
          .add(new GrammarRule("ltag", List.of(new RuleElement.Terminal("<"))))
          .add(new GrammarRule("rtag", List.of(new RuleElement.Terminal(">"))))
          .add(new GrammarRule("eq", List.of(new RuleElement.Terminal("="))))
          .add(new GrammarRule("quote", List.of(new RuleElement.Terminal("\""))))
          // Names - don't include / in the pattern
          .add(new GrammarRule("name", List.of(new RuleElement.Regex("[a-zA-Z][a-zA-Z0-9_]*"))))
          // Content - text that appears between tags, excludes special chars and whitespace
          // to allow proper tokenization of attributes
          .add(new GrammarRule("text", List.of(new RuleElement.Regex("[^<>&=\"/\\s]+"))))
          .build();
    }

    @Test
    void tokenizesSimpleElement() {
      final var lexer = LexicalAnalyzer.build(grammar());
      final var tokens = lexer.tokenizeSingle("<div>hello</div>");

      assertEquals(7, tokens.size());
      assertEquals("<", tokens.get(0).text());
      assertEquals("div", tokens.get(1).text());
      assertEquals(">", tokens.get(2).text());
      assertEquals("hello", tokens.get(3).text());
      assertEquals("</", tokens.get(4).text());
      assertEquals("div", tokens.get(5).text());
      assertEquals(">", tokens.get(6).text());
    }

    @Test
    void tokenizesElementWithAttribute() {
      final var lexer = LexicalAnalyzer.build(grammar());
      final var tokens = lexer.tokenizeSingle("<a href=\"url\">");

      // Expected: < a href = " url " >
      assertEquals(8, tokens.size(), "Tokens: " + tokens.stream().map(Token::text).toList());
      assertEquals("<", tokens.get(0).text());
      assertEquals("a", tokens.get(1).text());
      assertEquals("href", tokens.get(2).text());
      assertEquals("=", tokens.get(3).text());
      assertEquals("\"", tokens.get(4).text());
      assertEquals("url", tokens.get(5).text());
      assertEquals("\"", tokens.get(6).text());
      assertEquals(">", tokens.get(7).text());
    }

    @Test
    void tokenizesEmptyElement() {
      final var lexer = LexicalAnalyzer.build(grammar());
      final var tokens = lexer.tokenizeSingle("<br/>");

      assertEquals(3, tokens.size());
      assertEquals("<", tokens.get(0).text());
      assertEquals("br", tokens.get(1).text());
      assertEquals("/>", tokens.get(2).text());
    }

    @Test
    void longestMatchSelectsClosingTag() {
      final var lexer = LexicalAnalyzer.build(grammar());
      final var tokens = lexer.tokenizeSingle("</div>");

      // '</' should be matched as a single token, not '<' then '/'
      assertEquals(3, tokens.size());
      assertEquals("</", tokens.getFirst().text());
      assertEquals("ltag_close", tokens.getFirst().string("cat", null));
    }
  }

  @Nested
  class StateBasedScanning {

    @Test
    void extractsStateTransitions() {
      // Rule with state transition: cdata_open --> '<![CDATA[' ==> CDATA
      final var lhs = new GrammarRule.LHS("cdata_open", new Structure());
      final var rhs =
          List.<RuleElement>of(new RuleElement.Terminal("<![CDATA[").transition("CDATA"));
      final var grammar = Grammar.builder().add(new GrammarRule(lhs, rhs, List.of())).build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var rules = lexer.rules();

      assertEquals(1, rules.size());
      assertEquals("CDATA", rules.getFirst().next());
    }

    @Test
    void stateTransitionUpdatesContext() {
      final var grammar =
          Grammar.builder()
              // Rule that transitions to CDATA state
              .add(
                  new GrammarRule(
                      new GrammarRule.LHS("cdata_open", new Structure()),
                      List.of(new RuleElement.Terminal("<![CDATA[").transition("CDATA")),
                      List.of(Predicate.of("in_state", List.of(StringConstant.of("DEFAULT"))))))
              // Rule to parse content in CDATA state
              .add(
                  new GrammarRule(
                      new GrammarRule.LHS("text", new Structure()),
                      List.of(new RuleElement.Regex("((?!]]>).)+")),
                      List.of(Predicate.of("in_state", List.of(StringConstant.of("CDATA"))))))
              // Rule that transitions back to DEFAULT state
              .add(
                  new GrammarRule(
                      new GrammarRule.LHS("cdata_close", new Structure()),
                      List.of(new RuleElement.Terminal("]]>").transition("_")),
                      List.of(Predicate.of("in_state", List.of(StringConstant.of("CDATA"))))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var context = new Context().withPredicates(Builtins.all());
      final var tokens = lexer.tokenizeSingle(context, "<![CDATA[some content]]>");

      // Should have all the tokens
      assertTrue(tokens.size() >= 2);
      assertEquals("<![CDATA[", tokens.get(0).text());
      assertEquals("some content", tokens.get(1).text());
      assertEquals("]]>", tokens.get(2).text());

      // State is now managed internally by LexicalAnalyzer during tokenization
      // After tokenization completes, the state stack has finished its purpose
    }

    @Test
    void extractsMultipleRulesWithDifferentStates() {
      final var grammar =
          Grammar.builder()
              // Regular content rule
              .add(
                  new GrammarRule(
                      new GrammarRule.LHS("text", new Structure()),
                      List.of(new RuleElement.Regex("[^<]+")),
                      List.of()))
              // CDATA start - transitions to CDATA state
              .add(
                  new GrammarRule(
                      new GrammarRule.LHS("cdata_start", new Structure()),
                      List.of(new RuleElement.Terminal("<![CDATA[").transition("CDATA")),
                      List.of()))
              // CDATA end - transitions back
              .add(
                  new GrammarRule(
                      new GrammarRule.LHS("cdata_end", new Structure()),
                      List.of(new RuleElement.Terminal("]]>").transition("!DEFAULT")),
                      List.of()))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var rules = lexer.rules();

      assertEquals(3, rules.size());

      // Verify state transitions are captured
      final var startRule = rules.stream().filter(r -> r.text().equals("<![CDATA[")).findFirst();
      assertTrue(startRule.isPresent());
      assertEquals("CDATA", startRule.get().next());

      final var endRule = rules.stream().filter(r -> r.text().equals("]]>")).findFirst();
      assertTrue(endRule.isPresent());
      assertEquals("!DEFAULT", endRule.get().next());
    }

    @Test
    void tokenizesCDATAContent() {
      final var grammar =
          Grammar.builder()
              // CDATA delimiters
              .add(
                  new GrammarRule(
                      new GrammarRule.LHS("cdata_open", new Structure()),
                      List.of(new RuleElement.Terminal("<![CDATA[").transition("CDATA")),
                      List.of()))
              .add(
                  new GrammarRule(
                      new GrammarRule.LHS("cdata_close", new Structure()),
                      List.of(new RuleElement.Terminal("]]>").transition("DEFAULT")),
                      List.of()))
              // Content - letters and spaces (simple for this test)
              .add(new GrammarRule("word", List.of(new RuleElement.Regex("[a-z]+"))))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var tokens = lexer.tokenizeSingle("<![CDATA[hello world]]>");

      // Tokens: <![CDATA[ hello world ]]>
      assertTrue(tokens.size() >= 4, "Expected at least 4 tokens, got: " + tokens.size());
      assertEquals("<![CDATA[", tokens.get(0).text());
      assertEquals("hello", tokens.get(1).text());
      assertEquals("world", tokens.get(2).text());
      assertEquals("]]>", tokens.getLast().text());
    }

    @Test
    void popStateTransition() {
      // Rule that pops state
      final var grammar =
          Grammar.builder()
              .add(
                  new GrammarRule(
                      new GrammarRule.LHS("close", new Structure()),
                      List.of(new RuleElement.Terminal("}").transition("_")),
                      List.of()))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var rules = lexer.rules();

      assertEquals(1, rules.size());
      assertEquals("_", rules.getFirst().next());
    }

    @Test
    void resetStateTransition() {
      // Rule that resets to specific state
      final var grammar =
          Grammar.builder()
              .add(
                  new GrammarRule(
                      new GrammarRule.LHS("reset", new Structure()),
                      List.of(new RuleElement.Terminal("!!!").transition("!INITIAL")),
                      List.of()))
              .build();

      final var lexer = LexicalAnalyzer.build(grammar);
      final var rules = lexer.rules();

      assertEquals(1, rules.size());
      assertEquals("!INITIAL", rules.getFirst().next());
    }
  }

  @Nested
  class LoadFromGrammarFile {

    @Test
    void loadsRulesFromMathGrammar() throws Exception {
      final var resource = getClass().getClassLoader().getResourceAsStream("math.ug");
      assertNotNull(resource, "math.ug should exist in test resources");

      final var content = new String(resource.readAllBytes());
      final var grammar =
          com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory.parse(content);

      final var lexer = LexicalAnalyzer.build(grammar);

      // Should have rules for operators, numbers, variables, etc.
      assertTrue(lexer.rules().size() > 5, "Should extract multiple lexical rules");

      // Should be able to tokenize math expressions
      final var tokens = lexer.tokenizeSingle("2 + 3 * x");
      assertTrue(tokens.size() >= 5, "Should tokenize math expression");
    }
  }
}
