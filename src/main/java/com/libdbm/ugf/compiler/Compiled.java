package com.libdbm.ugf.compiler;

import com.libdbm.ugf.constraints.Predicates;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An immutable compiled grammar, shared by the lexer, the parser and the generator (CMP-8).
 *
 * @param start the start symbol
 * @param lexemes every lexeme, by id
 * @param productions every syntactic production, by id
 * @param predicates the registry the grammar was compiled against
 * @param whitespace skipped between tokens (S-L4), or {@code null} for none
 * @param nullable symbols that can derive the empty string
 * @param index productions by left-hand symbol, in id order
 * @param categories categories of named lexemes, which a {@link Element.Symbol} can also match
 * @param skips categories whose matches are discarded between tokens (S-L4)
 */
public record Compiled(
    String start,
    List<Lexeme> lexemes,
    List<Production> productions,
    Predicates predicates,
    Pattern whitespace,
    Set<String> nullable,
    Map<String, List<Production>> index,
    Set<String> categories,
    Set<String> skips) {

  public Compiled {
    lexemes = List.copyOf(lexemes);
    productions = List.copyOf(productions);
    nullable = Set.copyOf(nullable);
    final var copied = new LinkedHashMap<String, List<Production>>();
    index.forEach((symbol, list) -> copied.put(symbol, List.copyOf(list)));
    index = Map.copyOf(copied);
    categories = Set.copyOf(categories);
    skips = Set.copyOf(skips);
  }

  /** The productions for {@code symbol}, in id order. */
  public List<Production> productions(final String symbol) {
    return index.getOrDefault(symbol, List.of());
  }

  /** True if {@code symbol} names a lexical category. */
  public boolean lexical(final String symbol) {
    return categories.contains(symbol);
  }

  /** True if {@code symbol} can derive the empty string. */
  public boolean nullable(final String symbol) {
    return nullable.contains(symbol);
  }
}
