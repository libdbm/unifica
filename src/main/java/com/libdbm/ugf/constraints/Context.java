package com.libdbm.ugf.constraints;

import com.libdbm.ugf.features.NumericConstant;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Evaluation context for constraint checking.
 *
 * <p>Provides the central environment for constraint evaluation including:
 *
 * <ul>
 *   <li>Variable bindings (Map&lt;String, Value&gt;)
 *   <li>Custom predicates
 * </ul>
 *
 * <p>This is a pure constraint solver context with no notion of lexical state or position. Position
 * tracking is handled by parsing/lexical classes via the {@code position} binding. Lexer-only
 * predicates are identified by {@code LexicalContext.LEXER_ONLY_PREDICATES}.
 *
 * <p>Uses fluent setters (withXXX) for configuration:
 *
 * <pre>
 * context
 *     .withBinding("subject", "dog")
 *     .withBinding("position", 5);
 * </pre>
 *
 * <h3>Thread Safety</h3>
 *
 * <p>This class is <b>not thread-safe</b>. It contains mutable state (bindings) that is modified
 * during constraint evaluation. For concurrent use:
 *
 * <ul>
 *   <li>Use {@link #extend()} to create child contexts for each thread
 *   <li>Do not share a Context instance across threads without external synchronization
 * </ul>
 *
 * <p>Parent context references are safe to share since child contexts only read from parents.
 */
public class Context {
    private final Map<String, Value> bindings = new HashMap<>();
    private final Map<String, BiFunction<Context, List<Value>, Result>> predicates = new HashMap<>();
    private final Context parent;

    public Context() {
        this(null);
    }

    private Context(final Context parent) {
        this.parent = parent;
    }

    public Context withBinding(final String var, final Value value) {
        bindings.put(var, value);
        return this;
    }

    public Context withBinding(final String var, final String value) {
        bindings.put(var, StringConstant.of(value));
        return this;
    }

    public Context withBinding(final String var, final long value) {
        bindings.put(var, NumericConstant.of(value));
        return this;
    }

    public Context withBinding(final String var, final double value) {
        bindings.put(var, NumericConstant.of(value));
        return this;
    }

    public Context withBindings(final Map<String, Value> bindings) {
        this.bindings.putAll(bindings);
        return this;
    }

    /**
     * Register a predicate.
     *
     * @param name      predicate name
     * @param predicate the predicate function
     * @return this context for chaining
     */
    public Context withPredicate(
            final String name, final BiFunction<Context, List<Value>, Result> predicate) {
        predicates.put(name, predicate);
        return this;
    }

    /**
     * Register multiple predicates.
     *
     * @param additions map of predicate names to functions
     * @return this context for chaining
     */
    public Context withPredicates(
            final Map<String, BiFunction<Context, List<Value>, Result>> additions) {
        predicates.putAll(additions);
        return this;
    }

    public BiFunction<Context, List<Value>, Result> predicate(final String name) {
        final BiFunction<Context, List<Value>, Result> local = predicates.get(name);
        if (local != null) return local;
        return parent != null ? parent.predicate(name) : null;
    }

    public Value get(final String var) {
        final Value local = bindings.get(var);
        if (local != null) return local;
        return parent != null ? parent.get(var) : null;
    }

    /**
     * Create a child context that inherits predicates and bindings from this context, but can shadow
     * them locally.
     */
    public Context extend() {
        return new Context(this);
    }

    /**
     * Create a child context and immediately add/override the provided bindings.
     */
    public Context extend(final Map<String, Value> overrides) {
        return new Context(this).withBindings(overrides);
    }
}
