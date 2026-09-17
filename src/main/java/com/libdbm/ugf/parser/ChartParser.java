package com.libdbm.ugf.parser;

import com.libdbm.ugf.constraints.Constraint;
import com.libdbm.ugf.constraints.Context;
import com.libdbm.ugf.constraints.Predicate;
import com.libdbm.ugf.constraints.Result;
import com.libdbm.ugf.features.Binding;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Unifier;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.RuleElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Earley-style chart parser for unification grammars.
 *
 * <p>Uses dynamic programming to efficiently parse with arbitrary context-free grammars, including
 * left-recursive rules. Supports feature structure unification and constraint evaluation.
 *
 * <p>The parser consists of three core operations:
 *
 * <ul>
 *   <li><b>Predict</b>: Add new items for rules that could start at the current position
 *   <li><b>Scan</b>: Advance items that expect a terminal matching the current token
 *   <li><b>Complete</b>: Advance items waiting for a nonterminal that has been recognized
 * </ul>
 *
 * <p>Uses penalty-aware chart storage: when multiple paths reach the same parse state, only the
 * lowest-penalty path is retained. This ensures optimal parse selection.
 *
 * <h3>Thread Safety</h3>
 *
 * <p>This class is <b>not thread-safe</b>. Each instance maintains mutable state (context bindings,
 * chart cells) during parsing. For concurrent parsing:
 *
 * <ul>
 *   <li>Create a separate ChartParser instance per thread, or
 *   <li>Synchronize externally on parse() calls
 * </ul>
 *
 * <p>The Grammar and TokenStream instances passed to the constructor may be safely shared across
 * parser instances as they are effectively immutable after construction.
 */
public final class ChartParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChartParser.class);

    private final Context context;
    private final Grammar grammar;
    private final TokenStream stream;
    private final TokenEnhancer enhancer;
    private final ParseObserver observer;

    /**
     * Diagnostics for current parse (reset at start of each parse).
     */
    private ParseDiagnostics diagnostics;

    /**
     * Token lattice for current parse (reset at start of each parse). Used to attach character
     * spans to failure records.
     */
    private List<List<Token>> currentTokens;

    /**
     * Creates a parser with full configuration.
     *
     * <p>Use this constructor when you need to register custom predicates before parsing. The context
     * should typically be created via {@link Parsers#context()} which registers standard predicates.
     *
     * @param context  the evaluation context with predicates
     * @param grammar  the grammar to parse with
     * @param stream   the token stream for tokenization
     * @param enhancer token enhancement (e.g., POS tagging)
     * @param observer parse event observer for visualization/debugging
     */
    public ChartParser(
            final Context context,
            final Grammar grammar,
            final TokenStream stream,
            final TokenEnhancer enhancer,
            final ParseObserver observer) {
        this.context = context;
        this.grammar = grammar;
        this.stream = stream;
        this.enhancer = enhancer;
        this.observer = observer != null ? observer : ParseObserver.NOOP;
    }

    public ChartParser(
            final Context context,
            final Grammar grammar,
            final TokenStream stream,
            final TokenEnhancer enhancer) {
        this(context, grammar, stream, enhancer, ParseObserver.NOOP);
    }

    public ChartParser(final Context context, final Grammar grammar, final TokenStream stream) {
        this(context, grammar, stream, TokenEnhancer.identity());
    }

    public ChartParser(final Context context, final Grammar grammar) {
        this(context, grammar, LexicalAnalyzer.build(grammar));
    }

    public ChartParser(
            final Grammar grammar, final TokenStream stream, final TokenEnhancer enhancer) {
        this(Utilities.context(), grammar, stream, enhancer);
    }

    public ChartParser(final Grammar grammar, final TokenStream stream) {
        this(grammar, stream, TokenEnhancer.identity());
    }

    public ChartParser(final Grammar grammar) {
        this(grammar, LexicalAnalyzer.build(grammar));
    }

    public ChartParser(final Grammar grammar, final ParseObserver observer) {
        this(Utilities.context(), grammar, LexicalAnalyzer.build(grammar), TokenEnhancer.identity(), observer);
    }

    /**
     * Parse input text and return a ParseResult including penalty information.
     *
     * <p>The penalty reflects accumulated soft constraint violations. A penalty of 0 means all
     * constraints passed. Higher penalties indicate more or more severe soft constraint failures.
     *
     * @param text the input text to parse
     * @return ParseResult with tree and penalty (tree is null if parsing failed)
     */
    public ParseResult parse(final String text) {
        final var ambiguous = stream.tokenize(context, text);

        // Enhance all token alternatives (empty lattice is valid for ε-derivations)
        final var lattice = new ArrayList<List<Token>>();
        for (final var alternatives : ambiguous) {
            final var enhanced = new ArrayList<Token>();
            for (final var tok : alternatives) {
                enhanced.addAll(enhancer.enhance(List.of(tok)));
            }
            lattice.add(enhanced);
        }

        return parseTokenLattice(lattice);
    }

    /**
     * Parse pre-tokenized input. Each token is treated as a single alternative at its position.
     *
     * <p>Use this for POS-tagged input where tokens are already tagged with categories.
     *
     * @param tokens the pre-tokenized input
     * @return ParseResult with tree and penalty
     */
    public ParseResult parse(final List<Token> tokens) {
        // Convert to lattice format (each position has a single token)
        final var lattice = new ArrayList<List<Token>>();
        for (final var tok : tokens) {
            lattice.add(List.of(tok));
        }
        return parseTokenLattice(lattice);
    }

    /**
     * Internal: Parse a token lattice where each position may have multiple alternatives.
     */
    private ParseResult parseTokenLattice(final List<List<Token>> lattice) {
        final var begin = Instant.now();
        LOGGER.debug("Parsing token lattice with {} positions", lattice.size());
        LOGGER.debug("Token lattice: {}", lattice);

        // Initialize fresh diagnostics for this parse
        diagnostics = new ParseDiagnostics();
        currentTokens = lattice;

        // Re-register parser's state predicates (lexer may have overwritten them)
        context.withPredicate("in_state", (ctx, args) -> Result.ok());
        context.withPredicate("state_depth", (ctx, args) -> Result.ok());
        context.withPredicate("state_contains", (ctx, args) -> Result.ok());

        // Initialize chart with penalty-aware storage
        final var n = lattice.size();
        final List<ChartCell> chart = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            chart.add(new ChartCell());
        }

        // Seed with start symbol rules
        final var start = grammar.start();
        LOGGER.debug("Seeding chart with start symbol: {}", start);
        for (final var rule : grammar.rulesFor(start)) {
            final var features = rule.lhs().features().copy();
            chart.getFirst().add(new Item(rule, 0, 0, List.of(), features));
        }

        // Emit Start event
        observer.onStart(new ParseEvents.Start(lattice, grammar, start));

        // Process chart
        for (int i = 0; i <= n; i++) {
            final var cell = chart.get(i);
            var idx = 0;
            LOGGER.debug("Processing chart position {} ({} items)", i, cell.size());

            while (idx < cell.size()) {
                final var item = cell.get(idx++);

                if (item.complete()) {
                    complete(chart, cell, item, i);
                } else {
                    final var next = item.next();

                    if (next instanceof RuleElement.Nonterminal nt) {
                        predict(chart, cell, nt, i);
                    } else if (i < n) {
                        for (final var token : lattice.get(i)) {
                            scan(chart, item, token, i);
                        }
                    }
                }
            }

            // Emit Position event after processing each chart position
            final var items = new ArrayList<>(cell.items());
            final var complete = (int) items.stream().filter(Item::complete).count();
            observer.onPosition(new ParseEvents.Position(i, items, complete, items.size() - complete));
        }

        final var result = extract(chart, lattice, n, start);

        // Emit End event
        observer.onEnd(new ParseEvents.End(result, Duration.between(begin, Instant.now())));

        return result;
    }

    /**
     * Predict: Add items for rules that expand the expected nonterminal.
     */
    private void predict(
            final List<ChartCell> chart,
            final ChartCell cell,
            final RuleElement.Nonterminal nt,
            final int pos) {

        LOGGER.debug("Predict[{}]: expanding {}", pos, nt.name());
        for (final var rule : grammar.rulesFor(nt.name())) {
            // Check only state constraints during prediction (before variables are bound)
            final var result = checkStateConstraints(rule.constraints(), pos);
            if (!result.passed()) {
                LOGGER.debug("Predict[{}]: REJECTED {} - {}", pos, rule.lhs().symbol(), result.reason());
                observer.onPredict(new ParseEvents.Predict(pos, nt, rule, false, result.reason(), null));
                continue;
            }

            final var features = rule.lhs().features().copy();
            var item = new Item(rule, 0, pos, List.of(), features, result.penalty());

            // An empty rule is complete as soon as it is predicted, so its own constraints are checked
            // now; every other item is checked when a token or a constituent completes it.
            if (item.complete()) {
                final var own = own(item, pos);
                if (!own.passed()) {
                    LOGGER.debug("Predict[{}]: REJECTED empty {} - {}", pos, rule.lhs().symbol(), own.reason());
                    observer.onPredict(new ParseEvents.Predict(pos, nt, rule, false, own.reason(), null));
                    continue;
                }
                if (own.penalty() > 0) {
                    item = new Item(rule, 0, pos, List.of(), features, item.penalty() + own.penalty());
                }
            }

            final var added = cell.add(item);
            if (added) {
                LOGGER.debug("Predict[{}]: added {}", pos, item);
            }
            observer.onPredict(new ParseEvents.Predict(pos, nt, rule, added, null, added ? item : null));
        }
    }

    /**
     * Scan: Advance item if current token matches expected terminal/regex.
     */
    private void scan(
            final List<ChartCell> chart, final Item item, final Token token, final int pos) {

        final var next = item.next();
        if (next == null || !matches(next, token)) {
            LOGGER.trace("Scan[{}]: no match for '{}' expecting {}", pos, token.text(), next);
            observer.onScan(new ParseEvents.Scan(pos, item, token, false, null, null));
            return;
        }

        // Create leaf node for terminal
        // For labeled elements, include token text as feature for constraint access (e.g., W.text)
        final ParseTree.Leaf leaf;
        final var labeled =
                switch (next) {
                    case RuleElement.Terminal t -> t.label() != null;
                    case RuleElement.Regex r -> r.label() != null;
                    case RuleElement.TokenMatch tm -> true; // Always include text for TokenMatch
                    default -> false;
                };
        // A literal or regex matched the token's text, so the category the lexer attached (the name of
        // whichever lexical rule produced the token) says nothing about this match. Copying it would
        // make the same derivation look like two whenever two lexical rules match the same text.
        final var literal = !(next instanceof RuleElement.TokenMatch);
        if (labeled) {
            // Build features structure with text for constraint access
            final var features = new Structure();
            for (final var key : token.features().keys()) {
                if (!(literal && "cat".equals(key))) {
                    features.set(key, token.features().get(key));
                }
            }
            features.set("text", new StringConstant(token.text()));
            leaf = new ParseTree.Leaf(token.text(), token.start(), token.end(), features);
        } else {
            leaf = new ParseTree.Leaf(token.text(), token.start(), token.end());
        }
        final var children = append(item.children(), leaf);

        // Merge token features into item features (excluding internal _penalty)
        final var features = item.features().copy();
        for (final var key : token.features().keys()) {
            if (!key.startsWith("_") && !(literal && "cat".equals(key))) {
                features.set(key, token.features().get(key));
            }
        }

        // Extract lexical penalty from token (from defeasible lexical constraints)
        var penalty = item.penalty();
        final var penaltyValue = token.features().get("_penalty");
        if (penaltyValue instanceof com.libdbm.ugf.features.NumericConstant nc) {
            penalty += nc.value().intValue();
        }

        var advanced =
                new Item(
                        item.rule(), item.dot() + 1, item.origin(), children, features, penalty, item.ambiguous());

        // A token that completes the item: its own constraints are checked now, as for an item
        // completed by a constituent, so the item carries its own penalty. The exception is a token
        // the lexer produced from this very rule, whose penalty the token already carries.
        if (advanced.complete() && !lexical(advanced, token)) {
            final var own = own(advanced, pos + 1);
            if (!own.passed()) {
                LOGGER.debug("Scan[{}]: REJECTED {} - {}", pos, advanced.rule().lhs().symbol(), own.reason());
                final var constraints = advanced.rule().constraints();
                if (diagnostics != null && constraints != null && !constraints.isEmpty()) {
                    diagnostics.recordConstraintFailure(
                            advanced.rule().lhs().symbol(),
                            constraints.getFirst(),
                            own.reason(),
                            pos + 1,
                            true,
                            spanAt(pos));
                }
                chart.get(pos + 1).reject(advanced, "constraint failed: " + own.reason());
                observer.onScan(new ParseEvents.Scan(pos, item, token, true, leaf, null));
                return;
            }
            if (own.penalty() > 0) {
                advanced =
                        new Item(
                                advanced.rule(),
                                advanced.dot(),
                                advanced.origin(),
                                advanced.children(),
                                advanced.features(),
                                advanced.penalty() + own.penalty(),
                                advanced.ambiguous());
            }
        }

        final var added = chart.get(pos + 1).add(advanced);
        if (added) {
            LOGGER.debug("Scan[{}]: '{}' -> {}", pos, token.text(), advanced);
        }
        observer.onScan(new ParseEvents.Scan(pos, item, token, true, leaf, added ? advanced : null));
    }

    /**
     * Complete: Advance items waiting for the completed nonterminal.
     */
    private void complete(
            final List<ChartCell> chart, final ChartCell cell, final Item completed, final int pos) {

        final var symbol = completed.rule().lhs().symbol();
        final var origin = completed.origin();
        LOGGER.debug("Complete[{}]: {} from position {}", pos, symbol, origin);

        // Check constraints on the completed item's rule
        final var ctx = context.extend();
        ctx.withBinding("position", pos);  // Bind position for at_start/at_position predicates
        bindChildren(ctx, completed);
        final var constraints = completed.rule().constraints();
        LOGGER.debug(
                "Complete[{}]: Checking {} constraints for {}",
                pos,
                constraints != null ? constraints.size() : 0,
                symbol);
        final var result = ConstraintChecker.check(ctx, constraints);

        // Emit ConstraintEval event
        if (constraints != null && !constraints.isEmpty()) {
            observer.onConstraint(new ParseEvents.ConstraintEval(
                    pos, completed.rule(), constraints, result, ParseEvents.ConstraintEval.Phase.COMPLETE));
        }

        LOGGER.debug(
                "Complete[{}]: Constraint result: passed={}, reason={}",
                pos,
                result.passed(),
                result.reason());
        if (!result.passed()) {
            LOGGER.debug("Complete[{}]: REJECTED {} - {}", pos, symbol, result.reason());
            // Record constraint failure in diagnostics
            LOGGER.debug(
                    "Complete[{}]: Recording failure - diagnostics={}, constraints={}",
                    pos,
                    diagnostics != null,
                    constraints != null ? constraints.size() : 0);
            if (diagnostics != null && constraints != null && !constraints.isEmpty()) {
                final var isHard =
                        constraints.stream()
                                .anyMatch(c -> c.strength() == com.libdbm.ugf.constraints.Strength.REQUIRED);
                diagnostics.recordConstraintFailure(
                        completed.rule().lhs().symbol(),
                        constraints.getFirst(),
                        result.reason(),
                        pos,
                        isHard,
                        spanAt(pos));
                LOGGER.debug("Complete[{}]: Recorded constraint failure for {}", pos, symbol);
            }
            observer.onComplete(new ParseEvents.Complete(pos, completed, List.of(), List.of(), result));
            return;
        }

        // Build parse tree node
        final var node = new ParseTree.Node(symbol, null, completed.children(), completed.features());

        // Track waiting and advanced items for observer
        final var waiting = new ArrayList<Item>();
        final var advanced = new ArrayList<Item>();

        // Iterate over items at the origin that are waiting for this symbol
        for (final var wait : chart.get(origin).items()) {
            if (wait.complete()) continue;

            final var next = wait.next();
            if (!(next instanceof RuleElement.Nonterminal nt) || !nt.name().equals(symbol)) {
                continue;
            }
            waiting.add(wait);

            // Check feature unification
            final var unify = Unifier.unifyWithReason(wait.features(), completed.features(), nt.features());

            // Emit Unification event
            observer.onUnification(new ParseEvents.Unification(
                    pos, wait.features(), completed.features(), nt.features(),
                    unify.succeeded() ? unify.structure() : Optional.empty(),
                    Map.of()));

            if (!unify.succeeded()) {
                LOGGER.debug(
                        "Complete[{}]: UNIFY FAILED {} - {} (waiting={}, completed={})",
                        pos,
                        symbol,
                        unify.reason(),
                        wait.features(),
                        completed.features());
                if (diagnostics != null) {
                    diagnostics.recordUnificationFailure(
                            symbol,
                            wait.features(),
                            completed.features(),
                            nt.features(),
                            unify.reason(),
                            pos,
                            spanBetween(completed.origin(), pos));
                }
                continue;
            }
            final var unified = unify.structure();

            final var children = append(wait.children(), node);
            // The completed item already carries the penalty of its own constraints, added when it was
            // created; adding result.penalty() here as well counted it twice.
            var advPenalty = wait.penalty() + completed.penalty();
            final var adv =
                    new Item(
                            wait.rule(),
                            wait.dot() + 1,
                            wait.origin(),
                            children,
                            unified.get(),
                            advPenalty,
                            wait.ambiguous() || completed.ambiguous());

            // If advanced item is now complete, check its constraints BEFORE adding to chart
            if (adv.complete()) {
                final var advCtx = context.extend();
                advCtx.withBinding("position", pos);  // Bind position for at_start/at_position predicates
                bindChildren(advCtx, adv);
                final var advConstraints = adv.rule().constraints();
                if (advConstraints != null && !advConstraints.isEmpty()) {
                    final var advResult = ConstraintChecker.check(advCtx, advConstraints);

                    // Emit ConstraintEval event for advanced item
                    observer.onConstraint(new ParseEvents.ConstraintEval(
                            pos, adv.rule(), advConstraints, advResult, ParseEvents.ConstraintEval.Phase.COMPLETE));

                    if (!advResult.passed()) {
                        // REQUIRED constraint failed - record and skip this item
                        LOGGER.debug(
                                "Complete[{}]: REJECTED advanced {} - {}",
                                pos,
                                adv.rule().lhs().symbol(),
                                advResult.reason());
                        if (diagnostics != null) {
                            diagnostics.recordConstraintFailure(
                                    adv.rule().lhs().symbol(),
                                    advConstraints.getFirst(),
                                    advResult.reason(),
                                    pos,
                                    true,
                                    spanAt(pos));
                        }
                        cell.reject(adv, "constraint failed: " + advResult.reason());
                        continue;
                    }
                    // Add any penalty from soft constraints
                    advPenalty += advResult.penalty();
                }
            }

            // Re-create with updated penalty if needed
            final var finalItem =
                    advPenalty != adv.penalty()
                            ? new Item(
                            adv.rule(),
                            adv.dot(),
                            adv.origin(),
                            adv.children(),
                            adv.features(),
                            advPenalty,
                            adv.ambiguous())
                            : adv;

            if (cell.add(finalItem)) {
                LOGGER.debug("Complete[{}]: advanced {}", pos, finalItem);
                advanced.add(finalItem);
            }
        }

        // Emit Complete event
        observer.onComplete(new ParseEvents.Complete(pos, completed, waiting, advanced, result));
    }

    /**
     * Whether the lexer has already checked this item's constraints. {@link TerminalExtractor} turns a
     * rule of literals or regexes into a lexical rule named after it, constraints included, so a
     * one-element rule scanned from a token of that same name already carries the rule's penalty on
     * the token. A token match is never lexical: pre-tagged tokens reach the parser unchecked.
     */
    private static boolean lexical(final Item item, final Token token) {
        final var rhs = item.rule().rhs();
        return rhs.size() == 1
                && !(rhs.getFirst() instanceof RuleElement.TokenMatch)
                && token.features().get("cat") instanceof StringConstant(String cat)
                && cat.equals(item.rule().lhs().symbol());
    }

    /**
     * The constraints of a complete item's own rule, checked with its children bound. Every complete
     * item is checked when it is created and carries the resulting penalty itself, so a parent never
     * adds it again.
     */
    private Result own(final Item item, final int pos) {
        final var extended = context.extend();
        extended.withBinding("position", pos);
        bindChildren(extended, item);
        return ConstraintChecker.check(extended, item.rule().constraints());
    }

    /**
     * Bind all children of a completed item to their labels for constraint evaluation.
     */
    private void bindChildren(final Context context, final Item item) {
        final var rhs = item.rule().rhs();
        final var children = item.children();

        // Bind LHS symbol for constraint access (e.g., equals(S.num, ...))
        final var symbol = item.rule().lhs().symbol();
        context.withBinding(symbol, Binding.of(ConstraintChecker.text(item), item.features()));

        for (int i = 0; i < rhs.size() && i < children.size(); i++) {
            final var elem = rhs.get(i);
            final var child = children.get(i);

            final var label =
                    switch (elem) {
                        case RuleElement.Nonterminal nt -> nt.label() != null ? nt.label() : nt.name();
                        case RuleElement.Terminal t -> t.label();
                        case RuleElement.Regex r -> r.label();
                        case RuleElement.TokenMatch tm -> tm.label();
                        default -> null;
                    };

            if (label != null) {
                ConstraintChecker.bind(context, label, child);
            }
        }
    }

    /**
     * Check if rule element matches token.
     */
    private boolean matches(final RuleElement elem, final Token token) {
        return switch (elem) {
            case RuleElement.Terminal t -> t.text().equals(token.text());
            case RuleElement.Regex r -> r.compiled().matcher(token.text()).matches();
            case RuleElement.TokenMatch tm -> true; // Matches any token
            case RuleElement.Alternation alt -> {
                for (final var opt : alt.options()) {
                    if (matches(opt, token)) {
                        yield true;
                    }
                }
                yield false;
            }
            default -> false;
        };
    }

    /**
     * Check only state constraints (for prediction phase). Uses LexicalContext to determine which
     * predicates are state-only.
     */
    private Result checkStateConstraints(final List<Constraint> constraints, final int pos) {
        if (constraints == null || constraints.isEmpty()) {
            return new Result(true, null, 0);
        }

        final var filtered = filterState(constraints);
        if (filtered.isEmpty()) {
            return new Result(true, null, 0);
        }

        // Create context with position for at_position/at_start predicates
        final var extended = context.extend().withBinding("position", pos);
        return ConstraintChecker.check(extended, filtered);
    }

    /**
     * Filter constraints to only include state predicates.
     */
    private List<Constraint> filterState(final List<Constraint> constraints) {
        final var result = new ArrayList<Constraint>();
        for (final var c : constraints) {
            final var filtered = filterStateOne(c);
            if (filtered != null) {
                result.add(filtered);
            }
        }
        return result;
    }

    /**
     * Filter a single constraint for state predicates.
     */
    private Constraint filterStateOne(final Constraint c) {
        return switch (c) {
            case Predicate p -> isStatePredicate(p.name()) ? p : null;
            case Constraint.And a -> {
                final var filtered = filterState(a.conjuncts());
                yield filtered.isEmpty() ? null : new Constraint.And(filtered, a.strength(), a.priority());
            }
            case Constraint.Or o -> {
                final var all = o.disjuncts().stream().allMatch(this::isAllState);
                yield all ? o : null;
            }
            case Constraint.Not n -> {
                final var inner = filterStateOne(n.constraint());
                yield inner != null ? new Constraint.Not(inner, n.strength(), n.priority()) : null;
            }
        };
    }

    /**
     * Check if a constraint only uses state predicates.
     */
    private boolean isAllState(final Constraint c) {
        return switch (c) {
            case Predicate p -> isStatePredicate(p.name());
            case Constraint.And a -> a.conjuncts().stream().allMatch(this::isAllState);
            case Constraint.Or o -> o.disjuncts().stream().allMatch(this::isAllState);
            case Constraint.Not n -> isAllState(n.constraint());
        };
    }

    /**
     * Check if a predicate is a state predicate (can be evaluated without bindings). Includes lexer
     * state predicates plus parser position predicates.
     */
    private boolean isStatePredicate(final String name) {
        return LexicalContext.isLexerOnly(name)
                || "at_start".equals(name)
                || "at_position".equals(name);
    }

    /**
     * Extract completed parse tree from chart, selecting lowest penalty parse.
     *
     * <p>On failure (no winning item found) builds and records a {@link ParseDiagnostics.ParseLattice}
     * snapshot into the current diagnostics so callers can inspect every attempted path.
     */
    private ParseResult extract(
            final List<ChartCell> chart,
            final List<List<Token>> tokens,
            final int n,
            final String start) {
        LOGGER.debug("Extracting parse tree for start symbol '{}' at position {}", start, n);
        Item best = null;
        int bestPenalty = Integer.MAX_VALUE;
        int candidates = 0;
        final var valid = new ArrayList<Item>();

        // Per-item rejection reasons collected during extraction; used to attribute failures in
        // the lattice snapshot if no winner is found.
        final var extractFailures = new LinkedHashMap<Item, String>();

        for (final var item : chart.get(n).items()) {
            if (!item.complete() || item.origin() != 0) {
                continue;
            }
            if (!item.rule().lhs().symbol().equals(start)) {
                extractFailures.put(
                        item,
                        "completes '" + item.rule().lhs().symbol() + "' but expected start '" + start + "'");
                continue;
            }
            candidates++;

            // Defensive: re-verify constraints with proper bindings
            // This catches edge cases where constraints weren't properly checked during completion
            final var constraints = item.rule().constraints();
            if (constraints != null && !constraints.isEmpty()) {
                final var extended = context.extend();
                extended.withBinding("position", n);
                bindChildren(extended, item);
                final var result = ConstraintChecker.check(extended, constraints);
                if (!result.passed()) {
                    LOGGER.debug("Extract: REJECTED {} - {}", item.rule().lhs().symbol(), result.reason());
                    extractFailures.put(item, "constraint failed: " + result.reason());
                    continue;
                }
            }

            valid.add(item);
            if (item.penalty() < bestPenalty) {
                best = item;
                bestPenalty = item.penalty();
            }
        }

        if (best == null) {
            LOGGER.debug("No complete parse found (checked {} candidates)", candidates);
            if (diagnostics != null) {
                diagnostics.recordLattice(snapshot(chart, tokens, n, start, extractFailures));
            }
            return new ParseResult(null, 0, diagnostics);
        }

        LOGGER.debug("Selected parse from {} candidates with penalty {}", candidates, bestPenalty);
        final var tree = new ParseTree.Node(start, null, best.children(), best.features());

        // Ambiguous when the penalties cannot rank the readings: two start items tie at the lowest
        // penalty, or the winning item was built from a constituent that had two derivations.
        final var level = bestPenalty;
        final var tops = valid.stream().filter(item -> item.penalty() == level).toList();
        final var ambiguous = tops.size() > 1 || best.ambiguous();
        if (ambiguous && diagnostics != null) {
            locate(chart, tree, start, n, tops.size() > 1 ? tops : List.of());
        }
        return new ParseResult(tree, bestPenalty, diagnostics, ambiguous);
    }

    /**
     * Record where an ambiguous parse had more than one derivation: the start symbol when two start
     * items tied, and every tied constituent the returned tree contains. A tie in a constituent the
     * tree does not use is not reported, since it did not make this parse ambiguous.
     */
    private void locate(
            final List<ChartCell> chart,
            final ParseTree tree,
            final String start,
            final int n,
            final List<Item> tops) {
        final var nodes = new HashSet<String>();
        collect(tree, nodes);
        final var found = new LinkedHashSet<ParseDiagnostics.Ambiguity>();
        if (!tops.isEmpty()) {
            final var readings =
                    tops.stream().map(item -> item.rule() + " " + item.features().display() + " = " + sketch(item.children())).toList();
            found.add(new ParseDiagnostics.Ambiguity(start, covering(0, n), readings));
        }
        for (int column = 0; column < chart.size(); column++) {
            for (final var item : chart.get(column).ties()) {
                final var symbol = item.rule().lhs().symbol();
                final var span = covering(item.origin(), column);
                if (item.complete() && nodes.contains(key(symbol, span.charStart(), span.charEnd()))) {
                    final var readings = new ArrayList<String>();
                    readings.add(item.rule() + " = " + sketch(item.children()));
                    item.alternatives().forEach(alternative -> readings.add(item.rule() + " = " + sketch(alternative)));
                    found.add(new ParseDiagnostics.Ambiguity(symbol, span, readings));
                }
            }
        }
        if (found.isEmpty()) {
            // No tie sits at a node of the returned tree, which happens when the tie is inside a rule
            // that had not yet completed: two derivations of its first elements, differing only in
            // their features. Report every tie, complete or not, so the cause is still visible.
            for (int column = 0; column < chart.size(); column++) {
                for (final var item : chart.get(column).ties()) {
                    final var readings = new ArrayList<String>();
                    readings.add(item + " = " + sketch(item.children()));
                    item.alternatives().forEach(alternative -> readings.add(item + " = " + sketch(alternative)));
                    found.add(
                            new ParseDiagnostics.Ambiguity(
                                    item.rule().lhs().symbol(), covering(item.origin(), column), readings));
                }
            }
        }
        if (found.isEmpty()) {
            found.add(new ParseDiagnostics.Ambiguity(start, covering(0, n)));
        }
        found.forEach(
                ambiguity ->
                        diagnostics.recordAmbiguity(ambiguity.symbol(), ambiguity.span(), ambiguity.readings()));
    }

    /** A short rendering of a derivation: each child with its features, three levels deep. */
    private static String sketch(final List<ParseTree> children) {
        return String.join(" ", children.stream().map(child -> sketch(child, 3)).toList());
    }

    private static String sketch(final ParseTree tree, final int depth) {
        return switch (tree) {
            case ParseTree.Leaf leaf -> "'" + leaf.text() + "'";
            case ParseTree.Node node -> {
                final var features = node.features().isEmpty() ? "" : node.features().display();
                if (depth <= 1 || node.children().isEmpty()) {
                    yield node.symbol() + features;
                }
                yield node.symbol()
                        + features
                        + "("
                        + String.join(" ", node.children().stream().map(child -> sketch(child, depth - 1)).toList())
                        + ")";
            }
        };
    }

    /** Every non-empty node of a tree, keyed by symbol and character span. */
    private static void collect(final ParseTree tree, final Set<String> nodes) {
        if (tree instanceof ParseTree.Node node && !node.children().isEmpty()) {
            nodes.add(key(node.symbol(), node.start(), node.end()));
            node.children().forEach(child -> collect(child, nodes));
        }
    }

    private static String key(final String symbol, final int start, final int end) {
        return symbol + "@" + start + ".." + end;
    }

    /**
     * The characters covered by the tokens from column {@code origin} up to, but not including,
     * column {@code end}. An empty range is anchored at the start of its column.
     */
    private ParseDiagnostics.Span covering(final int origin, final int end) {
        if (currentTokens == null || end <= origin || origin >= currentTokens.size()) {
            final var at = currentTokens == null ? null : spanAt(origin);
            final var offset = at != null ? at.charStart() : 0;
            return new ParseDiagnostics.Span(origin, offset, offset);
        }
        final var first = currentTokens.get(origin);
        final var last = currentTokens.get(Math.min(end, currentTokens.size()) - 1);
        final var charStart = first.stream().mapToInt(Token::start).min().orElse(0);
        final var charEnd = last.stream().mapToInt(Token::end).max().orElse(charStart);
        return new ParseDiagnostics.Span(origin, charStart, charEnd);
    }

    /**
     * Build a lattice snapshot of the failed chart.
     *
     * <p>Walks every column of the chart, capturing active, completed and rejected items, the
     * character span covered by the column, the set of next symbols each active item was still
     * expecting, and any path-level failures. The final column also contributes failures for every
     * completed-but-wrong-symbol candidate (from {@code extractFailures}) plus {@code DEAD_END}
     * entries for partial parses rooted at the start position.
     */
    private ParseDiagnostics.ParseLattice snapshot(
            final List<ChartCell> chart,
            final List<List<Token>> tokens,
            final int n,
            final String start,
            final Map<Item, String> extractFailures) {

        final var columns = new ArrayList<ParseDiagnostics.Column>(chart.size());
        int furthest = 0;
        ParseDiagnostics.Span furthestSpan = columnSpan(tokens, 0, n);

        for (int i = 0; i < chart.size(); i++) {
            final var cell = chart.get(i);
            final var span = columnSpan(tokens, i, n);

            final var items = new ArrayList<ParseDiagnostics.LatticeItem>();
            final var expectedSet = new LinkedHashSet<String>();

            for (final var item : cell.items()) {
                final var state =
                        item.complete()
                                ? ParseDiagnostics.State.COMPLETE
                                : ParseDiagnostics.State.ACTIVE;
                items.add(toLatticeItem(item, state));
                if (!item.complete()) {
                    expectedSet.add(describe(item.next()));
                }
            }

            final var failures = new ArrayList<ParseDiagnostics.PathFailure>();

            for (final var rejected : cell.rejected()) {
                final var latticeItem = toLatticeItem(rejected.item(), ParseDiagnostics.State.REJECTED);
                items.add(latticeItem);
                final var kind =
                        rejected.reason().startsWith("constraint failed")
                                ? ParseDiagnostics.FailureKind.CONSTRAINT
                                : rejected.reason().startsWith("unification failed")
                                        ? ParseDiagnostics.FailureKind.UNIFICATION
                                        : ParseDiagnostics.FailureKind.DROPPED;
                failures.add(
                        new ParseDiagnostics.PathFailure(latticeItem, span, rejected.reason(), kind));
            }

            // Final-column attribution: wrong-start, extract-time constraint failures, and dead ends.
            if (i == n) {
                for (final var entry : extractFailures.entrySet()) {
                    final var item = entry.getKey();
                    final var reason = entry.getValue();
                    final var kind =
                            reason.startsWith("constraint failed")
                                    ? ParseDiagnostics.FailureKind.CONSTRAINT
                                    : ParseDiagnostics.FailureKind.WRONG_START;
                    failures.add(
                            new ParseDiagnostics.PathFailure(
                                    toLatticeItem(item, ParseDiagnostics.State.COMPLETE), span, reason, kind));
                }
                for (final var item : cell.items()) {
                    if (item.complete() || item.origin() != 0) continue;
                    final var expected = describe(item.next());
                    failures.add(
                            new ParseDiagnostics.PathFailure(
                                    toLatticeItem(item, ParseDiagnostics.State.ACTIVE),
                                    span,
                                    "blocked waiting for " + expected,
                                    ParseDiagnostics.FailureKind.DEAD_END));
                }
            }

            columns.add(
                    new ParseDiagnostics.Column(
                            i, span, items, List.copyOf(expectedSet), failures));

            // Furthest = highest column with any non-predicted progress (complete items or items with
            // dot > 0). Column 0 only has seeded predictions, so it rarely qualifies.
            final var progressed =
                    cell.items().stream().anyMatch(item -> item.complete() || item.dot() > 0);
            if (progressed) {
                furthest = i;
                furthestSpan = span;
            }
        }

        return new ParseDiagnostics.ParseLattice(columns, furthest, furthestSpan);
    }

    private ParseDiagnostics.LatticeItem toLatticeItem(
            final Item item, final ParseDiagnostics.State state) {
        return new ParseDiagnostics.LatticeItem(
                item.toString(),
                item.dot(),
                item.origin(),
                item.penalty(),
                state,
                item.features().toString());
    }

    /**
     * Convenience wrapper that computes a {@link ParseDiagnostics.Span} for the given chart position
     * against the current parse's token lattice. Returns {@code null} if no lattice is available.
     */
    private ParseDiagnostics.Span spanAt(final int pos) {
        if (currentTokens == null) return null;
        return columnSpan(currentTokens, pos, currentTokens.size());
    }

    /**
     * Build a span that covers the input range consumed between two chart columns (the extent of a
     * completed item from {@code originColumn} up to but not including {@code endColumn}). Returns
     * {@code null} if no lattice is available.
     */
    private ParseDiagnostics.Span spanBetween(final int originColumn, final int endColumn) {
        if (currentTokens == null) return null;
        final var start = columnSpan(currentTokens, originColumn, currentTokens.size());
        final var end = columnSpan(currentTokens, endColumn, currentTokens.size());
        final int charStart = start != null ? start.charStart() : 0;
        int charEnd = end != null ? end.charStart() : charStart;
        // If endColumn > 0 but columnSpan returned a zero-width end-of-input, use the last real token
        if (charEnd < charStart) charEnd = charStart;
        return new ParseDiagnostics.Span(originColumn, charStart, charEnd);
    }

    /**
     * Compute the character span covered by a chart column. Column {@code i} represents the state of
     * the parser <em>before</em> consuming token {@code i}; the returned span is the input range of
     * the next token (or a zero-width span at the end of input for {@code i == n}).
     */
    private ParseDiagnostics.Span columnSpan(
            final List<List<Token>> tokens, final int index, final int n) {
        if (tokens.isEmpty()) {
            return new ParseDiagnostics.Span(index, 0, 0);
        }
        if (index < n && index < tokens.size() && !tokens.get(index).isEmpty()) {
            int start = Integer.MAX_VALUE;
            int end = Integer.MIN_VALUE;
            for (final var token : tokens.get(index)) {
                if (token.start() < start) start = token.start();
                if (token.end() > end) end = token.end();
            }
            return new ParseDiagnostics.Span(index, start, end);
        }
        // index == n (end of input) or empty alternatives — anchor at end of last real token
        int end = 0;
        for (int j = Math.min(index, tokens.size()) - 1; j >= 0; j--) {
            if (!tokens.get(j).isEmpty()) {
                for (final var token : tokens.get(j)) {
                    if (token.end() > end) end = token.end();
                }
                break;
            }
        }
        return new ParseDiagnostics.Span(index, end, end);
    }

    private String describe(final RuleElement element) {
        if (element == null) return "<end>";
        return switch (element) {
            case RuleElement.Terminal t -> "'" + t.text() + "'";
            case RuleElement.Regex r -> "[" + r.pattern() + "]";
            case RuleElement.Nonterminal nt -> nt.name();
            case RuleElement.Alternation a -> "(alt)";
            case RuleElement.Repetition r -> describe(r.element()) + quant(r.quantifier());
            case RuleElement.StateAnnotation s -> "{" + s.state() + "}";
            case RuleElement.TokenMatch tm -> "{TOKEN}";
        };
    }

    private String quant(final RuleElement.Quantifier q) {
        return switch (q) {
            case ZERO_OR_MORE -> "*";
            case ONE_OR_MORE -> "+";
            case OPTIONAL -> "?";
        };
    }

    /**
     * Helper to append element to immutable list.
     */
    private List<ParseTree> append(final List<ParseTree> list, final ParseTree elem) {
        final var result = new ArrayList<>(list);
        result.add(elem);
        return List.copyOf(result);
    }

    /**
     * Get the context for external access.
     */
    public Context context() {
        return context;
    }

    /**
     * Get the grammar.
     */
    public Grammar grammar() {
        return grammar;
    }

    /**
     * An Earley item that was considered but dropped, paired with the reason it was dropped. Used by
     * the lattice snapshot to surface paths that would otherwise disappear silently.
     */
    record RejectedItem(Item item, String reason) {
    }

    /**
     * Chart cell with penalty-aware item storage.
     *
     * <p>Uses a Map keyed by (rule, dot, origin, features) to ensure only the lowest-penalty item for
     * each parse state is retained. Equal-penalty items are preserved as alternatives for ambiguity
     * detection.
     *
     * <p>Items that are dropped — either because a lower-penalty duplicate already exists, or because
     * an external caller explicitly rejected them via {@link #reject(Item, String)} — are preserved
     * in a parallel list to support diagnostic lattice snapshots.
     */
    private static final class ChartCell {
        private final Map<ItemKey, Item> items = new LinkedHashMap<>();
        private final List<Item> ordered = new ArrayList<>();
        private final List<RejectedItem> rejected = new ArrayList<>();
        private final List<Item> ties = new ArrayList<>();

        /**
         * Add an item to the cell.
         *
         * <p>The cell holds one item per parse state. {@code ordered} is the processing queue: an item
         * whose penalty drops, or that becomes ambiguous, is queued again, because items already built
         * from its earlier version carry the old penalty or lack the mark. Re-processing a derivation
         * the cell already holds changes nothing, so each state is re-queued a bounded number of times.
         *
         * @return true if item was added, replaced, or merged with existing
         */
        boolean add(final Item item) {
            final var key = new ItemKey(item);
            final var existing = items.get(key);

            if (existing == null) {
                items.put(key, item);
                ordered.add(item);
                return true;
            }

            // Replace if new item has lower penalty
            if (item.penalty() < existing.penalty()) {
                items.put(key, item);
                requeue(existing, item);
                rejected.add(new RejectedItem(existing, "replaced by lower-penalty duplicate"));
                return true;
            }

            // Merge as alternative if equal penalty (structural ambiguity)
            if (item.penalty() == existing.penalty()) {
                final var distinct =
                        !item.children().equals(existing.children())
                                && !existing.alternatives().contains(item.children());
                if (!distinct && (!item.ambiguous() || existing.ambiguous())) {
                    // The same derivation again, with nothing new to say about it
                    return false;
                }
                final var merged = distinct ? existing.withAlternative(item.children()) : existing.mark();
                items.put(key, merged);
                if (existing.ambiguous()) {
                    replace(existing, merged);
                } else {
                    requeue(existing, merged);
                    if (distinct) {
                        ties.add(merged);
                    }
                }
                return true;
            }

            rejected.add(new RejectedItem(item, "higher penalty than existing duplicate"));
            return false;
        }

        /** Swap an item for its new version in the queue. */
        private void replace(final Item existing, final Item replacement) {
            for (int i = 0; i < ordered.size(); i++) {
                if (ordered.get(i) == existing) {
                    ordered.set(i, replacement);
                    break;
                }
            }
        }

        /** Swap an item for its new version and queue the new version to be processed again. */
        private void requeue(final Item existing, final Item replacement) {
            replace(existing, replacement);
            ordered.add(replacement);
        }

        /**
         * The items at which two distinct derivations tied, in the order found, as the cell now holds
         * them, so alternatives merged after the first tie are included.
         */
        List<Item> ties() {
            return ties.stream().map(item -> items.getOrDefault(new ItemKey(item), item)).toList();
        }

        /**
         * Record an item that was considered but deliberately dropped outside {@link #add} (e.g. a
         * constraint or unification failure during completion).
         */
        void reject(final Item item, final String reason) {
            rejected.add(new RejectedItem(item, reason));
        }

        Item get(final int idx) {
            return ordered.get(idx);
        }

        int size() {
            return ordered.size();
        }

        /** One item per parse state; the queue can hold the same state more than once. */
        Collection<Item> items() {
            return List.copyOf(items.values());
        }

        List<RejectedItem> rejected() {
            return List.copyOf(rejected);
        }
    }

    /**
     * Key for chart deduplication - excludes penalty, children, and alternatives.
     */
    private record ItemKey(GrammarRule rule, int dot, int origin, Structure features) {
        ItemKey(final Item item) {
            this(item.rule(), item.dot(), item.origin(), item.features());
        }
    }
}
