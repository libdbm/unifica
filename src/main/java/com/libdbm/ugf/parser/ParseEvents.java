package com.libdbm.ugf.parser;

import com.libdbm.ugf.constraints.Constraint;
import com.libdbm.ugf.constraints.Result;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Value;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.RuleElement;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Event records for parser observation.
 *
 * <p>Each record captures the state at a specific point during parsing. All records are immutable
 * snapshots - they copy mutable data to prevent observers from seeing changes after the event.
 */
public final class ParseEvents {

    private ParseEvents() {
    }

    /**
     * Fired when parsing begins.
     *
     * @param lattice the token lattice (each position may have multiple token alternatives)
     * @param grammar the grammar being used
     * @param start   the start symbol
     */
    public record Start(List<List<Token>> lattice, Grammar grammar, String start) {
        public Start {
            lattice = lattice.stream().map(List::copyOf).toList();
        }
    }

    /**
     * Fired when parsing completes (success or failure).
     *
     * @param result  the parse result (may have null tree if failed)
     * @param elapsed time spent parsing
     */
    public record End(ParseResult result, Duration elapsed) {
    }

    /**
     * Fired when predict operation considers a rule.
     *
     * @param position the chart position
     * @param trigger  the nonterminal that triggered prediction
     * @param rule     the predicted rule
     * @param accepted whether the rule was added to the chart
     * @param reason   if rejected, the reason why
     * @param item     the item added (null if rejected)
     */
    public record Predict(
            int position,
            RuleElement.Nonterminal trigger,
            GrammarRule rule,
            boolean accepted,
            String reason,
            Item item) {
    }

    /**
     * Fired when scan operation attempts to match a token.
     *
     * @param position the chart position
     * @param item     the item being scanned
     * @param token    the token being matched
     * @param matched  whether the token matched
     * @param leaf     the leaf node created (null if not matched)
     * @param advanced the advanced item (null if not matched)
     */
    public record Scan(
            int position, Item item, Token token, boolean matched, ParseTree.Leaf leaf, Item advanced) {
    }

    /**
     * Fired when complete operation processes a completed item.
     *
     * @param position  the chart position
     * @param completed the completed item
     * @param waiting   items at origin waiting for this symbol
     * @param advanced  items that were advanced (may be fewer than waiting due to unification failure)
     * @param result    result of constraint evaluation on completed item
     */
    public record Complete(
            int position, Item completed, List<Item> waiting, List<Item> advanced, Result result) {
        public Complete {
            waiting = List.copyOf(waiting);
            advanced = List.copyOf(advanced);
        }
    }

    /**
     * Fired when feature unification is attempted during complete.
     *
     * @param position  the chart position
     * @param waiting   the waiting item's features
     * @param completed the completed item's features
     * @param target    the target features from the nonterminal reference
     * @param result    the unified features if successful
     * @param bindings  variable bindings after unification
     */
    public record Unification(
            int position,
            Structure waiting,
            Structure completed,
            Structure target,
            Optional<Structure> result,
            Map<String, Value> bindings) {
        public Unification {
            waiting = waiting.copy();
            completed = completed.copy();
            target = target.copy();
            result = result.map(Structure::copy);
            bindings = Map.copyOf(bindings);
        }
    }

    /**
     * Fired when constraints are evaluated.
     *
     * @param position    the chart position
     * @param rule        the rule whose constraints are being evaluated
     * @param constraints the constraints being evaluated
     * @param result      the evaluation result
     * @param phase       when the constraints were evaluated (predict or complete)
     */
    public record ConstraintEval(
            int position, GrammarRule rule, List<Constraint> constraints, Result result, Phase phase) {
        public ConstraintEval {
            constraints = List.copyOf(constraints);
        }

        /**
         * When constraints are evaluated.
         */
        public enum Phase {
            PREDICT,
            COMPLETE,
            EXTRACT
        }
    }

    /**
     * Fired when processing at a chart position completes.
     *
     * @param position   the chart position
     * @param items      all items in the cell
     * @param complete   count of complete items
     * @param incomplete count of incomplete items
     */
    public record Position(int position, List<Item> items, int complete, int incomplete) {
        public Position {
            items = List.copyOf(items);
        }
    }

    /**
     * Fired when a token is unexpected at a position.
     *
     * @param position the chart position
     * @param token    the unexpected token
     * @param expected symbols that were expected
     */
    public record Unexpected(int position, String token, java.util.Set<String> expected) {
        public Unexpected {
            expected = java.util.Set.copyOf(expected);
        }
    }
}
