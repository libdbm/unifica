package com.libdbm.ugf.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.libdbm.ugf.compiler.Compiler;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.lexer.TokenSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * S-F5 for {@code {TOKEN}}: features written on it unify with the token that fills it, as they do
 * for a nonterminal, so a grammar can read a caller token's features without a category for them.
 */
class TokenFeatureTests {

  private static Parser parser(final String source) {
    final var grammar = UnificationGrammarParserFactory.unvalidated(source).orElseThrow();
    return Parser.of(Compiler.compile(grammar, Predicates.standard()).orElseThrow());
  }

  /** Caller tokens with no category, each written {@code text/key=value/key=value}. */
  private static ParseResult parse(final Parser parser, final String... specs) {
    final var tokens = new ArrayList<Token>();
    var start = 0;
    for (final var spec : specs) {
      final var parts = spec.split("/");
      final var builder = Structure.builder();
      for (var index = 1; index < parts.length; index++) {
        final var pair = parts[index].split("=");
        builder.with(pair[0], new StringConstant(pair[1]));
      }
      tokens.add(new Token(parts[0], builder.build(), start, start + parts[0].length()));
      start += parts[0].length() + 1;
    }
    return parser.parse(TokenSource.of(List.copyOf(tokens)).tokenize("").orElseThrow());
  }

  private static Structure root(final ParseResult result) {
    return assertInstanceOf(ParseTree.Node.class, result.tree()).features();
  }

  @Test
  void testBinds() {
    final var parser =
        parser(
            "start S; S{head: $L} --> N{lemma: $L}; N{lemma: $L} --> {TOKEN}{cat: noun, lemma: $L};");

    final var result = parse(parser, "dogs/cat=noun/lemma=dog");

    assertEquals(Outcome.ACCEPTED, result.outcome());
    assertEquals(new StringConstant("dog"), root(result).get("head"));
  }

  @Test
  void testMismatch() {
    final var parser = parser("start S; S --> {TOKEN}{cat: noun};");

    assertEquals(Outcome.ACCEPTED, parse(parser, "dog/cat=noun").outcome());
    assertEquals(Outcome.REJECTED, parse(parser, "barks/cat=verb").outcome());
  }

  /** A feature the token does not have unifies with anything and leaves its variable unbound. */
  @Test
  void testMissing() {
    final var parser = parser("start S; S --> {TOKEN}{cat: noun, num: $N} where !is_bound($N);");

    assertEquals(Outcome.ACCEPTED, parse(parser, "gas/cat=noun").outcome());
    assertEquals(Outcome.REJECTED, parse(parser, "dogs/cat=noun/num=pl").outcome());
  }

  @Test
  void testAgreement() {
    final var parser = parser("start S; S --> {TOKEN}{num: $N} {TOKEN}{num: $N};");

    assertEquals(Outcome.ACCEPTED, parse(parser, "dog/num=sg", "barks/num=sg").outcome());
    assertEquals(Outcome.REJECTED, parse(parser, "dog/num=sg", "bark/num=pl").outcome());
  }

  @Test
  void testLabel() {
    final var parser = parser("start S; S --> {TOKEN}{cat: noun}:w where equals(w, 'dog');");

    assertEquals(Outcome.ACCEPTED, parse(parser, "dog/cat=noun").outcome());
    assertEquals(Outcome.REJECTED, parse(parser, "cat/cat=noun").outcome());
  }

  /** S-G4: a {TOKEN} inside a group shares its variables with the enclosing production. */
  @Test
  void testGroup() {
    final var parser = parser("start S; S{k: $K} --> 'x' ({TOKEN}{k: $K})?; A{k: a} --> 'a';");

    final var result = parser.parse("x a");

    assertEquals(Outcome.ACCEPTED, result.outcome());
    assertEquals(new StringConstant("a"), root(result).get("k"));
    assertEquals(Outcome.ACCEPTED, parser.parse("x").outcome());
  }

  /** A lexer token carries its lexical production's features, and {TOKEN} reads them too. */
  @Test
  void testLexerToken() {
    final var parser = parser("start S; S{k: $K} --> {TOKEN}{k: $K}; A{k: a} --> 'a';");

    final var result = parser.parse("a");

    assertEquals(Outcome.ACCEPTED, result.outcome());
    assertEquals(new StringConstant("a"), root(result).get("k"));
  }
}
