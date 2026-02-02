package com.libdbm.ugf.generator;

import com.libdbm.ugf.features.Structure;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

/**
 * Terminal generator that maps regex patterns to generation functions.
 *
 * <p>This generator allows you to register custom generators for specific regex patterns. Patterns
 * are matched exactly (not as regex) for simplicity.
 *
 * <p>Example:
 *
 * <pre>{@code
 * Random random = new Random();
 * RegexTerminalGenerator gen = RegexTerminalGenerator.builder()
 *     .register("[0-9]+", features -> String.valueOf(random.nextInt(100)))
 *     .register("[a-z]+", features -> "word" + random.nextInt(10))
 *     .register("[A-Z][a-z]*", features -> "Name" + random.nextInt(10))
 *     .fallback(new LiteralTerminalGenerator())
 *     .build();
 * }</pre>
 */
public final class RegexTerminalGenerator implements TerminalGenerator {

    private final Map<String, Function<Structure, String>> generators;
    private final TerminalGenerator fallback;

    private RegexTerminalGenerator(
            final Map<String, Function<Structure, String>> generators, final TerminalGenerator fallback) {
        this.generators = Map.copyOf(generators);
        this.fallback = fallback;
    }

    /**
     * Creates a builder for configuring regex terminal generators.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a generator for common numeric patterns.
     *
     * @param random random source
     * @return configured generator
     */
    public static RegexTerminalGenerator forNumbers(final Random random) {
        return builder()
                .register("[0-9]+", features -> String.valueOf(random.nextInt(100)))
                .register("[0-9]", features -> String.valueOf(random.nextInt(10)))
                .register("[1-9][0-9]*", features -> String.valueOf(1 + random.nextInt(99)))
                .fallback(new LiteralTerminalGenerator())
                .build();
    }

    @Override
    public Optional<String> generate(final String symbol, final Structure features) {
        if (generators.containsKey(symbol)) {
            return Optional.of(generators.get(symbol).apply(features));
        }
        return fallback.generate(symbol, features);
    }

    /**
     * Builder for {@link RegexTerminalGenerator}.
     */
    public static final class Builder {
        private final Map<String, Function<Structure, String>> generators = new HashMap<>();
        private TerminalGenerator fallback = (symbol, features) -> Optional.of(symbol);

        /**
         * Registers a generator for a specific pattern.
         *
         * @param pattern   the pattern to match (exact string match, not regex)
         * @param generator function that generates text given features
         * @return this builder
         */
        public Builder register(final String pattern, final Function<Structure, String> generator) {
            generators.put(pattern, generator);
            return this;
        }

        /**
         * Sets the fallback generator for unmatched patterns.
         *
         * @param fallback generator to use when pattern not found
         * @return this builder
         */
        public Builder fallback(final TerminalGenerator fallback) {
            this.fallback = fallback;
            return this;
        }

        /**
         * Builds the terminal generator.
         */
        public RegexTerminalGenerator build() {
            return new RegexTerminalGenerator(generators, fallback);
        }
    }
}
