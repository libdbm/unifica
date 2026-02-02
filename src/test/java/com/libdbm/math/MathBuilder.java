package com.libdbm.math;

import com.libdbm.math.MathExpr.*;
import com.libdbm.ugf.parser.ParseTree;
import com.libdbm.ugf.parser.Utilities;
import java.util.ArrayList;

/** Builds mathematical expression AST from unification grammar parse trees */
public final class MathBuilder {

  public static MathExpr build(final ParseTree tree) {
    return switch (tree) {
      case ParseTree.Leaf leaf ->
          throw new IllegalArgumentException("Cannot build from leaf: " + leaf);
      case ParseTree.Node node ->
          switch (node.symbol()) {
            case "expr" -> buildExpr(node);
            case "term" -> buildTerm(node);
            case "factor" -> buildFactor(node);
            case "atom" -> buildAtom(node);
            default -> throw new IllegalArgumentException("Unknown symbol: " + node.symbol());
          };
    };
  }

  private static MathExpr buildExpr(final ParseTree.Node node) {
    // expr --> term
    // expr --> expr '+' term
    // expr --> expr '-' term

    final var children = node.children();
    if (children.size() == 1) {
      // Just a term
      return build(children.getFirst());
    }

    // Binary operation
    MathExpr left = null;
    BinaryOperator op = null;
    MathExpr right = null;

    for (final var child : children) {
      if (child instanceof ParseTree.Node n) {
        if (n.symbol().equals("expr")) {
          left = buildExpr(n);
        } else if (n.symbol().equals("term")) {
          right = buildTerm(n);
        }
      } else if (child instanceof ParseTree.Leaf l) {
        if (l.text().equals("+") || l.text().equals("-")) {
          op = BinaryOperator.fromSymbol(l.text());
        }
      }
    }

    if (left != null && op != null && right != null) {
      return new BinaryOp(left, op, right);
    }

    throw new IllegalArgumentException("Invalid expr node: " + node);
  }

  private static MathExpr buildTerm(final ParseTree.Node node) {
    // term --> factor
    // term --> term '*' factor
    // term --> term '/' factor
    // term --> term '%' factor
    final var children = node.children();
    if (children.size() == 1) {
      return build(children.getFirst());
    }

    MathExpr left = null;
    BinaryOperator op = null;
    MathExpr right = null;

    for (final var child : children) {
      if (child instanceof ParseTree.Node n) {
        if (n.symbol().equals("term")) {
          left = buildTerm(n);
        } else if (n.symbol().equals("factor")) {
          right = buildFactor(n);
        }
      } else if (child instanceof ParseTree.Leaf l) {
        if (l.text().equals("*") || l.text().equals("/") || l.text().equals("%")) {
          op = BinaryOperator.fromSymbol(l.text());
        }
      }
    }

    if (left != null && op != null && right != null) {
      return new BinaryOp(left, op, right);
    }

    throw new IllegalArgumentException("Invalid term node: " + node);
  }

  private static MathExpr buildFactor(final ParseTree.Node node) {
    // factor --> atom
    // factor --> atom '^' factor
    // factor --> '-' factor
    // factor --> '+' factor
    final var children = node.children();
    if (children.size() == 1) {
      return build(children.getFirst());
    }

    // Check for unary operator
    if (children.getFirst() instanceof ParseTree.Leaf l
        && (l.text().equals("-") || l.text().equals("+"))) {

      final UnaryOperator op = UnaryOperator.fromSymbol(l.text());
      for (final var child : children) {
        if (child instanceof ParseTree.Node n && n.symbol().equals("factor")) {
          return new UnaryOp(op, buildFactor(n));
        }
      }
    }

    // Binary operation (exponentiation)
    MathExpr left = null;
    BinaryOperator op = null;
    MathExpr right = null;

    for (final var child : children) {
      if (child instanceof ParseTree.Node n) {
        if (n.symbol().equals("atom") && left == null) {
          left = buildAtom(n);
        } else if (n.symbol().equals("factor")) {
          right = buildFactor(n);
        }
      } else if (child instanceof ParseTree.Leaf l && l.text().equals("^")) {
        op = BinaryOperator.fromSymbol(l.text());
      }
    }

    if (left != null && op != null && right != null) {
      return new BinaryOp(left, op, right);
    }

    throw new IllegalArgumentException("Invalid factor node: " + node);
  }

  private static MathExpr buildAtom(final ParseTree.Node node) {
    // atom --> number
    // atom --> variable
    // atom --> '(' expr ')'
    // atom --> function '(' expr ')'
    // atom --> function '(' expr ',' expr ')'
    final var children = node.children();

    // Number
    for (final var child : children) {
      if (child instanceof ParseTree.Node n && n.symbol().equals("number")) {
        final var text = Utilities.textOf(n);
        if (text == null || text.trim().isEmpty()) {
          throw new IllegalArgumentException("Invalid number node: " + n);
        }
        return new Constant(Double.parseDouble(text));
      }
    }

    // Variable
    for (final var child : children) {
      if (child instanceof ParseTree.Node n && n.symbol().equals("variable")) {
        return new Variable(Utilities.textOf(n));
      }
    }

    // Parenthesized expression
    boolean hasParens = false;
    for (final var child : children) {
      if (child instanceof ParseTree.Leaf l && l.text().equals("(")) {
        hasParens = true;
        break;
      }
    }

    if (hasParens) {
      // Look for function name first
      String name = null;
      for (final var child : children) {
        if (child instanceof ParseTree.Node n && n.symbol().equals("function")) {
          name = Utilities.textOf(n);
          break;
        }
      }

      // Collect expr arguments
      final var args = new ArrayList<MathExpr>();
      for (final var child : children) {
        if (child instanceof ParseTree.Node n && n.symbol().equals("expr")) {
          args.add(buildExpr(n));
        }
      }

      if (name != null) {
        return new FunctionCall(name, args);
      } else if (!args.isEmpty()) {
        // Just parenthesized expression
        return args.getFirst();
      }
    }

    throw new IllegalArgumentException("Invalid atom node: " + node);
  }
}
