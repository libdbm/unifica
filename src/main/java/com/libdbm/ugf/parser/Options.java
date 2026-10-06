package com.libdbm.ugf.parser;

import java.util.Objects;

/**
 * Per-parser configuration (PAR-5).
 *
 * @param limits resource limits for each parse
 * @param observer receives parse events; {@link ParseObserver#NOOP} creates no events (PAR-8)
 * @param diagnostics whether failures are recorded for {@link ParseResult#diagnostics()}
 * @param enhancer applied to every token graph before parsing (LEX-8)
 */
public record Options(
    Limits limits, ParseObserver observer, boolean diagnostics, TokenEnhancer enhancer) {

  /** {@link Limits#DEFAULT}, no observer, no diagnostics, no enhancement. */
  public static final Options DEFAULT = new Options(Limits.DEFAULT, ParseObserver.NOOP, false);

  public Options {
    Objects.requireNonNull(limits, "limits");
    Objects.requireNonNull(observer, "observer");
    Objects.requireNonNull(enhancer, "enhancer");
  }

  public Options(final Limits limits, final ParseObserver observer, final boolean diagnostics) {
    this(limits, observer, diagnostics, TokenEnhancer.identity());
  }
}
