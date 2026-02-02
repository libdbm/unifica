package com.libdbm.ugf.grammar.loader;

import com.libdbm.ugf.UnificationGrammarBaseVisitor;
import com.libdbm.ugf.UnificationGrammarParser;
import com.libdbm.ugf.constraints.Constraint;
import com.libdbm.ugf.constraints.Predicate;
import com.libdbm.ugf.constraints.Strength;
import com.libdbm.ugf.features.FeaturePath;
import com.libdbm.ugf.features.StringConstant;
import com.libdbm.ugf.features.Structure;
import com.libdbm.ugf.features.Value;
import com.libdbm.ugf.features.Variable;
import com.libdbm.ugf.grammar.*;
import java.util.*;

/** Visitor that transforms ANTLR parse tree into Grammar objects. */
public final class UnificationGrammarVisitorImpl extends UnificationGrammarBaseVisitor<Object> {

  private final Grammar.Builder builder = Grammar.builder();
  private final List<String> errors = new ArrayList<>();
  private String moduleName = "anonymous";
  private Set<String> exports = null; // null means no export statement seen yet

  public static Grammar build(final UnificationGrammarParser.GrammarFileContext ctx) {
    final var visitor = new UnificationGrammarVisitorImpl();
    visitor.visit(ctx);

    if (!visitor.errors.isEmpty()) {
      throw new RuntimeException("Grammar errors: " + String.join(", ", visitor.errors));
    }

    return visitor.builder.build();
  }

  @Override
  public Object visitModuleStmt(final UnificationGrammarParser.ModuleStmtContext ctx) {
    moduleName = buildModuleName(ctx.moduleName());
    builder.module(new ModuleInfo(moduleName, exports));
    return null;
  }

  @Override
  public Object visitImportStmt(final UnificationGrammarParser.ImportStmtContext ctx) {
    final var decl = buildImportDecl(ctx);
    builder.addImport(decl);
    return null;
  }

  @Override
  public Object visitExportAll(final UnificationGrammarParser.ExportAllContext ctx) {
    // export * explicitly means export everything (same as omitting export)
    exports = Set.of();
    builder.module(new ModuleInfo(moduleName, exports));
    return null;
  }

  @Override
  public Object visitExportList(final UnificationGrammarParser.ExportListContext ctx) {
    // Accumulate exports from multiple export statements instead of replacing
    if (exports == null) {
      exports = new java.util.HashSet<String>();
    }
    for (final var id : ctx.IDENTIFIER()) {
      exports.add(id.getText());
    }
    builder.module(new ModuleInfo(moduleName, exports));
    return null;
  }

  @Override
  public Object visitStartStmt(final UnificationGrammarParser.StartStmtContext ctx) {
    builder.start(ctx.IDENTIFIER().getText());
    return null;
  }

  private String buildModuleName(final UnificationGrammarParser.ModuleNameContext ctx) {
    final var parts = new ArrayList<String>();
    for (final var id : ctx.IDENTIFIER()) {
      parts.add(id.getText());
    }
    return String.join(".", parts);
  }

  private ImportDeclaration buildImportDecl(final UnificationGrammarParser.ImportStmtContext ctx) {
    final String path;

    if (ctx.modulePath().moduleName() != null) {
      path = buildModuleName(ctx.modulePath().moduleName());
    } else {
      path = stripQuotes(ctx.modulePath().STRING().getText());
    }

    // Check for import specification
    if (ctx.importSpec() != null) {
      if (ctx.importSpec().getText().equals(".*")) {
        return new ImportDeclaration.All(path, null);
      } else {
        // Selective import
        final var symbols = new java.util.HashSet<String>();
        for (final var id : ctx.importSpec().IDENTIFIER()) {
          symbols.add(id.getText());
        }
        return new ImportDeclaration.Selective(path, symbols, null);
      }
    }

    // Legacy file import or default all import
    if (ctx.modulePath().STRING() != null) {
      return new ImportDeclaration.File(path);
    }

    return new ImportDeclaration.All(path, null);
  }

  @Override
  public Object visitLexicalRule(final UnificationGrammarParser.LexicalRuleContext ctx) {
    final var lhsSymbol = ctx.lhs().IDENTIFIER().getText();
    final var lhsFeatures =
        ctx.lhs().featureStruct() != null
            ? buildFeatureStruct(ctx.lhs().featureStruct())
            : new Structure();

    var rhs = buildLexicalRHS(ctx.lexicalRhs());
    final var constraints =
        ctx.whereClause() != null ? buildConstraints(ctx.whereClause()) : List.<Constraint>of();

    // Apply state transition to the last lexical element if present
    if (ctx.stateTransition() != null && !rhs.isEmpty()) {
      final var transition = parseStateTransition(ctx.stateTransition());
      rhs = applyTransition(rhs, transition);
    }

    final var lhs = new GrammarRule.LHS(lhsSymbol, lhsFeatures);
    builder.add(new GrammarRule(lhs, rhs, constraints));
    return null;
  }

  /** Apply transition to the last lexical element in the RHS. */
  private List<RuleElement> applyTransition(final List<RuleElement> rhs, final String transition) {
    if (rhs.isEmpty()) return rhs;

    final var result = new ArrayList<>(rhs);
    final var last = result.removeLast();
    result.add(applyTransitionToElement(last, transition));
    return result;
  }

  /** Recursively apply transition to a lexical element. */
  private RuleElement applyTransitionToElement(final RuleElement element, final String transition) {
    return switch (element) {
      case RuleElement.Terminal t -> t.transition(transition);
      case RuleElement.Regex r -> r.transition(transition);
      case RuleElement.Alternation alt -> {
        // Apply transition to all options
        final var options = new ArrayList<RuleElement>();
        for (final var opt : alt.options()) {
          options.add(applyTransitionToElement(opt, transition));
        }
        yield new RuleElement.Alternation(options);
      }
      case RuleElement.Repetition rep -> {
        // Apply transition to inner element
        final var inner = applyTransitionToElement(rep.element(), transition);
        yield new RuleElement.Repetition(inner, rep.quantifier());
      }
      default -> element; // StateAnnotation, Nonterminal - shouldn't be in lexical rules
    };
  }

  @Override
  public Object visitGrammarRule(final UnificationGrammarParser.GrammarRuleContext ctx) {
    final var lhsSymbol = ctx.lhs().IDENTIFIER().getText();
    final var lhsFeatures =
        ctx.lhs().featureStruct() != null
            ? buildFeatureStruct(ctx.lhs().featureStruct())
            : new Structure();

    final var rhs = buildRHS(ctx.rhs());
    final var constraints =
        ctx.whereClause() != null ? buildConstraints(ctx.whereClause()) : List.<Constraint>of();

    final var lhs = new GrammarRule.LHS(lhsSymbol, lhsFeatures);
    builder.add(new GrammarRule(lhs, rhs, constraints));
    return null;
  }

  private List<RuleElement> buildRHS(final UnificationGrammarParser.RhsContext ctx) {
    final var elements = new ArrayList<RuleElement>();

    for (final var elem : ctx.element()) {
      elements.add(buildElement(elem));
    }

    return elements;
  }

  private List<RuleElement> buildLexicalRHS(final UnificationGrammarParser.LexicalRhsContext ctx) {
    final var elements = new ArrayList<RuleElement>();

    for (final var elem : ctx.lexicalElement()) {
      elements.add(buildLexicalElement(elem));
    }

    return elements;
  }

  private RuleElement buildLexicalElement(
      final UnificationGrammarParser.LexicalElementContext ctx) {
    RuleElement element;

    if (ctx.labeledLexicalElement() != null) {
      element = buildLabeledLexicalElement(ctx.labeledLexicalElement());
    } else if (ctx.lexicalAlternation() != null) {
      element = buildLexicalAlternation(ctx.lexicalAlternation());
    } else {
      throw new RuntimeException("Unknown lexical element type");
    }

    // Apply quantifier if present
    if (ctx.quantifier() != null) {
      element = applyQuantifier(element, ctx.quantifier());
    }

    return element;
  }

  private RuleElement buildLabeledLexicalElement(
      final UnificationGrammarParser.LabeledLexicalElementContext ctx) {
    final var base = buildBaseLexicalElement(ctx.baseLexicalElement());

    // Apply label if present
    if (ctx.IDENTIFIER() != null) {
      final var label = ctx.IDENTIFIER().getText();
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
      final UnificationGrammarParser.BaseLexicalElementContext ctx) {
    if (ctx.terminal() != null) {
      return buildTerminal(ctx.terminal());
    } else if (ctx.regex() != null) {
      return buildRegex(ctx.regex());
    } else if (ctx.stateAnnotation() != null) {
      return buildStateAnnotation(ctx.stateAnnotation());
    } else if (ctx.tokenMatch() != null) {
      return new RuleElement.TokenMatch();
    } else if (ctx.lexicalElement() != null) {
      // Parenthesized lexical element
      return buildLexicalElement(ctx.lexicalElement());
    }

    throw new RuntimeException("Unknown base lexical element");
  }

  private RuleElement buildLexicalAlternation(
      final UnificationGrammarParser.LexicalAlternationContext ctx) {
    final var options = new ArrayList<RuleElement>();

    for (final var opt : ctx.lexicalAltOption()) {
      options.add(buildLexicalElement(opt.lexicalElement()));
    }

    return new RuleElement.Alternation(options);
  }

  private RuleElement buildElement(final UnificationGrammarParser.ElementContext ctx) {
    RuleElement element;

    if (ctx.labeledElement() != null) {
      element = buildLabeledElement(ctx.labeledElement());
    } else if (ctx.alternation() != null) {
      element = buildAlternation(ctx.alternation());
    } else {
      throw new RuntimeException("Unknown element type");
    }

    // Apply quantifier if present
    if (ctx.quantifier() != null) {
      element = applyQuantifier(element, ctx.quantifier());
    }

    return element;
  }

  private RuleElement buildLabeledElement(
      final UnificationGrammarParser.LabeledElementContext ctx) {
    final var base = buildBaseElement(ctx.baseElement());

    // Apply label if present to any element type
    if (ctx.IDENTIFIER() != null) {
      final var label = ctx.IDENTIFIER().getText();
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

  private RuleElement buildBaseElement(final UnificationGrammarParser.BaseElementContext ctx) {
    if (ctx.nonterminal() != null) {
      return buildNonterminal(ctx.nonterminal());
    } else if (ctx.terminal() != null) {
      return buildTerminal(ctx.terminal());
    } else if (ctx.regex() != null) {
      return buildRegex(ctx.regex());
    } else if (ctx.tokenMatch() != null) {
      return new RuleElement.TokenMatch();
    } else if (ctx.element() != null) {
      // Parenthesized element
      return buildElement(ctx.element());
    }

    throw new RuntimeException("Unknown base element");
  }

  private RuleElement buildStateAnnotation(
      final UnificationGrammarParser.StateAnnotationContext ctx) {
    final var state = ctx.IDENTIFIER().getText();
    return new RuleElement.StateAnnotation(state);
  }

  private RuleElement buildStateTransitionElement(
      final UnificationGrammarParser.StateTransitionContext ctx) {
    final var transition = parseStateTransition(ctx);
    return new RuleElement.StateAnnotation(transition);
  }

  private String parseStateTransition(final UnificationGrammarParser.StateTransitionContext ctx) {
    if (ctx instanceof UnificationGrammarParser.PushStateContext push) {
      return push.IDENTIFIER().getText();
    } else if (ctx instanceof UnificationGrammarParser.PopStateContext) {
      return "_";
    } else if (ctx instanceof UnificationGrammarParser.ResetStateContext reset) {
      return "!" + reset.IDENTIFIER().getText();
    }
    throw new IllegalArgumentException("Unknown state transition syntax");
  }

  private RuleElement buildNonterminal(final UnificationGrammarParser.NonterminalContext ctx) {
    final var name = ctx.IDENTIFIER().getText();
    final var features =
        ctx.featureStruct() != null ? buildFeatureStruct(ctx.featureStruct()) : new Structure();

    return new RuleElement.Nonterminal(name, null, features);
  }

  private RuleElement buildTerminal(final UnificationGrammarParser.TerminalContext ctx) {
    final var text = stripQuotes(ctx.STRING().getText());
    return new RuleElement.Terminal(text);
  }

  private RuleElement buildRegex(final UnificationGrammarParser.RegexContext ctx) {
    // Concatenate multiple REGEX tokens (e.g., [a-z][0-9]*)
    final var pattern = new StringBuilder();
    for (final var node : ctx.REGEX()) {
      pattern.append(node.getText());
    }
    return new RuleElement.Regex(pattern.toString());
  }

  private Structure buildFeatureStruct(final UnificationGrammarParser.FeatureStructContext ctx) {
    final var features = new Structure();

    for (final var pair : ctx.featurePair()) {
      final var key = pair.IDENTIFIER().getText();
      final var value = buildFeatureValue(pair.featureValue());
      features.set(key, value);
    }

    return features;
  }

  private Value buildFeatureValue(final UnificationGrammarParser.FeatureValueContext ctx) {
    if (ctx.IDENTIFIER() != null) {
      final var text = ctx.IDENTIFIER().getText();
      // Variables start with uppercase
      if (!text.isEmpty() && Character.isUpperCase(text.charAt(0))) {
        return new Variable(text);
      }
      return new StringConstant(text);
    } else if (ctx.STRING() != null) {
      return new StringConstant(stripQuotes(ctx.STRING().getText()));
    } else if (ctx.featureStruct() != null) {
      // Nested feature structures are now fully supported
      return buildFeatureStruct(ctx.featureStruct());
    }

    return new StringConstant("");
  }

  private RuleElement buildAlternation(final UnificationGrammarParser.AlternationContext ctx) {
    final var options = new ArrayList<RuleElement>();

    for (final var opt : ctx.altOption()) {
      options.add(buildElement(opt.element()));
    }

    return new RuleElement.Alternation(options);
  }

  private RuleElement applyQuantifier(
      final RuleElement element, final UnificationGrammarParser.QuantifierContext ctx) {
    final var q = ctx.getText();

    return switch (q) {
      case "*" -> new RuleElement.Repetition(element, RuleElement.Quantifier.ZERO_OR_MORE);
      case "+" -> new RuleElement.Repetition(element, RuleElement.Quantifier.ONE_OR_MORE);
      case "?" -> new RuleElement.Repetition(element, RuleElement.Quantifier.OPTIONAL);
      default -> throw new RuntimeException("Unknown quantifier: " + q);
    };
  }

  private List<Constraint> buildConstraints(final UnificationGrammarParser.WhereClauseContext ctx) {
    // Single expression returned as list for compatibility with existing API
    return List.of(buildExpr(ctx.constraintExpr()));
  }

  /**
   * Build a constraint expression (handles OR at the top level). constraintExpr : constraintTerm
   * ('|' constraintTerm)*
   */
  private Constraint buildExpr(final UnificationGrammarParser.ConstraintExprContext ctx) {
    final var terms = ctx.constraintTerm().stream().map(this::buildTerm).toList();
    return terms.size() == 1 ? terms.getFirst() : new Constraint.Or(terms);
  }

  /**
   * Build a constraint term (handles AND via comma). constraintTerm : constraintFactor (','
   * constraintFactor)*
   */
  private Constraint buildTerm(final UnificationGrammarParser.ConstraintTermContext ctx) {
    final var factors = ctx.constraintFactor().stream().map(this::buildFactor).toList();
    return factors.size() == 1 ? factors.getFirst() : new Constraint.And(factors);
  }

  /**
   * Build a constraint factor (handles NOT, grouping, and defeasible penalties).
   *
   * <p>Grammar: constraintFactor : '!' constraintFactor | '(' constraintExpr ')' penalty? |
   * predicate penalty?
   *
   * <p>When a penalty is present (e.g., `:10`), the constraint becomes defeasible with the
   * specified penalty value. Without a penalty, constraints are required (hard).
   */
  private Constraint buildFactor(final UnificationGrammarParser.ConstraintFactorContext ctx) {
    // Check for negation (first child is '!')
    if (ctx.getChildCount() > 0 && "!".equals(ctx.getChild(0).getText())) {
      return new Constraint.Not(buildFactor(ctx.constraintFactor()));
    }

    // Extract penalty if present
    final var penalty = ctx.penalty();
    final var hasDefeasible = penalty != null;
    final var priority = hasDefeasible ? Integer.parseInt(penalty.NUMBER().getText()) : 0;
    final var strength = hasDefeasible ? Strength.DEFEASIBLE : Strength.REQUIRED;

    // Check for grouped expression
    if (ctx.constraintExpr() != null) {
      final var inner = buildExpr(ctx.constraintExpr());
      return hasDefeasible ? withStrength(inner, strength, priority) : inner;
    }

    // Base case: predicate
    return buildPredicate(ctx.predicate(), strength, priority);
  }

  /**
   * Apply strength and priority to an existing constraint.
   *
   * <p>Used when a grouped expression has a penalty annotation.
   */
  private Constraint withStrength(final Constraint c, final Strength s, final int p) {
    return switch (c) {
      case Predicate pred -> Predicate.of(pred.name(), pred.args(), s, p);
      case Constraint.And and -> new Constraint.And(and.conjuncts(), s, p);
      case Constraint.Or or -> new Constraint.Or(or.disjuncts(), s, p);
      case Constraint.Not not -> new Constraint.Not(not.constraint(), s, p);
    };
  }

  /**
   * Build a predicate constraint with specified strength and priority.
   *
   * <p>Grammar: predicate : IDENTIFIER '(' args? ')'
   */
  private Constraint buildPredicate(
      final UnificationGrammarParser.PredicateContext ctx,
      final Strength strength,
      final int priority) {
    final var name = ctx.IDENTIFIER().getText();
    final var arguments = ctx.args() != null ? buildArgs(ctx.args()) : List.<Value>of();
    return Predicate.of(name, arguments, strength, priority);
  }

  private List<Value> buildArgs(final UnificationGrammarParser.ArgsContext ctx) {
    final var args = new ArrayList<Value>();

    for (final var arg : ctx.arg()) {
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

  private Value buildFeaturePath(final UnificationGrammarParser.FeaturePathContext ctx) {
    final var ids = ctx.IDENTIFIER();
    if (ids.size() == 1) {
      // Simple variable: W
      return Variable.of(ids.getFirst().getText());
    } else {
      // Feature path: W.cat -> FeaturePath(W, cat)
      final var parts = new ArrayList<String>();
      for (final var id : ids) {
        parts.add(id.getText());
      }
      return new FeaturePath(parts);
    }
  }

  private String stripQuotes(final String s) {
    if (s.length() >= 2) {
      if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
        return s.substring(1, s.length() - 1);
      }
    }
    return s;
  }
}
