package com.libdbm.math;

import com.libdbm.math.MathExpr.*;
import java.util.Map;

/** Evaluates mathematical expressions */
public final class MathEvaluator {

  /** Evaluate expression with given variable bindings */
  public static double eval(final MathExpr expr, final Map<String, Double> vars) {
    return switch (expr) {
      case Constant n -> n.value();
      case Variable v -> {
        if (!vars.containsKey(v.name())) {
          throw new RuntimeException("Undefined variable: " + v.name());
        }
        yield vars.get(v.name());
      }
      case BinaryOp b -> {
        final var left = eval(b.left(), vars);
        final var right = eval(b.right(), vars);
        yield switch (b.operator()) {
          case ADD -> left + right;
          case SUBTRACT -> left - right;
          case MULTIPLY -> left * right;
          case DIVIDE -> {
            if (right == 0) throw new ArithmeticException("Division by zero");
            yield left / right;
          }
          case MODULO -> left % right;
          case POWER -> Math.pow(left, right);
        };
      }
      case UnaryOp u -> {
        final var val = eval(u.expr(), vars);
        yield switch (u.operator()) {
          case PLUS -> val;
          case MINUS -> -val;
        };
      }
      case FunctionCall f -> function(f, vars);
    };
  }

  /** Evaluate expression with no variables */
  public static double eval(final MathExpr expr) {
    return eval(expr, Map.of());
  }

  private static double function(final FunctionCall f, final Map<String, Double> vars) {
    final var name = f.name();
    final var arity = f.args().size();

    if (arity == 1) {
      final var arg = eval(f.args().getFirst(), vars);

      return switch (name) {
        case "sin" -> Math.sin(arg);
        case "cos" -> Math.cos(arg);
        case "tan" -> Math.tan(arg);
        case "sqrt" -> Math.sqrt(arg);
        case "abs" -> Math.abs(arg);
        case "log" -> Math.log10(arg);
        case "ln" -> Math.log(arg);
        case "exp" -> Math.exp(arg);
        default -> throw new RuntimeException("Unknown function: " + name);
      };
    }

    if (arity == 2) {
      final var arg1 = eval(f.args().get(0), vars);
      final var arg2 = eval(f.args().get(1), vars);

      return switch (name) {
        case "pow" -> Math.pow(arg1, arg2);
        case "min" -> Math.min(arg1, arg2);
        case "max" -> Math.max(arg1, arg2);
        default -> throw new RuntimeException("Unknown function: " + name);
      };
    }

    throw new RuntimeException("Function " + name + " with arity " + arity + " not supported");
  }

  /** Pretty-print expression */
  public static String toString(final MathExpr expr) {
    return switch (expr) {
      case Constant n -> String.valueOf(n.value());
      case Variable v -> v.name();
      case BinaryOp b ->
          "(" + toString(b.left()) + " " + b.operator().symbol() + " " + toString(b.right()) + ")";
      case UnaryOp u -> u.operator().symbol() + toString(u.expr());
      case FunctionCall f -> {
        final var sb = new StringBuilder(f.name()).append("(");
        for (int i = 0; i < f.args().size(); i++) {
          if (i > 0) sb.append(", ");
          sb.append(toString(f.args().get(i)));
        }
        sb.append(")");
        yield sb.toString();
      }
    };
  }
}
