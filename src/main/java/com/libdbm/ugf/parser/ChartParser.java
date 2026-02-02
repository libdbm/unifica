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
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  /** Diagnostics for current parse (reset at start of each parse). */
  private ParseDiagnostics diagnostics;

  /**
   * Creates a parser with full configuration.
   *
   * <p>Use this constructor when you need to register custom predicates before parsing. The context
   * should typically be created via {@link Parsers#context()} which registers standard predicates.
   *
   * @param context the evaluation context with predicates
   * @param grammar the grammar to parse with
   * @param stream the token stream for tokenization
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

  /** Internal: Parse a token lattice where each position may have multiple alternatives. */
  private ParseResult parseTokenLattice(final List<List<Token>> lattice) {
    final var begin = Instant.now();
    LOGGER.debug("Parsing token lattice with {} positions", lattice.size());
    LOGGER.debug("Token lattice: {}", lattice);

    // Initialize fresh diagnostics for this parse
    diagnostics = new ParseDiagnostics();

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

    final var result = extract(chart, n, start);

    // Emit End event
    observer.onEnd(new ParseEvents.End(result, Duration.between(begin, Instant.now())));

    return result;
  }

  /** Predict: Add items for rules that expand the expected nonterminal. */
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
      final var item = new Item(rule, 0, pos, List.of(), features, result.penalty());

      final var added = cell.add(item);
      if (added) {
        LOGGER.debug("Predict[{}]: added {}", pos, item);
      }
      observer.onPredict(new ParseEvents.Predict(pos, nt, rule, added, null, added ? item : null));
    }
  }

  /** Scan: Advance item if current token matches expected terminal/regex. */
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
    if (labeled) {
      // Build features structure with text for constraint access
      final var features = token.features().copy();
      features.set("text", new StringConstant(token.text()));
      leaf = new ParseTree.Leaf(token.text(), token.start(), token.end(), features);
    } else {
      leaf = new ParseTree.Leaf(token.text(), token.start(), token.end());
    }
    final var children = append(item.children(), leaf);

    // Merge token features into item features (excluding internal _penalty)
    final var features = item.features().copy();
    for (final var key : token.features().keys()) {
      if (!key.startsWith("_")) {
        features.set(key, token.features().get(key));
      }
    }

    // Extract lexical penalty from token (from defeasible lexical constraints)
    var penalty = item.penalty();
    final var penaltyValue = token.features().get("_penalty");
    if (penaltyValue instanceof com.libdbm.ugf.features.NumericConstant nc) {
      penalty += nc.value().intValue();
    }

    final var advanced =
        new Item(item.rule(), item.dot() + 1, item.origin(), children, features, penalty);

    final var added = chart.get(pos + 1).add(advanced);
    if (added) {
      LOGGER.debug("Scan[{}]: '{}' -> {}", pos, token.text(), advanced);
    }
    observer.onScan(new ParseEvents.Scan(pos, item, token, true, leaf, added ? advanced : null));
  }

  /** Complete: Advance items waiting for the completed nonterminal. */
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
            completed.rule().lhs().symbol(), constraints.getFirst(), result.reason(), pos, isHard);
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
          unify.succeeded() ? Map.of() : Map.of()));

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
              pos);
        }
        continue;
      }
      final var unified = unify.structure();

      final var children = append(wait.children(), node);
      var advPenalty = wait.penalty() + completed.penalty() + result.penalty();
      final var adv =
          new Item(
              wait.rule(),
              wait.dot() + 1,
              wait.origin(),
              children,
              unified.get(),
              advPenalty);

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
                  true);
            }
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
                  advPenalty)
              : adv;

      if (cell.add(finalItem)) {
        LOGGER.debug("Complete[{}]: advanced {}", pos, finalItem);
        advanced.add(finalItem);
      }
    }

    // Emit Complete event
    observer.onComplete(new ParseEvents.Complete(pos, completed, waiting, advanced, result));
  }

  /** Bind all children of a completed item to their labels for constraint evaluation. */
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

  /** Check if rule element matches token. */
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

  /** Filter constraints to only include state predicates. */
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

  /** Filter a single constraint for state predicates. */
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

  /** Check if a constraint only uses state predicates. */
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

  /** Extract completed parse tree from chart, selecting lowest penalty parse. */
  private ParseResult extract(final List<ChartCell> chart, final int n, final String start) {
    LOGGER.debug("Extracting parse tree for start symbol '{}' at position {}", start, n);
    Item best = null;
    int bestPenalty = Integer.MAX_VALUE;
    int candidates = 0;

    for (final var item : chart.get(n).items()) {
      if (!item.complete() || item.origin() != 0 || !item.rule().lhs().symbol().equals(start)) {
        continue;
      }
      candidates++;

      // Defensive: re-verify constraints with proper bindings
      // This catches edge cases where constraints weren't properly checked during completion
      final var constraints = item.rule().constraints();
      if (constraints != null && !constraints.isEmpty()) {
        final var ctx = context.extend();
        ctx.withBinding("position", n);
        bindChildren(ctx, item);
        final var result = ConstraintChecker.check(ctx, constraints);
        if (!result.passed()) {
          LOGGER.debug("Extract: REJECTED {} - {}", item.rule().lhs().symbol(), result.reason());
          continue;
        }
      }

      if (item.penalty() < bestPenalty) {
        best = item;
        bestPenalty = item.penalty();
      }
    }

    if (best == null) {
      LOGGER.debug("No complete parse found (checked {} candidates)", candidates);
      return new ParseResult(null, 0, diagnostics);
    }

    LOGGER.debug("Selected parse from {} candidates with penalty {}", candidates, bestPenalty);
    final var tree = new ParseTree.Node(start, null, best.children(), best.features());
    return new ParseResult(tree, bestPenalty, diagnostics);
  }

  /** Helper to append element to immutable list. */
  private List<ParseTree> append(final List<ParseTree> list, final ParseTree elem) {
    final var result = new ArrayList<>(list);
    result.add(elem);
    return List.copyOf(result);
  }

  /** Get the context for external access. */
  public Context context() {
    return context;
  }

  /** Get the grammar. */
  public Grammar grammar() {
    return grammar;
  }

  /**
   * Chart cell with penalty-aware item storage.
   *
   * <p>Uses a Map keyed by (rule, dot, origin, features) to ensure only the lowest-penalty item for
   * each parse state is retained. Equal-penalty items are preserved as alternatives for ambiguity
   * detection.
   */
  private static final class ChartCell {
    private final Map<ItemKey, Item> items = new LinkedHashMap<>();
    private final List<Item> ordered = new ArrayList<>();

    /**
     * Add an item to the cell.
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
        // Update ordered list - find and replace
        for (int i = 0; i < ordered.size(); i++) {
          if (ordered.get(i) == existing) {
            ordered.set(i, item);
            break;
          }
        }
        return true;
      }

      // Merge as alternative if equal penalty (structural ambiguity)
      if (item.penalty() == existing.penalty()) {
        // Add item's children as an alternative derivation
        final var merged = existing.withAlternative(item.children());
        items.put(key, merged);
        for (int i = 0; i < ordered.size(); i++) {
          if (ordered.get(i) == existing) {
            ordered.set(i, merged);
            break;
          }
        }
        return true;
      }

      return false;
    }

    Item get(final int idx) {
      return ordered.get(idx);
    }

    int size() {
      return ordered.size();
    }

    Collection<Item> items() {
      return List.copyOf(ordered);
    }
  }

  /** Key for chart deduplication - excludes penalty, children, and alternatives. */
  private record ItemKey(GrammarRule rule, int dot, int origin, Structure features) {
    ItemKey(final Item item) {
      this(item.rule(), item.dot(), item.origin(), item.features());
    }
  }
}
