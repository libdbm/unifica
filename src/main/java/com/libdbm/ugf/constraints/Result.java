package com.libdbm.ugf.constraints;

/**
 * Result of constraint evaluation.
 */
public record Result(boolean passed, String reason, int penalty) {
    public Result(final boolean passed, final String reason) {
        this(passed, reason, 0);
    }

    public static Result ok() {
        return new Result(true, null);
    }

    public static Result fail(final String reason) {
        return new Result(false, reason);
    }

    public static Result result(final boolean passed, final String reasonIfFail) {
        return passed ? ok() : fail(reasonIfFail);
    }
}
