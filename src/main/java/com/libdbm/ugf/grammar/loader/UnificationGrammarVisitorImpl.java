package com.libdbm.ugf.grammar.loader;

import com.libdbm.ugf.UnificationGrammarBaseVisitor;
import com.libdbm.ugf.UnificationGrammarParser;
import com.libdbm.ugf.constraints.Expression;
import com.libdbm.ugf.features.*;
import com.libdbm.ugf.grammar.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Visitor that transforms ANTLR parse tree into Grammar objects. */
public final class UnificationGrammarVisitorImpl extends UnificationGrammarBaseVisitor<Object> {

  private final Grammar.Builder builder = Grammar.builder();
  private final List<String> errors = new ArrayList<>();
  private String moduleName = "anonymous";
  private Set<String> exports = null; // null means no export statement seen yet

  public static Grammar build(final UnificationGrammarParser.GrammarFileContext context) {
    final var visitor = new UnificationGrammarVisitorImpl();
    visitor.visit(context);

    if (!visitor.errors.isEmpty()) {
      throw new RuntimeException("Grammar errors: " + String.join(", ", visitor.errors));
    }

    return visitor.builder.build();
  }

  /** The production cost from an optional {@code @N} clause, 0 if absent (S-P7). */
  private static long cost(final UnificationGrammarParser.CostClauseContext context) {
    return context == null ? 0 : Long.parseLong(context.NUMBER().getText());
  }

  /** One element stands alone; anything else is a sequence (S-G4). */
  private static RuleElement group(final List<RuleElement> elements) {
    return elements.size() == 1 ? elements.getFirst() : new RuleElement.Sequence(elements);
  }

  /**
   * Resolves backslash escapes: n, t, r, u plus four hex digits; any other escaped character stands
   * for itself.
   */
  private static String unescape(final String text) {
    if (text.indexOf('\\') < 0) {
      return text;
    }
    final var builder = new StringBuilder();
    for (var i = 0; i < text.length(); i++) {
      final var c = text.charAt(i);
      if (c != '\\' || i + 1 == text.length()) {
        builder.append(c);
        continue;
      }
      final var next = text.charAt(++i);
      switch (next) {
        case 'n' -> builder.append('\n');
        case 't' -> builder.append('\t');
        case 'r' -> builder.append('\r');
        case 'u' -> {
          builder.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16));
          i += 4;
        }
        default -> builder.append(next);
      }
    }
    return builder.toString();
  }

  @Override
  public Object visitModuleStmt(final UnificationGrammarParser.ModuleStmtContext context) {
    moduleName = buildModuleName(context.moduleName());
    builder.module(new ModuleInfo(moduleName, exports));
    return null;
  }

  @Override
  public Object visitImportStmt(final UnificationGrammarParser.ImportStmtContext context) {
    final var decl = buildImportDecl(context);
    builder.addImport(decl);
    return null;
  }

  @Override
  public Object visitExportAll(final UnificationGrammarParser.ExportAllContext context) {
    // export * explicitly means export everything (same as omitting export)
    exports = Set.of();
    builder.module(new ModuleInfo(moduleName, exports));
    return null;
  }

  @Override
  public Object visitExportList(final UnificationGrammarParser.ExportListContext context) {
    // Accumulate exports from multiple export statements instead of replacing
    if (exports == null) {
      exports = new java.util.HashSet<String>();
    }
    for (final var id : context.IDENTIFIER()) {
      exports.add(id.getText());
    }
    builder.module(new ModuleInfo(moduleName, exports));
    return null;
  }

  @Override
  public Object visitWhitespaceStmt(final UnificationGrammarParser.WhitespaceStmtContext context) {
    if (context.regex() == null && !"none".equals(context.IDENTIFIER().getText())) {
      throw new IllegalArgumentException(
          "whitespace takes a pattern or 'none', not " + context.IDENTIFIER().getText());
    }
    builder.whitespace(
        context.regex() == null ? "" : ((RuleElement.Regex) buildRegex(context.regex())).pattern());
    return null;
  }

  @Override
  public Object visitSkipStmt(final UnificationGrammarParser.SkipStmtContext context) {
    for (final var id : context.IDENTIFIER()) {
      builder.skip(id.getText());
    }
    return null;
  }

  @Override
  public Object visitStartStmt(final UnificationGrammarParser.StartStmtContext context) {
    builder.start(context.IDENTIFIER().getText());
    return null;
  }

  private String buildModuleName(final UnificationGrammarParser.ModuleNameContext context) {
    final var parts = new ArrayList<String>();
    for (final var id : context.IDENTIFIER()) {
      parts.add(id.getText());
    }
    return String.join(".", parts);
  }

  private ImportDeclaration buildImportDecl(
      final UnificationGrammarParser.ImportStmtContext context) {
    final String path;

    if (context.modulePath().moduleName() != null) {
      path = buildModuleName(context.modulePath().moduleName());
    } else {
      path = stripQuotes(context.modulePath().STRING().getText());
    }

    // Check for import specification
    if (context.importSpec() != null) {
      if (context.importSpec().getText().equals(".*")) {
        return new ImportDeclaration.All(path, null);
      } else {
        // Selective import
        final var symbols = new java.util.HashSet<String>();
        for (final var id : context.importSpec().IDENTIFIER()) {
          symbols.add(id.getText());
        }
        return new ImportDeclaration.Selective(path, symbols, null);
      }
    }

    // A file import, or an import of every export
    if (context.modulePath().STRING() != null) {
      return new ImportDeclaration.File(path);
    }

    return new ImportDeclaration.All(path, null);
  }

  @Override
  public Object visitLexicalRule(final UnificationGrammarParser.LexicalRuleContext context) {
    final var lhsSymbol = context.lhs().IDENTIFIER().getText();
    final var lhsFeatures =
        context.lhs().featureStruct() != null
            ? buildFeatureStruct(context.lhs().featureStruct())
            : Structure.EMPTY;

    final var constraints =
        context.whereClause() != null
            ? buildConstraints(context.whereClause())
            : List.<Expression>of();
    final var lhs = new GrammarRule.LHS(lhsSymbol, lhsFeatures);

    final var transition =
        context.stateTransition() != null ? parseStateTransition(context.stateTransition()) : null;

    // Each top-level alternative is its own production, sharing constraints, transition and cost.
    for (final var alternative : context.lexicalRhs()) {
      final var rhs = buildLexicalRHS(alternative);
      final var kind = GrammarRule.classify(rhs, transition != null);
      builder.add(
          new GrammarRule(lhs, rhs, constraints, kind, cost(context.costClause()), transition));
    }
    return null;
  }

  @Override
  public Object visitGrammarRule(final UnificationGrammarParser.GrammarRuleContext context) {
    final var lhsSymbol = context.lhs().IDENTIFIER().getText();
    final var lhsFeatures =
        context.lhs().featureStruct() != null
            ? buildFeatureStruct(context.lhs().featureStruct())
            : Structure.EMPTY;

    final var constraints =
        context.whereClause() != null
            ? buildConstraints(context.whereClause())
            : List.<Expression>of();
    final var lhs = new GrammarRule.LHS(lhsSymbol, lhsFeatures);

    // Each top-level alternative is its own production, sharing constraints and cost. An
    // alternative without nonterminals is classified by S-G1 like any other production.
    for (final var alternative : context.rhs()) {
      final var rhs = buildRHS(alternative);
      final var kind = GrammarRule.classify(rhs, false);
      builder.add(new GrammarRule(lhs, rhs, constraints, kind, cost(context.costClause())));
    }
    return null;
  }

  private List<RuleElement> buildRHS(final UnificationGrammarParser.RhsContext context) {
    final var elements = new ArrayList<RuleElement>();

    for (final var elem : context.element()) {
      elements.add(buildElement(elem));
    }

    return elements;
  }

  private List<RuleElement> buildLexicalRHS(
      final UnificationGrammarParser.LexicalRhsContext context) {
    final var elements = new ArrayList<RuleElement>();

    for (final var elem : context.lexicalElement()) {
      elements.add(buildLexicalElement(elem));
    }

    return elements;
  }

  private RuleElement buildLexicalElement(
      final UnificationGrammarParser.LexicalElementContext context) {
    RuleElement element;

    if (context.labeledLexicalElement() != null) {
      element = buildLabeledLexicalElement(context.labeledLexicalElement());
    } else if (context.lexicalGroup() != null) {
      element = buildLexicalGroup(context.lexicalGroup());
    } else {
      throw new RuntimeException("Unknown lexical element type");
    }

    // Apply quantifier if present
    if (context.quantifier() != null) {
      element = applyQuantifier(element, context.quantifier());
    }

    return element;
  }

  private RuleElement buildLabeledLexicalElement(
      final UnificationGrammarParser.LabeledLexicalElementContext context) {
    final var base = buildBaseLexicalElement(context.baseLexicalElement());

    // Apply label if present
    if (context.IDENTIFIER() != null) {
      final var label = context.IDENTIFIER().getText();
      return switch (base) {
        case RuleElement.Terminal term -> new RuleElement.Terminal(term.text(), label);
        case RuleElement.Regex regex -> new RuleElement.Regex(regex.pattern(), label);
        case RuleElement.TokenMatch tm -> tm.with(label);
        default -> base;
      };
    }

    return base;
  }

  private RuleElement buildBaseLexicalElement(
      final UnificationGrammarParser.BaseLexicalElementContext context) {
    if (context.terminal() != null) {
      return buildTerminal(context.terminal());
    } else if (context.regex() != null) {
      return buildRegex(context.regex());
    } else if (context.stateAnnotation() != null) {
      return buildStateAnnotation(context.stateAnnotation());
    } else if (context.tokenMatch() != null) {
      return new RuleElement.TokenMatch();
    }

    throw new RuntimeException("Unknown base lexical element");
  }

  private RuleElement buildLexicalGroup(
      final UnificationGrammarParser.LexicalGroupContext context) {
    final var options = new ArrayList<RuleElement>();
    for (final var sequence : context.lexicalSequence()) {
      final var elements = new ArrayList<RuleElement>();
      for (final var element : sequence.lexicalElement()) {
        elements.add(buildLexicalElement(element));
      }
      options.add(group(elements));
    }
    return options.size() == 1 ? options.getFirst() : new RuleElement.Alternation(options);
  }

  private RuleElement buildElement(final UnificationGrammarParser.ElementContext context) {
    RuleElement element;

    if (context.labeledElement() != null) {
      element = buildLabeledElement(context.labeledElement());
    } else if (context.group() != null) {
      element = buildGroup(context.group());
    } else {
      throw new RuntimeException("Unknown element type");
    }

    // Apply quantifier if present
    if (context.quantifier() != null) {
      element = applyQuantifier(element, context.quantifier());
    }

    return element;
  }

  private RuleElement buildLabeledElement(
      final UnificationGrammarParser.LabeledElementContext context) {
    final var base = buildBaseElement(context.baseElement());

    // Apply label if present to any element type
    if (context.IDENTIFIER() != null) {
      final var label = context.IDENTIFIER().getText();
      return switch (base) {
        case RuleElement.Nonterminal nt ->
            new RuleElement.Nonterminal(nt.name(), label, nt.features());
        case RuleElement.Terminal term -> new RuleElement.Terminal(term.text(), label);
        case RuleElement.Regex regex -> new RuleElement.Regex(regex.pattern(), label);
        case RuleElement.TokenMatch tm -> tm.with(label);
        default -> base;
      };
    }

    return base;
  }

  private RuleElement buildBaseElement(final UnificationGrammarParser.BaseElementContext context) {
    if (context.nonterminal() != null) {
      return buildNonterminal(context.nonterminal());
    } else if (context.terminal() != null) {
      return buildTerminal(context.terminal());
    } else if (context.regex() != null) {
      return buildRegex(context.regex());
    } else if (context.tokenMatch() != null) {
      return new RuleElement.TokenMatch();
    }

    throw new RuntimeException("Unknown base element");
  }

  private RuleElement buildStateAnnotation(
      final UnificationGrammarParser.StateAnnotationContext context) {
    final var state = context.IDENTIFIER().getText();
    return new RuleElement.StateAnnotation(state);
  }

  private RuleElement buildStateTransitionElement(
      final UnificationGrammarParser.StateTransitionContext context) {
    final var transition = parseStateTransition(context);
    return new RuleElement.StateAnnotation(transition);
  }

  private String parseStateTransition(
      final UnificationGrammarParser.StateTransitionContext context) {
    if (context instanceof UnificationGrammarParser.PushStateContext push) {
      return push.IDENTIFIER().getText();
    } else if (context instanceof UnificationGrammarParser.PopStateContext) {
      return "_";
    } else if (context instanceof UnificationGrammarParser.ResetStateContext reset) {
      return "!" + reset.IDENTIFIER().getText();
    } else if (context instanceof UnificationGrammarParser.ReplaceStateContext replace) {
      return "^" + replace.IDENTIFIER().getText();
    }
    throw new IllegalArgumentException("Unknown state transition syntax");
  }

  private RuleElement buildNonterminal(final UnificationGrammarParser.NonterminalContext context) {
    final var name = context.IDENTIFIER().getText();
    final var features =
        context.featureStruct() != null
            ? buildFeatureStruct(context.featureStruct())
            : Structure.EMPTY;

    return new RuleElement.Nonterminal(name, null, features);
  }

  private RuleElement buildTerminal(final UnificationGrammarParser.TerminalContext context) {
    final var text = stripQuotes(context.STRING().getText());
    return new RuleElement.Terminal(text);
  }

  private RuleElement buildRegex(final UnificationGrammarParser.RegexContext context) {
    // Concatenate multiple REGEX tokens (e.g., [a-z][0-9]*)
    final var pattern = new StringBuilder();
    for (final var node : context.REGEX()) {
      pattern.append(node.getText());
    }
    return new RuleElement.Regex(pattern.toString());
  }

  private Structure buildFeatureStruct(
      final UnificationGrammarParser.FeatureStructContext context) {
    final var builder = Structure.builder();

    for (final var pair : context.featurePair()) {
      final var key = pair.IDENTIFIER().getText();
      final var value = buildFeatureValue(pair.featureValue());
      builder.with(key, value);
    }

    return builder.build();
  }

  private Value buildFeatureValue(final UnificationGrammarParser.FeatureValueContext context) {
    if (context.VARIABLE() != null) {
      return new Variable(context.VARIABLE().getText());
    } else if (context.IDENTIFIER() != null) {
      return new StringConstant(context.IDENTIFIER().getText());
    } else if (context.STRING() != null) {
      return new StringConstant(stripQuotes(context.STRING().getText()));
    } else if (context.featureStruct() != null) {
      return buildFeatureStruct(context.featureStruct());
    }

    return new StringConstant("");
  }

  private RuleElement buildGroup(final UnificationGrammarParser.GroupContext context) {
    final var options = new ArrayList<RuleElement>();
    for (final var sequence : context.sequence()) {
      final var elements = new ArrayList<RuleElement>();
      for (final var element : sequence.element()) {
        elements.add(buildElement(element));
      }
      options.add(group(elements));
    }
    return options.size() == 1 ? options.getFirst() : new RuleElement.Alternation(options);
  }

  private RuleElement applyQuantifier(
      final RuleElement element, final UnificationGrammarParser.QuantifierContext context) {
    final var q = context.getText();

    return switch (q) {
      case "*" -> new RuleElement.Repetition(element, RuleElement.Quantifier.ZERO_OR_MORE);
      case "+" -> new RuleElement.Repetition(element, RuleElement.Quantifier.ONE_OR_MORE);
      case "?" -> new RuleElement.Repetition(element, RuleElement.Quantifier.OPTIONAL);
      default -> throw new RuntimeException("Unknown quantifier: " + q);
    };
  }

  private List<Expression> buildConstraints(
      final UnificationGrammarParser.WhereClauseContext context) {
    return List.of(buildExpr(context.constraintExpr()));
  }

  /** constraintExpr : constraintTerm ('|' constraintTerm)* */
  private Expression buildExpr(final UnificationGrammarParser.ConstraintExprContext context) {
    final var terms = context.constraintTerm().stream().map(this::buildTerm).toList();
    return terms.size() == 1 ? terms.getFirst() : new Expression.Or(terms);
  }

  /** constraintTerm : constraintFactor (',' constraintFactor)* */
  private Expression buildTerm(final UnificationGrammarParser.ConstraintTermContext context) {
    final var factors = context.constraintFactor().stream().map(this::buildFactor).toList();
    return factors.size() == 1 ? factors.getFirst() : new Expression.And(factors);
  }

  /**
   * constraintFactor : '!' constraintFactor | '(' constraintExpr ')' penalty? | predicate penalty?.
   * A penalty {@code :N} makes the factor a weighted expression; compilation turns weighted
   * top-level conjuncts into soft groups (S-C2, S-C6).
   */
  private Expression buildFactor(final UnificationGrammarParser.ConstraintFactorContext context) {
    if (context.getChildCount() > 0 && "!".equals(context.getChild(0).getText())) {
      return new Expression.Not(buildFactor(context.constraintFactor()));
    }
    final var expression =
        context.constraintExpr() != null
            ? buildExpr(context.constraintExpr())
            : buildPredicate(context.predicate());
    return context.penalty() == null
        ? expression
        : new Expression.Weighted(expression, Long.parseLong(context.penalty().NUMBER().getText()));
  }

  /** predicate : IDENTIFIER '(' args? ')' */
  private Expression buildPredicate(final UnificationGrammarParser.PredicateContext context) {
    final var name = context.IDENTIFIER().getText();
    final var arguments = context.args() != null ? buildArgs(context.args()) : List.<Value>of();
    return new Expression.Call(name, arguments);
  }

  private List<Value> buildArgs(final UnificationGrammarParser.ArgsContext context) {
    final var args = new ArrayList<Value>();

    for (final var arg : context.arg()) {
      if (arg.featurePath() != null) {
        args.add(buildFeaturePath(arg.featurePath()));
      } else if (arg.STRING() != null) {
        args.add(StringConstant.of(stripQuotes(arg.STRING().getText())));
      } else if (arg.featureStruct() != null) {
        // Encode feature structure as string
        args.add(buildFeatureStruct(arg.featureStruct()));
      }
    }

    return args;
  }

  private Value buildFeaturePath(final UnificationGrammarParser.FeaturePathContext context) {
    // A variable can only be the root, so it comes first
    final var parts = new ArrayList<String>();
    if (context.VARIABLE() != null) {
      parts.add(context.VARIABLE().getText());
    }
    for (final var id : context.IDENTIFIER()) {
      parts.add(id.getText());
    }
    if (parts.size() == 1) {
      // Simple name: a variable $X, or a label or symbol w
      return Variable.of(parts.getFirst());
    }
    // Feature path: w.cat -> FeaturePath(w, cat)
    return new FeaturePath(parts);
  }

  /** Removes the quotes from a string literal and resolves its escapes. */
  private String stripQuotes(final String s) {
    if (s.length() >= 2) {
      if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
        return unescape(s.substring(1, s.length() - 1));
      }
    }
    return s;
  }
}
