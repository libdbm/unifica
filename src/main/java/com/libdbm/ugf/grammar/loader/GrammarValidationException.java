package com.libdbm.ugf.grammar.loader;

/**
 * Exception thrown when grammar validation fails.
 */
public final class GrammarValidationException extends RuntimeException {

    public GrammarValidationException(final String message) {
        super(message);
    }

    public GrammarValidationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
