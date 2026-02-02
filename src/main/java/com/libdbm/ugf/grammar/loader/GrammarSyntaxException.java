package com.libdbm.ugf.grammar.loader;

/**
 * Exception thrown when a grammar file contains syntax errors.
 *
 * <p>This exception is thrown during parsing (before semantic validation) when ANTLR encounters
 * malformed grammar syntax.
 */
public class GrammarSyntaxException extends RuntimeException {

  public GrammarSyntaxException(final String message) {
    super(message);
  }

  public GrammarSyntaxException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
