package com.libdbm.ugf.constraints;

import static com.libdbm.ugf.constraints.Result.*;

import com.libdbm.ugf.features.Binding;
import com.libdbm.ugf.features.FeaturePath;
import com.libdbm.ugf.features.NumericConstant;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Unifier;
import com.libdbm.ugf.features.Value;
import com.libdbm.ugf.features.Variable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Generic built-in predicates for constraint evaluation.
 *
 * <p>Provides only domain-independent predicates:
 *
 * <ul>
 *   <li>Unification: agree(X, Y), unify(X, Y)
 *   <li>Feature access: has_feature(X, f), get_feature(X, f, V), feature_eq(X, f, Y, g)
 *   <li>Equality: equals(A, B), not_equals(A, B)
 *   <li>Type checking: is_string(X), is_number(X), is_structure(X), is_bound(X)
 *   <li>String predicates: starts_with(X, prefix), ends_with(X, suffix), contains(X, sub),
 *       matches(X, regex), not_empty(X)
 *   <li>Case predicates: is_upper(X), is_lower(X), is_capitalized(X)
 *   <li>Numeric predicates: lt(X, Y), le(X, Y), gt(X, Y), ge(X, Y)
 * </ul>
 *
 * <p>Domain-specific predicates (CLEAN, CLIF, FOL) should be registered separately by their
 * respective parsers using {@link Context#withPredicate}.
 */
public final class Builtins {

  private Builtins() {}

  /** Returns all generic built-in predicates. */
  public static Map<String, BiFunction<Context, List<Value>, Result>> all() {
    final var ret = new HashMap<String, BiFunction<Context, List<Value>, Result>>();

    // Unification predicates
    ret.put("agree", Builtins::agree);
    ret.put("unify", Builtins::agree); // alias

    // Feature predicates
    ret.put("has_feature", Builtins::hasFeature);
    ret.put("get_feature", Builtins::getFeature);
    ret.put("feature_eq", Builtins::featureEq);

    // Equality predicates
    ret.put("equals", Builtins::equals);
    ret.put("not_equals", Builtins::notEquals);

    // Type checking predicates
    ret.put("is_string", Builtins::isString);
    ret.put("is_number", Builtins::isNumber);
    ret.put("is_structure", Builtins::isStructure);
    ret.put("is_bound", Builtins::isBound);

    // String predicates
    ret.put("starts_with", Builtins::startsWith);
    ret.put("ends_with", Builtins::endsWith);
    ret.put("contains", Builtins::contains);
    ret.put("matches", Builtins::matches);
    ret.put("not_empty", Builtins::notEmpty);
    ret.put("is_upper", Builtins::isUpper);
    ret.put("is_lower", Builtins::isLower);
    ret.put("is_capitalized", Builtins::isCapitalized);

    // Numeric predicates
    ret.put("lt", Builtins::lessThan);
    ret.put("le", Builtins::lessOrEqual);
    ret.put("gt", Builtins::greaterThan);
    ret.put("ge", Builtins::greaterOrEqual);

    return ret;
  }

  /**
   * Agreement check: verifies that two values can unify.
   *
   * <p>This is a <b>checking</b> predicate - it tests unifiability without modifying the context.
   * Bindings produced during unification are intentionally discarded. This is the standard behavior
   * for constraint checking during parsing, where we verify compatibility without committing to
   * bindings.
   *
   * <p>If you need bindings to persist for subsequent predicates, the parser should explicitly
   * manage a separate bindings environment and merge successful unifications.
   */
  private static Result agree(final Context context, final List<Value> args) {
    if (args.size() != 2) return fail("agree/2 expects 2 arguments");
    final var v1 = deref(context, args.get(0));
    final var v2 = deref(context, args.get(1));
    // Handle unbound variables gracefully
    if (v1 == null) return fail("agree: first argument is unbound");
    if (v2 == null) return fail("agree: second argument is unbound");
    final var unified = Unifier.unify(v1, v2, new HashMap<>());
    return unified.isPresent()
        ? ok()
        : fail("agreement failed: " + display(v1) + " vs " + display(v2));
  }

  /** has_feature(X, f) - checks if structure X has feature f */
  private static Result hasFeature(final Context context, final List<Value> args) {
    if (args.size() != 2) return fail("has_feature/2 expects 2 arguments");
    final var base = deref(context, args.get(0));
    final var feat = deref(context, args.get(1));

    if (!(base instanceof Structure s)) {
      return fail("has_feature expects a structure as first argument");
    }
    if (!(feat instanceof StringConstant(String name))) {
      return fail("has_feature expects a string as feature name");
    }
    return result(s.has(name), "feature not found: " + name);
  }

  /** get_feature(X, f, V) - checks if X.f equals V */
  private static Result getFeature(final Context context, final List<Value> args) {
    if (args.size() != 3) return fail("get_feature/3 expects 3 arguments");
    final var base = deref(context, args.get(0));
    final var feat = deref(context, args.get(1));
    final var expected = deref(context, args.get(2));

    if (!(base instanceof Structure s)) {
      return fail("get_feature expects a structure as first argument");
    }
    if (!(feat instanceof StringConstant(String name))) {
      return fail("get_feature expects a string as feature name");
    }
    final var actual = s.get(name);
    if (actual == null) {
      return fail("feature not found: " + name);
    }
    return result(
        actual.equals(expected),
        "feature value mismatch: " + display(actual) + " vs " + display(expected));
  }

  /** feature_eq(X, f, Y, g) - checks if X.f equals Y.g */
  private static Result featureEq(final Context context, final List<Value> args) {
    if (args.size() != 4) return fail("feature_eq/4 expects 4 arguments");
    final var base1 = deref(context, args.get(0));
    final var feat1 = deref(context, args.get(1));
    final var base2 = deref(context, args.get(2));
    final var feat2 = deref(context, args.get(3));

    if (!(feat1 instanceof StringConstant(String f1))
        || !(feat2 instanceof StringConstant(String f2))) {
      return fail("feature_eq expects feature names as string constants");
    }
    if (!(base1 instanceof Structure s1) || !(base2 instanceof Structure s2)) {
      return fail("feature_eq expects structures as first and third arguments");
    }
    final var v1 = s1.get(f1);
    final var v2 = s2.get(f2);
    if (v1 == null || v2 == null) {
      return fail("feature missing: " + (v1 == null ? f1 : f2));
    }
    return result(v1.equals(v2), "feature values not equal: " + display(v1) + " vs " + display(v2));
  }

  private static Result equals(final Context context, final List<Value> args) {
    if (args.size() != 2) return fail("equals/2 expects 2 arguments");
    final var v1 = deref(context, args.get(0));
    final var v2 = deref(context, args.get(1));
    return result(v1 != null && v1.equals(v2), "not equal: " + display(v1) + " vs " + display(v2));
  }

  private static Result notEquals(final Context context, final List<Value> args) {
    if (args.size() != 2) return fail("not_equals/2 expects 2 arguments");
    final var v1 = deref(context, args.get(0));
    final var v2 = deref(context, args.get(1));
    return result(v1 == null || !v1.equals(v2), "equal: " + display(v1));
  }

  private static Result isString(final Context context, final List<Value> args) {
    if (args.size() != 1) return fail("is_string/1 expects 1 argument");
    final var v = deref(context, args.getFirst());
    return result(v instanceof StringConstant, "not a string: " + display(v));
  }

  private static Result isNumber(final Context context, final List<Value> args) {
    if (args.size() != 1) return fail("is_number/1 expects 1 argument");
    final var v = deref(context, args.getFirst());
    return result(v instanceof NumericConstant, "not a number: " + display(v));
  }

  private static Result isStructure(final Context context, final List<Value> args) {
    if (args.size() != 1) return fail("is_structure/1 expects 1 argument");
    final var v = deref(context, args.getFirst());
    return result(v instanceof Structure, "not a structure: " + display(v));
  }

  private static Result isBound(final Context context, final List<Value> args) {
    if (args.size() != 1) return fail("is_bound/1 expects 1 argument");
    final var arg = args.getFirst();
    if (arg instanceof Variable(String name)) {
      return result(context.get(name) != null, "unbound: " + name);
    }
    return ok(); // non-variables are always "bound"
  }

  private static Result startsWith(final Context context, final List<Value> args) {
    if (args.size() != 2) return fail("starts_with/2 expects 2 arguments");
    final var v = deref(context, args.get(0));
    final var prefix = deref(context, args.get(1));

    if (!(v instanceof StringConstant(String s))) {
      return fail("starts_with expects a string as first argument");
    }
    if (!(prefix instanceof StringConstant(String p))) {
      return fail("starts_with expects a string as prefix");
    }
    return result(s.startsWith(p), "does not start with: " + p);
  }

  private static Result endsWith(final Context context, final List<Value> args) {
    if (args.size() != 2) return fail("ends_with/2 expects 2 arguments");
    final var v = deref(context, args.get(0));
    final var suffix = deref(context, args.get(1));

    if (!(v instanceof StringConstant(String s))) {
      return fail("ends_with expects a string as first argument");
    }
    if (!(suffix instanceof StringConstant(String p))) {
      return fail("ends_with expects a string as suffix");
    }
    return result(s.endsWith(p), "does not end with: " + p);
  }

  private static Result contains(final Context context, final List<Value> args) {
    if (args.size() != 2) return fail("contains/2 expects 2 arguments");
    final var v = deref(context, args.get(0));
    final var sub = deref(context, args.get(1));

    if (!(v instanceof StringConstant(String s))) {
      return fail("contains expects a string as first argument");
    }
    if (!(sub instanceof StringConstant(String p))) {
      return fail("contains expects a string as substring");
    }
    return result(s.contains(p), "does not contain: " + p);
  }

  private static Result matches(final Context context, final List<Value> args) {
    if (args.size() != 2) return fail("matches/2 expects 2 arguments");
    final var v = deref(context, args.get(0));
    final var pattern = deref(context, args.get(1));

    if (!(v instanceof StringConstant(String s))) {
      return fail("matches expects a string as first argument");
    }
    if (!(pattern instanceof StringConstant(String p))) {
      return fail("matches expects a regex pattern as second argument");
    }
    try {
      return result(s.matches(p), "does not match pattern: " + p);
    } catch (final Exception e) {
      return fail("invalid regex: " + p);
    }
  }

  private static Result notEmpty(final Context context, final List<Value> args) {
    if (args.size() != 1) return fail("not_empty/1 expects 1 argument");
    final var v = deref(context, args.getFirst());
    if (v == null) return fail("not_empty: argument is unbound");
    if (v instanceof StringConstant(String s)) {
      return result(s != null && !s.isEmpty(), "not_empty: empty string");
    }
    return fail("not_empty expects a string, got: " + v.getClass().getSimpleName());
  }

  /** Checks if entire string is uppercase. */
  private static Result isUpper(final Context context, final List<Value> args) {
    if (args.size() != 1) return fail("is_upper/1 expects 1 argument");
    final var v = deref(context, args.getFirst());
    if (!(v instanceof StringConstant(String s))) {
      return fail("is_upper expects a string");
    }
    final var pass = s != null && !s.isEmpty() && s.equals(s.toUpperCase());
    return result(pass, "not uppercase: " + s);
  }

  /** Checks if entire string is lowercase. */
  private static Result isLower(final Context context, final List<Value> args) {
    if (args.size() != 1) return fail("is_lower/1 expects 1 argument");
    final var v = deref(context, args.getFirst());
    if (!(v instanceof StringConstant(String s))) {
      return fail("is_lower expects a string");
    }
    final var pass = s != null && !s.isEmpty() && s.equals(s.toLowerCase());
    return result(pass, "not lowercase: " + s);
  }

  /** Checks if first character is uppercase (capitalized/title case). */
  private static Result isCapitalized(final Context context, final List<Value> args) {
    if (args.size() != 1) return fail("is_capitalized/1 expects 1 argument");
    final var v = deref(context, args.getFirst());
    if (!(v instanceof StringConstant(String s))) {
      return fail("is_capitalized expects a string");
    }
    final var pass = s != null && !s.isEmpty() && Character.isUpperCase(s.charAt(0));
    return result(pass, "not capitalized: " + s);
  }

  private static Result lessThan(final Context context, final List<Value> args) {
    if (args.size() != 2) return fail("lt/2 expects 2 arguments");
    final var v1 = deref(context, args.get(0));
    final var v2 = deref(context, args.get(1));

    if (!(v1 instanceof NumericConstant n1) || !(v2 instanceof NumericConstant n2)) {
      return fail("lt expects numeric arguments");
    }
    return result(n1.value().doubleValue() < n2.value().doubleValue(), "not less than");
  }

  private static Result lessOrEqual(final Context context, final List<Value> args) {
    if (args.size() != 2) return fail("le/2 expects 2 arguments");
    final var v1 = deref(context, args.get(0));
    final var v2 = deref(context, args.get(1));

    if (!(v1 instanceof NumericConstant n1) || !(v2 instanceof NumericConstant n2)) {
      return fail("le expects numeric arguments");
    }
    return result(n1.value().doubleValue() <= n2.value().doubleValue(), "not less or equal");
  }

  private static Result greaterThan(final Context context, final List<Value> args) {
    if (args.size() != 2) return fail("gt/2 expects 2 arguments");
    final var v1 = deref(context, args.get(0));
    final var v2 = deref(context, args.get(1));

    if (!(v1 instanceof NumericConstant n1) || !(v2 instanceof NumericConstant n2)) {
      return fail("gt expects numeric arguments");
    }
    return result(n1.value().doubleValue() > n2.value().doubleValue(), "not greater than");
  }

  private static Result greaterOrEqual(final Context context, final List<Value> args) {
    if (args.size() != 2) return fail("ge/2 expects 2 arguments");
    final var v1 = deref(context, args.get(0));
    final var v2 = deref(context, args.get(1));

    if (!(v1 instanceof NumericConstant n1) || !(v2 instanceof NumericConstant n2)) {
      return fail("ge expects numeric arguments");
    }
    return result(n1.value().doubleValue() >= n2.value().doubleValue(), "not greater or equal");
  }

  private static Value deref(final Context context, final Value v) {
    if (v instanceof Variable(String name)) {
      return context.get(name);
    }
    if (v instanceof FeaturePath path) {
      return lookup(context, path);
    }
    return v;
  }

  /** Dereference a feature path like W.cat. */
  private static Value lookup(final Context ctx, final FeaturePath path) {
    // Get the root binding
    final var root = ctx.get(path.root());
    if (root == null) {
      return null;
    }

    // If simple path (just a variable), return the root
    if (path.isSimple()) {
      return root;
    }

    // Traverse the path through features
    Value current = root;
    for (final var feat : path.path()) {
      if (current instanceof Binding b) {
        // Access feature from binding's feature structure
        current = b.features().get(feat);
      } else if (current instanceof Structure s) {
        current = s.get(feat);
      } else {
        // Can't traverse further
        return null;
      }
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  private static String display(final Value v) {
    return v == null ? "<unbound>" : v.display();
  }
}
