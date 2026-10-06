package com.libdbm.ugf.constraints;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.features.Binding;
import com.libdbm.ugf.features.Bindings;
import com.libdbm.ugf.features.BooleanConstant;
import com.libdbm.ugf.features.NumericConstant;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Unification;
import com.libdbm.ugf.features.Unifier;
import com.libdbm.ugf.features.Value;
import com.libdbm.ugf.features.Variable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The builtin predicates, registered with {@link Predicates.Builder#builtins()} (CON-6, CON-8):
 * unification ({@code agree}, {@code unify}), features ({@code has_feature}, {@code get_feature},
 * {@code feature_eq}), equality, type tests, strings ({@code starts_with}, {@code ends_with},
 * {@code contains}, {@code matches}, {@code not_empty}, {@code is_upper}, {@code is_lower}, {@code
 * is_capitalized}), exact numeric comparison ({@code lt}, {@code le}, {@code gt}, {@code ge}) and
 * token position ({@code at_start}, {@code at_position}).
 *
 * <p>String predicates use a value's text (S-C9), so a literal, a labelled token and a labelled
 * constituent behave alike.
 */
final class Builtins {

  private Builtins() {}

  private static final int PATTERNS = 256;

  /**
   * Compiled patterns for non-constant regex arguments, bounded and least recently used (CON-9).
   */
  private static final Map<String, Pattern> CACHE =
      Collections.synchronizedMap(
          new LinkedHashMap<>(PATTERNS, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, Pattern> eldest) {
              return size() > PATTERNS;
            }
          });

  /** Registers every builtin: the position predicates are positional, the rest syntactic. */
  static void register(final Predicates.Builder builder) {
    final var phase = Predicates.Phase.SYNTACTIC;
    builder.add("agree", 2, 2, phase, Builtins::agrees);
    builder.add("unify", 2, 2, phase, Builtins::agrees);
    builder.add(
        "has_feature",
        2,
        2,
        phase,
        (environment, args) -> {
          final var structure = features(environment.resolve(args.get(0)));
          final var name = environment.resolve(args.get(1));
          return structure != null
              && name != null
              && name.text() != null
              && structure.has(name.text());
        });
    builder.add(
        "get_feature",
        3,
        3,
        phase,
        (environment, args) -> {
          final var actual = feature(environment, args.get(0), args.get(1));
          return actual != null && actual.equals(environment.resolve(args.get(2)));
        });
    builder.add(
        "feature_eq",
        4,
        4,
        phase,
        (environment, args) -> {
          final var first = feature(environment, args.get(0), args.get(1));
          return first != null && first.equals(feature(environment, args.get(2), args.get(3)));
        });
    builder.add(
        "equals",
        2,
        2,
        phase,
        (environment, args) -> {
          final var first = environment.resolve(args.get(0));
          return first != null && first.equals(environment.resolve(args.get(1)));
        });
    builder.add(
        "not_equals",
        2,
        2,
        phase,
        (environment, args) -> {
          final var first = environment.resolve(args.get(0));
          return first == null || !first.equals(environment.resolve(args.get(1)));
        });
    builder.add(
        "is_string",
        1,
        1,
        phase,
        (environment, args) -> environment.resolve(args.getFirst()) instanceof StringConstant);
    builder.add(
        "is_number",
        1,
        1,
        phase,
        (environment, args) -> environment.resolve(args.getFirst()) instanceof NumericConstant);
    builder.add(
        "is_structure",
        1,
        1,
        phase,
        (environment, args) -> environment.resolve(args.getFirst()) instanceof Structure);
    builder.add(
        "is_bound",
        1,
        1,
        phase,
        (environment, args) ->
            !(args.getFirst() instanceof Variable) || environment.resolve(args.getFirst()) != null);
    builder.add("starts_with", 2, 2, phase, strings(String::startsWith));
    builder.add("ends_with", 2, 2, phase, strings(String::endsWith));
    builder.add("contains", 2, 2, phase, strings(String::contains));
    builder.add("matches", 2, 2, phase, strings(Builtins::matches));
    builder.add("not_empty", 1, 1, phase, string(text -> !text.isEmpty()));
    builder.add(
        "is_upper",
        1,
        1,
        phase,
        string(text -> !text.isEmpty() && text.equals(text.toUpperCase(Locale.ROOT))));
    builder.add(
        "is_lower",
        1,
        1,
        phase,
        string(text -> !text.isEmpty() && text.equals(text.toLowerCase(Locale.ROOT))));
    builder.add(
        "is_capitalized",
        1,
        1,
        phase,
        string(text -> !text.isEmpty() && Character.isUpperCase(text.charAt(0))));
    builder.add("lt", 2, 2, phase, numbers(comparison -> comparison < 0));
    builder.add("le", 2, 2, phase, numbers(comparison -> comparison <= 0));
    builder.add("gt", 2, 2, phase, numbers(comparison -> comparison > 0));
    builder.add("ge", 2, 2, phase, numbers(comparison -> comparison >= 0));
    final var positional = Predicates.Phase.POSITIONAL;
    builder.add(
        "at_start",
        0,
        0,
        positional,
        (environment, args) -> position(environment.get(Environment.POSITION)) == 0);
    builder.add(
        "at_position",
        1,
        1,
        positional,
        (environment, args) -> {
          final var position = position(environment.get(Environment.POSITION));
          return position >= 0 && position == position(args.getFirst());
        });
    builder.add(
        "at_end",
        0,
        0,
        positional,
        (environment, args) ->
            environment.get(Environment.END) instanceof BooleanConstant(boolean end) && end);
    builder.add(
        "before",
        1,
        Integer.MAX_VALUE,
        positional,
        (environment, args) -> {
          final var next = features(environment.get(Environment.NEXT));
          return next != null
              && args.stream()
                  .map(environment::resolve)
                  .anyMatch(
                      category ->
                          category != null && category.text() != null && next.has(category.text()));
        });
  }

  private static boolean agrees(final Environment environment, final List<Value> args) {
    final var first = environment.resolve(args.get(0));
    final var second = environment.resolve(args.get(1));
    return first != null
        && second != null
        && Unifier.unify(first, second, Bindings.EMPTY)
            instanceof Result.Success<Unification<Value>, ErrorDetails>;
  }

  /** The features of a structure or a constituent binding, or {@code null}. */
  private static Structure features(final Value value) {
    return switch (value) {
      case Structure structure -> structure;
      case Binding binding -> binding.features();
      case null, default -> null;
    };
  }

  private static Value feature(final Environment environment, final Value base, final Value name) {
    final var structure = features(environment.resolve(base));
    final var key = environment.resolve(name);
    return structure == null || key == null || key.text() == null
        ? null
        : structure.get(key.text());
  }

  /** A predicate over the texts of two arguments; false if either has no text (S-C9). */
  private static Predicates.Check strings(final BiPredicate<String, String> test) {
    return (environment, args) -> {
      final var first = environment.resolve(args.get(0));
      final var second = environment.resolve(args.get(1));
      return first != null
          && second != null
          && first.text() != null
          && second.text() != null
          && test.test(first.text(), second.text());
    };
  }

  /** A predicate over the text of one argument; false if it has no text (S-C9). */
  private static Predicates.Check string(final Predicate<String> test) {
    return (environment, args) -> {
      final var value = environment.resolve(args.getFirst());
      return value != null && value.text() != null && test.test(value.text());
    };
  }

  /** A predicate over the exact comparison of two numbers; false unless both are numbers. */
  /**
   * A comparison of two numbers (S-F4). As in IEEE 754, a comparison with NaN is false and the
   * infinities order below and above every finite value; finite values compare exactly.
   */
  private static Predicates.Check numbers(final IntPredicate test) {
    return (environment, args) ->
        environment.resolve(args.get(0)) instanceof NumericConstant first
            && environment.resolve(args.get(1)) instanceof NumericConstant second
            && !Double.isNaN(first.asDouble())
            && !Double.isNaN(second.asDouble())
            && test.test(compare(first, second));
  }

  private static int compare(final NumericConstant first, final NumericConstant second) {
    if (Double.isInfinite(first.asDouble()) || Double.isInfinite(second.asDouble())) {
      return Double.compare(first.asDouble(), second.asDouble());
    }
    return exact(first).compareTo(exact(second));
  }

  private static BigDecimal exact(final NumericConstant number) {
    return number.isFloating()
        ? new BigDecimal(number.asDouble())
        : BigDecimal.valueOf(number.asLong());
  }

  private static boolean matches(final String text, final String pattern) {
    final Pattern compiled;
    try {
      compiled = CACHE.computeIfAbsent(pattern, Pattern::compile);
    } catch (final PatternSyntaxException exception) {
      return false;
    }
    return compiled.matcher(text).matches();
  }

  /** A token position from a number or a numeric string, or -1. */
  private static long position(final Value value) {
    final var position = Lexical.integer(value);
    return position.isPresent() && position.getAsLong() >= 0 ? position.getAsLong() : -1;
  }
}
