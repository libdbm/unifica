package com.libdbm.ugf.constraints;

/**
 * Strength of a constraint for defeasible logic.
 */
public enum Strength {
    /**
     * Required constraint - must be satisfied for parse to succeed.
     */
    REQUIRED,

    /**
     * Defeasible constraint - can be violated but incurs a penalty. Used for preferences and soft
     * constraints.
     */
    DEFEASIBLE
}
