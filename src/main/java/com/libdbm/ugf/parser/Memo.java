package com.libdbm.ugf.parser;

import com.libdbm.ugf.constraints.Environment;
import com.libdbm.ugf.constraints.Expression;
import com.libdbm.ugf.constraints.Predicates;
import com.libdbm.ugf.features.Value;
import java.util.*;

/**
 * Predicate results for one parse, keyed by predicate name and arguments resolved through the
 * environment (PRF-2). Values are immutable and predicates are pure, so the key is complete.
 * Lexical and positional predicates read the state stack, offsets or the constituent's span as well
 * as their arguments, so they are always called.
 */
final class Memo {

  private final Map<Key, Boolean> results = new HashMap<>();
  private final Set<String> positional;
  private long calls;
  private long hits;

  Memo(final Predicates predicates) {
    final var names = new HashSet<>(predicates.names(Predicates.Phase.LEXICAL));
    names.addAll(predicates.names(Predicates.Phase.POSITIONAL));
    this.positional = Set.copyOf(names);
  }

  boolean test(final Environment environment, final Expression.Call call) {
    if (positional.contains(call.name())) {
      calls++;
      return environment.test(call);
    }
    // Stream.toList keeps unbound (null) arguments.
    final var key = new Key(call.name(), call.args().stream().map(environment::resolve).toList());
    final var cached = results.get(key);
    if (cached != null) {
      hits++;
      return cached;
    }
    calls++;
    final var result = environment.test(call);
    results.put(key, result);
    return result;
  }

  /** Predicates actually called. */
  long calls() {
    return calls;
  }

  /** Calls answered from the memo. */
  long hits() {
    return hits;
  }

  private record Key(String name, List<Value> args) {}
}
