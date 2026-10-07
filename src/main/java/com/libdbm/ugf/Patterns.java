package com.libdbm.ugf;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The portable regex subset (S-L7): every grammar regex, {@code whitespace} pattern and {@code
 * matches} pattern is checked against it, then compiled with its portable meaning. In Java that
 * means {@code $} becomes {@code \z}.
 */
public final class Patterns {

  /** The error code for a pattern outside the portable subset. */
  public static final String SUBSET = "pattern.subset";

  /** The Unicode general categories {@code \p{..}} may name. */
  private static final Set<String> CATEGORIES =
      Set.of(
          "L", "Lu", "Ll", "Lt", "Lm", "Lo", "M", "Mn", "Mc", "Me", "N", "Nd", "Nl", "No", "P",
          "Pc", "Pd", "Ps", "Pe", "Pi", "Pf", "Po", "S", "Sm", "Sc", "Sk", "So", "Z", "Zs", "Zl",
          "Zp", "C", "Cc", "Cf", "Cs", "Co", "Cn");

  /** ASCII punctuation, which may be escaped to stand for itself. */
  private static final String PUNCTUATION = "\\^$.|?*+()[]{}/-!\"#%&',:;<=>@_`~";

  /** The only flag a standalone pattern may start with. */
  private static final String FLAG = "(?s)";

  private static final Pattern BOUNDS = Pattern.compile("\\{[0-9]+(?:,[0-9]*)?}");

  private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]+");

  private Patterns() {}

  /**
   * Checks a standalone pattern ({@code whitespace} or {@code matches}) against the subset and
   * returns its Java form. It may start with {@code (?s)}.
   */
  public static Result<String, ErrorDetails> portable(final String source) {
    return scan(source, true);
  }

  /**
   * Checks a grammar regex element, part of a lexeme's pattern, against the subset and returns its
   * Java form. It takes no flag.
   */
  public static Result<String, ErrorDetails> fragment(final String source) {
    return scan(source, false);
  }

  private static Result<String, ErrorDetails> scan(final String source, final boolean flagged) {
    final var scanner = new Scanner(source);
    final var java = scanner.scan(flagged);
    return scanner.problem == null
        ? Result.success(java)
        : Result.failure(ErrorDetails.of(SUBSET, scanner.problem));
  }

  /** Checks a standalone pattern against the subset and compiles its Java form. */
  public static Result<Pattern, ErrorDetails> compile(final String source) {
    return portable(source)
        .flatMap(
            java -> {
              try {
                return Result.success(Pattern.compile(java));
              } catch (final PatternSyntaxException exception) {
                return Result.failure(
                    ErrorDetails.of(SUBSET, "invalid pattern: " + exception.getDescription()));
              }
            });
  }

  /** What the previous item in a character class was. */
  private enum Item {
    NONE,
    SINGLE,
    SET,
    RANGE
  }

  /** One pass over a pattern; {@link #problem} is set at the first construct outside the subset. */
  private static final class Scanner {
    private final String source;
    private final StringBuilder out = new StringBuilder();
    private int index;
    private String problem;

    private Scanner(final String source) {
      this.source = source;
    }

    private String scan(final boolean flagged) {
      if (flagged && source.startsWith(FLAG)) {
        out.append(FLAG);
        index = FLAG.length();
      }
      while (problem == null && index < source.length()) {
        final var c = source.charAt(index);
        switch (c) {
          case '\\' -> escape(false);
          case '[' -> klass();
          case '(' -> open();
          case '*', '+', '?' -> {
            out.append(c);
            index++;
            quantified();
          }
          case '{' -> bounds();
          case '}', ']' -> fail("a literal " + c + " must be escaped");
          case '$' -> {
            out.append("\\z");
            index++;
          }
          default -> {
            out.append(c);
            index++;
          }
        }
      }
      return out.toString();
    }

    private void fail(final String message) {
      problem = message + " at offset " + index + " (S-L7)";
    }

    private void open() {
      if (!source.startsWith("(?", index)) {
        out.append('(');
        index++;
        return;
      }
      for (final var prefix : new String[] {"(?:", "(?=", "(?!", "(?<=", "(?<!"}) {
        if (source.startsWith(prefix, index)) {
          out.append(prefix);
          index += prefix.length();
          return;
        }
      }
      fail(
          "only (?:, lookahead, lookbehind and a leading (?s) in a standalone pattern are portable");
    }

    private void bounds() {
      final var matcher = BOUNDS.matcher(source).region(index, source.length());
      if (!matcher.lookingAt()) {
        fail("a literal { must be escaped");
        return;
      }
      out.append(matcher.group());
      index = matcher.end();
      quantified();
    }

    /** After a quantifier: a lazy {@code ?} is portable, a possessive {@code +} is not. */
    private void quantified() {
      if (index < source.length() && source.charAt(index) == '?') {
        out.append('?');
        index++;
      } else if (index < source.length() && source.charAt(index) == '+') {
        fail("possessive quantifiers are not portable");
      }
    }

    private void klass() {
      out.append('[');
      index++;
      if (index < source.length() && source.charAt(index) == '^') {
        out.append('^');
        index++;
      }
      if (index < source.length() && source.charAt(index) == ']') {
        fail("a ] at the start of a class must be escaped");
        return;
      }
      // What the last item was: none yet, a single character, a set (\\w, \\p{..}) or a range.
      var last = Item.NONE;
      var ranging = false;
      while (problem == null && index < source.length()) {
        final var c = source.charAt(index);
        if (c == ']') {
          out.append(']');
          index++;
          return;
        }
        if (c == '-' && last != Item.NONE && !source.startsWith("-]", index)) {
          // Between two single characters, a - forms a range; anywhere else it must be escaped.
          if (last != Item.SINGLE || set(index + 1)) {
            fail("a - in a class must be first, last, escaped or between two single characters");
            return;
          }
          out.append('-');
          index++;
          ranging = true;
          continue;
        }
        final var item = set(index) ? Item.SET : Item.SINGLE;
        if (c == '[') {
          fail("a [ inside a class must be escaped");
        } else if (source.startsWith("&&", index)) {
          fail("class intersection is not portable");
        } else if (c == '\\') {
          escape(true);
        } else {
          out.append(c);
          index++;
        }
        last = ranging ? Item.RANGE : item;
        ranging = false;
      }
      if (problem == null) {
        fail("unterminated class");
      }
    }

    /**
     * True if a shorthand or property class such as {@code \\w} or {@code \\p{L}} starts at {@code
     * at}.
     */
    private boolean set(final int at) {
      return at + 1 < source.length()
          && source.charAt(at) == '\\'
          && "dDwWsSpP".indexOf(source.charAt(at + 1)) >= 0;
    }

    private void escape(final boolean inside) {
      if (index + 1 >= source.length()) {
        fail("a pattern cannot end with \\");
        return;
      }
      final var c = source.charAt(index + 1);
      switch (c) {
        case 'n', 'r', 't', 'f', 'd', 'D', 'w', 'W', 's', 'S' -> copy(2);
        case 'b', 'B' -> {
          if (inside) {
            fail("\\" + c + " is not portable inside a class");
          } else {
            copy(2);
          }
        }
        case 'x' -> hex(2);
        case 'u' -> hex(4);
        case 'p', 'P' -> property();
        default -> {
          if (PUNCTUATION.indexOf(c) >= 0) {
            copy(2);
          } else {
            fail("\\" + c + " is not portable");
          }
        }
      }
    }

    private void hex(final int digits) {
      final var end = index + 2 + digits;
      if (end > source.length() || !HEX.matcher(source.substring(index + 2, end)).matches()) {
        fail("\\" + source.charAt(index + 1) + " needs exactly " + digits + " hex digits");
        return;
      }
      copy(2 + digits);
    }

    private void property() {
      final var close = source.indexOf('}', index);
      if (index + 2 >= source.length() || source.charAt(index + 2) != '{' || close < 0) {
        fail("\\" + source.charAt(index + 1) + " needs a {category}");
        return;
      }
      final var name = source.substring(index + 3, close);
      if (!CATEGORIES.contains(name)) {
        fail("\\p{" + name + "} is not a Unicode general category");
        return;
      }
      copy(close + 1 - index);
    }

    private void copy(final int length) {
      out.append(source, index, index + length);
      index += length;
    }
  }
}
