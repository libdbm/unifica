package com.libdbm.ugf.generator;

import java.util.List;
import java.util.Map;

/**
 * The outcome of generating several sentences.
 *
 * @param sentences the sentences produced, each verified by parsing (S-N1)
 * @param requested how many sentences were asked for
 * @param failures for each reason a sentence could not be produced, how many times it happened
 */
public record Generation(List<String> sentences, int requested, Map<String, Integer> failures) {

  public Generation {
    sentences = List.copyOf(sentences);
    failures = Map.copyOf(failures);
  }
}
