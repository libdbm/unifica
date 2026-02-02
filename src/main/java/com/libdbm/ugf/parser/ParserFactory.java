package com.libdbm.ugf.parser;

import com.libdbm.ugf.constraints.Context;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarNormalizer;

import java.util.List;

/**
 * Factory for grammar-driven parsing with automatic tokenization.
 *
 * <p>This factory combines a grammar, lexical analyzer, and token enhancer to provide convenient
 * text-to-parse-tree conversion. It uses grammar-driven tokenization which handles lexical
 * ambiguity (e.g., "can" matching both noun and modal verb rules).
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * // Create factory with grammar
 * ParserFactory factory = ParserFactory.create(grammar);
 *
 * // Parse text (tokenizes internally, handles lexical ambiguity)
 * Optional<ParseTree> tree = factory.parse("the dog barks.");
 *
 * // Get full result with diagnostics
 * ParseResult result = factory.parseWithResult("the dog barks.");
 * }</pre>
 *
 * @see ChartParser for direct parsing
 * @see LexicalAnalyzer for grammar-driven tokenization
 */
public final class ParserFactory {

    private final Grammar grammar;
    private final LexicalAnalyzer lexer;
    private final TokenEnhancer enhancer;
    private final ChartParser parser;

    private ParserFactory(
            final Grammar grammar,
            final LexicalAnalyzer lexer,
            final TokenEnhancer enhancer,
            final ParseObserver observer) {
        // Normalize grammar once during factory construction
        this.grammar = GrammarNormalizer.normalize(grammar);
        this.lexer = lexer;
        this.enhancer = enhancer;
        // Create parser with lexer, enhancer, and observer (grammar is already normalized)
        this.parser = new ChartParser(
                Utilities.context(), this.grammar, lexer, enhancer, observer);
    }

    /**
     * Creates a factory with the given grammar and default lexer.
     */
    public static ParserFactory create(final Grammar grammar) {
        return new ParserFactory(
                grammar, LexicalAnalyzer.build(grammar), TokenEnhancer.identity(), ParseObserver.NOOP);
    }

    /**
     * Creates a factory with grammar and observer.
     */
    public static ParserFactory create(final Grammar grammar, final ParseObserver observer) {
        return new ParserFactory(
                grammar, LexicalAnalyzer.build(grammar), TokenEnhancer.identity(), observer);
    }

    /**
     * Creates a factory with grammar, lexer, and enhancer.
     */
    public static ParserFactory create(
            final Grammar grammar, final LexicalAnalyzer lexer, final TokenEnhancer enhancer) {
        return new ParserFactory(grammar, lexer, enhancer, ParseObserver.NOOP);
    }

    /**
     * Creates a factory with grammar, lexer, enhancer, and observer.
     */
    public static ParserFactory create(
            final Grammar grammar,
            final LexicalAnalyzer lexer,
            final TokenEnhancer enhancer,
            final ParseObserver observer) {
        return new ParserFactory(grammar, lexer, enhancer, observer);
    }

    /**
     * Parse input text and return full result with diagnostics.
     *
     * @param text the input text
     * @return parse result with tree, penalty, and diagnostics
     */
    public ParseResult parse(final String text) {
        return parser.parse(text);
    }

    /**
     * Tokenize text using the grammar-driven lexer.
     *
     * @param text the input text
     * @return token lattice (list of alternatives per position)
     */
    public List<List<Token>> tokenize(final String text) {
        return lexer.tokenizeAmbiguous(new Context(), text);
    }

    /**
     * Returns the ChartParser for direct parsing.
     */
    public ChartParser parser() {
        return parser;
    }

    /**
     * Returns the normalized grammar.
     */
    public Grammar grammar() {
        return grammar;
    }

    /**
     * Returns the lexical analyzer.
     */
    public LexicalAnalyzer lexer() {
        return lexer;
    }

    /**
     * Returns the token enhancer.
     */
    public TokenEnhancer enhancer() {
        return enhancer;
    }
}
