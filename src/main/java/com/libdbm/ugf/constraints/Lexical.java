package com.libdbm.ugf.constraints;

import com.libdbm.ugf.features.NumericConstant;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Value;
import java.math.BigDecimal;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * The lexical predicates (S-C7): they read the lexical state stack or the character position, so
 * they can only be evaluated while lexing. This is the only place their names are defined.
 */
final class Lexical {

  private static final Pattern NUMBER = Pattern.compile("-?\\d+");

  private Lexical() {}

  static void register(final Predicates.Builder builder) {
    final var phase = Predicates.Phase.LEXICAL;
    builder.add(
        "in_state",
        1,
        Integer.MAX_VALUE,
        phase,
        (environment, args) ->
            args.stream().anyMatch(arg -> text(arg).equals(environment.state())));
    builder.add(
        "state_depth",
        1,
        1,
        phase,
        (environment, args) -> {
          final var depth = integer(args.getFirst());
          return depth.isPresent() && environment.states().size() <= depth.getAsLong();
        });
    builder.add(
        "state_contains",
        1,
        Integer.MAX_VALUE,
        phase,
        (environment, args) ->
            args.stream().anyMatch(arg -> environment.states().contains(text(arg))));
    builder.add("at_char_start", 0, 0, phase, (environment, args) -> environment.position() == 0);
    builder.add(
        "at_char_position",
        1,
        1,
        phase,
        (environment, args) -> {
          final var position = integer(args.getFirst());
          return position.isPresent() && environment.position() == position.getAsLong();
        });
  }

  private static String text(final Value value) {
    return value instanceof StringConstant(String text) ? text : String.valueOf(value);
  }

  /**
   * The value as an exact integer that fits in a {@code long}: a number, or a number written as a
   * string literal (grammar source has no numeric literals). Fractions, non-finite numbers,
   * out-of-range values and anything else are empty, so the predicate using it is false (S-C8).
   */
  static OptionalLong integer(final Value value) {
    try {
      return switch (value) {
        case NumericConstant number when number.floating() -> {
          final var real = number.asDouble();
          yield Double.isFinite(real)
              ? OptionalLong.of(new BigDecimal(real).longValueExact())
              : OptionalLong.empty();
        }
        case NumericConstant number -> OptionalLong.of(number.asLong());
        case StringConstant(String text) when NUMBER.matcher(text.strip()).matches() ->
            OptionalLong.of(Long.parseLong(text.strip()));
        case null, default -> OptionalLong.empty();
      };
    } catch (final ArithmeticException | NumberFormatException exception) {
      return OptionalLong.empty();
    }
  }
}
