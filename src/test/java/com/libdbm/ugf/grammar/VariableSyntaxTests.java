package com.libdbm.ugf.grammar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.libdbm.ugf.constraints.Expression;
import com.libdbm.ugf.features.FeaturePath;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Variable;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A '$' prefix, not capitalization, marks a variable. */
class VariableSyntaxTests {

  private static GrammarRule rule(final String source) {
    final var grammar = UnificationGrammarParserFactory.unvalidated(source).orElseThrow();
    return grammar.rules().values().iterator().next().getFirst();
  }

  private static Structure features(final String source) {
    return rule(source).lhs().features();
  }

  @Test
  void testPrefixedValueIsVariable() {
    assertEquals(new Variable("$N"), features("S{num: $N} --> 'a';").get("num"));
    assertEquals(new Variable("$n"), features("S{num: $n} --> 'a';").get("num"));
  }

  @Test
  void testCapitalizedValueIsAtom() {
    assertEquals(new StringConstant("Nom"), features("S{case: Nom} --> 'a';").get("case"));
  }

  @Test
  void testPrefixedArgumentIsVariable() {
    final var call =
        (Expression.Call) rule("S --> 'a' where f($X, $X.num, n);").constraints().getFirst();
    assertEquals(
        List.of(new Variable("$X"), new FeaturePath(List.of("$X", "num")), new Variable("n")),
        call.args());
  }

  @Test
  void testDisplayOmitsPrefix() {
    assertEquals("?X", new Variable("$X").display());
  }
}
