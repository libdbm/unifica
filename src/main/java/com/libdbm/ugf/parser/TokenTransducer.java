package com.libdbm.ugf.parser;

import java.util.List;

/**
 * Transforms a token stream, possibly with side effects.
 *
 * <p>Transducers form composable pipelines for token processing:
 * <pre>{@code
 * var pipeline = posTokenizer
 *     .andThen(definitionTransducer)
 *     .andThen(lexiconEnhancer);
 *
 * var tokens = pipeline.transduce(text);
 * }</pre>
 *
 * <p>Unlike parsers which consume tokens to produce parse trees,
 * transducers produce token streams (possibly transformed or filtered).
 */
@FunctionalInterface
public interface TokenTransducer {

    /**
     * Identity transducer that passes tokens through unchanged.
     *
     * @return identity transducer
     */
    static TokenTransducer identity() {
        return tokens -> tokens;
    }

    /**
     * Transform a token stream.
     *
     * @param tokens input tokens
     * @return transformed tokens (may be same, filtered, or augmented)
     */
    List<Token> transduce(List<Token> tokens);

    /**
     * Compose this transducer with another.
     *
     * @param next the transducer to apply after this one
     * @return composed transducer
     */
    default TokenTransducer andThen(final TokenTransducer next) {
        return tokens -> next.transduce(this.transduce(tokens));
    }
}
