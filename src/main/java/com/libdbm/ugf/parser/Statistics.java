package com.libdbm.ugf.parser;

/**
 * Counters for one parse (PRF-1).
 *
 * @param nodes token graph nodes
 * @param edges token graph edges
 * @param states chart states created
 * @param agenda agenda entries processed
 * @param completions attempts to advance a waiting state over a completed constituent or a token
 * @param unifications three-way unifications performed
 * @param evaluations constraint plans evaluated
 * @param calls predicate calls made
 * @param hits predicate calls answered from the per-parse memo (PRF-2)
 * @param nanos elapsed time
 */
public record Statistics(
    long nodes,
    long edges,
    long states,
    long agenda,
    long completions,
    long unifications,
    long evaluations,
    long calls,
    long hits,
    long nanos) {}
