package com.libdbm.ugf.parser;

import com.libdbm.ugf.constraints.Context;

import java.util.List;
import java.util.function.Function;

/**
 * Abstraction for providing tokens to a parser.
 *
 * <p>A TokenStream produces a token lattice where each position may have multiple alternative
 * tokens. This supports lexical ambiguity where the same input substring can be tokenized in
 * different ways.
 *
 * <h3>Implementations</h3>
 *
 * <ul>
 *   <li>{@link LexicalAnalyzer} - Grammar-driven tokenization with state management
 *   <li>POS-based tokenizers - Wrap via {@link #from(Function)}
 * </ul>
 *
 * <h3>Usage with ChartParser</h3>
 *
 * <pre>{@code
 * // Grammar-driven tokenization
 * TokenStream stream = LexicalAnalyzer.build(grammar);
 * ChartParser parser = new ChartParser(grammar, stream);
 *
 * // POS-based tokenization (no lexical ambiguity)
 * TokenStream stream = TokenStream.from(posTokenStream::tokenize);
 * ChartParser parser = new ChartParser(grammar, stream);
 * }</pre>
 *
 * @see LexicalAnalyzer
 * @see ChartParser
 */
@FunctionalInterface
public interface TokenStream {

    /**
     * Create a TokenStream from a simple tokenizer that returns a flat token list.
     *
     * <p>Each token is wrapped in a singleton list (no lexical ambiguity).
     *
     * @param simple function that tokenizes text to a flat list
     * @return TokenStream wrapping the simple tokenizer
     */
    static TokenStream from(final Function<String, List<Token>> simple) {
        return (context, text) -> simple.apply(text).stream().map(List::of).toList();
    }

    /**
     * Tokenize input text into a lattice of token alternatives.
     *
     * <p>Each position in the returned list may have multiple token alternatives. For non-ambiguous
     * tokenizers, each position should contain a single-element list.
     *
     * @param context the constraint evaluation context
     * @param text    the input text to tokenize
     * @return lattice where each position has one or more token alternatives
     */
    List<List<Token>> tokenize(Context context, String text);

    /**
     * Tokenize without explicit context.
     *
     * @param text the input text to tokenize
     * @return lattice where each position has one or more token alternatives
     */
    default List<List<Token>> tokenize(final String text) {
        return tokenize(Utilities.context(), text);
    }
}
