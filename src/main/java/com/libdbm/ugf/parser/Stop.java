package com.libdbm.ugf.parser;

/**
 * Why a parse ended with {@link Outcome#LIMIT} or {@link Outcome#CANCELLED}.
 *
 * @param resource what ran out: one of the constants below
 * @param observed the amount used when the parse stopped, or -1 if the resource is not counted
 * @param allowed the configured limit, or -1 if there is none
 * @param message a readable explanation
 */
public record Stop(String resource, long observed, long allowed, String message) {

  /** Chart states created ({@link Limits#states()}). */
  public static final String STATES = "states";

  /** Agenda entries processed ({@link Limits#agenda()}). */
  public static final String AGENDA = "agenda";

  /** Packed derivation links recorded ({@link Limits#links()}). */
  public static final String LINKS = "links";

  /** Token graph nodes ({@link Limits#nodes()}). */
  public static final String NODES = "nodes";

  /** Lexical state stack depth ({@link Limits#depth()}). */
  public static final String DEPTH = "depth";

  /** Nodes of the selected tree ({@link Limits#tree()}). */
  public static final String TREE = "tree";

  /** The parse took longer than {@link Limits#deadline()}. */
  public static final String DEADLINE = "deadline";

  /** A penalty sum overflowed 64 bits (S-C10). */
  public static final String PENALTY = "penalty";

  /** The lexer could not build the token graph. */
  public static final String LEXER = "lexer";

  /** The parsing thread was interrupted. */
  public static final String INTERRUPT = "interrupt";
}
