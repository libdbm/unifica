package com.libdbm.ugf.generator;

import java.util.List;

/** Joins generated tokens into a sentence (GEN-7). */
@FunctionalInterface
public interface Joiner {

  String join(List<String> tokens);

  /**
   * Single spaces between tokens, except: none after an opening bracket (<code>&lt; ( [ &#123;
   * </code>), none before a closing bracket or {@code / > ! =}, and none next to punctuation
   * ({@code < > ( ) [ ] { } = / ' " ! ?}).
   */
  Joiner SPACED =
      tokens -> {
        final var builder = new StringBuilder();
        for (final var token : tokens) {
          if (token.isEmpty()) {
            continue;
          }
          if (!builder.isEmpty()) {
            final var last = builder.charAt(builder.length() - 1);
            final var first = token.charAt(0);
            final var after = "<([{".indexOf(last) >= 0;
            final var before = ">)]}/>!=".indexOf(first) >= 0 || token.startsWith("/>");
            final var punctuation = "<>()[]{}=/'\"!?";
            if (!after
                && !before
                && punctuation.indexOf(last) < 0
                && punctuation.indexOf(first) < 0) {
              builder.append(' ');
            }
          }
          builder.append(token);
        }
        return builder.toString();
      };

  /** A single space between every pair of tokens. */
  Joiner SPACE =
      tokens -> String.join(" ", tokens.stream().filter(token -> !token.isEmpty()).toList());
}
