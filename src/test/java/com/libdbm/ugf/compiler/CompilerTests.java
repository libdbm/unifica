package com.libdbm.ugf.compiler;

import static org.junit.jupiter.api.Assertions.*;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.constraints.Plan;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CompilerTests {

  private static final Predicates STANDARD = Predicates.standard();

  private static Grammar grammar(final String source) {
    return UnificationGrammarParserFactory.unvalidated(source).orElseThrow();
  }

  private static Compiled compile(final String source) {
    return Compiler.compile(grammar(source), STANDARD).orElseThrow();
  }

  private static ErrorDetails failure(final String source) {
    final var result = Compiler.compile(grammar(source), STANDARD);
    final var error = (ErrorDetails) assertInstanceOf(Result.Failure.class, result).error();
    assertEquals(Compiler.INVALID, error.code());
    return error;
  }

  private static List<Lexeme> lexemes(final Compiled compiled, final String category) {
    return compiled.lexemes().stream()
        .filter(lexeme -> lexeme.category().equals(category))
        .toList();
  }

  // Task 21: lexemes

  /**
   * Review 2: the interior regex of StringLiteral no longer becomes a token that swallows input.
   */
  @Test
  void testSimpleGrammarHasNoWholeInputLexeme() {
    final var source =
        UnificationGrammarParserFactory.unvalidated(Path.of("src/test/resources/simple.ug"))
            .orElseThrow();
    final var compiled = Compiler.compile(source, STANDARD).orElseThrow();

    for (final var lexeme : compiled.lexemes()) {
      final var matcher = lexeme.pattern().matcher("int x;");
      assertFalse(
          matcher.lookingAt() && matcher.end() == 6,
          "lexeme " + lexeme.category() + " swallows input");
    }
    assertTrue(compiled.lexical("StringLiteral"));
    assertTrue(
        lexemes(compiled, "StringLiteral").getFirst().pattern().matcher("\"a b\"").matches());
  }

  @Test
  void testStateAnnotationLowered() {
    final var compiled = compile("start s; s --> b; b --> {IN} 'y';");

    final var lexeme = lexemes(compiled, "b").getFirst();
    assertEquals(List.of("IN"), lexeme.states());
    assertTrue(lexeme.pattern().matcher("y").matches());
    assertEquals(1, compiled.lexemes().size());
  }

  @Test
  void testLexicalSequenceAtomic() {
    final var compiled = compile("start S; S --> T; T --> '\"' [a-z]* '\"';");

    assertEquals(1, compiled.lexemes().size());
    assertTrue(lexemes(compiled, "T").getFirst().pattern().matcher("\"abc\"").matches());
  }

  @Test
  void testAnonymousLexemesShared() {
    final var compiled = compile("start S; S --> 'a' X 'a'; X --> 'b';");

    final var anonymous = compiled.lexemes().stream().filter(Lexeme::anonymous).toList();
    assertEquals(1, anonymous.size());
    assertEquals("'a'", anonymous.getFirst().category());
    final var rhs = compiled.productions("S").getFirst().rhs();
    assertEquals(new Element.Terminal("'a'", null), rhs.getFirst());
    assertEquals(new Element.Symbol("X", null, Structure.EMPTY), rhs.get(1));
  }

  @Test
  void testLabelsBecomeGroups() {
    final var lexeme = lexemes(compile("start S; S --> T; T --> [a-z]+:w '!';"), "T").getFirst();

    final var matcher = lexeme.pattern().matcher("abc!");
    assertTrue(matcher.matches());
    assertEquals(List.of("w"), lexeme.labels());
    assertEquals("abc", matcher.group(Lexeme.group(0)));
  }

  @Test
  void testTransitionAndCost() {
    final var lexeme = lexemes(compile("start S; S --> o; o --> '<' ==> IN @2;"), "o").getFirst();

    assertEquals("IN", lexeme.transition());
    assertEquals(2, lexeme.cost());
  }

  @Test
  void testSequenceOfLiteralsIsSyntactic() {
    final var compiled = compile("start A; A --> 'a' 'b';");

    assertFalse(compiled.lexical("A"));
    assertEquals(2, compiled.productions("A").getFirst().rhs().size());
  }

  // Task 22: lowering, ids, nullable

  @Test
  void testRepetitionLowered() {
    final var compiled = compile("start S; S --> A+; A --> 'a';");

    final var rhs = compiled.productions("S").getFirst().rhs();
    final var aux = assertInstanceOf(Element.Symbol.class, rhs.getFirst()).name();
    assertTrue(aux.startsWith("A_plus_"), aux);
    assertEquals(2, compiled.productions(aux).size());
    assertTrue(compiled.productions(aux).stream().allMatch(Production::auxiliary));
  }

  @Test
  void testAlternationOfNonterminals() {
    final var compiled = compile("start S; S --> (A | B); A --> 'a'; B --> 'b';");

    final var aux = ((Element.Symbol) compiled.productions("S").getFirst().rhs().getFirst()).name();
    final var options = compiled.productions(aux);
    assertEquals(2, options.size());
    assertEquals("A", ((Element.Symbol) options.get(0).rhs().getFirst()).name());
  }

  @Test
  void testIdsStable() {
    final var source = "start S; S --> A+ 'x'; S --> (A | 'y'); A --> 'a';";
    final var first = compile(source);
    final var second = compile(source);

    assertEquals(first.productions(), second.productions());
    assertEquals(
        first.lexemes().stream()
            .map(lexeme -> lexeme.id() + lexeme.category() + lexeme.pattern())
            .toList(),
        second.lexemes().stream()
            .map(lexeme -> lexeme.id() + lexeme.category() + lexeme.pattern())
            .toList());
    for (var i = 0; i < first.productions().size(); i++) {
      assertEquals(i, first.productions().get(i).id());
    }
  }

  @Test
  void testNullable() {
    final var compiled = compile("start S; S --> A A; A --> E?; E --> 'a';");

    assertTrue(compiled.nullable("S"));
    assertTrue(compiled.nullable("A"));
    assertFalse(compiled.nullable("E"));
  }

  @Test
  void testRecursiveNullable() {
    final var compiled = compile("start S; S --> S S; S --> T?; T --> 'a';");

    assertTrue(compiled.nullable("S"));
  }

  // Task 23: validation

  @Test
  void testUnknownPredicatesAllReported() {
    final var error = failure("start S; S --> A where nope('a'), (also():3); A --> 'a';");

    assertEquals(2, error.issues().size());
    assertTrue(error.issues().get(0).contains("nope"), error.issues().toString());
    assertTrue(error.issues().get(1).contains("also"), error.issues().toString());
  }

  @Test
  void testWrongArity() {
    assertTrue(
        failure("start S; S --> A where equals('a'); A --> 'a';")
            .issues()
            .getFirst()
            .contains("equals"));
  }

  @Test
  void testUndefinedSymbol() {
    assertTrue(failure("start S; S --> A B; A --> 'a';").issues().getFirst().contains("B"));
  }

  @Test
  void testWeightUnderOr() {
    failure("start S; S --> A where (equals('a', 'a'):3 | equals('b', 'b')); A --> 'a';");
  }

  /**
   * S-C7: in a syntactic production a lexical predicate reads the state where the constituent
   * starts.
   */
  @Test
  void testLexicalPredicateInSyntacticProduction() {
    assertEquals(
        1, compile("start S; S --> A where in_state('X'); A --> 'a';").productions().size());
  }

  @Test
  void testInvalidConstantRegex() {
    failure("start S; S --> W:w where matches(w, '['); W --> 'x';");
  }

  @Test
  void testInvalidGrammarRegexIsSyntaxFailure() {
    final var result = UnificationGrammarParserFactory.unvalidated("start S; S --> [a-;");

    assertInstanceOf(Result.Failure.class, result);
  }

  @Test
  void testSkipMustNameLexicalProduction() {
    assertTrue(
        failure("start S; skip Missing; S --> 'a';").issues().getFirst().contains("Missing"));
    assertEquals(Set.of("C"), compile("start S; skip C; S --> 'a'; C --> '#' [a-z]*;").skips());
  }

  @Test
  void testCustomPredicates() {
    final var predicates =
        Predicates.builder()
            .builtins()
            .add("bound", 1, 1, Predicates.Phase.SYNTACTIC, (environment, args) -> true)
            .build();

    final var result =
        Compiler.compile(grammar("start S; S --> A:a where bound(a):1; A --> 'a';"), predicates);

    assertInstanceOf(Result.Success.class, result);
  }

  @Test
  void testEveryResourceGrammarCompiles() throws IOException {
    final var unloadable =
        Path.of("src/test/resources/conformance/constraint-penalty-overflow/grammar.ug");
    try (final Stream<Path> files = Files.walk(Path.of("src/test/resources"))) {
      for (final var file :
          files
              .filter(path -> path.toString().endsWith(".ug") && !path.equals(unloadable))
              .toList()) {
        final var expected = file.resolveSibling("expected.json");
        if (Files.exists(expected)
            && Files.readString(expected).replaceAll("\\s", "").contains("\"ok\":false")) {
          continue;
        }
        final var source = UnificationGrammarParserFactory.unvalidated(file).orElseThrow();
        final var result = Compiler.compile(source, STANDARD);
        assertInstanceOf(Result.Success.class, result, file + ": " + result);
      }
    }
  }

  /** CON-11: lowering groups and repetition never moves a call into or out of a soft group. */
  @Test
  void testLoweringKeepsSoftGroups() {
    final var source =
        "start S; S --> A (B | C D)* where (equals('a', 'a'), equals('b', 'b')):3, equals('c', 'c');"
            + " A --> 'a'; B --> 'b'; C --> 'c'; D --> 'd';";
    final var compiled = compile(source);
    final var expected =
        Plan.of(grammar(source).rulesFor("S").getFirst().constraints()).orElseThrow();

    final var root =
        compiled.productions().stream()
            .filter(production -> production.symbol().equals("S"))
            .toList();
    assertEquals(List.of(expected), root.stream().map(Production::plan).toList());
    assertEquals(1, expected.required().size());
    assertEquals(3, expected.soft().getFirst().weight());
    assertTrue(
        compiled.productions().stream()
            .filter(Production::auxiliary)
            .allMatch(production -> production.plan().isEmpty()));
  }
}
