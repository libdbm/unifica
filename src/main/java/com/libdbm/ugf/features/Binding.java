package com.libdbm.ugf.features;

/**
 * Represents a binding that combines both text content and feature structure. Used to bind labeled
 * constituents in constraint evaluation, allowing predicates to access either the text
 * representation or the associated features.
 *
 * <p>For example, when binding a labeled NP like {@code np:subject{num:sing}}, predicates can:
 *
 * <ul>
 *   <li>Check text via {@code is_not_keyword(subject)} - uses {@code text()}
 *   <li>Check features via {@code agree(subject, object)} - uses {@code features()}
 * </ul>
 */
public record Binding(String text, Structure features) implements Value {

    /**
     * Empty structure for bindings without features.
     */
    private static final Structure EMPTY = Structure.builder().build();

    /**
     * Creates a binding with text only (empty features).
     */
    public static Binding of(final String text) {
        return new Binding(text, EMPTY);
    }

    /**
     * Creates a binding with text and features.
     */
    public static Binding of(final String text, final Structure features) {
        return new Binding(text, features != null ? features : EMPTY);
    }

    @Override
    public boolean isVariable() {
        return false;
    }

    @Override
    public boolean isAtomic() {
        return false;
    }

    @Override
    public String display() {
        if (features.isEmpty()) {
            return "'" + text + "'";
        }
        return "'" + text + "'" + features.display();
    }

    @Override
    public String toString() {
        return display();
    }
}
