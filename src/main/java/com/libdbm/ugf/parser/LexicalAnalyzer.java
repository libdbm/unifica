package com.libdbm.ugf.parser;

import com.libdbm.ugf.constraints.Constraint;
import com.libdbm.ugf.constraints.Context;
import com.libdbm.ugf.constraints.Evaluator;
import com.libdbm.ugf.constraints.Strength;
import com.libdbm.ugf.features.NumericConstant;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.grammar.Grammar;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Grammar-driven lexical analyzer for tokenization.
 *
 * <p>Uses lexical rules extracted from the grammar to tokenize input with longest-match semantics.
 * Rules are extracted by {@link TerminalExtractor} from grammar terminals and regexes.
 *
 * <h3>State Management</h3>
 *
 * <p>This class works with {@link LexicalContext} for state-based tokenization. State transitions
 * are specified in lexical rules via {@code ==> STATE} syntax:
 *
 * <ul>
 *   <li>{@code ==> STATE} - Push new state onto stack
 *   <li>{@code ==> _} - Pop current state from stack
 *   <li>{@code ==> !STATE} - Reset stack to single state
 * </ul>
 *
 * <p>State-based scanning enables context-sensitive tokenization such as:
 *
 * <ul>
 *   <li>CDATA sections in XML
 *   <li>String literals with escape sequences
 *   <li>Comments and documentation blocks
 * </ul>
 *
 * <h3>Thread Safety</h3>
 *
 * <p>This class is <b>thread-safe</b> for concurrent tokenization. The lexical rules are immutable
 * after construction, and each tokenization call operates on its own {@link LexicalContext} for
 * state tracking.
 *
 * <p>The same LexicalAnalyzer instance can be safely shared across multiple threads. However, if
 * you pass a shared {@link LexicalContext} to tokenize(), you must synchronize access to it.
 *
 * @see TerminalExtractor
 * @see LexicalContext
 * @see LexicalRule
 */
public final class LexicalAnalyzer implements TokenStream {

  private static final Logger LOGGER = LoggerFactory.getLogger(LexicalAnalyzer.class);
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private final List<LexicalRule> rules;
  private final boolean skip;

  private LexicalAnalyzer(final List<LexicalRule> rules, final boolean skip) {
    this.rules = List.copyOf(rules);
    this.skip = skip;
  }

  /**
   * Build a lexical analyzer by extracting rules from grammar.
   *
   * @param grammar the grammar to extract rules from
   * @return a new lexical analyzer
   */
  public static LexicalAnalyzer build(final Grammar grammar) {
    return build(grammar, true);
  }

  /**
   * Build a lexical analyzer with configurable whitespace skipping.
   *
   * @param grammar the grammar to extract rules from
   * @param skip whether to skip whitespace between tokens
   * @return a new lexical analyzer
   */
  public static LexicalAnalyzer build(final Grammar grammar, final boolean skip) {
    final var rules = TerminalExtractor.extract(grammar);
    return new LexicalAnalyzer(rules, skip);
  }

  /**
   * Create a lexical analyzer from pre-extracted rules.
   *
   * @param rules the lexical rules to use
   * @param skip whether to skip whitespace between tokens
   * @return a new lexical analyzer
   */
  public static LexicalAnalyzer of(final List<LexicalRule> rules, final boolean skip) {
    return new LexicalAnalyzer(rules, skip);
  }

  /**
   * Tokenize input returning a lattice of token alternatives.
   *
   * <p>Implements {@link TokenStream} interface. Delegates to {@link #tokenizeAmbiguous}.
   *
   * @param context the constraint context for predicate evaluation
   * @param input the string to tokenize
   * @return list of token alternatives at each position
   */
  @Override
  public List<List<Token>> tokenize(final Context context, final String input) {
    return tokenizeAmbiguous(context, input);
  }

  /**
   * Tokenize input string returning a single token per position.
   *
   * <p>Uses longest-match semantics. For ambiguous tokenization, use {@link #tokenize} or {@link
   * #tokenizeAmbiguous}.
   *
   * @param input the string to tokenize
   * @return list of tokens
   */
  public List<Token> tokenizeSingle(final String input) {
    return tokenizeSingle(new LexicalContext(), input);
  }

  /**
   * Tokenize input string with given constraint context, single token per position.
   *
   * @param context the constraint context for predicate evaluation
   * @param input the string to tokenize
   * @return list of tokens
   */
  public List<Token> tokenizeSingle(final Context context, final String input) {
    return tokenizeSingle(new LexicalContext(context), input);
  }

  /**
   * Tokenize input string with given lexical context, single token per position.
   *
   * @param lexical the lexical context for state and constraint evaluation
   * @param input the string to tokenize
   * @return list of tokens
   */
  public List<Token> tokenizeSingle(final LexicalContext lexical, final String input) {
    final var tokens = new ArrayList<Token>();
    var pos = 0;

    while (pos < input.length()) {
      pos = skipWhitespace(input, pos);
      if (pos >= input.length()) break;

      lexical.position(pos);
      final var match = findBest(lexical.context(), input, pos);

      if (match == null || match.end == pos) {
        // No match - create error token for single character
        tokens.add(Token.of(input.substring(pos, pos + 1), pos, pos + 1));
        pos++;
      } else {
        tokens.add(match.token);
        pos = match.end;
        if (match.next != null) {
          lexical.apply(match.next);
        }
      }
    }

    return tokens;
  }

  /**
   * Tokenize input returning all ambiguous alternatives at each position.
   *
   * <p>Returns a list of token lists, where each inner list contains all possible tokens at that
   * position. This supports lexical ambiguity where the same text can have different features.
   *
   * @param context the constraint context for predicate evaluation
   * @param input the string to tokenize
   * @return list of token alternatives at each position
   */
  public List<List<Token>> tokenizeAmbiguous(final Context context, final String input) {
    return tokenizeAmbiguous(new LexicalContext(context), input);
  }

  /**
   * Tokenize with ambiguity support using given lexical context.
   *
   * @param lexical the lexical context for state and constraint evaluation
   * @param input the string to tokenize
   * @return list of token alternatives at each position
   */
  public List<List<Token>> tokenizeAmbiguous(final LexicalContext lexical, final String input) {
    final var result = new ArrayList<List<Token>>();
    var pos = 0;

    while (pos < input.length()) {
      pos = skipWhitespace(input, pos);
      if (pos >= input.length()) break;

      lexical.position(pos);
      final var matches = findAll(lexical.context(), input, pos);

      // Filter zero-length matches
      final var currentPos = pos;
      final var valid = matches.stream().filter(m -> m.end > currentPos).toList();

      if (valid.isEmpty()) {
        // No match - create error token
        result.add(List.of(Token.of(input.substring(pos, pos + 1), pos, pos + 1)));
        pos++;
      } else {
        final var tokens = new ArrayList<Token>();
        var nextPos = pos;
        String next = null;

        for (final var match : valid) {
          tokens.add(match.token);
          nextPos = match.end;
          if (match.next != null) {
            if (next != null && !next.equals(match.next)) {
              // Divergent state transitions in ambiguous tokenization - use first and warn
              LOGGER.warn(
                  "Ambiguous tokenization with divergent state transitions at position {}: "
                      + "'{}' vs '{}' for token '{}'. Using first transition.",
                  pos,
                  next,
                  match.next,
                  match.token.text());
            } else {
              next = match.next;
            }
          }
        }

        result.add(tokens);
        pos = nextPos;

        if (next != null) {
          lexical.apply(next);
        }
      }
    }

    return result;
  }

  /**
   * Skip whitespace if configured.
   *
   * @param input the input string
   * @param pos current position
   * @return new position after skipping whitespace
   */
  private int skipWhitespace(final String input, final int pos) {
    if (!skip) return pos;

    final var matcher = WHITESPACE.matcher(input);
    matcher.region(pos, input.length());
    return matcher.lookingAt() ? matcher.end() : pos;
  }

  /**
   * Find best (longest) match at position.
   *
   * @param context constraint context
   * @param input input string
   * @param pos position to match at
   * @return best match or null
   */
  private Match findBest(final Context context, final String input, final int pos) {
    final var matches = findAll(context, input, pos);
    return matches.isEmpty() ? null : matches.getFirst();
  }

  /**
   * Find all matches at position with longest-match semantics.
   *
   * @param context constraint context
   * @param input input string
   * @param pos position to match at
   * @return all matches at longest length
   */
  private List<Match> findAll(final Context context, final String input, final int pos) {
    final var matches = new ArrayList<Match>();
    var maxLen = 0;

    for (final var rule : rules) {
      // Check constraints - returns penalty for defeasible, rejects for required
      var penalty = 0;
      if (rule.hasConstraints()) {
        final var result = checkConstraints(context, rule.constraints());
        if (!result.passed()) {
          continue;
        }
        penalty = result.penalty();
      }

      final var matcher = rule.pattern().matcher(input);
      matcher.region(pos, input.length());

      if (matcher.lookingAt()) {
        final var len = matcher.end() - matcher.start();

        if (len > maxLen) {
          matches.clear();
          maxLen = len;
        }

        if (len == maxLen) {
          final var matched = input.substring(pos, matcher.end());
          final var features = rule.features().copy();
          if (rule.name() != null) {
            features.set("cat", new StringConstant(rule.name()));
          }
          // Store penalty in token features for parser to use
          if (penalty > 0) {
            features.set("_penalty", NumericConstant.of(penalty));
          }
          final var token = Token.of(matched, features, pos, matcher.end());
          matches.add(new Match(token, matcher.end(), rule.next(), rule.hasConstraints(), penalty));
        }
      }
    }

    // Prefer constrained matches if any exist, then lowest penalty
    if (matches.size() > 1) {
      final var hasConstrained = matches.stream().anyMatch(Match::constrained);
      if (hasConstrained) {
        matches.removeIf(m -> !m.constrained);
      }
      // Sort by penalty to prefer lower penalty matches
      matches.sort((a, b) -> Integer.compare(a.penalty(), b.penalty()));
    }

    return matches;
  }

  /**
   * Check constraints and return result with penalty.
   *
   * <p>REQUIRED constraints that fail cause rejection.
   * DEFEASIBLE constraints that fail accumulate penalty but don't reject.
   *
   * @param context constraint context
   * @param constraints constraints to check
   * @return result with passed status and accumulated penalty
   */
  private ConstraintResult checkConstraints(final Context context, final List<Constraint> constraints) {
    var penalty = 0;
    for (final var constraint : constraints) {
      final var result = Evaluator.eval(context, constraint);
      if (!result.passed()) {
        if (constraint.strength() == Strength.REQUIRED) {
          return new ConstraintResult(false, 0);
        }
        // DEFEASIBLE: accumulate penalty but continue
        penalty += result.penalty() > 0 ? result.penalty() : constraint.priority();
      } else {
        penalty += result.penalty();
      }
    }
    return new ConstraintResult(true, penalty);
  }

  /** Result of constraint checking with penalty. */
  private record ConstraintResult(boolean passed, int penalty) {}

  /**
   * Get the extracted lexical rules.
   *
   * @return unmodifiable list of rules
   */
  public List<LexicalRule> rules() {
    return rules;
  }

  /**
   * Check if whitespace skipping is enabled.
   *
   * @return true if whitespace is skipped
   */
  public boolean skipsWhitespace() {
    return skip;
  }

  /** Result of a lexical match. */
  private record Match(Token token, int end, String next, boolean constrained, int penalty) {}
}
